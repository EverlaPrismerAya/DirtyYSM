package net.everla.ysmPhysLib;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.config.ClientConfigScope;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.everla.reimaginedysm.compat.optifine.OptiFineDetector;
import net.everla.reimaginedysm.gpu.BoneSkinShader;
import net.everla.reimaginedysm.gpu.GpuCapability;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import java.lang.ref.WeakReference;
import java.util.*;

/** Render-thread coordinator. A failed/unsupported request always resolves on CPU before fallback drawing. */
public final class GpuPhysics {
    private static final Map<float[],Instance> instances=new WeakHashMap<>();
    private static final Set<Instance> resources=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<PhysDefinition,GpuPhysModel> models=new IdentityHashMap<>();
    private static final Map<PhysDefinition,String> rejected=new WeakHashMap<>();
    private static final Set<String> logged=new HashSet<>();
    private static String shaderFailure;
    private static String lastReason="experimental GPU physics disabled";
    private GpuPhysics() {}

    public static String getLastReason() {return lastReason;}

    static boolean prepare(AnimatedGeoModel owner,Matrix4f root,double time,boolean paused,Object world,
                           double x,double y,double z,int fps,boolean textured,java.util.function.Function<net.minecraft.world.phys.AABB,Iterable<net.minecraft.world.phys.shapes.VoxelShape>> blockCollisions) {
        float[] parameters=owner.getMatrixData();
        if(!PhysConfig.gpuEnabled()) {lastReason="experimental GPU physics disabled";deactivate(parameters);return false;}
        String reason=null;
        if(net.everla.reimaginedysm.compat.firstperson.FirstPersonCompat.isFirstPersonActive()) reason="FirstPerson body uses CPU rendering and physics";
        else if(!textured || ClientConfigScope.effective(GeneralConfig.USE_COMPATIBILITY_RENDERER).get()
                || !ClientConfigScope.effective(GeneralConfig.USE_GPU_RENDERER).get()) reason="GPU renderer required";
        else if(OptiFineDetector.isOptifinePresent()) reason="OptiFine is unsupported";
        else if(shaderFailure!=null) reason=shaderFailure;
        else if(!GpuCapability.isAvailable()) reason=GpuCapability.getReason();
        else if(!GL.getCapabilities().OpenGL43) reason="OpenGL 4.3 required";
        else if(!root.isFinite() || Math.abs(root.determinant())<1e-12f) reason="singular/non-finite model transform";
        else if(!supportsDefinition(owner.getGeoModel().ysmPhysics)) reason="CPU physics active; all dependency groups require world collisions";
        else if(!supportedScale(owner.getGeoModel().ysmPhysics,parameters,root)) reason="nonuniform/singular collision transforms require CPU physics";
        else if(requiresCpuPose(owner.getGeoModel(),gpuAffected(owner.getGeoModel().ysmPhysics),parameters)) reason="physical subtree requires a CPU pose (attachment, locator, bone query or FacingCamera)";
        else if(rejected.containsKey(owner.getGeoModel().ysmPhysics)) reason=rejected.get(owner.getGeoModel().ysmPhysics);
        if(reason!=null) {report(reason);deactivate(parameters);return false;}
        RenderSystem.assertOnRenderThread();
        try(var saved=new GpuPhysKernel.SavedState()) {
            reason=GpuPhysKernel.capabilityReason();
            if(reason!=null) {report(reason);deactivate(parameters);return false;}
            if(!BoneSkinShader.ensureCompiled()) {report("GPU skin shader unavailable");deactivate(parameters);return false;}
            try {GpuPhysKernel.compile();}
            catch(Exception e) {shaderFailure="GPU physics shader unavailable: "+e.getMessage();throw e;}
            var geometry=owner.getGeoModel();
            var model=models.get(geometry.ysmPhysics);
            boolean created=false;
            if(model==null) {
                boolean[] glowing=new boolean[geometry.bakedBones.size()];
                for(int i=0;i<glowing.length;i++) glowing[i]=geometry.bakedBones.get(i).glow;
                model=new GpuPhysModel(geometry.ysmPhysics,glowing);models.put(geometry.ysmPhysics,model);created=true;
            }
            var instance=instances.get(parameters);
            if(instance==null) {
                try {instance=new Instance(owner,model);}
                catch(RuntimeException | Error e) {if(created) {models.remove(geometry.ysmPhysics);model.close();}throw e;}
                instances.put(parameters,instance);resources.add(instance);model.users++;
                owner.ysmPhysics.partition(parameters,true);
            }
            // A shadow/main/extra draw at the same simulation time must not replace
            // the first pass's world-space targets with a different camera's pose.
            if(instance.time!=time || instance.world!=world || instance.fps!=fps) {
                instance.root.set(root);instance.time=time;instance.paused=paused;instance.world=world;
                instance.x=x;instance.y=y;instance.z=z;instance.fps=fps;
                instance.blockCollisions=blockCollisions;
            }
            instance.touched=System.nanoTime();
            owner.ysmPhysics.update(parameters,instance.root,time,paused,world,instance.x,instance.y,instance.z,instance.blockCollisions);
            boolean[] cpu=geometry.ysmPhysics.cpuCollisionIslands();
            boolean anyGpu=geometry.ysmPhysics.joints.stream().anyMatch(j->!cpu[j.bone()]);
            boolean anyCpu=geometry.ysmPhysics.joints.stream().anyMatch(j->cpu[j.bone()]);
            lastReason=!anyGpu?"CPU collision physics active; GPU bone matrices"
                    :anyCpu?"GPU physics active; collision dependency groups use CPU":"GPU physics active";return true;
        } catch(Exception | LinkageError e) {
            report("GPU physics initialization failed: "+e.getMessage());
            rejected.put(owner.getGeoModel().ysmPhysics,lastReason);deactivate(parameters);return false;
        } catch(OutOfMemoryError e) {
            report("GPU physics allocation failed");rejected.put(owner.getGeoModel().ysmPhysics,lastReason);deactivate(parameters);return false;
        }
    }

    /** Returns the instance's final bone SSBO, or 0 after applying CPU fallback to parameters. */
    public static int render(float[] parameters,Matrix4f renderRoot,int packedLight) {
        var instance=instances.get(parameters);
        if(instance==null) return 0;
        try(var saved=new GpuPhysKernel.SavedState()) {
            instance.draw(parameters,renderRoot,packedLight);return instance.kernel.output;
        } catch(Exception | LinkageError | OutOfMemoryError e) {
            report("GPU physics dispatch failed: "+e.getMessage());
            rejected.put(instance.kernel.model.definition,lastReason);fallback(parameters);return 0;
        }
    }

    public static void fallback(float[] parameters) {
        var instance=instances.get(parameters);
        if(instance==null) return;
        var owner=instance.owner.get();
        deactivate(parameters);
        if(owner!=null) {
            owner.ysmPhysics.setFrameRate(instance.fps);
            owner.ysmPhysics.update(parameters,instance.root,instance.time,instance.paused,instance.world,instance.x,instance.y,instance.z,instance.blockCollisions);
        }
    }

    static void deactivate(float[] parameters) {
        var instance=instances.remove(parameters);
        if(instance!=null) {
            var owner=instance.owner.get();if(owner!=null) owner.ysmPhysics.partition(parameters,false);
            release(instance);
        }
    }
    private static void release(Instance instance) {
        if(!resources.remove(instance)) return;
        var owner=instance.owner.get();
        if(owner!=null) owner.ysmPhysics.partition(owner.getMatrixData(),false);
        instance.kernel.close();
        var model=instance.kernel.model;
        if(--model.users==0) {models.remove(model.definition);model.close();}
    }
    public static void disposeModel(GeoModel model) {
        var iterator=instances.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(entry.getValue().kernel.model.definition==model.ysmPhysics) {release(entry.getValue());iterator.remove();}
        }
        rejected.remove(model.ysmPhysics);
    }
    /** Called every client tick, including world unloads and frames without any rendered players. */
    public static void tick() {
        if(resources.isEmpty()) return;
        RenderSystem.assertOnRenderThread();
        var world=Minecraft.getInstance().level;
        long now=System.nanoTime();
        var iterator=instances.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();var instance=entry.getValue();
            if(instance.owner.get()==null || instance.world!=world || now-instance.touched>5_000_000_000L || !PhysConfig.gpuEnabled() || PhysConfig.frameRate()==0) {
                var owner=instance.owner.get();if(owner!=null) owner.ysmPhysics.partition(entry.getKey(),false);
                release(instance);iterator.remove();
            }
        }
        // WeakHashMap expunges dead animation arrays; retain GL objects until explicitly deleted here.
        for(var instance:new ArrayList<>(resources)) if(instance.owner.get()==null) release(instance);
    }
    public static void shutdown() {
        for(var instance:new ArrayList<>(resources)) release(instance);
        instances.clear();rejected.clear();GpuPhysKernel.shutdown();shaderFailure=null;
    }
    private static void report(String reason) {
        lastReason=reason;
        if(logged.add(reason)) YesSteveModel.LOGGER.info("GPU physics -> CPU: {}",reason);
    }

    private static boolean[] gpuAffected(PhysDefinition definition) {
        boolean[] result=definition.affected.clone(),cpu=definition.cpuCollisionIslands();
        for(int i=0;i<result.length;i++) result[i] &= !cpu[i];
        return result;
    }

    static boolean requiresCpuPose(GeoModel model,boolean[] affected,float[] parameters) {
        var ids=new HashSet<Integer>();
        for(var list:List.of(model.leftHandIds,model.rightHandIds,model.elytraIds,model.tacPistolIds,model.tacRifleIds,
                model.leftWaistIds,model.rightWaistIds,model.leftShoulderIds,model.rightShoulderIds,model.bladeIds,
                model.sheathIds,model.headIds,model.backpackIds)) for(int id:list) ids.add(id);
        for(var lists:List.of(model.extraLeftHandGroups,model.extraRightHandGroups,model.passengerGroups))
            for(var list:lists) for(int id:list) ids.add(id);
        for(int i=0;i<affected.length;i++) if(affected[i]) {
            var bone=model.topLevelBones().get(i);
            // The Java billboard draw needs CPU-readable transforms for this subtree.
            if(model.bakedBones.get(i).facingCameraSubtree || ids.contains(bone.getBoneId()) || bone.getName().equals("ViewLocator") || bone.getName().equals("AllHead")
                    || parameters[i*12+11]!=0) return true;
        }
        return false;
    }

    /** World-contact islands stay on CPU; the remaining islands solve on GPU. */
    static boolean supportsDefinition(PhysDefinition definition) {
        boolean[] cpu=definition.blockCollisionIslands();
        return definition.joints.stream().anyMatch(joint->!cpu[joint.bone()]);
    }

    /** Float-only contact fitting has not passed parity for anisotropically scaled collision models. */
    static boolean supportedScale(PhysDefinition definition,float[] p,Matrix4f root) {
        // A zero-scale ordinary ancestor also makes inverse(parent) undefined without contacts.
        for(int i=0;i<definition.bones.size();i++) for(int component=6;component<9;component++)
            if(!Float.isFinite(p[i*12+component]) || Math.abs(p[i*12+component])<1e-6f) return false;
        if(definition.cubes.isEmpty()) return true;
        var scale=root.getScale(new Vector3f());
        if(root.determinant()<=0 || !uniform(scale.x,scale.y,scale.z)) return false;
        for(int i=0;i<definition.bones.size();i++) if(!uniform(p[i*12+6],p[i*12+7],p[i*12+8])) return false;
        return true;
    }
    private static boolean uniform(float x,float y,float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && x>1e-6f && y>1e-6f && z>1e-6f
                && Math.abs(x-y)<1e-5f && Math.abs(x-z)<1e-5f;
    }

    private static final class Instance {
        final WeakReference<AnimatedGeoModel> owner;
        final GpuPhysKernel kernel;
        final Matrix4f root=new Matrix4f();
        final Clock clock=new Clock();
        final Matrix4f[] animated;
        double time=Double.NaN,x,y,z,drawn=Double.NaN;
        Object world;
        int fps;
        boolean paused;
        java.util.function.Function<net.minecraft.world.phys.AABB,Iterable<net.minecraft.world.phys.shapes.VoxelShape>> blockCollisions;
        long touched;
        Instance(AnimatedGeoModel owner,GpuPhysModel model) {
            this.owner=new WeakReference<>(owner);kernel=new GpuPhysKernel(model,1);
            animated=new Matrix4f[model.original.length];Arrays.setAll(animated,i->new Matrix4f());
        }
        void draw(float[] parameters,Matrix4f renderRoot,int light) {
            if(time==drawn && clock.world==world && clock.fps==fps) {kernel.matrices(parameters,renderRoot,light);return;}
            var definition=kernel.model.definition;
            boolean rootJump=false;
            for(int i:definition.order) {
                int parent=definition.bones.get(i).parentIdx;
                RenderUtils.prepMatrixForBone(definition.bones.get(i),animated[i].set(parent<0?root:animated[parent]),parameters,i*12);
            }
            // Root target jumps are CPU-visible without reading simulated state back.
            for(var joint:definition.joints) if(joint.parent()<0) {
                var point=animated[joint.bone()].transformPosition(definition.pivot(joint.bone()));
                var previous=clock.roots.put(joint.bone(),point);
                if(previous!=null && previous.distanceSquared(point)>16) rootJump=true;
            }
            clock.advance(time,paused,world,x,y,z,fps,rootJump);
            // Contact islands have already been solved on CPU; no readback or
            // duplicate collision solve is needed when building the final matrices.
            kernel.solve(parameters,root,renderRoot,List.of(),clock.steps,clock.reset,1f/fps,clock.velocity,light,clock.alpha());
            drawn=time;
        }
    }

    /** CPU schedules time; the GPU owns all persistent physical positions/velocities. */
    static final class Clock {
        double last=Double.NaN,accumulator,originX,originY,originZ;
        Object world;
        int fps,steps;
        boolean reset;
        final Vector3f velocity=new Vector3f();
        final Map<Integer,Vector3f> roots=new HashMap<>();
        float alpha() {return (float)Math.max(0,Math.min(1,accumulator*fps));}
        void advance(double time,boolean paused,Object world,double x,double y,double z,int fps,boolean rootJump) {
            double elapsed=Double.isFinite(last)?Math.max(0,time-last):0;
            Vector3f shift=new Vector3f((float)(originX-x),(float)(originY-y),(float)(originZ-z));
            reset=!Double.isFinite(last) || !Double.isFinite(time) || time<last || elapsed>.25
                    || this.world!=world || this.fps!=fps || shift.lengthSquared()>16 || rootJump;
            if(reset) accumulator=0;
            velocity.zero();if(!reset && !paused && elapsed>1e-9) velocity.set(shift).mul((float)(-1/elapsed));
            steps=0;
            if(!reset && !paused && elapsed>0) {
                double dt=1.0/fps;accumulator=Math.min(accumulator+elapsed,dt*8);
                while(steps<8 && accumulator+1e-9>=dt) {steps++;accumulator-=dt;}
            }
            this.last=time;this.world=world;this.fps=fps;originX=x;originY=y;originZ=z;
        }
    }
}

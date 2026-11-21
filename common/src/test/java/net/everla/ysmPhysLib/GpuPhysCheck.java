package net.everla.ysmPhysLib;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.everla.reimaginedysm.gpu.ShaderUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.*;
import static net.everla.ysmPhysLib.PhysCheck.*;

/** Opt-in real driver check. Readbacks occur ONLY here, never in the production backend. */
public final class GpuPhysCheck {
    private static final Object WORLD=new Object();
    private static final Vector3f ZERO=new Vector3f();
    public static void main(String[] args) throws Exception {
        clock();
        var callback=GLFWErrorCallback.createPrint(System.err);callback.set();
        if(!GLFW.glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        long window=0;
        try {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
            window=GLFW.glfwCreateWindow(64,64,"ysmPhys offscreen verification",0,0);
            if(window==0) throw new IllegalStateException("OpenGL 4.3 context unavailable");
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();RenderSystem.initRenderThread();
            System.out.println("GPU physics verification: "+GL11.glGetString(GL11.GL_VENDOR)+" / "+GL11.glGetString(GL11.GL_RENDERER)+" / "+GL11.glGetString(GL11.GL_VERSION));
            String reason=GpuPhysKernel.capabilityReason();if(reason!=null) throw new AssertionError(reason);
            GpuPhysKernel.compile();
            for(int fps:new int[]{1,15,30,60,120}) for(int scenario=0;scenario<7;scenario++) {
                var fixture=fixture(scenario);
                if(GpuPhysics.supportsDefinition(fixture.ysmPhysics)) parity(fixture,fps,scenario);
                else assert !fixture.ysmPhysics.cubes.isEmpty() : "unexpected CPU fallback";
            }
            GeoModel demo=loadModel(System.getProperty("ysm.physicsExample"));
            sharedNativeMesh(demo);
            if(System.getProperty("ysm.physicsModel")!=null) sharedNativeMesh(loadModel(System.getProperty("ysm.physicsModel")));
            for(int fps:new int[]{1,15,30,60,120}) for(int scenario=2;scenario<=4;scenario++) parity(demo,fps,scenario);
            irisMatrices();
            queuedPhysicsMatrices();
            if(Boolean.getBoolean("ysm.gpuContactPrototype")) {
                for(int scenario=2;scenario<5;scenario++) parity(fixture(scenario),120,scenario,true);
                parity(demo,120,4,true);parity(demo,120,2,true);
                if(System.getProperty("ysm.physicsModel")!=null) parity(loadModel(System.getProperty("ysm.physicsModel")),120,4,true);
            }
            hybrid();
            visibilityAndState();
            for(int count:new int[]{1,16,64}) benchmark(fixture(0),count);
            if(Boolean.getBoolean("ysm.gpuContactPrototype")) for(int count:new int[]{1,16,64}) benchmark(demo,count);
            System.out.println("PASS: enabled GPU paths: parity, branches, cadence, resets, visibility, repeated draws, CPU collision islands and GL state restoration");
        } finally {
            if(window!=0) {GpuPhysKernel.shutdown();GLFW.glfwDestroyWindow(window);}
            GLFW.glfwTerminate();GLFW.glfwSetErrorCallback(null);callback.free();
        }
    }
    /** A world entity and HUD player reuse one mesh, but supply different poses and roots. */
    private static void sharedNativeMesh(GeoModel geometry) throws Exception {
        if(geometry.ysmPhysics==null) geometry.ysmPhysics=definition(geometry);
        com.elfmcys.yesstevemodel.NativeLibLoader.init();
        assert com.elfmcys.yesstevemodel.NativeLibLoader.isLoaded() : "native renderer unavailable";
        var mesh=net.everla.reimaginedysm.gpu.GpuRenderPath.getOrBuildMesh(geometry);
        assert mesh!=null;
        var first=parameters(geometry);var second=parameters(geometry);
        var firstPose=mesh.pose(first);var secondPose=mesh.pose(second);
        assert firstPose==mesh.pose(first) && firstPose!=secondPose : "pose ownership is not instance-specific";
        assert firstPose.boneSsbo!=secondPose.boneSsbo && firstPose.boneBuffer!=secondPose.boneBuffer : "instances share mutable bone data";
        firstPose.ensureXformBuffers(mesh);secondPose.ensureXformBuffers(mesh);
        assert firstPose.xformVbo()!=secondPose.xformVbo() && firstPose.xformVao()!=secondPose.xformVao() : "Iris instances share transformed vertices";
        var worldRoot=new Matrix4f().translate(2,3,-4).rotateXYZ(.2f,.7f,-.1f);
        var hudRoot=new Matrix4f().translate(20,40,1000).scale(35,-35,35);
        float[] poseScratch=new float[16],normalScratch=new float[9];
        var visibility=net.everla.reimaginedysm.gpu.GpuRenderPath.class.getDeclaredMethod("updateBoneVisibility",List.class,float[].class,ByteBuffer.class);
        visibility.setAccessible(true);
        int shader=ShaderUtil.compileShaderFromResource(GL20.GL_VERTEX_SHADER,"/bone_skin.vsh");
        int program=ShaderUtil.linkProgramWith(p->GL30.glTransformFeedbackVaryings(p,new CharSequence[]{"gl_Position"},GL30.GL_INTERLEAVED_ATTRIBS),shader);
        int feedback=GL15.glGenBuffers();
        var vertices=read(mesh.vbo,mesh.vertexCount*32);
        int draws=30*4,bytesPerDraw=mesh.vertexCount*16;
        var captured=MemoryUtil.memAlloc(bytesPerDraw*draws);
        var expectedDraws=new ArrayList<Matrix4f[]>();
        var drawnParameters=new ArrayList<float[]>();
        try {
            GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"u_proj"),false,new Matrix4f().get(new float[16]));
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,feedback);
            GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,(long)bytesPerDraw*draws,GL15.GL_STREAM_READ);
            GL30.glBindVertexArray(mesh.vao);
            GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            for(int frame=0;frame<30;frame++) {
                for(int bone=0;bone<geometry.bakedBones.size();bone++) {
                    int at=bone*12;
                    for(int axis=0;axis<3;axis++) {
                        first[at+axis]=.15f*(float)Math.sin(frame*.13+bone*.7+axis);
                        second[at+axis]=-.2f*(float)Math.cos(frame*.19+bone*.3+axis);
                        first[at+3+axis]=.4f*(float)Math.sin(frame*.1+bone+axis);
                        second[at+3+axis]=-.3f*(float)Math.cos(frame*.2+bone+axis);
                    }
                }
                second[3]=12;
                // Alternate the FirstPerson AllHead override when the fixture has one.
                int hiddenBone=0;
                for(int bone=0;bone<geometry.topLevelBones().size();bone++)
                    if(geometry.topLevelBones().get(bone).getName().equals("AllHead")) {hiddenBone=bone;break;}
                first[hiddenBone*12+9]=first[hiddenBone*12+10]=frame%2;
                // Queue alternating instances and repeated passes without readbacks/waits.
                for(int draw=0;draw<4;draw++) {
                    var p=draw==1?second:first;
                    var instance=mesh.pose(p);
                    instance.boneBuffer.clear();
                    var root=draw==1?hudRoot:worldRoot;
                    var before=p.clone();
                    GeoModel.nComputeBoneMatrices(mesh.pointer,root.get(poseScratch),root.normal(new org.joml.Matrix3f()).get(normalScratch),
                            p,1234,instance.boneBuffer);
                    visibility.invoke(null,geometry.bakedBones,p,instance.boneBuffer);
                    var expected=matrices(geometry.ysmPhysics,p,root);
                    for(int bone=0;bone<expected.length;bone++) {
                        var actual=new Matrix4f(instance.boneBuffer.asFloatBuffer().position(bone*36));
                        assert actual.equals(expected[bone],.002f) : "shared native mesh retained another instance's transform";
                    }
                    assert Arrays.equals(before,p) : "native draw changed instance animation";
                    instance.uploadBones();
                    GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,instance.boneSsbo);
                    GL30.glBindBufferRange(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,feedback,(long)(frame*4+draw)*bytesPerDraw,bytesPerDraw);
                    GL30.glBeginTransformFeedback(GL11.GL_POINTS);
                    GL11.glDrawArrays(GL11.GL_POINTS,0,mesh.vertexCount);
                    GL30.glEndTransformFeedback();
                    expectedDraws.add(expected);drawnParameters.add(before);
                }
            }
            // One read only after every draw has been submitted. Per-draw readbacks
            // serialize the GPU and conceal in-flight buffer reuse regressions.
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,feedback);
            GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,captured);
            for(int draw=0;draw<draws;draw++) {
                var expected=expectedDraws.get(draw);var p=drawnParameters.get(draw);
                for(int vertex=0;vertex<mesh.vertexCount;vertex++) {
                        int at=vertex*32,bone=Short.toUnsignedInt(vertices.getShort(at+24));
                        assert bone<expected.length : "invalid packed bone index";
                        boolean hidden=p[bone*12+9]!=0;
                        for(int ancestor=bone;ancestor>=0;ancestor=geometry.bakedBones.get(ancestor).parentIdx)
                            hidden |= p[ancestor*12+6]==0 || p[ancestor*12+7]==0 || p[ancestor*12+8]==0
                                    || (ancestor!=bone && p[ancestor*12+10]!=0);
                        var wanted=hidden?new org.joml.Vector4f(2,2,2,1):expected[bone].transform(new org.joml.Vector4f(
                                vertices.getFloat(at),vertices.getFloat(at+4),vertices.getFloat(at+8),1));
                        var actual=new org.joml.Vector4f(captured.asFloatBuffer().position(draw*mesh.vertexCount*4+vertex*4));
                        assert actual.equals(wanted,.003f) : "shared mesh vertex visibility/pose mismatch: draw="+draw+" vertex="+vertex+" bone="+bone+" actual="+actual+" expected="+wanted;
                }
            }
            var cleanup=mesh.getClass().getDeclaredMethod("cleanupPoses",boolean.class);cleanup.setAccessible(true);
            assert GL15.glIsBuffer(firstPose.boneSsbo) && GL15.glIsBuffer(secondPose.boneSsbo);
            cleanup.invoke(mesh,true);
            assert !GL15.glIsBuffer(firstPose.boneSsbo) && !GL15.glIsBuffer(secondPose.boneSsbo) : "pose SSBO leaked on unload";
            assert !GL15.glIsBuffer(firstPose.xformVbo()) && !GL15.glIsBuffer(secondPose.xformVbo()) : "Iris pose VBO leaked on unload";
        } finally {
            GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL30.glBindVertexArray(0);GL20.glUseProgram(0);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,0);
            GL15.glDeleteBuffers(feedback);GL20.glDeleteProgram(program);
            MemoryUtil.memFree(vertices);MemoryUtil.memFree(captured);
            net.everla.reimaginedysm.gpu.GpuRenderPath.disposeMesh(geometry);
        }
        System.out.println("PASS: 120 queued production skin draws, "+geometry.bakedBones.size()+" bones / "+mesh.vertexCount+" vertices, isolated SSBO/Iris outputs, poses, visibility and unload cleanup");
    }
    private static void hybrid() {
        var geometry=model(bone("ysmPhys#bc=true,s=0,r=3,air=0,a=180@cpu",-1,0,0,0),
                bone("ysmPhysEnd@cpu",0,16,0,0),bone("decoration",0,8,4,0),
                bone("ysmPhys#s=0,r=3,air=0,a=180@gpu",-1,32,0,0),bone("ysmPhysEnd@gpu",3,48,0,0),
                bone("decoration",3,40,4,0));
        var d=definition(geometry);
        assert GpuPhysics.supportsDefinition(d);
        for(int fps:new int[]{30,60,120}) {
            var full=new PhysSimulation(d);var partial=new PhysSimulation(d);full.setFrameRate(fps);partial.setFrameRate(fps);
            float[] expectedPose=parameters(geometry),pose=parameters(geometry);partial.partition(pose,true);
            var clock=new GpuPhysics.Clock();
            try(var topology=new GpuPhysModel(d,new boolean[d.bones.size()]);var kernel=new GpuPhysKernel(topology,1)) {
                assert topology.trees==1;
                for(int frame=0;frame<150;frame++) {
                    double time=frame/60.0,x=time*3;
                    double y=frame<60?Math.max(0,4*time-5*time*time):0;
                    var root=new Matrix4f().translate(0,.25f,0);
                    var floor=net.minecraft.world.phys.shapes.Shapes.create(new net.minecraft.world.phys.AABB(-100,-1,-100,100,0,100));
                    boolean paused=frame>=90 && frame<95;
                    full.update(expectedPose,root,time,paused,WORLD,x,y,0,bounds->List.of(floor));
                    partial.update(pose,root,time,paused,WORLD,x,y,0,bounds->List.of(floor));
                    clock.advance(time,paused,WORLD,x,y,0,fps,false);
                    kernel.solve(pose,root,root,List.of(),clock.steps,clock.reset,1f/fps,clock.velocity,1234,clock.alpha());
                    var expected=matrices(d,expectedPose,root);
                    var data=read(kernel.output,d.bones.size()*144);
                    try {
                        for(int i=0;i<d.bones.size();i++) {
                            var actual=new Matrix4f(data.asFloatBuffer().position(i*36));
                            for(var point:List.of(d.pivot(i),d.pivot(i).add(.2f,.3f,.1f)))
                                assert actual.transformPosition(new Vector3f(point)).distance(expected[i].transformPosition(new Vector3f(point)))<.001f
                                        : "hybrid matrix mismatch frame="+frame+" bone="+i;
                        }
                    } finally {MemoryUtil.memFree(data);}
                    kernel.matrices(pose,root,1234);
                }
            }
            partial.partition(pose,false);
            full.update(expectedPose,new Matrix4f(),3,false,WORLD,0,0,0);
            partial.update(pose,new Matrix4f(),3,false,WORLD,0,0,0);
            for(int i=0;i<pose.length;i++) assert Math.abs(pose[i]-expectedPose[i])<1e-5 : "hybrid fallback reset mismatch";
        }
        System.out.println("PASS: hybrid CPU terrain + GPU independent tree, ordinary descendants, sprint/jump, pause, repeated matrices and fallback");
    }

    private static void clock() {
        var builder=new net.minecraftforge.common.ForgeConfigSpec.Builder();PhysConfig.define(builder,false);builder.build();
        var localBuilder=new net.minecraftforge.common.ForgeConfigSpec.Builder();PhysConfig.define(localBuilder,true);localBuilder.build();
        assert !PhysConfig.EXPERIMENTAL_GPU.getDefault() && !PhysConfig.Local.EXPERIMENTAL_GPU.getDefault();
        assert PhysConfig.EXPERIMENTAL_GPU!=PhysConfig.Local.EXPERIMENTAL_GPU;
        var clock=new GpuPhysics.Clock();
        clock.advance(0,false,WORLD,0,0,0,60,false);assert clock.reset && clock.steps==0;
        clock.advance(1.0/120,false,WORLD,0,0,0,60,false);assert !clock.reset && clock.steps==0;
        clock.advance(1.0/60,false,WORLD,0,0,1,60,false);assert clock.steps==1 && Math.abs(clock.velocity.z-120)<1e-4;
        clock.advance(1.0/60,false,WORLD,0,0,1,60,false);assert clock.steps==0;
        clock.advance(.1,true,WORLD,0,0,1,60,false);assert clock.steps==0 && clock.velocity.lengthSquared()==0;
        clock.advance(.11,false,WORLD,0,0,1,120,false);assert clock.reset;
        clock.advance(.12,false,new Object(),0,0,1,120,false);assert clock.reset;
        clock.advance(.13,false,WORLD,100,0,1,120,false);assert clock.reset;
        clock.advance(1,false,WORLD,100,0,1,120,false);assert clock.reset;
        clock.advance(.5,false,WORLD,100,0,1,120,false);assert clock.reset;
    }
    private static GeoModel fixture(int scenario) {
        // Non-parent-first import order and a plain descendant are intentional.
        var m=scenario==1?model(bone("ysmPhys@a",2,-4,-8,0),bone("ysmPhys@b",2,4,-8,0),
                bone("ysmPhys@fork",-1,0,0,0),bone("decoration",0,-4,-18,0),
                bone("ysmPhysEnd@a",0,-4,-16,0),bone("ysmPhysEnd@b",1,4,-16,0)):
                model(bone("ysmPhysEnd@end",2,0,-20,0),bone("ysmPhys@root",-1,0,0,0),
                        bone("ysmPhys@child",1,0,-10,0),bone("decoration",2,0,-24,0),bone("ysmPhysC@body",-1,0,0,0));
        if(scenario==5) {
            m=model(bone("ysmPhysEnd@a",2,0,-20,0),bone("ysmPhys@root",-1,0,0,0),
                    bone("ysmPhys@child",1,0,-10,0),bone("decoration",2,0,-24,0),
                    bone("ysmPhys@other",-1,16,0,0),bone("ysmPhysEnd@other",4,16,-16,0));
        }
        if(scenario>=2 && scenario<=4) {
            float[] faces=cube();for(int i=0;i<faces.length;i+=3) {faces[i]*=.2f;faces[i+1]=faces[i+1]*.2f-.65f;faces[i+2]=faces[i+2]*.2f+.15f;}
            m.bakedBones.get(4).physicsFaces.add(faces);
        }
        m.ysmPhysics=definition(m);return m;
    }
    private static GeoModel loadModel(String path) throws Exception {
        try(var reader=new com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer(java.nio.file.Path.of(path))) {
            var geometry=reader.deserialize().mainEntity.mainModel;
            var method=com.elfmcys.yesstevemodel.resource.YSMClientMapper.class.getDeclaredMethod("buildMesh",
                    com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel.RawGeometry.class,
                    com.elfmcys.yesstevemodel.resource.models.GeometryDescription.class,int.class,
                    com.elfmcys.yesstevemodel.resource.YSMClientMapper.TranslucencyScanner.class,boolean.class);
            method.setAccessible(true);
            return (GeoModel)method.invoke(null,geometry,com.elfmcys.yesstevemodel.resource.YSMClientMapper.buildContext(geometry),1,null,false);
        }
    }
    private static List<PhysCollider> contacts(PhysDefinition d,float[] p,Matrix4f root) {
        var matrices=matrices(d,p,root);var result=new ArrayList<PhysCollider>();
        for(var cube:d.cubes) result.add(new PhysCollider(cube.faces(),matrices[cube.bone()]));
        return result;
    }
    private static Matrix4f[] matrices(PhysDefinition d,float[] p,Matrix4f root) {
        Matrix4f[] out=new Matrix4f[d.bones.size()];
        for(int i:d.order) {int parent=d.bones.get(i).parentIdx;out[i]=RenderUtils.prepMatrixForBone(d.bones.get(i),new Matrix4f(parent<0?root:out[parent]),p,i*12);}
        return out;
    }
    private static ByteBuffer read(int buffer,int bytes) {
        ByteBuffer out=MemoryUtil.memAlloc(bytes);GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);
        GL42.glMemoryBarrier(GL43.GL_BUFFER_UPDATE_BARRIER_BIT);GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,out);return out;
    }
    private static void parity(GeoModel geometry,int fps,int scenario) {parity(geometry,fps,scenario,false);}
    private static void parity(GeoModel geometry,int fps,int scenario,boolean prototype) {
        var d=geometry.ysmPhysics;
        var cpu=new PhysSimulation(d);cpu.setFrameRate(fps);
        var gpuParameters=parameters(geometry);var cpuParameters=gpuParameters.clone();
        var partial=new PhysSimulation(d);partial.setFrameRate(fps);
        if(!prototype) partial.partition(gpuParameters,true);
        var clock=new GpuPhysics.Clock();
        float worst=0;
        try(var topology=new GpuPhysModel(d,new boolean[d.bones.size()],prototype?d.blockCollisionIslands():d.cpuCollisionIslands());var kernel=new GpuPhysKernel(topology,1)) {
            for(int frame=0;frame<100;frame++) {
                Matrix4f root=new Matrix4f();
                if(scenario>=3) root.rotateX(.4f).scale(.7f);
                if(scenario==6) root.scale(1,1.2f,.9f);
                cpu.restore(cpuParameters);
                if(!prototype) partial.restore(gpuParameters);
                if(!prototype) for(var cube:d.cubes) {
                    int at=cube.bone()*12;
                    cpuParameters[at+2]=gpuParameters[at+2]=.15f*(float)Math.sin(frame*.1);
                    cpuParameters[at+9]=gpuParameters[at+9]=frame>=20 && frame<27?1:0;
                }
                // Pausing, repeated drawing, teleports, and backwards movement.
                boolean paused=frame>=30 && frame<35;double time=frame/60.0,z=scenario>=4?-4*time:0;
                if(!prototype && scenario>=4 && frame>50) z=-4*(100-frame)/60.0;
                if(frame>=70) z+=100;
                Object world=frame<80?WORLD:geometry;
                clock.advance(time,paused,world,0,0,z,fps,false);
                cpu.update(cpuParameters,root,time,paused,world,0,0,z);
                if(!prototype) partial.update(gpuParameters,root,time,paused,world,0,0,z);
                kernel.solve(gpuParameters,root,root,prototype?contacts(d,gpuParameters,root):List.of(),clock.steps,clock.reset,1f/fps,clock.velocity,1234,clock.alpha());
                var expected=matrices(d,cpuParameters,root);
                ByteBuffer output=read(kernel.output,d.bones.size()*GpuPhysKernel.RESULT_BYTES);
                try {
                    Matrix4f[] actualMatrices=new Matrix4f[d.bones.size()];
                    for(int i=0;i<d.bones.size();i++) {
                        Matrix4f actual=new Matrix4f(output.asFloatBuffer().position(i*36));
                        actualMatrices[i]=actual;
                        Vector3f p=actual.transformPosition(d.pivot(i)),q=expected[i].transformPosition(d.pivot(i));
                        assert p.isFinite() : "non-finite GPU pose";
                        float error=p.distance(q);worst=Math.max(worst,error);
                        assert error<(d.cubes.isEmpty()?.001f:.015f) : "GPU pose mismatch fps="+fps+" scenario="+scenario+" frame="+frame+" bone="+i+" error="+error+" actual="+p+" expected="+q;
                        assert output.getInt(i*144+128)==1234;
                    }
                    for(var joint:d.joints) for(int end:joint.outputs()) {
                        float expectedLength=expected[joint.bone()].transformPosition(d.pivot(joint.bone())).distance(expected[end].transformPosition(d.pivot(end)));
                        float actualLength=actualMatrices[joint.bone()].transformPosition(d.pivot(joint.bone())).distance(actualMatrices[end].transformPosition(d.pivot(end)));
                        assert Math.abs(expectedLength-actualLength)<.001f : "GPU bone stretched";
                    }
                } finally {MemoryUtil.memFree(output);}
                if(frame==25) {
                    ByteBuffer before=read(kernel.state,kernel.nodeCount*GpuPhysKernel.NODE_BYTES);
                    kernel.matrices(gpuParameters,new Matrix4f().translate(1,2,3),4321);
                    ByteBuffer after=read(kernel.state,kernel.nodeCount*GpuPhysKernel.NODE_BYTES);
                    try {
                        for(int i=0;i<kernel.nodeCount;i++) for(int j=0;j<16;j++) // position, velocity, target and correction
                            assert before.getInt(i*256+j*4)==after.getInt(i*256+j*4) : "repeated draw advanced simulation";
                    } finally {MemoryUtil.memFree(before);MemoryUtil.memFree(after);}
                }
            }
        }
        System.out.printf(Locale.ROOT,"GPU parity: %d joints, %d cubes, %d Hz, scenario %d, max pivot error %.6f blocks%n",d.joints.size(),d.cubes.size(),fps,scenario,worst);
    }
    private static void visibilityAndState() throws Exception {
        var contactModel=fixture(3);
        assert GpuPhysics.supportsDefinition(contactModel.ysmPhysics) : "body contacts disabled";
        assert !GpuPhysics.supportedScale(contactModel.ysmPhysics,parameters(contactModel),new Matrix4f().scale(.7f,1.2f,.9f));
        assert GpuPhysics.supportedScale(contactModel.ysmPhysics,parameters(contactModel),new Matrix4f().scale(.7f));
        assert !GpuPhysics.supportedScale(contactModel.ysmPhysics,parameters(contactModel),new Matrix4f().scale(-1,1,1));
        boolean[] contacts=contactModel.ysmPhysics.cpuCollisionIslands();
        for(var joint:contactModel.ysmPhysics.joints) assert contacts[joint.bone()] : "unvalidated GPU body solver enabled";
        var terrainOnly=model(bone("ysmPhys#bc=true@root",-1,0,0,0),bone("ysmPhysEnd@end",0,16,0,0));
        assert !GpuPhysics.supportsDefinition(definition(terrainOnly)) : "all-terrain model must report CPU physics";
        var geometry=fixture(0);var d=geometry.ysmPhysics;float[] p=parameters(geometry);
        p[2*12+9]=1; // hide self, keep ordinary child visible
        try(var topology=new GpuPhysModel(d,new boolean[d.bones.size()]);var kernel=new GpuPhysKernel(topology,1)) {
            kernel.solve(p,new Matrix4f(),new Matrix4f(),List.of(),0,true,1f/60,ZERO,1);
            ByteBuffer out=read(kernel.output,d.bones.size()*144);
            try {assert out.getInt(2*144+132)==1 && out.getInt(3*144+132)==0 && out.getInt(132)==1;}
            finally {MemoryUtil.memFree(out);}
            int sentinel=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,sentinel);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,4096,GL15.GL_DYNAMIC_COPY);
            int alignment=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
            GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,2,sentinel,alignment,256);
            try(var saved=new GpuPhysKernel.SavedState()) {kernel.matrices(p,new Matrix4f(),2);}
            assert GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,2)==sentinel;
            assert GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,2)==alignment;
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,2,0);GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,0);GL15.glDeleteBuffers(sentinel);
            assert !GpuPhysics.requiresCpuPose(geometry,topology.affected,p);
            p[2*12+11]=1;assert GpuPhysics.requiresCpuPose(geometry,topology.affected,p);
        }
    }
    /** Exercise the real Iris vertex-transform shader with GPU-local physics matrices. */
    private static void irisMatrices() throws Exception {
        var geometry=fixture(1);var d=geometry.ysmPhysics;var p=parameters(geometry);
        var root=new Matrix4f().translate(2,3,-4).rotateXYZ(.3f,-.5f,.2f).scale(.7f);
        int shader=ShaderUtil.linkProgram(ShaderUtil.compileShaderFromResource(GL43.GL_COMPUTE_SHADER,"/bone_xform.csh"));
        int input=GL15.glGenBuffers(),output=GL15.glGenBuffers();
        ByteBuffer vertices=MemoryUtil.memCalloc(32),local=null,world=null,result=null;
        try(var saved=new GpuPhysKernel.SavedState();var model=new GpuPhysModel(d,new boolean[d.bones.size()]);var kernel=new GpuPhysKernel(model,1)) {
            vertices.putFloat(.2f).putFloat(.3f).putFloat(.1f).putFloat(0).putFloat(0)
                    .putInt(511<<10).putInt(0).putInt(0).flip();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,input);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,vertices,GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,output);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,36,GL15.GL_DYNAMIC_COPY);
            kernel.solve(p,root,root,List.of(),0,true,1f/60,ZERO,1234);
            for(int frame=0;frame<10;frame++) kernel.solve(p,root,root,List.of(),1,false,1f/60,ZERO,1234);
            world=read(kernel.output,d.bones.size()*144);
            kernel.matrices(p,new Matrix4f(),1234);
            local=read(kernel.output,d.bones.size()*144);
            for(int i=0;i<d.bones.size();i++) {
                var actual=new Matrix4f(local.asFloatBuffer().position(i*36));
                var expected=new Matrix4f(world.asFloatBuffer().position(i*36));
                var point=new Vector3f(.2f,.3f,.1f);
                assert root.transformPosition(actual.transformPosition(new Vector3f(point)))
                        .distance(expected.transformPosition(new Vector3f(point)))<.001f : "Iris applies root twice";
            }
            GL20.glUseProgram(shader);
            GL20.glUniform4f(GL20.glGetUniformLocation(shader,"u_color"),1,1,1,1);
            GL20.glUniform1i(GL20.glGetUniformLocation(shader,"u_packedOverlay"),0);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(shader,"u_modelView"),false,root.get(new float[16]));
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,input);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,output);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,2,kernel.output);
            GL43.glDispatchCompute(1,1,1);GL42.glMemoryBarrier(GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            result=read(output,36);
            var actual=new Vector3f(result.getFloat(0),result.getFloat(4),result.getFloat(8));
            var expected=new Matrix4f(local.asFloatBuffer()).transformPosition(new Vector3f(.2f,.3f,.1f));
            assert actual.distance(expected)<.001f : "Iris vertex transform differs from local physical pose";
            assert GL11.glGetError()==GL11.GL_NO_ERROR : "Iris compute GL error";
        } finally {
            MemoryUtil.memFree(vertices);if(local!=null) MemoryUtil.memFree(local);if(world!=null) MemoryUtil.memFree(world);if(result!=null) MemoryUtil.memFree(result);
            GL15.glDeleteBuffers(input);GL15.glDeleteBuffers(output);GL20.glDeleteProgram(shader);
        }
        System.out.println("PASS: Iris local physics SSBO, actual vertex-transform shader and root applied once");
    }

    /** GPU physics outputs are also reused by main/shadow draws without CPU waits. */
    private static void queuedPhysicsMatrices() {
        var geometry=fixture(1);var d=geometry.ysmPhysics;
        int draws=120,bytesPerDraw=d.bones.size()*16;
        int program=ShaderUtil.linkProgramWith(p->GL30.glTransformFeedbackVaryings(p,new CharSequence[]{"gl_Position"},GL30.GL_INTERLEAVED_ATTRIBS),
                ShaderUtil.compileShader(GL20.GL_VERTEX_SHADER,"""
                    #version 430 core
                    struct Bone {mat4 transform;mat4 normal;ivec4 flags;};
                    layout(std430,binding=0) readonly buffer Bones {Bone bones[];};
                    void main(){gl_Position=bones[gl_VertexID].transform*vec4(.2,.3,.1,1);}
                    ""","queued physics consumer"));
        int vao=GL30.glGenVertexArrays(),feedback=GL15.glGenBuffers();
        var captured=MemoryUtil.memAlloc(draws*bytesPerDraw);
        var expected=new ArrayList<Matrix4f[]>();
        try(var topology=new GpuPhysModel(d,new boolean[d.bones.size()]);
            var first=new GpuPhysKernel(topology,1);var second=new GpuPhysKernel(topology,1)) {
            var parameters=parameters(geometry);
            first.solve(parameters,new Matrix4f(),new Matrix4f(),List.of(),0,true,1f/60,ZERO,1);
            second.solve(parameters,new Matrix4f(),new Matrix4f(),List.of(),0,true,1f/60,ZERO,1);
            GL30.glBindVertexArray(vao);GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,feedback);
            GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,(long)draws*bytesPerDraw,GL15.GL_STREAM_READ);
            for(int draw=0;draw<draws;draw++) {
                var kernel=draw%3==0?second:first;
                for(int bone=0;bone<d.bones.size();bone++) parameters[bone*12+2]=.3f*(float)Math.sin(draw*.17+bone);
                var root=new Matrix4f().translate(draw*.1f,draw%3,-2).rotateY(draw*.03f);
                expected.add(matrices(d,parameters,root));
                kernel.matrices(parameters,root,1);
                GL20.glUseProgram(program);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,kernel.output);
                GL30.glBindBufferRange(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,feedback,(long)draw*bytesPerDraw,bytesPerDraw);
                GL30.glBeginTransformFeedback(GL11.GL_POINTS);GL11.glDrawArrays(GL11.GL_POINTS,0,d.bones.size());GL30.glEndTransformFeedback();
            }
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,feedback);
            GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,captured);
            for(int draw=0;draw<draws;draw++) for(int bone=0;bone<d.bones.size();bone++) {
                var wanted=expected.get(draw)[bone].transform(new org.joml.Vector4f(.2f,.3f,.1f,1));
                var actual=new org.joml.Vector4f(captured.asFloatBuffer().position((draw*d.bones.size()+bone)*4));
                assert actual.equals(wanted,.001f) : "queued physics output was overwritten draw="+draw+" bone="+bone;
            }
            assert GL11.glGetError()==GL11.GL_NO_ERROR;
        } finally {
            GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL30.glBindVertexArray(0);GL20.glUseProgram(0);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,0);
            GL15.glDeleteBuffers(feedback);GL30.glDeleteVertexArrays(vao);GL20.glDeleteProgram(program);MemoryUtil.memFree(captured);
        }
        System.out.println("PASS: 120 queued GPU physics matrix/consumer draws, two instances and repeated passes, no intermediate readbacks");
    }

    private static void benchmark(GeoModel geometry,int count) throws Exception {
        var d=geometry.ysmPhysics;float[] p=parameters(geometry);var root=new Matrix4f();var contacts=contacts(d,p,root);
        int drawProgram=ShaderUtil.linkProgram(ShaderUtil.compileShader(GL20.GL_VERTEX_SHADER,"""
                #version 430 core
                struct Bone {mat4 transform;mat4 normal;ivec4 flags;};
                layout(std430,binding=0) readonly buffer Bones {Bone bones[];};
                void main(){gl_Position=bones[gl_VertexID].transform*vec4(0,0,0,1);gl_PointSize=1;}
                ""","physics benchmark consumer"),ShaderUtil.compileShader(GL20.GL_FRAGMENT_SHADER,"""
                #version 430 core
                out vec4 color;void main(){color=vec4(1);}
                ""","physics benchmark fragment"));
        int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        try(var topology=new GpuPhysModel(d,new boolean[d.bones.size()],d.blockCollisionIslands());var kernel=new GpuPhysKernel(topology,count)) {
            PhysSimulation[] cpu=new PhysSimulation[count];float[][] poses=new float[count][];
            for(int i=0;i<count;i++) {cpu[i]=new PhysSimulation(d);cpu[i].setFrameRate(60);poses[i]=p.clone();}
            for(int frame=0;frame<30;frame++) for(int i=0;i<count;i++) cpu[i].update(poses[i],root,frame/60.0,false,WORLD,0,0,0);
            long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();
            for(int frame=30;frame<60;frame++) for(int i=0;i<count;i++) {cpu[i].update(poses[i],root,frame/60.0,false,WORLD,0,0,0);matrices(d,poses[i],root);}
            double cpuMs=(System.nanoTime()-start)/30e6,kb=(bean.getThreadAllocatedBytes(Thread.currentThread().getId())-allocated)/30.0/1024;
            int[] queries=new int[30];for(int i=0;i<queries.length;i++) queries[i]=GL15.glGenQueries();
            kernel.solve(p,root,root,contacts,0,true,1f/60,ZERO,0);
            for(int frame=0;frame<10;frame++) kernel.solve(p,root,root,contacts,1,false,1f/60,ZERO,0);
            GL11.glFinish();start=System.nanoTime();
            for(int frame=0;frame<queries.length;frame++) {
                GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,queries[frame]);
                kernel.solve(p,root,root,contacts,1,false,1f/60,ZERO,0);
                GL20.glUseProgram(drawProgram);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,kernel.output);
                GL11.glDrawArrays(GL11.GL_POINTS,0,d.bones.size()*count);
                GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            }
            double submission=(System.nanoTime()-start)/30e6;GL11.glFlush();
            // Read availability first: querying an unavailable result would serialize every frame.
            long deadline=System.nanoTime()+15_000_000_000L;
            while(GL15.glGetQueryObjecti(queries[queries.length-1],GL15.GL_QUERY_RESULT_AVAILABLE)==0) {
                if(System.nanoTime()>deadline) throw new AssertionError("GPU timer query timed out");
                Thread.onSpinWait();
            }
            double[] samples=new double[queries.length];
            for(int i=0;i<queries.length;i++) {samples[i]=GL33.glGetQueryObjectui64(queries[i],GL15.GL_QUERY_RESULT)/1e6;GL15.glDeleteQueries(queries[i]);}
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT,"BENCH %d instances x %d joints / %d cubes: CPU solve+matrices %.3f ms, alloc %.1f KiB/frame; batched GPU upload+solve+matrices+consumer draw median %.3f ms, CPU submission %.3f ms%n",count,d.joints.size(),d.cubes.size(),cpuMs,kb,samples[15],submission);
        } finally {GL20.glUseProgram(0);GL20.glDeleteProgram(drawProgram);GL30.glBindVertexArray(0);GL30.glDeleteVertexArrays(vao);}
    }
}

package net.everla.ysmPhysLib;

import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/** Per-instance CPU solver. Positions are world-oriented but rebased near the player. */
public final class PhysSimulation {
    private static final double STEP_SECONDS=1.0/120;
    public static final float STEP=(float)STEP_SECONDS;
    private double stepSeconds=STEP_SECONDS;
    private float stepDuration=STEP;
    private final PhysDefinition definition;
    private final Vector3f[] positions, velocities, targets;
    private final Quaternionf[] rotations, previousRotations;
    private List<PhysDefinition.Joint> activeJoints;
    private boolean[] active;
    private boolean[] enabled;
    private boolean hybrid;
    private float[] base;
    private Matrix4f currentRoot;
    private boolean applied;
    private double lastTime=Double.NaN, accumulator;
    private Object world;
    private double originX,originY,originZ;
    private boolean initialized;
    private final Vector3f frameVelocity=new Vector3f();
    private final List<PhysCollider> colliders=new ArrayList<>();
    private java.util.function.Function<AABB,Iterable<VoxelShape>> blockCollisions;
    private record BlockContacts(AABB bounds, List<PhysCollider> contacts) {}
    private final java.util.Map<Integer,BlockContacts> blockContacts=new java.util.HashMap<>();

    public PhysSimulation(PhysDefinition definition) {
        this.definition=definition;
        activeJoints=definition.joints;
        int count=definition.bones.size();
        positions=new Vector3f[count];velocities=new Vector3f[count];targets=new Vector3f[count];rotations=new Quaternionf[count];
        for(int i=0;i<count;i++) {positions[i]=new Vector3f();velocities[i]=new Vector3f();targets[i]=new Vector3f();rotations[i]=new Quaternionf();}
        previousRotations=new Quaternionf[count];
        for(int i=0;i<count;i++) previousRotations[i]=new Quaternionf();
    }

    /** Must run before animation evaluation, including evaluation throttling and UI renders. */
    public void restore(float[] parameters) {
        if(!applied) return;
        for(var joint:activeJoints) System.arraycopy(base,joint.bone()*12,parameters,joint.bone()*12,3);
        applied=false;
    }

    void partition(float[] parameters, boolean hybrid) {
        if(this.hybrid==hybrid) return;
        restore(parameters);
        this.hybrid=hybrid;
        rebuildActive();
        invalidate();
    }

    public void setEnabled(float[] parameters, boolean[] enabled) {
        if(java.util.Arrays.equals(this.enabled,enabled)) return;
        restore(parameters);
        this.enabled=enabled.clone();
        rebuildActive();
        invalidate();
    }

    private void rebuildActive() {
        active=hybrid?definition.cpuCollisionIslands():new boolean[definition.bones.size()];
        if(!hybrid) java.util.Arrays.fill(active,true);
        if(enabled!=null) for(int i=0;i<active.length;i++) active[i]&=enabled[i];
        activeJoints=definition.joints.stream().filter(j->active[j.bone()]).toList();
    }

    public void invalidate() {initialized=false;lastTime=Double.NaN;accumulator=0;}

    /** Changing cadence starts from the current animation, without old-step debt. */
    public void setFrameRate(int fps) {
        if(fps<1 || fps>120) throw new IllegalArgumentException("Physics FPS must be 1..120");
        double duration=1.0/fps;
        if(duration!=stepSeconds) {
            stepSeconds=duration;stepDuration=(float)duration;
            invalidate();
        }
    }

    public void update(float[] parameters, Matrix4f root, double time, boolean paused, Object currentWorld,
                       double x, double y, double z) {
        update(parameters,root,time,paused,currentWorld,x,y,z,null);
    }

    /** Block queries are supplied only by the world-player adapter, never by UI previews. */
    public void update(float[] parameters, Matrix4f root, double time, boolean paused, Object currentWorld,
                       double x, double y, double z, java.util.function.Function<AABB,Iterable<VoxelShape>> blockCollisions) {
        if(activeJoints.isEmpty()) return;
        restore(parameters);
        base=parameters.clone();
        currentRoot=root;
        if(!root.isFinite() || Math.abs(root.determinant())<1e-12f) {invalidate();return;}
        Matrix4f[] matrices=new Matrix4f[positions.length];
        for(int i:definition.order) {
            int parent=definition.bones.get(i).parentIdx;
            matrices[i]=RenderUtils.prepMatrixForBone(definition.bones.get(i),new Matrix4f(parent<0?root:matrices[parent]),base,i*12);
        }
        for(int i=0;i<targets.length;i++) matrices[i].transformPosition(definition.pivot(i),targets[i]);
        Vector3f shift=new Vector3f((float)(originX-x),(float)(originY-y),(float)(originZ-z));
        boolean reset=!initialized || world!=currentWorld || !Double.isFinite(time) || time<lastTime || time-lastTime>.25 || shift.lengthSquared()>16;
        if(initialized && !reset) {
            // Root-relative state moves with its reference frame; absolute velocity
            // below preserves inertia without applying the full frame jump per substep.
            for(var joint:activeJoints) if(joint.parent()<0 && positions[joint.bone()].distanceSquared(targets[joint.bone()])>16) reset=true;
        }
        originX=x;originY=y;originZ=z;world=currentWorld;
        if(reset) {
            for(int i=0;i<positions.length;i++) {positions[i].set(targets[i]);velocities[i].zero();rotations[i].identity();previousRotations[i].identity();}
            accumulator=0;initialized=true;
        }
        double elapsed=Double.isFinite(lastTime)?Math.max(0,time-lastTime):0;
        if(!reset && !paused && elapsed>1e-9) frameVelocity.set(shift).mul((float)(-1/elapsed));
        else frameVelocity.zero();
        lastTime=time;
        if(!paused && !reset && elapsed>0) {
            this.blockCollisions=blockCollisions;
            blockContacts.clear();
            colliders.clear();
            boolean[] visible=visibility(base);
            for(var cube:definition.cubes) if(visible[cube.bone()]) colliders.add(new PhysCollider(cube.faces(),matrices[cube.bone()]));
            accumulator=Math.min(accumulator+elapsed,stepSeconds*8.0);
            for(int step=0;step<8 && accumulator+1e-9>=stepSeconds;step++) {
                for(var joint:activeJoints) previousRotations[joint.bone()].set(rotations[joint.bone()]);
                integrate();
                for(int iteration=0;iteration<8;iteration++) {
                    constrain();
                    collide(matrices);
                }
                // Positions and velocities are projected separately. Geometric
                // correction must never be converted into an outward impulse.
                constrain();
                accumulator-=stepSeconds;
            }
        }
        reconstruct(parameters);
        this.blockCollisions=null;
        blockContacts.clear();
        applied=true;
    }

    private boolean[] visibility(float[] p) {
        boolean[] visible=new boolean[positions.length], children=new boolean[positions.length];
        for(int i:definition.order) {
            int parent=definition.bones.get(i).parentIdx;
            boolean ancestor=parent<0 || children[parent];
            boolean scaled=p[i*12+6]!=0 && p[i*12+7]!=0 && p[i*12+8]!=0;
            visible[i]=ancestor && p[i*12+9]==0 && scaled;
            children[i]=ancestor && p[i*12+10]==0 && scaled;
        }
        return visible;
    }

    private void integrate() {
        for(var joint:activeJoints) {
            if(joint.parent()<0) {positions[joint.bone()].set(targets[joint.bone()]);velocities[joint.bone()].set(frameVelocity);}
            PhysParameters p=joint.parameters();
            for(int end:joint.outputs()) {
                Vector3f acceleration=new Vector3f(targets[end]).sub(positions[end]).mul(p.s());
                acceleration.y-=p.g();
                velocities[end].sub(frameVelocity).mul((float)Math.exp(-p.r()*stepDuration)).add(frameVelocity).fma(stepDuration,acceleration);
                applyAirDrag(velocities[end],p.air(),stepDuration);
                positions[end].fma(stepDuration,new Vector3f(velocities[end]).sub(frameVelocity));
                if(!positions[end].isFinite() || !velocities[end].isFinite()) {positions[end].set(targets[end]);velocities[end].zero();}
            }
        }
    }

    /** Exact velocity update for dv/dt = -air * |v| * v in still world-space air. */
    public static void applyAirDrag(Vector3f velocity, float air, float dt) {
        double speed=Math.sqrt((double)velocity.x*velocity.x+(double)velocity.y*velocity.y+(double)velocity.z*velocity.z);
        velocity.mul((float)(1/(1+(double)air*speed*dt)));
    }

    private void constrain() {
        pose(base.clone(),currentRoot);
    }

    private void collide(Matrix4f[] matrices) {
        Vector3f scale=new Vector3f();
        for(var joint:activeJoints) {
            matrices[joint.bone()].getScale(scale);
            float radius=joint.parameters().c()/16f*Math.max(scale.x,Math.max(scale.y,scale.z));
            for(int end:joint.outputs()) for(PhysCollider collider:joint.parameters().bc() && blockCollisions!=null?contacts(joint,
                    new AABB(positions[joint.bone()].x,positions[joint.bone()].y,positions[joint.bone()].z,
                            positions[end].x,positions[end].y,positions[end].z).inflate(radius)):colliders) {
                Vector3f startBefore=new Vector3f(positions[joint.bone()]), endBefore=new Vector3f(positions[end]);
                resolveContact(collider,positions[joint.bone()],positions[end],velocities[joint.bone()],velocities[end],radius,joint.parent()<0,frameVelocity);
                if(joint.parent()>=0 && positions[joint.bone()].distanceSquared(startBefore)<radius*radius*.01f
                        && positions[end].distanceSquared(endBefore)<radius*radius*.01f) {
                    // Under-relax shallow shared-node contacts to avoid alternating
                    // with the parent's length constraint. Deep contacts stay hard.
                    positions[joint.bone()].lerp(startBefore,.5f);
                    positions[end].lerp(endBefore,.5f);
                }
                if(!positions[end].isFinite() || !velocities[end].isFinite()) {positions[end].set(targets[end]);velocities[end].zero();}
            }
        }
    }

    /** Body colliders share the player's translating frame; terrain does not. */
    static void resolveContact(PhysCollider collider, Vector3f start, Vector3f end, Vector3f startVelocity,
                               Vector3f endVelocity, float radius, boolean fixedStart, Vector3f frameVelocity) {
        if(!collider.worldBlock) {startVelocity.sub(frameVelocity);endVelocity.sub(frameVelocity);}
        collider.resolveSegment(start,end,startVelocity,endVelocity,radius,fixedStart);
        if(!collider.worldBlock) {startVelocity.add(frameVelocity);endVelocity.add(frameVelocity);}
    }

    private void reconstruct(float[] output) {
        // Interpolate only the displayed correction; never feed it back into constraints.
        float alpha=(float)Math.max(0,Math.min(1,accumulator/stepSeconds));
        for(var joint:activeJoints) {
            int i=joint.bone();
            Quaternionf local=new Quaternionf().rotationZYX(base[i*12+2],base[i*12+1],base[i*12]);
            Vector3f euler=PhysMath.eulerZYX(new Quaternionf(previousRotations[i]).nlerp(rotations[i],alpha).mul(local).normalize());
            if(euler.isFinite()) {output[i*12]=euler.x;output[i*12+1]=euler.y;output[i*12+2]=euler.z;}
        }
    }

    private void pose(float[] output, Matrix4f root) {
        Matrix4f[] actual=new Matrix4f[positions.length];
        for(int i:definition.order) {
            int parent=definition.bones.get(i).parentIdx;
            Matrix4f before=parent<0?root:actual[parent];
            PhysDefinition.Joint joint=active==null || active[i]?definition.jointByBone[i]:null;
            if(joint!=null) {
                Matrix4f animated=RenderUtils.prepMatrixForBone(definition.bones.get(i),new Matrix4f(before),base,i*12);
                Vector3f pivot=animated.transformPosition(definition.pivot(i),new Vector3f());
                List<Vector3f> from=new ArrayList<>(),to=new ArrayList<>();
                for(int end:joint.outputs()) {
                    Matrix4f child=RenderUtils.prepMatrixForBone(definition.bones.get(end),new Matrix4f(animated),base,end*12);
                    from.add(child.transformPosition(definition.pivot(end),new Vector3f()).sub(pivot));
                    to.add(new Vector3f(positions[end]).sub(pivot));
                }
                // Fit in the parent's pre-scale coordinates: nonuniform model scaling must
                // not be misinterpreted as a bone rotation.
                Matrix4f inverseParent=new Matrix4f(before).invert();
                for(Vector3f v:from) inverseParent.transformDirection(v);
                for(Vector3f v:to) inverseParent.transformDirection(v);
                Quaternionf correction=PhysMath.limit(PhysMath.fit(from,to,rotations[i]),joint.parameters().a());
                // Resolve contacts on the actual, length-preserving bone pose. Pushing
                // free particle endpoints alone is insufficient: reconstruction used to
                // project that displacement away and put the visible segment back inside.
                Vector3f scale=animated.getScale(new Vector3f());
                float radius=joint.parameters().c()/16f*Math.max(scale.x,Math.max(scale.y,scale.z));
                List<PhysCollider> contacts=colliders;
                if(joint.parameters().bc() && blockCollisions!=null) {
                    float reach=0;
                    for(Vector3f source:from) reach=Math.max(reach,source.length());
                    // Frobenius norm bounds every rotated direction, including nonuniform parent scaling.
                    float bound=before.getScale(new Vector3f()).length()*reach+radius;
                    contacts=contacts(joint,new AABB(pivot.x,pivot.y,pivot.z,pivot.x,pivot.y,pivot.z).inflate(bound));
                }
                // A discrete solver may propose a clear pose on the opposite side
                // of a thin body. Stop at the first contact along the rotation from
                // the previous pose, rather than accepting that tunneled endpoint.
                Quaternionf previous=rotations[i];
                if(!contacts.isEmpty() && clearPose(from,previous,before,pivot,radius,contacts)) {
                    Quaternionf safe=new Quaternionf(previous);
                    float angle=2*(float)Math.acos(Math.min(1,Math.abs(previous.dot(correction))));
                    int samples=Math.max(1,Math.min(180,(int)Math.ceil(angle/Math.toRadians(1))));
                    for(int sample=1;sample<=samples;sample++) {
                        Quaternionf candidate=new Quaternionf(previous).slerp(correction,sample/(float)samples);
                        if(!clearPose(from,candidate,before,pivot,radius,contacts)) {
                            Quaternionf blocked=candidate;
                            for(int refinement=0;refinement<10;refinement++) {
                                Quaternionf midpoint=new Quaternionf(safe).slerp(blocked,.5f);
                                if(clearPose(from,midpoint,before,pivot,radius,contacts)) safe.set(midpoint);
                                else blocked.set(midpoint);
                            }
                            correction=safe;
                            break;
                        }
                        safe.set(candidate);
                    }
                }
                for(int contactPass=0;contactPass<24 && !contacts.isEmpty();contactPass++) {
                    List<Vector3f> desired=new ArrayList<>();
                    boolean contact=false;
                    int outputIndex=0;
                    for(Vector3f source:from) {
                        int end=joint.outputs()[outputIndex++];
                        Vector3f endpoint=new Vector3f(source).rotate(correction);
                        before.transformDirection(endpoint).add(pivot);
                        Vector3f contactVelocity=new Vector3f(velocities[end]).sub(frameVelocity);
                        for(PhysCollider collider:contacts) {
                            // Body blocks move with the player; terrain is stationary in world space.
                            if(collider.worldBlock) contactVelocity.add(frameVelocity);
                            contact |= collider.resolveSegment(new Vector3f(pivot),endpoint,new Vector3f(),contactVelocity,radius,true);
                            if(collider.worldBlock) contactVelocity.sub(frameVelocity);
                        }
                        velocities[end].set(contactVelocity).add(frameVelocity);
                        desired.add(inverseParent.transformDirection(endpoint.sub(pivot)));
                    }
                    if(!contact) break;
                    correction=PhysMath.limit(PhysMath.fit(from,desired,correction),joint.parameters().a());
                }
                rotations[i].set(correction);
                Quaternionf local=new Quaternionf().rotationZYX(base[i*12+2],base[i*12+1],base[i*12]);
                correction.mul(local).normalize();
                Vector3f euler=PhysMath.eulerZYX(correction);
                if(euler.isFinite()) {output[i*12]=euler.x;output[i*12+1]=euler.y;output[i*12+2]=euler.z;}
            }
            actual[i]=RenderUtils.prepMatrixForBone(definition.bones.get(i),new Matrix4f(before),output,i*12);
            if(joint!=null) {
                actual[i].transformPosition(definition.pivot(i),positions[i]);
                if(joint.parent()<0) velocities[i].set(frameVelocity);
                for(int end:joint.outputs()) {
                    Matrix4f child=RenderUtils.prepMatrixForBone(definition.bones.get(end),new Matrix4f(actual[i]),base,end*12);
                    child.transformPosition(definition.pivot(end),positions[end]);
                    Vector3f direction=new Vector3f(positions[end]).sub(positions[i]);
                    if(direction.lengthSquared()>1e-12f) {
                        direction.normalize();
                        float radial=new Vector3f(velocities[end]).sub(velocities[i]).dot(direction);
                        velocities[end].fma(-radial,direction);
                    }
                    if(!positions[end].isFinite()) {positions[end].set(targets[end]);velocities[end].zero();}
                }
            }
        }
    }

    public Vector3f position(int bone) {return new Vector3f(positions[bone]);}

    private List<PhysCollider> contacts(PhysDefinition.Joint joint,AABB bounds) {
        if(!joint.parameters().bc() || blockCollisions==null) return colliders;
        var cached=blockContacts.get(joint.bone());
        if(cached!=null && contains(cached.bounds,bounds)) return cached.contacts;
        // Cache per joint and simulation frame. Enclose all candidate rotations, not just the free tip.
        AABB query=(cached==null?bounds:cached.bounds.minmax(bounds)).inflate(.01);
        if(!Double.isFinite(query.getSize())) return colliders;
        var result=new ArrayList<>(colliders);
        for(VoxelShape shape:blockCollisions.apply(query.move(originX,originY,originZ)))
            for(AABB box:shape.toAabbs()) if(box.getXsize()>0 && box.getYsize()>0 && box.getZsize()>0)
                result.add(new PhysCollider(box.move(-originX,-originY,-originZ)));
        blockContacts.put(joint.bone(),new BlockContacts(query,result));
        return result;
    }

    private static boolean contains(AABB outer,AABB inner) {
        return outer.minX<=inner.minX && outer.minY<=inner.minY && outer.minZ<=inner.minZ
                && outer.maxX>=inner.maxX && outer.maxY>=inner.maxY && outer.maxZ>=inner.maxZ;
    }

    private boolean clearPose(List<Vector3f> sources, Quaternionf rotation, Matrix4f parent, Vector3f pivot, float radius,List<PhysCollider> contacts) {
        for(Vector3f source:sources) {
            Vector3f end=parent.transformDirection(new Vector3f(source).rotate(rotation)).add(pivot);
            for(PhysCollider collider:contacts)
                if(collider.resolveSegment(new Vector3f(pivot),new Vector3f(end),new Vector3f(),new Vector3f(),radius,true)) return false;
        }
        return true;
    }
}

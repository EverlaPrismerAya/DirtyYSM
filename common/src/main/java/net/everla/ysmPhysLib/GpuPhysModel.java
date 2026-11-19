package net.everla.ysmPhysLib;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

/** Immutable, parent-first GPU topology. Shared by all instances of a model. */
final class GpuPhysModel implements AutoCloseable {
    final PhysDefinition definition;
    final int[] original, sorted;
    final boolean[] affected;
    final int trees;
    final int buffer;
    int users;

    GpuPhysModel(PhysDefinition definition, boolean[] glowing) {
        this.definition=definition;
        original=definition.order.clone();sorted=new int[original.length];affected=definition.affected;
        for(int i=0;i<original.length;i++) sorted[original[i]]=i;
        int[] first=new int[original.length], next=new int[original.length];
        java.util.Arrays.fill(first,-1);java.util.Arrays.fill(next,-1);
        for(var joint:definition.joints) {
            int previous=-1;
            for(int end:joint.outputs()) {
                if(previous<0) first[joint.bone()]=sorted[end];else next[previous]=sorted[end];
                previous=end;
            }
        }
        boolean[] ends=new boolean[original.length];for(int end:definition.ends) ends[end]=true;
        int[] island=definition.islands();
        boolean[] cpu=definition.blockCollisionIslands();
        int treeCount=0;
        int[] remap=new int[original.length];java.util.Arrays.fill(remap,-1);
        for(int i:original) {
            if(cpu[i]) island[i]=-1;
            else if(island[i]>=0) {
                int group=island[i];
                if(remap[group]<0) remap[group]=treeCount++;
                island[i]=remap[group];
            }
        }
        trees=Math.max(1,treeCount);
        ByteBuffer data=MemoryUtil.memCalloc(original.length*80);
        try {
            for(int i:original) {
                int parent=definition.bones.get(i).parentIdx;
                var joint=cpu[i]?null:definition.jointByBone[i];
                var pivot=definition.pivot(i);
                data.putFloat(pivot.x).putFloat(pivot.y).putFloat(pivot.z).putFloat(i);
                data.putInt(parent<0?-1:sorted[parent]).putInt(first[i]).putInt(next[i])
                        .putInt(joint==null?-2:joint.parent()<0?-1:sorted[joint.parent()]);
                var p=joint==null?PhysParameters.DEFAULT:joint.parameters();
                data.putFloat(p.r()).putFloat(p.g()).putFloat(p.s()).putFloat(p.a());
                data.putFloat(p.c()).putFloat(p.air()).putFloat(glowing[i]?1:0).putFloat(ends[i]?1:0);
                data.putInt(island[i]).putInt(i).putInt(glowing[i]?1:0).putInt(0);
            }
            data.flip();buffer=GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,data,GL15.GL_STATIC_DRAW);
            if(GL15.glGetBufferParameteri(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)!=data.remaining()) {
                GL15.glDeleteBuffers(buffer);throw new IllegalStateException("GPU physics topology allocation failed");
            }
        } finally {MemoryUtil.memFree(data);}
    }

    @Override public void close() {GL15.glDeleteBuffers(buffer);}
}

package net.everla.reimaginedysm.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import com.elfmcys.yesstevemodel.YesSteveModel;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public final class IrisRenderPath {
    private static final float[] modelViewScratch = new float[16];


    public static boolean tryRender(GeoModel model, PoseStack.Pose pose, float[] boneParams, int renderPartMask, int packedLight, int packedOverlay, float r, float g, float b, float a, ResourceLocation textureLocation) {
        if (!GpuCapability.isAvailable()) return false;
        int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try(var saved=new ShaderUtil.SavedComputeState(3)) {
            return render(model,pose,boneParams,renderPartMask,packedLight,packedOverlay,r,g,b,a,textureLocation);
        } catch(Exception | LinkageError | OutOfMemoryError failure) {
            YesSteveModel.LOGGER.error("Iris GPU draw failed; using CPU pose and fallback rendering",failure);
            net.everla.ysmPhysLib.GpuPhysics.fallback(boneParams);
            return false;
        } finally {
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER,array);
            com.mojang.blaze3d.vertex.BufferUploader.invalidate();
        }
    }

    private static boolean render(GeoModel model,PoseStack.Pose pose,float[] boneParams,int renderPartMask,
                                  int packedLight,int packedOverlay,float r,float g,float b,float a,ResourceLocation textureLocation) {

        if (!BoneXformCompute.ensureCompiled()) return false;
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return false;

        GpuMesh mesh = GpuRenderPath.getOrBuildMesh(model);
        if (mesh == null) return false;
        mesh.ensureXformBuffers();


        ByteBuffer boneBuf = mesh.perFrameBoneBuffer;
        boneBuf.clear();
        // Iris consumes model-local matrices; the entity shader applies pose once.
        int physicsBuffer=net.everla.ysmPhysLib.GpuPhysics.render(boneParams,new Matrix4f(),packedLight);
        if(physicsBuffer==0) {
            float[] cpuParams=net.everla.ysmPhysLib.PlayerPhysics.hideEndpoints(model,boneParams);
            GeoModel.nComputeBoneMatricesLocal(mesh.pointer,cpuParams,packedLight,boneBuf);
            GpuRenderPath.updateBoneVisibility(model.bakedBones,cpuParams,boneBuf);
            boneBuf.position(0);boneBuf.limit(mesh.boneCount*144);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,mesh.boneSsbo);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0L,boneBuf);
        }

        GlStateManager._glUseProgram(BoneXformCompute.program());
        if (BoneXformCompute.locColor() >= 0) GL20.glUniform4f(BoneXformCompute.locColor(), r, g, b, a);
        if (BoneXformCompute.locOverlay() >= 0) GL20.glUniform1i(BoneXformCompute.locOverlay(), packedOverlay);

        if (BoneXformCompute.locModelView() >= 0) {
            pose.pose().get(modelViewScratch);
            GL20.glUniformMatrix4fv(BoneXformCompute.locModelView(), false, modelViewScratch);
        }

        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, mesh.vbo);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, mesh.xformVbo());
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, physicsBuffer==0?mesh.boneSsbo:physicsBuffer);

        GL43.glDispatchCompute(BoneXformCompute.dispatchGroupCount(mesh.vertexCount), 1, 1);

        GL43.glMemoryBarrier(GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        checkError("vertex transform");

        RenderType rt = RenderType.entityCutoutNoCull(textureLocation);
        ShaderInstance shader=null;
        try {
            rt.setupRenderState();
            shader=RenderSystem.getShader();
            if(shader==null) return false;
            if(shader.MODEL_VIEW_MATRIX!=null) shader.MODEL_VIEW_MATRIX.set(pose.pose());
            if(shader.PROJECTION_MATRIX!=null) shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
            if(shader.COLOR_MODULATOR!=null) shader.COLOR_MODULATOR.set(1.0f,1.0f,1.0f,1.0f);
            if(shader.GLINT_ALPHA!=null) shader.GLINT_ALPHA.set(1.0f);
            shader.apply();
            GlStateManager._glBindVertexArray(mesh.xformVao());
            int drawCount=mesh.indexDrawCount(renderPartMask);
            if(drawCount>0) GL11.glDrawElements(GL11.GL_TRIANGLES,drawCount,GL11.GL_UNSIGNED_INT,mesh.indexOffsetBytes(renderPartMask));
            checkError("draw");
            return true;
        } finally {
            try {if(shader!=null) shader.clear();}
            finally {rt.clearRenderState();}
        }
    }
    private static void checkError(String stage) {
        int error=GL11.glGetError();
        if(error!=GL11.GL_NO_ERROR) throw new IllegalStateException("Iris "+stage+" GL error 0x"+Integer.toHexString(error));
    }
}

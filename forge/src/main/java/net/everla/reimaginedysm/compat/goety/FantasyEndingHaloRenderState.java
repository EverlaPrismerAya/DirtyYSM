package net.everla.reimaginedysm.compat.goety;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Keeps DomainOfFade's immediate draws out of the pending player/world batches. */
public final class FantasyEndingHaloRenderState implements AutoCloseable {
    private static MultiBufferSource.BufferSource isolatedBuffers;
    private static boolean active;
    private final boolean wasActive = active;
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

    public FantasyEndingHaloRenderState() {
        if (isolatedBuffers == null) isolatedBuffers = MultiBufferSource.immediate(new BufferBuilder(256));
        active = true;
    }

    public static MultiBufferSource.BufferSource bufferSource(RenderBuffers worldBuffers) {
        return active ? isolatedBuffers : worldBuffers.bufferSource();
    }

    public static boolean isActive() {
        return active;
    }

    @Override
    public void close() {
        try {
            isolatedBuffers.endBatch();
        } finally {
            active = wasActive;
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            // GlowRenderLayer uses raw GL depth calls, bypassing Minecraft's state cache.
            // Force both cached values through a different value before restoring them.
            RenderSystem.depthMask(!depthMask);
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc == GL11.GL_LESS ? GL11.GL_LEQUAL : GL11.GL_LESS);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }
}

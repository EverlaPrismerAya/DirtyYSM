package net.everla.reimaginedysm.compat.goety;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Opt-in driver regression: shared pending batches, cached/raw depth state and actual occlusion. */
public final class FantasyEndingHaloRenderCheck {
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(32, 32, "FantasyEnding render check", 0, 0);
        if (window == 0) throw new AssertionError("OpenGL context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            RenderSystem.initRenderThread();
            buffers();
            states();
            occlusion();
            assert GL11.glGetError() == GL11.GL_NO_ERROR;
            System.out.println("PASS: FantasyEnding buffer isolation, exception cleanup, raw/cached GL state and depth occlusion on "
                    + GL11.glGetString(GL11.GL_RENDERER));
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void buffers() {
        var world = new RenderBuffers();
        var type = new CountingType();
        assert FantasyEndingHaloRenderState.bufferSource(world) == world.bufferSource();
        world.bufferSource().getBuffer(type).vertex(0, 0, 0).endVertex();
        try (var scope = new FantasyEndingHaloRenderState()) {
            var halo = FantasyEndingHaloRenderState.bufferSource(world);
            assert halo != world.bufferSource();
            halo.getBuffer(type).vertex(0, 0, 0).endVertex();
            halo.endBatch(type); // Native polygon/star helpers flush individual render types.
            halo.endBatch(); // Native Curios renderer flushes everything before returning.
            assert type.draws == 1 : "native flush drained pending world/player geometry";
        }
        assert type.draws == 1;
        assert FantasyEndingHaloRenderState.bufferSource(world) == world.bufferSource();
        world.bufferSource().endBatch();
        assert type.draws == 2 : "pending world geometry lost";
    }

    private static void states() {
        for (int mask = 0; mask < 16; mask++) {
            boolean depth = (mask & 1) != 0, write = (mask & 2) != 0;
            boolean blend = (mask & 4) != 0, cull = (mask & 8) != 0;
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(write);
            RenderSystem.depthFunc(GL11.GL_GREATER);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            var failure = new IllegalStateException("native renderer failed");
            try (var scope = new FantasyEndingHaloRenderState()) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.depthFunc(GL11.GL_GREATER);
                // GlowRenderLayer bypasses the Minecraft cache; desired and cached values can match.
                GL11.glDepthMask(!write);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                RenderSystem.disableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                throw failure;
            } catch (IllegalStateException caught) {
                assert caught == failure;
            }
            assert GL11.glIsEnabled(GL11.GL_DEPTH_TEST) == depth;
            assert GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) == write;
            assert GL11.glGetInteger(GL11.GL_DEPTH_FUNC) == GL11.GL_GREATER;
            assert GL11.glIsEnabled(GL11.GL_BLEND) == blend;
            assert GL11.glIsEnabled(GL11.GL_CULL_FACE) == cull;
            assert GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL11.GL_ONE;
            assert GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) == GL11.GL_ZERO;
            assert GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA) == GL11.GL_ZERO;
            assert GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) == GL11.GL_ONE;
            RenderSystem.depthMask(!write);
            assert GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) != write : "depth cache desynchronized";
        }
    }

    private static void occlusion() {
        int vertex = shader(GL20.GL_VERTEX_SHADER, """
                #version 330 core
                uniform float z;
                const vec2 p[3] = vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));
                void main() { gl_Position=vec4(p[gl_VertexID],z,1); }
                """);
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, """
                #version 330 core
                uniform vec3 color;
                out vec4 outColor;
                void main() { outColor=vec4(color,1); }
                """);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        assert GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0 : GL20.glGetProgramInfoLog(program);
        int vao = GL30.glGenVertexArrays();
        var pixel = MemoryUtil.memAlloc(4);
        try {
            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(program);
            GL11.glViewport(0, 0, 32, 32);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LESS);
            GL11.glClearDepth(1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            draw(program, -.5f, 0, 1); // Front face is green.
            try (var scope = new FantasyEndingHaloRenderState()) {
                RenderSystem.disableDepthTest();
                GL11.glDepthMask(false);
                GL11.glDepthFunc(GL11.GL_ALWAYS);
            }
            draw(program, .5f, 1, 0); // Rear face must stay behind it after the halo render.
            GL11.glReadPixels(16, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            assert (pixel.get(0) & 255) == 0 && (pixel.get(1) & 255) == 255 : "rear face overwrote front face";
        } finally {
            MemoryUtil.memFree(pixel);
            GL30.glDeleteVertexArrays(vao);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    private static void draw(int program, float z, float red, float green) {
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "z"), z);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "color"), red, green, 0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static int shader(int kind, String source) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        assert GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0 : GL20.glGetShaderInfoLog(shader);
        return shader;
    }

    private static final class CountingType extends RenderType {
        int draws;
        CountingType() {
            super("halo_buffer_check", DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES,
                    256, false, false, () -> {}, () -> {});
        }
        @Override
        public void end(BufferBuilder builder, VertexSorting sorting) {
            builder.end().release();
            draws++;
        }
    }
}

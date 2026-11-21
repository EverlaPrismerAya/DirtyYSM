package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import net.everla.ysmPhysLib.WorldPhysicsContext;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;

/** Run with :common:checkRenderContext; no Minecraft instance or GL context needed. */
public final class RenderContextCheck {
    public static void main(String[] args) throws Exception {
        FacingCameraSplitCheck.run();
        for (float projectionY : new float[] {-1, 1}) {
            Matrix4f root = new Matrix4f().rotateZ((float) Math.PI).rotateX(-.2f)
                    .rotateY(.7f).scale(-32, -32, 32);
            Matrix4f bone = new Matrix4f().translate(1, 2, 3);
            var pivot = new Matrix4f(root).mul(bone).getTranslation(new org.joml.Vector3f());
            FacingCameraTransform.apply(bone, FacingCameraTransform.previewToCamera(root, projectionY));
            Matrix4f view = new Matrix4f(root).mul(bone);
            assert view.getTranslation(new org.joml.Vector3f()).distance(pivot) < 1e-4f;
            assert view.m00() > 0 && view.m11() * projectionY > 0 : "preview billboard upside down";
            // A local positive Z turn must have the same projected handedness
            // in GUI (Y down) and perspective/first-person (Y up).
            view.rotateZ(.3f);
            assert view.m01() * projectionY > 0 : "preview spin reversed";
        }
        // Camera yaw/pitch, entity rotation, a rotated/nonuniformly scaled parent,
        // and nested billboard groups must retain their pivots and face the view.
        for (int i = 0; i < 20; i++) {
            Matrix4f root = new Matrix4f().rotateXYZ(i * .13f, i * .27f, .2f).scale(-.7f, -.7f, .7f);
            Matrix4f bone = new Matrix4f().translate(2, 3, 4).rotateXYZ(.4f, .7f, .1f).scale(2, 3, 4).translate(1, 2, 3);
            org.joml.Vector3f pivot = new Matrix4f(root).mul(bone).getTranslation(new org.joml.Vector3f());
            FacingCameraTransform.apply(bone, root);
            Matrix4f view = new Matrix4f(root).mul(bone);
            assert view.getTranslation(new org.joml.Vector3f()).distance(pivot) < 1e-4f;
            assert Math.abs(view.m01()) + Math.abs(view.m02()) + Math.abs(view.m10())
                    + Math.abs(view.m12()) + Math.abs(view.m20()) + Math.abs(view.m21()) < 1e-4f;
            Matrix4f nested = new Matrix4f(bone).translate(1, 0, 0).rotateY(.9f);
            FacingCameraTransform.apply(nested, root);
            assert Math.abs(new Matrix4f(root).mul(nested).m20()) < 1e-4f;
        }
        Matrix4f hidden = new Matrix4f().scale(0);
        FacingCameraTransform.apply(hidden, new Matrix4f().scale(0));
        assert hidden.isFinite();
        var body = new BufferBuilder(256);
        var outline = new BufferBuilder(256);
        assert NativeModelRenderer.isGpuBuffer(body, 1) : "ordinary model lost GPU rendering";
        assert !NativeModelRenderer.isGpuBuffer(VertexMultiConsumer.create(outline, body), 1)
                : "glowing entity bypassed its outline consumer";
        assert !NativeModelRenderer.isGpuBuffer(body, 0) : "transparent body became an opaque GPU draw";
        assert !NativeModelRenderer.isGpuBuffer(body, .15f) : "translucent body lost buffered blending";
        // ToroHealth calls the dispatcher directly, without either preview flag.
        // First-person arms outside the world pass must also use buffered drawing.
        assert NativeModelRenderer.isPreview() : "unmarked inventory/HUD/arm draw reached GPU path";
        assert !NativeModelRenderer.isGpuPass(false) : "off-world draw entered GPU path";
        assert NativeModelRenderer.isGpuPass(false, true) : "global mode excluded off-world previews";
        assert NativeModelRenderer.isGpuPass(true, true) : "global mode excluded first-person rendering";
        assert !NativeModelRenderer.isGpuPass(false, false) : "disabled global mode changed default routing";
        WorldPhysicsContext.begin(new Matrix4f());
        try {
            assert !NativeModelRenderer.isPreview() : "world entity lost GPU rendering";
            assert NativeModelRenderer.isGpuPass(false) : "ordinary world entity lost GPU rendering";
            assert !NativeModelRenderer.isGpuPass(true) : "FirstPerson body entered GPU path";
            assert !NativeModelRenderer.isPreview() : "FirstPerson fallback disabled world physics/head hiding";
            WorldPhysicsContext.begin(new Matrix4f().rotateY(.5f));
            try {assert !NativeModelRenderer.isPreview() : "shadow world entity lost GPU rendering";}
            finally {WorldPhysicsContext.end();}
            ModelPreviewRenderer.setPreviewMode(true);
            try {assert !NativeModelRenderer.isGpuPass(false) : "explicit preview entered GPU path";}
            finally {ModelPreviewRenderer.setPreviewMode(false);}
            ModelPreviewRenderer.setExtraPlayerMode(true);
            try {assert !NativeModelRenderer.isGpuPass(false) : "paper doll entered GPU path";}
            finally {ModelPreviewRenderer.setExtraPlayerMode(false);}
            assert !NativeModelRenderer.isPreview() : "nested preview changed world rendering";
        } finally {WorldPhysicsContext.end();}
        assert NativeModelRenderer.isPreview() : "world scope leaked into UI";
        System.out.println("PASS: GPU world/shadow gate; outline/multi-consumers and transparent bodies retain buffered rendering");
    }
}

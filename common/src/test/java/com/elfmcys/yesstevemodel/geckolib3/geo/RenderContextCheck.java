package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import net.everla.ysmPhysLib.WorldPhysicsContext;
import org.joml.Matrix4f;

/** Run with :common:checkRenderContext; no Minecraft instance or GL context needed. */
public final class RenderContextCheck {
    public static void main(String[] args) {
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
        System.out.println("PASS: GPU world/shadow gate; FirstPerson body, inventory, ToroHealth HUD, paper doll and arms use CPU rendering");
    }
}

package com.elfmcys.yesstevemodel.geckolib3.geo;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Spherical billboard at the bone pivot, before its own scale and pivot offset. */
final class FacingCameraTransform {
    static Matrix4f previewToCamera(Matrix4f root, float projectionY) {
        // GUI projection has downward-positive Y. Keep local +Y up and +Z
        // toward the viewer, as in the world camera's billboard coordinates.
        return projectionY < 0
                ? new Matrix4f().rotationX((float) Math.PI).mul(root)
                : new Matrix4f(root);
    }

    static void apply(Matrix4f bone, Matrix4f modelToCamera) {
        // Hidden/zero-scale ancestors cannot be inverted and have no visible surface.
        if (Math.abs(modelToCamera.determinant()) < 1e-12f) return;
        Matrix4f view = new Matrix4f(modelToCamera).mul(bone);
        Vector3f scale = view.getScale(new Vector3f());
        if (view.determinant() < 0) scale.x = -scale.x;
        view.m00(scale.x).m01(0).m02(0)
                .m10(0).m11(scale.y).m12(0)
                .m20(0).m21(0).m22(scale.z);
        new Matrix4f(modelToCamera).invert().mul(view, bone);
    }
}

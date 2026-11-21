package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.resource.models.GeometryDescription;
import net.everla.reimaginedysm.gpu.GpuMeshBuilder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

final class FacingCameraSplitCheck {
    static void run() throws Exception {
        // Child before parent; nested billboard; ordinary sibling and case-sensitive name.
        String[] names = {"child", "FacingCamera_spark", "body", "FacingCamera_nested", "facingCamera_plain"};
        int[] parents = {1, 2, -1, 0, 2};
        GeoBone[] bones = new GeoBone[names.length];
        for (int i = 0; i < bones.length; i++) {
            bones[i] = new GeoBone(names[i], false, false, false, 0, 0, 0, 0, 0, 0);
            bones[i].parentIdx = parents[i];
        }
        String[][] groups = new String[35][];
        Arrays.setAll(groups, i -> new String[0]);
        GeoModel model = new GeoModel(bones, groups, new boolean[3],
                new GeometryDescription("billboard_check", 16, 16, 4, 4, new double[3]), new boolean[1]);
        model.bakedBones = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            var bone = new GeoModel.BakedBone();
            bone.name = names[i];
            bone.parentIdx = parents[i];
            var cube = new GeoModel.BakedCube();
            var quad = new GeoModel.BakedQuad();
            quad.positions[0] = 100 + i;
            cube.quads.add(quad);
            bone.cubes.add(cube);
            model.bakedBones.add(bone);
        }
        GeoModel.classifyFacingCameraBones(model.bakedBones);
        assert model.hasFacingCameraBones;
        for (int i = 0; i < names.length; i++) {
            var bone = model.bakedBones.get(i);
            boolean javaDraw = i != 2 && i != 4;
            assert bone.facingCameraSubtree == javaDraw : names[i];
            // Exactly one draw owns each cube; the full Java fallback still retains all geometry.
            assert bone.acceleratedCubes().size() + (javaDraw ? bone.cubes.size() : 0) == 1;
            assert bone.cubes.size() == 1;
        }
        var nativeSerializer = GeoModel.class.getDeclaredMethod("serializeNativeModel");
        nativeSerializer.setAccessible(true);
        verify((ByteBuffer) nativeSerializer.invoke(model), true, parents);
        var gpuSerializer = GpuMeshBuilder.class.getDeclaredMethod("serializeModel", GeoModel.class);
        gpuSerializer.setAccessible(true);
        verify((ByteBuffer) gpuSerializer.invoke(null, model), false, parents);
        System.out.println("PASS: billboard subtree split; native/GPU caches retain skeleton and only ordinary geometry");
    }

    private static void verify(ByteBuffer data, boolean nativeFormat, int[] parents) {
        assert data.getInt() == parents.length;
        for (int i = 0; i < parents.length; i++) {
            assert data.getInt() == parents[i] : "bone indices changed";
            data.position(data.position() + 17); // part mask, glow, pivot
            int cubes = data.getInt();
            assert cubes == ((i == 2 || i == 4) ? 1 : 0) : "duplicated or missing geometry";
            if (cubes != 0) {
                data.get();
                assert data.getInt() == 1;
                if (nativeFormat) data.get();
                assert data.getFloat() == 100 + i;
                data.position(data.position() + 88);
            }
        }
        assert data.position() == data.capacity() : "incorrect serialized size";
    }
}

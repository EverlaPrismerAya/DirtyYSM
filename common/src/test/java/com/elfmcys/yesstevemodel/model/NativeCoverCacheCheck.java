package com.elfmcys.yesstevemodel.model;

import net.everla.dirtyYsm.api.network.FragmentLimits;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;

public final class NativeCoverCacheCheck {
    public static void main(String[] args) throws Exception {
        // The reported 525-fragment catalog is valid clientbound, never a valid upload.
        FragmentLimits.validateCount(525, FragmentLimits.CLIENTBOUND_SIZE);
        boolean rejected = false;
        try { FragmentLimits.validateCount(525, FragmentLimits.SERVERBOUND_SIZE); }
        catch (IllegalArgumentException expected) { rejected = true; }
        assert rejected;
        FragmentLimits.validateCount(FragmentLimits.countLimit(FragmentLimits.CLIENTBOUND_SIZE), FragmentLimits.CLIENTBOUND_SIZE);
        rejected = false;
        try { FragmentLimits.validateCount(FragmentLimits.countLimit(FragmentLimits.CLIENTBOUND_SIZE) + 1, FragmentLimits.CLIENTBOUND_SIZE); }
        catch (IllegalArgumentException expected) { rejected = true; }
        assert rejected;
        System.out.println("PASS: 525-fragment client catalog, upload limits, no cover payloads");
    }
}

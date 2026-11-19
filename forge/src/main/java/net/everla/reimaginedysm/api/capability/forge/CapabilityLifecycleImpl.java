package net.everla.reimaginedysm.api.capability.forge;

import net.minecraft.world.entity.Entity;

public final class CapabilityLifecycleImpl {

    private CapabilityLifecycleImpl() {
    }

    public static void revive(Entity entity) {
        entity.reviveCaps();
    }

    public static void invalidate(Entity entity) {
        entity.invalidateCaps();
    }
}

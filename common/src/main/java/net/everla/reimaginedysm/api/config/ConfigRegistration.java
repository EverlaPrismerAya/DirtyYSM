package net.everla.reimaginedysm.api.config;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistration {

    private ConfigRegistration() {
    }

    @ExpectPlatform
    public static void registerNamed(String modId, ModConfig.Type type, ForgeConfigSpec spec, String filename) { throw new AssertionError(); }

    @ExpectPlatform
    public static void register(String modId, ModConfig.Type type, ForgeConfigSpec spec) {
        throw new AssertionError();
    }
}

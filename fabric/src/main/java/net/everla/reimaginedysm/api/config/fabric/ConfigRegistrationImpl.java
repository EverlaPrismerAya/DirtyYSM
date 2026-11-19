package net.everla.reimaginedysm.api.config.fabric;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    public static void registerNamed(String modId, ModConfig.Type type, ForgeConfigSpec spec, String filename) {
        ForgeConfigRegistry.INSTANCE.register(modId, type, spec, filename);
    }

    public static void register(String modId, ModConfig.Type type, ForgeConfigSpec spec) {
        ForgeConfigRegistry.INSTANCE.register(modId, type, spec);
    }
}

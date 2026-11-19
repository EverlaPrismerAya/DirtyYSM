package net.everla.reimaginedysm.api.config.forge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    @SuppressWarnings({"deprecated","removal"})
    public static void registerNamed(String modId, ModConfig.Type type, ForgeConfigSpec spec, String filename) {
        ModLoadingContext.get().registerConfig(type, spec, filename);
    }

    public static void register(String modId, ModConfig.Type type, ForgeConfigSpec spec) {
        ModLoadingContext.get().registerConfig(type, spec);
    }
}

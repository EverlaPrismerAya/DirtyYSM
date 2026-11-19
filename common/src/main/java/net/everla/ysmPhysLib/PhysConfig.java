package net.everla.ysmPhysLib;

import com.elfmcys.yesstevemodel.config.ClientConfigScope;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings, isolated between normal and client-only profiles. */
public final class PhysConfig {
    public static ForgeConfigSpec.BooleanValue ENABLED;
    public static ForgeConfigSpec.IntValue FPS;
    public static ForgeConfigSpec.BooleanValue EXPERIMENTAL_GPU;
    public static final class Local {
        public static ForgeConfigSpec.BooleanValue ENABLED;
        public static ForgeConfigSpec.IntValue FPS;
        public static ForgeConfigSpec.BooleanValue EXPERIMENTAL_GPU;
    }
    private PhysConfig() {}

    public static void define(ForgeConfigSpec.Builder builder, boolean local) {
        builder.push("physics_groups");
        var enabled=builder.comment("Enable ysmPhys simulation for world players only.").define("Enabled",true);
        var fps=builder.comment("Physics updates per second (0 disables simulation). Lower rates use larger fixed steps.")
                .defineInRange("SimulationFPS",60,0,120);
        var gpu=builder.comment("Experimental OpenGL compute physics. Body colliders, unsupported hardware, shader packs and CPU pose consumers fall back to CPU. No guaranteed speedup.")
                .define("ExperimentalGPUAcceleration",false);
        if(local) {Local.ENABLED=enabled;Local.FPS=fps;Local.EXPERIMENTAL_GPU=gpu;}
        else {ENABLED=enabled;FPS=fps;EXPERIMENTAL_GPU=gpu;}
        builder.pop();
    }
    public static int frameRate() {
        return ClientConfigScope.effective(ENABLED).get()?ClientConfigScope.effective(FPS).get():0;
    }
    public static boolean gpuEnabled() {return ClientConfigScope.effective(EXPERIMENTAL_GPU).get();}
}

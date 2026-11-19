package net.everla.ysmPhysLib;

import com.elfmcys.yesstevemodel.config.ClientConfigScope;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings, isolated between normal and client-only profiles. */
public final class PhysConfig {
    public static ForgeConfigSpec.BooleanValue ENABLED;
    public static ForgeConfigSpec.IntValue FPS;
    public static final class Local {
        public static ForgeConfigSpec.BooleanValue ENABLED;
        public static ForgeConfigSpec.IntValue FPS;
    }
    private PhysConfig() {}

    public static void define(ForgeConfigSpec.Builder builder, boolean local) {
        builder.push("physics_groups");
        var enabled=builder.comment("Enable ysmPhys simulation for world players only.").define("Enabled",true);
        var fps=builder.comment("Physics updates per second (0 disables simulation). Lower rates use larger fixed steps.")
                .defineInRange("SimulationFPS",60,0,120);
        if(local) {Local.ENABLED=enabled;Local.FPS=fps;}
        else {ENABLED=enabled;FPS=fps;}
        builder.pop();
    }
    public static int frameRate() {
        return ClientConfigScope.effective(ENABLED).get()?ClientConfigScope.effective(FPS).get():0;
    }
}

package com.elfmcys.yesstevemodel.config;

import com.elfmcys.yesstevemodel.client.ClientOnlyMode;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.IdentityHashMap;
import java.util.Map;

/** Separate config objects, including GUI edits; FORCE_CLIENT_MODE is the shared mode selector. */
public final class ClientConfigScope {
    private static final Map<ForgeConfigSpec.ConfigValue<?>,ForgeConfigSpec.ConfigValue<?>> isolated=new IdentityHashMap<>();
    public static ForgeConfigSpec buildSpec() {
        ForgeConfigSpec spec=ClientOnlyGeneralConfig.buildSpec();
        bind(GeneralConfig.class,ClientOnlyGeneralConfig.class);
        bind(ExtraPlayerRenderConfig.class,ClientOnlyRenderConfig.class);
        bind(LoadingStateConfig.class,ClientOnlyLoadingConfig.class);
        return spec;
    }
    private static void bind(Class<?> normal,Class<?> local) {
        try {
            for(var field:normal.getFields()) if(ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType()) && !field.getName().equals("FORCE_CLIENT_MODE"))
                isolated.put((ForgeConfigSpec.ConfigValue<?>)field.get(null),(ForgeConfigSpec.ConfigValue<?>)local.getField(field.getName()).get(null));
        } catch(ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    @SuppressWarnings("unchecked")
    public static <T extends ForgeConfigSpec.ConfigValue<?>> T effective(T normal) {
        return ClientOnlyMode.isActive() ? (T)isolated.getOrDefault(normal,normal) : normal;
    }
}

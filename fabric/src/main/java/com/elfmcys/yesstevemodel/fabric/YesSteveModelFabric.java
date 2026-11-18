package com.elfmcys.yesstevemodel.fabric;

import com.elfmcys.yesstevemodel.RuntimePolicy;

import com.elfmcys.yesstevemodel.YesSteveModel;
import net.fabricmc.api.ModInitializer;

public final class YesSteveModelFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        if (RuntimePolicy.isDisabled()) {
            return;
        }
        YesSteveModel.init();
        FabricCapabilityHooks.register();
    }
}

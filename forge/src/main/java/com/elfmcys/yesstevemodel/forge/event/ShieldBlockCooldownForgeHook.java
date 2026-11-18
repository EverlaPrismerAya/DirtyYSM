package com.elfmcys.yesstevemodel.forge.event;

import com.elfmcys.yesstevemodel.RuntimePolicy;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.event.ShieldBlockCooldownEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YesSteveModel.MOD_ID)
public final class ShieldBlockCooldownForgeHook {

    private ShieldBlockCooldownForgeHook() {
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (RuntimePolicy.isDisabled()) {
            return;
        }
        ShieldBlockCooldownEvent.onShieldBlock(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (RuntimePolicy.isDisabled()) {
            return;
        }
        ShieldBlockCooldownEvent.onLivingTick(event.getEntity());
    }
}

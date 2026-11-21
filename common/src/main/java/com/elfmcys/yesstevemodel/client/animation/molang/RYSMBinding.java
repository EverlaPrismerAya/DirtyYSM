package com.elfmcys.yesstevemodel.client.animation.molang;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class RYSMBinding extends ContextBinding {
    public RYSMBinding() {
        playerEntityVar("longitudinal_speed", ctx -> getRelativeSpeed(ctx, false));
        playerEntityVar("lateral_speed", ctx -> getRelativeSpeed(ctx, true));
    }

    private static double getRelativeSpeed(IContext<Player> context, boolean lateral) {
        var tracker = context.geoInstance().getPositionTracker();
        float elapsedTicks = tracker.getTimeDelta();
        if (elapsedTicks <= 0) {
            return 0;
        }
        Vec3 delta = tracker.getPositionDelta();
        // View yaw, rather than body yaw; pitch does not change the horizontal axes.
        double yaw = Math.toRadians(context.entity().getViewYRot(context.animationEvent().getFrameTime()));
        double displacement = lateral
                ? delta.x * Math.cos(yaw) + delta.z * Math.sin(yaw)
                : -delta.x * Math.sin(yaw) + delta.z * Math.cos(yaw);
        double speed = displacement * 20.0 / elapsedTicks;
        // Minecraft's float/trig movement calculations can leave a small cross-axis component.
        double tolerance = Math.max(1.0E-6, Math.hypot(delta.x, delta.z) * 20.0 / elapsedTicks * 1.0E-4);
        return Math.abs(speed) <= tolerance ? 0 : speed;
    }
}

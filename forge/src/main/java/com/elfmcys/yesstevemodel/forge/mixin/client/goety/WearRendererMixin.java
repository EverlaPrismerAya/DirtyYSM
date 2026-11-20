package com.elfmcys.yesstevemodel.forge.mixin.client.goety;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.reimaginedysm.compat.goety.HeadWearRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.Polarice3.Goety.client.render.WearRenderer", remap = false)
public abstract class WearRendererMixin implements HeadWearRenderer {
    @Shadow(remap = false)
    private HumanoidModel<LivingEntity> getModel() { throw new AssertionError(); }

    @Shadow(remap = false)
    private void render(LivingEntity entity, ItemStack stack, PoseStack poses,
                        MultiBufferSource buffers, int light) { throw new AssertionError(); }

    @Override
    public void ysm$renderHead(LivingEntity entity, ItemStack stack, PoseStack poses,
                              MultiBufferSource buffers, int light, float age) {
        var model = getModel();
        model.setupAnim(entity, 0, 0, age, 0, 0);
        // The YSM locator already contains the animated head pose and player scale.
        model.head.resetPose();
        model.young = false;
        render(entity, stack, poses, buffers, light);
    }
}

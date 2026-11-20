package net.everla.reimaginedysm.compat.goety;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface HeadWearRenderer {
    void ysm$renderHead(LivingEntity entity, ItemStack stack, PoseStack poses,
                        MultiBufferSource buffers, int light, float age);
}

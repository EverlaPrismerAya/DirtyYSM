package net.everla.reimaginedysm.compat.youkai;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Uses Youkai's Homecoming's own Forge armor models and textures. */
public final class YoukaiHeadLayer extends GeoLayerRenderer<CustomPlayerEntity> {
    private final HumanoidModel<LivingEntity> defaultModel = new HumanoidModel<>(
            Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, CustomPlayerEntity entity,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        var player = entity.getEntity();
        var stack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (player.isInvisible() || !(stack.getItem() instanceof ArmorItem armor)
                || armor.getEquipmentSlot() != EquipmentSlot.HEAD
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("youkaishomecoming")) return;
        var geoModel = entity.getCurrentModel();
        if (geoModel == null) return;
        var locator = geoModel.youkaiHeadBones().isEmpty() ? geoModel.headBones() : geoModel.youkaiHeadBones();
        if (locator.isEmpty()) return;
        var model = IClientItemExtensions.of(stack).getHumanoidArmorModel(player, stack, EquipmentSlot.HEAD, defaultModel);
        String texture = stack.getItem().getArmorTexture(stack, player, EquipmentSlot.HEAD, null);
        if (texture == null) return;
        // Head rotation, position and scale already come from the YSM locator.
        model.head.resetPose();
        model.hat.resetPose();
        model.young = false;
        poses.pushPose();
        try {
            if (RenderUtils.prepMatrixForLocator(poses, locator)) return;
            poses.scale(-1, -1, 1);
            var consumer = ItemRenderer.getArmorFoilBuffer(buffers,
                    RenderType.armorCutoutNoCull(new ResourceLocation(texture)), false, stack.hasFoil());
            model.head.render(poses, consumer, light, OverlayTexture.NO_OVERLAY);
            model.hat.render(poses, consumer, light, OverlayTexture.NO_OVERLAY);
        } finally {
            poses.popPose();
        }
    }
}

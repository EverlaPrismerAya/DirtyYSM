package net.everla.reimaginedysm.compat.goety;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.MolangValue;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

import java.lang.reflect.Method;
import java.util.*;

/** Uses the installed mods' textures/renderers; no accessory assets are bundled. */
public final class GoetyAccessoryLayer extends GeoLayerRenderer<CustomPlayerEntity> {
    private static final Set<UUID> RENDERED = new HashSet<>();
    private static boolean rendering;
    private final Map<ModelAssembly, Set<String>> overrides = new WeakHashMap<>();
    private final ModelPart halo;
    private final PlayerModel<AbstractClientPlayer> parentModel;
    private final RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent;
    private Method spin;
    private Method meteoring;
    private boolean abilityResolved;

    public GoetyAccessoryLayer() {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("halo", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8, -8, 0, 16, 16, 0), PartPose.ZERO);
        halo = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("halo");
        parentModel = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        parent = new RenderLayerParent<>() {
            public PlayerModel<AbstractClientPlayer> getModel() { return parentModel; }
            public ResourceLocation getTextureLocation(AbstractClientPlayer player) { return player.getSkinTextureLocation(); }
        };
    }

    @SubscribeEvent
    public static void beginFrame(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) RENDERED.clear();
    }

    public static boolean rendered(Player player) { return RENDERED.contains(player.getUUID()); }
    public static boolean isRendering() { return rendering; }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, CustomPlayerEntity entity,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (!(entity.getEntity() instanceof AbstractClientPlayer player) || player.isInvisible() || player.isSpectator()) return;
        var model = entity.getCurrentModel();
        if (model == null) return;
        RENDERED.add(player.getUUID());
        var references = overrides.computeIfAbsent(entity.getModelAssembly(), GoetyAccessoryLayer::findOverrides);
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            // Equipment owns priority even when hidden or rendered by the model itself.
            int highestHalo = 0;
            for (var handler : inventory.getCurios().values()) {
                for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                    highestHalo = Math.max(highestHalo, haloPriority(BuiltInRegistries.ITEM.getKey(
                            handler.getStacks().getStackInSlot(i).getItem())));
                }
            }
            final int equippedHalo = highestHalo;
            inventory.getCurios().forEach((slot, handler) -> {
            for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                ItemStack stack = handler.getCosmeticStacks().getStackInSlot(i);
                boolean cosmetic = !stack.isEmpty();
                boolean visible = i < handler.getRenders().size() && handler.getRenders().get(i);
                if (!cosmetic && visible) stack = handler.getStacks().getStackInSlot(i);
                if (stack.isEmpty() || handledByModel(references, stack)
                        || handledByModel(entity.getCurioRenderQueries(), stack)) continue;
                var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (!isSupported(id)) continue;
                int priority = haloPriority(id);
                if (priority > 0 && priority < equippedHalo) continue;
                boolean bodyHalo = id.getPath().equals("halo_of_the_end");
                if (!bodyHalo && model.headBones().isEmpty()) continue;
                poses.pushPose();
                try {
                    if (bodyHalo) {
                        if (prepareBody(poses, model)) continue;
                    } else if (RenderUtils.prepMatrixForLocator(poses, model.headBones())) continue;
                    poses.scale(-1, -1, 1);
                    rendering = id.getPath().equals("halo_of_the_end");
                    renderAccessory(stack, id, new SlotContext(slot, player, i, cosmetic, visible), poses,
                            buffers, light, partialTick, age);
                } finally {
                    rendering = false;
                    poses.popPose();
                }
            }
            });
        });
    }

    private static boolean prepareBody(PoseStack poses,
            com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel model) {
        var bones = model.getGeoModel().topLevelBones();
        int body = -1;
        for (String name : List.of("AllBody", "Body", "body")) {
            for (int i = 0; i < bones.size(); i++) {
                if (bones.get(i).getName().equals(name)) {
                    body = i;
                    break;
                }
            }
            if (body >= 0) break;
        }
        // Keep the renderer's feet-origin coordinates; a locator would move it to a pivot.
        var chain = new ArrayList<Integer>();
        for (int i = body; i >= 0; i = bones.get(i).parentIdx) chain.add(i);
        for (int i = chain.size() - 1; i >= 0; i--) {
            var bone = model.bones().get(bones.get(chain.get(i)).getBoneId());
            if (RenderUtils.prepMatrixForBone(poses, bone)) return true;
        }
        return false;
    }

    private static int haloPriority(ResourceLocation id) {
        if (!id.getNamespace().equals("goety_revelation")) return 0;
        return switch (id.getPath()) {
            case "halo_of_the_end" -> 3;
            case "ascension_halo" -> 2;
            case "broken_halo" -> 1;
            default -> 0;
        };
    }

    public static boolean isSupported(ResourceLocation id) {
        return id.getNamespace().equals("goety") && Set.of("unholy_hat", "unholy_hat_halo").contains(id.getPath())
                || id.getNamespace().equals("goety_revelation")
                && Set.of("ascension_halo", "broken_halo", "halo_of_the_end").contains(id.getPath());
    }

    private static boolean handledByModel(Set<String> references, ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (references.contains(id)) return true;
        for (String reference : references) {
            if (reference.startsWith("#") && stack.is(TagKey.create(Registries.ITEM,
                    new ResourceLocation(reference.substring(1))))) return true;
        }
        return false;
    }

    private void renderAccessory(ItemStack stack, ResourceLocation id, SlotContext slot, PoseStack poses,
                                 MultiBufferSource buffers, int light, float partialTick, float age) {
        if (id.getNamespace().equals("goety")) {
            CuriosRendererRegistry.getRenderer(stack.getItem()).ifPresent(renderer -> {
                if (renderer instanceof HeadWearRenderer wear) wear.ysm$renderHead(slot.entity(), stack, poses, buffers, light, age);
            });
        } else if (id.getPath().equals("halo_of_the_end")) {
            // Keep RevelationFix's native animation, shader and health-dependent effects.
            parentModel.head.resetPose();
            parentModel.young = false;
            CuriosRendererRegistry.getRenderer(stack.getItem()).ifPresent(renderer -> renderer.render(stack, slot,
                    poses, parent, buffers, light, 0, 0, partialTick, age, 0, 0));
        } else {
            renderRevelationHalo(slot.entity(), id.getPath().equals("broken_halo"), poses, buffers, light);
        }
    }

    private void renderRevelationHalo(net.minecraft.world.entity.LivingEntity player, boolean broken,
                                      PoseStack poses, MultiBufferSource buffers, int light) {
        if (!abilityResolved) {
            abilityResolved = true;
            try {
                Class<?> ability = Class.forName("z1gned.goetyrevelation.util.PlayerAbilityHelper");
                spin = ability.getMethod("getSpin");
                meteoring = ability.getMethod("getMeteoring");
            } catch (ReflectiveOperationException e) {
                com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.warn("GR halo animation API unavailable", e);
            }
        }
        poses.translate(0, -12.0 / 16, 5.0 / 16);
        poses.mulPose(Axis.XP.rotation(0.7854F));
        String texture = broken ? "broken_halo" : "halo";
        if (!broken && spin != null && meteoring != null) {
            try {
                poses.mulPose(Axis.ZP.rotationDegrees(90 * ((Number) spin.invoke(player)).floatValue()));
                if (((Number) meteoring.invoke(player)).intValue() > 0) texture = "halo_second";
            } catch (ReflectiveOperationException | RuntimeException e) {
                spin = null;
                meteoring = null;
                com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.warn("GR halo animation API failed", e);
            }
        }
        halo.render(poses, buffers.getBuffer(RenderType.energySwirl(new ResourceLocation("goety_revelation",
                "textures/entity/player/" + texture + ".png"), 1, 1)), light, OverlayTexture.NO_OVERLAY);
    }

    private static void collect(Set<String> references, IValue value) {
        if (value instanceof MolangValue molang) references.addAll(molang.getCurioRenderReferences());
    }

    private static Set<String> findOverrides(ModelAssembly assembly) {
        Set<String> references = new HashSet<>();
        assembly.getAnimationBundle().getMainAnimations().values().forEach(animation -> {
            if (!animation.isFromPrimaryAssembly) references.addAll(animation.curioRenderReferences);
        });
        assembly.getAnimationBundle().getAnimationEntries().values().forEach(controller ->
                controller.getStates().values().forEach(state -> {
                    state.getAnimations().forEach(pair -> collect(references, pair.getValue()));
                    state.getTransitions().forEach(pair -> collect(references, pair.value()));
                    state.getPreExpressions().forEach(value -> collect(references, value));
                    state.getPostExpressions().forEach(value -> collect(references, value));
                }));
        assembly.getExpressionCache().getFunctions().values().forEach(value -> collect(references, value));
        assembly.getExpressionCache().getEvents().values().forEach(values -> values.forEach(value -> collect(references, value)));
        return Set.copyOf(references);
    }
}

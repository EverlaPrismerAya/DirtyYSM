package net.everla.reimaginedysm.compat;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.MolangValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Model-owned headwear, whether queried as armor or as a Curios item. */
public final class ModelAccessoryOverrides {
    private static final Map<ModelAssembly, Set<String>> OVERRIDES = new WeakHashMap<>();

    private ModelAccessoryOverrides() {}

    public static boolean handles(CustomPlayerEntity entity, ItemStack stack) {
        return matches(OVERRIDES.computeIfAbsent(entity.getModelAssembly(), ModelAccessoryOverrides::findOverrides), stack)
                || matches(entity.getCurioRenderQueries(), stack);
    }

    public static void recordHeadQuery(IContext<?> context, EquipmentSlot slot, ResourceLocation id, boolean tag) {
        if (slot == EquipmentSlot.HEAD && context.geoInstance() instanceof CustomPlayerEntity model) {
            model.recordCurioRenderQuery((tag ? "#" : "") + id);
        }
    }

    private static boolean matches(Set<String> references, ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (references.contains(id)) return true;
        for (String reference : references) {
            if (reference.startsWith("#") && stack.is(TagKey.create(Registries.ITEM,
                    new ResourceLocation(reference.substring(1))))) return true;
        }
        return false;
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

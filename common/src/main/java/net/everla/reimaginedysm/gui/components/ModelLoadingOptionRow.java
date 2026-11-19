package net.everla.reimaginedysm.gui.components;

import com.elfmcys.yesstevemodel.client.ModelLoadingPolicy;
import net.everla.reimaginedysm.gui.Option;
import net.everla.reimaginedysm.gui.OptionRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.elfmcys.yesstevemodel.config.ModelLoadingMode;

public final class ModelLoadingOptionRow extends EnumOptionRow<ModelLoadingMode> {
    public ModelLoadingOptionRow() {
        super(0, 0, 0, 22, new Option<>("model_loading_mode", ModelLoadingPolicy::get, ModelLoadingPolicy::set) {
            @Override public Component getDescription() {
                return Component.translatable("gui.yes_steve_model.config.model_loading_mode." + get().name().toLowerCase(java.util.Locale.ROOT) + ".desc");
            }
        }, ModelLoadingMode.values());
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.yes_steve_model.config.model_loading_mode.desc")));
    }
    @Override public Component getMessage() {
        return ModelLoadingPolicy.editable() ? super.getMessage() : super.getMessage().copy().append(Component.translatable("gui.yes_steve_model.config.model_loading_mode.server"));
    }
    @Override protected Component valueLabel(ModelLoadingMode value) {
        return Component.translatable("gui.yes_steve_model.config.model_loading_mode." + value.name().toLowerCase(java.util.Locale.ROOT));
    }
    @Override protected void renderControl(GuiGraphics g, int x, int y, float pt) {
        if (!ModelLoadingPolicy.editable() || !option.isDirty()) option.undo();
        if (!ModelLoadingPolicy.editable()) closeOverlay();
        super.renderControl(g,x,y,pt);
    }
    @Override public void onClick(double x, double y) {
        if (ModelLoadingPolicy.editable()) super.onClick(x,y);
    }
    @Override public boolean overlayMouseClicked(double x, double y, int button, float scroll) {
        if (!ModelLoadingPolicy.editable()) { closeOverlay(); return false; }
        return super.overlayMouseClicked(x,y,button,scroll);
    }
}

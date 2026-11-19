package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.config.ExtraPlayerRenderConfig;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.config.LoadingStateConfig;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import net.everla.reimaginedysm.gui.Option;
import net.everla.reimaginedysm.gui.OptionGroup;
import net.everla.reimaginedysm.gui.OptionScreen;
import net.everla.reimaginedysm.gui.components.BooleanOptionRow;
import net.everla.reimaginedysm.gui.components.EnumOptionRow;
import net.everla.reimaginedysm.gui.components.SliderOptionRow;

public class ExtraPlayerConfigScreen extends OptionScreen {
    private Option<Boolean> forceModeOption;

    public ExtraPlayerConfigScreen(@Nullable PlayerModelScreen modelScreen) {
        super(Component.literal("Reimagined YSM"), modelScreen);
    }

    @Override public Component getTitle() {
        return super.getTitle().copy().append(Component.translatable(com.elfmcys.yesstevemodel.client.ClientOnlyMode.isActive()
                ? "gui.yes_steve_model.config.profile.local" : "gui.yes_steve_model.config.profile.normal"));
    }
    @Override protected void onApply() {
        boolean old=com.elfmcys.yesstevemodel.client.ClientOnlyMode.isForced();
        // Commit the current profile first; switching modes must not redirect pending settings.
        for(OptionGroup group:groups) for(var row:group.getRows()) {
            if(row.getOption()!=null && row.getOption()!=forceModeOption)row.getOption().apply();
        }
        forceModeOption.apply();
        GeneralConfig.FORCE_CLIENT_MODE.save();
        GeneralConfig.MODEL_LOADING_MODE.save();
        com.elfmcys.yesstevemodel.config.ClientOnlyGeneralConfig.MODEL_LOADING_MODE.save();
        if(old!=com.elfmcys.yesstevemodel.client.ClientOnlyMode.isForced()) rebuildWidgets();
    }

    @Override
    protected void registerGroups() {
        forceModeOption=Option.ofBoolean("force_client_mode",GeneralConfig.FORCE_CLIENT_MODE);
        OptionGroup general = new OptionGroup("general")
                .add(new SliderOptionRow(0, 0, 0, 22, Option.ofDouble("sound_volume", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.SOUND_VOLUME)), 0.0d, 100.0d, 1.0d, "%"))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_self_model", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.DISABLE_SELF_MODEL))))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_other_model", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.DISABLE_OTHER_MODEL))))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_self_hands", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.DISABLE_SELF_HANDS))))
                .add(new BooleanOptionRow(0, 0, 0, 22, forceModeOption))
                .add(new SliderOptionRow(0, 0, 0, 22, Option.ofDouble("handshake_timeout", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.HANDSHAKE_TIMEOUT)), 1.0d, 60.0d, 1.0d, "s"))
                .add(new SliderOptionRow(0, 0, 0, 22, Option.ofDouble("search_suggestion_count", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.SEARCH_SUGGESTION_COUNT)), 1.0d, 30.0d, 1.0d, ""));

        OptionGroup rendering = new OptionGroup("rendering")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_player_render", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER))))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_player_render_third_person", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER_THIRD_PERSON))))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_projectile_model", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.DISABLE_PROJECTILE_MODEL))))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_vehicle_model", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.DISABLE_VEHICLE_MODEL))))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_external_first_person_anim", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.DISABLE_EXTERNAL_FP_ANIM))));

        OptionGroup performance = new OptionGroup("performance")
                .add(new net.everla.reimaginedysm.gui.components.ModelLoadingOptionRow())
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("use_compatibility_renderer", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.USE_COMPATIBILITY_RENDERER))))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("use_gpu_renderer", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.USE_GPU_RENDERER))));

        OptionGroup misc = new OptionGroup("misc")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("print_animation_roulette_msg", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG))))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_loading_state_screen", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN))))
                .add(new EnumOptionRow<>(0, 0, 0, 22, Option.ofEnum("loading_state_position", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(LoadingStateConfig.LOADING_STATE_POSITION)), LoadingStateConfig.Position.values()))
                .add(new EnumOptionRow<>(0, 0, 0, 22, Option.ofEnum("roulette_settings_mode", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.ROULETTE_SETTINGS_MODE)), GeneralConfig.RouletteSettingsMode.values()))
                .add(new EnumOptionRow<>(0, 0, 0, 22, Option.ofEnum("roulette_mode", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.ROULETTE_MODE)), GeneralConfig.RouletteMode.values()))
                .add(new EnumOptionRow<>(0, 0, 0, 22, Option.ofEnum("texture_screen_mode", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.TEXTURE_SCREEN_MODE)), GeneralConfig.TextureScreenMode.values()))
                .add(new EnumOptionRow<>(0, 0, 0, 22, Option.ofEnum("model_info_screen_mode", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.MODEL_INFO_SCREEN_MODE)), GeneralConfig.ModelInfoScreenMode.values()))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("blur_gui", com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.BLUR_GUI))));

        if(ServerConfigScreen.available()) {
            groups.add(new OptionGroup("server_admin").add(new net.everla.reimaginedysm.gui.OptionRow<Object>(0,0,0,24,null) {
                @Override public Component getMessage() { return ServerConfigScreen.text("title"); }
                @Override protected void renderControl(net.minecraft.client.gui.GuiGraphics g,int x,int y,float pt) {
                    g.drawString(net.minecraft.client.Minecraft.getInstance().font,ServerConfigScreen.text("open"),controlX(),controlY()+4,-1);
                }
                @Override public void onClick(double x,double y) { ServerConfigScreen.open(ExtraPlayerConfigScreen.this); }
            }));
        }
        groups.add(general);
        groups.add(rendering);
        groups.add(performance);
        var physicsFps=com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(net.everla.ysmPhysLib.PhysConfig.FPS);
        groups.add(new OptionGroup("physics_groups")
                .add(new BooleanOptionRow(0,0,0,22,Option.ofBoolean("physics_enabled",
                        com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(net.everla.ysmPhysLib.PhysConfig.ENABLED))))
                .add(new SliderOptionRow(0,0,0,22,new Option<Double>("physics_fps",()->physicsFps.get().doubleValue(),
                        value->physicsFps.set((int)Math.round(value))),0,120,1," FPS")));
        groups.add(misc);
    }
}

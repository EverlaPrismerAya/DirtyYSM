package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.config.ServerConfigPanel;
import com.elfmcys.yesstevemodel.config.ModelLoadingMode;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.*;
import net.everla.reimaginedysm.gui.*;
import net.everla.reimaginedysm.gui.components.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Function;

/** Server snapshot embedded in the normal options screen; never edits local COMMON values. */
public final class ServerConfigScreen {
    private List<String> fields = List.of();
    private final List<Option<?>> options = new ArrayList<>();
    private final List<OptionRow<?>> rows = new ArrayList<>();
    private boolean editable;
    private boolean waiting = true;
    private String status = "loading";

    public static Component text(String key) {
        return Component.translatable("gui.yes_steve_model.server_config." + key);
    }
    public static boolean available() {
        return NetworkHandler.supportsServerExtensions() && Minecraft.getInstance().player != null;
    }
    public void request() {
        NetworkHandler.sendToServer(new C2SServerConfigPacket(List.of()));
    }
    public static void receive(S2CServerConfigPacket packet) {
        if (Minecraft.getInstance().screen instanceof ExtraPlayerConfigScreen screen) screen.receiveServerConfig(packet);
    }
    public void accept(S2CServerConfigPacket packet) {
        waiting = false;
        editable = packet.allowed();
        fields = packet.fields();
        status = packet.result().isEmpty() ? (editable ? "ready" : "readonly") : packet.result();
    }
    private <T> Option<T> option(int index, Function<String,T> parse) {
        return new Option<T>(ServerConfigPanel.KEYS.get(index), () -> parse.apply(fields.get(index)), value -> {}) {
            @Override public Component getLabel() { return text(ServerConfigPanel.KEYS.get(index)); }
            @Override public Component getDescription() { return text(status).copy().append("\n").append(text("notice")); }
        };
    }
    public OptionGroup group() {
        options.clear();
        rows.clear();
        OptionGroup group = new OptionGroup("server_admin");
        group.add(new OptionRow<Object>(0,0,0,22,null) {
            @Override public Component getMessage() { return text(status); }
            @Override protected void renderControl(GuiGraphics g,int x,int y,float pt) {
                g.drawString(Minecraft.getInstance().font,text("refresh"),controlX(),controlY()+4,-1);
            }
            @Override public void onClick(double x,double y) { if (!waiting) { waiting=true;status="loading";rows.forEach(row->row.active=false);request(); } }
        });
        if (fields.size() != ServerConfigPanel.KEYS.size()) return group;
        for (int i=0;i<fields.size();i++) {
            OptionRow<?> row;
            if (i==0) {
                Option<ModelLoadingMode> o=option(i,ModelLoadingMode::valueOf);
                row=new EnumOptionRow<>(0,0,0,24,o,ModelLoadingMode.values()) {
                    @Override protected Component valueLabel(ModelLoadingMode value) {
                        return Component.translatable("gui.yes_steve_model.config.model_loading_mode."+value.name().toLowerCase(Locale.ROOT));
                    }
                };
            } else if (i==1 || i==8) {
                row=new BooleanOptionRow(0,0,0,24,option(i,Boolean::parseBoolean));
            } else {
                row=new TextRow(option(i,Function.identity()),i==4?8192:512);
            }
            row.active=editable && !waiting;
            rows.add(row);
            options.add(row.getOption());
            group.add(row);
        }
        return group;
    }
    public void apply() {
        if (!editable || waiting || options.stream().noneMatch(Option::isDirty)) return;
        List<String> pending=options.stream().map(o->o.get().toString()).toList();
        waiting=true;status="loading";
        rows.forEach(row-> { row.active=false;row.closeOverlay();row.setFocused(false); });
        NetworkHandler.sendToServer(new C2SServerConfigPacket(pending));
    }
    private static final class TextRow extends OptionRow<String> {
        private final EditBox box;
        TextRow(Option<String> option,int limit) {
            super(0,0,0,24,option);
            box=new EditBox(Minecraft.getInstance().font,0,0,150,20,option.getLabel());
            box.setMaxLength(limit);box.setValue(option.get());
            box.setResponder(option::setPending);
        }
        @Override protected int controlWidth() { return Math.max(90,width/2); }
        @Override protected void renderControl(GuiGraphics g,int x,int y,float pt) {
            box.setX(controlX());box.setY(controlY());box.setWidth(controlWidth());
            box.setEditable(active);box.render(g,x,y,pt);
        }
        @Override public void onClick(double x,double y) { box.mouseClicked(x,y,0); }
        @Override public void setFocused(boolean focused) { super.setFocused(focused);box.setFocused(focused && active); }
        @Override public boolean keyPressed(int key,int scan,int mods) { return active && box.keyPressed(key,scan,mods); }
        @Override public boolean charTyped(char ch,int mods) { return active && box.charTyped(ch,mods); }
        @Override public void refresh() { box.setValue(option.get()); }
    }
}

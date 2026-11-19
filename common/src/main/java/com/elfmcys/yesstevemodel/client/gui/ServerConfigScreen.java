package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.config.ServerConfigPanel;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Edits a server snapshot, never the remote client's local COMMON config. */
public final class ServerConfigScreen extends Screen {
    private final Screen parent;
    private List<String> fields=new ArrayList<>();
    private final Map<Integer,EditBox> editors=new HashMap<>();
    private int page;
    private boolean waiting=true;
    private String status="loading";
    private Button save;
    private int dropdownX=-1,dropdownY;
    private int spacing() { return Math.max(22,Math.min(30,(height-110)/5)); }
    private int footerY() { return Math.min(height-28,40+5*spacing()+42); }
    public ServerConfigScreen(Screen parent) { super(text("title"));this.parent=parent; }
    public static Component text(String key) { return Component.translatable("gui.yes_steve_model.server_config."+key); }
    public static boolean available() {
        var mc=Minecraft.getInstance();
        return NetworkHandler.isClientConnected() && mc.player!=null && (mc.getSingleplayerServer()!=null || mc.player.hasPermissions(2));
    }
    public static void open(Screen parent) {
        if(!available())return;
        Minecraft.getInstance().setScreen(new ServerConfigScreen(parent));
        NetworkHandler.sendToServer(new C2SServerConfigPacket(List.of()));
    }
    public static void receive(S2CServerConfigPacket packet) {
        if(!(Minecraft.getInstance().screen instanceof ServerConfigScreen screen))return;
        screen.waiting=false;
        screen.status=packet.allowed() ? (packet.result().isEmpty()?"ready":packet.result()) : "denied";
        if(packet.allowed() && packet.fields().size()==10 && !packet.result().equals("invalid"))screen.fields=new ArrayList<>(packet.fields());
        if(!packet.allowed())screen.fields.clear();
        screen.rebuildWidgets();
    }
    private void capture() { editors.forEach((i,box)->fields.set(i,box.getValue())); }
    @Override protected void init() {
        dropdownX=-1;
        editors.clear();
        int left=width/2-150, top=40;
        if(fields.size()==10) for(int row=0;row<5;row++) {
            int index=page*5+row, y=top+row*spacing();
            String value=fields.get(index);
            if(index==0) {
                Button mode=Button.builder(modeLabel(value),b-> {
                    capture();openModes(left+152,y);
                }).bounds(left+152,y,148,20).build();
                addRenderableWidget(mode);
            } else if(index==1 || index==8) {
                addRenderableWidget(Button.builder(text(value),b->{
                    fields.set(index,Boolean.toString(!Boolean.parseBoolean(fields.get(index))));
                    b.setMessage(text(fields.get(index)));
                }).bounds(left+152,y,148,20).build());
            } else {
                EditBox box=new EditBox(font,left+152,y,148,20,text(ServerConfigPanel.KEYS.get(index)));
                box.setMaxLength(index==4?8192:512);box.setValue(value);
                editors.put(index,box);addRenderableWidget(box);
            }
        }
        int bottom=footerY();
        addRenderableWidget(Button.builder(text("back"),b->onClose()).bounds(left,bottom,72,20).build());
        Button next=addRenderableWidget(Button.builder(text(page==0?"next":"previous"),b->{capture();page=1-page;rebuildWidgets();}).bounds(left+76,bottom,72,20).build());
        next.active=fields.size()==10 && !waiting;
        Button refresh=addRenderableWidget(Button.builder(text("refresh"),b->{waiting=true;status="loading";rebuildWidgets();NetworkHandler.sendToServer(new C2SServerConfigPacket(List.of()));}).bounds(left+152,bottom,72,20).build());
        refresh.active=!waiting;
        save=addRenderableWidget(Button.builder(text("save"),b->{
            capture();waiting=true;status="loading";rebuildWidgets();
            NetworkHandler.sendToServer(new C2SServerConfigPacket(fields));
        }).bounds(left+228,bottom,72,20).build());
        save.active=fields.size()==10 && !waiting;
        for(var editor:editors.values())editor.setEditable(!waiting);
    }
    private static Component modeLabel(String mode) {
        return Component.translatable("gui.yes_steve_model.config.model_loading_mode."+mode.toLowerCase(Locale.ROOT));
    }
    private void openModes(int x,int y) {
        if(waiting)return;
        dropdownX=dropdownX<0?x:-1;dropdownY=y+20;
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(dropdownX>=0) {
            int index=(int)((y-dropdownY)/20);
            if(button==0 && x>=dropdownX && x<dropdownX+148 && y>=dropdownY && index<3) {
                fields.set(0,new String[]{"FULL","PAGE","POTATO"}[index]);
            }
            rebuildWidgets();return true;
        }
        if(waiting)return false;
        return super.mouseClicked(x,y,button);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font,title,width/2,18,-1);
        if(fields.size()==10)for(int row=0;row<5;row++) {
            int index=page*5+row;
            g.drawString(font,text(ServerConfigPanel.KEYS.get(index)),width/2-150,46+row*spacing(),-1);
        }
        g.drawCenteredString(font,text(status),width/2,footerY()-30,0xFFFFCC66);
        g.drawCenteredString(font,text("notice"),width/2,footerY()-16,0xFFAAAAAA);
        super.render(g,mouseX,mouseY,partialTick);
        if(dropdownX>=0) {
            g.pose().pushPose();g.pose().translate(0,0,300);
            for(int i=0;i<3;i++) {
                int y=dropdownY+i*20;
                boolean hover=mouseX>=dropdownX && mouseX<dropdownX+148 && mouseY>=y && mouseY<y+20;
                g.fill(dropdownX,y,dropdownX+148,y+20,hover?0xFF444444:0xFF111111);
                g.drawString(font,modeLabel(new String[]{"FULL","PAGE","POTATO"}[i]),dropdownX+5,y+6,-1);
            }
            g.pose().popPose();
        }
    }
    @Override public void tick() {
        super.tick();
        if(!NetworkHandler.isClientConnected()) onClose();
        for(EditBox editor:editors.values())editor.tick();
    }
    @Override public void resize(Minecraft mc,int width,int height) { capture();super.resize(mc,width,height); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

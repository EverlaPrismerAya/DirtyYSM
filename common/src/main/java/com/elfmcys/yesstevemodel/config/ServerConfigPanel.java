package com.elfmcys.yesstevemodel.config;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.*;

/** Explicit allowlist for the administrator editor. Never accepts arbitrary config paths. */
public final class ServerConfigPanel {
    public static final List<String> KEYS=List.of("ModelLoadingMode","CanSwitchModel","DefaultModelId","DefaultModelTexture","ClientNotDisplayModels","ThreadCount","BandwidthLimit","PlayerSyncTimeout","LowBandwidthUsage","AcceptSoundFX");
    public static boolean allowed(ServerPlayer player) {
        return player!=null && (player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile()));
    }
    private static List<ForgeConfigSpec.ConfigValue<?>> values() {
        return List.of(CommonConfig.MODEL_LOADING_MODE,CommonConfig.CAN_SWITCH_MODEL,CommonConfig.DEFAULT_MODEL_ID,CommonConfig.DEFAULT_MODEL_TEXTURE,CommonConfig.CLIENT_NOT_DISPLAY_MODELS,CommonConfig.THREAD_COUNT,CommonConfig.BANDWIDTH_LIMIT,CommonConfig.PLAYER_SYNC_TIMEOUT,CommonConfig.LOW_BANDWIDTH_USAGE,CommonConfig.ACCEPT_SOUND_FX);
    }
    public static List<String> snapshot() {
        return values().stream().map(v -> v.get() instanceof List<?> list ? String.join(",",list.stream().map(Object::toString).toList()) : v.get().toString()).toList();
    }
    public static List<Object> validate(List<String> input) {
        if(input.size()!=KEYS.size())throw new IllegalArgumentException("Invalid config field count");
        for(String text:input) if(text.length()>8192)throw new IllegalArgumentException("Config field too long");
        List<Object> parsed=new ArrayList<>();
        parsed.add(ModelLoadingMode.valueOf(input.get(0)));
        parsed.add(bool(input.get(1)));
        parsed.add(id(input.get(2)));parsed.add(id(input.get(3)));
        List<String> hidden=input.get(4).isBlank()?List.of():Arrays.stream(input.get(4).split(",",-1)).map(String::trim).map(ServerConfigPanel::id).distinct().toList();
        if(hidden.size()>256)throw new IllegalArgumentException("Too many hidden models");
        parsed.add(hidden);
        parsed.add(integer(input.get(5),0,Math.max(2,Runtime.getRuntime().availableProcessors()-1)));
        parsed.add(integer(input.get(6),1,999));parsed.add(integer(input.get(7),0,120));
        parsed.add(bool(input.get(8)));parsed.add(integer(input.get(9),0,2));
        return parsed;
    }
    private static String id(String value) {
        if(value.isBlank() || value.length()>512 || value.chars().anyMatch(c->c<32 || c==','))throw new IllegalArgumentException("Invalid model or texture ID");
        return value;
    }
    private static boolean bool(String text) {
        if(!text.equals("true") && !text.equals("false"))throw new IllegalArgumentException("Expected true/false");
        return Boolean.parseBoolean(text);
    }
    private static int integer(String text,int min,int max) {
        int value=Integer.parseInt(text);if(value<min || value>max)throw new IllegalArgumentException("Expected "+min+".."+max);return value;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void apply(List<String> input) {
        List<Object> parsed=validate(input); // Validate everything before changing anything.
        var values=values();
        for(int i=0;i<values.size();i++)((ForgeConfigSpec.ConfigValue)values.get(i)).set(parsed.get(i));
        CommonConfig.MODEL_LOADING_MODE.save();
    }
}

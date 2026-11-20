package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.config.*;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import net.minecraft.client.Minecraft;

public final class ModelLoadingPolicy {
    private static volatile ModelLoadingMode serverMode;
    private static volatile com.elfmcys.yesstevemodel.network.message.S2CCommonPolicyPacket commonPolicy;
    public static void receiveCommonPolicy(com.elfmcys.yesstevemodel.network.message.S2CCommonPolicyPacket policy) { commonPolicy=policy; }
    public static boolean canSwitchModel() { return ClientOnlyMode.isActive() ? true : commonPolicy==null ? CommonConfig.CAN_SWITCH_MODEL.get() : commonPolicy.canSwitch(); }
    public static boolean lowBandwidthUsage() { return ClientOnlyMode.isActive() ? false : commonPolicy==null ? CommonConfig.LOW_BANDWIDTH_USAGE.get() : commonPolicy.lowBandwidth(); }
    public static java.util.List<String> hiddenModels() { return ClientOnlyMode.isActive() ? java.util.List.of() : commonPolicy==null ? CommonConfig.CLIENT_NOT_DISPLAY_MODELS.get() : commonPolicy.hiddenModels(); }
    private static volatile java.util.List<String> inUse = java.util.List.of();
    public static ModelLoadingMode get() {
        if(ClientOnlyMode.isActive())return com.elfmcys.yesstevemodel.config.ClientOnlyGeneralConfig.MODEL_LOADING_MODE.get();
        if (NetworkHandler.isOfficialServer()) return ModelLoadingMode.FULL;
        if (NetworkHandler.isClientConnected())
            return serverMode == null ? ModelLoadingMode.POTATO : serverMode;
        if (Minecraft.getInstance().getSingleplayerServer() != null) return CommonConfig.MODEL_LOADING_MODE.get();
        return com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.MODEL_LOADING_MODE).get();
    }
    public static void catalog(ModelLoadingMode mode) { serverMode = mode; }
    public static void receive(ModelLoadingMode mode, java.util.List<String> models) {
        inUse = java.util.List.copyOf(models);
        serverMode = mode;
        ClientModelManager.updateModelLoadingMode();
        requestUsedModels();
    }
    public static void requestUsedModels() {
        if (ClientOnlyMode.isActive()) return;
        for (String id : inUse) ClientModelManager.requestLazyModel(id);
    }
    public static void reset() { serverMode = null; commonPolicy=null; inUse = java.util.List.of(); }
    public static boolean editable() {
        return !NetworkHandler.isClientConnected()
                || Minecraft.getInstance().getSingleplayerServer() != null;
    }
    public static void set(ModelLoadingMode mode) {
        if(ClientOnlyMode.isActive()) {
            ClientOnlyGeneralConfig.MODEL_LOADING_MODE.set(mode);ClientOnlyGeneralConfig.MODEL_LOADING_MODE.save();
            ClientModelManager.updateModelLoadingMode();return;
        }
        if (!editable()) return;
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                CommonConfig.MODEL_LOADING_MODE.set(mode);
                CommonConfig.MODEL_LOADING_MODE.save();
                ServerModelManager.syncLoadingMode(server);
            });
        } else {
            com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.MODEL_LOADING_MODE).set(mode);
            com.elfmcys.yesstevemodel.config.ClientConfigScope.effective(GeneralConfig.MODEL_LOADING_MODE).save();
            ClientModelManager.updateModelLoadingMode();
        }
    }
}

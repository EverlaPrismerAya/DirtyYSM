package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.config.CommonConfig;
import com.elfmcys.yesstevemodel.client.ModelLoadingPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.everla.reimaginedysm.api.network.PacketContext;
import java.util.List;
import java.util.ArrayList;

/** Session-only server policy: COMMON configs are not automatically synchronized by Forge. */
public record S2CCommonPolicyPacket(boolean canSwitch, boolean lowBandwidth, List<String> hiddenModels) {
    public S2CCommonPolicyPacket { hiddenModels=List.copyOf(hiddenModels); }
    public static S2CCommonPolicyPacket current() {
        return new S2CCommonPolicyPacket(CommonConfig.CAN_SWITCH_MODEL.get(),CommonConfig.LOW_BANDWIDTH_USAGE.get(),CommonConfig.CLIENT_NOT_DISPLAY_MODELS.get());
    }
    public static void encode(S2CCommonPolicyPacket p,FriendlyByteBuf b) {
        b.writeBoolean(p.canSwitch);b.writeBoolean(p.lowBandwidth);b.writeVarInt(p.hiddenModels.size());
        for(String id:p.hiddenModels)b.writeUtf(id,512);
    }
    public static S2CCommonPolicyPacket decode(FriendlyByteBuf b) {
        boolean canSwitch=b.readBoolean(), lowBandwidth=b.readBoolean();int count=b.readVarInt();
        if(count<0 || count>16384)throw new IllegalArgumentException("Too many hidden models");
        List<String> hidden=new ArrayList<>();for(int i=0;i<count;i++)hidden.add(b.readUtf(512));
        return new S2CCommonPolicyPacket(canSwitch,lowBandwidth,hidden);
    }
    public static void handle(S2CCommonPolicyPacket p,PacketContext c) {
        if(c.isClientSide())c.enqueueWork(()->ModelLoadingPolicy.receiveCommonPolicy(p));
    }
}

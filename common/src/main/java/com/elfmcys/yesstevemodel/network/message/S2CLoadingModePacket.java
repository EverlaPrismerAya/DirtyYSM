package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.config.ModelLoadingMode;
import com.elfmcys.yesstevemodel.client.ModelLoadingPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.everla.reimaginedysm.api.network.PacketContext;
import java.util.*;

/** Server policy and the currently used models, including players outside tracking range. */
public record S2CLoadingModePacket(ModelLoadingMode mode, List<String> inUse) {
    public S2CLoadingModePacket { inUse=List.copyOf(inUse); }
    public static void encode(S2CLoadingModePacket p, FriendlyByteBuf b) {
        b.writeEnum(p.mode);b.writeVarInt(p.inUse.size());for(String id:p.inUse)b.writeUtf(id,512);
    }
    public static S2CLoadingModePacket decode(FriendlyByteBuf b) {
        ModelLoadingMode mode=b.readEnum(ModelLoadingMode.class);int count=b.readVarInt();
        if(count<0 || count>4096)throw new IllegalArgumentException("Too many active models");
        List<String> ids=new ArrayList<>();for(int i=0;i<count;i++)ids.add(b.readUtf(512));
        return new S2CLoadingModePacket(mode,ids);
    }
    public static void handle(S2CLoadingModePacket p, PacketContext c) {
        if(c.isClientSide())c.enqueueWork(()->ModelLoadingPolicy.receive(p.mode,p.inUse));
    }
}

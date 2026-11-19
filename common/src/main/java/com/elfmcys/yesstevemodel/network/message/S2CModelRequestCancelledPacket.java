package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import net.minecraft.network.FriendlyByteBuf;
import net.everla.reimaginedysm.api.network.PacketContext;

public record S2CModelRequestCancelledPacket(long h1, long h2, long token, boolean retryDemand) {
    public static void encode(S2CModelRequestCancelledPacket p, FriendlyByteBuf b) {
        b.writeVarLong(p.h1);b.writeVarLong(p.h2);b.writeVarLong(p.token);b.writeBoolean(p.retryDemand);
    }
    public static S2CModelRequestCancelledPacket decode(FriendlyByteBuf b) {
        return new S2CModelRequestCancelledPacket(b.readVarLong(),b.readVarLong(),b.readVarLong(),b.readBoolean());
    }
    public static void handle(S2CModelRequestCancelledPacket p, PacketContext c) {
        if(c.isClientSide()) c.enqueueWork(()->ClientModelManager.modelRequestCancelled(p.h1,p.h2,p.token,p.retryDemand));
    }
}

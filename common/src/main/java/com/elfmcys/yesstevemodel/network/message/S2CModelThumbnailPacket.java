package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.client.ModelThumbnails;
import com.elfmcys.yesstevemodel.model.ModelThumbnailRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.everla.reimaginedysm.api.network.PacketContext;

public record S2CModelThumbnailPacket(long page, String model, String hash, byte[] png) {
    public static void encode(S2CModelThumbnailPacket p, FriendlyByteBuf b) {
        b.writeLong(p.page); b.writeUtf(p.model,512); b.writeUtf(p.hash,64); b.writeByteArray(p.png);
    }
    public static S2CModelThumbnailPacket decode(FriendlyByteBuf b) {
        return new S2CModelThumbnailPacket(b.readLong(),b.readUtf(512),b.readUtf(64),b.readByteArray(ModelThumbnailRenderer.MAX_BYTES));
    }
    public static void handle(S2CModelThumbnailPacket p, PacketContext c) {
        if(c.isClientSide()) c.enqueueWork(() -> ModelThumbnails.receive(p.page,p.model,p.hash,p.png));
    }
}

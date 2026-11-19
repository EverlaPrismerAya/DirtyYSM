package com.elfmcys.yesstevemodel.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.everla.reimaginedysm.api.network.PacketContext;

public record C2SModelUploadChunkPacket(long uploadId, int offset, byte[] data) {

    public static void encode(C2SModelUploadChunkPacket message, FriendlyByteBuf buf) {
        buf.writeVarLong(message.uploadId);
        buf.writeVarInt(message.offset);
        buf.writeByteArray(message.data);
    }

    public static C2SModelUploadChunkPacket decode(FriendlyByteBuf buf) {
        return new C2SModelUploadChunkPacket(buf.readVarLong(), buf.readVarInt(), buf.readByteArray(32_000));
    }

    public static void handle(C2SModelUploadChunkPacket message, PacketContext ctx) {
        if (ctx.isServerSide() && ctx.getSender() != null)
            ctx.enqueueWork(() -> net.everla.reimaginedysm.server.ModelUploadReceiver.chunk(ctx.getSender(), message));
    }
}

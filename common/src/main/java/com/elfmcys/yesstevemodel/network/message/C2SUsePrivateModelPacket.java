package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.capability.ModelInfoCapability;
import net.everla.reimaginedysm.api.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;

/** No private model id, texture, or other private metadata leaves the client. */
public record C2SUsePrivateModelPacket() {
    public static void encode(C2SUsePrivateModelPacket message, FriendlyByteBuf buf) {}
    public static C2SUsePrivateModelPacket decode(FriendlyByteBuf buf) { return new C2SUsePrivateModelPacket(); }
    public static void handle(C2SUsePrivateModelPacket message, PacketContext context) {
        if (context.isServerSide() && context.getSender() != null) {
            context.enqueueWork(() -> ModelInfoCapability.get(context.getSender()).ifPresent(cap -> {
                cap.resetToDefault();
                cap.stopAnimation(context.getSender());
            }));
        }
    }
}

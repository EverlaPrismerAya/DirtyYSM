package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.client.gui.ServerConfigScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.everla.reimaginedysm.api.network.PacketContext;
import java.util.*;

public record S2CServerConfigPacket(boolean allowed,List<String> fields,String result) {
    public S2CServerConfigPacket { fields=List.copyOf(fields); }
    public static void encode(S2CServerConfigPacket p,FriendlyByteBuf b) {
        b.writeBoolean(p.allowed);b.writeVarInt(p.fields.size());for(String field:p.fields)b.writeUtf(field,8192);b.writeUtf(p.result,32);
    }
    public static S2CServerConfigPacket decode(FriendlyByteBuf b) {
        boolean allowed=b.readBoolean();int count=b.readVarInt();if(count!=0 && count!=10)throw new IllegalArgumentException("Invalid config field count");
        List<String> fields=new ArrayList<>();for(int i=0;i<count;i++)fields.add(b.readUtf(8192));return new S2CServerConfigPacket(allowed,fields,b.readUtf(32));
    }
    public static void handle(S2CServerConfigPacket p,PacketContext c) {
        if(c.isClientSide())c.enqueueWork(()->ServerConfigScreen.receive(p));
    }
}

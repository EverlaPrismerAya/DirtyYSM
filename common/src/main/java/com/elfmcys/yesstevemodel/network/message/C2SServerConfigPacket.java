package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.config.ServerConfigPanel;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.everla.reimaginedysm.api.network.PacketContext;
import java.util.*;

/** Empty fields request a snapshot; a complete list commits one validated transaction. */
public record C2SServerConfigPacket(List<String> fields) {
    public C2SServerConfigPacket { fields=List.copyOf(fields); }
    public static void encode(C2SServerConfigPacket p,FriendlyByteBuf b) {
        b.writeVarInt(p.fields.size());for(String field:p.fields)b.writeUtf(field,8192);
    }
    public static C2SServerConfigPacket decode(FriendlyByteBuf b) {
        int count=b.readVarInt();if(count!=0 && count!=10)throw new IllegalArgumentException("Invalid config field count");
        List<String> fields=new ArrayList<>();for(int i=0;i<count;i++)fields.add(b.readUtf(8192));return new C2SServerConfigPacket(fields);
    }
    public static void handle(C2SServerConfigPacket p,PacketContext c) {
        if(c.isServerSide())c.enqueueWork(()-> {
            var player=c.getSender();if(player==null)return;
            if(!ServerConfigPanel.allowed(player)) {
                NetworkHandler.sendToClientPlayer(new S2CServerConfigPacket(false,List.of(),"denied"),player);return;
            }
            String result="";
            try {
                if(!p.fields.isEmpty()) {
                    ServerConfigPanel.apply(p.fields);
                    ServerModelManager.syncLoadingMode(player.server);
                    result="saved";
                }
            } catch(Exception ex) { result="invalid"; }
            NetworkHandler.sendToClientPlayer(new S2CServerConfigPacket(true,ServerConfigPanel.snapshot(),result),player);
        });
    }
}

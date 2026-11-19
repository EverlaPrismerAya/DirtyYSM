package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.model.ServerModelManager;
import net.minecraft.network.FriendlyByteBuf;
import net.everla.reimaginedysm.api.network.PacketContext;
import java.util.*;

public record C2SModelThumbnailsPacket(long page, List<String> models) {
    public C2SModelThumbnailsPacket { if (models.size()>10) throw new IllegalArgumentException("Too many thumbnails"); models=List.copyOf(models); }
    public static void encode(C2SModelThumbnailsPacket p, FriendlyByteBuf b) {
        b.writeLong(p.page); b.writeVarInt(p.models.size()); for(String id:p.models) b.writeUtf(id,512);
    }
    public static C2SModelThumbnailsPacket decode(FriendlyByteBuf b) {
        long page=b.readLong(); int count=b.readVarInt();
        if(count<0 || count>10) throw new IllegalArgumentException("Too many thumbnails");
        List<String> ids=new ArrayList<>(); for(int i=0;i<count;i++) ids.add(b.readUtf(512));
        return new C2SModelThumbnailsPacket(page,ids);
    }
    public static void handle(C2SModelThumbnailsPacket p, PacketContext c) {
        if(c.isServerSide()) c.enqueueWork(() -> { if(c.getSender()!=null) ServerModelManager.requestThumbnails(c.getSender(),p.page,p.models); });
    }
}

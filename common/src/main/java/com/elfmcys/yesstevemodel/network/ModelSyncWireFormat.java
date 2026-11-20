package com.elfmcys.yesstevemodel.network;

import net.everla.reimaginedysm.security.YSMByteBuf;
import java.util.Collection;

/** The official catalog has no loading-mode prefix or metadata JSON on each entry. */
public final class ModelSyncWireFormat {
    private ModelSyncWireFormat() {}
    public record Entry(long hash1, long hash2, String id, boolean auth, int customSkin, int version, String metadata) {}

    public static Entry readEntry(YSMByteBuf buf, boolean official) {
        long hash1=buf.readVarLong(), hash2=buf.readVarLong();
        String id=buf.readString();
        boolean auth=buf.readVarInt()==1;
        int customSkin=buf.readVarInt(), version=buf.readVarInt();
        return new Entry(hash1,hash2,id,auth,customSkin,version,official ? null : buf.readString());
    }

    /** Official packet 04 contains only a count and hash pairs. */
    public static void writeOfficialRequests(YSMByteBuf buf, Collection<Entry> missing) {
        buf.writeVarInt(missing.size());
        for (Entry entry : missing) { buf.writeVarLong(entry.hash1); buf.writeVarLong(entry.hash2); }
    }

    public static void validateChunk(int total, int offset, int length, int available, int received) {
        if (total<=0 || offset<0 || length<=0 || offset>total-length || length>available || offset!=received)
            throw new IllegalArgumentException("Invalid or non-contiguous YSM model chunk");
    }
}

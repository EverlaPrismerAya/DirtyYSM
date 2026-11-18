package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import it.unimi.dsi.fastutil.objects.Object2FloatArrayMap;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import com.elfmcys.yesstevemodel.resource.*;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import net.everla.dirtyYsm.security.*;
import java.nio.file.*;
import java.util.*;

/** Runs in an isolated Gradle build directory, never against a player's config folder. */
public final class ModelTransferCheck {
    public static void main(String[] args) throws Exception {
        String hash = "0123456789abcdef0123456789abcdef";
        RawYsmModel raw = new RawYsmModel();
        raw.properties.sha256 = hash;
        raw.metadata.name = "cache fixture";
        byte[] serialized;
        try (YSMByteBuf writer = YSMBinarySerializer.serialize(raw, 32, true)) { serialized = writer.toArray(); }
        YsmCrypt.CachePayload payload = new YsmCrypt.CachePayload(serialized, 32);
        Path canonical = SharedModelCache.store(hash, payload);
        byte[] first = Files.readAllBytes(canonical);
        assert canonical.equals(SharedModelCache.find(hash)) : "Content hash cache miss";
        assert canonical.equals(SharedModelCache.store(hash, payload));
        assert Arrays.equals(first, Files.readAllBytes(canonical)) : "Cache hit rewrote model data";
        try (YSMBinaryDeserializer reader = new YSMBinaryDeserializer(YsmCrypt.readInPlace(first.clone(), SharedModelCache.key()).data(), 32)) {
            assert hash.equals(reader.deserializeKeepOpen().properties.sha256) : "Cached payload changed";
        }
        byte[] serverA = new byte[56], serverB = new byte[56], legacyKey = new byte[56];
        Arrays.fill(serverA, (byte) 1); Arrays.fill(serverB, (byte) 2); Arrays.fill(legacyKey, (byte) 3);
        long[] saltedA = YsmCrypt.calculateModelHashes(hash, serverA), saltedB = YsmCrypt.calculateModelHashes(hash, serverB);
        assert !Arrays.equals(saltedA, saltedB);
        assert SharedModelCache.find(hash) != null : "Server salt must not affect content cache lookup";

        // Exercise migration of a valid old cache from a different server catalog.
        Files.delete(canonical);
        Path oldFolder = ServerModelManager.CACHE_CLIENT.resolve("old-server");
        Files.createDirectories(oldFolder);
        Path legacy = oldFolder.resolve(YSMClientCache.generateCacheFileName(saltedA[0], saltedA[1], legacyKey));
        Files.write(legacy, YsmCrypt.encryptServerCache(serialized, legacyKey, saltedA[0], saltedA[1]));
        assert canonical.equals(SharedModelCache.findLegacy(hash, legacyKey)) : "Old cache was not reused by raw hash";
        byte[] damaged = Files.readAllBytes(canonical); damaged[damaged.length / 2] ^= 1;
        Files.write(canonical, damaged);
        assert SharedModelCache.find(hash) == null : "Corrupt cache must not count as a hit";
        assert SharedModelCache.findLegacy(hash, legacyKey) != null : "Valid legacy cache should repair corrupt canonical cache";
        boolean rejected = false;
        try { SharedModelCache.path("../outside"); } catch (IllegalArgumentException expected) { rejected = true; }
        assert rejected : "Hash path traversal accepted";

        Path privateFolder = PrivateModelLoader.FOLDER.resolve("fixture");
        Files.createDirectories(privateFolder);
        Files.writeString(privateFolder.resolve("ysm.json"), "{\"spec\":2,\"metadata\":{\"name\":\"private fixture\"}}");
        Set<String> before = new HashSet<>(ServerModelManager.getServerModelInfo().keySet());
        List<String> loaded = new ArrayList<>();
        PrivateModelLoader.load((id, model) -> {
            loaded.add(id);
            assert SharedModelCache.find(model.properties.sha256) != null;
        });
        assert loaded.contains("client/fixture") : "Private folder was not loaded";
        assert before.equals(ServerModelManager.getServerModelInfo().keySet()) : "Private model leaked into the shared server catalog";
        assert !ServerModelManager.getServerModelInfo().containsKey("client/fixture");
        assert SharedModelCache.path("a".repeat(64)).getFileName().toString().endsWith(".cache");
        int localPlayer = 42;
        assert NetworkHandler.isPrivateModelStatePacket(C2SPlayAnimationPacket.createDefault(), localPlayer);
        assert NetworkHandler.isPrivateModelStatePacket(new C2SPlayAnimationPacket(1, "", localPlayer), localPlayer);
        assert !NetworkHandler.isPrivateModelStatePacket(new C2SPlayAnimationPacket(1, "", 43), localPlayer) : "Other entity animation must not be blocked";
        assert NetworkHandler.isPrivateModelStatePacket(new C2SRequestExecuteMolangPacket("v.private=1", localPlayer), localPlayer);
        assert !NetworkHandler.isPrivateModelStatePacket(new C2SRequestExecuteMolangPacket("v.other=1", 43), localPlayer);
        assert NetworkHandler.isPrivateModelStatePacket(new C2SCompleteFeedbackPacket(new FeedbackData(123, new Object2FloatArrayMap<>(), null, localPlayer)), localPlayer);
        assert NetworkHandler.isPrivateModelStatePacket(new C2SSyncAnimationExpressionPacket(new FloatArrayList()), localPlayer);
        assert !NetworkHandler.isPrivateModelStatePacket(new C2SUsePrivateModelPacket(), localPlayer) : "Default reset must reach the server";
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            C2SUsePrivateModelPacket.encode(new C2SUsePrivateModelPacket(), packet);
            assert packet.readableBytes() == 0 : "Private selection packet must contain no model data";
        } finally { packet.release(); }
        System.out.println("PASS: model hash reuse, server salt independence, legacy migration, corruption rejection, private folder/catalog isolation, private state filtering and empty reset packet");
    }
}

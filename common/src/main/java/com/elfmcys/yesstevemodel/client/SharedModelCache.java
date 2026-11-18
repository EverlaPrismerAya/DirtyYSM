package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import net.everla.dirtyYsm.security.YsmCrypt;
import java.nio.file.*;
import java.util.stream.Stream;

/** Content-addressed local cache, independent of a server's key and catalog names. */
public final class SharedModelCache {
    private static final Path FOLDER = ServerModelManager.CACHE.resolve("client_shared");
    // Local storage encoding only; this is not an access-control or encryption secret.
    private static final byte[] KEY = new byte[56];

    private SharedModelCache() {}

    public static byte[] key() { return KEY.clone(); }

    public static Path path(String hash) {
        if (hash == null || !hash.matches("(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{64})")) throw new IllegalArgumentException("Invalid model content hash");
        return FOLDER.resolve(hash.toLowerCase(java.util.Locale.ROOT) + ".cache");
    }

    public static Path find(String hash) {
        Path file = path(hash);
        long[] hashes = YsmCrypt.calculateModelHashes(hash, KEY);
        try {
            return Files.isRegularFile(file) && YsmCrypt.verifyServerCache(Files.readAllBytes(file), hashes[0], hashes[1]) ? file : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Path store(String hash, YsmCrypt.CachePayload payload) throws Exception {
        if (payload.formatVersion() != 32) throw new IllegalArgumentException("Expected normalized model format 32");
        Path existing = find(hash);
        if (existing != null) return existing;
        Path target = path(hash);
        long[] hashes = YsmCrypt.calculateModelHashes(hash, KEY);
        byte[] data = YsmCrypt.encryptServerCache(payload.data(), KEY, hashes[0], hashes[1]);
        Files.createDirectories(FOLDER);
        Path temp = Files.createTempFile(FOLDER, ".model-", ".part");
        try {
            Files.write(temp, data);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }

    /** Migrate older caches lazily, including those named using another server's key. */
    // ponytail: linear scan of pre-upgrade caches; build a persistent hash index if migration latency becomes noticeable.
    public static Path findLegacy(String hash, byte[] clientKey) throws Exception {
        if (!Files.isDirectory(ServerModelManager.CACHE_CLIENT)) return null;
        try (Stream<Path> files = Files.walk(ServerModelManager.CACHE_CLIENT)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().matches("[0-9a-fA-F]{40}")).toList()) {
                try {
                    java.util.UUID id = net.everla.dirtyYsm.security.YSMClientCache.getModelUUIDFromFileName(file.getFileName().toString(), clientKey);
                    if (id == null || !net.everla.dirtyYsm.security.YSMClientCache.verifyFileContent(file.toFile(), id.getMostSignificantBits(), id.getLeastSignificantBits())) continue;
                    YsmCrypt.CachePayload payload = YsmCrypt.readInPlace(Files.readAllBytes(file), clientKey);
                    try (YSMBinaryDeserializer reader = new YSMBinaryDeserializer(payload.data(), payload.formatVersion())) {
                        if (hash.equals(reader.deserializeKeepOpen().properties.sha256)) return store(hash, payload);
                    }
                } catch (Exception ignored) {
                    // A cache from an unknown key or a corrupt file is not a reusable model.
                }
            }
        }
        return null;
    }
}

package net.everla.ysmPhysLib;

import java.util.HashSet;

/** Units: seconds, blocks (gravity), degrees (angle), model pixels (radius). */
public record PhysParameters(float r, float g, float s, float a, float c, float air) {
    /** Baking recipe identity, independent of the experimental mod release version. */
    public static final String GEOMETRY_CACHE_KEY = "ysmPhysC-solid-without-uv-1";
    public static final PhysParameters DEFAULT = new PhysParameters(1, 9.8f, 20, 90, 1, 0.5f);

    public static boolean isEnd(String name) {
        return name.startsWith("ysmPhysEnd@") && name.length() > 11 && name.indexOf('@',11)<0;
    }

    public static boolean isCollider(String name) {
        return name.startsWith("ysmPhysC@") && name.length()>9 && name.indexOf('@',9)<0;
    }

    public static boolean isCandidate(String name) {
        return name.startsWith("ysmPhys") && !name.startsWith("ysmPhysEnd") && !name.startsWith("ysmPhysC");
    }

    public static PhysParameters parse(String name, PhysParameters parent) {
        int at = name.indexOf('@');
        if (at < 0 || at == name.length() - 1 || name.indexOf('@', at + 1) >= 0)
            throw new IllegalArgumentException("expected ysmPhys[#parameters]@name");
        String prefix = name.substring(0, at);
        if (prefix.equals("ysmPhys")) return parent;
        if (!prefix.startsWith("ysmPhys#") || prefix.length() == 8)
            throw new IllegalArgumentException("invalid physics prefix");
        float r = parent.r, g = parent.g, s = parent.s, a = parent.a, c = parent.c, air = parent.air;
        var seen = new HashSet<String>();
        for (String entry : prefix.substring(8).split(",", -1)) {
            String[] pair = entry.trim().split("=", -1);
            if (pair.length != 2 || !seen.add(pair[0].trim()))
                throw new IllegalArgumentException("invalid or duplicate parameter: " + entry);
            float value = Float.parseFloat(pair[1].trim());
            if (!Float.isFinite(value)) throw new IllegalArgumentException("non-finite parameter");
            switch (pair[0].trim()) {
                case "r" -> r = value;
                case "g" -> g = value;
                case "s" -> s = value;
                case "a" -> a = value;
                case "c" -> c = value;
                case "air" -> air = value;
                default -> throw new IllegalArgumentException("unknown parameter: " + pair[0]);
            }
        }
        if (r < 0 || s < 0 || c < 0 || air < 0 || a < 0 || a > 180)
            throw new IllegalArgumentException("parameter outside supported range");
        return new PhysParameters(r, g, s, a, c, air);
    }
}

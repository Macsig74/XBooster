package dev.dr4.booster.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection bridge to DonutShards — zero compile dependency.
 * All calls are safe: returns 0 / false when DonutShards is absent.
 */
public final class ShardsAPI {

    private static Object sm;
    private static Method get, add, remove, has, set;
    private static boolean tried, ok;

    private ShardsAPI() {}

    private static synchronized boolean init() {
        if (tried) return ok;
        tried = true;
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("DonutShards");
            if (pl == null) return false;
            Class<?> main = pl.getClass();
            Object inst   = main.getMethod("getInstance").invoke(null);
            sm            = main.getMethod("getShardsManager").invoke(inst);
            Class<?> c    = sm.getClass();
            get    = c.getMethod("getShards",    UUID.class);
            has    = c.getMethod("hasEnough",    UUID.class, long.class);
            add    = c.getMethod("addShards",    UUID.class, long.class);
            remove = c.getMethod("removeShards", UUID.class, long.class);
            set    = c.getMethod("setShards",    UUID.class, long.class);
            return ok = (sm != null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Resets the cached hook so the next call re-initialises (useful after reload). */
    public static synchronized void reset() {
        tried = false;
        ok    = false;
        sm    = null;
        get = add = remove = has = set = null;
    }

    public static boolean available()            { return init(); }
    public static long    get(UUID u)            { try { return init() ? ((Number) get.invoke(sm, u)).longValue() : 0L; } catch (Throwable t) { return 0L; } }
    public static boolean has(UUID u, long amt)  { try { return init() && (boolean) has.invoke(sm, u, amt); } catch (Throwable t) { return false; } }
    public static void    give(UUID u, long amt) { try { if (init()) add.invoke(sm, u, amt); } catch (Throwable ignored) {} }
    public static boolean take(UUID u, long amt) { if (!has(u, amt)) return false; try { remove.invoke(sm, u, amt); return true; } catch (Throwable t) { return false; } }
    public static void    set(UUID u, long amt)  { try { if (init()) set.invoke(sm, u, amt); } catch (Throwable ignored) {} }
}

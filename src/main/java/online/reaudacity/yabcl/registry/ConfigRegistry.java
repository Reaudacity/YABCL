package online.reaudacity.yabcl.registry;

import online.reaudacity.yabcl.log.YabclLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global registry of all YABCL-managed config instances.
 *
 * <p>Every config loaded through {@link online.reaudacity.yabcl.YabclConfig#load} or
 * {@link online.reaudacity.yabcl.builder.ConfigBuilder#build} is registered here by
 * its class. This enables a single {@code YabclConfig.reloadAll(plugin)} call to
 * refresh every config at once.</p>
 *
 * <h3>Key design</h3>
 * <p>The registry is keyed by the config class ({@code Class<?>}). Registering the same
 * class twice replaces the old entry and logs a debug notice — this is the expected
 * behaviour during a reload.</p>
 *
 * <p>There is one registry per JVM class-loader; it is effectively a process-scoped
 * singleton. For multi-plugin setups, each plugin should use its own YABCL shade.</p>
 */
public final class ConfigRegistry {

    // -------------------------------------------------------------------------
    // Internal holder — keeps the loaded instance and a reload callback together
    // -------------------------------------------------------------------------

    public record Entry(
            @NotNull Object instance,
            @NotNull Runnable reloader
    ) {}

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private static final Map<Class<?>, Entry> registry = new LinkedHashMap<>();

    private ConfigRegistry() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a config instance under its class.
     *
     * @param clazz    the config class (the key)
     * @param instance the loaded config object
     * @param reloader a {@link Runnable} that reloads this config when invoked
     */
    public static <T> void register(
            @NotNull Class<T> clazz,
            @NotNull T instance,
            @NotNull Runnable reloader
    ) {
        if (registry.containsKey(clazz)) {
            YabclLogger.debug("Re-registering config: " + clazz.getSimpleName());
        }
        registry.put(clazz, new Entry(instance, reloader));
        YabclLogger.debug("Registered config: " + clazz.getSimpleName());
    }

    /**
     * Removes a config from the registry.
     *
     * @param clazz the config class to deregister
     */
    public static <T> void unregister(@NotNull Class<T> clazz) {
        registry.remove(clazz);
        YabclLogger.debug("Unregistered config: " + clazz.getSimpleName());
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the registry entry for the given class, or {@code null} if not registered.
     *
     * @param clazz the config class
     * @return the entry, or {@code null}
     */
    public static <T> @Nullable Entry get(@NotNull Class<T> clazz) {
        return registry.get(clazz);
    }

    /**
     * Returns {@code true} if the given class is registered.
     *
     * @param clazz the config class
     * @return whether it is registered
     */
    public static boolean isRegistered(@NotNull Class<?> clazz) {
        return registry.containsKey(clazz);
    }

    // -------------------------------------------------------------------------
    // Bulk operations
    // -------------------------------------------------------------------------

    /**
     * Invokes the reload callback for every registered config in registration order.
     * Errors in individual reloads are caught and logged without stopping others.
     */
    public static void reloadAll() {
        if (registry.isEmpty()) {
            YabclLogger.info("No configs registered — nothing to reload.");
            return;
        }

        YabclLogger.info("Reloading <yellow>" + registry.size() + "</yellow> config(s)...");
        int ok = 0, failed = 0;

        for (Map.Entry<Class<?>, Entry> entry : registry.entrySet()) {
            try {
                entry.getValue().reloader().run();
                ok++;
            } catch (Exception ex) {
                YabclLogger.error("Failed to reload config "
                        + entry.getKey().getSimpleName(), ex);
                failed++;
            }
        }

        if (failed == 0) {
            YabclLogger.info("All <yellow>" + ok + "</yellow> config(s) reloaded successfully.");
        } else {
            YabclLogger.warn(ok + " config(s) reloaded, " + failed + " failed.");
        }
    }

    /** Returns an unmodifiable view of all entries. */
    public static @NotNull Collection<Entry> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /** Returns the number of registered configs. */
    public static int size() {
        return registry.size();
    }
}
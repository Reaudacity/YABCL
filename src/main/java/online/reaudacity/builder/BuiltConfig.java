package online.reaudacity.builder;

import online.reaudacity.yabcl.exception.ConfigLoadException;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A lightweight, type-safe config accessor returned by {@link ConfigBuilder#build}.
 *
 * <p>Wraps the loaded {@link FileConfiguration} and exposes typed getters with
 * clear fallback semantics. This is the builder API's equivalent of an annotated
 * {@link online.reaudacity.yabcl.YabclConfig} subclass.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * BuiltConfig config = ConfigBuilder.create("settings.yml", "1.0")
 *     .field("general.prefix",   "<gray>[Plugin]</gray>")
 *     .field("limits.max-homes", 3)
 *     .field("limits.enabled",   true)
 *     .header("Settings file", "Managed by YABCL.")
 *     .build(plugin);
 *
 * String prefix  = config.getString("general.prefix");
 * int    maxHomes = config.getInt("limits.max-homes");
 * boolean enabled = config.getBoolean("limits.enabled");
 * }</pre>
 *
 * <h3>Missing keys</h3>
 * <p>Getters return the default value that was declared in the builder, or the
 * type's zero value ({@code ""}, {@code 0}, {@code false}) if no default was set.
 * Use {@link #require(String)} to throw on absent keys.</p>
 *
 * <h3>Reloading</h3>
 * <p>Call {@link #reload()} to re-read the file from disk into the same instance.
 * The {@link ConfigBuilder} reloader is stored internally so the full load pipeline
 * (including migration) is re-run.</p>
 */
public final class BuiltConfig {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private @NotNull FileConfiguration config;
    private final @NotNull ConfigBuilder builder;

    BuiltConfig(@NotNull FileConfiguration config, @NotNull ConfigBuilder builder) {
        this.config  = config;
        this.builder = builder;
    }

    // -------------------------------------------------------------------------
    // Reload
    // -------------------------------------------------------------------------

    /**
     * Reloads this config from disk, running the full YABCL pipeline
     * (migration, defaults, version stamp) again.
     */
    public void reload() {
        BuiltConfig fresh = builder.buildInternal();
        this.config = fresh.config;
    }

    // -------------------------------------------------------------------------
    // String
    // -------------------------------------------------------------------------

    /**
     * Returns the string value at {@code path}, or the builder's declared default,
     * or {@code ""} if neither exists.
     */
    public @NotNull String getString(@NotNull String path) {
        return getString(path, builder.defaultString(path));
    }

    /**
     * Returns the string value at {@code path}, or {@code fallback} if absent.
     */
    public @NotNull String getString(@NotNull String path, @NotNull String fallback) {
        String val = config.getString(path);
        return val != null ? val : fallback;
    }

    // -------------------------------------------------------------------------
    // Integer
    // -------------------------------------------------------------------------

    /** Returns the int value at {@code path}, or the builder default, or {@code 0}. */
    public int getInt(@NotNull String path) {
        return config.getInt(path, builder.defaultInt(path));
    }

    /** Returns the int value at {@code path}, or {@code fallback}. */
    public int getInt(@NotNull String path, int fallback) {
        return config.getInt(path, fallback);
    }

    // -------------------------------------------------------------------------
    // Long
    // -------------------------------------------------------------------------

    /** Returns the long value at {@code path}, or the builder default, or {@code 0L}. */
    public long getLong(@NotNull String path) {
        return config.getLong(path, builder.defaultLong(path));
    }

    /** Returns the long value at {@code path}, or {@code fallback}. */
    public long getLong(@NotNull String path, long fallback) {
        return config.getLong(path, fallback);
    }

    // -------------------------------------------------------------------------
    // Double
    // -------------------------------------------------------------------------

    /** Returns the double value at {@code path}, or the builder default, or {@code 0.0}. */
    public double getDouble(@NotNull String path) {
        return config.getDouble(path, builder.defaultDouble(path));
    }

    /** Returns the double value at {@code path}, or {@code fallback}. */
    public double getDouble(@NotNull String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    // -------------------------------------------------------------------------
    // Boolean
    // -------------------------------------------------------------------------

    /** Returns the boolean value at {@code path}, or the builder default, or {@code false}. */
    public boolean getBoolean(@NotNull String path) {
        return config.getBoolean(path, builder.defaultBoolean(path));
    }

    /** Returns the boolean value at {@code path}, or {@code fallback}. */
    public boolean getBoolean(@NotNull String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    // -------------------------------------------------------------------------
    // Lists
    // -------------------------------------------------------------------------

    /** Returns the string list at {@code path}, or an empty list if absent. */
    public @NotNull List<String> getStringList(@NotNull String path) {
        return config.getStringList(path);
    }

    /** Returns the integer list at {@code path}, or an empty list if absent. */
    public @NotNull List<Integer> getIntegerList(@NotNull String path) {
        return config.getIntegerList(path);
    }

    // -------------------------------------------------------------------------
    // Required access
    // -------------------------------------------------------------------------

    /**
     * Returns the raw value at {@code path} or throws if it is absent.
     *
     * @param path the YAML path
     * @return the raw value (never null)
     * @throws ConfigLoadException if the path is absent
     */
    public @NotNull Object require(@NotNull String path) {
        Object val = config.get(path);
        if (val == null) {
            throw new ConfigLoadException(
                    "Required config key '" + path + "' is absent in " + builder.getFileName());
        }
        return val;
    }

    // -------------------------------------------------------------------------
    // Existence
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the given path is set. */
    public boolean isSet(@NotNull String path) {
        return config.isSet(path);
    }

    /** Returns {@code true} if the given path exists in the config. */
    public boolean contains(@NotNull String path) {
        return config.contains(path);
    }

    // -------------------------------------------------------------------------
    // Raw access
    // -------------------------------------------------------------------------

    /**
     * Returns the underlying {@link FileConfiguration} for advanced operations.
     * Prefer the typed getters when possible.
     */
    public @NotNull FileConfiguration raw() {
        return config;
    }

    /** Returns the YAML file name this config was loaded from. */
    public @NotNull String getFileName() {
        return builder.getFileName();
    }

    @Override
    public String toString() {
        return "BuiltConfig{file='" + builder.getFileName() + "'}";
    }
}
package online.reaudacity.yabcl.builder;

import online.reaudacity.yabcl.log.YabclLogger;
import online.reaudacity.yabcl.registry.ConfigRegistry;
import online.reaudacity.yabcl.version.ConfigVersionManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Fluent builder for creating YABCL-managed configs without annotations.
 *
 * <p>Use this when you prefer a programmatic style or need to construct configs
 * dynamically at runtime (e.g. per-player, per-world, or dynamically-named files).</p>
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * BuiltConfig config = ConfigBuilder.create("settings.yml", "1.0")
 *     .header("My Plugin — Settings", "Do not edit config-version.")
 *     .comment("general.prefix", "The chat prefix shown before messages.")
 *     .field("general.prefix",   "<gray>[Plugin]</gray>")
 *     .field("limits.max-homes", 3)
 *     .field("limits.enabled",   true)
 *     .field("motd.lines",       List.of("Welcome!", "Enjoy your stay."))
 *     .build(plugin);
 *
 * String prefix = config.getString("general.prefix");
 * int max       = config.getInt("limits.max-homes");
 * }</pre>
 *
 * <h3>Migrations</h3>
 * <pre>{@code
 * BuiltConfig config = ConfigBuilder.create("settings.yml", "2.0")
 *     .field("general.prefix", "<gray>[Plugin]</gray>")
 *     .migrate("1.0", "2.0", cfg -> {
 *         if (cfg.isSet("prefix")) {
 *             cfg.set("general.prefix", cfg.get("prefix"));
 *             cfg.set("prefix", null);
 *         }
 *     })
 *     .build(plugin);
 * }</pre>
 *
 * <h3>Required fields</h3>
 * <pre>{@code
 * ConfigBuilder.create("db.yml", "1.0")
 *     .required("database.password")  // throws ConfigLoadException if absent
 *     .build(plugin);
 * }</pre>
 *
 * <h3>Reloading</h3>
 * <p>Call {@link BuiltConfig#reload()} on the returned instance at any time.
 * The builder is retained inside the {@link BuiltConfig}, so the full pipeline re-runs.</p>
 */
public final class ConfigBuilder {

    // -------------------------------------------------------------------------
    // Internal field descriptor
    // -------------------------------------------------------------------------

    private record FieldDef(
            @NotNull String path,
            @Nullable Object defaultValue,
            boolean required
    ) {}

    // -------------------------------------------------------------------------
    // Internal migration descriptor
    // -------------------------------------------------------------------------

    @FunctionalInterface
    public interface MigrationAction {
        void run(@NotNull FileConfiguration config);
    }

    private record MigrationDef(
            @NotNull String from,
            @NotNull String to,
            @NotNull MigrationAction action
    ) {}

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final @NotNull String fileName;
    private final @NotNull String version;

    private final Map<String, FieldDef>    fields     = new LinkedHashMap<>();
    private final Map<String, String[]>    comments   = new LinkedHashMap<>();
    private final List<MigrationDef>       migrations = new ArrayList<>();
    private @Nullable String[]             header     = null;

    private @Nullable JavaPlugin plugin; // set during build()

    // -------------------------------------------------------------------------
    // Constructor (private — use factory)
    // -------------------------------------------------------------------------

    private ConfigBuilder(@NotNull String fileName, @NotNull String version) {
        this.fileName = fileName;
        this.version  = version;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new builder for the given file name and schema version.
     *
     * @param fileName the YAML file name, relative to the plugin's data folder
     *                 (supports sub-folders: {@code "modules/economy.yml"})
     * @param version  the current schema version string
     * @return a new builder
     */
    public static @NotNull ConfigBuilder create(@NotNull String fileName, @NotNull String version) {
        return new ConfigBuilder(fileName, version);
    }

    // -------------------------------------------------------------------------
    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Declares a field with a default value. The default is written to disk on
     * first generation and used as a fallback when the key is absent in the file.
     *
     * @param path         dot-separated YAML path
     * @param defaultValue the default value (String, int, double, boolean, float, long, List)
     * @return {@code this} for chaining
     */
    public @NotNull ConfigBuilder field(@NotNull String path, @NotNull Object defaultValue) {
        fields.put(path, new FieldDef(path, defaultValue, false));
        return this;
    }

    /**
     * Declares a required field. {@link BuiltConfig#require(String)} semantics apply:
     * if the key is absent after load, a {@link online.reaudacity.yabcl.exception.ConfigLoadException}
     * is thrown.
     *
     * @param path dot-separated YAML path
     * @return {@code this} for chaining
     */
    public @NotNull ConfigBuilder required(@NotNull String path) {
        fields.put(path, new FieldDef(path, null, true));
        return this;
    }

    /**
     * Attaches an inline comment to be written above the given key on first file generation.
     *
     * @param path    dot-separated YAML path
     * @param comment one or more comment lines (each becomes a {@code #} line)
     * @return {@code this} for chaining
     */
    public @NotNull ConfigBuilder comment(@NotNull String path, @NotNull String... comment) {
        comments.put(path, comment);
        return this;
    }

    /**
     * Sets the file header comment written at the top of the YAML on first generation.
     *
     * @param lines one or more header lines
     * @return {@code this} for chaining
     */
    public @NotNull ConfigBuilder header(@NotNull String... lines) {
        this.header = lines;
        return this;
    }

    /**
     * Declares a migration step from one version to another.
     * Steps are executed in declaration order when the on-disk version is older.
     *
     * @param from   the version this step migrates from
     * @param to     the version this step produces
     * @param action a lambda that mutates the raw {@link FileConfiguration}
     * @return {@code this} for chaining
     */
    public @NotNull ConfigBuilder migrate(
            @NotNull String from,
            @NotNull String to,
            @NotNull MigrationAction action
    ) {
        migrations.add(new MigrationDef(from, to, action));
        return this;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Builds and loads the config. Runs the full YABCL pipeline:
     * resolve file → load YAML → migrate → write defaults → stamp version → save if needed.
     *
     * @param plugin the owning plugin
     * @return the loaded {@link BuiltConfig}
     */
    public @NotNull BuiltConfig build(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        BuiltConfig result = buildInternal();
        // Register with a name key derived from fileName so registry can track it
        ConfigRegistry.<BuiltConfig>register(
                (Class<BuiltConfig>) result.getClass(),
                result,
                () -> result.reload()
        );
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal build (also used by BuiltConfig#reload)
    // -------------------------------------------------------------------------

    @NotNull BuiltConfig buildInternal() {
        if (plugin == null) throw new IllegalStateException("ConfigBuilder.build(plugin) must be called first.");

        File file         = new File(plugin.getDataFolder(), fileName);
        boolean isNewFile = !file.exists();

        if (isNewFile) {
            file.getParentFile().mkdirs();
            InputStream bundled = plugin.getResource(fileName);
            if (bundled != null) {
                plugin.saveResource(fileName, false);
                YabclLogger.info("Saved default config: <yellow>" + fileName + "</yellow>");
            } else {
                try {
                    file.createNewFile();
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "YABCL: Could not create " + fileName, ex);
                }
            }
        }

        // Load YAML
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Apply bundled defaults
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration bundledDefaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(bundledDefaults);
        }

        // Migration
        boolean migrated = false;
        String storedVersion = ConfigVersionManager.readVersion(config);
        if (!isNewFile && storedVersion != null && !storedVersion.equals(version)) {
            migrated = runLambdaMigrations(config, storedVersion);
        }

        // Write header on first generation
        if (isNewFile && header != null && config instanceof YamlConfiguration yaml) {
            yaml.options().setHeader(Arrays.asList(header));
        }

        // Write declared defaults for missing keys
        for (FieldDef def : fields.values()) {
            if (def.required()) {
                if (!config.isSet(def.path())) {
                    throw new online.reaudacity.yabcl.exception.ConfigLoadException(
                            "Required key '" + def.path() + "' is absent in " + fileName);
                }
            } else if (def.defaultValue() != null && !config.isSet(def.path())) {
                config.set(def.path(), def.defaultValue());
                YabclLogger.debug("Wrote default for '" + def.path() + "' in " + fileName);
            }
        }

        // Stamp version
        ConfigVersionManager.stamp(config, version);

        // Save on new or migrated
        if (isNewFile || migrated) {
            try {
                config.save(file);
                YabclLogger.info("Config saved: <yellow>" + fileName + "</yellow>");
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "YABCL: Could not save " + fileName, ex);
            }
        }

        YabclLogger.info("Loaded <yellow>" + fileName + "</yellow> <dark_gray>v" + version + "</dark_gray>");
        return new BuiltConfig(config, this);
    }

    // -------------------------------------------------------------------------
    // Lambda migration runner
    // -------------------------------------------------------------------------

    private boolean runLambdaMigrations(@NotNull FileConfiguration config, @NotNull String storedVersion) {
        String current = storedVersion;
        int applied    = 0;

        while (!current.equals(version)) {
            String finalCurrent = current;
            MigrationDef step = migrations.stream()
                    .filter(m -> m.from().equals(finalCurrent))
                    .findFirst()
                    .orElse(null);

            if (step == null) {
                YabclLogger.warn("No builder migration from '" + current
                        + "' to '" + version + "' in " + fileName + ". Stopping.");
                break;
            }

            YabclLogger.info("Applying migration: <yellow>" + step.from()
                    + "</yellow> → <yellow>" + step.to() + "</yellow> (" + fileName + ")");
            try {
                step.action().run(config);
                current = step.to();
                config.set(ConfigVersionManager.VERSION_KEY, current);
                applied++;
            } catch (Exception ex) {
                YabclLogger.error("Migration " + step.from() + "→" + step.to()
                        + " failed in " + fileName, ex);
                break;
            }
        }

        return applied > 0;
    }

    // -------------------------------------------------------------------------
    // Default lookup helpers (used by BuiltConfig)
    // -------------------------------------------------------------------------

    @NotNull String getFileName() { return fileName; }

    @NotNull String getString(@NotNull String path) {
        FieldDef def = fields.get(path);
        if (def == null || def.defaultValue() == null) return "";
        return def.defaultValue().toString();
    }

    @NotNull String defaultString(@NotNull String path) { return getString(path); }

    int defaultInt(@NotNull String path) {
        FieldDef def = fields.get(path);
        if (def == null || def.defaultValue() == null) return 0;
        try { return Integer.parseInt(def.defaultValue().toString()); }
        catch (NumberFormatException e) { return 0; }
    }

    long defaultLong(@NotNull String path) {
        FieldDef def = fields.get(path);
        if (def == null || def.defaultValue() == null) return 0L;
        try { return Long.parseLong(def.defaultValue().toString()); }
        catch (NumberFormatException e) { return 0L; }
    }

    double defaultDouble(@NotNull String path) {
        FieldDef def = fields.get(path);
        if (def == null || def.defaultValue() == null) return 0.0;
        try { return Double.parseDouble(def.defaultValue().toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    boolean defaultBoolean(@NotNull String path) {
        FieldDef def = fields.get(path);
        if (def == null || def.defaultValue() == null) return false;
        return Boolean.parseBoolean(def.defaultValue().toString());
    }
}
package online.reaudacity.yabcl;

import online.reaudacity.yabcl.annotation.ConfigField;
import online.reaudacity.yabcl.annotation.ConfigMigration;
import online.reaudacity.yabcl.annotation.ConfigSection;
import online.reaudacity.yabcl.annotation.ConfigVersion;
import online.reaudacity.yabcl.codec.ConfigCodec;
import online.reaudacity.yabcl.exception.ConfigLoadException;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Abstract base class for annotation-driven YAML configuration.
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * @ConfigVersion(file = "config.yml", version = "1.0", header = {
 *     "My Plugin Configuration",
 *     "Managed by YABCL — do not edit config-version."
 * })
 * public class MyConfig extends YabclConfig {
 *
 *     @ConfigField(path = "general.prefix",
 *                  defaultValue = "<gray>[MyPlugin]</gray>",
 *                  comment = "The chat prefix.")
 *     public String prefix;
 *
 *     @ConfigField(path = "limits.max-homes", defaultValue = "3")
 *     public int maxHomes;
 *
 *     @ConfigSection(path = "database")
 *     public DatabaseSection database;
 *
 *     public static class DatabaseSection {
 *         @ConfigField(path = "host", defaultValue = "localhost")
 *         public String host;
 *         @ConfigField(path = "port", defaultValue = "3306")
 *         public int port;
 *     }
 *
 *     // Optional: migrate from v1.0 → v2.0
 *     @ConfigMigration(from = "1.0", to = "2.0")
 *     public void migrateV1(FileConfiguration cfg) {
 *         if (cfg.isSet("old-prefix")) {
 *             cfg.set("general.prefix", cfg.get("old-prefix"));
 *             cfg.set("old-prefix", null);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>Loading</h3>
 * <pre>{@code
 * // In onEnable:
 * ConfigLoadResult<MyConfig> result = YabclConfig.load(this, MyConfig.class);
 * if (result.isFailed()) { disable(); return; }
 * MyConfig config = result.require();
 * }</pre>
 *
 * <h3>Reloading</h3>
 * <pre>{@code
 * YabclConfig.reload(this, MyConfig.class);
 * // Or reload every registered config:
 * YabclConfig.reloadAll();
 * }</pre>
 *
 * <h3>Field population rules</h3>
 * <ul>
 *   <li>Public instance fields annotated with {@link ConfigField} are populated in declaration order.</li>
 *   <li>Fields annotated with {@link ConfigSection} have their type instantiated and recursively populated.</li>
 *   <li>Un-annotated fields are ignored.</li>
 *   <li>Fields may have Java initializer values — these act as a secondary fallback if
 *       no YAML value and no {@code defaultValue} are found.</li>
 * </ul>
 *
 * <h3>Path resolution</h3>
 * <p>If a {@code path} attribute is empty, the Java field name is used as the YAML key.
 * Section paths are prepended automatically when recursing into nested sections.</p>
 */
public abstract class YabclConfig {

    // -------------------------------------------------------------------------
    // Static entry points
    // -------------------------------------------------------------------------

    /**
     * Loads a config of the given class, populates its fields, and registers it
     * in the {@link ConfigRegistry} for later reload-all.
     *
     * @param plugin the owning plugin
     * @param clazz  the config class (must extend {@link YabclConfig})
     * @param <T>    the config type
     * @return the load result — check {@link ConfigLoadResult#isFailed()} before using
     */
    public static <T extends YabclConfig> @NotNull ConfigLoadResult<T> load(
            @NotNull JavaPlugin plugin,
            @NotNull Class<T> clazz
    ) {
        ConfigVersion versionAnnotation = clazz.getAnnotation(ConfigVersion.class);
        if (versionAnnotation == null) {
            return ConfigLoadResult.failed(
                    clazz.getSimpleName() + " is missing @ConfigVersion. YABCL cannot load it."
            );
        }

        T instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            return ConfigLoadResult.failed(
                    "Could not instantiate " + clazz.getSimpleName()
                    + ". Ensure it has a public no-arg constructor.", null
            );
        }

        ConfigLoadResult<T> result = instance.loadFromDisk(plugin, versionAnnotation);

        if (!result.isFailed()) {
            ConfigRegistry.register(clazz, result.require(), () -> reload(plugin, clazz));
        }

        return result;
    }

    /**
     * Reloads the config for the given class by re-running the load pipeline.
     * The updated instance replaces the one in the registry.
     *
     * @param plugin the owning plugin
     * @param clazz  the config class
     * @param <T>    the config type
     * @return the new load result
     */
    public static <T extends YabclConfig> @NotNull ConfigLoadResult<T> reload(
            @NotNull JavaPlugin plugin,
            @NotNull Class<T> clazz
    ) {
        YabclLogger.info("Reloading <yellow>" + clazz.getSimpleName() + "</yellow>...");
        return load(plugin, clazz);
    }

    /**
     * Triggers a reload of every config registered in the global {@link ConfigRegistry}.
     */
    public static void reloadAll() {
        ConfigRegistry.reloadAll();
    }

    // -------------------------------------------------------------------------
    // Instance load pipeline
    // -------------------------------------------------------------------------

    /**
     * The internal load pipeline. Resolves the file, loads YAML, runs migrations,
     * populates fields, stamps the version, and saves if the file was new or migrated.
     */
    @SuppressWarnings("unchecked")
    <T extends YabclConfig> @NotNull ConfigLoadResult<T> loadFromDisk(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigVersion versionAnnotation
    ) {
        List<String> warnings = new ArrayList<>();
        String fileName = versionAnnotation.file();
        String targetVersion = versionAnnotation.version();

        // ── Resolve file ────────────────────────────────────────────────────
        File file = new File(plugin.getDataFolder(), fileName);
        boolean isNewFile = !file.exists();

        if (isNewFile) {
            file.getParentFile().mkdirs();
            // Try to copy a bundled resource first
            InputStream bundled = plugin.getResource(fileName);
            if (bundled != null) {
                plugin.saveResource(fileName, false);
                YabclLogger.info("Saved default config: <yellow>" + fileName + "</yellow>");
            } else {
                try {
                    file.createNewFile();
                    YabclLogger.info("Created empty config: <yellow>" + fileName + "</yellow>");
                } catch (IOException ex) {
                    return ConfigLoadResult.failed("Could not create config file: " + fileName, (T) this);
                }
            }
        }

        // ── Load YAML ───────────────────────────────────────────────────────
        FileConfiguration yamlConfig = YamlConfiguration.loadConfiguration(file);

        // Apply bundled defaults so Bukkit knows fallback values
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            yamlConfig.setDefaults(defaults);
        }

        // ── Migration ───────────────────────────────────────────────────────
        boolean migrated = false;
        String storedVersion = ConfigVersionManager.readVersion(yamlConfig);

        if (!isNewFile && storedVersion != null && !storedVersion.equals(targetVersion)) {
            migrated = ConfigVersionManager.migrate(this, yamlConfig, targetVersion);
        }

        // ── Write file header (first generation) ───────────────────────────
        if (isNewFile && versionAnnotation.header().length > 0) {
            writeHeader(yamlConfig, versionAnnotation.header());
        }

        // ── Populate fields ─────────────────────────────────────────────────
        try {
            populateFields(this, getClass(), yamlConfig, "", warnings);
        } catch (ConfigLoadException ex) {
            return ConfigLoadResult.failed(ex.getMessage(), (T) this);
        }

        // ── Stamp version ────────────────────────────────────────────────────
        ConfigVersionManager.stamp(yamlConfig, targetVersion);

        // ── Write defaults / save on new file or migration ──────────────────
        if (isNewFile || migrated) {
            writeDefaults(this, getClass(), yamlConfig, "");
            try {
                yamlConfig.save(file);
                YabclLogger.info("Config saved: <yellow>" + fileName + "</yellow>");
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "YABCL: Could not save config: " + fileName, ex);
                warnings.add("Could not save config file after " + (isNewFile ? "creation" : "migration") + ": " + ex.getMessage());
            }
        }

        // ── Result ──────────────────────────────────────────────────────────
        YabclLogger.info("Loaded <yellow>" + getClass().getSimpleName()
                + "</yellow> <dark_gray>(" + fileName + " v" + targetVersion + ")</dark_gray>");

        return warnings.isEmpty()
                ? ConfigLoadResult.success((T) this)
                : ConfigLoadResult.partial((T) this, warnings);
    }

    // -------------------------------------------------------------------------
    // Field population (recursive)
    // -------------------------------------------------------------------------

    /**
     * Iterates all declared fields of {@code clazz}, populating those annotated with
     * {@link ConfigField} or recursing into those annotated with {@link ConfigSection}.
     *
     * @param target    the object instance to populate
     * @param clazz     the class whose fields to scan (may be a section POJO)
     * @param config    the YAML config to read from
     * @param prefix    the path prefix accumulated from parent sections (e.g. {@code "database."})
     * @param warnings  accumulator for non-fatal warnings
     */
    private static void populateFields(
            @NotNull Object target,
            @NotNull Class<?> clazz,
            @NotNull FileConfiguration config,
            @NotNull String prefix,
            @NotNull List<String> warnings
    ) {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            // ── @ConfigSection ─────────────────────────────────────────────
            ConfigSection sectionAnnotation = field.getAnnotation(ConfigSection.class);
            if (sectionAnnotation != null) {
                String sectionPath = resolveKey(sectionAnnotation.path(), field.getName());
                String fullSectionPath = prefix + sectionPath;

                try {
                    Object sectionInstance = field.getType().getDeclaredConstructor().newInstance();
                    field.set(target, sectionInstance);
                    // Recurse with the accumulated path prefix
                    populateFields(sectionInstance, field.getType(), config,
                            fullSectionPath + ".", warnings);
                } catch (Exception ex) {
                    throw new ConfigLoadException(
                            "Could not instantiate @ConfigSection type "
                            + field.getType().getSimpleName()
                            + " for field '" + field.getName() + "'.", ex);
                }
                continue;
            }

            // ── @ConfigField ───────────────────────────────────────────────
            ConfigField fieldAnnotation = field.getAnnotation(ConfigField.class);
            if (fieldAnnotation == null) continue;

            String key      = resolveKey(fieldAnnotation.path(), field.getName());
            String fullPath = prefix + key;
            String defVal   = fieldAnnotation.defaultValue().isEmpty() ? null : fieldAnnotation.defaultValue();

            if (!ConfigCodec.isSupported(field.getGenericType())) {
                YabclLogger.warn("Field '" + field.getName() + "' in " + clazz.getSimpleName()
                        + " has unsupported type " + field.getGenericType()
                        + ". Annotate with @ConfigSection for nested POJOs. Skipping.");
                warnings.add("Unsupported type for field '" + field.getName() + "' — skipped.");
                continue;
            }

            try {
                Object value = ConfigCodec.read(
                        config, fullPath, field.getGenericType(),
                        defVal, fieldAnnotation.required(), field.getName()
                );

                if (value != null) {
                    field.set(target, value);
                } else if (defVal == null && !fieldAnnotation.required()) {
                    // Key absent, no default — leave the Java initializer value in place
                    warnings.add("Key '" + fullPath + "' absent and has no defaultValue — field '"
                            + field.getName() + "' uses its Java initializer.");
                }

            } catch (ConfigLoadException ex) {
                throw ex; // propagate required-field failures
            } catch (Exception ex) {
                throw new ConfigLoadException(
                        "Could not set field '" + field.getName() + "' at path '" + fullPath + "'", ex);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Write defaults (first-time generation)
    // -------------------------------------------------------------------------

    private static void writeDefaults(
            @NotNull Object target,
            @NotNull Class<?> clazz,
            @NotNull FileConfiguration config,
            @NotNull String prefix
    ) {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            ConfigSection sectionAnnotation = field.getAnnotation(ConfigSection.class);
            if (sectionAnnotation != null) {
                String sectionPath = resolveKey(sectionAnnotation.path(), field.getName());
                try {
                    Object sectionInstance = field.get(target);
                    if (sectionInstance != null) {
                        writeDefaults(sectionInstance, field.getType(), config, prefix + sectionPath + ".");
                    }
                } catch (IllegalAccessException ignored) {}
                continue;
            }

            ConfigField fieldAnnotation = field.getAnnotation(ConfigField.class);
            if (fieldAnnotation == null || fieldAnnotation.defaultValue().isEmpty()) continue;

            String key      = resolveKey(fieldAnnotation.path(), field.getName());
            String fullPath = prefix + key;
            ConfigCodec.writeDefault(config, fullPath, fieldAnnotation.defaultValue(), field.getGenericType());
        }
    }

    // -------------------------------------------------------------------------
    // Header
    // -------------------------------------------------------------------------

    private static void writeHeader(@NotNull FileConfiguration config, @NotNull String[] lines) {
        // Bukkit's YamlConfiguration doesn't have a setHeader method in all versions;
        // use options() where available via cast
        if (config instanceof YamlConfiguration yaml) {
            yaml.options().setHeader(java.util.Arrays.asList(lines));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code declared} if non-empty, otherwise falls back to {@code fieldName}.
     */
    private static @NotNull String resolveKey(@NotNull String declared, @NotNull String fieldName) {
        return declared.isEmpty() ? fieldName : declared;
    }
}
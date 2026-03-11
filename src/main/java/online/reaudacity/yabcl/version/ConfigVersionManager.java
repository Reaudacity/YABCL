package online.reaudacity.yabcl.version;

import online.reaudacity.yabcl.annotation.ConfigMigration;
import online.reaudacity.yabcl.log.YabclLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Discovers {@link ConfigMigration}-annotated methods on a config class and runs
 * them in sequence to bring an old config file up to the current schema version.
 *
 * <h3>How migration is run</h3>
 * <ol>
 *   <li>YABCL reads the {@code config-version} key from the loaded YAML file.</li>
 *   <li>If it matches the current version declared in {@link online.reaudacity.yabcl.annotation.ConfigVersion#version()},
 *       no migration is needed.</li>
 *   <li>Otherwise, {@link #migrate} is called. It discovers all {@code @ConfigMigration} methods,
 *       sorts them by their {@code from} field, and repeatedly applies the first method whose
 *       {@code from} matches the current file version — advancing the version after each step.</li>
 *   <li>Migration stops when the file version matches the target, or when no applicable
 *       migration method is found (in which case a warning is logged).</li>
 * </ol>
 *
 * <h3>Version key</h3>
 * <p>YABCL uses {@value #VERSION_KEY} as the YAML key for the schema version. This key is
 * managed automatically — do not set it manually in your migration methods.</p>
 */
public final class ConfigVersionManager {

    /** The YAML key where YABCL stores the schema version. */
    public static final String VERSION_KEY = "config-version";

    private ConfigVersionManager() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reads the version stored in {@code config} and runs the migration chain on
     * {@code instance} until the stored version matches {@code targetVersion}.
     *
     * @param instance      the config object that declares {@link ConfigMigration} methods
     * @param config        the raw YAML configuration to mutate
     * @param targetVersion the desired final version string
     * @return {@code true} if the config was migrated, {@code false} if already current
     */
    public static boolean migrate(
            @NotNull Object instance,
            @NotNull FileConfiguration config,
            @NotNull String targetVersion
    ) {
        String currentVersion = config.getString(VERSION_KEY, null);

        if (currentVersion == null) {
            YabclLogger.debug("No '" + VERSION_KEY + "' found in file — treating as first load, skipping migration.");
            return false;
        }

        if (currentVersion.equals(targetVersion)) {
            YabclLogger.debug("Config version '" + currentVersion + "' matches target — no migration needed.");
            return false;
        }

        YabclLogger.info("Config version mismatch: file is <yellow>" + currentVersion
                + "</yellow>, expected <yellow>" + targetVersion + "</yellow>. Running migrations.");

        List<MigrationStep> steps = discoverSteps(instance.getClass());

        if (steps.isEmpty()) {
            YabclLogger.warn("No @ConfigMigration methods found on "
                    + instance.getClass().getSimpleName()
                    + " — cannot migrate from '" + currentVersion + "' to '" + targetVersion + "'.");
            return false;
        }

        String version = currentVersion;
        int applied    = 0;

        while (!version.equals(targetVersion)) {
            MigrationStep step = findStep(steps, version);
            if (step == null) {
                YabclLogger.warn("No migration found from version '" + version
                        + "' to '" + targetVersion + "' in "
                        + instance.getClass().getSimpleName()
                        + ". Stopping migration chain at '" + version + "'.");
                break;
            }

            YabclLogger.info("Applying migration: <yellow>" + step.from + "</yellow>"
                    + " → <yellow>" + step.to + "</yellow>");

            try {
                step.method.invoke(instance, config);
                version = step.to;
                config.set(VERSION_KEY, version);
                applied++;
            } catch (Exception ex) {
                YabclLogger.error("Migration from '" + step.from + "' to '" + step.to
                        + "' threw an exception — stopping migration", ex);
                break;
            }
        }

        if (applied > 0) {
            YabclLogger.info("Migration complete. Applied <yellow>" + applied
                    + "</yellow> step(s). Config is now at version <yellow>" + version + "</yellow>.");
        }

        return applied > 0;
    }

    /**
     * Writes the current version to {@code config} under {@link #VERSION_KEY}.
     * Called after a successful load or migration.
     *
     * @param config  the config to stamp
     * @param version the version string to write
     */
    public static void stamp(@NotNull FileConfiguration config, @NotNull String version) {
        config.set(VERSION_KEY, version);
    }

    /**
     * Reads the version stored in {@code config}, or {@code null} if not set.
     *
     * @param config the config to inspect
     * @return the stored version string, or {@code null}
     */
    public static @Nullable String readVersion(@NotNull FileConfiguration config) {
        return config.getString(VERSION_KEY, null);
    }

    // -------------------------------------------------------------------------
    // Discovery
    // -------------------------------------------------------------------------

    private static @NotNull List<MigrationStep> discoverSteps(@NotNull Class<?> clazz) {
        List<MigrationStep> steps = new ArrayList<>();
        for (Method method : clazz.getMethods()) {
            ConfigMigration annotation = method.getAnnotation(ConfigMigration.class);
            if (annotation == null) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !FileConfiguration.class.isAssignableFrom(params[0])) {
                YabclLogger.warn("@ConfigMigration on " + method.getName()
                        + " in " + clazz.getSimpleName()
                        + " has wrong signature — expected (FileConfiguration). Skipping.");
                continue;
            }

            steps.add(new MigrationStep(annotation.from(), annotation.to(), method));
        }
        // Sort by 'from' — simple string sort; works for semver-ish or integer versions
        steps.sort(Comparator.comparing(s -> s.from));
        return steps;
    }

    private static @Nullable MigrationStep findStep(@NotNull List<MigrationStep> steps, @NotNull String from) {
        for (MigrationStep step : steps) {
            if (step.from.equals(from)) return step;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Internal record
    // -------------------------------------------------------------------------

    private record MigrationStep(@NotNull String from, @NotNull String to, @NotNull Method method) {}
}
package online.reaudacity.yabcl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one or more version migrations for an annotated config class.
 *
 * <p>Annotate a method with {@code @ConfigMigration} to have YABCL call it automatically
 * when it detects that the on-disk config version is older than the current schema version
 * declared in {@link ConfigVersion#version()}.</p>
 *
 * <h3>Method signature</h3>
 * <p>The annotated method must be {@code public}, belong to the same class as
 * {@link ConfigVersion}, and accept exactly one argument of type
 * {@link org.bukkit.configuration.file.FileConfiguration}:</p>
 * <pre>{@code
 * @ConfigMigration(from = "1.0", to = "2.0")
 * public void migrateV1toV2(FileConfiguration config) {
 *     // e.g. rename a key
 *     if (config.isSet("old-key")) {
 *         config.set("new-key", config.get("old-key"));
 *         config.set("old-key", null);
 *     }
 *     // Add a new key that didn't exist in v1
 *     if (!config.isSet("new-feature.enabled")) {
 *         config.set("new-feature.enabled", false);
 *     }
 * }
 * }</pre>
 *
 * <h3>Migration chain</h3>
 * <p>YABCL sorts all {@code @ConfigMigration} methods by their {@code from} version
 * and runs them in order. If a file is at version {@code "1.0"} and the current schema
 * is {@code "3.0"}, YABCL will run {@code 1.0→2.0} then {@code 2.0→3.0} in sequence,
 * as long as both migration methods are declared.</p>
 *
 * <h3>Multiple migrations on one class</h3>
 * <p>Declare one method per migration step:</p>
 * <pre>{@code
 * @ConfigMigration(from = "1.0", to = "2.0")
 * public void migrate1to2(FileConfiguration cfg) { ... }
 *
 * @ConfigMigration(from = "2.0", to = "3.0")
 * public void migrate2to3(FileConfiguration cfg) { ... }
 * }</pre>
 *
 * <h3>No migration available</h3>
 * <p>If YABCL cannot find a migration path between the on-disk version and the current
 * schema, it logs a warning and proceeds with best-effort field population — missing keys
 * fall back to their {@code defaultValue}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConfigMigration {

    /**
     * The config version this migration step starts from.
     * Must match a version string previously declared in {@link ConfigVersion#version()}.
     */
    String from();

    /**
     * The config version this migration step produces.
     * YABCL updates the on-disk {@code config-version} key to this value after the method runs.
     */
    String to();
}
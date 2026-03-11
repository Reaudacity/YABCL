package online.reaudacity.yabcl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the YAML file and current schema version for an annotated config class.
 *
 * <p>Place this on a class that extends {@link online.reaudacity.yabcl.YabclConfig}:</p>
 * <pre>{@code
 * @ConfigVersion(file = "settings.yml", version = "2.0")
 * public class SettingsConfig extends YabclConfig {
 *     @ConfigField(path = "max-players", defaultValue = "20")
 *     public int maxPlayers;
 * }
 * }</pre>
 *
 * <h3>Version tracking</h3>
 * <p>YABCL writes the declared {@code version} string under the {@code config-version} key
 * whenever a config is saved. On load, if the file's stored version differs from the
 * annotation's {@code version}, YABCL runs any matching {@link ConfigMigration} steps
 * before populating fields.</p>
 *
 * <h3>Sub-folder support</h3>
 * <p>Use forward slashes in {@code file} to place the config in a sub-folder inside the
 * plugin's data folder:</p>
 * <pre>{@code
 * @ConfigVersion(file = "modules/economy.yml", version = "1.0")
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigVersion {

    /**
     * The YAML file name, relative to the plugin's data folder.
     * Sub-folders are expressed with {@code /} separators.
     *
     * <p>Example: {@code "settings.yml"} or {@code "modules/economy.yml"}</p>
     */
    String file();

    /**
     * The current schema version string. Written to the config file under the
     * {@code config-version} key and compared against the file on load to detect
     * stale configs that need migration.
     *
     * <p>Any format works — SemVer ({@code "1.2.0"}), integers ({@code "3"}), dates, etc.</p>
     */
    String version();

    /**
     * Optional header comment written at the top of the YAML file when it is first generated.
     * Multi-line comments are supported; each element becomes a separate {@code #} line.
     *
     * <pre>{@code
     * header = {
     *     "Settings for My Plugin",
     *     "Do not edit config-version — YABCL manages it automatically."
     * }
     * }</pre>
     */
    String[] header() default {};
}
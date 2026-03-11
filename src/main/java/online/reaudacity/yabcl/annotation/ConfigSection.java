package online.reaudacity.yabcl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a nested configuration section backed by a plain Java object (POJO).
 *
 * <p>The field's type must be a concrete class with a public no-arg constructor.
 * YABCL will recursively scan its fields for {@link ConfigField} and further
 * {@link ConfigSection} annotations, mapping each one under the declared {@link #path}.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Nested POJO
 * public static class DatabaseSection {
 *     @ConfigField(path = "host",     defaultValue = "localhost")
 *     public String host;
 *
 *     @ConfigField(path = "port",     defaultValue = "3306")
 *     public int port;
 *
 *     @ConfigField(path = "password", required = true)
 *     public String password;
 * }
 *
 * // Parent config
 * @ConfigVersion(file = "config.yml", version = "1.0")
 * public class MyConfig extends YabclConfig {
 *     @ConfigSection(path = "database")
 *     public DatabaseSection database;
 * }
 * }</pre>
 *
 * <p>This maps to the following YAML structure:</p>
 * <pre>
 * database:
 *   host: localhost
 *   port: 3306
 *   password: secret
 * </pre>
 *
 * <h3>Sections can be nested arbitrarily deep</h3>
 * <p>A {@code DatabaseSection} field may itself contain another {@code @ConfigSection}
 * field, and YABCL will descend recursively.</p>
 *
 * <h3>Comments</h3>
 * <p>{@code comment} lines are written above the section key on first file generation.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigSection {

    /**
     * Dot-separated YAML path for this section (e.g. {@code "database"} or {@code "modules.economy"}).
     * If empty, the field's Java name is used as the section key.
     */
    String path() default "";

    /**
     * Optional comment lines written above this section key on first file generation.
     * Each element becomes a separate {@code #} line.
     */
    String[] comment() default {};
}
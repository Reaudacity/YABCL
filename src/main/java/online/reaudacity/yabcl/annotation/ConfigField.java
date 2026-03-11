package online.reaudacity.yabcl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a Java field to a YAML config path, with an optional default value and inline comment.
 *
 * <h3>Supported field types</h3>
 * <ul>
 *   <li>{@code String}</li>
 *   <li>{@code int} / {@code Integer}</li>
 *   <li>{@code long} / {@code Long}</li>
 *   <li>{@code double} / {@code Double}</li>
 *   <li>{@code float} / {@code Float}</li>
 *   <li>{@code boolean} / {@code Boolean}</li>
 *   <li>{@code List<String>}</li>
 *   <li>{@code List<Integer>}</li>
 *   <li>{@code List<Double>}</li>
 * </ul>
 * <p>For nested POJO objects, use {@link ConfigSection} instead.</p>
 *
 * <h3>Basic usage</h3>
 * <pre>{@code
 * @ConfigField(path = "general.prefix", defaultValue = "<gray>[Plugin]</gray>",
 *              comment = "The chat prefix shown before plugin messages.")
 * public String prefix;
 *
 * @ConfigField(path = "limits.max-homes", defaultValue = "3",
 *              comment = "Maximum homes a player may set.")
 * public int maxHomes;
 * }</pre>
 *
 * <h3>Required fields</h3>
 * <p>Set {@code required = true} to cause a {@link online.reaudacity.yabcl.exception.ConfigLoadException}
 * if the key is absent from the YAML file and no {@code defaultValue} is provided.</p>
 *
 * <h3>Default values</h3>
 * <p>{@code defaultValue} is always a {@code String} and is coerced to the field's declared type
 * at load time. For list types, separate values with a newline character ({@code \n}) or use
 * a comma-delimited string — YABCL tries both.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigField {

    /**
     * Dot-separated YAML path for this field (e.g. {@code "general.prefix"}).
     * If empty, the field's Java name is used as a single-level key.
     */
    String path() default "";

    /**
     * Default value as a string. Coerced to the field type at load time.
     * If empty and {@code required} is {@code false}, the field is left at its Java initializer value.
     */
    String defaultValue() default "";

    /**
     * Optional inline comment written above this key in the YAML file on first generation.
     * Multi-line comments are supported — each element becomes its own {@code #} line.
     */
    String[] comment() default {};

    /**
     * If {@code true}, YABCL throws a {@link online.reaudacity.yabcl.exception.ConfigLoadException}
     * when this path is absent and no {@code defaultValue} is provided.
     * Defaults to {@code false}.
     */
    boolean required() default false;
}
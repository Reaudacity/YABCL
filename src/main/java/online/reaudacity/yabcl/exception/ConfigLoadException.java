package online.reaudacity.yabcl.exception;

/**
 * Thrown when YABCL cannot load a config due to a structural or I/O error.
 *
 * <p>Common causes:</p>
 * <ul>
 *   <li>A {@code required = true} field is absent and has no {@code defaultValue}</li>
 *   <li>The YAML file cannot be read or is malformed</li>
 *   <li>A {@link online.reaudacity.yabcl.annotation.ConfigVersion} annotation is missing
 *       from the class</li>
 *   <li>A nested {@link online.reaudacity.yabcl.annotation.ConfigSection} type cannot be
 *       instantiated (no no-arg constructor)</li>
 * </ul>
 */
public class ConfigLoadException extends RuntimeException {

    public ConfigLoadException(String message) {
        super(message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
package online.reaudacity.yabcl.codec;

import online.reaudacity.yabcl.exception.ConfigLoadException;
import online.reaudacity.yabcl.log.YabclLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads a value from a {@link FileConfiguration} and coerces it to the requested Java type.
 *
 * <h3>Supported types</h3>
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
 *
 * <p>For nested section types, see
 * {@link online.reaudacity.yabcl.YabclConfig} which recurses into them
 * rather than delegating to this codec.</p>
 */
public final class ConfigCodec {

    private ConfigCodec() {}

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Reads the value at {@code path} from {@code config}, coercing it to {@code targetType}.
     * If the key is absent, {@code defaultRaw} is coerced and returned instead.
     *
     * @param config      the source config
     * @param path        dot-separated YAML path
     * @param targetType  the Java type to coerce into (use {@code field.getGenericType()} for lists)
     * @param defaultRaw  the default string value, or {@code null} if no default is declared
     * @param required    if {@code true} and neither the key nor a default exists, throws
     * @param fieldName   the Java field name, used in log messages
     * @return the coerced value, or {@code null} if absent and not required
     * @throws ConfigLoadException if {@code required} is {@code true} and no value can be resolved
     */
    public static @Nullable Object read(
            @NotNull FileConfiguration config,
            @NotNull String path,
            @NotNull Type targetType,
            @Nullable String defaultRaw,
            boolean required,
            @NotNull String fieldName
    ) {
        boolean hasDefault = defaultRaw != null && !defaultRaw.isEmpty();

        if (!config.isSet(path)) {
            if (hasDefault) {
                YabclLogger.debug("Key '" + path + "' missing — using default: " + defaultRaw);
                return coerce(defaultRaw, targetType, path);
            }
            if (required) {
                throw new ConfigLoadException(
                        "Required config key '" + path + "' (field: " + fieldName
                        + ") is absent and has no defaultValue.");
            }
            return null;
        }

        Object raw = config.get(path);
        return coerceFromObject(raw, targetType, path, defaultRaw);
    }

    // -------------------------------------------------------------------------
    // Write default
    // -------------------------------------------------------------------------

    /**
     * Writes {@code defaultRaw} to {@code config} at {@code path} if the key is not already set.
     * Used during first-time file generation.
     *
     * @param config     the config to write to
     * @param path       the dot-separated YAML path
     * @param defaultRaw the raw default string value
     * @param targetType the Java type, used to coerce the value before writing
     */
    public static void writeDefault(
            @NotNull FileConfiguration config,
            @NotNull String path,
            @NotNull String defaultRaw,
            @NotNull Type targetType
    ) {
        if (!config.isSet(path)) {
            Object value = coerce(defaultRaw, targetType, path);
            if (value != null) config.set(path, value);
        }
    }

    // -------------------------------------------------------------------------
    // Type coercion — raw string → target type
    // -------------------------------------------------------------------------

    /**
     * Coerces a raw string (typically from a {@code defaultValue}) to the target type.
     */
    public static @Nullable Object coerce(
            @NotNull String raw,
            @NotNull Type targetType,
            @NotNull String path
    ) {
        try {
            if (targetType == String.class) return raw;
            if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(raw.trim());
            if (targetType == long.class || targetType == Long.class)   return Long.parseLong(raw.trim());
            if (targetType == double.class || targetType == Double.class) return Double.parseDouble(raw.trim());
            if (targetType == float.class || targetType == Float.class)  return Float.parseFloat(raw.trim());
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(raw.trim());

            if (targetType instanceof ParameterizedType pt) {
                Type listElement = pt.getActualTypeArguments()[0];
                List<String> parts = splitListDefault(raw);

                if (listElement == String.class)  return parts;
                if (listElement == Integer.class) return parts.stream().map(s -> Integer.parseInt(s.trim())).collect(Collectors.toList());
                if (listElement == Double.class)  return parts.stream().map(s -> Double.parseDouble(s.trim())).collect(Collectors.toList());
            }

        } catch (Exception ex) {
            YabclLogger.warn("Could not coerce default value '" + raw
                    + "' for path '" + path + "': " + ex.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Type coercion — YAML object → target type
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static @Nullable Object coerceFromObject(
            @Nullable Object raw,
            @NotNull Type targetType,
            @NotNull String path,
            @Nullable String fallback
    ) {
        if (raw == null) return fallback != null ? coerce(fallback, targetType, path) : null;

        try {
            if (targetType == String.class)  return raw.toString();
            if (targetType == int.class     || targetType == Integer.class) return toInt(raw);
            if (targetType == long.class    || targetType == Long.class)    return toLong(raw);
            if (targetType == double.class  || targetType == Double.class)  return toDouble(raw);
            if (targetType == float.class   || targetType == Float.class)   return toFloat(raw);
            if (targetType == boolean.class || targetType == Boolean.class) return toBoolean(raw);

            if (targetType instanceof ParameterizedType pt && raw instanceof List) {
                Type listElement = pt.getActualTypeArguments()[0];
                List<?> list = (List<?>) raw;

                if (listElement == String.class)  return list.stream().map(Object::toString).collect(Collectors.toList());
                if (listElement == Integer.class) return list.stream().map(e -> toInt(e)).collect(Collectors.toList());
                if (listElement == Double.class)  return list.stream().map(e -> toDouble(e)).collect(Collectors.toList());
            }

        } catch (Exception ex) {
            YabclLogger.warn("Type coercion failed for path '" + path + "' (value: " + raw
                    + "): " + ex.getMessage()
                    + (fallback != null ? " — using default" : ""));
            if (fallback != null) return coerce(fallback, targetType, path);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Primitive converters
    // -------------------------------------------------------------------------

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString().trim());
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString().trim());
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString().trim());
    }

    private static float toFloat(Object v) {
        if (v instanceof Number n) return n.floatValue();
        return Float.parseFloat(v.toString().trim());
    }

    private static boolean toBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString().trim());
    }

    // -------------------------------------------------------------------------
    // List default parsing
    // -------------------------------------------------------------------------

    /**
     * Splits a list default string. Tries newline first ({@code \n}), then comma.
     *
     * <pre>
     * "alpha\nbeta\ngamma" → ["alpha", "beta", "gamma"]
     * "alpha,beta,gamma"   → ["alpha", "beta", "gamma"]
     * "alpha"              → ["alpha"]
     * </pre>
     */
    private static List<String> splitListDefault(@NotNull String raw) {
        if (raw.contains("\n")) {
            return Arrays.stream(raw.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        if (raw.contains(",")) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return List.of(raw.trim());
    }

    // -------------------------------------------------------------------------
    // Type support check
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given type is directly supported by this codec
     * (i.e. not a section type that needs recursive handling).
     */
    public static boolean isSupported(@NotNull Type type) {
        if (type == String.class)  return true;
        if (type == int.class     || type == Integer.class) return true;
        if (type == long.class    || type == Long.class)    return true;
        if (type == double.class  || type == Double.class)  return true;
        if (type == float.class   || type == Float.class)   return true;
        if (type == boolean.class || type == Boolean.class) return true;
        if (type instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw != List.class) return false;
            Type elem = pt.getActualTypeArguments()[0];
            return elem == String.class || elem == Integer.class || elem == Double.class;
        }
        return false;
    }
}
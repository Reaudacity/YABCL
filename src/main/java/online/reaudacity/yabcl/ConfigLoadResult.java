package online.reaudacity.yabcl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of a YABCL config load or reload operation.
 *
 * <p>Holds the outcome status, any warnings (non-fatal issues like keys falling back to
 * defaults), and the error message if the load failed entirely.</p>
 *
 * <h3>Checking results</h3>
 * <pre>{@code
 * ConfigLoadResult result = YabclConfig.load(plugin, MyConfig.class);
 *
 * if (result.isFailed()) {
 *     logger.severe("Config failed to load: " + result.getError());
 *     return;
 * }
 * if (result.hasWarnings()) {
 *     result.getWarnings().forEach(w -> logger.warning("Config warning: " + w));
 * }
 * MyConfig config = result.get(); // guaranteed non-null when !isFailed()
 * }</pre>
 */
public final class ConfigLoadResult<T> {

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    public enum Status {
        /** All fields populated without issues. */
        SUCCESS,
        /** Fields populated, but some warnings were generated (e.g. missing keys using defaults). */
        PARTIAL,
        /** Load failed — the config instance may be partially populated or null. */
        FAILED
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final @NotNull Status status;
    private final @Nullable T config;
    private final @NotNull List<String> warnings;
    private final @Nullable String error;

    private ConfigLoadResult(
            @NotNull Status status,
            @Nullable T config,
            @NotNull List<String> warnings,
            @Nullable String error
    ) {
        this.status   = status;
        this.config   = config;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.error    = error;
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /** Creates a fully successful result with no warnings. */
    public static <T> ConfigLoadResult<T> success(@NotNull T config) {
        return new ConfigLoadResult<>(Status.SUCCESS, config, List.of(), null);
    }

    /** Creates a partial result: config loaded but with one or more warnings. */
    public static <T> ConfigLoadResult<T> partial(@NotNull T config, @NotNull List<String> warnings) {
        return new ConfigLoadResult<>(Status.PARTIAL, config, warnings, null);
    }

    /** Creates a failed result with an error message. Config may be null. */
    public static <T> ConfigLoadResult<T> failed(@NotNull String error) {
        return new ConfigLoadResult<>(Status.FAILED, null, List.of(), error);
    }

    /** Creates a failed result with an error message and a partially-populated config. */
    public static <T> ConfigLoadResult<T> failed(@NotNull String error, @Nullable T partialConfig) {
        return new ConfigLoadResult<>(Status.FAILED, partialConfig, List.of(), error);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the outcome status of this load operation. */
    public @NotNull Status getStatus()          { return status; }

    /** Returns {@code true} if the load succeeded (status is SUCCESS or PARTIAL). */
    public boolean isSuccess()                  { return status != Status.FAILED; }

    /** Returns {@code true} if the load failed entirely. */
    public boolean isFailed()                   { return status == Status.FAILED; }

    /** Returns {@code true} if there are non-fatal warnings. */
    public boolean hasWarnings()                { return !warnings.isEmpty(); }

    /** Returns an immutable list of warning messages. Empty if none. */
    public @NotNull List<String> getWarnings()  { return warnings; }

    /**
     * Returns the error message if the load failed, or {@code null} if it succeeded.
     *
     * @return the error string, or {@code null}
     */
    public @Nullable String getError()          { return error; }

    /**
     * Returns the loaded config instance.
     *
     * <p>Non-null when {@link #isSuccess()} is {@code true}.
     * May be {@code null} or partially populated when {@link #isFailed()} is {@code true}.</p>
     *
     * @return the config instance, or {@code null}
     */
    public @Nullable T get()                    { return config; }

    /**
     * Returns the config instance or throws if the load failed.
     *
     * @return the config instance (never null)
     * @throws IllegalStateException if the load failed
     */
    public @NotNull T require() {
        if (config == null) {
            throw new IllegalStateException(
                    "YABCL config load failed — cannot call require(). Error: " + error
            );
        }
        return config;
    }

    @Override
    public String toString() {
        return "ConfigLoadResult{status=" + status
                + (error != null ? ", error='" + error + "'" : "")
                + ", warnings=" + warnings.size()
                + "}";
    }
}
package online.reaudacity.yabcl.log;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal logger for YABCL.
 *
 * <p>Mirrors the {@code MessageUtil.send(null, ...)} pattern from the host plugin:
 * all messages are formatted with MiniMessage and sent to the server console.
 * A prefix tag is prepended to every message so operators can identify YABCL output
 * at a glance.</p>
 *
 * <h3>Log levels</h3>
 * <ul>
 *   <li>{@link #info}  — normal operational messages</li>
 *   <li>{@link #warn}  — non-fatal problems (missing keys, using defaults)</li>
 *   <li>{@link #error} — unrecoverable errors; always includes the cause if available</li>
 *   <li>{@link #debug} — verbose output, only emitted when {@link #setDebug} is {@code true}</li>
 * </ul>
 *
 * <p>The default prefix is {@code <dark_aqua>[YABCL]</dark_aqua>}.
 * Provide a custom one at startup with {@link #setPrefix}.</p>
 */
public final class YabclLogger {

    // -------------------------------------------------------------------------
    // Defaults
    // -------------------------------------------------------------------------

    private static final String DEFAULT_PREFIX = "<dark_aqua>[YABCL]</dark_aqua> ";

    private static String prefix = DEFAULT_PREFIX;
    private static boolean debug = false;

    // Use Paper's logger directly for reliability before the plugin fully boots
    private static Logger logger = Bukkit.getLogger();

    private YabclLogger() {}

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /**
     * Overrides the console prefix. Call once during plugin {@code onLoad} or {@code onEnable}.
     *
     * @param miniMessagePrefix a MiniMessage-formatted prefix, e.g. {@code "<aqua>[MyPlugin/YABCL]</aqua> "}
     */
    public static void setPrefix(@NotNull String miniMessagePrefix) {
        prefix = miniMessagePrefix;
    }

    /**
     * Enables or disables debug output. Off by default.
     *
     * @param enabled {@code true} to print debug messages
     */
    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    /**
     * Overrides the underlying {@link Logger} instance. Defaults to {@link Bukkit#getLogger()}.
     *
     * @param pluginLogger the plugin's own logger for namespaced output
     */
    public static void setLogger(@NotNull Logger pluginLogger) {
        logger = pluginLogger;
    }

    // -------------------------------------------------------------------------
    // Log methods
    // -------------------------------------------------------------------------

    /**
     * Logs an informational message.
     *
     * @param message MiniMessage-formatted message
     */
    public static void info(@NotNull String message) {
        log(Level.INFO, "<gray>" + message + "</gray>");
    }

    /**
     * Logs a warning — a non-fatal issue such as a missing key falling back to a default.
     *
     * @param message MiniMessage-formatted message
     */
    public static void warn(@NotNull String message) {
        log(Level.WARNING, "<yellow>⚠ " + message + "</yellow>");
    }

    /**
     * Logs an error with no associated throwable.
     *
     * @param message MiniMessage-formatted message
     */
    public static void error(@NotNull String message) {
        log(Level.SEVERE, "<red>✗ " + message + "</red>");
    }

    /**
     * Logs an error and its cause.
     *
     * @param message MiniMessage-formatted message
     * @param cause   the throwable that triggered this error, or {@code null}
     */
    public static void error(@NotNull String message, @Nullable Throwable cause) {
        log(Level.SEVERE, "<red>✗ " + message + (cause != null ? ": " + cause.getMessage() : "") + "</red>");
        if (cause != null) logger.log(Level.SEVERE, "YABCL error stack trace:", cause);
    }

    /**
     * Logs a debug message. Only emitted when {@link #setDebug} is {@code true}.
     *
     * @param message MiniMessage-formatted message
     */
    public static void debug(@NotNull String message) {
        if (!debug) return;
        log(Level.INFO, "<dark_gray>[debug] " + message + "</dark_gray>");
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void log(@NotNull Level level, @NotNull String message) {
        // Strip MiniMessage tags to produce a plain string for the logger
        String plain = MiniMessage.miniMessage().stripTags(prefix + message);
        logger.log(level, plain);
    }
}
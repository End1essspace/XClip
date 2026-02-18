package io.xseries.xclip.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.xseries.xclip.system.WindowsAutoStartService;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Loads/saves config.json.
 *
 * Rules:
 * - missing file => create defaults
 * - corrupt file => backup to config.bad-<ts>.json and create defaults
 * - always returns normalized config
 */
public final class ConfigService {

    private final Path path;
    private final Gson gson;

    public ConfigService(Path path) {
        this.path = path;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Path path() {
        return path;
    }
    
    public void applyRuntime(Config cfg) {
        if (cfg == null) return;
        applyAutoStart(cfg.normalized());
    }

    public Config loadOrCreate() {
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            return Config.defaults();
        }

        if (!Files.exists(path)) {
            Config cfg = Config.defaults();
            safeWrite(cfg);
            return cfg;
        }

        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Config cfg = gson.fromJson(r, Config.class);
            if (cfg == null) throw new IllegalStateException("config parsed to null");

            Config migrated = migrateIfNeeded(cfg);
            Config normalized = migrated.normalized();

            if (!equalsSemantically(migrated, normalized)) {
                safeWrite(normalized);
            }

            return normalized;

        } catch (Exception ex) {
            backupCorrupt();
            Config cfg = Config.defaults();
            safeWrite(cfg);
            return cfg;
        }
    }
    public void persist(Config cfg) {
        if (cfg == null) return;
        safeWrite(cfg.normalized());
    }
    public void save(Config cfg) {
        if (cfg == null) return;

        Config normalized = cfg.normalized();
        safeWrite(normalized);

        // Apply Windows autostart according to config flag
        applyAutoStart(normalized);
    }

    private void safeWrite(Config cfg) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(cfg, w);
            }

            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
        }
    }

    private void backupCorrupt() {
        try {
            if (!Files.exists(path)) return;
            String ts = String.valueOf(Instant.now().toEpochMilli());
            Path bad = path.resolveSibling("config.bad-" + ts + ".json");
            Files.move(path, bad, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private void applyAutoStart(Config config) {
        try {
            if (config.startOnBoot()) {
                WindowsAutoStartService.enable(resolveLaunchPath());
            } else {
                WindowsAutoStartService.disable();
            }
        } catch (Exception ignored) {
            // Silent fail in v1.0 (can log later)
        }
    }

    private Path resolveLaunchPath() {
        // Prefer launcher EXE when packaged (jpackage)
        try {
            String cmd = ProcessHandle.current().info().command().orElse(null);
            if (cmd != null) {
                String lc = cmd.toLowerCase();
                if (lc.endsWith(".exe") && lc.contains("xclip")) {
                    return Path.of(cmd);
                }
            }
        } catch (Exception ignored) {}

        // Fallback: jar location (dev/run)
        try {
            return Path.of(
                    ConfigService.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Cannot resolve launch path", e);
        }
    }

    private Config migrateIfNeeded(Config cfg) {
        // v1: no real migrations yet; hook for future
        if (cfg.version() <= 0) return Config.defaults();
        if (cfg.version() > Config.CURRENT_VERSION) {
            // Forward version: keep values but clamp
            return cfg;
        }
        return cfg;
    }

    private boolean equalsSemantically(Config a, Config b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.version() == b.version()
                && a.maxHistory() == b.maxHistory()
                && a.minClipLength() == b.minClipLength()
                && a.startOnBoot() == b.startOnBoot()
                && a.startMinimized() == b.startMinimized()
                && a.watcherEnabled() == b.watcherEnabled()
                && a.windowX() == b.windowX()
                && a.windowY() == b.windowY()
                && a.windowW() == b.windowW()
                && a.windowH() == b.windowH()
                && a.windowMaximized() == b.windowMaximized();
    }
}

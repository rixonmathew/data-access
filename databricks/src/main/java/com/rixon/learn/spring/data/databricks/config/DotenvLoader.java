package com.rixon.learn.spring.data.databricks.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility to discover and load environment variables from a .env file.
 * Searches candidate locations including 'databricks/.env', '.env', and module directories.
 */
public final class DotenvLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DotenvLoader.class);
    private static final Map<String, String> CACHED_PROPERTIES = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    private DotenvLoader() {
    }

    public static synchronized Map<String, String> load() {
        if (!initialized) {
            Map<String, String> loaded = discoverAndParse();
            CACHED_PROPERTIES.putAll(loaded);
            initialized = true;
        }
        return Collections.unmodifiableMap(CACHED_PROPERTIES);
    }

    public static String get(String key) {
        if (!initialized) {
            load();
        }
        // System environment variable takes precedence if explicitly set
        String sysEnv = System.getenv(key);
        if (sysEnv != null && !sysEnv.isBlank()) {
            return sysEnv;
        }
        // JVM System property
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        // Fallback to .env file
        return CACHED_PROPERTIES.get(key);
    }

    public static synchronized void reload() {
        CACHED_PROPERTIES.clear();
        initialized = false;
        load();
    }

    private static Map<String, String> discoverAndParse() {
        List<Path> candidates = new ArrayList<>();

        String userDir = System.getProperty("user.dir", ".");
        Path currentDir = Path.of(userDir);

        // 1. databricks/.env relative to cwd
        candidates.add(currentDir.resolve("databricks/.env"));
        // 2. .env in cwd (e.g. if running inside databricks module)
        candidates.add(currentDir.resolve(".env"));
        // 3. Parent directory .env
        if (currentDir.getParent() != null) {
            candidates.add(currentDir.getParent().resolve("databricks/.env"));
            candidates.add(currentDir.getParent().resolve(".env"));
        }
        // 4. Hardcoded relative paths
        candidates.add(Path.of("databricks/.env"));
        candidates.add(Path.of(".env"));

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                LOGGER.info("Discovered .env file at: {}", candidate.toAbsolutePath().normalize());
                return parseEnvFile(candidate);
            }
        }

        LOGGER.debug("No .env file found in candidate locations.");
        return Collections.emptyMap();
    }

    public static Map<String, String> parseEnvFile(Path path) {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Strip optional leading 'export '
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }

                int eqIdx = line.indexOf('=');
                if (eqIdx == -1) {
                    continue;
                }

                String key = line.substring(0, eqIdx).trim();
                String rawValue = line.substring(eqIdx + 1).trim();

                // Handle quotes around value
                String val = stripQuotes(rawValue);

                if (!key.isEmpty()) {
                    map.put(key, val);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read .env file at {}: {}", path, e.getMessage());
        }
        return map;
    }

    private static String stripQuotes(String str) {
        if (str == null || str.length() < 2) {
            return str;
        }
        if ((str.startsWith("\"") && str.endsWith("\"")) ||
            (str.startsWith("'") && str.endsWith("'"))) {
            return str.substring(1, str.length() - 1);
        }
        // Strip inline comments if not quoted: e.g. FOO=BAR # comment
        int hashIdx = str.indexOf('#');
        if (hashIdx != -1) {
            str = str.substring(0, hashIdx).trim();
        }
        return str;
    }
}

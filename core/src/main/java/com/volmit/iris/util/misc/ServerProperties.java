package com.volmit.iris.util.misc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ServerProperties {
    public static final Properties DATA = new Properties();
    public static final File SERVER_PROPERTIES;
    public static final File BUKKIT_YML;
    public static final File SPIGOT_YML;
    public static final File PAPER_DIR;
    public static final String LEVEL_NAME;

    static {
        String[] args = ProcessHandle.current()
                .info()
                .arguments()
                .orElse(new String[0]);

        String propertiesPath = "server.properties";
        String bukkitYml = "bukkit.yml";
        String spigotYml = "spigot.yml";
        String paperDir = "config";
        String levelName = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String next = i < args.length - 1 ? args[i + 1] : null;

            propertiesPath = parse(arg, next, propertiesPath, "-c", "--config");
            bukkitYml = parse(arg, next, bukkitYml, "-b", "--bukkit-settings");
            levelName = parse(arg, next, levelName, "-w", "--level-name", "--world");
            spigotYml = parse(arg, next, spigotYml, "-S", "--spigot-settings");
            paperDir = parse(arg, next, paperDir, "-paper-dir", "--paper-settings-directory");
        }

        SERVER_PROPERTIES = new File(propertiesPath);
        BUKKIT_YML = new File(bukkitYml);
        SPIGOT_YML = new File(spigotYml);
        PAPER_DIR = new File(paperDir);
        try (FileInputStream in = new FileInputStream(SERVER_PROPERTIES)){
            DATA.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (levelName != null) LEVEL_NAME = levelName;
        else LEVEL_NAME = DATA.getProperty("level-name", "world");
    }

    private static String parse(
            @NotNull String current,
            @Nullable String next,
            String fallback,
            @NotNull String @NotNull ... keys
    ) {
        for (String k : keys) {
            if (current.equals(k) && next != null)
                return next;
            if (current.startsWith(k + "=") && current.length() > k.length() + 1)
                return current.substring(k.length() + 1);
        }
        return fallback;
    }
}

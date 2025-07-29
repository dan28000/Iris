package com.volmit.iris.util.misc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ServerProperties {
    public static final Properties DATA = new Properties();
    public static File SERVER_PROPERTIES = new File("server.properties");
    public static File BUKKIT_YML = new File("bukkit.yml");
    public static File SPIGOT_YML = new File("spigot.yml");
    public static File PAPER_DIR = new File("config");
    public static String LEVEL_NAME = "world";

    public static void init(Paths paths) {
        SERVER_PROPERTIES = paths.serverProperties();
        BUKKIT_YML = paths.bukkitYml();
        SPIGOT_YML = paths.spigotYml();
        PAPER_DIR = paths.paperDir();
        String levelName = paths.levelName();

        try (FileInputStream input = new FileInputStream(SERVER_PROPERTIES)) {
            DATA.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (levelName != null) LEVEL_NAME = levelName;
        else LEVEL_NAME = DATA.getProperty("level-name", "world");
    }

    public record Paths(File serverProperties, File bukkitYml, File spigotYml, File paperDir, String levelName) {}
}

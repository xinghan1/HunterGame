package com.huntergame.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class PluginConfigFile {

    private final JavaPlugin plugin;
    private final String fileName;
    private final File file;
    private FileConfiguration config;

    public PluginConfigFile(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getTranslatedString(String key, String defaultValue) {
        if (!config.contains(key)) {
            config.set(key, defaultValue);
            save();
        }

        String value = config.getString(key, defaultValue);
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("保存 " + fileName + " 失败: " + e.getMessage());
        }
    }
}


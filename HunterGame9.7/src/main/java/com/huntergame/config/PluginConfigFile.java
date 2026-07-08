package com.huntergame.config;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginConfigFile {
   private final JavaPlugin plugin;
   private final String fileName;
   private final File file;
   private FileConfiguration config;

   public PluginConfigFile(JavaPlugin plugin, String fileName) {
      this.plugin = plugin;
      this.fileName = fileName;
      this.file = new File(plugin.getDataFolder(), fileName);
      this.reload();
   }

   public void reload() {
      if (!this.file.exists()) {
         this.plugin.saveResource(this.fileName, false);
      }

      this.config = YamlConfiguration.loadConfiguration(this.file);
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public String getTranslatedString(String key, String defaultValue) {
      if (!this.config.contains(key)) {
         this.config.set(key, defaultValue);
         this.save();
      }

      String value = this.config.getString(key, defaultValue);
      return ChatColor.translateAlternateColorCodes('&', value);
   }

   public void save() {
      try {
         this.config.save(this.file);
      } catch (IOException e) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = this.fileName;
         var10000.severe("\u4fdd\u5b58 " + var10001 + " \u5931\u8d25: " + e.getMessage());
      }

   }
}

package com.huntergame.data;

import com.huntergame.HunterGame;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class DataStorageManager {
   private final HunterGame plugin;
   private final Map<UUID, Integer> killCache = new HashMap<>();
   private HikariDataSource dataSource;
   private String databaseType;
   private String tableName;
   private File dataFile;
   private FileConfiguration fileConfig;
   private boolean fileDirty = false;
   private int fileSaveTaskId = -1;

   public DataStorageManager(HunterGame plugin) {
      this.plugin = plugin;
      this.databaseType = plugin.getConfig().getString("database.type", "file").toLowerCase();
      String prefix = plugin.getConfig().getString("database.table_prefix", "");
      this.tableName = prefix + "player_stats";
      this.setupStorage();
   }

   private void setupStorage() {
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.setupMySQL();
      } else {
         this.plugin.getLogger().warning("\u672a\u6307\u5b9a\u6709\u6548\u7684\u6570\u636e\u5e93\u7c7b\u578b\u6216\u7c7b\u578b\u4e3a 'file'\uff0c\u5c06\u4f7f\u7528\u672c\u5730\u6587\u4ef6\u5b58\u50a8\uff01");
         this.setupFileStorage();
      }

   }

   private void setupMySQL() {
      String host = this.plugin.getConfig().getString("database.host", "localhost");
      int port = this.plugin.getConfig().getInt("database.port", 3306);
      String database = this.plugin.getConfig().getString("database.name", "minecraft");
      String username = this.plugin.getConfig().getString("database.username", "root");
      String password = this.plugin.getConfig().getString("database.password", "");
      String timeZone = this.plugin.getConfig().getString("database.timezone", "UTC");
      boolean useSSL = this.plugin.getConfig().getBoolean("database.use_ssl", false);

      try {
         String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?serverTimezone=%s&useSSL=%b&allowPublicKeyRetrieval=true&characterEncoding=utf-8&rewriteBatchedStatements=true", host, port, database, timeZone, useSSL);
         HikariConfig config = new HikariConfig();
         config.setJdbcUrl(jdbcUrl);
         config.setDriverClassName("com.mysql.cj.jdbc.Driver");
         config.setUsername(username);
         config.setPassword(password);
         config.setMaximumPoolSize(this.plugin.getConfig().getInt("database.pool_size", 5));
         config.setMinimumIdle(2);
         config.setConnectionTimeout(5000L);
         this.dataSource = new HikariDataSource(config);
         this.plugin.getLogger().info("MySQL \u8fde\u63a5\u6c60\u521d\u59cb\u5316\u6210\u529f\uff01");
         Connection conn = this.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + this.tableName + " (uuid VARCHAR(36) PRIMARY KEY,name VARCHAR(36) DEFAULT '',kills INT DEFAULT 0,kills_put INT DEFAULT 0,deaths INT DEFAULT 0,games_played INT DEFAULT 0,hunter_wins INT DEFAULT 0,escape_wins INT DEFAULT 0,total_wins INT DEFAULT 0,proficiency DOUBLE DEFAULT 0.0,`rank` VARCHAR(36) DEFAULT '') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            } catch (Throwable var16) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var15) {
                     var16.addSuppressed(var15);
                  }
               }

               throw var16;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var17) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var14) {
                  var17.addSuppressed(var14);
               }
            }

            throw var17;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         this.plugin.getLogger().severe("MySQL \u521d\u59cb\u5316\u5931\u8d25\uff01" + e.getMessage());
         e.printStackTrace();
      }

   }

   private void setupFileStorage() {
      this.dataFile = new File(this.plugin.getDataFolder(), this.plugin.getConfig().getString("database.file_path", "player_data.yml"));
      if (!this.dataFile.exists()) {
         try {
            this.dataFile.getParentFile().mkdirs();
            this.dataFile.createNewFile();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

      this.fileConfig = YamlConfiguration.loadConfiguration(this.dataFile);
   }

   public void resetSeasonalData() {
      if ("mysql".equalsIgnoreCase(this.databaseType) && this.dataSource != null) {
         String sql = "UPDATE " + this.tableName + " SET proficiency = 0.0, `rank` = ''";

         try {
            Connection conn = this.getConnection();

            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.executeUpdate();
               } catch (Throwable var8) {
                  if (pstmt != null) {
                     try {
                        pstmt.close();
                     } catch (Throwable var7) {
                        var8.addSuppressed(var7);
                     }
                  }

                  throw var8;
               }

               if (pstmt != null) {
                  pstmt.close();
               }
            } catch (Throwable var9) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var6) {
                     var9.addSuppressed(var6);
                  }
               }

               throw var9;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
      } else if ("file".equalsIgnoreCase(this.databaseType) && this.fileConfig != null) {
         for(String uuidStr : this.fileConfig.getKeys(false)) {
            if (this.isValidUUID(uuidStr)) {
               this.fileConfig.set(uuidStr + ".proficiency", (double)0.0F);
               this.fileConfig.set(uuidStr + ".rank", "");
            }
         }

         this.saveFileConfig();
      }

   }

   public void addKill(UUID playerId) {
      this.killCache.put(playerId, this.killCache.getOrDefault(playerId, 0) + 1);
   }

   public int getKills(UUID playerId) {
      return this.killCache.getOrDefault(playerId, 0);
   }

   public void resetKillCache() {
      this.killCache.clear();
   }

   public void addKillput(UUID playerId, Player player) {
      this.addKillput(playerId, player.getName());
   }

   public void addKillput(UUID playerId, String playerName) {
      String key = playerId.toString() + ".kills_put";
      int newValue = this.getValue(playerId, "kills_put") + 1;
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.updateDatabase(playerId, playerName, "kills_put", newValue);
      } else {
         this.fileConfig.set(playerId.toString() + ".name", playerName);
         this.fileConfig.set(key, newValue);
         this.saveFileConfig();
      }

   }

   public int getKillsput(UUID playerId) {
      return this.getValue(playerId, "kills_put");
   }

   public void addDeath(UUID playerId, Player player) {
      String key = playerId.toString() + ".deaths";
      int newValue = this.getValue(playerId, "deaths") + 1;
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.updateDatabase(playerId, player, "deaths", newValue);
      } else {
         this.fileConfig.set(key, newValue);
         this.saveFileConfig();
      }

   }

   public int getDeaths(UUID playerId) {
      return this.getValue(playerId, "deaths");
   }

   public void addGamePlayed(UUID playerId, Player player) {
      String key = playerId.toString() + ".games_played";
      int newValue = this.getValue(playerId, "games_played") + 1;
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.updateDatabase(playerId, player, "games_played", newValue);
      } else {
         this.fileConfig.set(key, newValue);
         this.saveFileConfig();
      }

   }

   public int getGamesPlayed(UUID playerId) {
      return this.getValue(playerId, "games_played");
   }

   public void addHunterWin(UUID playerId, Player player) {
      String key = playerId.toString() + ".hunter_wins";
      int newValue = this.getValue(playerId, "hunter_wins") + 1;
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.updateDatabase(playerId, player, "hunter_wins", newValue);
      } else {
         this.fileConfig.set(key, newValue);
         this.saveFileConfig();
      }

      this.saveTotalWins(playerId, player);
   }

   public int getHunterWin(UUID playerId) {
      return this.getValue(playerId, "hunter_wins");
   }

   public void addEscapeWin(UUID playerId, Player player) {
      String key = playerId.toString() + ".escape_wins";
      int newValue = this.getValue(playerId, "escape_wins") + 1;
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.updateDatabase(playerId, player, "escape_wins", newValue);
      } else {
         this.fileConfig.set(key, newValue);
         this.saveFileConfig();
      }

      this.saveTotalWins(playerId, player);
   }

   public int getEscapeWin(UUID playerId) {
      return this.getValue(playerId, "escape_wins");
   }

   public void saveTotalWins(UUID playerId, Player player) {
      int totalWins = this.getEscapeWin(playerId) + this.getHunterWin(playerId);
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.updateDatabase(playerId, player, "total_wins", totalWins);
      } else {
         String key = playerId.toString() + ".total_wins";
         this.fileConfig.set(key, totalWins);
         this.saveFileConfig();
      }

   }

   public int getTotalWins(UUID playerId) {
      return this.getEscapeWin(playerId) + this.getHunterWin(playerId);
   }

   public void addProficiency(Player player, double count) {
      UUID playerId = player.getUniqueId();
      double newValue = this.getProficiency(player) + count;
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.updateDatabase(playerId, player, "proficiency", newValue);
      } else {
         String key = playerId.toString() + ".proficiency";
         this.fileConfig.set(key, newValue);
         this.saveFileConfig();
      }

   }

   public double getProficiency(Player player) {
      UUID playerId = player.getUniqueId();
      double proficiency = (double)0.0F;
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         String sql = "SELECT proficiency FROM " + this.tableName + " WHERE uuid = ?";

         try {
            Connection conn = this.getConnection();

            try {
               PreparedStatement stmt = conn.prepareStatement(sql);

               try {
                  stmt.setString(1, playerId.toString());
                  ResultSet rs = stmt.executeQuery();
                  if (rs.next()) {
                     proficiency = rs.getDouble("proficiency");
                  }
               } catch (Throwable var12) {
                  if (stmt != null) {
                     try {
                        stmt.close();
                     } catch (Throwable var11) {
                        var12.addSuppressed(var11);
                     }
                  }

                  throw var12;
               }

               if (stmt != null) {
                  stmt.close();
               }
            } catch (Throwable var13) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var10) {
                     var13.addSuppressed(var10);
                  }
               }

               throw var13;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
      } else {
         String key = playerId.toString() + ".proficiency";
         proficiency = this.fileConfig.getDouble(key, (double)0.0F);
      }

      return (new BigDecimal(proficiency)).setScale(2, RoundingMode.HALF_UP).doubleValue();
   }

   public void addRank(UUID playerId, Player player) {
      double proficiency = this.getProficiency(player);
      String rankName = this.plugin.getRankManager().getRankName(proficiency);
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         this.updateDatabase(playerId, player, "rank", rankName);
      } else {
         this.fileConfig.set(playerId.toString() + ".rank", rankName);
         this.saveFileConfig();
      }

   }

   private void updateDatabase(UUID uuid, Player player, String column, Object value) {
      this.updateDatabase(uuid, player.getName(), column, value);
   }

   private void updateDatabase(UUID uuid, String playerName, String column, Object value) {
      if (this.dataSource != null) {
         String sql = "INSERT INTO " + this.tableName + " (uuid, name, `" + column + "`) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), `" + column + "` = VALUES(`" + column + "`)";

         try {
            Connection conn = this.getConnection();

            try {
               PreparedStatement statement = conn.prepareStatement(sql);

               try {
                  statement.setString(1, uuid.toString());
                  statement.setString(2, playerName);
                  if (value instanceof Integer) {
                     statement.setInt(3, (Integer)value);
                  } else if (value instanceof Double) {
                     statement.setDouble(3, (Double)value);
                  } else if (value instanceof String) {
                     statement.setString(3, (String)value);
                  }

                  statement.executeUpdate();
               } catch (Throwable var12) {
                  if (statement != null) {
                     try {
                        statement.close();
                     } catch (Throwable var11) {
                        var12.addSuppressed(var11);
                     }
                  }

                  throw var12;
               }

               if (statement != null) {
                  statement.close();
               }
            } catch (Throwable var13) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var10) {
                     var13.addSuppressed(var10);
                  }
               }

               throw var13;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = String.valueOf(uuid);
            var10000.severe("\u66f4\u65b0\u6570\u636e\u5e93\u5931\u8d25 for player " + var10001 + " (" + playerName + ")");
            e.printStackTrace();
         }

      }
   }

   private int getValue(UUID playerId, String field) {
      if ("mysql".equalsIgnoreCase(this.databaseType)) {
         String sql = "SELECT `" + field + "` FROM " + this.tableName + " WHERE uuid = ?";

         try {
            Connection conn = this.getConnection();

            int var7;
            label89: {
               try {
                  PreparedStatement stmt = conn.prepareStatement(sql);

                  label83: {
                     try {
                        stmt.setString(1, playerId.toString());
                        ResultSet rs = stmt.executeQuery();
                        if (!rs.next()) {
                           break label83;
                        }

                        var7 = rs.getInt(field);
                     } catch (Throwable var10) {
                        if (stmt != null) {
                           try {
                              stmt.close();
                           } catch (Throwable var9) {
                              var10.addSuppressed(var9);
                           }
                        }

                        throw var10;
                     }

                     if (stmt != null) {
                        stmt.close();
                     }
                     break label89;
                  }

                  if (stmt != null) {
                     stmt.close();
                  }
               } catch (Throwable var11) {
                  if (conn != null) {
                     try {
                        conn.close();
                     } catch (Throwable var8) {
                        var11.addSuppressed(var8);
                     }
                  }

                  throw var11;
               }

               if (conn != null) {
                  conn.close();
               }

               return 0;
            }

            if (conn != null) {
               conn.close();
            }

            return var7;
         } catch (SQLException var12) {
            return 0;
         }
      } else {
         return this.fileConfig.getInt(playerId.toString() + "." + field, 0);
      }
   }

   private void saveFileConfig() {
      if (this.fileConfig != null && this.dataFile != null) {
         this.fileDirty = true;
         if (this.fileSaveTaskId == -1) {
            this.fileSaveTaskId = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::flushFileConfig, 20L).getTaskId();
         }
      }
   }

   private void flushFileConfig() {
      if (this.fileDirty && this.fileConfig != null && this.dataFile != null) {
         try {
            this.fileConfig.save(this.dataFile);
            this.fileDirty = false;
         } catch (IOException e) {
            this.plugin.getLogger().severe("\u4fdd\u5b58\u73a9\u5bb6\u6570\u636e\u5931\u8d25: " + e.getMessage());
         } finally {
            this.fileSaveTaskId = -1;
         }

      } else {
         this.fileSaveTaskId = -1;
      }
   }

   public Connection getConnection() throws SQLException {
      if (this.dataSource == null) {
         throw new SQLException("\u6570\u636e\u6e90\u672a\u521d\u59cb\u5316\u3002");
      } else {
         return this.dataSource.getConnection();
      }
   }

   private boolean isValidUUID(String uuidStr) {
      try {
         UUID.fromString(uuidStr);
         return true;
      } catch (Exception var3) {
         return false;
      }
   }

   public Map<UUID, Integer> getAllPlayerTiers() {
      Map<UUID, Integer> tiers = new HashMap<>();
      if ("mysql".equalsIgnoreCase(this.databaseType) && this.dataSource != null) {
         String sql = "SELECT uuid, proficiency FROM " + this.tableName + " WHERE proficiency > 0 ORDER BY proficiency DESC";

         try {
            Connection conn = this.getConnection();

            try {
               PreparedStatement stmt = conn.prepareStatement(sql);

               try {
                  ResultSet rs = stmt.executeQuery();
                  int tier = 1;

                  while(rs.next()) {
                     tiers.put(UUID.fromString(rs.getString("uuid")), tier++);
                  }
               } catch (Throwable var9) {
                  if (stmt != null) {
                     try {
                        stmt.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (stmt != null) {
                  stmt.close();
               }
            } catch (Throwable var10) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var7) {
                     var10.addSuppressed(var7);
                  }
               }

               throw var10;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
      } else if (this.fileConfig != null) {
         Map<UUID, Double> profMap = new HashMap<>();

         for(String key : this.fileConfig.getKeys(false)) {
            if (this.isValidUUID(key)) {
               double prof = this.fileConfig.getDouble(key + ".proficiency", (double)0.0F);
               if (prof > (double)0.0F) {
                  profMap.put(UUID.fromString(key), prof);
               }
            }
         }

         List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(profMap.entrySet());
         sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
         int rank = 1;

         for(Map.Entry<UUID, Double> entry : sorted) {
            tiers.put(entry.getKey(), rank++);
         }
      }

      return tiers;
   }

   public void shutdown() {
      if (this.fileSaveTaskId != -1) {
         this.plugin.getServer().getScheduler().cancelTask(this.fileSaveTaskId);
         this.fileSaveTaskId = -1;
      }

      this.flushFileConfig();
      if (this.dataSource != null && !this.dataSource.isClosed()) {
         this.dataSource.close();
      }

   }
}

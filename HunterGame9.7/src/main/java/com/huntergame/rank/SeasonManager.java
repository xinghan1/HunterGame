package com.huntergame.rank;

import com.huntergame.HunterGame;
import com.huntergame.data.DataStorageManager;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class SeasonManager implements Listener {
   private final HunterGame plugin;
   private final RankManager rankManager;
   private final DataStorageManager dataStorageManager;
   private File seasonDataFile;
   private FileConfiguration seasonDataConfig;
   private String currentSeasonId;
   private Map<String, List<String>> seasonRewards = new HashMap();

   public SeasonManager(HunterGame plugin, RankManager rankManager, DataStorageManager dataStorageManager) {
      this.plugin = plugin;
      this.rankManager = rankManager;
      this.dataStorageManager = dataStorageManager;
      Bukkit.getPluginManager().registerEvents(this, plugin);
      this.initSeasonDataFile();
      this.loadSeasonRewards();
      if (this.isUsingMySQL()) {
         this.initializeTables();
         this.currentSeasonId = this.getCurrentSeasonIdFromDB();
         if (this.currentSeasonId == null || this.currentSeasonId.isEmpty()) {
            this.currentSeasonId = "S1";
            this.saveCurrentSeasonIdToDB(this.currentSeasonId);
         }
      } else {
         this.currentSeasonId = this.getCurrentSeasonIdFromFile();
         if (this.currentSeasonId == null || this.currentSeasonId.isEmpty()) {
            this.currentSeasonId = "S1";
            this.saveCurrentSeasonIdToFile(this.currentSeasonId);
         }
      }

      plugin.getLogger().info("\u5df2\u52a0\u8f7d\u5f53\u524d\u8d5b\u5b63\uff0c\u8d5b\u5b63ID: " + this.currentSeasonId);
   }

   public boolean startNewSeason(String newSeasonId) {
      if (newSeasonId != null && !newSeasonId.trim().isEmpty()) {
         newSeasonId = newSeasonId.trim();
         this.plugin.getLogger().info("\u6b63\u5728\u51c6\u5907\u65b0\u8d5b\u5b63: " + newSeasonId);
         if (this.isSeasonIdExists(newSeasonId)) {
            this.plugin.getLogger().severe("\u65e0\u6cd5\u5f00\u59cb\u65b0\u8d5b\u5b63\uff01\u8d5b\u5b63ID '" + newSeasonId + "' \u5df2\u5b58\u5728\u3002");
            return false;
         } else {
            this.dataStorageManager.resetSeasonalData();
            if (this.isUsingMySQL()) {
               try {
                  Connection conn = this.dataStorageManager.getConnection();

                  try {
                     conn.setAutoCommit(false);
                     String insertSql = "INSERT INTO season_info (season_id, player_uuid, season_start_time) VALUES (?, 'system', NOW())";
                     PreparedStatement pstmt = conn.prepareStatement(insertSql);

                     try {
                        pstmt.setString(1, newSeasonId);
                        pstmt.executeUpdate();
                     } catch (Throwable var12) {
                        if (pstmt != null) {
                           try {
                              pstmt.close();
                           } catch (Throwable var10) {
                              var12.addSuppressed(var10);
                           }
                        }

                        throw var12;
                     }

                     if (pstmt != null) {
                        pstmt.close();
                     }

                     String deleteOldSystemSql = "DELETE FROM season_info WHERE player_uuid = 'system' AND season_id != ?";
                     PreparedStatement pstmt2 = conn.prepareStatement(deleteOldSystemSql);

                     try {
                        pstmt2.setString(1, newSeasonId);
                        pstmt2.executeUpdate();
                     } catch (Throwable var11) {
                        if (pstmt2 != null) {
                           try {
                              pstmt2.close();
                           } catch (Throwable var9) {
                              var11.addSuppressed(var9);
                           }
                        }

                        throw var11;
                     }

                     if (pstmt2 != null) {
                        pstmt2.close();
                     }

                     conn.commit();
                     this.currentSeasonId = newSeasonId;
                  } catch (Throwable var13) {
                     if (conn != null) {
                        try {
                           conn.close();
                        } catch (Throwable var8) {
                           var13.addSuppressed(var8);
                        }
                     }

                     throw var13;
                  }

                  if (conn != null) {
                     conn.close();
                  }
               } catch (SQLException e) {
                  this.plugin.getLogger().severe("\u65b0\u8d5b\u5b63\u5f00\u59cb\u65f6\uff0cMySQL \u6570\u636e\u5e93\u64cd\u4f5c\u5931\u8d25\uff01");
                  e.printStackTrace();
                  return false;
               }
            } else {
               this.currentSeasonId = newSeasonId;
               this.resetAllClaimedRewardsInFile();
               this.saveCurrentSeasonIdToFile(this.currentSeasonId);
            }

            this.plugin.getLogger().info("\u65b0\u8d5b\u5b63 '" + newSeasonId + "' \u5df2\u6210\u529f\u5f00\u59cb\uff01");
            return true;
         }
      } else {
         this.plugin.getLogger().warning("\u5c1d\u8bd5\u4f7f\u7528\u7a7a\u8d5b\u5b63ID\u5f00\u59cb\u65b0\u8d5b\u5b63\uff01");
         return false;
      }
   }

   private boolean isSeasonIdExists(String seasonId) {
      if (!this.isUsingMySQL()) {
         return false;
      } else {
         String sql = "SELECT 1 FROM season_info WHERE season_id = ? LIMIT 1";

         try {
            Connection conn = this.dataStorageManager.getConnection();

            boolean var6;
            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.setString(1, seasonId);
                  ResultSet rs = pstmt.executeQuery();

                  try {
                     var6 = rs.next();
                  } catch (Throwable var11) {
                     if (rs != null) {
                        try {
                           rs.close();
                        } catch (Throwable var10) {
                           var11.addSuppressed(var10);
                        }
                     }

                     throw var11;
                  }

                  if (rs != null) {
                     rs.close();
                  }
               } catch (Throwable var12) {
                  if (pstmt != null) {
                     try {
                        pstmt.close();
                     } catch (Throwable var9) {
                        var12.addSuppressed(var9);
                     }
                  }

                  throw var12;
               }

               if (pstmt != null) {
                  pstmt.close();
               }
            } catch (Throwable var13) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var8) {
                     var13.addSuppressed(var8);
                  }
               }

               throw var13;
            }

            if (conn != null) {
               conn.close();
            }

            return var6;
         } catch (SQLException e) {
            this.plugin.getLogger().warning("\u68c0\u67e5\u8d5b\u5b63ID '" + seasonId + "' \u662f\u5426\u5b58\u5728\u65f6\u51fa\u9519\uff01");
            e.printStackTrace();
            return true;
         }
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (!this.hasPlayerClaimedSeasonReward(playerId)) {
         Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            double proficiency = this.dataStorageManager.getProficiency(player);
            String rank = this.rankManager.getRank(proficiency);
            if (this.seasonRewards.containsKey(rank)) {
               for(String command : (List<String>)this.seasonRewards.get(rank)) {
                  String formattedCommand = command.replace("%player%", player.getName()).replace("%rank%", rank).replace("%season%", this.currentSeasonId);
                  Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand));
               }

               for(String line : this.plugin.getMessageList("season_reward_message", Arrays.asList("&a=====================================", "&6         \u65b0\u8d5b\u5b63 '%season%' \u5f00\u59cb\u5566\uff01          ", "&a-------------------------------------", "&a\u4f60\u7684\u4e0a\u8d5b\u5b63\u6bb5\u4f4d\u4e3a: &6%rank%", "&a\u8d5b\u5b63\u5956\u52b1\u5df2\u81ea\u52a8\u53d1\u653e\u81f3\u4f60\u7684\u8d26\u6237\uff01", "&a\u8d5b\u5b63\u52aa\u529b\u51b2\u5206\uff0c\u53ef\u89e3\u9501\u66f4\u9ad8\u7ea7\u5956\u52b1\uff01", "&a====================================="))) {
                  player.sendMessage(line.replace("%season%", this.currentSeasonId).replace("%rank%", rank));
               }
            } else {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = player.getName();
               var10000.warning("\u73a9\u5bb6 " + var10001 + " \u7684\u6bb5\u4f4d " + rank + " \u6ca1\u6709\u914d\u7f6e\u5bf9\u5e94\u7684\u8d5b\u5b63\u5956\u52b1\u3002");
            }

            this.markPlayerClaimedSeasonReward(playerId);
         });
      }

   }

   private boolean isUsingMySQL() {
      return "mysql".equalsIgnoreCase(this.plugin.getConfig().getString("database.type", "file"));
   }

   private void initializeTables() {
      String createTableSQL = "CREATE TABLE IF NOT EXISTS season_info (id INT AUTO_INCREMENT PRIMARY KEY, season_id VARCHAR(50) NOT NULL COMMENT '\u8d5b\u5b63\u7684\u552f\u4e00ID (\u4f8b\u5982: S1, 2024_\u6625\u5b63)', player_uuid VARCHAR(36) NOT NULL COMMENT '\u5df2\u9886\u53d6\u5956\u52b1\u7684\u73a9\u5bb6UUID', season_start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '\u8d5b\u5b63\u5f00\u59cb\u65f6\u95f4', claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '\u9886\u53d6\u5956\u52b1\u7684\u65f6\u95f4', UNIQUE KEY unique_season_player (season_id, player_uuid) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='\u8d5b\u5b63\u4fe1\u606f\u548c\u73a9\u5bb6\u5956\u52b1\u9886\u53d6\u8bb0\u5f55\u8868';";

      try {
         Connection conn = this.dataStorageManager.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               stmt.execute(createTableSQL);
            } catch (Throwable var8) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (stmt != null) {
               stmt.close();
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
         this.plugin.getLogger().severe("\u521b\u5efa\u6216\u521d\u59cb\u5316 season_info \u8868\u5931\u8d25\uff01");
         e.printStackTrace();
      }

   }

   private String getCurrentSeasonIdFromDB() {
      String sql = "SELECT season_id FROM season_info WHERE player_uuid = 'system' LIMIT 1";

      try {
         Connection conn = this.dataStorageManager.getConnection();

         String var5;
         label114: {
            try {
               PreparedStatement pstmt;
               label106: {
                  pstmt = conn.prepareStatement(sql);

                  try {
                     ResultSet rs = pstmt.executeQuery();

                     label86: {
                        try {
                           if (rs.next()) {
                              var5 = rs.getString("season_id");
                              break label86;
                           }
                        } catch (Throwable var10) {
                           if (rs != null) {
                              try {
                                 rs.close();
                              } catch (Throwable var9) {
                                 var10.addSuppressed(var9);
                              }
                           }

                           throw var10;
                        }

                        if (rs != null) {
                           rs.close();
                        }
                        break label106;
                     }

                     if (rs != null) {
                        rs.close();
                     }
                  } catch (Throwable var11) {
                     if (pstmt != null) {
                        try {
                           pstmt.close();
                        } catch (Throwable var8) {
                           var11.addSuppressed(var8);
                        }
                     }

                     throw var11;
                  }

                  if (pstmt != null) {
                     pstmt.close();
                  }
                  break label114;
               }

               if (pstmt != null) {
                  pstmt.close();
               }
            } catch (Throwable var12) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var7) {
                     var12.addSuppressed(var7);
                  }
               }

               throw var12;
            }

            if (conn != null) {
               conn.close();
            }

            return null;
         }

         if (conn != null) {
            conn.close();
         }

         return var5;
      } catch (SQLException e) {
         this.plugin.getLogger().warning("\u4ece\u6570\u636e\u5e93\u8bfb\u53d6\u5f53\u524d\u8d5b\u5b63ID\u5931\u8d25\uff01");
         e.printStackTrace();
         return null;
      }
   }

   private void saveCurrentSeasonIdToDB(String seasonId) {
      String sql = "INSERT INTO season_info (season_id, player_uuid) VALUES (?, 'system') ON DUPLICATE KEY UPDATE season_id = VALUES(season_id)";

      try {
         Connection conn = this.dataStorageManager.getConnection();

         try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            try {
               pstmt.setString(1, seasonId);
               pstmt.executeUpdate();
            } catch (Throwable var9) {
               if (pstmt != null) {
                  try {
                     pstmt.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }
               }

               throw var9;
            }

            if (pstmt != null) {
               pstmt.close();
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
         this.plugin.getLogger().severe("\u5411\u6570\u636e\u5e93\u4fdd\u5b58\u5f53\u524d\u8d5b\u5b63ID\u5931\u8d25\uff01");
         e.printStackTrace();
      }

   }

   private boolean hasPlayerClaimedSeasonRewardInDB(UUID playerId) {
      String sql = "SELECT 1 FROM season_info WHERE season_id = ? AND player_uuid = ? LIMIT 1";

      try {
         Connection conn = this.dataStorageManager.getConnection();

         boolean var6;
         try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            try {
               pstmt.setString(1, this.currentSeasonId);
               pstmt.setString(2, playerId.toString());
               ResultSet rs = pstmt.executeQuery();

               try {
                  var6 = rs.next();
               } catch (Throwable var11) {
                  if (rs != null) {
                     try {
                        rs.close();
                     } catch (Throwable var10) {
                        var11.addSuppressed(var10);
                     }
                  }

                  throw var11;
               }

               if (rs != null) {
                  rs.close();
               }
            } catch (Throwable var12) {
               if (pstmt != null) {
                  try {
                     pstmt.close();
                  } catch (Throwable var9) {
                     var12.addSuppressed(var9);
                  }
               }

               throw var12;
            }

            if (pstmt != null) {
               pstmt.close();
            }
         } catch (Throwable var13) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var8) {
                  var13.addSuppressed(var8);
               }
            }

            throw var13;
         }

         if (conn != null) {
            conn.close();
         }

         return var6;
      } catch (SQLException e) {
         this.plugin.getLogger().warning("\u68c0\u67e5\u73a9\u5bb6 " + String.valueOf(playerId) + " \u662f\u5426\u5df2\u9886\u53d6\u5956\u52b1\u65f6\u6570\u636e\u5e93\u51fa\u9519\uff01");
         e.printStackTrace();
         return false;
      }
   }

   private void markPlayerClaimedSeasonRewardInDB(UUID playerId) {
      String sql = "INSERT IGNORE INTO season_info (season_id, player_uuid) VALUES (?, ?)";

      try {
         Connection conn = this.dataStorageManager.getConnection();

         try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            try {
               pstmt.setString(1, this.currentSeasonId);
               pstmt.setString(2, playerId.toString());
               pstmt.executeUpdate();
            } catch (Throwable var9) {
               if (pstmt != null) {
                  try {
                     pstmt.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }
               }

               throw var9;
            }

            if (pstmt != null) {
               pstmt.close();
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
         this.plugin.getLogger().severe("\u6807\u8bb0\u73a9\u5bb6 " + String.valueOf(playerId) + " \u5df2\u9886\u53d6\u5956\u52b1\u65f6\u6570\u636e\u5e93\u51fa\u9519\uff01");
         e.printStackTrace();
      }

   }

   private void initSeasonDataFile() {
      this.seasonDataFile = new File(this.plugin.getDataFolder(), "season_data.yml");
      if (!this.seasonDataFile.exists()) {
         try {
            this.seasonDataFile.getParentFile().mkdirs();
            this.seasonDataFile.createNewFile();
         } catch (IOException e) {
            this.plugin.getLogger().severe("\u521b\u5efa\u8d5b\u5b63\u6570\u636e\u6587\u4ef6\u5931\u8d25: " + e.getMessage());
            e.printStackTrace();
         }
      }

      this.seasonDataConfig = YamlConfiguration.loadConfiguration(this.seasonDataFile);
   }

   private String getCurrentSeasonIdFromFile() {
      return this.seasonDataConfig.getString("current-season", (String)null);
   }

   private void saveCurrentSeasonIdToFile(String seasonId) {
      this.seasonDataConfig.set("current-season", seasonId);
      this.saveSeasonDataFile();
   }

   private boolean hasPlayerClaimedSeasonRewardFromFile(UUID playerId) {
      return this.seasonDataConfig.getBoolean("claimed-rewards." + playerId.toString(), false);
   }

   private void markPlayerClaimedSeasonRewardInFile(UUID playerId) {
      this.seasonDataConfig.set("claimed-rewards." + playerId.toString(), true);
      this.saveSeasonDataFile();
   }

   private void resetAllClaimedRewardsInFile() {
      if (this.seasonDataConfig.contains("claimed-rewards")) {
         this.seasonDataConfig.set("claimed-rewards", (Object)null);
         this.saveSeasonDataFile();
      }

   }

   private void saveSeasonDataFile() {
      try {
         this.seasonDataConfig.save(this.seasonDataFile);
      } catch (IOException e) {
         this.plugin.getLogger().severe("\u4fdd\u5b58\u8d5b\u5b63\u6570\u636e\u6587\u4ef6\u5931\u8d25: " + e.getMessage());
         e.printStackTrace();
      }

   }

   public boolean hasPlayerClaimedSeasonReward(UUID playerId) {
      return this.isUsingMySQL() ? this.hasPlayerClaimedSeasonRewardInDB(playerId) : this.hasPlayerClaimedSeasonRewardFromFile(playerId);
   }

   public void markPlayerClaimedSeasonReward(UUID playerId) {
      if (this.isUsingMySQL()) {
         this.markPlayerClaimedSeasonRewardInDB(playerId);
      } else {
         this.markPlayerClaimedSeasonRewardInFile(playerId);
      }

   }

   public String getCurrentSeasonId() {
      return this.currentSeasonId;
   }

   public void loadSeasonRewards() {
      this.seasonRewards.clear();
      FileConfiguration rankConfig = this.loadRankConfig();
      if (rankConfig != null) {
         if (!rankConfig.contains("season-rewards")) {
            this.plugin.getLogger().severe("rank.yml \u4e2d\u672a\u627e\u5230 season-rewards \u914d\u7f6e\u8282\u70b9\uff01");
         } else {
            ConfigurationSection rewardsSection = rankConfig.getConfigurationSection("season-rewards");
            if (rewardsSection == null) {
               this.plugin.getLogger().severe("rank.yml \u4e2d season-rewards \u914d\u7f6e\u8282\u70b9\u683c\u5f0f\u9519\u8bef\uff01");
            } else {
               for(String rankKey : rewardsSection.getKeys(false)) {
                  List<String> commands = rewardsSection.getStringList(rankKey);
                  String cleanRankKey = rankKey.trim();
                  this.seasonRewards.put(cleanRankKey, commands);
               }

               this.plugin.getLogger().info("\u8d5b\u5b63\u5956\u52b1\u52a0\u8f7d\u5b8c\u6210\uff0c\u5171\u52a0\u8f7d " + this.seasonRewards.size() + " \u4e2a\u6bb5\u4f4d\u7684\u5956\u52b1");
            }
         }
      }
   }

   private FileConfiguration loadRankConfig() {
      File rankFile = new File(this.plugin.getDataFolder(), "rank.yml");
      if (!rankFile.exists()) {
         this.plugin.getLogger().severe("rank.yml \u6587\u4ef6\u4e0d\u5b58\u5728\uff01\u8bf7\u68c0\u67e5\u63d2\u4ef6\u6570\u636e\u6587\u4ef6\u5939");
         return null;
      } else {
         return YamlConfiguration.loadConfiguration(rankFile);
      }
   }
}

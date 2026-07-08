package com.huntergame;

import com.huntergame.combat.LastDamageTracker;
import com.huntergame.command.HunterGameCommand;
import com.huntergame.config.PluginConfigFile;
import com.huntergame.data.DataStorageManager;
import com.huntergame.game.CageManager;
import com.huntergame.game.EscaperQuitCountdown;
import com.huntergame.game.GameSettlement;
import com.huntergame.game.StartGame;
import com.huntergame.gui.GuideGUI;
import com.huntergame.gui.SpectatorGUI;
import com.huntergame.inventory.SharedBackpackManager;
import com.huntergame.listener.ChatActivityListener;
import com.huntergame.listener.CustomEntityListener;
import com.huntergame.listener.DeathMessageListener;
import com.huntergame.listener.DragonFightListener;
import com.huntergame.listener.FinalBattleHunterAdvancementListener;
import com.huntergame.listener.GameDeathListener;
import com.huntergame.listener.HunterRespawnListener;
import com.huntergame.listener.InactivityMonitor;
import com.huntergame.listener.NoDamageListener;
import com.huntergame.listener.PlayerConnectionListener;
import com.huntergame.listener.ServerSelectorListener;
import com.huntergame.listener.WaitingLobbyListener;
import com.huntergame.message.MessageBroadcaster;
import com.huntergame.motd.MotdListener;
import com.huntergame.placeholder.HunterGamePlaceholder;
import com.huntergame.portal.EndPortalTracker;
import com.huntergame.profession.FinalBattleProfessionManager;
import com.huntergame.rank.RankManager;
import com.huntergame.rank.SeasonManager;
import com.huntergame.reward.GameRewardService;
import com.huntergame.role.RoleSelectionHandler;
import com.huntergame.scoreboard.HunterScoreboardManager;
import com.huntergame.session.DisconnectProtectionService;
import com.huntergame.skill.SkillManager;
import com.huntergame.skill.skills.ExplosiveCrossbowListener;
import com.huntergame.skill.skills.FreezeSkill;
import com.huntergame.spectator.SpectatorService;
import com.huntergame.tracking.HunterTracker;
import com.huntergame.world.DamageProtection;
import com.huntergame.world.EndWorldProtector;
import com.huntergame.world.EndermanLimiter;
import com.huntergame.world.OnlineWorldResetManager;
import com.huntergame.world.OreMultiplier;
import com.huntergame.world.WorldBorderManager;
import com.xigua.baseAPI.BaseAPI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class HunterGame extends JavaPlugin implements Listener {
   private final Set<UUID> escapers = new LinkedHashSet();
   private final Set<UUID> hunters = new LinkedHashSet();
   private final Set<UUID> deathescapers = new LinkedHashSet();
   private Location lobbyLocation;
   private boolean gameInProgress = false;
   private boolean gameEnded = false;
   private final Set<Player> playersWithoutRole = new HashSet();
   private final Set<UUID> realSpectators = new HashSet();
   private PluginConfigFile languageConfigFile;
   private PluginConfigFile guiConfigFile;
   private SharedBackpackManager sharedBackpackManager;
   private SpectatorService spectatorService;
   private EndPortalTracker endPortalTracker;
   HunterScoreboardManager scoreboardManager;
   private HunterGameCommand setCommand;
   public CageManager glassCageManager;
   public DamageProtection damageprotection;
   private long startTime = 0L;
   private DisconnectProtectionService disconnectProtection;
   private int timeLimitTaskId = -1;
   private InactivityMonitor inactivityDetection;
   private StartGame startGameCommand;
   HunterTracker hunterTracker;
   private SeasonManager seasonManager;
   private SkillManager skillManager;
   private EndWorldProtector endProtector;
   private FreezeSkill freezeSkill;
   private EndermanLimiter endermanLimiter;
   private OreMultiplier oreMultiplier;
   private ExplosiveCrossbowListener explosiveCrossbowListener;
   private GameSettlement gameSettlement;
   private GameRewardService gameRewards;
   private EscaperQuitCountdown escaperQuitCountdown;
   private LastDamageTracker lastDamageTracker;
   private MessageBroadcaster messageBroadcaster;
   private RankManager rankManager;
   private GuideGUI guideManager;
   private DataStorageManager dataStorageManager;
   private HunterGamePlaceholder hunterGamePlaceholder;
   private SpectatorGUI spectatorGUI;
   private FinalBattleProfessionManager finalBattleProfessionManager;
   private OnlineWorldResetManager onlineWorldResetManager;
   public static final int MODE_FINAL_BATTLE = 2;
   public static final int MODE_VANILLA_HUNTER = 3;
   private static final int FINAL_BATTLE_GLOWING_REFRESH_TICKS = 100;
   private static final int FINAL_BATTLE_GLOWING_DURATION_TICKS = 160;
   private int currentBattleType = 0;
   private int currentGameMode = 0;
   private boolean serverClosing = false;
   private boolean resetScheduled = false;
   private boolean resetInProgress = false;
   private boolean settlementStarted = false;
   private BaseAPI baseAPI;
   private int gameTime = 300;

   public void onEnable() {
      this.initializeOptionalDependencies();
      this.setupDefaultConfig();
      this.initializeConfigFiles();
      this.loadLobbyWorld();
      Bukkit.getScheduler().runTaskLater(this, this::loadConfig, 100L);
      this.startHungerRegenerationTask();
      WorldBorderManager.setupWorldBorder();
      this.setupConfiguredEndWorldBorder();
      this.getLogger().info("\u5df2\u6fc0\u6d3b\u4e16\u754c\u8fb9\u754c\uff01");
      this.initializeManagers();
      this.registerCommandsAndEvents();
      this.registerRepeatingTasks();
      this.enablePlaceholderApi();
   }

   private void setupConfiguredEndWorldBorder() {
      WorldBorderManager.setupEndWorldBorder(
         this.getConfig().getString("game.online_reset.schematic_reset.world", "world_the_end"),
         this.getConfig().getInt("game.online_reset.schematic_reset.paste_origin.x", 0),
         this.getConfig().getInt("game.online_reset.schematic_reset.paste_origin.y", 90),
         this.getConfig().getInt("game.online_reset.schematic_reset.paste_origin.z", 0),
         (double)this.getConfig().getInt("game.online_reset.schematic_reset.cleanup_radius", 300)
      );
   }

   private void initializeOptionalDependencies() {
      if (Bukkit.getPluginManager().getPlugin("BaseAPI") != null) {
         this.baseAPI = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
         this.getLogger().info("\u53d1\u73b0 BaseAPI\uff0c\u5c06\u542f\u7528\u57fa\u5ca9\u7248\u901a\u4fe1\uff01");
      } else {
         this.getLogger().warning("\u672a\u627e\u5230 BaseAPI\uff0c\u57fa\u5ca9\u7248\u901a\u4fe1\u4e0d\u53ef\u7528\uff01");
      }

   }

   private void setupDefaultConfig() {
      this.saveDefaultConfig();
      this.getConfig().options().copyDefaults(true);
      this.saveConfig();
   }

   private void initializeConfigFiles() {
      this.languageConfigFile = new PluginConfigFile(this, "message.yml");
      this.guiConfigFile = new PluginConfigFile(this, "gui.yml");
   }

   private void initializeManagers() {
      this.skillManager = new SkillManager(this);
      this.endProtector = new EndWorldProtector(this);
      this.setCommand = new HunterGameCommand(this);
      this.dataStorageManager = new DataStorageManager(this);
      this.sharedBackpackManager = new SharedBackpackManager(this);
      this.spectatorService = new SpectatorService(this);
      this.endPortalTracker = new EndPortalTracker(this);
      this.scoreboardManager = new HunterScoreboardManager(this);
      this.glassCageManager = new CageManager(this);
      this.damageprotection = new DamageProtection(this);
      this.disconnectProtection = new DisconnectProtectionService(this);
      long inactivityKickTime = this.getConfig().getLong("game.kickTime", 10L) * 60L * 1000L;
      this.inactivityDetection = new InactivityMonitor(this, inactivityKickTime);
      this.freezeSkill = new FreezeSkill(this);
      this.explosiveCrossbowListener = new ExplosiveCrossbowListener(this);
      this.endermanLimiter = new EndermanLimiter(this);
      this.gameSettlement = new GameSettlement(this);
      this.gameRewards = new GameRewardService(this);
      this.escaperQuitCountdown = new EscaperQuitCountdown(this);
      this.lastDamageTracker = new LastDamageTracker(this);
      this.rankManager = new RankManager(this);
      this.hunterGamePlaceholder = new HunterGamePlaceholder(this);
      this.finalBattleProfessionManager = new FinalBattleProfessionManager(this);
      this.onlineWorldResetManager = new OnlineWorldResetManager(this);
      this.messageBroadcaster = new MessageBroadcaster(this);
      this.seasonManager = new SeasonManager(this, this.rankManager, this.dataStorageManager);
      this.guideManager = new GuideGUI(this);
   }

   private void registerRepeatingTasks() {
      Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 20L, 20L);
      Bukkit.getScheduler().runTaskTimer(this, () -> {
         if (this.isGameRunning()) {
            for(Player hunter : this.getHunters()) {
               if (hunter != null && hunter.isOnline() && hunter.getGameMode() != GameMode.SPECTATOR) {
                  this.spectatorService.showHunterParticleDirection(hunter);
               }
            }
         }

      }, 10L, 10L);
      Bukkit.getScheduler().runTaskTimer(this, () -> {
         if (this.isGameRunning()) {
            this.spectatorService.checkAndTeleportSpectators();
         }

      }, 40L, 40L);
      Bukkit.getScheduler().runTaskTimer(this, this::refreshNightVision, 0L, 200L);
      Bukkit.getScheduler().runTaskTimer(this, this::refreshFinalBattleEscaperGlowing, 0L, 100L);
   }

   private void updateScoreboards() {
      for(Player player : Bukkit.getOnlinePlayers()) {
         if (!this.gameInProgress) {
            this.scoreboardManager.updateWaitingBoard(player);
         } else if (this.isFinalBattleMode()) {
            this.scoreboardManager.updateFinalBattleBoard(player);
         } else {
            this.scoreboardManager.updateGameBoard(player);
         }
      }

   }

   private void refreshNightVision() {
      if (this.isGameRunning()) {
         for(Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 900, 0, false, false));
         }

      }
   }

   private void refreshFinalBattleEscaperGlowing() {
      if (this.isGameRunning() && this.isFinalBattleMode()) {
         PotionEffect glowing = new PotionEffect(PotionEffectType.GLOWING, 160, 0, false, false);

         for(Player escaper : this.getEscapers()) {
            if (escaper != null && escaper.isOnline() && escaper.getGameMode() != GameMode.SPECTATOR) {
               escaper.addPotionEffect(glowing, true);
            }
         }

      }
   }

   private void enablePlaceholderApi() {
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         this.hunterGamePlaceholder.register();
         this.getLogger().info("HunterGame \u7684 PlaceholderAPI \u6269\u5c55\u5df2\u542f\u7528\uff01");
      } else {
         this.getLogger().warning("\u672a\u627e\u5230 PlaceholderAPI\uff0c\u76f8\u5173\u5360\u4f4d\u7b26\u529f\u80fd\u5c06\u4e0d\u53ef\u7528\uff01");
      }

   }

   public void onDisable() {
      if (this.messageBroadcaster != null) {
         this.messageBroadcaster.stopBroadcasting();
      }

      if (this.disconnectProtection != null) {
         this.disconnectProtection.cleanup();
      }

      if (this.endProtector != null) {
         this.endProtector.cleanup();
      }

      if (this.endermanLimiter != null) {
         this.endermanLimiter.stop();
      }

      if (this.endPortalTracker != null) {
         this.endPortalTracker.cancelSearch();
      }

      if (this.oreMultiplier != null) {
         this.oreMultiplier.stop();
      }

      if (this.dataStorageManager != null) {
         this.dataStorageManager.shutdown();
      }

      this.cancelTimeLimitTask();
   }

   public void teleportSpectatorToRandomPlayer(Player spectator) {
      this.spectatorService.teleportToRandomPlayer(spectator);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      return this.setCommand.onCommand(sender, command, label, args);
   }

   private void registerCommandsAndEvents() {
      this.registerEvent(this);
      this.registerEvent(new ChatActivityListener(this, this.inactivityDetection));
      this.registerEvent(this.lastDamageTracker);
      this.registerEvent(new DeathMessageListener(this));
      this.registerEvent(new RoleSelectionHandler(this, this.disconnectProtection));
      this.registerEvent(this.hunterGamePlaceholder);
      ServerSelectorListener serverSelectorListener = new ServerSelectorListener(this);
      this.registerEvent(new PlayerConnectionListener(this, this.escaperQuitCountdown, serverSelectorListener));
      this.registerEvent(serverSelectorListener);
      this.registerEvent(new WaitingLobbyListener(this));
      this.registerEvent(new FinalBattleHunterAdvancementListener(this));
      this.registerEvent(new GameDeathListener(this));
      this.registerEvent(new DragonFightListener(this));
      this.startGameCommand = new StartGame(this);
      this.registerEvent(this.startGameCommand);
      this.registerEvent(new NoDamageListener(this));
      this.hunterTracker = new HunterTracker(this);
      this.registerEvent(this.hunterTracker);
      this.registerEvent(new CustomEntityListener(this));
      this.registerEvent(this.sharedBackpackManager);
      this.registerCommand("huntergame");
      this.registerCommand("hg");
      this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
      this.registerEvent(new HunterRespawnListener(this));
      this.registerEvent(this.skillManager);
      this.registerEvent(this.freezeSkill);
      this.registerEvent(this.explosiveCrossbowListener);
      this.registerEvent(this.gameSettlement);
      this.registerEvent(this.finalBattleProfessionManager);
      this.registerEvent(new MotdListener(this));
      this.spectatorGUI = new SpectatorGUI(this);
      this.registerEvent(this.spectatorGUI);
      if (!this.isVanillaHunterMode()) {
         this.oreMultiplier = new OreMultiplier(this);
      }

   }

   private void registerEvent(Listener listener) {
      this.getServer().getPluginManager().registerEvents(listener, this);
   }

   private void registerCommand(String commandName) {
      PluginCommand pluginCommand = this.getCommand(commandName);
      if (pluginCommand == null) {
         this.getLogger().warning("plugin.yml \u4e2d\u672a\u627e\u5230\u547d\u4ee4: " + commandName);
      } else {
         pluginCommand.setExecutor(this.setCommand);
         pluginCommand.setTabCompleter(this.setCommand);
      }
   }

   public void setGameMode(int mode) {
      this.currentGameMode = mode;
   }

   public boolean isGameRunning() {
      return this.gameInProgress;
   }

   public boolean isGameEnded() {
      return this.gameEnded;
   }

   public int getGameMode() {
      return this.currentGameMode;
   }

   public boolean isFinalBattleMode() {
      return this.currentGameMode == 2;
   }

   public boolean isVanillaHunterMode() {
      return this.currentGameMode == 3;
   }

   public HunterTracker getHunterTracker() {
      return this.hunterTracker;
   }

   public RankManager getRankManager() {
      return this.rankManager;
   }

   public HunterScoreboardManager getScoreboardManager() {
      return this.scoreboardManager;
   }

   public DataStorageManager getDataStorageManager() {
      return this.dataStorageManager;
   }

   public HunterGamePlaceholder getHunterGamePlaceholder() {
      return this.hunterGamePlaceholder;
   }

   public StartGame getStartGameCommand() {
      return this.startGameCommand;
   }

   public SkillManager getSkillManager() {
      return this.skillManager;
   }

   public FreezeSkill getFreezeSkill() {
      return this.freezeSkill;
   }

   public ExplosiveCrossbowListener getExplosiveCrossbowListener() {
      return this.explosiveCrossbowListener;
   }

   public GameRewardService getGameRewardService() {
      return this.gameRewards;
   }

   public EscaperQuitCountdown getEscaperQuitCountdown() {
      return this.escaperQuitCountdown;
   }

   public LastDamageTracker getLastDamageTracker() {
      return this.lastDamageTracker;
   }

   public SeasonManager getSeasonManager() {
      return this.seasonManager;
   }

   public FinalBattleProfessionManager getFinalBattleProfessionManager() {
      return this.finalBattleProfessionManager;
   }

   public void setGameInProgress(boolean status) {
      this.gameInProgress = status;
   }

   public void setServerClosing(boolean serverClosing) {
      this.serverClosing = serverClosing;
   }

   public Location getLobbyLocation() {
      return this.lobbyLocation;
   }

   public boolean isServerClosing() {
      return this.serverClosing;
   }

   public boolean isResetting() {
      return this.resetInProgress || this.onlineWorldResetManager != null && this.onlineWorldResetManager.isResetting();
   }

   public void setResetInProgress(boolean resetInProgress) {
      this.resetInProgress = resetInProgress;
   }

   public boolean beginSettlement() {
      if (!this.settlementStarted && !this.resetScheduled && !this.resetInProgress) {
         this.settlementStarted = true;
         this.gameEnded = true;
         this.setGameInProgress(false);
         this.cancelTimeLimitTask();
         return true;
      } else {
         return false;
      }
   }

   public GuideGUI getGuideManager() {
      return this.guideManager;
   }

   public void setBattleType(int type) {
      this.currentBattleType = type;
   }

   public int getBattleType() {
      return this.currentBattleType;
   }

   public boolean isPersistenceBattle() {
      return this.currentBattleType == 1;
   }

   public void reloadPluginConfig() {
      this.reloadConfig();
      if (this.languageConfigFile != null) {
         this.languageConfigFile.reload();
      }

      if (this.guiConfigFile != null) {
         this.guiConfigFile.reload();
      }

      if (this.messageBroadcaster != null) {
         this.messageBroadcaster.loadConfig();
      }

      if (this.scoreboardManager != null) {
         this.scoreboardManager.reload();
      }

      if (this.skillManager != null) {
         this.skillManager.reloadSkillConfig();
      }

      if (this.finalBattleProfessionManager != null) {
         this.finalBattleProfessionManager.reload();
      }

      this.saveConfig();
      this.getLogger().info("HunterGame \u914d\u7f6e\u6587\u4ef6\u5df2\u91cd\u8f7d!");
   }

   public void loadConfig() {
      FileConfiguration config = this.getConfig();
      String lobbyWorld = config.getString("lobby.world", "world");
      World world = Bukkit.getWorld(lobbyWorld);
      if (world != null) {
         this.lobbyLocation = new Location(world, config.getDouble("lobby.x"), config.getDouble("lobby.y"), config.getDouble("lobby.z"));
      } else {
         this.getLogger().warning("Lobby world not found: " + lobbyWorld);
      }

   }

   public boolean isEscaper(UUID playerId) {
      return this.escapers.contains(playerId);
   }

   public boolean isHunter(UUID playerId) {
      return this.hunters.contains(playerId);
   }

   public boolean isDeathescapers(UUID playerId) {
      return this.deathescapers.contains(playerId);
   }

   public void removeEscaper(UUID playerId) {
      this.escapers.remove(playerId);
   }

   public void addEscaper(UUID playerId) {
      this.hunters.remove(playerId);
      this.escapers.add(playerId);
      this.setRoleTags(playerId, false, true);
   }

   public void removeHunter(UUID playerId) {
      this.hunters.remove(playerId);
   }

   public void addHunter(UUID playerId) {
      this.escapers.remove(playerId);
      this.deathescapers.remove(playerId);
      this.hunters.add(playerId);
      this.setRoleTags(playerId, true, false);
   }

   public void removeDeathescapers(UUID playerId) {
      this.deathescapers.remove(playerId);
   }

   public void addDeathescapers(UUID playerId) {
      this.deathescapers.add(playerId);
   }

   private void setRoleTags(UUID playerId, boolean hunter, boolean escaper) {
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
         player.getPersistentDataContainer().set(RoleSelectionHandler.IS_HUNTER, PersistentDataType.BOOLEAN, hunter);
         player.getPersistentDataContainer().set(RoleSelectionHandler.IS_ESCAPER, PersistentDataType.BOOLEAN, escaper);
      }
   }

   public void clearGameData() {
      this.escapers.clear();
      this.hunters.clear();
      this.deathescapers.clear();
      this.playersWithoutRole.clear();
      this.realSpectators.clear();
      if (this.endPortalTracker != null) {
         this.endPortalTracker.reset();
      }

      if (this.startGameCommand != null) {
         this.startGameCommand.clearFinalBattleRespawnData();
      }

      if (this.finalBattleProfessionManager != null) {
         this.finalBattleProfessionManager.resetSelections();
      }

      if (this.dataStorageManager != null) {
         this.dataStorageManager.resetKillCache();
      }

      if (this.lastDamageTracker != null) {
         this.lastDamageTracker.reset();
      }

   }

   public void addPlayerWithoutRole(Player player) {
      this.playersWithoutRole.add(player);
   }

   public void removePlayerWithoutRole(Player player) {
      this.playersWithoutRole.remove(player);
   }

   public boolean isPlayerWithoutRole(Player player) {
      return this.playersWithoutRole.contains(player);
   }

   public void addRealSpectator(UUID playerId) {
      this.realSpectators.add(playerId);
      Player player = Bukkit.getPlayer(playerId);
      if (this.spectatorGUI != null && player != null && player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
         Bukkit.getScheduler().runTaskLater(this, () -> this.spectatorGUI.updateSpectatorInventory(player), 2L);
      }

   }

   public void removeRealSpectator(UUID playerId) {
      this.realSpectators.remove(playerId);
   }

   public boolean isRealSpectator(UUID playerId) {
      return this.realSpectators.contains(playerId);
   }

   public List<Player> getHunters() {
      return this.getOnlinePlayers(this.hunters);
   }

   public List<Player> getEscapers() {
      return this.getOnlinePlayers(this.escapers);
   }

   private List<Player> getOnlinePlayers(Collection<UUID> playerIds) {
      List<Player> playerList = new ArrayList();

      for(UUID playerId : playerIds) {
         Player player = Bukkit.getPlayer(playerId);
         if (player != null) {
            playerList.add(player);
         }
      }

      return playerList;
   }

   public GameSettlement getGameSettlement() {
      return this.gameSettlement;
   }

   private void loadLobbyWorld() {
      String worldName = this.getConfig().getString("lobby.world", "normal");
      World lobbyWorld = Bukkit.getWorld(worldName);
      if (lobbyWorld == null) {
         this.getLogger().info("\u6b63\u5728\u52a0\u8f7d\u5927\u5385\u4e16\u754c: " + worldName);
         WorldCreator creator = new WorldCreator(worldName);
         creator.environment(Environment.NORMAL);
         creator.generateStructures(true);

         try {
            lobbyWorld = Bukkit.createWorld(creator);
            if (lobbyWorld != null) {
               this.getLogger().info("\u5927\u5385\u4e16\u754c " + worldName + " \u52a0\u8f7d\u6210\u529f\uff01");
               this.updateLobbySpawn(lobbyWorld);
            } else {
               this.getLogger().warning("\u5927\u5385\u4e16\u754c " + worldName + " \u52a0\u8f7d\u5931\u8d25\uff01");
            }
         } catch (Exception e) {
            this.getLogger().severe("\u52a0\u8f7d\u5927\u5385\u4e16\u754c\u65f6\u51fa\u9519: " + e.getMessage());
         }
      } else {
         this.getLogger().info("\u5927\u5385\u4e16\u754c " + worldName + " \u5df2\u7ecf\u52a0\u8f7d");
         this.updateLobbySpawn(lobbyWorld);
      }

   }

   private void updateLobbySpawn(World lobbyWorld) {
      Location spawnLocation = this.getConfiguredLobbyLocation(lobbyWorld);
      lobbyWorld.setSpawnLocation(spawnLocation);
      Logger var10000 = this.getLogger();
      double var10001 = spawnLocation.getX();
      var10000.info("\u5927\u5385\u51fa\u751f\u70b9\u5df2\u8bbe\u7f6e: " + var10001 + ", " + spawnLocation.getY() + ", " + spawnLocation.getZ());
   }

   public World getLobbyWorld() {
      String worldName = this.getConfig().getString("lobby.world", "normal");
      return Bukkit.getWorld(worldName);
   }

   public Location getLobbySpawnLocation() {
      World lobbyWorld = this.getLobbyWorld();
      if (lobbyWorld == null) {
         this.getLogger().warning("\u5927\u5385\u4e16\u754c\u672a\u52a0\u8f7d");
         return ((World)Bukkit.getWorlds().get(0)).getSpawnLocation();
      } else {
         return this.getConfiguredLobbyLocation(lobbyWorld);
      }
   }

   private Location getConfiguredLobbyLocation(World lobbyWorld) {
      double x = this.getConfig().getDouble("lobby.x", (double)0.0F);
      double y = this.getConfig().getDouble("lobby.y", (double)100.0F);
      double z = this.getConfig().getDouble("lobby.z", (double)0.0F);
      return new Location(lobbyWorld, x, y, z);
   }

   public FileConfiguration getGuiConfig() {
      return this.guiConfigFile.getConfig();
   }

   public FileConfiguration getLanguageConfig() {
      return this.languageConfigFile.getConfig();
   }

   public void saveLanguageConfig() {
      this.languageConfigFile.save();
   }

   public String getMessage(String key, String defaultValue) {
      return this.languageConfigFile.getTranslatedString(key, defaultValue);
   }

   public List<String> getMessageList(String key, List<String> defaultValues) {
      List<String> defaults = defaultValues == null ? Collections.emptyList() : defaultValues;
      FileConfiguration config = this.languageConfigFile.getConfig();
      if (!config.isList(key)) {
         config.set(key, defaults);
         this.languageConfigFile.save();
      }

      List<String> values = config.getStringList(key);
      if (values.isEmpty() && !defaults.isEmpty()) {
         values = defaults;
      }

      List<String> translated = new ArrayList();

      for(String value : values) {
         translated.add(ChatColor.translateAlternateColorCodes('&', value));
      }

      return translated;
   }

   public int getGameTime() {
      return this.gameTime;
   }

   public Inventory getHunterSharedInventory() {
      return this.sharedBackpackManager.getHunterInventory();
   }

   public Inventory getEscaperSharedInventory() {
      return this.sharedBackpackManager.getEscaperInventory();
   }

   public void giveSharedBackpack(Player player, boolean isHunter) {
      this.sharedBackpackManager.giveBackpack(player, isHunter);
   }

   public boolean isHunterSharedBackpack(ItemStack item) {
      return this.sharedBackpackManager.isHunterBackpack(item);
   }

   public boolean isEscaperSharedBackpack(ItemStack item) {
      return this.sharedBackpackManager.isEscaperBackpack(item);
   }

   public Location findAndSetNearestEndPortal(Player player, int radius) {
      return this.endPortalTracker.findAndSetNearestEndPortal(player, radius);
   }

   public String getPortalCoordinatesPlaceholder(Player player) {
      return this.endPortalTracker.getPortalCoordinatesPlaceholder(player);
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent event) {
      Player player = event.getPlayer();
      UUID uuid = player.getUniqueId();
      World newWorld = player.getWorld();
      if (newWorld.getEnvironment() == Environment.THE_END && this.isHunter(uuid)) {
         this.removeSpeedEffect(player);
      }

   }

   private void removeSpeedEffect(Player player) {
      if (player.hasPotionEffect(PotionEffectType.SPEED)) {
         player.removePotionEffect(PotionEffectType.SPEED);
      }

   }

   public void resetGame() {
      if (!this.resetScheduled && !this.resetInProgress) {
         if (this.settlementStarted || this.beginSettlement()) {
            this.resetScheduled = true;
            this.setServerClosing(true);
            this.getGameSettlement().showGameEndStats();
            this.endGame();
            int delaySeconds = this.getConfig().getInt("game.end_delay", 15);
            Bukkit.broadcastMessage(this.getMessage("reset_scheduled", "&c\u670d\u52a1\u5668\u5c06\u5728 %seconds% \u79d2\u540e\u5728\u7ebf\u91cd\u7f6e\u5730\u56fe...").replace("%seconds%", String.valueOf(delaySeconds)));
            Bukkit.getScheduler().runTaskLater(this, () -> {
               if (this.onlineWorldResetManager != null) {
                  this.onlineWorldResetManager.startReset();
               } else {
                  this.prepareForOnlineWorldReset();
                  this.completeOnlineWorldReset();
               }

            }, 20L * (long)delaySeconds);
         }
      }
   }

   public void prepareForOnlineWorldReset() {
      this.endGame();
      this.clearGameData();
      if (this.escaperQuitCountdown != null) {
         this.escaperQuitCountdown.cancelSilently();
      }

      if (this.startGameCommand != null) {
         this.startGameCommand.resetRuntimeData();
      }

      if (this.skillManager != null) {
         this.skillManager.resetAllRuntimeData();
      }

      if (this.sharedBackpackManager != null) {
         this.sharedBackpackManager.clearBackpacks();
      }

      if (this.spectatorGUI != null) {
         this.spectatorGUI.clearAllSpectatorSlots();
      }

      if (this.glassCageManager != null) {
         this.glassCageManager.cleanup();
      }

      if (this.disconnectProtection != null) {
         this.disconnectProtection.clearAllData();
      }

      if (this.gameSettlement != null) {
         this.gameSettlement.resetStats();
      }

      if (this.gameRewards != null) {
         this.gameRewards.resetSettlementRewards();
      }

      if (this.oreMultiplier != null) {
         this.oreMultiplier.stop();
      }

   }

   public void completeOnlineWorldReset() {
      this.currentGameMode = 0;
      this.currentBattleType = 0;
      this.startTime = 0L;
      this.gameEnded = false;
      this.gameInProgress = false;
      this.resetScheduled = false;
      this.resetInProgress = false;
      this.settlementStarted = false;
      this.setServerClosing(false);
      this.loadConfig();
      WorldBorderManager.setupWorldBorder();
      this.setupConfiguredEndWorldBorder();
      if (this.oreMultiplier != null) {
         this.oreMultiplier.start();
      }

      this.getLogger().info("\u6e38\u620f\u72b6\u6001\u548c\u6218\u6597\u6570\u636e\u5df2\u6e05\u7406\uff0c\u670d\u52a1\u5668\u56de\u5230\u7b49\u5f85\u72b6\u6001\u3002");
   }

   @EventHandler
   public void onPlayerPortal(PlayerPortalEvent event) {
      Player player = event.getPlayer();
      Location to = event.getTo();
      if (to != null && to.getWorld() != null) {
         if (event.getCause() == TeleportCause.END_PORTAL && to.getWorld().getEnvironment() == Environment.THE_END) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 254, true, false));
         }

      }
   }

   public void startGame() {
      this.gameEnded = false;
      this.settlementStarted = false;
      this.gameInProgress = true;
      this.startTime = System.currentTimeMillis();
      if (this.dataStorageManager != null) {
         this.dataStorageManager.resetKillCache();
      }

      if (this.gameSettlement != null) {
         this.gameSettlement.resetStats();
      }

      if (this.lastDamageTracker != null) {
         this.lastDamageTracker.reset();
      }

      if (this.gameRewards != null) {
         this.gameRewards.resetSettlementRewards();
      }

      this.startTimeLimitCheck();
   }

   public void endGame() {
      this.gameInProgress = false;
      this.cancelTimeLimitTask();
   }

   public String getFormattedGameTime() {
      if (!this.gameInProgress) {
         return "0:00";
      } else {
         long elapsedMillis = System.currentTimeMillis() - this.startTime;
         long seconds = elapsedMillis / 1000L;
         long minutes = seconds / 60L;
         seconds %= 60L;
         return String.format("%d:%02d", minutes, seconds);
      }
   }

   public long getElapsedMinutes() {
      return (System.currentTimeMillis() - this.startTime) / 60000L;
   }

   public long getElapsedSeconds() {
      return (System.currentTimeMillis() - this.startTime) / 1000L;
   }

   private void startTimeLimitCheck() {
      this.cancelTimeLimitTask();
      this.timeLimitTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
         if (this.gameInProgress) {
            if (this.isPersistenceBattle()) {
               int minutes;
               if (this.isFinalBattleMode()) {
                  minutes = this.getConfig().getInt("game.persistence_modes.final_battle_minutes", 10);
               } else {
                  minutes = this.getConfig().getInt("game.persistence_modes.vanilla_hunter_minutes", 25);
               }

               long timeLimit = (long)(minutes * 60) * 1000L;
               long elapsed = System.currentTimeMillis() - this.startTime;
               if (elapsed >= timeLimit) {
                  this.forceEndGamePersistence();
               }
            } else {
               long timeLimit;
               if (this.isFinalBattleMode()) {
                  timeLimit = 1500000L;
               } else {
                  timeLimit = 10800000L;
               }

               long elapsed = System.currentTimeMillis() - this.startTime;
               if (elapsed >= timeLimit) {
                  this.forceEndGameClearance();
               }
            }

         }
      }, 20L, 20L);
   }

   private void cancelTimeLimitTask() {
      if (this.timeLimitTaskId != -1) {
         Bukkit.getScheduler().cancelTask(this.timeLimitTaskId);
         this.timeLimitTaskId = -1;
      }

   }

   private void forceEndGamePersistence() {
      if (this.gameInProgress && this.beginSettlement()) {
         Bukkit.broadcastMessage(this.getMessage("persistence_time_up_escapers_win", "&6\u65f6\u95f4\u5df2\u5230\uff0c\u9003\u751f\u8005\u6210\u529f\u5b58\u6d3b\uff01\u9003\u751f\u8005\u80dc\u5229\uff01"));

         for(Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!this.gameRewards.hasSettlementReward(playerId)) {
               this.getDataStorageManager().saveTotalWins(playerId, player);
               if (!this.isEscaper(playerId) && !this.isDeathescapers(playerId)) {
                  player.sendTitle(this.getMessage("persistence_hunter_fail_title", "&c\u65f6\u95f4\u8017\u5c3d"), this.getMessage("persistence_hunter_fail_subtitle", "&f\u4f60\u8ffd\u6740\u5931\u8d25\u4e86..."), 10, 100, 20);
                  this.gameRewards.giveHunterFailReward(player);
               } else {
                  player.sendTitle(this.getMessage("persistence_escaper_victory_title", "&a\u751f\u5b58\u6210\u529f"), this.getMessage("persistence_escaper_victory_subtitle", "&f\u4f60\u4eec\u575a\u6301\u5230\u4e86\u6700\u540e\uff01"), 10, 100, 20);
                  this.gameRewards.giveEscaperReward(player);
                  this.getDataStorageManager().addEscapeWin(playerId, player);
               }
            }
         }

         this.resetGame();
      }
   }

   private void forceEndGameClearance() {
      if (this.gameInProgress && this.beginSettlement()) {
         Bukkit.broadcastMessage(this.getMessage("clearance_time_up_hunters_win", "&6\u6e38\u620f\u65f6\u95f4\u8017\u5c3d\uff0c\u730e\u4eba\u80dc\u5229\uff01"));

         for(Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!this.gameRewards.hasSettlementReward(playerId)) {
               this.getDataStorageManager().saveTotalWins(playerId, player);
               if (!this.isEscaper(playerId) && !this.isDeathescapers(playerId)) {
                  player.sendTitle(this.getMessage("clearance_hunter_victory_title", "&a\u65f6\u95f4\u8017\u5c3d"), this.getMessage("clearance_hunter_victory_subtitle", "&f\u4f60\u6210\u529f\u5b88\u4f4f\u4e86\u80dc\u5229"), 10, 100, 20);
                  this.getDataStorageManager().addHunterWin(playerId, player);
                  this.gameRewards.giveHunterReward(player);
               } else {
                  player.sendTitle(this.getMessage("clearance_escaper_fail_title", "&c\u65f6\u95f4\u8017\u5c3d"), this.getMessage("clearance_escaper_fail_subtitle", "&f\u4f60\u672a\u80fd\u51fb\u8d25\u672b\u5f71\u9f99"), 10, 100, 20);
                  this.gameRewards.giveEscaperFailReward(player);
               }
            }
         }

         this.resetGame();
      }
   }

   private void startHungerRegenerationTask() {
      (new BukkitRunnable() {
         public void run() {
            for(Player player : HunterGame.this.getServer().getOnlinePlayers()) {
               if (!HunterGame.this.gameInProgress) {
                  int currentFoodLevel = player.getFoodLevel();
                  if (currentFoodLevel < 20) {
                     player.setFoodLevel(currentFoodLevel + 1);
                  }
               }
            }

         }
      }).runTaskTimer(this, 0L, 20L);
   }

   public BaseAPI getBaseAPI() {
      return this.baseAPI;
   }
}

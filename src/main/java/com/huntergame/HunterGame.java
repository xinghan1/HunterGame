package com.huntergame;

import com.huntergame.gui.GuideGUI;
import com.huntergame.gui.SpectatorGUI;
import com.xigua.baseAPI.BaseAPI;
import com.huntergame.message.MessageBroadcaster;
import com.huntergame.motd.MotdListener;
import com.huntergame.rank.RankManager;
import com.huntergame.rank.SeasonManager;
import com.huntergame.command.HunterGameCommand;
import com.huntergame.combat.LastDamageTracker;
import com.huntergame.config.PluginConfigFile;
import com.huntergame.data.DataStorageManager;
import com.huntergame.game.CageManager;
import com.huntergame.game.EscaperQuitCountdown;
import com.huntergame.game.GameSettlement;
import com.huntergame.game.StartGame;
import com.huntergame.inventory.SharedBackpackManager;
import com.huntergame.listener.ChatActivityListener;
import com.huntergame.listener.CustomEntityListener;
import com.huntergame.listener.DeathMessageListener;
import com.huntergame.listener.DragonFightListener;
import com.huntergame.listener.GameDeathListener;
import com.huntergame.listener.HunterRespawnListener;
import com.huntergame.listener.InactivityMonitor;
import com.huntergame.listener.NoDamageListener;
import com.huntergame.listener.PlayerConnectionListener;
import com.huntergame.listener.ServerSelectorListener;
import com.huntergame.listener.WaitingLobbyListener;
import com.huntergame.placeholder.HunterGamePlaceholder;
import com.huntergame.portal.EndPortalTracker;
import com.huntergame.profession.FinalBattleProfessionManager;
import com.huntergame.reward.GameRewardService;
import com.huntergame.role.RoleSelectionHandler;
import com.huntergame.scoreboard.HunterScoreboardManager;
import com.huntergame.session.DisconnectProtectionService;
import com.huntergame.skill.SkillManager;
import com.huntergame.skill.skills.ExplosiveCrossbowListener;
import com.huntergame.skill.skills.FreezeSkill;
import com.huntergame.spectator.SpectatorService;
import com.huntergame.tracking.HunterTracker;
import com.huntergame.vote.VoteSystem;
import com.huntergame.world.*;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class HunterGame extends JavaPlugin implements Listener {

    private final Set<UUID> escapers = new LinkedHashSet<>(); // 逃生者列表
    private final Set<UUID> hunters = new LinkedHashSet<>();  // 猎人列表
    private final Set<UUID> deathescapers = new LinkedHashSet<>();  // 死亡逃生者列表
    private Location lobbyLocation;
    private boolean gameInProgress = false; // 是否开始
    private boolean gameEnded = false; // 是否结束
    private final Set<Player> playersWithoutRole = new HashSet<>(); // 记录未选择角色的玩家
    private final Set<UUID> realSpectators = new HashSet<>(); // 记录真正的旁观者（猎人复活中和选择旁观者角色）
    private PluginConfigFile languageConfigFile;
    private PluginConfigFile guiConfigFile;
    private SharedBackpackManager sharedBackpackManager;
    private SpectatorService spectatorService;
    private EndPortalTracker endPortalTracker;
    HunterScoreboardManager scoreboardManager;
    private HunterGameCommand setCommand;
    public CageManager glassCageManager;
    public DamageProtection damageprotection;
    private long startTime = 0;
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
    private static final int FINAL_BATTLE_GLOWING_REFRESH_TICKS = 20 * 5;
    private static final int FINAL_BATTLE_GLOWING_DURATION_TICKS = 20 * 8;
    private int currentBattleType = 0;
    private int currentGameMode = 0; // 当前游戏模式
    private boolean serverClosing = false;  // 标志服务器是否正在关闭
    private boolean resetScheduled = false;
    private boolean resetInProgress = false;
    private boolean settlementStarted = false;

    private BaseAPI baseAPI;
    @Override
    public void onEnable() {
        initializeOptionalDependencies();
        setupDefaultConfig();
        initializeConfigFiles();
        loadLobbyWorld();

        Bukkit.getScheduler().runTaskLater(this, this::loadConfig, 20L * 5);

        startHungerRegenerationTask(); // 游戏未开始，持续恢复饱食度
        WorldBorderManager.setupWorldBorder();
        getLogger().info("已激活世界边界！");

        initializeManagers();
        registerCommandsAndEvents(); // 设置监听
        registerRepeatingTasks();
        enablePlaceholderApi();
    }

    private void initializeOptionalDependencies() {
        if (Bukkit.getPluginManager().getPlugin("BaseAPI") != null) {
            baseAPI = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
            getLogger().info("发现 BaseAPI，将启用基岩版通信！");
        } else {
            getLogger().warning("未找到 BaseAPI，基岩版通信不可用！");
        }
    }

    private void setupDefaultConfig() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void initializeConfigFiles() {
        languageConfigFile = new PluginConfigFile(this, "message.yml");
        guiConfigFile = new PluginConfigFile(this, "gui.yml");
    }

    private void initializeManagers() {
        skillManager = new SkillManager(this);
        endProtector = new EndWorldProtector(this);
        setCommand = new HunterGameCommand(this);
        dataStorageManager = new DataStorageManager(this);
        sharedBackpackManager = new SharedBackpackManager(this);
        spectatorService = new SpectatorService(this);
        endPortalTracker = new EndPortalTracker(this);
        scoreboardManager = new HunterScoreboardManager(this);
        glassCageManager = new CageManager(this);
        damageprotection = new DamageProtection(this);
        disconnectProtection = new DisconnectProtectionService(this);

        long inactivityKickTime = getConfig().getLong("game.kickTime", 10) * 60 * 1000;
        inactivityDetection = new InactivityMonitor(this, inactivityKickTime);

        freezeSkill = new FreezeSkill(this);
        explosiveCrossbowListener = new ExplosiveCrossbowListener(this);
        endermanLimiter = new EndermanLimiter(this);
        gameSettlement = new GameSettlement(this);
        gameRewards = new GameRewardService(this);
        escaperQuitCountdown = new EscaperQuitCountdown(this);
        lastDamageTracker = new LastDamageTracker(this);
        rankManager = new RankManager(this);
        hunterGamePlaceholder = new HunterGamePlaceholder(this);
        finalBattleProfessionManager = new FinalBattleProfessionManager(this);
        onlineWorldResetManager = new OnlineWorldResetManager(this);
        messageBroadcaster = new MessageBroadcaster(this);
        seasonManager = new SeasonManager(this, rankManager, dataStorageManager);
        guideManager = new GuideGUI(this);
    }

    private void registerRepeatingTasks() {
        Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (isGameRunning()) {
                for (Player hunter : getHunters()) {
                    if (hunter != null && hunter.isOnline() && hunter.getGameMode() != GameMode.SPECTATOR) {
                        spectatorService.showHunterParticleDirection(hunter);
                    }
                }
            }
        }, 10L, 10L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (isGameRunning()) {
                spectatorService.checkAndTeleportSpectators();
            }
        }, 40L, 40L);

        Bukkit.getScheduler().runTaskTimer(this, this::refreshNightVision, 0L, 20L * 10);
        Bukkit.getScheduler().runTaskTimer(this, this::refreshFinalBattleEscaperGlowing, 0L, FINAL_BATTLE_GLOWING_REFRESH_TICKS);
    }

    private void updateScoreboards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gameInProgress) {
                scoreboardManager.updateWaitingBoard(player);
            } else if (isFinalBattleMode()) {
                scoreboardManager.updateFinalBattleBoard(player);
            } else {
                scoreboardManager.updateGameBoard(player);
            }
        }
    }

    private void refreshNightVision() {
        if (!isGameRunning()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 45, 0, false, false));
        }
    }

    private void refreshFinalBattleEscaperGlowing() {
        if (!isGameRunning() || !isFinalBattleMode()) {
            return;
        }

        PotionEffect glowing = new PotionEffect(
                PotionEffectType.GLOWING,
                FINAL_BATTLE_GLOWING_DURATION_TICKS,
                0,
                false,
                false
        );

        for (Player escaper : getEscapers()) {
            if (escaper != null && escaper.isOnline() && escaper.getGameMode() != GameMode.SPECTATOR) {
                escaper.addPotionEffect(glowing, true);
            }
        }
    }

    private void enablePlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            hunterGamePlaceholder.register();
            getLogger().info("HunterGame 的 PlaceholderAPI 扩展已启用！");
        } else {
            getLogger().warning("未找到 PlaceholderAPI，相关占位符功能将不可用！");
        }
    }

    @Override
    public void onDisable() {
        if (messageBroadcaster != null) {
            messageBroadcaster.stopBroadcasting();
        }
        if (disconnectProtection != null) {
            disconnectProtection.cleanup();
        }
        if (endProtector != null) {
            endProtector.cleanup();
        }
        if (endermanLimiter != null) {
            endermanLimiter.stop();
        }
        if (endPortalTracker != null) {
            endPortalTracker.cancelSearch();
        }
        if (oreMultiplier != null) {
            oreMultiplier.stop();
        }
        if (dataStorageManager != null) {
            dataStorageManager.shutdown();
        }
        cancelTimeLimitTask();
    }

    /**
     * 旁观者随机传送到猎人或逃生者位置
     */
    public void teleportSpectatorToRandomPlayer(Player spectator) {
        spectatorService.teleportToRandomPlayer(spectator);
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return setCommand.onCommand(sender, command, label, args);
    }

    private void registerCommandsAndEvents() {
        registerEvent(this);
        registerEvent(new ChatActivityListener(this, inactivityDetection));
        registerEvent(lastDamageTracker);
        registerEvent(new DeathMessageListener(this));
        registerEvent(new RoleSelectionHandler(this, disconnectProtection));
        registerEvent(hunterGamePlaceholder);
        ServerSelectorListener serverSelectorListener = new ServerSelectorListener(this);
        registerEvent(new PlayerConnectionListener(this, escaperQuitCountdown, serverSelectorListener));
        registerEvent(serverSelectorListener);
        registerEvent(new WaitingLobbyListener(this));
        registerEvent(new GameDeathListener(this));
        registerEvent(new DragonFightListener(this));
        startGameCommand = new StartGame(this);
        registerEvent(startGameCommand);
        registerEvent(new NoDamageListener(this));
        hunterTracker = new HunterTracker(this);
        registerEvent(hunterTracker);
        registerEvent(new CustomEntityListener(this));
        registerEvent(sharedBackpackManager);
        registerCommand("huntergame");
        registerCommand("hg");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        registerEvent(new HunterRespawnListener(this));
        registerEvent(skillManager);
        registerEvent(freezeSkill);
        registerEvent(explosiveCrossbowListener);
        registerEvent(gameSettlement);
        registerEvent(finalBattleProfessionManager);
        registerEvent(new MotdListener(this));
        spectatorGUI = new SpectatorGUI(this);
        registerEvent(spectatorGUI);

        // 如果不是原版猎人模式，则触发
        if (!isVanillaHunterMode()) {
            oreMultiplier = new OreMultiplier(this); // 启用 3 倍矿石生成
        }

    }

    private void registerEvent(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommand(String commandName) {
        PluginCommand pluginCommand = getCommand(commandName);
        if (pluginCommand == null) {
            getLogger().warning("plugin.yml 中未找到命令: " + commandName);
            return;
        }
        pluginCommand.setExecutor(setCommand);
        pluginCommand.setTabCompleter(setCommand);
    }

    // 设置游戏模式
    public void setGameMode(int mode) {
        this.currentGameMode = mode;
    }
    // 游戏是否开始
    public boolean isGameRunning() {
        return gameInProgress;
    }
    // 游戏是否结束
    public boolean isGameEnded() {
        return gameEnded;
    }
    // 获取当前游戏模式
    public int getGameMode() {
        return currentGameMode;
    }
    // 检查是否为终章之战
    public boolean isFinalBattleMode() {
        return currentGameMode == MODE_FINAL_BATTLE;
    }
    // 检查是否处于原版猎人模式
    public boolean isVanillaHunterMode() {return currentGameMode == MODE_VANILLA_HUNTER;}
    public HunterTracker getHunterTracker() {
        return hunterTracker;
    }
    public RankManager getRankManager(){
        return rankManager;
    }
    public HunterScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    public DataStorageManager getDataStorageManager() {
        return dataStorageManager;
    }
    public HunterGamePlaceholder getHunterGamePlaceholder() {
        return hunterGamePlaceholder;
    }
    public StartGame getStartGameCommand() {
        return startGameCommand;
    }
    public SkillManager getSkillManager() {
        return skillManager;
    }
    public FreezeSkill getFreezeSkill() {
        return freezeSkill;
    }
    public ExplosiveCrossbowListener getExplosiveCrossbowListener() {
        return explosiveCrossbowListener;
    }
    public GameRewardService getGameRewardService() {
        return gameRewards;
    }
    public EscaperQuitCountdown getEscaperQuitCountdown() {
        return escaperQuitCountdown;
    }
    public LastDamageTracker getLastDamageTracker() {
        return lastDamageTracker;
    }
    public SeasonManager getSeasonManager() {
        return seasonManager;
    }
    public FinalBattleProfessionManager getFinalBattleProfessionManager() {
        return finalBattleProfessionManager;
    }
    public void setGameInProgress(boolean status) {
        gameInProgress = status;
    }
    public void setServerClosing(boolean serverClosing) {
        this.serverClosing = serverClosing;
    }
    public Location getLobbyLocation() {
        return lobbyLocation;
    }
    public boolean isServerClosing() {
        return serverClosing;
    }
    public boolean isResetting() {return resetInProgress || (onlineWorldResetManager != null && onlineWorldResetManager.isResetting());}
    public void setResetInProgress(boolean resetInProgress) {
        this.resetInProgress = resetInProgress;
    }
    public boolean beginSettlement() {
        if (settlementStarted || resetScheduled || resetInProgress) {
            return false;
        }

        settlementStarted = true;
        gameEnded = true;
        setGameInProgress(false);
        cancelTimeLimitTask();
        return true;
    }
    public GuideGUI getGuideManager() {return guideManager;}
    // 设置战役类型
    public void setBattleType(int type) {
        this.currentBattleType = type;
    }
    // 获取战役类型
    public int getBattleType() {
        return currentBattleType;
    }
    // 判断是否为生存战
    public boolean isPersistenceBattle() {
        return currentBattleType == VoteSystem.TYPE_PERSISTENCE;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        if (languageConfigFile != null) {
            languageConfigFile.reload();
        }
        if (guiConfigFile != null) {
            guiConfigFile.reload();
        }
        if (messageBroadcaster != null) {
            messageBroadcaster.loadConfig();
        }
        if (scoreboardManager != null) {
            scoreboardManager.reload();
        }
        if (skillManager != null) {
            skillManager.reloadSkillConfig();
        }
        if (finalBattleProfessionManager != null) {
            finalBattleProfessionManager.reload();
        }
        saveConfig();
        getLogger().info("HunterGame 配置文件已重载!");
    }

    public void loadConfig() {
        FileConfiguration config = getConfig();
        String lobbyWorld = config.getString("lobby.world", "world");
        World world = Bukkit.getWorld(lobbyWorld);
        if (world != null) {
            lobbyLocation = new Location(
                    world,
                    config.getDouble("lobby.x"),
                    config.getDouble("lobby.y"),
                    config.getDouble("lobby.z")
            );
        } else {
            getLogger().warning("Lobby world not found: " + lobbyWorld);
        }
    }


    public boolean isEscaper(UUID playerId) {
        return escapers.contains(playerId);
    }
    public boolean isHunter(UUID playerId) {
        return hunters.contains(playerId);
    }
    public boolean isDeathescapers(UUID playerId) {
        return deathescapers.contains(playerId);
    }

    public void removeEscaper(UUID playerId) {
        escapers.remove(playerId);
    }
    public void addEscaper(UUID playerId) {
        hunters.remove(playerId);
        escapers.add(playerId);
        setRoleTags(playerId, false, true);
    }
    public void removeHunter(UUID playerId) {
        hunters.remove(playerId);
    }
    public void addHunter(UUID playerId) {
        escapers.remove(playerId);
        deathescapers.remove(playerId);
        hunters.add(playerId);
        setRoleTags(playerId, true, false);
    }
    public void removeDeathescapers(UUID playerId) {
        deathescapers.remove(playerId);
    }
    public void addDeathescapers(UUID playerId) {
        deathescapers.add(playerId);
    }

    private void setRoleTags(UUID playerId, boolean hunter, boolean escaper) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().set(RoleSelectionHandler.IS_HUNTER, PersistentDataType.BOOLEAN, hunter);
        player.getPersistentDataContainer().set(RoleSelectionHandler.IS_ESCAPER, PersistentDataType.BOOLEAN, escaper);
    }

    public void clearGameData() {
        escapers.clear();
        hunters.clear();
        deathescapers.clear();
        playersWithoutRole.clear();
        realSpectators.clear(); // 清理旁观者标记
        if (endPortalTracker != null) {
            endPortalTracker.reset();
        }
        if (startGameCommand != null) {
            startGameCommand.clearFinalBattleRespawnData();
        }
        if (finalBattleProfessionManager != null) {
            finalBattleProfessionManager.resetSelections();
        }
        if (dataStorageManager != null) {
            dataStorageManager.resetKillCache();
        }
        if (lastDamageTracker != null) {
            lastDamageTracker.reset();
        }

    }

    public void addPlayerWithoutRole(Player player) {
        playersWithoutRole.add(player);
    }
    public void removePlayerWithoutRole(Player player) {
        playersWithoutRole.remove(player);
    }
    public boolean isPlayerWithoutRole(Player player) {
        return playersWithoutRole.contains(player);
    }

    /**
     * 添加真正的旁观者（猎人复活中或选择旁观者角色）
     */
    public void addRealSpectator(UUID playerId) {
        realSpectators.add(playerId);
        // 更新旁观者背包
        Player player = Bukkit.getPlayer(playerId);
        if (spectatorGUI != null && player != null && player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
            Bukkit.getScheduler().runTaskLater(this, () -> spectatorGUI.updateSpectatorInventory(player), 2L);
        }
    }

    /**
     * 移除真正的旁观者标记
     */
    public void removeRealSpectator(UUID playerId) {
        realSpectators.remove(playerId);
    }

    /**
     * 检查是否为真正的旁观者
     */
    public boolean isRealSpectator(UUID playerId) {
        return realSpectators.contains(playerId);
    }
    public List<Player> getHunters() {
        return getOnlinePlayers(hunters);
    }
    public List<Player> getEscapers() {
        return getOnlinePlayers(escapers);
    }

    private List<Player> getOnlinePlayers(Collection<UUID> playerIds) {
        List<Player> playerList = new ArrayList<>();
        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                playerList.add(player);
            }
        }
        return playerList;
    }

    public GameSettlement getGameSettlement() {
        return gameSettlement;
    }

    /**
     * 加载大厅世界
     */
    private void loadLobbyWorld() {
        String worldName = getConfig().getString("lobby.world", "normal");

        World lobbyWorld = Bukkit.getWorld(worldName);

        if (lobbyWorld == null) {
            getLogger().info("正在加载大厅世界: " + worldName);

            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(true);

            try {
                lobbyWorld = Bukkit.createWorld(creator);
                if (lobbyWorld != null) {
                    getLogger().info("大厅世界 " + worldName + " 加载成功！");
                    updateLobbySpawn(lobbyWorld);
                } else {
                    getLogger().warning("大厅世界 " + worldName + " 加载失败！");
                }
            } catch (Exception e) {
                getLogger().severe("加载大厅世界时出错: " + e.getMessage());
            }
        } else {
            getLogger().info("大厅世界 " + worldName + " 已经加载");
            updateLobbySpawn(lobbyWorld);
        }
    }

    private void updateLobbySpawn(World lobbyWorld) {
        Location spawnLocation = getConfiguredLobbyLocation(lobbyWorld);
        lobbyWorld.setSpawnLocation(spawnLocation);
        getLogger().info("大厅出生点已设置: "
                + spawnLocation.getX() + ", "
                + spawnLocation.getY() + ", "
                + spawnLocation.getZ());
    }

    /**
     * 获取大厅世界
     */
    public World getLobbyWorld() {
        String worldName = getConfig().getString("lobby.world", "normal");
        return Bukkit.getWorld(worldName);
    }

    /**
     * 获取大厅出生点
     */
    public Location getLobbySpawnLocation() {
        World lobbyWorld = getLobbyWorld();
        if (lobbyWorld == null) {
            getLogger().warning("大厅世界未加载");
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        return getConfiguredLobbyLocation(lobbyWorld);
    }

    private Location getConfiguredLobbyLocation(World lobbyWorld) {
        double x = getConfig().getDouble("lobby.x", 0);
        double y = getConfig().getDouble("lobby.y", 100.0);
        double z = getConfig().getDouble("lobby.z", 0);
        return new Location(lobbyWorld, x, y, z);
    }

    public FileConfiguration getGuiConfig() {
        return guiConfigFile.getConfig();
    }

    public FileConfiguration getLanguageConfig() {
        return languageConfigFile.getConfig();
    }

    public void saveLanguageConfig() {
        languageConfigFile.save();
    }

    public String getMessage(String key, String defaultValue) {
        return languageConfigFile.getTranslatedString(key, defaultValue);
    }

    public List<String> getMessageList(String key, List<String> defaultValues) {
        List<String> defaults = defaultValues == null ? Collections.emptyList() : defaultValues;
        FileConfiguration config = languageConfigFile.getConfig();
        if (!config.isList(key)) {
            config.set(key, defaults);
            languageConfigFile.save();
        }

        List<String> values = config.getStringList(key);
        if (values.isEmpty() && !defaults.isEmpty()) {
            values = defaults;
        }

        List<String> translated = new ArrayList<>();
        for (String value : values) {
            translated.add(ChatColor.translateAlternateColorCodes('&', value));
        }
        return translated;
    }

    private int gameTime = 300;
    public int getGameTime() {
        return gameTime;
    }

    // 猎人共享背包GUI
    public Inventory getHunterSharedInventory() {
        return sharedBackpackManager.getHunterInventory();
    }

    // 逃生者共享背包GUI
    public Inventory getEscaperSharedInventory() {
        return sharedBackpackManager.getEscaperInventory();
    }

    // 发放共享背包
    public void giveSharedBackpack(Player player, boolean isHunter) {
        sharedBackpackManager.giveBackpack(player, isHunter);
    }

    public boolean isHunterSharedBackpack(ItemStack item) {
        return sharedBackpackManager.isHunterBackpack(item);
    }
    public boolean isEscaperSharedBackpack(ItemStack item) {
        return sharedBackpackManager.isEscaperBackpack(item);
    }
    public Location findAndSetNearestEndPortal(Player player, int radius) {
        return endPortalTracker.findAndSetNearestEndPortal(player, radius);
    }

    public String getPortalCoordinatesPlaceholder(Player player) {
        return endPortalTracker.getPortalCoordinatesPlaceholder(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        World newWorld = player.getWorld();

        // 检查玩家是否进入末地
        if (newWorld.getEnvironment() == World.Environment.THE_END && isHunter(uuid)) {
            // 清除玩家的速度效果
            removeSpeedEffect(player);
        }
    }

    private void removeSpeedEffect(Player player) {
        // 检查玩家是否有速度效果
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            // 移除速度效果
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    // 游戏结束后在线重置，不再重启服务器
    public void resetGame() {
        if (resetScheduled || resetInProgress) {
            return;
        }
        if (!settlementStarted && !beginSettlement()) {
            return;
        }

        resetScheduled = true;
        setServerClosing(true); // 游戏结束到地图重置完成前，禁止新玩家进入
        getGameSettlement().showGameEndStats(); // 游戏结算
        endGame();
        int delaySeconds = getConfig().getInt("game.end_delay", 15);
        Bukkit.broadcastMessage(getMessage("reset_scheduled", "&c服务器将在 %seconds% 秒后在线重置地图...")
                .replace("%seconds%", String.valueOf(delaySeconds)));

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (onlineWorldResetManager != null) {
                onlineWorldResetManager.startReset();
            } else {
                prepareForOnlineWorldReset();
                completeOnlineWorldReset();
            }
        }, 20L * delaySeconds);
    }

    public void prepareForOnlineWorldReset() {
        endGame();
        clearGameData();
        if (escaperQuitCountdown != null) {
            escaperQuitCountdown.cancelSilently();
        }
        if (startGameCommand != null) {
            startGameCommand.resetRuntimeData();
        }
        if (skillManager != null) {
            skillManager.resetAllRuntimeData();
        }
        if (sharedBackpackManager != null) {
            sharedBackpackManager.clearBackpacks();
        }
        if (spectatorGUI != null) {
            spectatorGUI.clearAllSpectatorSlots();
        }
        if (glassCageManager != null) {
            glassCageManager.cleanup();
        }
        if (disconnectProtection != null) {
            disconnectProtection.clearAllData();
        }
        if (gameSettlement != null) {
            gameSettlement.resetStats();
        }
        if (gameRewards != null) {
            gameRewards.resetSettlementRewards();
        }
        if (oreMultiplier != null) {
            oreMultiplier.stop();
        }
    }

    public void completeOnlineWorldReset() {
        currentGameMode = 0;
        currentBattleType = 0;
        startTime = 0;
        gameEnded = false;
        gameInProgress = false;
        resetScheduled = false;
        resetInProgress = false;
        settlementStarted = false;
        setServerClosing(false);
        loadConfig();
        WorldBorderManager.setupWorldBorder();
        if (oreMultiplier != null) {
            oreMultiplier.start();
        }
        getLogger().info("游戏状态和战斗数据已清理，服务器回到等待状态。");
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }

        // 判断是否通过末地传送门进入末地
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                && to.getWorld().getEnvironment() == World.Environment.THE_END) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE,
                    100,
                    254,
                    true,
                    false
            ));
        }
    }


    public void startGame() {
        gameEnded = false;
        settlementStarted = false;
        gameInProgress = true;
        startTime = System.currentTimeMillis();
        if (dataStorageManager != null) {
            dataStorageManager.resetKillCache();
        }
        if (gameSettlement != null) {
            gameSettlement.resetStats();
        }
        if (lastDamageTracker != null) {
            lastDamageTracker.reset();
        }
        if (gameRewards != null) {
            gameRewards.resetSettlementRewards();
        }
        startTimeLimitCheck();
    }

    public void endGame() {
        gameInProgress = false;
        cancelTimeLimitTask();
    }

    // 获取当前游戏时间
    public String getFormattedGameTime() {
        if (!gameInProgress) return "0:00";

        long elapsedMillis = System.currentTimeMillis() - startTime;
        long seconds = elapsedMillis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    public long getElapsedMinutes() {
        return (System.currentTimeMillis() - startTime) / (1000 * 60);
    }
    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    private void startTimeLimitCheck() {
        cancelTimeLimitTask();
        timeLimitTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                this,
                () -> {
                    if (!gameInProgress) return;

                    long timeLimit;

                    // 生存战逻辑：时间到 -> 逃生者胜利
                    if (isPersistenceBattle()) {
                        int minutes;
                        if (isFinalBattleMode()) {
                            minutes = getConfig().getInt("game.persistence_modes.final_battle_minutes", 10);
                        } else {
                            minutes = getConfig().getInt("game.persistence_modes.vanilla_hunter_minutes", 25);
                        }
                        timeLimit = minutes * 60 * 1000L;

                        long elapsed = System.currentTimeMillis() - startTime;
                        if (elapsed >= timeLimit) {
                            forceEndGamePersistence(); // 生存战时间到
                        }
                    }
                    // 通关战逻辑：时间到 -> 猎人胜利
                    else {
                        if (isFinalBattleMode()) {
                            timeLimit = 25 * 60 * 1000L;
                        } else {
                            timeLimit = 3 * 60 * 60 * 1000L;
                        }

                        long elapsed = System.currentTimeMillis() - startTime;
                        if (elapsed >= timeLimit) {
                            forceEndGameClearance(); // 通关战时间到
                        }
                    }
                },
                20L, 20L);

    }

    private void cancelTimeLimitTask() {
        if (timeLimitTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timeLimitTaskId);
            timeLimitTaskId = -1;
        }
    }

    // 生存战时间结束：逃生者胜利
    private void forceEndGamePersistence() {
        if (!gameInProgress || !beginSettlement()) return;

        Bukkit.broadcastMessage(getMessage("persistence_time_up_escapers_win", "&6时间已到，逃生者成功存活！逃生者胜利！"));

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (gameRewards.hasSettlementReward(playerId)) {
                continue;
            }

            getDataStorageManager().saveTotalWins(playerId, player);
            // 逃生者胜利
            if (isEscaper(playerId) || isDeathescapers(playerId)) {
                player.sendTitle(
                        getMessage("persistence_escaper_victory_title", "&a生存成功"),
                        getMessage("persistence_escaper_victory_subtitle", "&f你们坚持到了最后！"),
                        10, 100, 20
                );
                gameRewards.giveEscaperReward(player);
                getDataStorageManager().addEscapeWin(playerId, player);
            } else {
                player.sendTitle(
                        getMessage("persistence_hunter_fail_title", "&c时间耗尽"),
                        getMessage("persistence_hunter_fail_subtitle", "&f你追杀失败了..."),
                        10, 100, 20
                );
                gameRewards.giveHunterFailReward(player);
            }
        }
        resetGame();
    }

    // 通关战时间结束：猎人胜利
    private void forceEndGameClearance() {
        if (!gameInProgress || !beginSettlement()) return;
        Bukkit.broadcastMessage(getMessage("clearance_time_up_hunters_win", "&6游戏时间耗尽，猎人胜利！"));

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (gameRewards.hasSettlementReward(playerId)) {
                continue;
            }

            getDataStorageManager().saveTotalWins(playerId, player);
            if (isEscaper(playerId) || isDeathescapers(playerId)) {
                player.sendTitle(
                        getMessage("clearance_escaper_fail_title", "&c时间耗尽"),
                        getMessage("clearance_escaper_fail_subtitle", "&f你未能击败末影龙"),
                        10, 100, 20
                );
                gameRewards.giveEscaperFailReward(player);
            } else {
                player.sendTitle(
                        getMessage("clearance_hunter_victory_title", "&a时间耗尽"),
                        getMessage("clearance_hunter_victory_subtitle", "&f你成功守住了胜利"),
                        10, 100, 20
                );
                getDataStorageManager().addHunterWin(playerId, player);
                gameRewards.giveHunterReward(player);
            }
        }
        resetGame();
    }

    // 恢复饱食度
    private void startHungerRegenerationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    if (!gameInProgress){
                        int currentFoodLevel = player.getFoodLevel();
                        if (currentFoodLevel < 20) {
                            player.setFoodLevel(currentFoodLevel + 1);
                        }
                    }

                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public BaseAPI getBaseAPI() {
        return baseAPI;
    }
}

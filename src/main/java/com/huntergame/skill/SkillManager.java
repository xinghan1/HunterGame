package com.huntergame.skill;

import com.huntergame.HunterGame;
import com.huntergame.skill.skills.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SkillManager implements Listener {
    public static final Map<String, Integer> SKILL_COOLDOWNS = new HashMap<>();
    public static final Map<String, Integer> SKILL_DURATIONS = new HashMap<>();

    static {
        SKILL_COOLDOWNS.put("二段跳", 22);
        SKILL_COOLDOWNS.put("突进", 30);
        SKILL_COOLDOWNS.put("爆破专家", 30);
        SKILL_COOLDOWNS.put("隐身", 90);
        SKILL_COOLDOWNS.put("盾构机", 40);
        SKILL_COOLDOWNS.put("神龟", 170);
        SKILL_COOLDOWNS.put("爆炸弩", 200);
        SKILL_COOLDOWNS.put("穿墙", 265);
        SKILL_COOLDOWNS.put("击退领域", 45);
        SKILL_COOLDOWNS.put("肾上腺爆发", 110);
        SKILL_COOLDOWNS.put("熔岩行者", 30);
        SKILL_COOLDOWNS.put("闪现", 120);
        SKILL_COOLDOWNS.put("定身术", 125);
        SKILL_COOLDOWNS.put("腾空", 12);

        SKILL_DURATIONS.put("隐身", 30);
        SKILL_DURATIONS.put("盾构机", 30);
        SKILL_DURATIONS.put("神龟", 20);
        SKILL_DURATIONS.put("肾上腺爆发", 15);
        SKILL_DURATIONS.put("定身术", 3);
        SKILL_DURATIONS.put("穿墙", 4);
    }

    public final Map<UUID, String> escapeeSkills = new HashMap<>();
    public final Map<UUID, String> hunterSkills = new HashMap<>();

    private static final String SKILLS_FILE = "skills.yml";

    private final HunterGame plugin;
    private final SkillContext context;
    private final Map<String, HunterSkill> skills = new LinkedHashMap<>();
    private final Map<UUID, Cooldown> cooldowns = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bossBarTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> activeDurationOverrides = new ConcurrentHashMap<>();

    private File skillsFile;
    private FileConfiguration skillsConfig;
    private long endGlobalCooldownUntil = 0L;

    public SkillManager(HunterGame plugin) {
        this.plugin = plugin;
        this.context = new SkillContext(plugin, this);
        registerSkills();
        reloadSkillConfig();
    }

    private void registerSkills() {
        registerSkill(new DoubleJumpSkill());
        registerSkill(new DashSkill());
        registerSkill(new DemolitionExpertSkill());
        registerSkill(new InvisibilitySkill());
        registerSkill(new HasteMinerSkill());
        registerSkill(new TurtleSkill());
        registerSkill(new ExplosiveCrossbowSkill());
        registerSkill(new WallPhaseSkill());
        registerSkill(new KnockbackFieldSkill());
        registerSkill(new AdrenalineBurstSkill());
        registerSkill(new LavaWalkerSkill());
        registerSkill(new BlinkSkill());
        registerSkill(new FreezeSpellSkill());
        registerSkill(new AirLaunchSkill());
    }

    private void registerSkill(HunterSkill skill) {
        skills.put(skill.getName(), skill);
    }

    public void reloadSkillConfig() {
        ensureSkillsFile();
        skillsConfig = YamlConfiguration.loadConfiguration(skillsFile);
        try (InputStream defaultStream = plugin.getResource(SKILLS_FILE)) {
            if (defaultStream != null) {
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );
                skillsConfig.setDefaults(defaults);
                skillsConfig.options().copyDefaults(true);
                skillsConfig.save(skillsFile);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 skills.yml 默认节点失败: " + ex.getMessage());
        }
    }

    public void resetAllRuntimeData() {
        escapeeSkills.clear();
        hunterSkills.clear();
        cooldowns.clear();
        activeDurationOverrides.clear();
        endGlobalCooldownUntil = 0L;

        for (Integer taskId : bossBarTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        bossBarTasks.clear();

        for (BossBar bossBar : playerBossBars.values()) {
            bossBar.removeAll();
        }
        playerBossBars.clear();
    }

    private void ensureSkillsFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        skillsFile = new File(plugin.getDataFolder(), SKILLS_FILE);
        if (!skillsFile.exists()) {
            plugin.saveResource(SKILLS_FILE, false);
        }
    }

    private String normalizeSkillName(String skill) {
        if (skill == null) {
            return "";
        }
        String cleanName = ChatColor.stripColor(skill);
        return cleanName == null ? "" : cleanName.trim();
    }

    private ConfigurationSection getSkillSection(String skill) {
        if (skillsConfig == null) {
            reloadSkillConfig();
        }
        String cleanName = normalizeSkillName(skill);
        if (cleanName.isEmpty()) {
            return null;
        }
        return skillsConfig.getConfigurationSection("skills." + cleanName);
    }

    public boolean isSkillConfigured(String skill) {
        String cleanName = normalizeSkillName(skill);
        return skills.containsKey(cleanName) && getSkillSection(cleanName) != null;
    }

    public boolean isSkillEnabled(String skill) {
        String cleanName = normalizeSkillName(skill);
        if (!skills.containsKey(cleanName)) {
            return false;
        }
        ConfigurationSection section = getSkillSection(cleanName);
        return section != null && section.getBoolean("enabled", true);
    }

    public boolean canGrantSkill(String skill) {
        return isSkillConfigured(skill) && isSkillEnabled(skill);
    }

    public int getSkillCooldown(String skill) {
        String cleanName = normalizeSkillName(skill);
        ConfigurationSection section = getSkillSection(cleanName);
        int fallback = SKILL_COOLDOWNS.getOrDefault(cleanName, 0);
        return section == null ? fallback : section.getInt("cooldown", fallback);
    }

    public int getSkillDuration(String skill) {
        String cleanName = normalizeSkillName(skill);
        ConfigurationSection section = getSkillSection(cleanName);
        int fallback = SKILL_DURATIONS.getOrDefault(cleanName, 0);
        return section == null ? fallback : section.getInt("duration", fallback);
    }

    public int getSkillInt(String skill, String key, int fallback) {
        ConfigurationSection section = getSkillSection(skill);
        return section == null ? fallback : section.getInt("parameters." + key, fallback);
    }

    public double getSkillDouble(String skill, String key, double fallback) {
        ConfigurationSection section = getSkillSection(skill);
        return section == null ? fallback : section.getDouble("parameters." + key, fallback);
    }

    public List<Integer> getSkillIntegerList(String skill, String key, List<Integer> fallback) {
        ConfigurationSection section = getSkillSection(skill);
        if (section == null || !section.isList("parameters." + key)) {
            return fallback;
        }
        List<Integer> values = section.getIntegerList("parameters." + key);
        return values.isEmpty() ? fallback : values;
    }

    public boolean isWeaponOrTool(Material material) {
        String matName = material.name();
        return matName.endsWith("_SWORD") || matName.endsWith("_PICKAXE") || matName.endsWith("_AXE");
    }

    public boolean isEndGlobalCooldownActive() {
        return System.currentTimeMillis() < endGlobalCooldownUntil;
    }

    public String getSelectedSkill(Player player) {
        UUID uuid = player.getUniqueId();
        if (escapeeSkills.containsKey(uuid)) {
            return escapeeSkills.get(uuid);
        }
        if (hunterSkills.containsKey(uuid)) {
            return hunterSkills.get(uuid);
        }
        return null;
    }

    public void confirmSkillSelection(Player player, String skillName) {
        String cleanName = normalizeSkillName(skillName);
        UUID uuid = player.getUniqueId();
        HunterSkill skill = getEnabledSkill(cleanName);

        if (skill == null || !canGrantSkill(cleanName)) {
            player.sendMessage(plugin.getMessage("skill_not_configured_or_disabled", "&c该职业技能未配置或已禁用: %skill%")
                    .replace("%skill%", cleanName));
            return;
        }

        cooldowns.remove(uuid);
        activeDurationOverrides.remove(uuid);
        cancelBossBarTask(uuid);
        removeBossBar(uuid);

        if (plugin.isEscaper(uuid)) {
            hunterSkills.remove(uuid);
            escapeeSkills.put(uuid, cleanName);
        } else {
            escapeeSkills.remove(uuid);
            hunterSkills.put(uuid, cleanName);
        }

        skill.onSelected(player, context);
        showReadyBossBar(player, cleanName);

        player.sendMessage(plugin.getMessage("selected_skills", "&f你选择了 职业技能: &6%jobs%")
                .replace("%jobs%", cleanName)
        );
        player.closeInventory();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String selectedSkill = getSelectedSkill(player);

        removeBossBar(uuid);
        cancelBossBarTask(uuid);

        if (selectedSkill == null || selectedSkill.isEmpty() || !isSkillEnabled(selectedSkill)) {
            return;
        }
        BossBar newBar = createBossBar(player, getBossBarText(selectedSkill, "ready", 0));
        Cooldown cooldown = cooldowns.get(uuid);
        if (cooldown == null) {
            showReadyBossBar(player, selectedSkill);
            return;
        }

        long currentTime = System.currentTimeMillis();
        int duration = activeDurationOverrides.getOrDefault(uuid, cooldown.durationSeconds);
        long durationEndTime = cooldown.lastUsed + (duration * 1000L);
        long cooldownEndTime = cooldown.lastUsed + (cooldown.totalSeconds() * 1000L);

        if (duration > 0 && currentTime < durationEndTime) {
            int remainingDuration = (int) Math.max(1, (durationEndTime - currentTime) / 1000);
            startDurationTracking(player, selectedSkill, remainingDuration, cooldown.cooldownSeconds);
        } else if (currentTime < cooldownEndTime) {
            int remainingCooldown = (int) Math.max(1, (cooldownEndTime - currentTime) / 1000);
            startCooldownTracking(player, selectedSkill, remainingCooldown);
        } else {
            newBar.setVisible(true);
            showReadyBossBar(player, selectedSkill);
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        String skillName = getSelectedSkill(player);
        if (!"突进".equals(skillName)
                && !"腾空".equals(skillName) && !player.isSneaking()) {
            return;
        }
        HunterSkill skill = getEnabledSkill(skillName);
        if (skill == null || !skill.canActivateByRightClick()) {
            return;
        }

        if (!skill.canActivateWithItem(player.getInventory().getItemInMainHand(), context)) {
            return;
        }

        if (!checkCooldown(player, skillName)) {
            return;
        }

        SkillActivationResult result = skill.activate(player, context);
        if (result.isActivated()) {
            startCooldown(player, skillName, result.getDurationOverrideSeconds());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        dispatchSelectedSkill(event.getPlayer(), skill -> skill.onPlayerMove(event, context));
    }

    @EventHandler
    public void onPlayerJump(PlayerToggleFlightEvent event) {
        dispatchSelectedSkill(event.getPlayer(), skill -> skill.onPlayerToggleFlight(event, context));
    }

    @EventHandler
    public void onEnterEnd(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getName().equalsIgnoreCase("world_the_end")) {
            endGlobalCooldownUntil = System.currentTimeMillis() + 10_000;
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            capSpearDamage(event, damager);
            dispatchSelectedSkill(damager, skill -> skill.onEntityDamageByEntity(event, context));
        }
    }

    private void capSpearDamage(EntityDamageByEntityEvent event, Player damager) {
        if (event.isCancelled() || !isSpear(damager.getInventory().getItemInMainHand())) {
            return;
        }

        double maxDamage = getSkillDouble("突进", "max_spear_damage", 18.0);
        if (maxDamage > 0 && event.getDamage() > maxDamage) {
            event.setDamage(maxDamage);
        }
    }

    private boolean isSpear(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return item.getType().name().contains("SPEAR");
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        forEachSkill(skill -> skill.onEntityDamage(event, context));
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        forEachSkill(skill -> skill.onPlayerRespawn(event, context));
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        dispatchSelectedSkill(event.getPlayer(), skill -> skill.onLeftClick(event, context));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        forEachSkill(skill -> skill.onPlayerQuit(event, context));
        removeBossBar(uuid);
        cancelBossBarTask(uuid);
        activeDurationOverrides.remove(uuid);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        forEachSkill(skill -> skill.onPlayerDeath(event, context));
    }

    public boolean checkCooldown(Player player, String skill) {
        int lockRemainingSeconds = getFinalBattleSkillLockRemainingSeconds(player);
        if (lockRemainingSeconds > 0) {
            player.sendActionBar(plugin.getMessage("skill_start_lock_actionbar", "&c终章开局保护中，技能将在 %seconds% 秒后可用")
                    .replace("%seconds%", String.valueOf(lockRemainingSeconds)));
            return false;
        }

        Cooldown cooldown = cooldowns.get(player.getUniqueId());
        if (cooldown == null) {
            return true;
        }

        long cooldownEndTime = cooldown.lastUsed + (cooldown.totalSeconds() * 1000L);
        if (System.currentTimeMillis() >= cooldownEndTime) {
            cooldowns.remove(player.getUniqueId());
            activeDurationOverrides.remove(player.getUniqueId());
            showReadyBossBar(player, skill);
            return true;
        }

        return false;
    }

    private int getFinalBattleSkillLockRemainingSeconds(Player player) {
        if (!plugin.isGameRunning() || !plugin.isFinalBattleMode()) {
            return 0;
        }

        UUID uuid = player.getUniqueId();
        if (!plugin.isHunter(uuid) && !plugin.isEscaper(uuid)) {
            return 0;
        }

        int lockSeconds = Math.max(0, plugin.getConfig().getInt("final_battle.skill_lock_seconds", 40));
        long elapsedSeconds = plugin.getElapsedSeconds();
        return (int) Math.max(0, lockSeconds - elapsedSeconds);
    }

    public void startCooldown(Player player, String skill) {
        startCooldown(player, skill, null);
    }

    private void startCooldown(Player player, String skill, Integer durationOverrideSeconds) {
        int cooldown = getSkillCooldown(skill);
        int duration = durationOverrideSeconds == null ? getSkillDuration(skill) : durationOverrideSeconds;
        UUID uuid = player.getUniqueId();

        Cooldown cd = new Cooldown();
        cd.lastUsed = System.currentTimeMillis();
        cd.cooldownSeconds = cooldown;
        cd.durationSeconds = duration;
        cooldowns.put(uuid, cd);

        if (duration > 0) {
            activeDurationOverrides.put(uuid, duration);
            startDurationTracking(player, skill, duration, cooldown);
        } else {
            activeDurationOverrides.remove(uuid);
            startCooldownTracking(player, skill, cooldown);
        }
    }

    private void startDurationTracking(Player player, String skill, int duration, int cooldown) {
        UUID uuid = player.getUniqueId();
        long endTime = System.currentTimeMillis() + (duration * 1000L);
        long startTime = System.currentTimeMillis();

        cancelBossBarTask(uuid);
        AtomicInteger taskId = new AtomicInteger();
        taskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline() || !skill.equals(getSelectedSkill(player))) {
                Bukkit.getScheduler().cancelTask(taskId.get());
                bossBarTasks.remove(uuid);
                return;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime >= endTime) {
                Bukkit.getScheduler().cancelTask(taskId.get());
                bossBarTasks.remove(uuid);
                activeDurationOverrides.remove(uuid);
                startCooldownTracking(player, skill, cooldown);
                return;
            }

            double progress = 1.0 - (currentTime - startTime) / (double) (duration * 1000L);
            int secondsLeft = (int) Math.ceil((endTime - currentTime) / 1000.0);
            updateDurationBossBar(player, skill, clampProgress(progress), secondsLeft);
        }, 0L, 20L));

        bossBarTasks.put(uuid, taskId.get());
    }

    private void startCooldownTracking(Player player, String skill, int cooldown) {
        UUID uuid = player.getUniqueId();
        if (cooldown <= 0) {
            showReadyBossBar(player, skill);
            return;
        }

        long endTime = System.currentTimeMillis() + (cooldown * 1000L);
        long startTime = System.currentTimeMillis();

        cancelBossBarTask(uuid);
        AtomicInteger taskId = new AtomicInteger();
        taskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline() || !skill.equals(getSelectedSkill(player))) {
                Bukkit.getScheduler().cancelTask(taskId.get());
                bossBarTasks.remove(uuid);
                return;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime >= endTime) {
                Bukkit.getScheduler().cancelTask(taskId.get());
                bossBarTasks.remove(uuid);
                showReadyBossBar(player, skill);
                return;
            }

            double progress = (currentTime - startTime) / (double) (cooldown * 1000L);
            int secondsLeft = (int) Math.ceil((endTime - currentTime) / 1000.0);
            updateCooldownBossBar(player, skill, clampProgress(progress), secondsLeft);
        }, 0L, 20L));

        bossBarTasks.put(uuid, taskId.get());
    }

    private HunterSkill getEnabledSkill(String skillName) {
        String cleanName = normalizeSkillName(skillName);
        if (!isSkillEnabled(cleanName)) {
            return null;
        }
        return skills.get(cleanName);
    }

    private void dispatchSelectedSkill(Player player, Consumer<HunterSkill> action) {
        HunterSkill skill = getEnabledSkill(getSelectedSkill(player));
        if (skill != null) {
            action.accept(skill);
        }
    }

    private void forEachSkill(Consumer<HunterSkill> action) {
        for (HunterSkill skill : skills.values()) {
            action.accept(skill);
        }
    }

    private BossBar getOrCreateBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bossBar = playerBossBars.get(uuid);
        if (bossBar == null) {
            bossBar = createBossBar(player, plugin.getMessage("skill_bossbar_ready_fallback", "&a技能就绪"));
            bossBar.setVisible(false);
        }
        return bossBar;
    }

    private BossBar createBossBar(Player player, String title) {
        BossBar bossBar = Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);
        playerBossBars.put(player.getUniqueId(), bossBar);
        return bossBar;
    }

    private void showReadyBossBar(Player player, String skillName) {
        BossBar bossBar = getOrCreateBossBar(player);
        bossBar.setVisible(skillName != null && !skillName.isEmpty());
        bossBar.setColor(BarColor.GREEN);
        bossBar.setTitle(getBossBarText(skillName, "ready", 0));
        bossBar.setProgress(1.0);
    }

    private void updateDurationBossBar(Player player, String skillName, double progress, int secondsLeft) {
        BossBar bossBar = getOrCreateBossBar(player);
        bossBar.setVisible(true);
        bossBar.setColor(BarColor.BLUE);
        bossBar.setTitle(getBossBarText(skillName, "duration", secondsLeft));
        bossBar.setProgress(progress);
    }

    private void updateCooldownBossBar(Player player, String skillName, double progress, int secondsLeft) {
        BossBar bossBar = getOrCreateBossBar(player);
        bossBar.setVisible(true);
        bossBar.setColor(BarColor.RED);
        bossBar.setTitle(getBossBarText(skillName, "cooldown", secondsLeft));
        bossBar.setProgress(progress);
    }

    private String getBossBarText(String skillName, String state, int secondsLeft) {
        String fallback;
        if ("duration".equals(state)) {
            fallback = "&b技能: %skill% (持续中: %seconds%秒) 触发方式: 蹲下+手持工具+右键";
        } else if ("cooldown".equals(state)) {
            fallback = "&c技能: %skill% (冷却中: %seconds%秒) 触发方式: 蹲下+手持工具+右键";
        } else {
            fallback = "&a技能: %skill% (就绪) 触发方式: 蹲下+手持工具+右键";
        }

        ConfigurationSection section = getSkillSection(skillName);
        String text = section == null ? fallback : section.getString("bossbar." + state, fallback);
        String trigger;
        if ("突进".equals(skillName)) {
            trigger = "手持长矛+右键";
        } else if ("腾空".equals(skillName)) {
            trigger = "手持重锤+右键";
        } else {
            trigger = "蹲下+手持工具+右键";
        }
        if ("突进".equals(skillName)) {
            text = text.replace("蹲下+手持长矛+右键", trigger)
                    .replace("蹲下+手持工具+右键", trigger);
        } else if ("腾空".equals(skillName)) {
            text = text.replace("蹲下+手持重锤+右键", trigger)
                    .replace("蹲下+手持工具+右键", trigger);
        } else if (text.contains("右键")) {
            text = text.replace("触发方式: 手持", "触发方式: 蹲下+手持");
        }
        return ChatColor.translateAlternateColorCodes('&', text)
                .replace("%skill%", skillName == null ? "" : skillName)
                .replace("%seconds%", String.valueOf(Math.max(0, secondsLeft)))
                .replace("%trigger%", trigger);
    }

    private double clampProgress(double progress) {
        return Math.max(0.0, Math.min(1.0, progress));
    }

    private void removeBossBar(UUID uuid) {
        BossBar oldBar = playerBossBars.remove(uuid);
        if (oldBar != null) {
            oldBar.removeAll();
        }
    }

    private void cancelBossBarTask(UUID uuid) {
        Integer taskId = bossBarTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private static class Cooldown {
        long lastUsed;
        int cooldownSeconds;
        int durationSeconds;

        int totalSeconds() {
            return Math.max(0, cooldownSeconds) + Math.max(0, durationSeconds);
        }
    }
}


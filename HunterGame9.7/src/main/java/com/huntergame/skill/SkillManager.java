package com.huntergame.skill;

import com.huntergame.HunterGame;
import com.huntergame.skill.skills.AdrenalineBurstSkill;
import com.huntergame.skill.skills.BlinkSkill;
import com.huntergame.skill.skills.DashSkill;
import com.huntergame.skill.skills.DemolitionExpertSkill;
import com.huntergame.skill.skills.DoubleJumpSkill;
import com.huntergame.skill.skills.ExplosiveCrossbowSkill;
import com.huntergame.skill.skills.FreezeSpellSkill;
import com.huntergame.skill.skills.HasteMinerSkill;
import com.huntergame.skill.skills.HunterSkill;
import com.huntergame.skill.skills.InvisibilitySkill;
import com.huntergame.skill.skills.KnockbackFieldSkill;
import com.huntergame.skill.skills.LavaWalkerSkill;
import com.huntergame.skill.skills.SkillActivationResult;
import com.huntergame.skill.skills.SkillContext;
import com.huntergame.skill.skills.TurtleSkill;
import com.huntergame.skill.skills.WallPhaseSkill;
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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
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

public class SkillManager implements Listener {
   public static final Map<String, Integer> SKILL_COOLDOWNS = new HashMap();
   public static final Map<String, Integer> SKILL_DURATIONS = new HashMap();
   public final Map<UUID, String> escapeeSkills = new HashMap();
   public final Map<UUID, String> hunterSkills = new HashMap();
   private static final String SKILLS_FILE = "skills.yml";
   private final HunterGame plugin;
   private final SkillContext context;
   private final Map<String, HunterSkill> skills = new LinkedHashMap();
   private final Map<UUID, Cooldown> cooldowns = new HashMap();
   private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap();
   private final Map<UUID, Integer> bossBarTasks = new ConcurrentHashMap();
   private final Map<UUID, Integer> activeDurationOverrides = new ConcurrentHashMap();
   private File skillsFile;
   private FileConfiguration skillsConfig;
   private long endGlobalCooldownUntil = 0L;

   public SkillManager(HunterGame plugin) {
      this.plugin = plugin;
      this.context = new SkillContext(plugin, this);
      this.registerSkills();
      this.reloadSkillConfig();
   }

   private void registerSkills() {
      this.registerSkill(new DoubleJumpSkill());
      this.registerSkill(new DashSkill());
      this.registerSkill(new DemolitionExpertSkill());
      this.registerSkill(new InvisibilitySkill());
      this.registerSkill(new HasteMinerSkill());
      this.registerSkill(new TurtleSkill());
      this.registerSkill(new ExplosiveCrossbowSkill());
      this.registerSkill(new WallPhaseSkill());
      this.registerSkill(new KnockbackFieldSkill());
      this.registerSkill(new AdrenalineBurstSkill());
      this.registerSkill(new LavaWalkerSkill());
      this.registerSkill(new BlinkSkill());
      this.registerSkill(new FreezeSpellSkill());
   }

   private void registerSkill(HunterSkill skill) {
      this.skills.put(skill.getName(), skill);
   }

   public void reloadSkillConfig() {
      this.ensureSkillsFile();
      this.skillsConfig = YamlConfiguration.loadConfiguration(this.skillsFile);

      try {
         InputStream defaultStream = this.plugin.getResource("skills.yml");

         try {
            if (defaultStream != null) {
               FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
               this.skillsConfig.setDefaults(defaults);
               this.skillsConfig.options().copyDefaults(true);
               this.skillsConfig.save(this.skillsFile);
            }
         } catch (Throwable var5) {
            if (defaultStream != null) {
               try {
                  defaultStream.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (defaultStream != null) {
            defaultStream.close();
         }
      } catch (IOException ex) {
         this.plugin.getLogger().warning("\u4fdd\u5b58 skills.yml \u9ed8\u8ba4\u8282\u70b9\u5931\u8d25: " + ex.getMessage());
      }

   }

   public void resetAllRuntimeData() {
      this.escapeeSkills.clear();
      this.hunterSkills.clear();
      this.cooldowns.clear();
      this.activeDurationOverrides.clear();
      this.endGlobalCooldownUntil = 0L;

      for(Integer taskId : this.bossBarTasks.values()) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

      this.bossBarTasks.clear();

      for(BossBar bossBar : this.playerBossBars.values()) {
         bossBar.removeAll();
      }

      this.playerBossBars.clear();
   }

   private void ensureSkillsFile() {
      if (!this.plugin.getDataFolder().exists()) {
         this.plugin.getDataFolder().mkdirs();
      }

      this.skillsFile = new File(this.plugin.getDataFolder(), "skills.yml");
      if (!this.skillsFile.exists()) {
         this.plugin.saveResource("skills.yml", false);
      }

   }

   private String normalizeSkillName(String skill) {
      if (skill == null) {
         return "";
      } else {
         String cleanName = ChatColor.stripColor(skill);
         return cleanName == null ? "" : cleanName.trim();
      }
   }

   private ConfigurationSection getSkillSection(String skill) {
      if (this.skillsConfig == null) {
         this.reloadSkillConfig();
      }

      String cleanName = this.normalizeSkillName(skill);
      return cleanName.isEmpty() ? null : this.skillsConfig.getConfigurationSection("skills." + cleanName);
   }

   public boolean isSkillConfigured(String skill) {
      String cleanName = this.normalizeSkillName(skill);
      return this.skills.containsKey(cleanName) && this.getSkillSection(cleanName) != null;
   }

   public boolean isSkillEnabled(String skill) {
      String cleanName = this.normalizeSkillName(skill);
      if (!this.skills.containsKey(cleanName)) {
         return false;
      } else {
         ConfigurationSection section = this.getSkillSection(cleanName);
         return section != null && section.getBoolean("enabled", true);
      }
   }

   public boolean canGrantSkill(String skill) {
      return this.isSkillConfigured(skill) && this.isSkillEnabled(skill);
   }

   public int getSkillCooldown(String skill) {
      String cleanName = this.normalizeSkillName(skill);
      ConfigurationSection section = this.getSkillSection(cleanName);
      int fallback = (Integer)SKILL_COOLDOWNS.getOrDefault(cleanName, 0);
      return section == null ? fallback : section.getInt("cooldown", fallback);
   }

   public int getSkillDuration(String skill) {
      String cleanName = this.normalizeSkillName(skill);
      ConfigurationSection section = this.getSkillSection(cleanName);
      int fallback = (Integer)SKILL_DURATIONS.getOrDefault(cleanName, 0);
      return section == null ? fallback : section.getInt("duration", fallback);
   }

   public int getSkillInt(String skill, String key, int fallback) {
      ConfigurationSection section = this.getSkillSection(skill);
      return section == null ? fallback : section.getInt("parameters." + key, fallback);
   }

   public double getSkillDouble(String skill, String key, double fallback) {
      ConfigurationSection section = this.getSkillSection(skill);
      return section == null ? fallback : section.getDouble("parameters." + key, fallback);
   }

   public List<Integer> getSkillIntegerList(String skill, String key, List<Integer> fallback) {
      ConfigurationSection section = this.getSkillSection(skill);
      if (section != null && section.isList("parameters." + key)) {
         List<Integer> values = section.getIntegerList("parameters." + key);
         return values.isEmpty() ? fallback : values;
      } else {
         return fallback;
      }
   }

   public boolean isWeaponOrTool(Material material) {
      String matName = material.name();
      return matName.endsWith("_SWORD") || matName.endsWith("_PICKAXE") || matName.endsWith("_AXE");
   }

   public boolean isEndGlobalCooldownActive() {
      return System.currentTimeMillis() < this.endGlobalCooldownUntil;
   }

   public String getSelectedSkill(Player player) {
      UUID uuid = player.getUniqueId();
      if (this.escapeeSkills.containsKey(uuid)) {
         return (String)this.escapeeSkills.get(uuid);
      } else {
         return this.hunterSkills.containsKey(uuid) ? (String)this.hunterSkills.get(uuid) : null;
      }
   }

   public void confirmSkillSelection(Player player, String skillName) {
      String cleanName = this.normalizeSkillName(skillName);
      UUID uuid = player.getUniqueId();
      HunterSkill skill = this.getEnabledSkill(cleanName);
      if (skill != null && this.canGrantSkill(cleanName)) {
         this.cooldowns.remove(uuid);
         this.activeDurationOverrides.remove(uuid);
         this.cancelBossBarTask(uuid);
         this.removeBossBar(uuid);
         if (this.plugin.isEscaper(uuid)) {
            this.hunterSkills.remove(uuid);
            this.escapeeSkills.put(uuid, cleanName);
         } else {
            this.escapeeSkills.remove(uuid);
            this.hunterSkills.put(uuid, cleanName);
         }

         skill.onSelected(player, this.context);
         this.showReadyBossBar(player, cleanName);
         player.sendMessage(this.plugin.getMessage("selected_skills", "&f\u4f60\u9009\u62e9\u4e86 \u804c\u4e1a\u6280\u80fd: &6%jobs%").replace("%jobs%", cleanName));
         player.closeInventory();
      } else {
         player.sendMessage(this.plugin.getMessage("skill_not_configured_or_disabled", "&c\u8be5\u804c\u4e1a\u6280\u80fd\u672a\u914d\u7f6e\u6216\u5df2\u7981\u7528: %skill%").replace("%skill%", cleanName));
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID uuid = player.getUniqueId();
      String selectedSkill = this.getSelectedSkill(player);
      this.removeBossBar(uuid);
      this.cancelBossBarTask(uuid);
      if (selectedSkill != null && !selectedSkill.isEmpty() && this.isSkillEnabled(selectedSkill)) {
         BossBar newBar = this.createBossBar(player, this.getBossBarText(selectedSkill, "ready", 0));
         Cooldown cooldown = (Cooldown)this.cooldowns.get(uuid);
         if (cooldown == null) {
            this.showReadyBossBar(player, selectedSkill);
         } else {
            long currentTime = System.currentTimeMillis();
            int duration = (Integer)this.activeDurationOverrides.getOrDefault(uuid, cooldown.durationSeconds);
            long durationEndTime = cooldown.lastUsed + (long)duration * 1000L;
            long cooldownEndTime = cooldown.lastUsed + (long)cooldown.totalSeconds() * 1000L;
            if (duration > 0 && currentTime < durationEndTime) {
               int remainingDuration = (int)Math.max(1L, (durationEndTime - currentTime) / 1000L);
               this.startDurationTracking(player, selectedSkill, remainingDuration, cooldown.cooldownSeconds);
            } else if (currentTime < cooldownEndTime) {
               int remainingCooldown = (int)Math.max(1L, (cooldownEndTime - currentTime) / 1000L);
               this.startCooldownTracking(player, selectedSkill, remainingCooldown);
            } else {
               newBar.setVisible(true);
               this.showReadyBossBar(player, selectedSkill);
            }

         }
      }
   }

   @EventHandler
   public void onRightClick(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Player player = event.getPlayer();
         String skillName = this.getSelectedSkill(player);
         HunterSkill skill = this.getEnabledSkill(skillName);
         if (skill != null && skill.canActivateByRightClick()) {
            if (skill.canActivateWithItem(player.getInventory().getItemInMainHand(), this.context)) {
               if (this.checkCooldown(player, skillName)) {
                  SkillActivationResult result = skill.activate(player, this.context);
                  if (result.isActivated()) {
                     this.startCooldown(player, skillName, result.getDurationOverrideSeconds());
                  }

               }
            }
         }
      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      this.dispatchSelectedSkill(event.getPlayer(), (skill) -> skill.onPlayerMove(event, this.context));
   }

   @EventHandler
   public void onPlayerJump(PlayerToggleFlightEvent event) {
      this.dispatchSelectedSkill(event.getPlayer(), (skill) -> skill.onPlayerToggleFlight(event, this.context));
   }

   @EventHandler
   public void onEnterEnd(PlayerChangedWorldEvent event) {
      Player player = event.getPlayer();
      if (player.getWorld().getName().equalsIgnoreCase("world_the_end")) {
         this.endGlobalCooldownUntil = System.currentTimeMillis() + 10000L;
      }

   }

   @EventHandler
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player) {
         Player damager = (Player)event.getDamager();
         this.capSpearDamage(event, damager);
         this.dispatchSelectedSkill(damager, (skill) -> skill.onEntityDamageByEntity(event, this.context));
      }

   }

   private void capSpearDamage(EntityDamageByEntityEvent event, Player damager) {
      if (!event.isCancelled() && this.isSpear(damager.getInventory().getItemInMainHand())) {
         double maxDamage = this.getSkillDouble("\u7a81\u8fdb", "max_spear_damage", (double)18.0F);
         if (maxDamage > (double)0.0F && event.getDamage() > maxDamage) {
            event.setDamage(maxDamage);
         }

      }
   }

   private boolean isSpear(ItemStack item) {
      return item != null && item.getType() != Material.AIR ? item.getType().name().contains("SPEAR") : false;
   }

   @EventHandler
   public void onEntityDamage(EntityDamageEvent event) {
      this.forEachSkill((skill) -> skill.onEntityDamage(event, this.context));
   }

   @EventHandler
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      this.forEachSkill((skill) -> skill.onPlayerRespawn(event, this.context));
   }

   @EventHandler
   public void onLeftClick(PlayerInteractEvent event) {
      if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
         this.dispatchSelectedSkill(event.getPlayer(), (skill) -> skill.onLeftClick(event, this.context));
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.forEachSkill((skill) -> skill.onPlayerQuit(event, this.context));
      this.removeBossBar(uuid);
      this.cancelBossBarTask(uuid);
      this.activeDurationOverrides.remove(uuid);
   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      this.forEachSkill((skill) -> skill.onPlayerDeath(event, this.context));
   }

   public boolean checkCooldown(Player player, String skill) {
      int lockRemainingSeconds = this.getFinalBattleSkillLockRemainingSeconds(player);
      if (lockRemainingSeconds > 0) {
         player.sendActionBar(this.plugin.getMessage("skill_start_lock_actionbar", "&c\u7ec8\u7ae0\u5f00\u5c40\u4fdd\u62a4\u4e2d\uff0c\u6280\u80fd\u5c06\u5728 %seconds% \u79d2\u540e\u53ef\u7528").replace("%seconds%", String.valueOf(lockRemainingSeconds)));
         return false;
      } else {
         Cooldown cooldown = (Cooldown)this.cooldowns.get(player.getUniqueId());
         if (cooldown == null) {
            return true;
         } else {
            long cooldownEndTime = cooldown.lastUsed + (long)cooldown.totalSeconds() * 1000L;
            if (System.currentTimeMillis() >= cooldownEndTime) {
               this.cooldowns.remove(player.getUniqueId());
               this.activeDurationOverrides.remove(player.getUniqueId());
               this.showReadyBossBar(player, skill);
               return true;
            } else {
               return false;
            }
         }
      }
   }

   private int getFinalBattleSkillLockRemainingSeconds(Player player) {
      if (this.plugin.isGameRunning() && this.plugin.isFinalBattleMode()) {
         UUID uuid = player.getUniqueId();
         if (!this.plugin.isHunter(uuid) && !this.plugin.isEscaper(uuid)) {
            return 0;
         } else {
            int lockSeconds = Math.max(0, this.plugin.getConfig().getInt("final_battle.skill_lock_seconds", 40));
            long elapsedSeconds = this.plugin.getElapsedSeconds();
            return (int)Math.max(0L, (long)lockSeconds - elapsedSeconds);
         }
      } else {
         return 0;
      }
   }

   public void startCooldown(Player player, String skill) {
      this.startCooldown(player, skill, (Integer)null);
   }

   private void startCooldown(Player player, String skill, Integer durationOverrideSeconds) {
      int cooldown = this.getSkillCooldown(skill);
      int duration = durationOverrideSeconds == null ? this.getSkillDuration(skill) : durationOverrideSeconds;
      UUID uuid = player.getUniqueId();
      Cooldown cd = new Cooldown();
      cd.lastUsed = System.currentTimeMillis();
      cd.cooldownSeconds = cooldown;
      cd.durationSeconds = duration;
      this.cooldowns.put(uuid, cd);
      if (duration > 0) {
         this.activeDurationOverrides.put(uuid, duration);
         this.startDurationTracking(player, skill, duration, cooldown);
      } else {
         this.activeDurationOverrides.remove(uuid);
         this.startCooldownTracking(player, skill, cooldown);
      }

   }

   private void startDurationTracking(Player player, String skill, int duration, int cooldown) {
      UUID uuid = player.getUniqueId();
      long endTime = System.currentTimeMillis() + (long)duration * 1000L;
      long startTime = System.currentTimeMillis();
      this.cancelBossBarTask(uuid);
      AtomicInteger taskId = new AtomicInteger();
      taskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
         if (player.isOnline() && skill.equals(this.getSelectedSkill(player))) {
            long currentTime = System.currentTimeMillis();
            if (currentTime >= endTime) {
               Bukkit.getScheduler().cancelTask(taskId.get());
               this.bossBarTasks.remove(uuid);
               this.activeDurationOverrides.remove(uuid);
               this.startCooldownTracking(player, skill, cooldown);
            } else {
               double progress = (double)1.0F - (double)(currentTime - startTime) / (double)((long)duration * 1000L);
               int secondsLeft = (int)Math.ceil((double)(endTime - currentTime) / (double)1000.0F);
               this.updateDurationBossBar(player, skill, this.clampProgress(progress), secondsLeft);
            }
         } else {
            Bukkit.getScheduler().cancelTask(taskId.get());
            this.bossBarTasks.remove(uuid);
         }
      }, 0L, 20L));
      this.bossBarTasks.put(uuid, taskId.get());
   }

   private void startCooldownTracking(Player player, String skill, int cooldown) {
      UUID uuid = player.getUniqueId();
      if (cooldown <= 0) {
         this.showReadyBossBar(player, skill);
      } else {
         long endTime = System.currentTimeMillis() + (long)cooldown * 1000L;
         long startTime = System.currentTimeMillis();
         this.cancelBossBarTask(uuid);
         AtomicInteger taskId = new AtomicInteger();
         taskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
            if (player.isOnline() && skill.equals(this.getSelectedSkill(player))) {
               long currentTime = System.currentTimeMillis();
               if (currentTime >= endTime) {
                  Bukkit.getScheduler().cancelTask(taskId.get());
                  this.bossBarTasks.remove(uuid);
                  this.showReadyBossBar(player, skill);
               } else {
                  double progress = (double)(currentTime - startTime) / (double)((long)cooldown * 1000L);
                  int secondsLeft = (int)Math.ceil((double)(endTime - currentTime) / (double)1000.0F);
                  this.updateCooldownBossBar(player, skill, this.clampProgress(progress), secondsLeft);
               }
            } else {
               Bukkit.getScheduler().cancelTask(taskId.get());
               this.bossBarTasks.remove(uuid);
            }
         }, 0L, 20L));
         this.bossBarTasks.put(uuid, taskId.get());
      }
   }

   private HunterSkill getEnabledSkill(String skillName) {
      String cleanName = this.normalizeSkillName(skillName);
      return !this.isSkillEnabled(cleanName) ? null : (HunterSkill)this.skills.get(cleanName);
   }

   private void dispatchSelectedSkill(Player player, Consumer<HunterSkill> action) {
      HunterSkill skill = this.getEnabledSkill(this.getSelectedSkill(player));
      if (skill != null) {
         action.accept(skill);
      }

   }

   private void forEachSkill(Consumer<HunterSkill> action) {
      for(HunterSkill skill : this.skills.values()) {
         action.accept(skill);
      }

   }

   private BossBar getOrCreateBossBar(Player player) {
      UUID uuid = player.getUniqueId();
      BossBar bossBar = (BossBar)this.playerBossBars.get(uuid);
      if (bossBar == null) {
         bossBar = this.createBossBar(player, this.plugin.getMessage("skill_bossbar_ready_fallback", "&a\u6280\u80fd\u5c31\u7eea"));
         bossBar.setVisible(false);
      }

      return bossBar;
   }

   private BossBar createBossBar(Player player, String title) {
      BossBar bossBar = Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SOLID, new BarFlag[0]);
      bossBar.addPlayer(player);
      bossBar.setProgress((double)1.0F);
      this.playerBossBars.put(player.getUniqueId(), bossBar);
      return bossBar;
   }

   private void showReadyBossBar(Player player, String skillName) {
      BossBar bossBar = this.getOrCreateBossBar(player);
      bossBar.setVisible(skillName != null && !skillName.isEmpty());
      bossBar.setColor(BarColor.GREEN);
      bossBar.setTitle(this.getBossBarText(skillName, "ready", 0));
      bossBar.setProgress((double)1.0F);
   }

   private void updateDurationBossBar(Player player, String skillName, double progress, int secondsLeft) {
      BossBar bossBar = this.getOrCreateBossBar(player);
      bossBar.setVisible(true);
      bossBar.setColor(BarColor.BLUE);
      bossBar.setTitle(this.getBossBarText(skillName, "duration", secondsLeft));
      bossBar.setProgress(progress);
   }

   private void updateCooldownBossBar(Player player, String skillName, double progress, int secondsLeft) {
      BossBar bossBar = this.getOrCreateBossBar(player);
      bossBar.setVisible(true);
      bossBar.setColor(BarColor.RED);
      bossBar.setTitle(this.getBossBarText(skillName, "cooldown", secondsLeft));
      bossBar.setProgress(progress);
   }

   private String getBossBarText(String skillName, String state, int secondsLeft) {
      String fallback;
      if ("duration".equals(state)) {
         fallback = "&b\u6280\u80fd: %skill% (\u6301\u7eed\u4e2d: %seconds%\u79d2) \u89e6\u53d1\u65b9\u5f0f: \u624b\u6301\u5de5\u5177+\u53f3\u952e";
      } else if ("cooldown".equals(state)) {
         fallback = "&c\u6280\u80fd: %skill% (\u51b7\u5374\u4e2d: %seconds%\u79d2) \u89e6\u53d1\u65b9\u5f0f: \u624b\u6301\u5de5\u5177+\u53f3\u952e";
      } else {
         fallback = "&a\u6280\u80fd: %skill% (\u5c31\u7eea) \u89e6\u53d1\u65b9\u5f0f: \u624b\u6301\u5de5\u5177+\u53f3\u952e";
      }

      ConfigurationSection section = this.getSkillSection(skillName);
      String text = section == null ? fallback : section.getString("bossbar." + state, fallback);
      return ChatColor.translateAlternateColorCodes('&', text).replace("%skill%", skillName == null ? "" : skillName).replace("%seconds%", String.valueOf(Math.max(0, secondsLeft))).replace("%trigger%", "\u624b\u6301\u5de5\u5177+\u53f3\u952e");
   }

   private double clampProgress(double progress) {
      return Math.max((double)0.0F, Math.min((double)1.0F, progress));
   }

   private void removeBossBar(UUID uuid) {
      BossBar oldBar = (BossBar)this.playerBossBars.remove(uuid);
      if (oldBar != null) {
         oldBar.removeAll();
      }

   }

   private void cancelBossBarTask(UUID uuid) {
      Integer taskId = (Integer)this.bossBarTasks.remove(uuid);
      if (taskId != null) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

   }

   static {
      SKILL_COOLDOWNS.put("\u4e8c\u6bb5\u8df3", 22);
      SKILL_COOLDOWNS.put("\u7a81\u8fdb", 30);
      SKILL_COOLDOWNS.put("\u7206\u7834\u4e13\u5bb6", 30);
      SKILL_COOLDOWNS.put("\u9690\u8eab", 90);
      SKILL_COOLDOWNS.put("\u76fe\u6784\u673a", 40);
      SKILL_COOLDOWNS.put("\u795e\u9f9f", 170);
      SKILL_COOLDOWNS.put("\u7206\u70b8\u5f29", 200);
      SKILL_COOLDOWNS.put("\u7a7f\u5899", 265);
      SKILL_COOLDOWNS.put("\u51fb\u9000\u9886\u57df", 45);
      SKILL_COOLDOWNS.put("\u80be\u4e0a\u817a\u7206\u53d1", 110);
      SKILL_COOLDOWNS.put("\u7194\u5ca9\u884c\u8005", 30);
      SKILL_COOLDOWNS.put("\u95ea\u73b0", 120);
      SKILL_COOLDOWNS.put("\u5b9a\u8eab\u672f", 125);
      SKILL_DURATIONS.put("\u9690\u8eab", 30);
      SKILL_DURATIONS.put("\u76fe\u6784\u673a", 30);
      SKILL_DURATIONS.put("\u795e\u9f9f", 20);
      SKILL_DURATIONS.put("\u80be\u4e0a\u817a\u7206\u53d1", 15);
      SKILL_DURATIONS.put("\u5b9a\u8eab\u672f", 3);
      SKILL_DURATIONS.put("\u7a7f\u5899", 4);
   }

   private static class Cooldown {
      long lastUsed;
      int cooldownSeconds;
      int durationSeconds;

      int totalSeconds() {
         return Math.max(0, this.cooldownSeconds) + Math.max(0, this.durationSeconds);
      }
   }
}

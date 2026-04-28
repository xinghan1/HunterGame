package com.huntergame.skill;

import com.huntergame.Gui.SkillSelectionGUI;
import com.huntergame.HunterGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SkillManager implements Listener {
    public final Map<UUID, String> escapeeSkills = new HashMap<>();
    public final Map<UUID, String> hunterSkills = new HashMap<>();
    private final Map<UUID, Cooldown> cooldowns = new HashMap<>();
    private final HunterGame plugin;
    private final Map<UUID, ItemStack[]> armorStorage = new HashMap<>();
    private final Map<UUID, ItemStack> mainHandStorage = new HashMap<>();
    private final Map<UUID, Integer> soulTasks = new HashMap<>();
    private final Map<UUID, Integer> speedTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> meitoActive = new ConcurrentHashMap<>();
    private long endGlobalCooldownUntil = 0L;
    private final Map<UUID, Boolean> remoteImmune = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> immuneParticlesTask = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> offHandStorage = new HashMap<>();

    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bossBarTasks = new ConcurrentHashMap<>();

    // 是否启用基岩版 GUI
    private boolean useBedrockGUI = false;

    public SkillManager(HunterGame plugin) {
        this.plugin = plugin;
        this.useBedrockGUI = Bukkit.getPluginManager().getPlugin("BaseAPI") != null;
    }

    public static final Map<String, Integer> SKILL_COOLDOWNS = new HashMap<String, Integer>() {{
        put("二段跳", 22);
        put("爆破专家", 30);
        put("隐身", 90);
        put("盾构机", 40);
        put("神龟", 170);
        put("爆炸弩", 200);
        put("穿墙", 265);
        put("击退领域", 45);
        put("肾上腺爆发", 110);
        put("熔岩行者", 30);
        put("闪现", 120);
        put("定身术", 125);
    }};

    public static final Map<String, Integer> SKILL_DURATIONS = new HashMap<String, Integer>() {{
        put("隐身", 30);
        put("盾构机", 30);
        put("神龟", 20);
        put("肾上腺爆发", 15);
        put("定身术", 3);
        put("穿墙", 4);
    }};

    // 更新获取技能冷却时间的方法
    private int getSkillCooldown(String skill) {
        int baseCooldown = SKILL_COOLDOWNS.getOrDefault(skill, 0);

        return baseCooldown;
    }

    // 更新获取技能持续时间的方法
    private int getSkillDuration(String skill) {
        return SKILL_DURATIONS.getOrDefault(skill, 0);
    }

    private void startCooldown(Player player, String skill) {
        Cooldown cd = new Cooldown();
        cd.lastUsed = System.currentTimeMillis();
        cd.duration = getSkillCooldown(skill); // 使用全局配置

        cooldowns.put(player.getUniqueId(), cd);

        // 启动Boss血条更新任务前确保可见性
        updateBossBarVisibility(player);

        int duration = getSkillDuration(skill);
        int cooldown = cd.duration;

        if (duration > 0) {
            startDurationTracking(player, skill, duration, cooldown);
        } else {
            startCooldownTracking(player, skill, cooldown);
        }
    }


    // 初始化玩家的Boss血条
    private BossBar getOrCreateBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerBossBars.containsKey(uuid)) {
            BossBar bossBar = Bukkit.createBossBar(
                    "§a技能就绪",
                    BarColor.GREEN,
                    BarStyle.SOLID
            );
            bossBar.addPlayer(player);
            // 初始状态设置为不可见，直到选择技能
            bossBar.setVisible(false);
            bossBar.setProgress(1.0);
            playerBossBars.put(uuid, bossBar);
        }
        return playerBossBars.get(uuid);
    }

    // 根据技能选择状态更新Boss血条可见性
    private void updateBossBarVisibility(Player player) {
        String selectedSkill = getSelectedSkill(player);
        BossBar bossBar = getOrCreateBossBar(player);

        // 如果有选中的技能则显示血条，否则隐藏
        boolean shouldShow = selectedSkill != null && !selectedSkill.isEmpty();
        bossBar.setVisible(shouldShow);
    }

    // 更新Boss血条显示
    private void updateBossBar(Player player, String skillName, double progress, boolean isDuration) {
        BossBar bossBar = getOrCreateBossBar(player);
        String title;

        if (isDuration) {
            // 持续时间显示 - 从100%减少到0%
            int secondsLeft = (int) Math.ceil(progress * getSkillDuration(skillName));
            title = "§b技能: " + skillName + " (持续中: " + secondsLeft + "秒) 触发方式: 手持工具+蹲下右键";
            bossBar.setColor(BarColor.BLUE);
        } else {
            // 冷却时间显示 - 从0%增加到100%
            if (progress >= 1.0) {
                title = "§a技能: " + skillName + " (就绪) 触发方式: 手持工具+蹲下右键";
                bossBar.setColor(BarColor.GREEN);
            } else {
                int secondsLeft = (int) Math.ceil((1 - progress) * (getSkillCooldown(skillName) - getSkillDuration(skillName)));
                title = "§c技能: " + skillName + " (冷却中: " + secondsLeft + "秒) 触发方式: 手持工具+蹲下右键";
                bossBar.setColor(BarColor.RED);
            }
        }

        bossBar.setTitle(title);
        bossBar.setProgress(progress);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String selectedSkill = getSelectedSkill(player);

        if (playerBossBars.containsKey(playerId)) {
            BossBar oldBar = playerBossBars.remove(playerId);
            oldBar.removeAll(); // 移除旧Player绑定
        }

        if (bossBarTasks.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(bossBarTasks.get(playerId));
            bossBarTasks.remove(playerId);
        }

        // 若玩家已选择技能，重建血条并恢复状态
        if (selectedSkill != null && !selectedSkill.isEmpty()) {
            // 重建血条实例，绑定新Player
            BossBar newBar = Bukkit.createBossBar(
                    "§a技能: " + selectedSkill + " (就绪) 触发方式: 手持工具+蹲下右键",
                    BarColor.GREEN,
                    BarStyle.SOLID
            );
            newBar.addPlayer(player);
            newBar.setProgress(1.0);
            playerBossBars.put(playerId, newBar);

            // 4. 检查是否有未结束的冷却/持续时间，重启更新任务
            Cooldown cooldown = cooldowns.get(playerId);
            if (cooldown != null) {
                long currentTime = System.currentTimeMillis();
                long cooldownEndTime = cooldown.lastUsed + (cooldown.duration * 1000L);
                int duration = getSkillDuration(selectedSkill);
                long durationEndTime = cooldown.lastUsed + (duration * 1000L);

                // 情况1：技能仍在持续中（如隐身、盾构机）
                if (currentTime < durationEndTime) {
                    int remainingDuration = (int) ((durationEndTime - currentTime) / 1000);
                    startDurationTracking(player, selectedSkill, remainingDuration, cooldown.duration);
                }
                // 情况2：技能处于冷却中（持续时间结束后）
                else if (currentTime < cooldownEndTime) {
                    int remainingCooldown = (int) ((cooldownEndTime - currentTime) / 1000);
                    startCooldownTracking(player, selectedSkill, remainingCooldown);
                }
                // 情况3：冷却已结束，显示就绪状态
                else {
                    newBar.setTitle("§a技能: " + selectedSkill + " (就绪) 触发方式: 手持工具+蹲下右键");
                    newBar.setColor(BarColor.GREEN);
                    newBar.setProgress(1.0);
                }
            } else {
                // 无冷却记录，默认显示就绪状态
                newBar.setTitle("§a技能: " + selectedSkill + " (就绪) 触发方式: 手持工具+蹲下右键");
                newBar.setColor(BarColor.GREEN);
                newBar.setProgress(1.0);
            }

            // 确保血条可见
            newBar.setVisible(true);
        }
    }


    // 在类中添加Lore映射
    public static final Map<String, List<String>> SKILL_LORES = new HashMap<String, List<String>>() {{
        put("二段跳", Arrays.asList(
                "§7双击空格可二次跳跃",
                "§7以及摔落免伤",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("二段跳") + "秒"
        ));
        put("爆破专家", Arrays.asList(
                "§7生成3个即将爆炸的TNT",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("爆破专家") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("隐身", Arrays.asList(
                "§7隐身30秒并隐藏装备",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("隐身") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("盾构机", Arrays.asList(
                "§7大幅提升挖掘速度",
                "§7持续时间: §730秒",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("盾构机") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("神龟", Arrays.asList(
                "§7获得20秒的抗性IV效果",
                "§7同时获得20秒减速效果",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("神龟") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("爆炸弩", Arrays.asList(
                "§7左键获取4个爆炸火火箭",
                "§7发射爆炸火箭伤害为真实伤害",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("爆炸弩") + "秒",
                "§1",
                "§c手持弩左键触发"
        ));
        put("穿墙", Arrays.asList(
                "§7获得4秒旁观效果",
                "§75秒后变为生存模式",
                "§7请注意你周围环境是否安全",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("穿墙") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("击退领域", Arrays.asList(
                "§7击退周围10格内的所有玩家",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("击退领域") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("肾上腺爆发", Arrays.asList(
                "§7获得15秒超高速移动能力",
                "§7结束后获得§c3秒减速II",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("肾上腺爆发") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("熔岩行者", Arrays.asList(
                "§7在脚下生成流动岩浆",
                "§7攻击玩家有20%概率触发燃烧效果",
                "§7获得永久抗火效果(需要手动触发一次)",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("熔岩行者") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("闪现", Arrays.asList(
                "§7传送到准心瞄准的位置",
                "§7可穿过墙壁，最大距离40格",
                "§7并且给予10秒无敌和迅捷效果以及免疫远程武器攻击",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("闪现") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));
        put("定身术", Arrays.asList(
                "§7使附近逃生者90秒发光效果",
                "§7并且将40格范围逃生者定住3秒钟",
                "§7但3秒钟内目标处于无敌状态",
                "§7获得永久急迫II效果",
                "§e冷却时间: §a" + SKILL_COOLDOWNS.get("定身术") + "秒",
                "§1",
                "§c手持工具+右键+蹲下触发"
        ));

    }};

    public void openSkillSelection(Player player) {
        SkillSelectionGUI.openSkillSelection(plugin, player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        // 只处理职业选择GUI
        if (!title.equals("选择你的职业")) return;

        // 检查是否已选择技能
        if (!hasSelectedSkill(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(plugin.getMessage("skill_selection", "&c你必须选择一个职业技能！"));
                openSkillSelection(player);
            }, 1L); // 延迟1tick防止事件冲突
        }
    }

    // 检查技能选择状态的方法
    private boolean hasSelectedSkill(Player player) {
        UUID uuid = player.getUniqueId();
        return escapeeSkills.containsKey(uuid) || hunterSkills.containsKey(uuid);
    }



    // Java GUI 点击处理
    @EventHandler
    public void onSkillSelect(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("选择你的职业")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            Player player = (Player) event.getWhoClicked();
            String skillName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            confirmSkillSelection(player, skillName);
        }
    }

    /**
     * 确认选择技能
     */
    public void confirmSkillSelection(Player player, String skillName) {
        String cleanName = ChatColor.stripColor(skillName);
        UUID uuid = player.getUniqueId();

        if (plugin.isEscaper(uuid)) {
            hunterSkills.remove(uuid);
            escapeeSkills.put(uuid, cleanName);
        } else {
            escapeeSkills.remove(uuid);
            hunterSkills.put(uuid, cleanName);
            if ("爆炸弩".equals(cleanName)) {
                plugin.getExplosiveCrossbowListener().giveCrossbowPackage(player);
            }
        }

        updateBossBarVisibility(player);
        BossBar bossBar = getOrCreateBossBar(player);
        bossBar.setProgress(1.0);
        bossBar.setTitle("§a技能: " + cleanName + " (就绪)");
        bossBar.setColor(BarColor.GREEN);

        player.sendMessage(plugin.getMessage("selected_skills", "&f你选择了 职业技能: &6%jobs%")
                .replace("%jobs%", cleanName)
        );

        player.closeInventory();
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if(event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        // 检查玩家是否正在蹲下 (isSneaking())
        if (!player.isSneaking()) return; // 如果没有蹲下，直接返回

        String skill = getSelectedSkill(player);

        if(skill == null) return;
        // 检查是否选择了二段跳技能
        if (isDoubleJumpSkillSelected(player)) return;

        // 提前过滤不需要右键触发的技能
        if(skill == null || "爆炸弩".equals(skill)) return;

        if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
            if(checkCooldown(player, skill)) {
                if(activateSkill(player, skill)) { // 只有技能成功激活才进入冷却
                    startCooldown(player, skill);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 仅检查垂直移动变化
        if (event.getFrom().getY() == event.getTo().getY()) return;

        Player player = event.getPlayer();
        // 检查是否选择了二段跳技能
        if (!isDoubleJumpSkillSelected(player)) return;

        // 排除创造模式和旁观者模式
        if (player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR) return;

        // 仅在玩家未接触地面时设置允许飞行
        if (player.isOnGround()) {
            player.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onPlayerJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // 排除创造模式和旁观者模式
        if (player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR) return;

        player.setAllowFlight(false); // 关闭允许飞行

        // 检查是否选择了二段跳技能
        if (!isDoubleJumpSkillSelected(player)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                player.setAllowFlight(false);
            }
        }.runTaskLater(plugin, 20); // 1 tick = 50ms，转换为tick数

        // 检查冷却时间
        if (!checkCooldown(player, "二段跳")) return;

        // 执行二段跳逻辑
        performDoubleJump(player);
        startCooldown(player, "二段跳");

    }

    private boolean isDoubleJumpSkillSelected(Player player) {
        String skill = getSelectedSkill(player);
        return skill != null && skill.equals("二段跳");
    }
    private boolean isbaozhanu(Player player) {
        String skill = getSelectedSkill(player);
        return skill != null && skill.equals("爆炸弩");
    }


    private void performDoubleJump(Player player) {
        // 增强跳跃效果
        double verticalPower = 1.0;   // 垂直速度基数
        double horizontalPower = 1.5; // 水平速度基数

        // 根据玩家身份调整跳跃力度
        if (plugin.isEscaper(player.getUniqueId())) {
            verticalPower = 1.3;  // 逃生者跳得更高
            horizontalPower = 1.8; // 逃生者跳得更远
        }

        player.setVelocity(player.getLocation().getDirection()
                .multiply(horizontalPower)    // 水平速度
                .setY(verticalPower));        // 垂直速度

        // 增强粒子效果
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                player.getLocation(),
                30, 0.5, 0.5, 0.5, 0.15
        );

        // 添加声音效果
        player.playSound(player.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
        // 二段跳完成后关闭飞行
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setAllowFlight(false);
            }
        }.runTaskLater(plugin, 40); // 1 tick = 50ms，转换为tick数
    }


    // 新增工具检查方法
    private boolean isWeaponOrTool(Material material) {
        String matName = material.name();
        return matName.endsWith("_SWORD") ||
                matName.endsWith("_PICKAXE") ||
                matName.endsWith("_AXE");
    }



    // 在 SkillManager 类中添加这个方法
    public String getSelectedSkill(Player player) {
        UUID uuid = player.getUniqueId();

        // 优先检查逃生者技能
        if (escapeeSkills.containsKey(uuid)) {
            return escapeeSkills.get(uuid);
        }

        // 再检查猎人技能
        if (hunterSkills.containsKey(uuid)) {
            return hunterSkills.get(uuid);
        }

        return null; // 没有选择技能的情况
    }

    @EventHandler
    public void onEnterEnd(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (!player.getWorld().getName().equalsIgnoreCase("world_the_end")) return;

        // 设置全体技能冷却为现在+10秒（单位：毫秒）
        endGlobalCooldownUntil = System.currentTimeMillis() + 10_000;
    }


    public final Set<UUID> soulMode = new HashSet<>();

    private boolean activateSkill(Player player, String skill) {
        UUID playerId = player.getUniqueId();

        switch(ChatColor.stripColor(skill)) {
            case "二段跳":
                // 动态标题
                player.sendTitle(
                        "§b⚡ §l二段跳 §r⚡",
                        "",
                        10,  // 淡入时间（ticks）
                        30,  // 停留时间
                        10   // 淡出时间
                );
                player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
                return true;
            case "爆破专家":
                // 如果现在还在全局技能冷却期，则禁止使用技能
                if (System.currentTimeMillis() < endGlobalCooldownUntil) {
                    player.sendMessage(plugin.getMessage("skill_disabled", "&c技能暂时被禁用!"));
                    return false;
                }
                // 动态标题
                player.sendTitle(
                        "§b⚡ §l爆破专家 §r⚡",
                        "§7",
                        10,  // 淡入时间（ticks）
                        30,  // 停留时间
                        10   // 淡出时间
                );
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    // 获取玩家位置和方向
                    Location spawnLoc = player.getLocation().clone();
                    Vector direction = spawnLoc.getDirection();

                    // 向前移动1格（保留小数防止截断）
                    spawnLoc.add(direction.multiply(1.2)); // 1.2倍距离防止脚下生成

                    // 确保生成在安全高度（玩家腰部位置）
                    spawnLoc.setY(spawnLoc.getY() + 0.8);

                    // 生成点燃的TNT
                    TNTPrimed tnt = player.getWorld().spawn(spawnLoc, TNTPrimed.class);
                    tnt.setFuseTicks(60); // 设置3秒后爆炸
                    TNTPrimed tnt2 = player.getWorld().spawn(spawnLoc, TNTPrimed.class);
                    tnt2.setFuseTicks(50); // 设置3秒后爆炸
                    tnt2.setFuseTicks(65); // 设置3秒后爆炸
                    player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
                    return true;
                }
            case "隐身":
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    // 动态标题
                    player.sendTitle(
                            "§b\uD83D\uDC7B §l隐身 §r\uD83D\uDC7B",
                            "§7持续时间: §a" + SKILL_DURATIONS.get("隐身") + "秒",
                            10,  // 淡入时间（ticks）
                            60,  // 停留时间
                            10   // 淡出时间
                    );

                    plugin.removeEscaper(playerId);

                    // 存储原始装备、物品和副手物品
                    UUID uuid = player.getUniqueId();
                    armorStorage.put(uuid, player.getInventory().getArmorContents());
                    offHandStorage.put(uuid, player.getInventory().getItemInOffHand());

                    // 清空可见装备和副手物品
                    player.getInventory().setArmorContents(new ItemStack[4]);
                    player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

                    // 检测玩家是否处于定身状态
                    if (plugin.getFreezeSkill().isPlayerFrozen(playerId)) {
                        plugin.getFreezeSkill().unfreezeNow(playerId);
                    }
                    player.removePotionEffect(PotionEffectType.GLOWING); // 清除发光效果

                    // 添加药水效果
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.INVISIBILITY, SKILL_DURATIONS.get("隐身") * 20, 0, true, false));
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED, SKILL_DURATIONS.get("隐身") * 20, 0, true, false));

                    // 30秒后恢复
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        restorePlayerVisibility(player);
                    }, 600);

                    // 开始持续时间跟踪（替代原有的startCooldown）
                    int duration = getSkillDuration(skill);
                    int cooldownTime = getSkillCooldown(skill);
                    startDurationTracking(player, skill, duration, cooldownTime);
                    startCooldown(player, "隐身");

                    player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
                    return true;
                }
            case "盾构机":
                // 动态标题
                player.sendTitle(
                        "§b⚡ §l盾构机 §r⚡",
                        "§7持续时间: §a" + SKILL_DURATIONS.get("盾构机") + "秒",
                        10,  // 淡入时间（ticks）
                        40,  // 停留时间
                        10   // 淡出时间
                );

                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.HASTE, SKILL_DURATIONS.get("盾构机") * 20, 254));

                    // 开始持续时间跟踪（替代原有的startCooldown）
                    int duration = getSkillDuration(skill);
                    int cooldownTime = getSkillCooldown(skill);
                    startDurationTracking(player, skill, duration, cooldownTime);
                    startCooldown(player, "盾构机");

                    player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
                    return true;
                }
            case "神龟":
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    // 动态标题
                    player.sendTitle(
                            "§b\uD83D\uDEE1 §l神龟 §r\uD83D\uDEE1",
                            "§7持续时间: §a" + SKILL_DURATIONS.get("神龟") + "秒",
                            10,  // 淡入时间（ticks）
                            60,  // 停留时间
                            10   // 淡出时间
                    );
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.RESISTANCE, SKILL_DURATIONS.get("神龟") * 20, 3));
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, SKILL_DURATIONS.get("神龟") * 20, 0));
                    return true;
                }
            case "爆炸弩":
                // 确保只有选择该技能时才能激活
                if (!"爆炸弩".equals(getSelectedSkill(player))) return false;
                plugin.getExplosiveCrossbowListener().giveCrossbowPackage(player);
                return true;
            case "穿墙":
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    // 切换旁观模式
                    player.setGameMode(GameMode.SPECTATOR);
                    soulMode.add(player.getUniqueId()); // 添加标记
                    player.sendTitle("§e穿墙", "§7" + SKILL_DURATIONS.get("穿墙") + "秒后回归", 10, 40, 10);

                    // 4秒后恢复生存模式
                    int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.sendTitle("§a回归现世", "", 10, 20, 10);
                        soulTasks.remove(player.getUniqueId());
                        soulMode.remove(player.getUniqueId()); // 移除标记
                    }, SKILL_DURATIONS.get("穿墙") * 20).getTaskId(); // 持续时间

                    soulTasks.put(player.getUniqueId(), taskId);
                }
                return true;
            case "击退领域":
                // 如果现在还在全局技能冷却期，则禁止使用技能
                if (System.currentTimeMillis() < endGlobalCooldownUntil) {
                    player.sendMessage(plugin.getMessage("skill_disabled", "&c技能暂时被禁用!"));
                    return false;
                }
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    // 动态标题
                    player.sendTitle(
                            "§b\uD83D\uDEE1 §l击退领域 §r\uD83D\uDEE1",
                            "",
                            10,  // 淡入时间（ticks）
                            60,  // 停留时间
                            10   // 淡出时间
                    );
                    // 获取周围玩家
                    Collection<Player> nearbyPlayers = player.getWorld().getNearbyPlayers(
                            player.getLocation(), 10, 10, 10,
                            p -> p != player && p.getGameMode() == GameMode.SURVIVAL
                    );

                    // 对每个玩家施加击退
                    for (Player target : nearbyPlayers) {
                        Vector direction = target.getLocation().toVector()
                                .subtract(player.getLocation().toVector())
                                .normalize()
                                .multiply(9.0) // 击退力度
                                .setY(1.5);     // 垂直击退

                        target.setVelocity(direction);
                    }

                    // 特效和音效
                    player.getWorld().playSound(player.getLocation(),
                            Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.8f);
                    player.spawnParticle(Particle.EXPLOSION,
                            player.getLocation(), 3);

                    player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
                    return true;
                }
            case "肾上腺爆发":
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    // 清除可能存在的旧任务
                    if (speedTasks.containsKey(player.getUniqueId())) {
                        Bukkit.getScheduler().cancelTask(speedTasks.get(player.getUniqueId()));
                    }

                    int time = SKILL_DURATIONS.get("肾上腺爆发") * 20;
                    // 根据玩家身份调整跳跃力度
                    if (plugin.isEscaper(player.getUniqueId())) {
                        time = (SKILL_DURATIONS.get("肾上腺爆发") + 5) * 20;
                    }

                    // 给予速度效果（等级5）
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED,
                            time,  // 20秒（20ticks/秒 × 10秒）
                            4,    // 等级10（0-based索引）
                            true, // 显示粒子
                            true  // 显示图标
                    ));

                    // 初始特效
                    player.getWorld().spawnParticle(
                            Particle.FIREWORK,
                            player.getLocation().add(0, 1, 0),
                            30, 0.5, 0.5, 0.5, 0.2
                    );
                    player.playSound(player.getLocation(),
                            Sound.ENTITY_ILLUSIONER_CAST_SPELL,
                            1.0f, 0.5f);

                    // 动态标题
                    player.sendTitle(
                            "§b⚡ §l肾上腺爆发§r⚡",
                            "§7持续时间: §a" + SKILL_DURATIONS.get("肾上腺爆发") + "秒",
                            10,  // 淡入时间（ticks）
                            60,  // 停留时间
                            10   // 淡出时间
                    );

                    // 持续粒子效果 - 确保taskId在所有路径中初始化
                    int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                        // 蓝色轨迹粒子
                        player.getWorld().spawnParticle(
                                Particle.DUST,
                                player.getLocation().add(0, 0.2, 0),
                                5,
                                0.1, 0.1, 0.1,
                                new Particle.DustOptions(Color.fromRGB(0, 0, 255), 1.5f)
                        );

                        // 电火花粒子
                        player.getWorld().spawnParticle(
                                Particle.ELECTRIC_SPARK,
                                player.getLocation().add(0, 1, 0),
                                3,
                                0.3, 0.5, 0.3, 0.1
                        );
                    }, 0L, 2L); // 立即开始，每2ticks执行

                    speedTasks.put(player.getUniqueId(), taskId);

                    // 15秒后清除效果
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // 移除粒子任务
                        if (speedTasks.containsKey(player.getUniqueId())) {
                            Bukkit.getScheduler().cancelTask(taskId);
                            speedTasks.remove(player.getUniqueId());
                        }

                        // 移除速度效果
                        player.removePotionEffect(PotionEffectType.SPEED);

                        // 添加减速副作用
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS,
                                60,  // 5秒
                                1,   // 等级II
                                true, true
                        ));

                        player.playSound(player.getLocation(),
                                Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,
                                1.0f, 1.0f);



                    }, time); // 15秒后执行

                    // 开始持续时间跟踪（替代原有的startCooldown）
                    int duration = getSkillDuration(skill);
                    int cooldownTime = getSkillCooldown(skill);
                    startDurationTracking(player, skill, duration, cooldownTime);
                    startCooldown(player, "肾上腺爆发"); // 开始冷却

                    player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
                    return true;
                }
                return false; // 确保在条件不满足时返回
            case "熔岩行者":
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    // 获取玩家朝向和位置
                    Location playerLoc = player.getLocation();
                    float yaw = playerLoc.getYaw(); // 玩家朝向角度

                    // 计算前方1格的坐标（腰部位置 = 玩家Y坐标 + 1.0）
                    double x = playerLoc.getX() - Math.sin(Math.toRadians(yaw)) * 1.0;
                    double z = playerLoc.getZ() + Math.cos(Math.toRadians(yaw)) * 1.0;
                    double y = playerLoc.getY() + 1.0; // 腰部位置

                    // 创建目标位置
                    Location targetLoc = new Location(playerLoc.getWorld(), x, y, z);
                    Block targetBlock = targetLoc.getBlock();

                    targetBlock.setType(Material.LAVA);
                    // 添加永久抗火效果
                    PotionEffect fireResistance = new PotionEffect(
                            PotionEffectType.FIRE_RESISTANCE,
                            Integer.MAX_VALUE,
                            0,
                            true,
                            false
                    );
                    player.addPotionEffect(fireResistance);

                    // 视觉反馈
                    player.sendTitle(
                            "§b\uD83D\uDCA5 §l熔岩行者 §r\uD83D\uDCA5",
                            "§7永久抗火效果已激活",
                            10, 60, 10
                    );
                    player.spawnParticle(Particle.LAVA, playerLoc, 10, 0.2, 0.2, 0.2, 0.1);
                    player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
                    return true;
                }
                return false; // 确保在条件不满足时返回
            case "闪现":
                int juli = 40; // 猎人闪现距离30
                if (plugin.isEscaper(player.getUniqueId())) {
                    juli = 50; // 逃生者闪现距离40
                }
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    Location targetLocation = findTargetLocation(player, juli);



                    // 播放传送前的粒子效果
                    player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);

                    // 执行传送，增强视觉效果
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.isOnline()) {
                                playTeleportEffects(player, player.getLocation(), targetLocation);
                                player.teleport(targetLocation);
                                player.sendTitle("§b\uD83D\uDCC8 §l闪现 §r\uD83D\uDCC8", "", 10, 30, 10);
                                activatePermanentRemoteImmune(player);

                                // 新增：给予10秒摔落免疫效果
                                player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.RESISTANCE,
                                        200, // 10秒 (20ticks/秒 × 10秒)
                                        100,   // 等级0（仅免疫摔落伤害）
                                        true,
                                        false
                                ));
                                player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.SPEED,
                                        200, // 5秒 (20ticks/秒 × 10秒)
                                        1,   // 等级0（仅免疫摔落伤害）
                                        true,
                                        false
                                ));
                            }

                        }
                    }.runTaskLater(plugin, 0L);

                    return true;

                }
                return false; // 确保在条件不满足时返回
            case "定身术":
                if (isWeaponOrTool(player.getInventory().getItemInMainHand().getType())) {
                    // 动态标题
                    player.sendTitle(
                            "§b\uD83D\uDC40 §l定身术 §r\uD83D\uDC40",
                            "§7持续时间: §a" + SKILL_DURATIONS.get("定身术") + "秒",
                            10,  // 淡入时间（ticks）
                            60,  // 停留时间
                            10   // 淡出时间
                    );

                    // 给予永久急迫III效果
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.HASTE,
                            Integer.MAX_VALUE,  // 永久效果
                            2,                  // 等级III（0-based索引）
                            true,               // 显示粒子
                            true                // 显示图标
                    ));

                    // 激活发光效果
                    activateGlowEffect(player);
                    // 激活定身效果（3秒）
                    plugin.getFreezeSkill().freezeEscapers(player, SKILL_DURATIONS.get("定身术") * 20); // 持续时间

                    // 设置60秒后移除发光效果 - 确保taskId初始化
                    int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        removeGlowEffect(player);
                    }, 1800).getTaskId(); // 1200 ticks = 90秒

                    // 存储任务ID以便清理
                    if (speedTasks.containsKey(player.getUniqueId())) {
                        Bukkit.getScheduler().cancelTask(speedTasks.get(player.getUniqueId()));
                    }
                    speedTasks.put(player.getUniqueId(), taskId);

                    // 开始持续时间跟踪（替代原有的startCooldown）
                    int duration = getSkillDuration(skill);
                    int cooldownTime = getSkillCooldown(skill);
                    startDurationTracking(player, skill, duration, cooldownTime);
                    startCooldown(player, "定身术");

                    player.sendMessage(plugin.getMessage("activation_skill", "&7技能已激活！"));
                    return true;
                }
                return false; // 确保在条件不满足时返回
            default:
                return false;
        }
    }
    private final Random random = new Random();
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 只处理玩家造成的伤害
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();

        // 判断玩家是否拥有“熔岩行者”技能，可以根据权限、NBT、metadata、玩家列表等方式判断，这里以权限为例
        if ("熔岩行者".equals(getSelectedSkill(attacker))) {
            // 10% 几率触发着火
            if (random.nextDouble() < 0.20) {
                Entity victim = event.getEntity();
                if (victim instanceof LivingEntity) {
                    ((LivingEntity) victim).setFireTicks(60); // 着火 3 秒（20 ticks = 1秒）
                }
            }
        }


    }

    // 激活逃生者发光效果
    private void activateGlowEffect(Player hunter) {
        UUID hunterId = hunter.getUniqueId();
        // 遍历所有在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            // 如果是逃生者，则对猎人显示发光效果
            if (plugin.isEscaper(playerId)) {
                // 添加发光效果
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING,
                        1200,  // 60秒
                        0,    // 等级0
                        true, // 显示粒子
                        false // 不显示图标
                ));

                // 确保猎人能看到发光效果（即使效果对所有人启用）
                hunter.showPlayer(plugin, player);
                hunter.spawnParticle(Particle.DUST, player.getLocation(), 10,
                        new Particle.DustOptions(Color.fromRGB(0, 0, 0), 1.0f));
            }
        }
    }

    // 移除逃生者发光效果
    private void removeGlowEffect(Player hunter) {
        // 遍历所有在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            // 如果是逃生者，移除发光效果
            if (plugin.isEscaper(playerId)) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
    }

    private void activatePermanentRemoteImmune(Player player) {
        UUID uuid = player.getUniqueId();
        remoteImmune.put(uuid, true);


    }
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        UUID uuid = player.getUniqueId();

        if (remoteImmune.getOrDefault(uuid, false) && isRemoteWeaponDamage(event)) {
            event.setCancelled(true);
        }
    }
    // 替换原有findTargetLocation方法
    private Location findTargetLocation(Player player, int maxDistance) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();

        // 直接计算30格距离的目标位置
        Location target = start.clone().add(direction.multiply(maxDistance));

        // 保持玩家的原始朝向角度
        target.setYaw(start.getYaw());
        target.setPitch(start.getPitch());

        return target;
    }
    // 修改playTeleportEffects方法，增加碰撞粒子效果
    private void playTeleportEffects(Player player, Location from, Location to) {
        // 起点粒子效果
        from.getWorld().spawnParticle(Particle.PORTAL, from, 50, 0.5, 0.5, 0.5, 0.1);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // 终点粒子效果
        to.getWorld().spawnParticle(Particle.EXPLOSION, to, 3, 0.2, 0.2, 0.2, 0.1);
        to.getWorld().spawnParticle(Particle.PORTAL, to, 50, 0.5, 0.5, 0.5, 0.1);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        // 玩家视角特效
        player.spawnParticle(Particle.END_ROD, player.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
    }

    // 新增恢复可见性方法
    private void restorePlayerVisibility(Player player) {
        UUID playerId = player.getUniqueId();
        UUID uuid = player.getUniqueId();

        plugin.addEscaper(playerId);

        // 启动猎人距离显示功能
        plugin.getHunterTracker().startTrackingEscaper(player);

        // 恢复装备和副手物品
        if (armorStorage.containsKey(uuid)) {
            player.getInventory().setArmorContents(armorStorage.get(uuid));
            armorStorage.remove(uuid);
        }

        if (offHandStorage.containsKey(uuid)) {
            player.getInventory().setItemInOffHand(offHandStorage.get(uuid));
            offHandStorage.remove(uuid);
        }

        // 强制更新玩家外观
        player.updateInventory();
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(plugin, player);
            online.showPlayer(plugin, player);
        }
    }

    // 冷却系统实现
    private static class Cooldown {
        long lastUsed;
        int duration; // 秒
    }

    public boolean checkCooldown(Player player, String skill) {
        UUID uuid = player.getUniqueId();
        // 获取玩家的BossBar
        BossBar bossBar = getOrCreateBossBar(player);

        // 检查BossBar是否是否就绪状态（进度为1.0）
        if (bossBar.getProgress() >= 1.0) {
            // 技能就绪，可以使用
            return true;
        }

        return false;
    }

    // 修改startDurationTracking方法，使用新的任务映射
    private void startDurationTracking(Player player, String skill, int duration, int cooldown) {
        UUID uuid = player.getUniqueId();
        long endTime = System.currentTimeMillis() + (duration * 1000L);
        long startTime = System.currentTimeMillis();

        // 取消可能存在的旧任务
        if (bossBarTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(bossBarTasks.get(uuid));
        }

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
                startCooldownTracking(player, skill, cooldown);
                return;
            }

            // 持续时间进度: 从1.0降到0.0
            double progress = 1.0 - (currentTime - startTime) / (double)(duration * 1000L);
            updateBossBar(player, skill, progress, true); // 标记为持续时间
        }, 0L, 20L));

        bossBarTasks.put(uuid, taskId.get());
    }

    private void startCooldownTracking(Player player, String skill, int cooldown) {
        UUID uuid = player.getUniqueId();
        long endTime = System.currentTimeMillis() + (cooldown * 1000L);
        long startTime = System.currentTimeMillis();

        // 取消可能存在的旧任务
        if (bossBarTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(bossBarTasks.get(uuid));
        }
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
                updateBossBar(player, skill, 1.0, false); // 冷却结束
                return;
            }

            // 冷却进度: 从0.0升到1.0
            double progress = (currentTime - startTime) / (double)(cooldown * 1000L);
            updateBossBar(player, skill, progress, false); // 标记为冷却时间
        }, 0L, 20L));

        bossBarTasks.put(uuid, taskId.get());
    }


    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 检查玩家是否选择了"爆炸弩"技能
        if ("爆炸弩".equals(getSelectedSkill(player))) {
            plugin.getExplosiveCrossbowListener().giveCrossbowPackage(player);
        }

        // 如果之前激活过免疫，重生后恢复
        if (remoteImmune.getOrDefault(player.getUniqueId(), false)) {
            activatePermanentRemoteImmune(player);
        }
    }




    // 监听左键点击事件
    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        // 只处理左键动作
        if(event.getAction() != Action.LEFT_CLICK_AIR &&
                event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        // 检查是否选择了爆炸弩技能
        if (!isbaozhanu(player)) return;

        // 检查是否是职业弩
        if(isCrossbowSkillItem(item)) {
            handleRocketSupply(player);
        }
    }
    // 判断是否为职业弩
    private boolean isCrossbowSkillItem(ItemStack item) {
        if(item == null || item.getType() != Material.CROSSBOW) return false;
        if(!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        return meta.hasLore() && meta.getLore().contains(ChatColor.GRAY + "左键获取爆炸火箭");
    }

    // 处理弹药补充
    private void handleRocketSupply(Player player) {
        // 检查冷却
        if(!checkCooldown(player, "爆炸弩")) {
            player.sendActionBar(ChatColor.RED + "技能冷却中！");
            return;
        }

        // 检查背包空间
        if(player.getInventory().firstEmpty() == -1) {
            player.sendMessage(plugin.getMessage("backpack_full", "背包已满，无法获取弹药！"));
            return;
        }

        // 给予弹药
        ItemStack rockets = plugin.getExplosiveCrossbowListener().createExplosiveRockets(5);
        player.getInventory().addItem(rockets);

        // 效果反馈
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);
        player.spawnParticle(Particle.FIREWORK, player.getEyeLocation(), 20);
        player.sendTitle("", ChatColor.YELLOW + "✧ 获得5发爆炸火箭 ✧", 10, 40, 10);

        // 开始冷却
        startCooldown(player, "爆炸弩");
    }

    // 处理玩家退出
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. 清理灵魂模式和速度任务（原有逻辑保留）
        if(soulTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(soulTasks.get(uuid));
            soulTasks.remove(uuid);
            soulMode.remove(uuid);
        }
        if (speedTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(speedTasks.get(uuid));
            speedTasks.remove(uuid);
        }

        // 2. 新增：清理Boss血条实例（避免绑定旧Player）
        if (playerBossBars.containsKey(uuid)) {
            BossBar oldBar = playerBossBars.remove(uuid);
            oldBar.removeAll(); // 移除玩家绑定
        }

        // 3. 新增：清理Boss血条更新任务
        if (bossBarTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(bossBarTasks.get(uuid));
            bossBarTasks.remove(uuid);
        }

        // 4. 清理其他状态（原有逻辑保留）
        armorStorage.remove(uuid);
        mainHandStorage.remove(uuid);
        offHandStorage.remove(uuid);
        remoteImmune.remove(uuid);
        immuneParticlesTask.remove(uuid);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        if (speedTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(speedTasks.get(uuid));
            speedTasks.remove(uuid);
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE); // 移除抗火效果

        // 移除发光效果任务
        if (speedTasks.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().cancelTask(speedTasks.get(player.getUniqueId()));
            speedTasks.remove(player.getUniqueId());
        }

        // 移除急迫效果
        player.removePotionEffect(PotionEffectType.HASTE);
    }




    private boolean isRemoteWeaponDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();

        // 基础远程伤害原因
        if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            return true;
        }

        // 处理实体爆炸伤害（如爆炸箭、烟花）
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return true;
        }

        // 特殊远程武器伤害判断（通过伤害来源）
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
            Entity damager = damageEvent.getDamager();

            // 判断伤害来源是否为远程武器
            if (damager instanceof Projectile) {
                Projectile projectile = (Projectile) damager;
                Entity shooter = (Entity) projectile.getShooter();

                // 排除玩家自身造成的投射物伤害（如弓箭自伤）
                if (shooter instanceof Player && shooter != event.getEntity()) {
                    return true;
                }
            }

            // 判断是否为远程武器实体（如骷髅的箭）
            String damagerType = damager.getType().name();
            if (damagerType.contains("ARROW") || damagerType.contains("SNOWBALL") ||
                    damagerType.contains("EGG") || damagerType.contains("TRIDENT")) {
                return true;
            }
        }

        return false;
    }


    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // 获取事件涉及的玩家
        Player player = event.getPlayer();

        // 获取传送的原因
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (player.getGameMode() == GameMode.SPECTATOR && soulMode.contains(player.getUniqueId())) {
            // 如果两个条件都满足，就取消这个事件
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("unable_transmit", "你当前状态无法传送"));
        }
    }


}

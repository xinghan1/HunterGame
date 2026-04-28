package com.huntergame.effect;

import com.huntergame.HunterGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class EffectManager implements Listener {
    private final HunterGame plugin;
    private FileConfiguration effectConfig;
    private final File configFile;

    private boolean enable; // 是否启用
    private List<String> allEffectIds;
    private int globalTaskInterval;         // 全局效果任务间隔（tick）
    private int defaultPotionDuration;      // 药水默认持续时间（tick）
    private int defaultSpecialDuration;     // 特殊效果默认持续时间（tick）
    private Map<Material, Material> oreToIngotMap;
    private final Random random = new Random();
    private BukkitTask enhancementTask;

    // 方向轴枚举（用于超级挖矿的方向判断）
    private enum Axis { X, Y, Z }

    public EffectManager(HunterGame plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "effect.yml");
        loadOrCreateConfig();
        cacheConfigValues();
    }

    private void loadOrCreateConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("effect.yml", false);
            plugin.getLogger().info("已生成默认 effect.yml 配置文件");
        }
        this.effectConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        loadOrCreateConfig();
        cacheConfigValues();
        plugin.getLogger().info("Effect.yml 配置已重新加载");
    }

    // 缓存配置到内存
    private void cacheConfigValues() {
        this.enable = effectConfig.getBoolean("global.enable", true);
        // 1. 全局基础配置（秒转tick：1秒=20tick）
        this.globalTaskInterval = Math.max(20 * 10, effectConfig.getInt("global.enhancement-interval") * 20); // 最小10秒间隔
        this.defaultPotionDuration = Math.max(20 * 10, effectConfig.getInt("global.default-effect-duration") * 20); // 最小10秒
        this.defaultSpecialDuration = Math.max(20 * 10, effectConfig.getInt("global.special-effect-duration") * 20); // 最小10秒

        this.allEffectIds = new ArrayList<>();
        if (effectConfig.contains("potion-effects")) {
            allEffectIds.addAll(effectConfig.getConfigurationSection("potion-effects").getKeys(false));
        }

        allEffectIds = allEffectIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        this.oreToIngotMap = new HashMap<>();
        if (effectConfig.contains("ore-smelt-mapping")) {
            Map<String, Object> rawMapping = effectConfig.getConfigurationSection("ore-smelt-mapping").getValues(false);
            for (Map.Entry<String, Object> entry : rawMapping.entrySet()) {
                try {
                    Material ore = Material.valueOf(entry.getKey().toUpperCase(Locale.ENGLISH));
                    Material ingot = Material.valueOf(entry.getValue().toString().toUpperCase(Locale.ENGLISH));
                    oreToIngotMap.put(ore, ingot);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的矿石映射配置：" + entry.getKey() + " → " + entry.getValue());
                }
            }
        }
        if (oreToIngotMap.isEmpty()) {
            oreToIngotMap.put(Material.IRON_ORE, Material.IRON_INGOT);
            oreToIngotMap.put(Material.GOLD_ORE, Material.GOLD_INGOT);
            oreToIngotMap.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        }
    }

    public void startEnhancementTask() {
        stopEnhancementTask();
        enhancementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enable) return;

                if (!plugin.isGameRunning()) {
                    this.cancel();
                    return;
                }

                if (allEffectIds.isEmpty()) {
                    plugin.getLogger().warning("effect.yml 中未配置任何可触发效果");
                    return;
                }

                String randomEffectId = allEffectIds.get(random.nextInt(allEffectIds.size()));
                applyEffectToAllPlayers(randomEffectId);
            }
        }.runTaskTimer(plugin, 400L, globalTaskInterval);
    }

    public void stopEnhancementTask() {
        if (enhancementTask != null) {
            enhancementTask.cancel();
            enhancementTask = null;
        }
    }


    private void applyEffectToAllPlayers(String effectId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || !player.isValid()) {
                continue;
            }

            // 区分效果类型执行
            if (isSinglePotionEffect(effectId)) {
                applySinglePotionEffect(player, effectId);
            } else if (isCombinationEffect(effectId)) {
                applyCombinationEffect(player, effectId);
            } else if (isSpecialEffect(effectId)) {
                applySpecialEffect(player, effectId);
            } else {
                plugin.getLogger().warning("无效效果ID：" + effectId + "（请检查 potion-effects 配置）");
            }
        }
    }

    /** 判断是否为「单个药水效果」 */
    private boolean isSinglePotionEffect(String effectId) {
        String configPath = "potion-effects." + effectId;
        return effectConfig.contains(configPath)
                && !"COMBINATION".equals(effectConfig.getString(configPath + ".type", ""));
    }

    /** 判断是否为「组合药水效果」 */
    private boolean isCombinationEffect(String effectId) {
        String configPath = "potion-effects." + effectId;
        return effectConfig.contains(configPath)
                && "COMBINATION".equals(effectConfig.getString(configPath + ".type", ""))
                && effectConfig.contains(configPath + ".combine");
    }

    /** 判断是否为「特殊效果」（爆破/超级挖矿） */
    private boolean isSpecialEffect(String effectId) {
        return effectConfig.contains("special-effects." + effectId);
    }

    /** 应用单个药水效果 */
    private void applySinglePotionEffect(Player player, String effectId) {
        String configPath = "potion-effects." + effectId;

        // 读取配置参数（缺省值兜底）
        String effectTypeStr = effectConfig.getString(configPath + ".type", "");
        int amplifier = Math.max(0, effectConfig.getInt(configPath + ".amplifier", 0)); // 等级最小为0（I级）
        int durationTick = Math.max(defaultPotionDuration / 2, effectConfig.getInt(configPath + ".duration", defaultPotionDuration));
        String message = effectConfig.getString(configPath + ".message", "&a获得特殊药水效果！").replace('&', '§');

        // 验证药水类型合法性
        PotionEffectType effectType = PotionEffectType.getByName(effectTypeStr);
        if (effectType == null) {
            plugin.getLogger().warning("效果 " + effectId + " 类型错误：" + effectTypeStr);
            return;
        }

        // 应用效果并发送提示
        player.addPotionEffect(new PotionEffect(effectType, durationTick, amplifier, false, true)); // 隐藏粒子+显示图标
        if (!message.trim().isEmpty()) {
            player.sendMessage(message);
        }
    }

    /** 应用组合药水效果（执行多个子效果） */
    private void applyCombinationEffect(Player player, String effectId) {
        String configPath = "potion-effects." + effectId;

        // 读取组合配置
        List<String> subEffectIds = effectConfig.getStringList(configPath + ".combine");
        String message = effectConfig.getString(configPath + ".message", "&a获得所有药水效果！").replace('&', '§');

        // 执行每个子效果
        List<String> validSubEffects = new ArrayList<>();
        for (String subId : subEffectIds) {
            if (isSinglePotionEffect(subId)) {
                applySinglePotionEffect(player, subId);
                validSubEffects.add(subId);
            }
        }

        // 仅当有有效子效果时发送提示
        if (!validSubEffects.isEmpty() && !message.trim().isEmpty()) {
            player.sendMessage(message);
        }
    }

    /** 应用特殊效果（爆破采矿/超级挖矿） */
    private void applySpecialEffect(Player player, String effectId) {
        String configPath = "special-effects." + effectId;

        // 读取特殊效果配置
        String startMsg = effectConfig.getString(configPath + ".message", "&a特殊效果激活！").replace('&', '§');
        String endMsg = effectConfig.getString(configPath + ".end-message", "&c特殊效果结束！").replace('&', '§');
        int durationTick = Math.max(defaultSpecialDuration / 2, effectConfig.getInt(configPath + ".duration", defaultSpecialDuration));

        // 替换消息占位符（{duration} 显示秒数）
        startMsg = startMsg.replace("{duration}", String.valueOf(durationTick / 20));
        endMsg = endMsg.replace("{duration}", String.valueOf(durationTick / 20));

        // 标记效果元数据（避免与其他插件冲突，绑定当前插件）
        player.setMetadata(effectId, new FixedMetadataValue(plugin, true));
        player.sendMessage(startMsg);

        // 效果到期自动清理
        String finalEndMsg = endMsg;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.hasMetadata(effectId)) {
                    player.removeMetadata(effectId, plugin);
                    player.sendMessage(finalEndMsg);
                }
            }
        }.runTaskLater(plugin, durationTick);
    }

    // ------------------------------
    // 方块破坏事件监听（特殊效果逻辑）
    // ------------------------------
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block brokenBlock = event.getBlock();

        // 跳过创造模式/观察者模式玩家
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 1. 处理爆破采矿效果
        if (hasSpecialEffect(player, "explosion_mining")) {
            handleExplosionMining(player, brokenBlock);
        }

        // 2. 处理超级挖矿效果
        if (hasSpecialEffect(player, "auto_mining")) {
            handleAutoMining(player, brokenBlock);
        }
    }

    /** 判断玩家是否拥有指定特殊效果 */
    private boolean hasSpecialEffect(Player player, String effectId) {
        if (!player.isOnline() || !player.isValid()) {
            return false;
        }
        List<MetadataValue> metadata = player.getMetadata(effectId);
        // 过滤：仅认可当前插件设置的元数据
        return metadata.stream()
                .anyMatch(meta -> meta.getOwningPlugin() == plugin && meta.asBoolean());
    }

    /** 爆破采矿逻辑（范围、熔炼从配置读取） */
    private void handleExplosionMining(Player player, Block centerBlock) {
        String configPath = "special-effects.explosion_mining";
        int radius = Math.max(1, Math.min(3, effectConfig.getInt(configPath + ".radius", 1))); // 范围限制1-3（3x3到7x7）
        boolean needSmelt = effectConfig.getBoolean(configPath + ".smelt-ores", true);

        // 遍历爆破范围内所有方块
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block targetBlock = centerBlock.getRelative(x, y, z);
                    // 仅破坏可挖掘方块，且避免重复破坏
                    if (isBreakableBlock(targetBlock.getType()) && targetBlock.getType() != Material.AIR) {
                        dropBlockItem(player, targetBlock, needSmelt);
                    }
                }
            }
        }

    }

    /** 超级挖矿逻辑（距离、方向从配置读取） */
    private void handleAutoMining(Player player, Block originBlock) {
        String configPath = "special-effects.auto_mining";
        int maxDistance = Math.max(3, Math.min(10, effectConfig.getInt(configPath + ".max-distance", 5))); // 距离限制3-10
        boolean skipUnbreakable = effectConfig.getBoolean(configPath + ".skip-unbreakable", true);

        // 获取玩家视线主方向轴
        Vector eyeDir = player.getEyeLocation().getDirection().normalize();
        Axis mainAxis = getMainDirectionAxis(eyeDir);
        int directionStep = getDirectionStep(
                mainAxis == Axis.X ? eyeDir.getX() : (mainAxis == Axis.Y ? eyeDir.getY() : eyeDir.getZ())
        );

        // 沿主方向挖掘指定距离
        Block currentBlock = originBlock;
        for (int i = 1; i <= maxDistance; i++) {
            // 计算下一个方块位置
            currentBlock = switch (mainAxis) {
                case X -> currentBlock.getRelative(directionStep, 0, 0);
                case Y -> currentBlock.getRelative(0, directionStep, 0);
                case Z -> currentBlock.getRelative(0, 0, directionStep);
            };

            // 检查方块有效性
            if (!currentBlock.getWorld().isChunkLoaded(currentBlock.getChunk())) {
                continue; // 跳过未加载区块的方块
            }

            Material blockType = currentBlock.getType();
            if (blockType == Material.AIR) {
                continue; // 跳过空气
            }

            // 处理不可挖掘方块
            if (!isBreakableBlock(blockType)) {
                if (skipUnbreakable) {
                    break; // 遇到不可挖掘方块停止
                } else {
                    continue; // 不停止但跳过
                }
            }

            // 破坏方块并掉落物品
            dropBlockItem(player, currentBlock, true);

        }
    }

    /** 判断方块是否可破坏（排除基岩、容器等关键方块） */
    private boolean isBreakableBlock(Material material) {
        if (!material.isBlock() || material == Material.BEDROCK) {
            return false;
        }
        // 排除容器类方块
        if (material == Material.CHEST || material == Material.ENDER_CHEST ||
                material == Material.BARREL || material == Material.SHULKER_BOX ||
                material == Material.END_PORTAL_FRAME || material == Material.END_PORTAL ||
                material == Material.NETHER_PORTAL) {
            return false;
        }
        return true;
    }

    /** 方块掉落物品（支持自动熔炼） */
    private void dropBlockItem(Player player, Block block, boolean needSmelt) {
        Material blockType = block.getType();
        World world = block.getWorld();
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        if (needSmelt && oreToIngotMap.containsKey(blockType)) {
            // 自动熔炼逻辑
            Material ingot = oreToIngotMap.get(blockType);
            block.setType(Material.AIR);
            world.dropItemNaturally(dropLoc, new ItemStack(ingot, 1));
            world.playSound(dropLoc, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.5f, 1.0f);
        } else {
            // 普通掉落
            block.breakNaturally(player.getInventory().getItemInMainHand());
        }
    }

    /** 获取视线主方向轴 */
    private Axis getMainDirectionAxis(Vector direction) {
        double xAbs = Math.abs(direction.getX());
        double yAbs = Math.abs(direction.getY());
        double zAbs = Math.abs(direction.getZ());

        if (yAbs > xAbs && yAbs > zAbs) {
            return Axis.Y;
        } else if (xAbs > zAbs) {
            return Axis.X;
        } else {
            return Axis.Z;
        }
    }

    /** 获取方向步长（-1 或 1） */
    private int getDirectionStep(double value) {
        if (value > 0.1) return 1;
        if (value < -0.1) return -1;
        return 0;
    }


}

package com.huntergame.vote;

import com.huntergame.bedrock.BedrockVoteSystemGUI;
import com.huntergame.gui.VoteSystemGUI;
import com.huntergame.HunterGame;
import com.huntergame.util.BedrockSupport;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 独立的投票系统类：负责模式选择、阵营选择、战役类型的GUI与逻辑
 */
public class VoteSystem implements Listener {
    // 投票类型常量
    public static final int VOTE_ESCAPER = 1;    // 投票逃生者
    public static final int VOTE_HUNTER = 2;     // 投票猎人
    public static final int MODE_FINAL_BATTLE = 2;   // 终章之战
    public static final int MODE_VANILLA_HUNTER = 3; // 原版猎人模式

    // 战役类型常量
    public static final int TYPE_PERSISTENCE = 1; // 生存战
    public static final int TYPE_CLEARANCE = 2;   // 通关战

    private final HunterGame plugin;
    private final Map<UUID, Integer> playerVotes = new HashMap<>();
    public final Map<UUID, Integer> playerModeChoice = new HashMap<>();
    private final Map<UUID, Integer> playerTypeChoice = new HashMap<>(); // 战役类型投票

    private ConfigurationSection voteGuiConfig;
    private ConfigurationSection voteItemConfig;


    public VoteSystem(HunterGame plugin) {
        this.plugin = plugin;
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 加载投票相关配置
     */
    private void loadConfig() {
        plugin.saveDefaultConfig();

        // 1. 初始化模式选择开关配置（固定/投票）
        if (!plugin.getConfig().contains("mode-selection.toggle")) {
            // 开关默认值：false（关闭固定模式，默认投票模式）
            plugin.getConfig().set("mode-selection.toggle.fixed-mode-enabled", false);
            plugin.getConfig().set("mode-selection.toggle.fixed-mode-id", MODE_FINAL_BATTLE);
        }

        // 从 gui.yml 读取配置
        voteGuiConfig = plugin.getGuiConfig().getConfigurationSection("vote-gui");
        voteItemConfig = plugin.getGuiConfig().getConfigurationSection("vote-item");

        // 配置缺失时初始化默认值
        if (voteGuiConfig == null) {
            voteGuiConfig = plugin.getGuiConfig().createSection("vote-gui");
            initDefaultVoteGuiConfig();
        }
        if (voteItemConfig == null) {
            voteItemConfig = plugin.getGuiConfig().createSection("vote-item");
            initDefaultVoteItemConfig();
        }
        plugin.saveConfig();
    }

    /**
     * 工具方法：判断是否启用固定模式
     */
    public boolean isFixedModeEnabled() {
        return plugin.getConfig().getBoolean("mode-selection.toggle.fixed-mode-enabled", false);
    }

    /**
     * 工具方法：获取固定模式的ID和名称
     * @return 数组：[0] = 固定模式ID，[1] = 固定模式名称
     */
    public Object[] getFixedModeInfo() {
        int fixedModeId = normalizeGameMode(plugin.getConfig().getInt("mode-selection.toggle.fixed-mode-id", MODE_FINAL_BATTLE));
        String fixedModeName = "未知模式";

        // 根据模式ID匹配对应名称（与默认配置中的模式名称一致）
        switch (fixedModeId) {
            case MODE_FINAL_BATTLE:
                fixedModeName = "终章之战";
                break;
            case MODE_VANILLA_HUNTER:
                fixedModeName = "原版猎人";
                break;
        }
        return new Object[]{fixedModeId, fixedModeName};
    }


    /**
     * 初始化默认的投票GUI配置（防止配置缺失导致报错）
     */
    private void initDefaultVoteGuiConfig() {
        voteGuiConfig.set("title", "模式与角色选择");
        voteGuiConfig.set("size", 36);
        voteGuiConfig.set("messages.no-mode-selected", "&e请先选择游戏模式！");

        // 战役类型配置 (生存战/通关战)
        ConfigurationSection typeSection = voteGuiConfig.createSection("type-section");
        typeSection.set("slot", 3);
        ConfigurationSection typeItems = typeSection.createSection("items");
        ConfigurationSection persistence = typeItems.createSection("0");
        persistence.set("id", TYPE_PERSISTENCE);
        persistence.set("material", "CLOCK");
        persistence.set("name", "&e生存战");
        ConfigurationSection clearance = typeItems.createSection("1");
        clearance.set("id", TYPE_CLEARANCE);
        clearance.set("material", "DIAMOND_SWORD");
        clearance.set("name", "&a通关战");

        // 模式选择区默认配置
        ConfigurationSection modeSection = voteGuiConfig.createSection("mode-section");
        modeSection.set("slot", 12);
        ConfigurationSection modeItems = modeSection.createSection("items");

        ConfigurationSection vanillaMode = modeItems.createSection("0");
        vanillaMode.set("id", MODE_VANILLA_HUNTER);
        vanillaMode.set("material", "IRON_INGOT");
        vanillaMode.set("name", "&7原版猎人");

        ConfigurationSection finalMode = modeItems.createSection("1");
        finalMode.set("id", MODE_FINAL_BATTLE);
        finalMode.set("material", "DRAGON_EGG");
        finalMode.set("name", "&c终章之战");

        // 阵营选择区默认配置
        ConfigurationSection roleSection = voteGuiConfig.createSection("role-section");
        roleSection.set("slot", 21);
        ConfigurationSection roleItems = roleSection.createSection("items");

        ConfigurationSection escaper = roleItems.createSection("0");
        escaper.set("id", VOTE_ESCAPER);
        escaper.set("material", "EMERALD");
        escaper.set("name", "&a逃生者角色");

        ConfigurationSection hunter = roleItems.createSection("1");
        hunter.set("id", VOTE_HUNTER);
        hunter.set("material", "REDSTONE");
        hunter.set("name", "&c猎人角色");

        // 分隔装饰默认配置
        ConfigurationSection divider = voteGuiConfig.createSection("divider");
        divider.set("material", "GRAY_STAINED_GLASS_PANE");
        divider.set("name", " ");
    }

    /**
     * 初始化默认的投票物品配置
     */
    private void initDefaultVoteItemConfig() {
        voteItemConfig.set("material", "NAME_TAG");
        voteItemConfig.set("name", "&6投票");
        voteItemConfig.set("lore", Collections.singletonList("&7右键点击选择你想加入的阵营"));
        voteItemConfig.set("slot", 1);
        voteItemConfig.set("persistent-key", "campaign_vote");
    }

    // ==================== 投票物品相关 ====================

    /**
     * 给玩家发放投票物品（命名牌）
     */
    public void giveVoteItemToPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        ItemStack voteItem = createVoteItem();
        int slot = voteItemConfig.getInt("slot", 1); // 默认第2格（索引1）
        player.getInventory().setItem(slot, voteItem);
    }

    /**
     * 创建投票物品（从配置读取属性）
     */
    private ItemStack createVoteItem() {
        // 从配置读取物品属性
        Material material = Material.valueOf(voteItemConfig.getString("material", "NAME_TAG"));
        String name = ChatColor.translateAlternateColorCodes('&', voteItemConfig.getString("name", "&6投票"));
        List<String> lore = voteItemConfig.getStringList("lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
        String persistentKey = voteItemConfig.getString("persistent-key", "campaign_vote");

        // 构建物品
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            NamespacedKey voteKey = new NamespacedKey(plugin, "huntergame_vote_item");
            // 设置自定义标记（用于识别投票物品）
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(plugin, persistentKey), PersistentDataType.STRING, persistentKey);
            meta.getPersistentDataContainer().set(voteKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    // 向GUI添加战役类型选择物品
    public void addTypeItemsToGUI(Inventory gui) {
        ConfigurationSection typeSection = voteGuiConfig.getConfigurationSection("type-section");
        if (typeSection == null) return;

        int baseSlot = typeSection.getInt("slot", 3);
        List<ConfigurationSection> typeItems = getConfigItems(typeSection.getConfigurationSection("items"));

        for (int i = 0; i < typeItems.size(); i++) {
            ConfigurationSection itemConfig = typeItems.get(i);
            ItemStack item = createConfigItem(itemConfig);
            // 添加投票计数 Lore
            addVoteCountToItemLore(item, itemConfig.getInt("id"), "type");

            // 战役类型间隔2格放置
            int targetSlot = baseSlot + (i * 2);
            if (targetSlot < gui.getSize()) {
                gui.setItem(targetSlot, item);
            }
        }
    }

    /**
     * 向GUI添加模式选择物品（从配置读取）
     */
    public void addModeItemsToGUI(Inventory gui) {
        if (isFixedModeEnabled()) {
            return;
        }

        ConfigurationSection modeSection = voteGuiConfig.getConfigurationSection("mode-section");
        if (modeSection == null) return;

        int baseSlot = modeSection.getInt("slot", 12); // 模式选择基础槽位
        List<ConfigurationSection> modeItems = getConfigItems(modeSection.getConfigurationSection("items"));

        int visibleIndex = 0;
        // 逐个添加模式物品（带投票计数）
        for (int i = 0; i < modeItems.size(); i++) {
            ConfigurationSection itemConfig = modeItems.get(i);
            if (!isSupportedGameMode(itemConfig.getInt("id"))) {
                continue;
            }
            ItemStack item = createConfigItem(itemConfig);
            // 添加当前投票数到Lore
            addVoteCountToItemLore(item, itemConfig.getInt("id"), "mode");
            // 放置到GUI（基础槽位 + 偏移）
            int targetSlot = baseSlot + visibleIndex;
            if (targetSlot < gui.getSize()) {
                gui.setItem(targetSlot, item);
            }
            visibleIndex++;
        }
    }

    /**
     * 向GUI添加阵营选择物品（从配置读取）
     */
    public void addRoleItemsToGUI(Inventory gui) {
        ConfigurationSection roleSection = voteGuiConfig.getConfigurationSection("role-section");
        if (roleSection == null) return;

        int baseSlot = roleSection.getInt("slot", 21); // 阵营选择基础槽位
        List<ConfigurationSection> roleItems = getConfigItems(roleSection.getConfigurationSection("items"));

        // 逐个添加阵营物品（带投票计数）
        for (int i = 0; i < roleItems.size(); i++) {
            ConfigurationSection itemConfig = roleItems.get(i);
            ItemStack item = createConfigItem(itemConfig);
            // 添加当前投票数到Lore
            addVoteCountToItemLore(item, itemConfig.getInt("id"), "role");
            // 放置到GUI（基础槽位 + 偏移）
            int targetSlot = baseSlot + (i * 2); // 间隔1格放置（21→23）
            if (targetSlot < gui.getSize()) {
                gui.setItem(targetSlot, item);
            }
        }
    }

    /**
     * 填充分隔装饰物品（空白格子）
     */
    public void fillDividerItems(Inventory gui) {
        ConfigurationSection dividerConfig = voteGuiConfig.getConfigurationSection("divider");
        if (dividerConfig == null) return;

        // 1. 构建分隔装饰物品（原有逻辑不变）
        Material material = Material.valueOf(dividerConfig.getString("material", "GRAY_STAINED_GLASS_PANE"));
        String name = ChatColor.translateAlternateColorCodes('&', dividerConfig.getString("name", " "));
        ItemStack divider = new ItemStack(material);
        ItemMeta meta = divider.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            divider.setItemMeta(meta);
        }

        // 2. 填充所有空白格子（原有逻辑不变）
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, divider);
            }
        }

        // 3. 新增：固定模式下，强制填充模式选择槽位
        if (isFixedModeEnabled()) {
            ConfigurationSection modeSection = voteGuiConfig.getConfigurationSection("mode-section");
            if (modeSection != null) {
                int baseSlot = modeSection.getInt("slot", 12);
                List<ConfigurationSection> modeItems = getConfigItems(modeSection.getConfigurationSection("items"));

                for (int i = 0; i < modeItems.size(); i++) {
                    int targetSlot = baseSlot + i;
                    if (targetSlot < gui.getSize()) {
                        gui.setItem(targetSlot, divider);
                    }
                }
            }
        }
    }

    /**
     * 从配置读取物品列表（兼容索引式配置）
     */
    public List<ConfigurationSection> getConfigItems(ConfigurationSection itemsSection) {
        List<ConfigurationSection> items = new ArrayList<>();
        if (itemsSection == null) return items;

        // 遍历配置的所有物品（如 items.0, items.1）
        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection item = itemsSection.getConfigurationSection(key);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 根据配置创建物品（通用方法）
     */
    private ItemStack createConfigItem(ConfigurationSection itemConfig) {
        // 读取物品基础属性
        Material material = Material.valueOf(itemConfig.getString("material", "STONE"));
        String name = ChatColor.translateAlternateColorCodes('&', itemConfig.getString("name", "未命名物品"));
        List<String> lore = itemConfig.getStringList("lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());

        // 构建物品
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 给物品添加投票计数Lore（通用）
     * type: "mode", "role", "type"
     */
    private void addVoteCountToItemLore(ItemStack item, int targetId, String type) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        int count = 0;
        if (type.equals("mode")) count = getModeVoteCount(targetId);
        else if (type.equals("role")) count = getRoleVoteCount(targetId);
        else if (type.equals("type")) count = getTypeVoteCount(targetId);

        // 添加计数Lore
        String currentSelectionFormat = voteGuiConfig.getString("lore.current-selection", "&7当前选择: %count%");
        currentSelectionFormat = ChatColor.translateAlternateColorCodes('&', currentSelectionFormat)
                .replace("%count%", String.valueOf(count));

        lore.add(currentSelectionFormat);

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    // ==================== 投票逻辑相关 ====================

    /**
     * 记录玩家的模式选择
     */
    public void recordPlayerModeChoice(Player player, int mode) {
        if (player == null) return;
        playerModeChoice.put(player.getUniqueId(), mode);
    }

    /**
     * 记录玩家的阵营投票
     */
    public void recordPlayerRoleVote(Player player, int role) {
        if (player == null) return;
        playerVotes.put(player.getUniqueId(), role);
    }

    /**
     * 记录玩家的战役类型投票
     */
    public void recordPlayerTypeVote(Player player, int type) {
        if (player == null) return;
        playerTypeChoice.put(player.getUniqueId(), type);
    }

    public int getModeVoteCount(int mode) {
        return (int) playerModeChoice.values().stream().filter(vote -> vote == mode).count();
    }

    public int getRoleVoteCount(int role) {
        return (int) playerVotes.values().stream().filter(vote -> vote == role).count();
    }

    public int getTypeVoteCount(int type) {
        return (int) playerTypeChoice.values().stream().filter(vote -> vote == type).count();
    }

    /**
     * 确定最终选中的游戏模式（取票数最高）
     */
    public int determineFinalGameMode() {
        if (isFixedModeEnabled()) {
            return normalizeGameMode(plugin.getConfig().getInt("mode-selection.toggle.fixed-mode-id", MODE_FINAL_BATTLE));
        }

        int vanillaCount = getModeVoteCount(MODE_VANILLA_HUNTER);
        int finalCount = getModeVoteCount(MODE_FINAL_BATTLE);

        return finalCount >= vanillaCount ? MODE_FINAL_BATTLE : MODE_VANILLA_HUNTER;
    }

    /**
     * 确定最终战役类型 (生存战/通关战)
     */
    public int determineFinalBattleType() {
        int persistenceCount = getTypeVoteCount(TYPE_PERSISTENCE);
        int clearanceCount = getTypeVoteCount(TYPE_CLEARANCE);

        // 票数相同或生存战更多时，默认生存战？ 或者按配置。这里设为票数高者得
        if (persistenceCount >= clearanceCount) return TYPE_PERSISTENCE;
        return TYPE_CLEARANCE;
    }

    public Map<UUID, Integer> getPlayerRoleVotes() {
        return new HashMap<>(playerVotes);
    }

    public HunterGame getPlugin() {
        return plugin;
    }

    public void resetVoteData() {
        playerVotes.clear();
        playerModeChoice.clear();
        playerTypeChoice.clear();
    }

    /**
     * 清理单个玩家的投票记录（玩家退出时调用）
     */
    public void clearPlayerVote(UUID playerId) {
        playerVotes.remove(playerId);
        playerModeChoice.remove(playerId);
        playerTypeChoice.remove(playerId);
    }

    /**
     * GUI 点击事件监听
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        InventoryView view = event.getView();

        String guiTitle = ChatColor.translateAlternateColorCodes(
                '&', voteGuiConfig.getString("title", "模式与角色选择")
        );

        if (!view.getTitle().equals(guiTitle)) return;

        event.setCancelled(true);
        if (item == null || !item.hasItemMeta()) return;

        int slot = event.getRawSlot();

        handleTypeSelection(player, slot); // 战役类型处理
        handleModeSelection(player, slot); // 模式处理
        handleRoleSelection(player, slot); // 阵营处理
    }

    /**
     * 处理战役类型选择 (Row 1)
     */
    private void handleTypeSelection(Player player, int slot) {
        ConfigurationSection typeSection = voteGuiConfig.getConfigurationSection("type-section");
        if (typeSection == null) return;

        int baseSlot = typeSection.getInt("slot", 3);
        List<ConfigurationSection> typeItems = getConfigItems(typeSection.getConfigurationSection("items"));

        for (int i = 0; i < typeItems.size(); i++) {
            if (slot == baseSlot + (i * 2)) { // 间隔2格
                ConfigurationSection itemConfig = typeItems.get(i);
                int typeId = itemConfig.getInt("id");
                String name = ChatColor.translateAlternateColorCodes('&', itemConfig.getString("name"));

                recordPlayerTypeVote(player, typeId);
                player.sendMessage(plugin.getMessage("choice", "&a已选择: %modeName%").replace("%modeName%", name));
                playItemSound(player, itemConfig);

                // 刷新界面
                refreshGUI(player);
                return;
            }
        }
    }

    /**
     * 处理模式选择
     */
    private void handleModeSelection(Player player, int slot) {
        if (isFixedModeEnabled()) return;
        ConfigurationSection modeSection = voteGuiConfig.getConfigurationSection("mode-section");
        if (modeSection == null) return;

        int baseSlot = modeSection.getInt("slot", 12);
        List<ConfigurationSection> modeItems = getConfigItems(modeSection.getConfigurationSection("items"));

        int visibleIndex = 0;
        for (int i = 0; i < modeItems.size(); i++) {
            ConfigurationSection itemConfig = modeItems.get(i);
            int modeId = itemConfig.getInt("id");
            if (!isSupportedGameMode(modeId)) {
                continue;
            }

            if (slot == baseSlot + visibleIndex) {
                String modeName = ChatColor.translateAlternateColorCodes(
                        '&', itemConfig.getString("name", "未知模式")
                );

                recordPlayerModeChoice(player, modeId);
                player.sendMessage(plugin.getMessage("choice", "&a已选择: %modeName%")
                        .replace("%modeName%", modeName));

                playItemSound(player, itemConfig);
                refreshGUI(player);
                return;
            }
            visibleIndex++;
        }
    }

    /**
     * 处理阵营选择
     */
    private void handleRoleSelection(Player player, int slot) {
        if (!isFixedModeEnabled()) {
            if (!playerModeChoice.containsKey(player.getUniqueId())) {
                String msg = ChatColor.translateAlternateColorCodes(
                        '&', voteGuiConfig.getString("messages.no-mode-selected", "&e请先选择游戏模式！")
                );
                player.sendMessage(msg);
                return;
            }
        }

        ConfigurationSection roleSection = voteGuiConfig.getConfigurationSection("role-section");
        if (roleSection == null) return;

        int baseSlot = roleSection.getInt("slot", 21);
        List<ConfigurationSection> roleItems = getConfigItems(roleSection.getConfigurationSection("items"));

        for (int i = 0; i < roleItems.size(); i++) {
            if (slot == baseSlot + (i * 2)) {
                ConfigurationSection itemConfig = roleItems.get(i);
                int roleId = itemConfig.getInt("id");
                String roleName = ChatColor.translateAlternateColorCodes(
                        '&', itemConfig.getString("name", "未知阵营")
                );

                recordPlayerRoleVote(player, roleId);
                String key = roleId == VOTE_ESCAPER ? "choice_camp_escaper" : "choice_camp_hunter";
                String fallback = roleId == VOTE_ESCAPER
                        ? "&a已选择阵营: %role%"
                        : "&c已选择阵营: %role%";
                player.sendMessage(plugin.getMessage(key, fallback).replace("%role%", roleName));

                playItemSound(player, itemConfig);
                player.closeInventory();
                return;
            }
        }
    }

    private void refreshGUI(Player player) {
        player.closeInventory();
        new BukkitRunnable() {
            @Override
            public void run() {
                VoteSystemGUI.openVoteGUI(VoteSystem.this, voteGuiConfig, player);
            }
        }.runTaskLater(plugin, 2); // 稍微延迟防止GUI闪烁问题
    }

    /**
     * 播放配置中的音效
     */
    public void playItemSound(Player player, ConfigurationSection itemConfig) {
        try {
            String soundName = itemConfig.getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
            Sound sound = Sound.valueOf(soundName);
            float pitch = (float) itemConfig.getDouble("pitch", 1.0);
            player.playSound(player.getLocation(), sound, 1f, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的音效名: " + itemConfig.getString("sound"));
        }
    }

    public boolean isSupportedGameMode(int modeId) {
        return modeId == MODE_FINAL_BATTLE || modeId == MODE_VANILLA_HUNTER;
    }

    public int normalizeGameMode(int modeId) {
        return isSupportedGameMode(modeId) ? modeId : MODE_FINAL_BATTLE;
    }

    /**
     * 玩家右键投票物品打开 GUI
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK &&
                event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;

        String persistentKey = voteItemConfig.getString("persistent-key", "campaign_vote");
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        if (container.has(new NamespacedKey(plugin, persistentKey), PersistentDataType.STRING)) {
            if (BedrockSupport.isBedrockPlayer(plugin, player)) {
                try {
                    BedrockVoteSystemGUI.openBedrockVoteMenu(this, voteGuiConfig, plugin, player);
                } catch (Exception e) {
                    VoteSystemGUI.openVoteGUI(VoteSystem.this, voteGuiConfig, player);
                }
            } else {
                VoteSystemGUI.openVoteGUI(this, voteGuiConfig, player);
            }
            event.setCancelled(true);
        }
    }
}


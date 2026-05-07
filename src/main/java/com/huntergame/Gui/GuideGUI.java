package com.huntergame.gui;

import com.huntergame.bedrock.BedrockGuideGUI;
import com.huntergame.HunterGame;
import com.huntergame.util.BedrockSupport;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GuideGUI implements Listener {
    private final HunterGame plugin;
    private final File configFile;
    private FileConfiguration config;

    // GUI配置
    private String guiTitle;
    private int guiSize;
    private ItemStack triggerItem;
    private final List<GuideItem> guideItems = new ArrayList<>();
    private final String persistentKey = "guide_gui_trigger";
    private int forcedSlot = 8; // 默认放在快捷栏最后一格（可在配置中修改）


    // 物品模型
    public static class GuideItem {
        public int slot;
        public Material material;
        public String name;
        public List<String> lore;
        public String clickAction;

        GuideItem(int slot, Material material, String name, List<String> lore, String clickAction) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.clickAction = clickAction;
        }
    }

    public GuideGUI(HunterGame plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "guidegui.yml");
        saveDefaultConfig();
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // 保存默认配置
    private void saveDefaultConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("guidegui.yml", false);
        }
    }

    // 加载配置（新增强制槽位配置）
    public void loadConfig() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        guideItems.clear();

        // GUI基础配置
        guiTitle = ChatColor.translateAlternateColorCodes('&',
                config.getString("gui.title", "&6玩法介绍"));
        guiSize = config.getInt("gui.size", 27);
        if (guiSize % 9 != 0) guiSize = 27;

        // 触发物品配置（新增强制槽位）
        forcedSlot = config.getInt("trigger-item.forced-slot", 8); // 0-35（0-8是快捷栏）
        // 确保槽位有效（0-35之间）
        if (forcedSlot < 0 || forcedSlot > 35) forcedSlot = 8;

        initTriggerItem();
        loadGuideItems();
    }

    // 初始化触发物品（添加禁止移动的标记）
    private void initTriggerItem() {
        Material material = Material.valueOf(
                config.getString("trigger-item.material", "BOOK").toUpperCase()
        );
        String name = ChatColor.translateAlternateColorCodes('&',
                config.getString("trigger-item.name", "&a玩法指南")
        );
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("trigger-item.lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        triggerItem = new ItemStack(material);
        ItemMeta meta = triggerItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            // 持久化标记（用于判断是否为触发物品）
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(plugin, persistentKey),
                    PersistentDataType.STRING, persistentKey);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            triggerItem.setItemMeta(meta);
        }
    }

    // 加载GUI内物品
    private void loadGuideItems() {
        if (!config.contains("items")) return;
        for (String key : config.getConfigurationSection("items").getKeys(false)) {
            String path = "items." + key;
            int slot = config.getInt(path + ".slot", 0);
            Material material = Material.valueOf(
                    config.getString(path + ".material", "PAPER").toUpperCase()
            );
            String name = ChatColor.translateAlternateColorCodes('&',
                    config.getString(path + ".name", "未命名物品")
            );
            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList(path + ".lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            String clickAction = config.getString(path + ".click-action", "none");
            guideItems.add(new GuideItem(slot, material, name, lore, clickAction));
        }
    }

    /**
     * 给予玩家触发物品（强制放在指定槽位）
     */
    public void giveTriggerItem(Player player) {
        if (player == null || !player.isOnline()) return;

        Inventory inv = player.getInventory();
        // 检查目标槽位是否已有该物品
        ItemStack slotItem = inv.getItem(forcedSlot);
        if (slotItem != null && isTriggerItem(slotItem)) {
            return; // 已存在，无需重复给予
        }

        // 临时存储目标槽位的物品（如果有的话）
        ItemStack tempItem = slotItem;
        // 放置触发物品到指定槽位
        inv.setItem(forcedSlot, triggerItem.clone());

        // 如果原槽位有物品，尝试放入其他空槽
        if (tempItem != null && !tempItem.getType().isAir()) {
            HashMap<Integer, ItemStack> leftover = inv.addItem(tempItem);
            // 若无法放入，掉落地上
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
        }

        // 延迟1tick刷新背包，确保物品显示正确
        new BukkitRunnable() {
            @Override
            public void run() {
                player.updateInventory();
            }
        }.runTaskLater(plugin, 1L);
    }


    /**
     * 打开玩法介绍GUI
     */
    public void openGuideGUI(Player player) {
        if (player == null || !player.isOnline()) return;

        // 检查是否已打开该GUI
        InventoryView openView = player.getOpenInventory();
        if (openView.getTitle().equals(guiTitle)) {
            return;
        }

        Inventory gui = Bukkit.createInventory(null, guiSize, guiTitle);
        for (GuideItem item : guideItems) {
            if (item.slot < 0 || item.slot >= guiSize) continue;

            ItemStack stack = new ItemStack(item.material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.name);
                meta.setLore(item.lore);
                stack.setItemMeta(meta);
            }
            gui.setItem(item.slot, stack);
        }
        player.openInventory(gui);
    }

    /**
     * 监听触发物品点击（打开GUI）
     */
    @EventHandler
    public void onTriggerClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !isTriggerItem(item)) return;

        String triggerAction = config.getString("trigger-item.trigger-action", "RIGHT_CLICK");
        boolean isRight = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean isLeft = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if ((triggerAction.equalsIgnoreCase("RIGHT_CLICK") && isRight) ||
                (triggerAction.equalsIgnoreCase("LEFT_CLICK") && isLeft) ||
                (triggerAction.equalsIgnoreCase("BOTH") && (isRight || isLeft))) {
            if (BedrockSupport.isBedrockPlayer(plugin, player)) {
                try {
                    BedrockGuideGUI.openBedrockGuide(plugin, guideItems, guiTitle, player);
                } catch (Exception e) {
                    openGuideGUI(player);
                }
            } else {
                openGuideGUI(player);
            }
            event.setCancelled(true);
        }

    }

    /**
     * 监听GUI点击（防止拿取物品）
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        InventoryView view = event.getView();

        // 仅处理目标GUI
        if (!view.getTitle().equals(guiTitle)) return;

        event.setCancelled(true); // 禁止所有点击操作

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String itemName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        for (GuideItem item : guideItems) {
            if (ChatColor.stripColor(item.name).equals(itemName)) {
                handleItemClick(player, item.clickAction);
                break;
            }
        }
    }

    /**
     * 禁止移动/丢弃触发物品
     */
    @EventHandler
    public void onItemMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // 检查点击的物品是否为触发物品
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // 情况1：点击了触发物品
        if (current != null && isTriggerItem(current)) {
            event.setCancelled(true);
            return;
        }

        // 情况2：鼠标上有触发物品（尝试放置）
        if (cursor != null && isTriggerItem(cursor)) {
            event.setCancelled(true);
        }
    }


    /**
     * 禁止丢弃触发物品
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isTriggerItem(item)) {
            event.setCancelled(true);
        }
    }

    /**
     * 禁止切换到副手
     */
    @EventHandler
    public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        // 检查主手或副手是否为触发物品
        if ((mainHand != null && isTriggerItem(mainHand)) ||
                (offHand != null && isTriggerItem(offHand))) {
            event.setCancelled(true);
        }
    }

    /**
     * 玩家背包变化时，强制将物品放回指定槽位
     */
    @EventHandler
    public void onInventoryChange(InventoryEvent event) {
        Inventory inv = event.getInventory();
        // 只处理玩家背包
        if (!(inv instanceof PlayerInventory)) return;
        Player player = (Player) (inv).getHolder();
        if (player == null || !player.isOnline()) return;

        // 检查触发物品是否在正确的槽位
        ItemStack correctSlotItem = inv.getItem(forcedSlot);
        if (correctSlotItem == null || !isTriggerItem(correctSlotItem)) {
            // 查找背包中是否有触发物品
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && isTriggerItem(item)) {
                    // 临时存储目标槽位的物品
                    ItemStack temp = inv.getItem(forcedSlot);
                    // 移回正确槽位
                    inv.setItem(forcedSlot, item);
                    inv.setItem(i, temp);
                    break;
                }
            }
        }

        // 确保玩家一定有该物品（防止通过特殊手段删除）
        boolean hasItem = false;
        for (ItemStack item : inv.getContents()) {
            if (item != null && isTriggerItem(item)) {
                hasItem = true;
                break;
            }
        }
        if (!hasItem) {
            // 重新给予物品
            giveTriggerItem(player);
        }
    }

    /**
     * 处理GUI物品点击动作
     */
    private void handleItemClick(Player player, String action) {
        if (action == null || action.equalsIgnoreCase("none")) return;
        if (action.equalsIgnoreCase("open-submenu")) {
            player.sendMessage(plugin.getMessage("guide_open_submenu", "&a打开子菜单（示例）"));
        }
    }

    /**
     * 判断是否为触发物品
     */
    private boolean isTriggerItem(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(new NamespacedKey(plugin, persistentKey), PersistentDataType.STRING);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存guidegui.yml: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        loadConfig();
    }
}


package com.huntergame.crafting;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PermissionRecipeManager implements Listener {
    private static final String RECIPE_FOLDER = "recipes";
    private static final String GUI_TITLE_PREFIX = "权限配方: ";
    private static final int GUI_SIZE = 27;
    private static final int SAVE_SLOT = 26;
    private static final int BOOK_SLOT = 7;
    private static final String RECIPE_BOOK_KEY = "huntergame_recipe_book";
    private static final String RECIPE_DISPLAY_KEY = "huntergame_recipe_display";
    private static final int[] EDIT_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};

    private final HunterGame plugin;
    private final NamespacedKey recipeBookKey;
    private final NamespacedKey recipeDisplayKey;
    private final Map<String, PermissionRecipe> recipes = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Integer>> usageCounts = new HashMap<>();
    private final Set<NamespacedKey> registeredRecipeKeys = new LinkedHashSet<>();

    public PermissionRecipeManager(HunterGame plugin) {
        this.plugin = plugin;
        this.recipeBookKey = new NamespacedKey(plugin, RECIPE_BOOK_KEY);
        this.recipeDisplayKey = new NamespacedKey(plugin, RECIPE_DISPLAY_KEY);
        reload();
    }

    public void reload() {
        unregisterBukkitRecipes();
        recipes.clear();
        File folder = getRecipeFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建权限配方目录: " + folder.getAbsolutePath());
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            PermissionRecipe recipe = loadRecipe(file);
            if (recipe != null) {
                recipes.put(recipe.id, recipe);
            }
        }
        registerBukkitRecipes();
    }

    public void resetUsageCounts() {
        usageCounts.clear();
    }

    public Set<String> getRecipeIds() {
        return new LinkedHashSet<>(recipes.keySet());
    }

    public void giveWaitingRecipeBook(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.getInventory().setItem(BOOK_SLOT, createRecipeBook(false));
    }

    public void giveUnlockedRecipeBook(Player player) {
        if (player == null || !player.isOnline() || plugin.isFinalBattleMode() || getUnlockedRecipes(player).isEmpty()) {
            return;
        }
        if (hasUnlockedRecipeBook(player)) {
            return;
        }
        player.getInventory().addItem(createRecipeBook(true));
    }

    public void openRecipeBook(Player player, boolean unlockedOnly) {
        List<PermissionRecipe> visibleRecipes = unlockedOnly ? getUnlockedRecipes(player) : new ArrayList<>(recipes.values());
        int size = Math.max(9, ((visibleRecipes.size() + 8) / 9) * 9);
        size = Math.min(54, size);
        RecipeBookHolder holder = new RecipeBookHolder(unlockedOnly);
        Inventory inventory = Bukkit.createInventory(holder, size, color(unlockedOnly
                ? message("custom_recipe_unlocked_book_title", "&8已解锁配方")
                : message("custom_recipe_all_book_title", "&8权限配方书")));
        holder.setInventory(inventory);

        int slot = 0;
        for (PermissionRecipe recipe : visibleRecipes) {
            if (slot >= size) {
                break;
            }
            inventory.setItem(slot++, createRecipeDisplayItem(player, recipe));
        }

        if (visibleRecipes.isEmpty()) {
            inventory.setItem(4, createNamedItem(Material.BARRIER, unlockedOnly
                    ? message("custom_recipe_no_unlocked", "&c暂无已解锁配方")
                    : message("custom_recipe_no_recipes", "&c暂无权限配方")));
        }

        player.openInventory(inventory);
    }

    public void openRecipeEditor(Player player, String rawId) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage(message("custom_recipe_hold_result", "&c请先把要作为合成结果的物品拿在主手。"));
            return;
        }

        String id = normalizeRecipeId(rawId);
        if (id == null || id.isEmpty()) {
            id = normalizeRecipeId(getSimpleItemId(handItem));
        }
        if (id == null || id.isEmpty()) {
            player.sendMessage(message("custom_recipe_invalid_id", "&c配方 ID 无效。"));
            return;
        }

        ItemStack result = handItem.clone();
        PermissionRecipe existing = recipes.get(id);
        RecipeEditorHolder holder = new RecipeEditorHolder(id, result);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, color("&8" + GUI_TITLE_PREFIX + id));
        holder.setInventory(inventory);

        fillEditorInventory(inventory);
        if (existing != null) {
            for (int i = 0; i < EDIT_SLOTS.length; i++) {
                ItemStack ingredient = existing.ingredients[i];
                if (ingredient != null) {
                    inventory.setItem(EDIT_SLOTS[i], ingredient.clone());
                }
            }
            player.sendMessage(message("custom_recipe_loaded", "&a已载入旧配方，可直接修改后保存。"));
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onRecipeBookUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isRecipeBook(item)) {
            return;
        }

        event.setCancelled(true);
        openRecipeBook(event.getPlayer(), isUnlockedOnlyRecipeBook(item));
    }

    @EventHandler
    public void onRecipeBookDrop(PlayerDropItemEvent event) {
        if (isLockedRecipeBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRecipeBookSwap(PlayerSwapHandItemsEvent event) {
        if (isLockedRecipeBook(event.getMainHandItem()) || isLockedRecipeBook(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRecipeBookClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();

        if (holder instanceof RecipeBookHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            String recipeId = getRecipeDisplayId(clicked);
            if (recipeId != null && event.getWhoClicked() instanceof Player) {
                openRecipeDetail((Player) event.getWhoClicked(), recipeId, ((RecipeBookHolder) holder).unlockedOnly);
            }
            return;
        }

        if (holder instanceof RecipeDetailHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == SAVE_SLOT && event.getWhoClicked() instanceof Player) {
                openRecipeBook((Player) event.getWhoClicked(), ((RecipeDetailHolder) holder).unlockedOnly);
            }
            return;
        }

        if (isLockedRecipeBook(event.getCurrentItem()) || isLockedRecipeBook(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRecipeBookDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof RecipeBookHolder || holder instanceof RecipeDetailHolder) {
            event.setCancelled(true);
            return;
        }

        ItemStack oldCursor = event.getOldCursor();
        if (isLockedRecipeBook(oldCursor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEditorClick(InventoryClickEvent event) {
        RecipeEditorHolder holder = getEditorHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot < GUI_SIZE) {
            if (rawSlot == SAVE_SLOT) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player) {
                    saveEditorRecipe((Player) event.getWhoClicked(), holder);
                }
                return;
            }

            if (!isEditSlot(rawSlot)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEditorDrag(InventoryDragEvent event) {
        RecipeEditorHolder holder = getEditorHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < GUI_SIZE && !isEditSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        PermissionRecipe recipe = findMatchingRecipe(inventory.getMatrix());
        if (recipe == null) {
            return;
        }

        Player player = getCraftingPlayer(event);
        if (player == null || !canCraft(player, recipe, false)) {
            inventory.setResult(null);
            return;
        }

        inventory.setResult(recipe.result.clone());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }

        CraftingInventory inventory = event.getInventory();
        PermissionRecipe recipe = findMatchingRecipe(inventory.getMatrix());
        if (recipe == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (isShiftCraftClick(event.getClick())) {
            event.setCancelled(true);
            player.sendMessage(message("custom_recipe_no_shift", "&c权限配方不支持 Shift 批量合成，请单次点击合成。"));
            return;
        }

        if (!canCraft(player, recipe, true)) {
            event.setCancelled(true);
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItemComplete(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT || isShiftCraftClick(event.getClick())) {
            return;
        }

        CraftingInventory inventory = event.getInventory();
        PermissionRecipe recipe = findMatchingRecipe(inventory.getMatrix());
        if (recipe == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (!canCraft(player, recipe, false)) {
            return;
        }

        addUsage(player.getUniqueId(), recipe.id);
        Bukkit.getScheduler().runTask(plugin, () -> refreshCraftingResult(player, inventory));
    }

    private void openRecipeDetail(Player player, String recipeId, boolean unlockedOnly) {
        PermissionRecipe recipe = recipes.get(recipeId);
        if (recipe == null) {
            return;
        }
        if (unlockedOnly && !player.hasPermission(recipe.permission)) {
            return;
        }

        RecipeDetailHolder holder = new RecipeDetailHolder(recipeId, unlockedOnly);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, color(message("custom_recipe_detail_title", "&8配方详情: %id%").replace("%id%", recipe.id)));
        holder.setInventory(inventory);

        ItemStack locked = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, "&7");
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            inventory.setItem(slot, locked.clone());
        }
        for (int i = 0; i < EDIT_SLOTS.length; i++) {
            ItemStack ingredient = recipe.ingredients[i];
            inventory.setItem(EDIT_SLOTS[i], ingredient == null ? null : ingredient.clone());
        }
        inventory.setItem(SAVE_SLOT, createNamedItem(Material.ARROW, message("custom_recipe_back", "&a返回")));
        player.openInventory(inventory);
    }

    private void fillEditorInventory(Inventory inventory) {
        ItemStack locked = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, "&7已锁定");
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            inventory.setItem(slot, locked.clone());
        }
        for (int slot : EDIT_SLOTS) {
            inventory.setItem(slot, null);
        }
        inventory.setItem(SAVE_SLOT, createNamedItem(Material.LIME_WOOL, "&a保存配方"));
    }

    private void saveEditorRecipe(Player player, RecipeEditorHolder holder) {
        Inventory inventory = holder.getInventory();
        ItemStack[] ingredients = new ItemStack[9];
        boolean hasIngredient = false;

        for (int i = 0; i < EDIT_SLOTS.length; i++) {
            ItemStack item = inventory.getItem(EDIT_SLOTS[i]);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            ItemStack ingredient = item.clone();
            ingredient.setAmount(1);
            ingredients[i] = ingredient;
            hasIngredient = true;
        }

        if (!hasIngredient) {
            player.sendMessage(message("custom_recipe_empty", "&c请至少放入一个配方材料。"));
            return;
        }

        PermissionRecipe recipe = new PermissionRecipe(
                holder.id,
                getPermission(holder.id),
                Math.max(1, getDefaultMaxUses()),
                holder.result.clone(),
                ingredients
        );
        saveRecipe(recipe);
        recipes.put(recipe.id, recipe);
        player.closeInventory();
        player.sendMessage(message("custom_recipe_saved", "&a权限配方已保存: &e%id%").replace("%id%", recipe.id));
    }

    private PermissionRecipe loadRecipe(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = normalizeRecipeId(config.getString("id", stripExtension(file.getName())));
        if (id == null || id.isEmpty()) {
            plugin.getLogger().warning("跳过无效权限配方文件: " + file.getName());
            return null;
        }

        ItemStack result = config.getItemStack("result");
        if (result == null || result.getType() == Material.AIR) {
            plugin.getLogger().warning("权限配方缺少结果物品，已跳过: " + file.getName());
            return null;
        }

        ItemStack[] ingredients = new ItemStack[9];
        for (int i = 0; i < ingredients.length; i++) {
            ItemStack item = config.getItemStack("ingredients." + i + ".item");
            if (item != null && item.getType() != Material.AIR) {
                item.setAmount(1);
                ingredients[i] = item;
            }
        }

        String permission = config.getString("permission", getPermission(id));
        int maxUses = Math.max(1, config.getInt("max_uses", getDefaultMaxUses()));
        return new PermissionRecipe(id, permission, maxUses, result, ingredients);
    }

    private void saveRecipe(PermissionRecipe recipe) {
        File folder = getRecipeFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建权限配方目录: " + folder.getAbsolutePath());
            return;
        }

        File file = new File(folder, recipe.id + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", recipe.id);
        config.set("result_item_id", getItemId(recipe.result));
        config.set("permission", recipe.permission);
        config.set("max_uses", recipe.maxUses);
        config.set("result", recipe.result);

        for (int i = 0; i < recipe.ingredients.length; i++) {
            ItemStack ingredient = recipe.ingredients[i];
            if (ingredient == null) {
                config.set("ingredients." + i, null);
                continue;
            }
            config.set("ingredients." + i + ".item_id", getItemId(ingredient));
            config.set("ingredients." + i + ".item", ingredient);
        }

        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存权限配方失败 " + recipe.id + ": " + ex.getMessage());
        }
    }

    private PermissionRecipe findMatchingRecipe(ItemStack[] matrix) {
        if (matrix == null || matrix.length != 9) {
            return null;
        }

        for (PermissionRecipe recipe : recipes.values()) {
            if (matchesRecipe(matrix, recipe)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean matchesRecipe(ItemStack[] matrix, PermissionRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack required = recipe.ingredients[i];
            ItemStack actual = matrix[i];
            boolean requiresItem = required != null && required.getType() != Material.AIR;
            boolean hasItem = actual != null && actual.getType() != Material.AIR;

            if (!requiresItem && !hasItem) {
                continue;
            }
            if (requiresItem != hasItem) {
                return false;
            }
            if (!actual.isSimilar(required)) {
                return false;
            }
        }
        return true;
    }

    private boolean canCraft(Player player, PermissionRecipe recipe, boolean sendMessage) {
        if (!player.hasPermission(recipe.permission)) {
            if (sendMessage) {
                player.sendMessage(message("custom_recipe_no_permission", "&c你没有权限使用该合成配方。"));
            }
            return false;
        }

        int used = getUsage(player.getUniqueId(), recipe.id);
        if (used >= recipe.maxUses) {
            if (sendMessage) {
                player.sendMessage(message("custom_recipe_usage_limit", "&c该配方最多只能合成 %max% 次。")
                        .replace("%max%", String.valueOf(recipe.maxUses)));
            }
            return false;
        }
        return true;
    }

    private void registerBukkitRecipes() {
        for (PermissionRecipe recipe : recipes.values()) {
            NamespacedKey key = createRecipeKey(recipe.id);
            Bukkit.removeRecipe(key);

            try {
                ShapedRecipe shapedRecipe = new ShapedRecipe(key, recipe.result.clone());
                shapedRecipe.shape(createRecipeShape(recipe));
                for (int i = 0; i < recipe.ingredients.length; i++) {
                    ItemStack ingredient = recipe.ingredients[i];
                    if (ingredient == null || ingredient.getType() == Material.AIR) {
                        continue;
                    }
                    ItemStack choiceItem = ingredient.clone();
                    choiceItem.setAmount(1);
                    shapedRecipe.setIngredient(getRecipeSymbol(i), new RecipeChoice.ExactChoice(choiceItem));
                }

                Bukkit.addRecipe(shapedRecipe);
                registeredRecipeKeys.add(key);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                plugin.getLogger().warning("注册权限配方失败 " + recipe.id + ": " + ex.getMessage());
            }
        }
    }

    public void unregisterBukkitRecipes() {
        for (NamespacedKey key : registeredRecipeKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredRecipeKeys.clear();
    }

    private String[] createRecipeShape(PermissionRecipe recipe) {
        String[] rows = new String[3];
        for (int row = 0; row < 3; row++) {
            StringBuilder builder = new StringBuilder(3);
            for (int column = 0; column < 3; column++) {
                int index = row * 3 + column;
                ItemStack ingredient = recipe.ingredients[index];
                builder.append(ingredient == null || ingredient.getType() == Material.AIR ? ' ' : getRecipeSymbol(index));
            }
            rows[row] = builder.toString();
        }
        return rows;
    }

    private char getRecipeSymbol(int index) {
        return (char) ('A' + index);
    }

    private NamespacedKey createRecipeKey(String id) {
        return new NamespacedKey(plugin, "permission_recipe_" + id);
    }

    private boolean isShiftCraftClick(ClickType click) {
        return click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
    }

    private void refreshCraftingResult(Player player, CraftingInventory inventory) {
        if (!player.isOnline()) {
            return;
        }

        PermissionRecipe nextRecipe = findMatchingRecipe(inventory.getMatrix());
        inventory.setResult(nextRecipe != null && canCraft(player, nextRecipe, false) ? nextRecipe.result.clone() : null);
        player.updateInventory();
    }

    private List<PermissionRecipe> getUnlockedRecipes(Player player) {
        List<PermissionRecipe> unlocked = new ArrayList<>();
        for (PermissionRecipe recipe : recipes.values()) {
            if (player.hasPermission(recipe.permission)) {
                unlocked.add(recipe);
            }
        }
        return unlocked;
    }

    private ItemStack createRecipeBook(boolean unlockedOnly) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(unlockedOnly
                    ? message("custom_recipe_unlocked_book_name", "&a已解锁配方书")
                    : message("custom_recipe_book_name", "&d合成配方")));
            List<String> lore = new ArrayList<>();
            lore.add(color(unlockedOnly
                    ? message("custom_recipe_unlocked_book_lore", "&7右键查看已解锁的权限配方")
                    : message("custom_recipe_book_lore", "&7右键查看所有权限配方与解锁状态")));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(recipeBookKey, PersistentDataType.BYTE, unlockedOnly ? (byte) 2 : (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isRecipeBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(recipeBookKey, PersistentDataType.BYTE);
    }

    private boolean isUnlockedOnlyRecipeBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(recipeBookKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 2;
    }

    private boolean hasUnlockedRecipeBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isUnlockedOnlyRecipeBook(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLockedRecipeBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(recipeBookKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private ItemStack createRecipeDisplayItem(Player player, PermissionRecipe recipe) {
        ItemStack item = recipe.result.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            if (!lore.isEmpty()) {
                lore.add(color("&8&m----------------"));
            }
            boolean unlocked = player.hasPermission(recipe.permission);
            int used = getUsage(player.getUniqueId(), recipe.id);
            lore.add(color(unlocked ? "&a状态: 已解锁" : "&c状态: 未解锁"));
            lore.add(color("&7可使用次数: &e" + Math.max(0, recipe.maxUses - used) + "&7/&e" + recipe.maxUses));
            lore.add(color("&e点击查看摆放方式"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(recipeDisplayKey, PersistentDataType.STRING, recipe.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getRecipeDisplayId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.get(recipeDisplayKey, PersistentDataType.STRING);
    }

    private Player getCraftingPlayer(PrepareItemCraftEvent event) {
        List<HumanEntity> viewers = event.getViewers();
        if (viewers.isEmpty() || !(viewers.get(0) instanceof Player)) {
            return null;
        }
        return (Player) viewers.get(0);
    }

    private RecipeEditorHolder getEditorHolder(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof RecipeEditorHolder ? (RecipeEditorHolder) holder : null;
    }

    private boolean isEditSlot(int slot) {
        for (int editSlot : EDIT_SLOTS) {
            if (editSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createNamedItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(displayName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int getUsage(UUID playerId, String recipeId) {
        return usageCounts.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(recipeId, 0);
    }

    private void addUsage(UUID playerId, String recipeId) {
        usageCounts.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .merge(recipeId, 1, Integer::sum);
    }

    private int getDefaultMaxUses() {
        return Math.max(1, plugin.getConfig().getInt("game.custom_recipes.max_uses", 3));
    }

    private String getPermission(String id) {
        return "huntergame.recipe." + id;
    }

    private File getRecipeFolder() {
        return new File(plugin.getDataFolder(), RECIPE_FOLDER);
    }

    private String normalizeRecipeId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(':', '_');
        normalized = normalized.replaceAll("[^a-z0-9_-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        while (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String getSimpleItemId(ItemStack item) {
        NamespacedKey key = item.getType().getKey();
        if ("minecraft".equals(key.getNamespace())) {
            return key.getKey();
        }
        return key.getNamespace() + "_" + key.getKey();
    }

    private String getItemId(ItemStack item) {
        return item.getType().getKey().toString();
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String message(String key, String fallback) {
        return plugin.getMessage(key, fallback);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static final class RecipeEditorHolder implements InventoryHolder {
        private final String id;
        private final ItemStack result;
        private Inventory inventory;

        private RecipeEditorHolder(String id, ItemStack result) {
            this.id = id;
            this.result = result;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class RecipeBookHolder implements InventoryHolder {
        private final boolean unlockedOnly;
        private Inventory inventory;

        private RecipeBookHolder(boolean unlockedOnly) {
            this.unlockedOnly = unlockedOnly;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class RecipeDetailHolder implements InventoryHolder {
        private final String recipeId;
        private final boolean unlockedOnly;
        private Inventory inventory;

        private RecipeDetailHolder(String recipeId, boolean unlockedOnly) {
            this.recipeId = recipeId;
            this.unlockedOnly = unlockedOnly;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PermissionRecipe {
        private final String id;
        private final String permission;
        private final int maxUses;
        private final ItemStack result;
        private final ItemStack[] ingredients;

        private PermissionRecipe(String id, String permission, int maxUses, ItemStack result, ItemStack[] ingredients) {
            this.id = id;
            this.permission = permission;
            this.maxUses = maxUses;
            this.result = result;
            this.ingredients = ingredients;
        }
    }
}
package com.huntergame.world;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OnlineWorldResetManager {
    private static final String ROOT = "game.shutdown_reset";
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String PENDING_RESET_FILE = "pending-shutdown-world-reset.txt";

    private final HunterGame plugin;
    private final Deque<WorldResetTarget> resetQueue = new ArrayDeque<>();
    private final Map<String, Integer> attempts = new HashMap<>();
    private final Set<String> failedWorlds = new LinkedHashSet<>();
    private final List<WorldResetTarget> deferredShutdownTargets = new ArrayList<>();
    private boolean resetting = false;
    private boolean shutdownHookRegistered = false;

    public OnlineWorldResetManager(HunterGame plugin) {
        this.plugin = plugin;
    }

    public boolean isResetting() {
        return resetting;
    }

    public void startReset() {
        if (resetting) {
            return;
        }

        resetting = true;
        attempts.clear();
        failedWorlds.clear();
        deferredShutdownTargets.clear();
        shutdownHookRegistered = false;
        plugin.setResetInProgress(true);
        Bukkit.broadcastMessage(plugin.getMessage("shutdown_reset_started", "&c服务器正在重置地图并关闭..."));

        transferPlayersToLobby();
        long kickDelayTicks = Math.max(1L, getLong("player_kick_delay_ticks", 100L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            kickRemainingPlayers();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.prepareForOnlineWorldReset();
                resetQueue.clear();
                resetQueue.addAll(loadWorldTargets());
                plugin.getLogger().info("关服地图重置队列: " + resetQueue.stream()
                        .map(target -> target.name)
                        .collect(Collectors.joining(", ")));
                resetNextWorld();
            }, Math.max(1L, getLong("post_kick_prepare_delay_ticks", 20L)));
        }, kickDelayTicks);
    }

    private void transferPlayersToLobby() {
        String server = getConfig().getString("BungeeCord.server_lobby", "lobby");
        if (server == null || server.isBlank()) {
            return;
        }

        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeUTF("Connect");
                out.writeUTF(server);
                player.sendPluginMessage(plugin, BUNGEE_CHANNEL, bytes.toByteArray());
            } catch (IOException ex) {
                plugin.getLogger().warning("发送跨服传送请求失败: " + player.getName() + " -> " + ex.getMessage());
            }
        }
    }

    private void kickRemainingPlayers() {
        String message = plugin.getMessage("shutdown_reset_kick_message", "&c服务器正在重置地图并关闭，请稍后再加入");
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            player.kickPlayer(message);
        }
    }

    private void resetNextWorld() {
        WorldResetTarget target = resetQueue.pollFirst();
        if (target == null) {
            finishResetAndShutdown();
            return;
        }

        plugin.getLogger().info("开始关服重置世界: " + target.name);
        World world = Bukkit.getWorld(target.name);
        if (world != null) {
            if (!world.getPlayers().isEmpty()) {
                kickPlayersInWorld(world);
                retryWorld(target, "仍有玩家停留");
                return;
            }

            world.setAutoSave(false);
            if (!Bukkit.unloadWorld(world, false)) {
                if (isPrimaryWorld(world)) {
                    deferWorldForShutdown(target, "主世界无法在关服前卸载");
                    scheduleNextWorld();
                    return;
                }
                retryWorld(target, "卸载失败");
                return;
            }
        }

        deleteWorldFolderAsync(target);
    }

    private void kickPlayersInWorld(World world) {
        String message = plugin.getMessage("shutdown_reset_kick_message", "&c服务器正在重置地图并关闭，请稍后再加入");
        for (Player player : new ArrayList<>(world.getPlayers())) {
            player.kickPlayer(message);
        }
    }

    private void deleteWorldFolderAsync(WorldResetTarget target) {
        File folder = new File(Bukkit.getWorldContainer(), target.name);
        if (!isSafeWorldFolder(folder)) {
            markWorldFailed(target, "目录安全检查失败: " + folder.getAbsolutePath());
            scheduleNextWorld();
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean deleted = deleteWorldFolder(target, folder);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!deleted) {
                    retryWorld(target, "文件夹删除失败");
                    return;
                }

                plugin.getLogger().info("世界目录已清理: " + target.name);
                attempts.remove(target.name);
                scheduleNextWorld();
            });
        });
    }

    private boolean deleteWorldFolder(WorldResetTarget target, File folder) {
        return deleteWorldFolder(folder, target.preserveDatapacks, plugin.getLogger());
    }

    private void retryWorld(WorldResetTarget target, String reason) {
        int attempt = attempts.merge(target.name, 1, Integer::sum);
        int maxAttempts = Math.max(0, getInt("world_retry_times", 5));
        if (attempt <= maxAttempts) {
            long delayTicks = Math.max(1L, getLong("world_retry_delay_ticks", 40L));
            plugin.getLogger().warning("世界关服重置暂未完成，将重试: " + target.name
                    + "，原因: " + reason
                    + "，次数: " + attempt + "/" + maxAttempts);
            resetQueue.addFirst(target);
            Bukkit.getScheduler().runTaskLater(plugin, this::resetNextWorld, delayTicks);
            return;
        }

        markWorldFailed(target, reason + "，超过重试上限 " + maxAttempts);
        scheduleNextWorld();
    }

    private void markWorldFailed(WorldResetTarget target, String reason) {
        failedWorlds.add(target.name);
        plugin.getLogger().severe("世界关服重置失败: " + target.name + "，原因: " + reason);
    }

    private void scheduleNextWorld() {
        long delayTicks = Math.max(1L, getLong("world_step_delay_ticks", 60L));
        Bukkit.getScheduler().runTaskLater(plugin, this::resetNextWorld, delayTicks);
    }

    private void finishResetAndShutdown() {
        resetting = false;
        if (!deferredShutdownTargets.isEmpty()) {
            writePendingResetFile(deferredShutdownTargets);
            registerShutdownCleanupHook(deferredShutdownTargets);
        }

        if (failedWorlds.isEmpty() && deferredShutdownTargets.isEmpty()) {
            plugin.getLogger().info("地图文件已清理完成，服务器即将关闭。");
        } else if (failedWorlds.isEmpty()) {
            plugin.getLogger().warning("部分地图将在服务器进程退出后清理: "
                    + deferredShutdownTargets.stream().map(target -> target.name).collect(Collectors.joining(", "))
                    + "。服务器即将关闭。");
        } else {
            plugin.getLogger().severe("部分地图删除失败: " + String.join(", ", failedWorlds) + "。服务器仍将关闭。请检查文件占用后手动处理。");
        }
        Bukkit.shutdown();
    }

    private List<WorldResetTarget> loadWorldTargets() {
        List<WorldResetTarget> targets = new ArrayList<>();
        for (Map<?, ?> map : getConfig().getMapList(ROOT + ".worlds")) {
            Object nameValue = map.get("name");
            String name = nameValue == null ? "" : String.valueOf(nameValue).trim();
            if (name.isEmpty()) {
                continue;
            }

            Object environmentValue = map.get("environment");
            String environmentName = environmentValue == null ? "NORMAL" : String.valueOf(environmentValue);
            World.Environment environment = parseEnvironment(environmentName);
            Object preserveValue = map.get("preserve_datapacks");
            boolean preserveDatapacks = preserveValue != null && Boolean.parseBoolean(String.valueOf(preserveValue));
            targets.add(new WorldResetTarget(name, environment, preserveDatapacks));
        }

        if (targets.isEmpty()) {
            targets.add(new WorldResetTarget("world", World.Environment.NORMAL, true));
            targets.add(new WorldResetTarget("world_nether", World.Environment.NETHER, false));
            targets.add(new WorldResetTarget("world_the_end", World.Environment.THE_END, false));
        }
        return targets;
    }

    private World.Environment parseEnvironment(String raw) {
        try {
            return World.Environment.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("无效世界环境 " + raw + "，已使用 NORMAL。");
            return World.Environment.NORMAL;
        }
    }

    private boolean isSafeWorldFolder(File folder) {
        return isSafeWorldFolder(Bukkit.getWorldContainer(), folder, plugin.getLogger());
    }

    private boolean isPrimaryWorld(World world) {
        List<World> worlds = Bukkit.getWorlds();
        return !worlds.isEmpty() && worlds.get(0).getUID().equals(world.getUID());
    }

    private void deferWorldForShutdown(WorldResetTarget target, String reason) {
        boolean exists = deferredShutdownTargets.stream().anyMatch(existing -> existing.name.equals(target.name));
        if (!exists) {
            deferredShutdownTargets.add(target);
        }
        attempts.remove(target.name);
        plugin.getLogger().warning("世界将延后到服务器进程退出后清理: " + target.name + "，原因: " + reason);
    }

    private void writePendingResetFile(List<WorldResetTarget> targets) {
        File pendingFile = getPendingResetFile(plugin);
        File parent = pendingFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        List<String> lines = targets.stream()
                .map(target -> target.name + "\t" + target.preserveDatapacks)
                .collect(Collectors.toList());
        try {
            Files.write(pendingFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("写入关服待清理地图列表失败: " + ex.getMessage());
        }
    }

    private void registerShutdownCleanupHook(List<WorldResetTarget> targets) {
        if (shutdownHookRegistered) {
            return;
        }

        shutdownHookRegistered = true;
        List<WorldResetTarget> targetsSnapshot = new ArrayList<>(targets);
        File worldContainer = Bukkit.getWorldContainer();
        File pendingFile = getPendingResetFile(plugin);
        Logger logger = plugin.getLogger();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            List<WorldResetTarget> failed = cleanupWorldFoldersWithRetries(
                    worldContainer,
                    targetsSnapshot,
                    logger,
                    20,
                    500L
            );
            if (failed.isEmpty()) {
                try {
                    Files.deleteIfExists(pendingFile.toPath());
                } catch (IOException ex) {
                    logger.warning("删除关服待清理地图列表失败: " + ex.getMessage());
                }
            }
        }, "HunterGame-ShutdownWorldReset"));
    }

    public static void cleanupPendingShutdownResets(HunterGame plugin) {
        File pendingFile = getPendingResetFile(plugin);
        if (!pendingFile.exists()) {
            return;
        }

        List<WorldResetTarget> targets = readPendingResetFile(pendingFile, plugin.getLogger());
        if (targets.isEmpty()) {
            try {
                Files.deleteIfExists(pendingFile.toPath());
            } catch (IOException ex) {
                plugin.getLogger().warning("删除空的关服待清理地图列表失败: " + ex.getMessage());
            }
            return;
        }

        plugin.getLogger().warning("发现上次关服遗留的地图清理任务: "
                + targets.stream().map(target -> target.name).collect(Collectors.joining(", ")));
        List<WorldResetTarget> failed = cleanupWorldFoldersWithRetries(
                Bukkit.getWorldContainer(),
                targets,
                plugin.getLogger(),
                10,
                300L
        );
        if (failed.isEmpty()) {
            try {
                Files.deleteIfExists(pendingFile.toPath());
            } catch (IOException ex) {
                plugin.getLogger().warning("删除关服待清理地图列表失败: " + ex.getMessage());
            }
            plugin.getLogger().info("上次关服遗留的地图清理任务已完成。");
            return;
        }

        rewritePendingResetFile(pendingFile, failed, plugin.getLogger());
        plugin.getLogger().severe("上次关服遗留的地图仍未清理完成: "
                + failed.stream().map(target -> target.name).collect(Collectors.joining(", ")));
    }

    private static File getPendingResetFile(HunterGame plugin) {
        return new File(plugin.getDataFolder(), PENDING_RESET_FILE);
    }

    private static List<WorldResetTarget> readPendingResetFile(File pendingFile, Logger logger) {
        List<WorldResetTarget> targets = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(pendingFile.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] parts = trimmed.split("\\t", 2);
                String name = parts[0].trim();
                if (name.isEmpty()) {
                    continue;
                }
                boolean preserveDatapacks = parts.length > 1 && Boolean.parseBoolean(parts[1].trim());
                targets.add(new WorldResetTarget(name, World.Environment.NORMAL, preserveDatapacks));
            }
        } catch (IOException ex) {
            logger.warning("读取关服待清理地图列表失败: " + ex.getMessage());
        }
        return targets;
    }

    private static void rewritePendingResetFile(File pendingFile, List<WorldResetTarget> targets, Logger logger) {
        List<String> lines = targets.stream()
                .map(target -> target.name + "\t" + target.preserveDatapacks)
                .collect(Collectors.toList());
        try {
            Files.write(pendingFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logger.warning("更新关服待清理地图列表失败: " + ex.getMessage());
        }
    }

    private static List<WorldResetTarget> cleanupWorldFoldersWithRetries(
            File worldContainer,
            List<WorldResetTarget> targets,
            Logger logger,
            int attempts,
            long sleepMillis
    ) {
        List<WorldResetTarget> failed = new ArrayList<>();
        for (WorldResetTarget target : targets) {
            File folder = new File(worldContainer, target.name);
            if (!isSafeWorldFolder(worldContainer, folder, logger)) {
                failed.add(target);
                continue;
            }

            boolean deleted = false;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                if (deleteWorldFolder(folder, target.preserveDatapacks, logger)) {
                    deleted = true;
                    logger.info("延后地图目录已清理: " + target.name);
                    break;
                }

                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!deleted) {
                failed.add(target);
            }
        }
        return failed;
    }

    private static boolean deleteWorldFolder(File folder, boolean preserveDatapacks, Logger logger) {
        if (!folder.exists()) {
            return true;
        }

        Path folderPath = folder.toPath();
        Path preservedDatapacks = preserveDatapacks ? folderPath.resolve("datapacks") : null;

        try {
            Files.walkFileTree(folderPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (preservedDatapacks != null && dir.equals(preservedDatapacks)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (preservedDatapacks != null && file.startsWith(preservedDatapacks)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    if (preservedDatapacks != null && (dir.equals(folderPath) || dir.equals(preservedDatapacks))) {
                        return FileVisitResult.CONTINUE;
                    }
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException ex) {
            logger.warning("删除世界文件夹失败 " + folder.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private static boolean isSafeWorldFolder(File worldContainer, File folder, Logger logger) {
        try {
            Path container = worldContainer.getCanonicalFile().toPath();
            Path target = folder.getCanonicalFile().toPath();
            return target.startsWith(container) && !target.equals(container);
        } catch (IOException ex) {
            logger.warning("检查世界目录安全性失败: " + ex.getMessage());
            return false;
        }
    }

    private int getInt(String key, int fallback) {
        return getConfig().getInt(ROOT + "." + key, getConfig().getInt("game.online_reset." + key, fallback));
    }

    private long getLong(String key, long fallback) {
        return getConfig().getLong(ROOT + "." + key, getConfig().getLong("game.online_reset." + key, fallback));
    }

    private FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    private static final class WorldResetTarget {
        private final String name;
        private final World.Environment environment;
        private final boolean preserveDatapacks;

        private WorldResetTarget(String name, World.Environment environment, boolean preserveDatapacks) {
            this.name = name;
            this.environment = environment;
            this.preserveDatapacks = preserveDatapacks;
        }
    }
}

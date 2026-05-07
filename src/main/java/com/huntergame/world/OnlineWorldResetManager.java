package com.huntergame.world;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OnlineWorldResetManager {
    private static final String ROOT = "game.online_reset";
    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private final HunterGame plugin;
    private final Deque<WorldResetTarget> resetQueue = new ArrayDeque<>();
    private boolean resetting = false;

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
        plugin.setResetInProgress(true);
        Bukkit.broadcastMessage(plugin.getMessage("online_reset_started", "&c服务器进入重置中..."));

        transferPlayersToLobby();
        long kickDelayTicks = Math.max(1L, getConfig().getLong(ROOT + ".player_kick_delay_ticks", 40L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            kickRemainingPlayers();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.prepareForOnlineWorldReset();
                resetQueue.clear();
                resetQueue.addAll(loadWorldTargets());
                resetNextWorld();
            }, Math.max(1L, getConfig().getLong(ROOT + ".post_kick_prepare_delay_ticks", 20L)));
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
        String message = plugin.getMessage("online_reset_kick_message", "&c服务器正在重置地图，请稍后再加入");

        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            player.kickPlayer(message);
        }
    }

    private void resetNextWorld() {
        WorldResetTarget target = resetQueue.pollFirst();
        if (target == null) {
            finishReset();
            return;
        }

        World world = Bukkit.getWorld(target.name);
        if (world != null) {
            if (!world.getPlayers().isEmpty()) {
                kickPlayersInWorld(world);
                resetQueue.addFirst(target);
                Bukkit.getScheduler().runTaskLater(plugin, this::resetNextWorld, 20L);
                return;
            }

            world.setAutoSave(false);
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().warning("世界卸载失败，已跳过本轮重置: " + target.name);
                scheduleNextWorld();
                return;
            }
        }

        deleteWorldFolderThenCreate(target);
    }

    private void kickPlayersInWorld(World world) {
        String message = plugin.getMessage("online_reset_kick_message", "&c服务器正在重置地图，请稍后再加入");
        for (Player player : new ArrayList<>(world.getPlayers())) {
            player.kickPlayer(message);
        }
    }

    private void deleteWorldFolderThenCreate(WorldResetTarget target) {
        File folder = new File(Bukkit.getWorldContainer(), target.name);
        if (!isSafeWorldFolder(folder)) {
            plugin.getLogger().severe("拒绝删除异常世界目录: " + folder.getAbsolutePath());
            scheduleNextWorld();
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean deleted = deleteWorldFolder(folder);
            Bukkit.getScheduler().runTask(plugin, () -> createWorldAndContinue(target, deleted));
        });
    }

    private void createWorldAndContinue(WorldResetTarget target, boolean deleted) {
        if (!deleted) {
            plugin.getLogger().warning("世界文件夹删除失败，跳过重新创建: " + target.name);
            scheduleNextWorld();
            return;
        }

        WorldCreator creator = new WorldCreator(target.name);
        creator.environment(target.environment);
        creator.generateStructures(true);

        World created = Bukkit.createWorld(creator);
        if (created == null) {
            plugin.getLogger().warning("世界重新创建失败: " + target.name);
        } else {
            created.setAutoSave(true);
            plugin.getLogger().info("世界已重新生成: " + target.name);
        }

        scheduleNextWorld();
    }

    private void scheduleNextWorld() {
        long delayTicks = Math.max(1L, getConfig().getLong(ROOT + ".world_step_delay_ticks", 40L));
        Bukkit.getScheduler().runTaskLater(plugin, this::resetNextWorld, delayTicks);
    }

    private void finishReset() {
        resetting = false;
        plugin.completeOnlineWorldReset();
        plugin.getLogger().info("在线世界重置完成。");
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
            targets.add(new WorldResetTarget(name, environment));
        }

        if (targets.isEmpty()) {
            targets.add(new WorldResetTarget("world", World.Environment.NORMAL));
            targets.add(new WorldResetTarget("world_nether", World.Environment.NETHER));
            targets.add(new WorldResetTarget("world_the_end", World.Environment.THE_END));
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
        try {
            Path container = Bukkit.getWorldContainer().getCanonicalFile().toPath();
            Path target = folder.getCanonicalFile().toPath();
            return target.startsWith(container) && !target.equals(container);
        } catch (IOException ex) {
            plugin.getLogger().warning("检查世界目录安全性失败: " + ex.getMessage());
            return false;
        }
    }

    private boolean deleteWorldFolder(File folder) {
        if (!folder.exists()) {
            return true;
        }

        try {
            Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("删除世界文件夹失败 " + folder.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    private static final class WorldResetTarget {
        private final String name;
        private final World.Environment environment;

        private WorldResetTarget(String name, World.Environment environment) {
            this.name = name;
            this.environment = environment;
        }
    }
}

package com.huntergame;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetLobbyCommand {
    private final HunterGame plugin;

    public SetLobbyCommand(HunterGame plugin) {
        this.plugin = plugin;
    }


   public void setLobby(CommandSender sender){
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("non_player", "只有玩家才能执行这个命令！"));
            return;
        }

        Player player = (Player) sender;
        // 获取玩家当前坐标
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            player.sendMessage(plugin.getMessage("world_not_found", "&c无法获取当前位置的世界！"));
            return;
        }

        // 保存大厅位置到配置文件
        plugin.getConfig().set("lobby.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.x", location.getX());
        plugin.getConfig().set("lobby.y", location.getY());
        plugin.getConfig().set("lobby.z", location.getZ());
        plugin.saveConfig();

        // 反馈给玩家
        player.sendMessage(plugin.getMessage("set_lobby", "&a大厅位置已成功设置为：\n &7世界: %world% \n &7坐标: %coordinates%")
                .replace("%world%", location.getWorld().getName())
                .replace("%coordinates%", String.format("%.2f, %.2f, %.2f", location.getX(), location.getY(), location.getZ())));
    }

}

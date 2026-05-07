package com.huntergame.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

public class WorldBorderManager {

    public static void setupWorldBorder() {
        // 获取名为 "world" 的世界
        World world = Bukkit.getWorld("world");
        if (world == null) {
            Bukkit.getLogger().warning("找不到名为 'world' 的世界！");
            return;
        }

        // 获取世界边界对象并配置
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);      // 设置边界中心点 (默认在 0,0)
        border.setSize(10000);         // 设置边界大小为 4000x4000
        border.setDamageAmount(2);   // 设置边界外伤害为 0（可选）
        border.setWarningDistance(0); // 边界警告距离（可选）
    }
}

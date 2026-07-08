package com.huntergame.util;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class BedrockSupport {
    private BedrockSupport() {
    }

    public static boolean isBedrockPlayer(HunterGame plugin, Player player) {
        if (plugin == null || player == null || !isFloodgateEnabled()) {
            return false;
        }

        try {
            return FloodgateSupport.isBedrockPlayer(player);
        } catch (IllegalStateException | LinkageError ex) {
            return false;
        }
    }

    public static boolean isFloodgateEnabled() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if ("floodgate".equalsIgnoreCase(plugin.getName()) && plugin.isEnabled()) {
                return true;
            }
        }
        return false;
    }
}


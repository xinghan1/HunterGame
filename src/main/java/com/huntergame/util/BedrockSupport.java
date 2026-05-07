package com.huntergame.util;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class BedrockSupport {
    private BedrockSupport() {
    }

    public static boolean isBedrockPlayer(HunterGame plugin, Player player) {
        if (plugin == null || player == null || plugin.getBaseAPI() == null || !isFloodgateEnabled()) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Object result = isFloodgatePlayer.invoke(api, player.getUniqueId());
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    private static boolean isFloodgateEnabled() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if ("floodgate".equalsIgnoreCase(plugin.getName()) && plugin.isEnabled()) {
                return true;
            }
        }
        return false;
    }
}


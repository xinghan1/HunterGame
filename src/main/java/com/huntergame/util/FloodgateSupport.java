package com.huntergame.util;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

public final class FloodgateSupport {
    private FloodgateSupport() {
    }

    public static boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }

        FloodgateApi api = FloodgateApi.getInstance();
        return api != null && api.isFloodgatePlayer(player.getUniqueId());
    }

    public static boolean sendForm(Player player, SimpleForm.Builder builder) {
        if (player == null || builder == null || !BedrockSupport.isFloodgateEnabled()) {
            return false;
        }

        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api != null && api.sendForm(player.getUniqueId(), builder);
        } catch (IllegalStateException | LinkageError ex) {
            return false;
        }
    }
}

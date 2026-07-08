package com.xigua.baseAPI;

import com.xigua.cumulus.form.SimpleForm;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public class BaseAPI extends JavaPlugin {
   public void sendForm(UUID playerId, SimpleForm.Builder builder) {
   }

   public void notifyToClient(Object player, String channel, String module, String action, Map<String, Object> eventData) {
   }
}

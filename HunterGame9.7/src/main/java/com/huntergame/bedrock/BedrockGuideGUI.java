package com.huntergame.bedrock;

import com.huntergame.HunterGame;
import com.huntergame.gui.GuideGUI;
import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BedrockGuideGUI {
   public static void openBedrockGuide(HunterGame plugin, List<GuideGUI.GuideItem> guideItems, String guiTitle, Player player) {
      BaseAPI baseAPI = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
      if (baseAPI != null) {
         StringBuilder contentBuilder = new StringBuilder();

         for(GuideGUI.GuideItem item : guideItems) {
            contentBuilder.append(item.name).append("\n");
            if (item.lore != null && !item.lore.isEmpty()) {
               for(String line : item.lore) {
                  contentBuilder.append(line).append("\n");
               }
            }

            contentBuilder.append(plugin.getMessage("bedrock_guide_separator", "\n&8------------------------\n\n"));
         }

         SimpleForm.Builder builder = ((SimpleForm.Builder)SimpleForm.builder().title(guiTitle)).content(contentBuilder.toString()).button(plugin.getMessage("bedrock_guide_close_button", "&c\u5173\u95ed\u754c\u9762"));
         baseAPI.sendForm(player.getUniqueId(), builder);
      }
   }
}

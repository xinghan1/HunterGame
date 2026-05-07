package com.huntergame.bedrock;

import com.huntergame.gui.GuideGUI;
import com.huntergame.HunterGame;
import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class BedrockGuideGUI {

    public static void openBedrockGuide(HunterGame plugin, List<GuideGUI.GuideItem> guideItems, String guiTitle, Player player) {
        BaseAPI baseAPI = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (baseAPI == null) {
            return;
        }

        // 构建长文本内容
        StringBuilder contentBuilder = new StringBuilder();
        // 遍历所有配置好的物品
        for (GuideGUI.GuideItem item : guideItems) {
            contentBuilder.append(item.name).append("\n");
            // 拼接介绍内容
            if (item.lore != null && !item.lore.isEmpty()) {
                for (String line : item.lore) {
                    contentBuilder.append(line).append("\n");
                }
            }
            contentBuilder.append(plugin.getMessage("bedrock_guide_separator", "\n&8------------------------\n\n"));
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(guiTitle)
                .content(contentBuilder.toString())
                .button(plugin.getMessage("bedrock_guide_close_button", "&c关闭界面"));
        baseAPI.sendForm(player.getUniqueId(), builder);
    }
}


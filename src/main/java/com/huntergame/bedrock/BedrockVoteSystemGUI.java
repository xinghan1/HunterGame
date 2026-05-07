package com.huntergame.bedrock;

import com.huntergame.HunterGame;
import com.huntergame.vote.VoteSystem;
import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class BedrockVoteSystemGUI {

    /**
     * 打开基岩版投票主菜单
     */
    public static void openBedrockVoteMenu(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, HunterGame plugin, Player player) {
        BaseAPI baseAPI = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (baseAPI == null) {
            return;
        }

        if (voteSystem.isFixedModeEnabled()) {
            Object[] fixedModeInfo = voteSystem.getFixedModeInfo();
            voteSystem.recordPlayerModeChoice(player, (int) fixedModeInfo[0]);
            // 固定模式下只显示角色和胜利条件投票
            SimpleForm.Builder builder = SimpleForm.builder()
                    .title(plugin.getMessage("bedrock_vote_menu_title", "&c&l游戏投票"))
                    .content(plugin.getMessage("bedrock_vote_menu_content", "请选择你的投票："))
                    .button(plugin.getMessage("bedrock_vote_role_button", "&a投票游戏角色"))
                    .button(plugin.getMessage("bedrock_vote_type_button", "&6投票胜利条件"))
                    .button(plugin.getMessage("bedrock_close_button", "&c关闭菜单"))
                    .validResultHandler(response -> {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                switch (response.clickedButtonId()) {
                                    case 0:
                                        openBedrockRoleVote(voteSystem, voteGuiConfig, plugin, player);
                                        break;
                                    case 1:
                                        openBedrockTypeVote(voteSystem, voteGuiConfig, plugin, player);
                                        break;
                                    case 2:
                                        break;
                                }
                            }
                        }.runTask(plugin);
                    });
            baseAPI.sendForm(player.getUniqueId(), builder);
        } else {
            SimpleForm.Builder builder = SimpleForm.builder()
                    .title(plugin.getMessage("bedrock_vote_menu_title", "&c&l游戏投票"))
                    .content(plugin.getMessage("bedrock_vote_menu_content", "请选择你的投票："))
                    .button(plugin.getMessage("bedrock_vote_mode_button", "&e投票游戏模式"))
                    .button(plugin.getMessage("bedrock_vote_role_button", "&a投票游戏角色"))
                    .button(plugin.getMessage("bedrock_vote_type_button", "&6投票胜利条件"))
                    .button(plugin.getMessage("bedrock_close_button", "&c关闭菜单"))
                    .validResultHandler(response -> {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                switch (response.clickedButtonId()) {
                                    case 0:
                                        openBedrockModeVote(voteSystem, voteGuiConfig, plugin, player);
                                        break;
                                    case 1:
                                        openBedrockRoleVote(voteSystem, voteGuiConfig, plugin, player);
                                        break;
                                    case 2:
                                        openBedrockTypeVote(voteSystem, voteGuiConfig, plugin, player);
                                        break;
                                    case 3:
                                        break;
                                }
                            }
                        }.runTask(plugin);
                    });
            baseAPI.sendForm(player.getUniqueId(), builder);
        }
    }

    /**
     * 注意：这里改为 static 方法
     */
    public static void openBedrockModeVote(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, HunterGame plugin, Player player) {
        BaseAPI spigotMaster = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (spigotMaster == null) {
            return;
        }

        ConfigurationSection modeSection = voteGuiConfig.getConfigurationSection("mode-section.items");
        if (modeSection == null) return;

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(plugin.getMessage("bedrock_mode_vote_title", "&e&l选择游戏模式"))
                .content(plugin.getMessage("bedrock_mode_vote_content", "票数最高者将成为本局模式："));

        List<Integer> modeIds = new ArrayList<>();
        List<ConfigurationSection> modeConfigs = new ArrayList<>();

        for (ConfigurationSection modeCfg : voteSystem.getConfigItems(modeSection)) {
            int modeId = modeCfg.getInt("id");
            if (!voteSystem.isSupportedGameMode(modeId)) {
                continue;
            }
            String name = ChatColor.translateAlternateColorCodes('&', modeCfg.getString("name", "未知模式"));
            int currentVotes = voteSystem.getModeVoteCount(modeId);
            builder.button(plugin.getMessage("bedrock_vote_option_button", "%name%\n&r&a当前票数: %votes%")
                    .replace("%name%", name)
                    .replace("%votes%", String.valueOf(currentVotes)));
            modeIds.add(modeId);
            modeConfigs.add(modeCfg);
        }

        builder.button(plugin.getMessage("bedrock_back_button", "&c返回主菜单"));

        builder.validResultHandler(response -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    int clickedId = response.clickedButtonId();

                    if (clickedId >= modeIds.size()) {
                        openBedrockVoteMenu(voteSystem, voteGuiConfig, plugin, player);
                        return;
                    }

                    int selectedModeId = modeIds.get(clickedId);
                    ConfigurationSection selectedCfg = modeConfigs.get(clickedId);
                    String modeName = ChatColor.translateAlternateColorCodes('&', selectedCfg.getString("name", "未知模式"));

                    voteSystem.recordPlayerModeChoice(player, selectedModeId);

                    player.sendMessage(plugin.getMessage("choice", "&a已选择: %modeName%")
                            .replace("%modeName%", modeName));
                    voteSystem.playItemSound(player, selectedCfg);

                    openBedrockVoteMenu(voteSystem, voteGuiConfig, plugin, player);
                }
            }.runTask(plugin);
        });

        spigotMaster.sendForm(player.getUniqueId(), builder);
    }

    /**
     * 注意：这里改为 static 方法
     */
    public static void openBedrockRoleVote(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, HunterGame plugin, Player player) {
        BaseAPI spigotMaster = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (spigotMaster == null) {
            return;
        }

        if (!voteSystem.isFixedModeEnabled() && !voteSystem.playerModeChoice.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    voteGuiConfig.getString("messages.no-mode-selected", "&e请先选择游戏模式！")));
            openBedrockVoteMenu(voteSystem, voteGuiConfig, plugin, player);
            return;
        }

        ConfigurationSection roleSection = voteGuiConfig.getConfigurationSection("role-section.items");
        if (roleSection == null) return;

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(plugin.getMessage("bedrock_role_vote_title", "&b&l选择游戏角色"))
                .content(plugin.getMessage("bedrock_role_vote_content", "选择你的阵营（概率增加）："));

        List<Integer> roleIds = new ArrayList<>();
        List<ConfigurationSection> roleConfigs = voteSystem.getConfigItems(roleSection);

        for (ConfigurationSection roleCfg : roleConfigs) {
            int roleId = roleCfg.getInt("id");
            String name = ChatColor.translateAlternateColorCodes('&', roleCfg.getString("name", "未知角色"));
            int currentVotes = voteSystem.getRoleVoteCount(roleId);

            builder.button(plugin.getMessage("bedrock_vote_option_button", "%name%\n&r&a当前票数: %votes%")
                    .replace("%name%", name)
                    .replace("%votes%", String.valueOf(currentVotes)));
            roleIds.add(roleId);
        }

        if (!voteSystem.isFixedModeEnabled()) {
            builder.button(plugin.getMessage("bedrock_back_button", "&c返回主菜单"));
        } else {
            builder.button(plugin.getMessage("bedrock_close_short_button", "&c关闭"));
        }

        builder.validResultHandler(response -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    int clickedId = response.clickedButtonId();

                    if (clickedId >= roleIds.size()) {
                        if (!voteSystem.isFixedModeEnabled()) openBedrockVoteMenu(voteSystem, voteGuiConfig, plugin, player);
                        return;
                    }

                    int selectedRoleId = roleIds.get(clickedId);
                    ConfigurationSection selectedCfg = roleConfigs.get(clickedId);
                    String roleName = ChatColor.translateAlternateColorCodes('&', selectedCfg.getString("name", "未知角色"));

                    voteSystem.recordPlayerRoleVote(player, selectedRoleId);

                    String key = selectedRoleId == VoteSystem.VOTE_ESCAPER ? "choice_camp_escaper" : "choice_camp_hunter";
                    String fallback = selectedRoleId == VoteSystem.VOTE_ESCAPER
                            ? "&a已选择阵营: %role%"
                            : "&c已选择阵营: %role%";
                    player.sendMessage(plugin.getMessage(key, fallback)
                            .replace("%role%", roleName));
                    voteSystem.playItemSound(player, selectedCfg);
                }
            }.runTask(plugin);
        });

        spigotMaster.sendForm(player.getUniqueId(), builder);
    }

    /**
     * 基岩版胜利条件（战役类型）投票表单
     */
    public static void openBedrockTypeVote(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, HunterGame plugin, Player player) {
        BaseAPI spigotMaster = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (spigotMaster == null) {
            return;
        }

        ConfigurationSection typeSection = voteGuiConfig.getConfigurationSection("type-section.items");
        if (typeSection == null) return;

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(plugin.getMessage("bedrock_type_vote_title", "&6&l选择胜利条件"))
                .content(plugin.getMessage("bedrock_type_vote_content", "票数最高者将成为本局胜利条件："));

        List<Integer> typeIds = new ArrayList<>();
        List<ConfigurationSection> typeConfigs = voteSystem.getConfigItems(typeSection);

        for (ConfigurationSection typeCfg : typeConfigs) {
            int typeId = typeCfg.getInt("id");
            String name = ChatColor.translateAlternateColorCodes('&', typeCfg.getString("name", "未知类型"));
            int currentVotes = voteSystem.getTypeVoteCount(typeId);
            builder.button(plugin.getMessage("bedrock_vote_option_button", "%name%\n&r&a当前票数: %votes%")
                    .replace("%name%", name)
                    .replace("%votes%", String.valueOf(currentVotes)));
            typeIds.add(typeId);
        }

        builder.button(plugin.getMessage("bedrock_back_button", "&c返回主菜单"));

        builder.validResultHandler(response -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    int clickedId = response.clickedButtonId();

                    if (clickedId >= typeIds.size()) {
                        openBedrockVoteMenu(voteSystem, voteGuiConfig, plugin, player);
                        return;
                    }

                    int selectedTypeId = typeIds.get(clickedId);
                    ConfigurationSection selectedCfg = typeConfigs.get(clickedId);
                    String typeName = ChatColor.translateAlternateColorCodes('&', selectedCfg.getString("name", "未知类型"));

                    voteSystem.recordPlayerTypeVote(player, selectedTypeId);

                    player.sendMessage(plugin.getMessage("choice", "&a已选择: %modeName%")
                            .replace("%modeName%", typeName));
                    voteSystem.playItemSound(player, selectedCfg);

                    openBedrockVoteMenu(voteSystem, voteGuiConfig, plugin, player);
                }
            }.runTask(plugin);
        });

        spigotMaster.sendForm(player.getUniqueId(), builder);
    }
}


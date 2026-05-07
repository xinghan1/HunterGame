package com.huntergame.reward;

import com.huntergame.HunterGame;
import org.bukkit.entity.Player;

import java.util.UUID;

public class GameRewardService {
    private final SettlementRewardService settlementRewardService;

    public GameRewardService(HunterGame plugin) {
        this.settlementRewardService = new SettlementRewardService(plugin);
    }

    public boolean giveHunterReward(Player player) {
        return settlementRewardService.rewardHunter(player, true);
    }

    public boolean giveHunterFailReward(Player player) {
        return settlementRewardService.rewardHunter(player, false);
    }

    public boolean giveEscaperReward(Player player) {
        return settlementRewardService.rewardEscaper(player, true);
    }

    public boolean giveEscaperFailReward(Player player) {
        return settlementRewardService.rewardEscaper(player, false);
    }

    public boolean hasSettlementReward(UUID uuid) {
        return settlementRewardService.hasRewarded(uuid);
    }

    public void resetSettlementRewards() {
        settlementRewardService.resetForGame();
    }
}



package com.huntergame.reward;

import com.huntergame.HunterGame;
import java.util.UUID;
import org.bukkit.entity.Player;

public class GameRewardService {
   private final SettlementRewardService settlementRewardService;

   public GameRewardService(HunterGame plugin) {
      this.settlementRewardService = new SettlementRewardService(plugin);
   }

   public boolean giveHunterReward(Player player) {
      return this.settlementRewardService.rewardHunter(player, true);
   }

   public boolean giveHunterFailReward(Player player) {
      return this.settlementRewardService.rewardHunter(player, false);
   }

   public boolean giveEscaperReward(Player player) {
      return this.settlementRewardService.rewardEscaper(player, true);
   }

   public boolean giveEscaperFailReward(Player player) {
      return this.settlementRewardService.rewardEscaper(player, false);
   }

   public boolean hasSettlementReward(UUID uuid) {
      return this.settlementRewardService.hasRewarded(uuid);
   }

   public void resetSettlementRewards() {
      this.settlementRewardService.resetForGame();
   }
}

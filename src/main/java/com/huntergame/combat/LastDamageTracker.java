package com.huntergame.combat;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LastDamageTracker implements Listener {
    private static final long CREDIT_WINDOW_MILLIS = 30_000L;

    private final HunterGame plugin;
    private final Map<UUID, DamageCredit> lastPlayerDamage = new HashMap<>();

    public LastDamageTracker(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0.0) {
            return;
        }

        Player damager = getPlayerDamager(event.getDamager());
        if (damager == null || damager.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        lastPlayerDamage.put(victim.getUniqueId(), DamageCredit.of(damager));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID victimId = event.getEntity().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> lastPlayerDamage.remove(victimId));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastPlayerDamage.remove(event.getPlayer().getUniqueId());
    }

    public DamageCredit getCreditedKiller(Player victim) {
        DamageCredit directCredit = getDirectDamageCredit(victim);
        if (directCredit != null) {
            return directCredit;
        }

        DamageCredit credit = lastPlayerDamage.get(victim.getUniqueId());
        if (credit == null || credit.playerId().equals(victim.getUniqueId())) {
            return null;
        }
        if (System.currentTimeMillis() - credit.damageTimeMillis() > CREDIT_WINDOW_MILLIS) {
            return null;
        }
        return credit;
    }

    private DamageCredit getDirectDamageCredit(Player victim) {
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent)) {
            return null;
        }

        Player damager = getPlayerDamager(damageEvent.getDamager());
        if (damager == null || damager.getUniqueId().equals(victim.getUniqueId())) {
            return null;
        }
        return DamageCredit.of(damager);
    }

    public Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    public void reset() {
        lastPlayerDamage.clear();
    }

    public record DamageCredit(UUID playerId, String playerName, long damageTimeMillis) {
        static DamageCredit of(Player player) {
            return new DamageCredit(player.getUniqueId(), player.getName(), System.currentTimeMillis());
        }
    }
}

package com.huntergame.skill;

import com.huntergame.HunterGame;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExplosiveCrossbowListener implements Listener {

    private final HunterGame plugin;
    private final NamespacedKey explosiveCrossbowKey;
    private static final double EXPLOSION_RADIUS = 2.5;
    private static final double BASE_DAMAGE = 1;

    public ExplosiveCrossbowListener(HunterGame plugin) {
        this.plugin = plugin;
        this.explosiveCrossbowKey = new NamespacedKey(plugin, "explosive_crossbow");
    }

    // 监听弩发射火箭事件，标记爆炸火箭
    @EventHandler
    public void onPlayerShootBow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Firework) {
            Firework firework = (Firework) event.getEntity();
            ProjectileSource shooterSource = firework.getShooter();

            if (shooterSource instanceof Player) {
                Player player = (Player) shooterSource;
                ItemStack itemInHand = player.getInventory().getItemInMainHand();

                if (isExplosiveCrossbow(itemInHand)) {
                    markAsExplosiveRocket(firework, player);
                }
            }
        }
    }

    // 监听火箭爆炸事件，执行真实伤害
    @EventHandler
    public void onFireworkExplode(FireworkExplodeEvent event) {
        Firework firework = event.getEntity();

        if (isExplosiveRocket(firework)) {
            Location loc = firework.getLocation();
            ProjectileSource shooter = firework.getShooter();

            if (shooter instanceof Player) {
                explodeWithTrueDamage(loc, (Player) shooter);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
            }
        }
    }

    private boolean isExplosiveCrossbow(ItemStack item) {
        if (item == null || item.getType() != Material.CROSSBOW) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Byte data = meta.getPersistentDataContainer().get(
                explosiveCrossbowKey,
                PersistentDataType.BYTE
        );

        return data != null && data == 1;
    }

    private void markAsExplosiveRocket(Firework firework, Player shooter) {
        firework.setMetadata("explosive_rocket", new FixedMetadataValue(plugin, true));
        firework.setShooter(shooter);
    }

    private boolean isExplosiveRocket(Firework firework) {
        return firework.hasMetadata("explosive_rocket");
    }

    private void explodeWithTrueDamage(Location center, Player shooter) {
        Collection<Entity> entities = center.getWorld().getNearbyEntities(
                center, EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS);

        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.equals(shooter)) continue; // 不伤自己

            LivingEntity target = (LivingEntity) entity;
            double distance = center.distance(entity.getLocation());
            if (distance > EXPLOSION_RADIUS) continue;

            double damage = BASE_DAMAGE * (1 - distance / EXPLOSION_RADIUS);
            damage = Math.max(1, damage);

            applyTrueDamage(target, damage);

            // 击退效果，远离爆炸中心
            Vector knockback = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.5);
            entity.setVelocity(knockback);

        }
    }

    private void applyTrueDamage(LivingEntity target, double damage) {
        double health = target.getHealth();
        AttributeInstance attr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;

        double newHealth = health - damage;
        newHealth = Math.min(maxHealth, newHealth);
        newHealth = Math.max(0, newHealth);
        target.setHealth(newHealth);

        // 伤害指示粒子
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 10);
    }

    public void giveCrossbowPackage(Player player) {
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = crossbow.getItemMeta();

        meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);

        meta.getPersistentDataContainer().set(
                explosiveCrossbowKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "左键获取爆炸火箭");
        meta.setLore(lore);


        crossbow.setItemMeta(meta);

        ItemStack rockets = createExplosiveRockets(4);

        player.getInventory().addItem(crossbow, rockets);
        player.updateInventory();
    }

    ItemStack createExplosiveRockets(int amount) {
        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET, amount);
        FireworkMeta meta = (FireworkMeta) rocket.getItemMeta();

        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST)
                .withColor(Color.RED)
                .withFade(Color.ORANGE)
                .withTrail()
                .withFlicker()
                .build();

        meta.addEffect(effect);
        meta.setPower(2);
        rocket.setItemMeta(meta);

        return rocket;
    }
}

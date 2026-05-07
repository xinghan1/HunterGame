package com.huntergame.skill.skills;

import com.huntergame.HunterGame;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
    private static final String SKILL_NAME = "爆炸弩";
    private static final double EXPLOSION_RADIUS = 2.5;
    private static final double DEFAULT_TRUE_DAMAGE = 3.0;

    private final HunterGame plugin;
    private final NamespacedKey explosiveCrossbowKey;

    public ExplosiveCrossbowListener(HunterGame plugin) {
        this.plugin = plugin;
        this.explosiveCrossbowKey = new NamespacedKey(plugin, "explosive_crossbow");
    }

    @EventHandler
    public void onPlayerShootBow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Firework)) {
            return;
        }

        Firework firework = (Firework) event.getEntity();
        ProjectileSource shooterSource = firework.getShooter();
        if (!(shooterSource instanceof Player)) {
            return;
        }

        Player player = (Player) shooterSource;
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (isExplosiveCrossbow(itemInHand)) {
            markAsExplosiveRocket(firework, player);
        }
    }

    @EventHandler
    public void onFireworkExplode(FireworkExplodeEvent event) {
        Firework firework = event.getEntity();
        if (!isExplosiveRocket(firework)) {
            return;
        }

        Location loc = firework.getLocation();
        ProjectileSource shooter = firework.getShooter();
        if (shooter instanceof Player) {
            explodeWithTrueDamage(loc, (Player) shooter);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        }
    }

    private boolean isExplosiveCrossbow(ItemStack item) {
        if (item == null || item.getType() != Material.CROSSBOW) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        Byte data = meta.getPersistentDataContainer().get(explosiveCrossbowKey, PersistentDataType.BYTE);
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
        Collection<Entity> entities = center.getWorld().getNearbyEntities(center, EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS);
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity) || entity.equals(shooter)) {
                continue;
            }

            LivingEntity target = (LivingEntity) entity;
            double distance = center.distance(entity.getLocation());
            if (distance > EXPLOSION_RADIUS) {
                continue;
            }

            applyTrueDamage(target, getTrueDamage());

            Vector knockback = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.5);
            entity.setVelocity(knockback);
        }
    }

    private void applyTrueDamage(LivingEntity target, double damage) {
        double health = target.getHealth();
        AttributeInstance attr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;

        double newHealth = Math.max(0, Math.min(maxHealth, health - damage));
        target.setHealth(newHealth);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 10);
    }

    private double getTrueDamage() {
        return Math.max(0.0, plugin.getSkillManager().getSkillDouble(SKILL_NAME, "true_damage", DEFAULT_TRUE_DAMAGE));
    }

    public void giveCrossbowPackage(Player player) {
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = crossbow.getItemMeta();
        meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);
        meta.getPersistentDataContainer().set(explosiveCrossbowKey, PersistentDataType.BYTE, (byte) 1);

        List<String> lore = new ArrayList<>();
        lore.add(getCrossbowSupplyLore());
        meta.setLore(lore);
        crossbow.setItemMeta(meta);

        ItemStack rockets = createExplosiveRockets(4);
        player.getInventory().addItem(crossbow, rockets);
        player.updateInventory();
    }

    public String getCrossbowSupplyLore() {
        return plugin.getMessage("explosive_crossbow_supply_lore", "&7左键获取爆炸火箭");
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


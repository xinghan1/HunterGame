package com.huntergame.world;

import com.huntergame.HunterGame;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.DragonBattle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public class OnlineWorldResetManager {
   private static final String BUNGEE_CHANNEL = "BungeeCord";
   private static final String FINAL_BATTLE_WORLD = "world_the_end";
   private static final int DEFAULT_CLEAR_CENTER_X = 100;
   private static final int DEFAULT_CLEAR_CENTER_Z = 0;
   private static final int DEFAULT_CLEAR_RADIUS_BLOCKS = 160;
   private final HunterGame plugin;
   private boolean resetting = false;

   public OnlineWorldResetManager(HunterGame plugin) {
      this.plugin = plugin;
   }

   public boolean isResetting() {
      return this.resetting;
   }

   public void startReset() {
      if (!this.resetting) {
         this.resetting = true;
         this.plugin.setResetInProgress(true);
         Bukkit.broadcastMessage(this.plugin.getMessage("online_reset_started", "&c服务器进入重置中..."));
         this.transferPlayersToLobby();
         long kickDelayTicks = Math.max(1L, this.getConfig().getLong("game.online_reset.player_kick_delay_ticks", 40L));
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.kickRemainingPlayers();
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               this.plugin.prepareForOnlineWorldReset();
               this.resetFinalBattleCoreArea();
            }, Math.max(1L, this.getConfig().getLong("game.online_reset.post_kick_prepare_delay_ticks", 20L)));
         }, kickDelayTicks);
      }
   }

   private void transferPlayersToLobby() {
      String server = this.getConfig().getString("BungeeCord.server_lobby", "lobby");
      if (server != null && !server.isBlank()) {
         for(Player player : new ArrayList<Player>(Bukkit.getOnlinePlayers())) {
            try {
               ByteArrayOutputStream bytes = new ByteArrayOutputStream();
               DataOutputStream out = new DataOutputStream(bytes);
               out.writeUTF("Connect");
               out.writeUTF(server);
               player.sendPluginMessage(this.plugin, BUNGEE_CHANNEL, bytes.toByteArray());
            } catch (IOException ex) {
               Logger logger = this.plugin.getLogger();
               logger.warning("发送跨服传送请求失败: " + player.getName() + " -> " + ex.getMessage());
            }
         }
      }
   }

   private void kickRemainingPlayers() {
      String message = this.plugin.getMessage("online_reset_kick_message", "&c服务器正在重置地图，请稍后再加入");
      for(Player player : new ArrayList<Player>(Bukkit.getOnlinePlayers())) {
         player.kickPlayer(message);
      }
   }

   private void resetFinalBattleCoreArea() {
      World world = this.getOrCreateFinalBattleWorld();
      if (world == null) {
         this.plugin.getLogger().warning("末地世界加载失败，无法执行 schematic 重置: " + this.getFinalBattleWorldName());
         this.finishReset();
         return;
      }

      this.prepareWorld(world);
      this.cleanupCoreAreaEntities(world);
      if (!this.pasteResetSchematic(world)) {
         world.setAutoSave(true);
         this.finishReset();
         return;
      }

      this.afterSchematicPasted(world);
   }

   private World getOrCreateFinalBattleWorld() {
      String worldName = this.getFinalBattleWorldName();
      World world = Bukkit.getWorld(worldName);
      if (world == null) {
         WorldCreator creator = new WorldCreator(worldName);
         creator.environment(Environment.THE_END);
         creator.generateStructures(false);
         world = Bukkit.createWorld(creator);
      }

      return world;
   }

   private String getFinalBattleWorldName() {
      return this.getConfig().getString("game.online_reset.schematic_reset.world", FINAL_BATTLE_WORLD);
   }

   private void prepareWorld(World world) {
      world.setAutoSave(false);
      world.setTime(6000L);
      world.setStorm(false);
      world.setThundering(false);
   }

   private void cleanupCoreAreaEntities(World world) {
      int removed = 0;
      double centerX = this.getConfig().getDouble("game.online_reset.schematic_reset.cleanup_center.x", DEFAULT_CLEAR_CENTER_X);
      double centerZ = this.getConfig().getDouble("game.online_reset.schematic_reset.cleanup_center.z", DEFAULT_CLEAR_CENTER_Z);
      double radius = Math.max(1.0D, this.getConfig().getDouble("game.online_reset.schematic_reset.cleanup_radius", DEFAULT_CLEAR_RADIUS_BLOCKS));
      double radiusSquared = radius * radius;
      for(Entity entity : new ArrayList<Entity>(world.getEntities())) {
         if (!(entity instanceof Player)) {
            Location location = entity.getLocation();
            double dx = location.getX() - centerX;
            double dz = location.getZ() - centerZ;
            if (dx * dx + dz * dz <= radiusSquared || entity instanceof EnderDragon) {
               entity.remove();
               ++removed;
            }
         }
      }

      this.plugin.getLogger().info("末地重置区域实体清理完成: " + removed);
   }

   private boolean pasteResetSchematic(World world) {
      if (!this.isWorldEditAvailable()) {
         this.plugin.getLogger().severe("未检测到 FastAsyncWorldEdit/WorldEdit，无法使用 schematic 重置末地战场。请安装 FAWE 或 WorldEdit。 ");
         return false;
      }

      File schematicFile = this.getSchematicFile();
      if (schematicFile == null || !schematicFile.isFile()) {
         this.plugin.getLogger().severe("末地重置 schematic 文件不存在: " + (schematicFile == null ? "未配置" : schematicFile.getAbsolutePath()));
         return false;
      }

      int pasteX = this.getConfig().getInt("game.online_reset.schematic_reset.paste_origin.x", 0);
      int pasteY = this.getConfig().getInt("game.online_reset.schematic_reset.paste_origin.y", 0);
      int pasteZ = this.getConfig().getInt("game.online_reset.schematic_reset.paste_origin.z", 0);
      boolean ignoreAir = this.getConfig().getBoolean("game.online_reset.schematic_reset.ignore_air", false);

      try {
         this.pasteSchematicWithWorldEditApi(world, schematicFile, pasteX, pasteY, pasteZ, ignoreAir);
         this.plugin.getLogger().info("末地战场已通过 schematic 重置: " + schematicFile.getAbsolutePath() + ", 粘贴原点=(" + pasteX + ", " + pasteY + ", " + pasteZ + "), ignoreAir=" + ignoreAir);
         return true;
      } catch (Exception ex) {
         this.plugin.getLogger().log(Level.SEVERE, "粘贴末地重置 schematic 失败: " + schematicFile.getAbsolutePath(), ex);
         return false;
      }
   }

   private void pasteSchematicWithWorldEditApi(World world, File schematicFile, int pasteX, int pasteY, int pasteZ, boolean ignoreAir) throws Exception {
      Class<?> clipboardFormatsClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
      Object format = clipboardFormatsClass.getMethod("findByFile", File.class).invoke(null, schematicFile);
      if (format == null) {
         throw new IllegalArgumentException("无法识别 schematic 格式: " + schematicFile.getAbsolutePath());
      }

      Class<?> blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
      Object pasteAt = blockVector3Class.getMethod("at", int.class, int.class, int.class).invoke(null, pasteX, pasteY, pasteZ);
      Object reader = null;
      Object editSession = null;

      try (FileInputStream inputStream = new FileInputStream(schematicFile)) {
         Class<?> clipboardFormatInterface = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
         Method getReaderMethod = clipboardFormatInterface.getMethod("getReader", java.io.InputStream.class);
         getReaderMethod.setAccessible(true);
         reader = getReaderMethod.invoke(format, inputStream);

         Method readMethod = findMethod(reader.getClass(), "read");
         readMethod.setAccessible(true);
         Object clipboard = readMethod.invoke(reader);

         Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
         Object adaptedWorld = bukkitAdapterClass.getMethod("adapt", World.class).invoke(null, world);
         Object worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit").getMethod("getInstance").invoke(null);

         Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
         Method newEditSessionMethod = worldEdit.getClass().getMethod("newEditSession", weWorldClass);
         newEditSessionMethod.setAccessible(true);
         editSession = newEditSessionMethod.invoke(worldEdit, adaptedWorld);

         Class<?> clipboardClass = Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard");
         Constructor<?> holderConstructor = Class.forName("com.sk89q.worldedit.session.ClipboardHolder").getConstructor(clipboardClass);
         holderConstructor.setAccessible(true);
         Object clipboardHolder = holderConstructor.newInstance(clipboard);

         // createPaste 在不同版本中参数类型不同（EditSession 或 Extent），按名称+参数数量查找
         Method createPasteMethod = findMethodByName(clipboardHolder.getClass(), "createPaste", 1);
         createPasteMethod.setAccessible(true);
         Object pasteBuilder = createPasteMethod.invoke(clipboardHolder, editSession);

         Method toMethod = findMethodByName(pasteBuilder.getClass(), "to", 1);
         toMethod.setAccessible(true);
         pasteBuilder = toMethod.invoke(pasteBuilder, pasteAt);

         Method ignoreAirMethod = findMethodByName(pasteBuilder.getClass(), "ignoreAirBlocks", 1);
         ignoreAirMethod.setAccessible(true);
         pasteBuilder = ignoreAirMethod.invoke(pasteBuilder, ignoreAir);

         Method copyEntitiesMethod = findOptionalMethodByName(pasteBuilder.getClass(), "copyEntities", 1);
         if (copyEntitiesMethod != null) {
            copyEntitiesMethod.setAccessible(true);
            pasteBuilder = copyEntitiesMethod.invoke(pasteBuilder, true);
         }

         Method buildMethod = findMethod(pasteBuilder.getClass(), "build");
         buildMethod.setAccessible(true);
         Object operation = buildMethod.invoke(pasteBuilder);

         Class<?> operationClass = Class.forName("com.sk89q.worldedit.function.operation.Operation");
         Class<?> operationsClass = Class.forName("com.sk89q.worldedit.function.operation.Operations");
         Method completeMethod = operationsClass.getMethod("complete", operationClass);
         completeMethod.setAccessible(true);
         completeMethod.invoke(null, operation);

         Method flushMethod = findMethod(editSession.getClass(), "flushSession");
         flushMethod.setAccessible(true);
         flushMethod.invoke(editSession);
      } finally {
         this.closeQuietly(editSession);
         this.closeQuietly(reader);
      }
   }

   private static Method findMethod(Class<?> clazz, String name) throws NoSuchMethodException {
      for (Method m : clazz.getMethods()) {
         if (m.getName().equals(name) && m.getParameterCount() == 0) {
            return m;
         }
      }
      throw new NoSuchMethodException(clazz.getName() + "." + name + "()");
   }

   private static Method findMethodByName(Class<?> clazz, String name, int paramCount) throws NoSuchMethodException {
      for (Method m : clazz.getMethods()) {
         if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
            return m;
         }
      }
      // 也搜索 declared methods（包括继承链上的非 public 方法）
      Class<?> c = clazz;
      while (c != null) {
         for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
               return m;
            }
         }
         c = c.getSuperclass();
      }
      throw new NoSuchMethodException(clazz.getName() + "." + name + "(" + paramCount + " params)");
   }

   private static Method findOptionalMethodByName(Class<?> clazz, String name, int paramCount) {
      try {
         return findMethodByName(clazz, name, paramCount);
      } catch (NoSuchMethodException ignored) {
         return null;
      }
   }

   private void closeQuietly(Object closeable) {
      if (closeable instanceof AutoCloseable) {
         try {
            ((AutoCloseable)closeable).close();
         } catch (Exception ignored) {
         }
      }
   }

   private boolean isWorldEditAvailable() {
      return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit") || Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
   }

   private File getSchematicFile() {
      String configuredPath = this.getConfig().getString("game.online_reset.schematic_reset.file", "schematics/final_battle_reset.schem");
      if (configuredPath == null || configuredPath.isBlank()) {
         return null;
      }

      File file = new File(configuredPath);
      if (!file.isAbsolute()) {
         file = new File(this.plugin.getDataFolder(), configuredPath);
      }

      return file;
   }

   private void afterSchematicPasted(World world) {
      world.setAutoSave(true);
      world.setTime(6000L);
      world.setStorm(false);
      world.setThundering(false);
      this.respawnDragon(world);
      this.plugin.getLogger().info("末地中心战场 schematic 重置完成。未卸载世界、未删除地图文件。 ");
      this.finishReset();
   }

   private void respawnDragon(World world) {
      Location dragonSpawn = new Location(world, this.getConfig().getDouble("game.online_reset.schematic_reset.dragon_spawn.x", 0.0D), this.getConfig().getDouble("game.online_reset.schematic_reset.dragon_spawn.y", 90.0D), this.getConfig().getDouble("game.online_reset.schematic_reset.dragon_spawn.z", 0.0D));

      for(Entity entity : new ArrayList<Entity>(world.getEntities())) {
         if (entity instanceof EnderDragon) {
            entity.remove();
         }
      }

      EnderDragon dragon = null;
      DragonBattle battle = world.getEnderDragonBattle();
      if (battle != null) {
         try {
            battle.initiateRespawn();
         } catch (IllegalStateException ignored) {
         }

         dragon = battle.getEnderDragon();
      }

      if (dragon == null || dragon.isDead()) {
         dragon = (EnderDragon)world.spawnEntity(dragonSpawn, EntityType.ENDER_DRAGON);
      }

      dragon.teleport(dragonSpawn);
      AttributeInstance maxHealth = dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH);
      if (maxHealth != null) {
         dragon.setHealth(maxHealth.getValue());
      }

      dragon.setPhase(EnderDragon.Phase.CIRCLING);
      this.plugin.getLogger().info("末影龙已在 " + this.formatLocation(dragonSpawn) + " 重新生成并设置为盘旋状态。 ");
   }

   private String formatLocation(Location location) {
      return location.getWorld().getName() + "(" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
   }

   private void finishReset() {
      this.resetting = false;
      this.plugin.completeOnlineWorldReset();
      this.plugin.getLogger().info("在线世界重置完成。");
   }

   private FileConfiguration getConfig() {
      return this.plugin.getConfig();
   }
}

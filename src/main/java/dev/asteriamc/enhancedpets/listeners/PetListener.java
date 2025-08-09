package dev.asteriamc.enhancedpets.listeners;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;
import org.bukkit.entity.Fireball;
import java.util.HashMap;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.vehicle.VehicleEnterEvent;

public class PetListener implements Listener {
   private final Enhancedpets plugin;
   private final PetManager petManager;
   private final ShiftClickTracker shiftTracker = new ShiftClickTracker();

   public PetListener(Enhancedpets plugin) {
      this.plugin = plugin;
      this.petManager = plugin.getPetManager();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onEntityTame(EntityTameEvent event) {
      if (event.getEntity() instanceof Tameable pet && event.getOwner() instanceof Player) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (pet.isValid() && pet.isTamed() && pet.getOwnerUniqueId() != null && !this.petManager.isManagedPet(pet.getUniqueId())) {
               this.petManager.registerPet(pet);
            }
         }, 1L);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onEntityDeath(EntityDeathEvent event) {
      if (event.getEntity() instanceof Tameable && this.petManager.isManagedPet(event.getEntity().getUniqueId())) {
         UUID petUUID = event.getEntity().getUniqueId();
         PetData data = this.petManager.getPetData(petUUID);
         String name = data != null ? data.getDisplayName() : "Unknown Pet";
         this.plugin.getLogger().info("Managed pet " + name + " (UUID: " + petUUID + ") died. Marking as dead.");
         
         this.petManager.unregisterPet(event.getEntity());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onEntityUnleash(EntityUnleashEvent event) {
      if (event.getEntity() instanceof Tameable pet && this.petManager.isManagedPet(pet.getUniqueId())) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            PetData data = this.petManager.getPetData(pet.getUniqueId());
            if (data != null) {
               if (!pet.isValid()) {
                  this.plugin.getLogger().info("Managed pet " + data.getDisplayName() + " became invalid. Unregistering.");
                  this.petManager.unregisterPet(pet.getUniqueId());
               } else {
                  UUID currentOwner = pet.getOwnerUniqueId();
                  if (currentOwner == null || !currentOwner.equals(data.getOwnerUUID())) {
                     this.plugin.getLogger().info("Pet " + data.getDisplayName() + " owner changed unexpectedly or lost owner. Untaming and unregistering.");
                     if (pet.isTamed()) {
                        pet.setOwner(null);
                        pet.setTamed(false);
                     }

                     this.petManager.unregisterPet(pet.getUniqueId());
                  }
               }
            }
         }, 1L);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChunkLoad(ChunkLoadEvent event) {
      Chunk chunk = event.getChunk();

      for (Entity entity : chunk.getEntities()) {
         if (entity instanceof Tameable pet && pet.isTamed() && pet.getOwnerUniqueId() != null && !this.petManager.isManagedPet(pet.getUniqueId())) {
            this.plugin
               .getLogger()
               .log(Level.FINE, "Found unmanaged tamed pet {0} ({1}) in loaded chunk. Registering...", new Object[]{pet.getType(), pet.getUniqueId()});
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
               if (pet.isValid() && pet.isTamed() && pet.getOwnerUniqueId() != null && !this.petManager.isManagedPet(pet.getUniqueId())) {
                  this.petManager.registerPet(pet);
               }
            }, 1L);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onEntityBreed(EntityBreedEvent event) {
      
      if (event.getEntity() instanceof Tameable babyPet) {
         UUID babyUUID = babyPet.getUniqueId();

         
         this.plugin
                 .getServer()
                 .getScheduler()
                 .runTaskLater(
                         this.plugin,
                         () -> {
                            
                            if (this.plugin.getServer().getEntity(babyUUID) instanceof Tameable babyToCheck && babyToCheck.isValid()) {

                               
                               if (babyToCheck.isTamed()
                                       && babyToCheck.getOwnerUniqueId() != null
                                       && !this.petManager.isManagedPet(babyToCheck.getUniqueId())) {

                                  this.plugin.getLogger().info("Registering newly bred pet " + babyToCheck.getType() + " (UUID: " + babyToCheck.getUniqueId() + ")");
                                  this.petManager.registerPet(babyToCheck);
                               }
                            }
                         },
                         2L 
                 );
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPetRenameWithNameTag(PlayerInteractEntityEvent event) {
      
      ItemStack itemInHand = event.getPlayer().getInventory().getItem(event.getHand());

      
      if (itemInHand != null && itemInHand.getType() == Material.NAME_TAG && itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasDisplayName()) {
         Entity clickedEntity = event.getRightClicked();

         if (clickedEntity instanceof Tameable && petManager.isManagedPet(clickedEntity.getUniqueId())) {
            
            UUID petUUID = clickedEntity.getUniqueId();
            PetData petData = petManager.getPetData(petUUID);

            if (petData != null) {
               
               String newName = ChatColor.stripColor(itemInHand.getItemMeta().getDisplayName());

               
               petData.setDisplayName(newName);
               petManager.updatePetData(petData); 

               plugin.getLogger().info("Detected name tag rename for pet " + petUUID + ". Synced name to '" + newName + "'.");

               
               
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
      if (event.getEntity() instanceof Creature petCreature) {
         if (petCreature instanceof Tameable pet) {
            if (pet.isTamed() && this.petManager.isManagedPet(pet.getUniqueId())) {
               PetData petData = this.petManager.getPetData(pet.getUniqueId());
               if (petData != null) {
                  LivingEntity target = event.getTarget();
                  if (target != null) {
                     if (petData.getMode() == BehaviorMode.PASSIVE) {
                        event.setTarget(null);
                        event.setCancelled(true);
                     } else if (petData.isFriendlyPlayer(target.getUniqueId())) {
                        event.setTarget(null);
                        event.setCancelled(true);
                     } else if (pet instanceof Wolf && target instanceof Creeper && this.plugin.getConfigManager().getDogCreeperBehavior().equals("FLEE")) {
                        event.setTarget(null);
                        event.setCancelled(true);
                     } else {
                        if (pet instanceof Cat && this.plugin.getConfigManager().isCatsAttackHostiles() && target instanceof Monster) {
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Tameable pet && this.petManager.isManagedPet(pet.getUniqueId())) {
         PetData petData = this.petManager.getPetData(pet.getUniqueId());
         if (petData == null) {
            return;
         }

         if (petData.getMode() == BehaviorMode.PASSIVE) {
            event.setCancelled(true);
            return;
         }

         Entity damagedEntity = event.getEntity();
         if (petData.isFriendlyPlayer(damagedEntity.getUniqueId())) {
            event.setCancelled(true);
            return;
         }
      }
   }

   private final Map<UUID, PendingClick> pending = new ConcurrentHashMap<>();

   private static final class PendingClick {
      final Entity entity;
      final boolean wasSitting;
      final long expiry;

      PendingClick(Entity e, boolean sitting) {
         this.entity = e;
         this.wasSitting = sitting;
         this.expiry = System.currentTimeMillis() + 250L;
      }

      boolean isExpired() {
         return System.currentTimeMillis() > expiry;
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
      
      Player player = e.getPlayer();
      Entity target = e.getRightClicked();
      ItemStack itemInHand = player.getInventory().getItem(e.getHand());
      
      if (target != null && target.getType().name().equalsIgnoreCase("HAPPY_GHAST") &&
          itemInHand != null && itemInHand.getType() == Material.SNOWBALL) {
         e.setCancelled(true);
         if (!petManager.isManagedPet(target.getUniqueId())) {
            if (Math.random() < 0.2) { 
               String defaultName = petManager.assignNewDefaultName(target.getType());
               petManager.registerNonTameablePet(target, player.getUniqueId(), defaultName);
               player.sendMessage(ChatColor.GREEN + "You have tamed this Happy Ghast! It is now your pet.");
            } else {
               player.sendMessage(ChatColor.YELLOW + "The Happy Ghast resisted your taming attempt. Try again!");
            }
         } else {
            player.sendMessage(ChatColor.YELLOW + "This Happy Ghast is already registered as a pet.");
         }
         return;
      }
      if (!plugin.getConfigManager().isShiftDoubleClickGUI()) return;
      if (e.getHand() != EquipmentSlot.HAND) return;

      Player p = e.getPlayer();
      if (!p.isSneaking()) return;

      Entity targetEntity = e.getRightClicked();
      if (!(targetEntity instanceof Tameable pet)) return;
      if (!pet.isTamed()) return;
      if (!pet.getOwnerUniqueId().equals(p.getUniqueId())) return;

      
      e.setCancelled(true);

      UUID uuid = p.getUniqueId();
      PendingClick prev = pending.get(uuid);

      
      if (prev != null && !prev.isExpired() && prev.entity.equals(targetEntity)) {
         pending.remove(uuid);
         plugin.getServer().getScheduler().runTask(plugin, () ->
                 plugin.getGuiManager().openPetMenu(p, targetEntity.getUniqueId())
         );
         return;
      }

      
      boolean sitting = (targetEntity instanceof Sittable s) && s.isSitting();
      pending.put(uuid, new PendingClick(targetEntity, sitting));

      plugin.getServer().getScheduler().runTaskLater(plugin, task -> {
         PendingClick pc = pending.remove(uuid);
         if (pc == null || pc.isExpired()) return;

         
         if (pc.entity.isValid() && pc.entity instanceof Sittable s) {
            s.setSitting(!pc.wasSitting);
         }
      }, 5L); 
   }

   
   private final Map<UUID, Long> ghastMountTime = new HashMap<>();

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onVehicleEnter(VehicleEnterEvent event) {
      if (event.getEntered() instanceof Player player && event.getVehicle().getType().name().equalsIgnoreCase("HAPPY_GHAST")) {
         ghastMountTime.put(player.getUniqueId(), System.currentTimeMillis());
      }
   }

   
   private final Map<UUID, Long> ghastFireballCooldown = new HashMap<>();

   @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
   public void onPlayerAnimation(PlayerAnimationEvent event) {
      if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
      Player player = event.getPlayer();
      Entity vehicle = player.getVehicle();
      if (vehicle == null || !vehicle.getType().name().equalsIgnoreCase("HAPPY_GHAST")) return;
      PetData petData = petManager.getPetData(vehicle.getUniqueId());
      if (petData == null || !petData.getOwnerUUID().equals(player.getUniqueId())) return;
      
      if (ghastMountTime.containsKey(player.getUniqueId())) {
         long mountTime = ghastMountTime.get(player.getUniqueId());
         if (System.currentTimeMillis() - mountTime < 1000) return;
         ghastMountTime.remove(player.getUniqueId());
      }
      long now = System.currentTimeMillis();
      if (ghastFireballCooldown.containsKey(player.getUniqueId())) {
         long last = ghastFireballCooldown.get(player.getUniqueId());
         if (now - last < 1000) return;
      }
      ghastFireballCooldown.put(player.getUniqueId(), now);
      Fireball fireball = ((LivingEntity)vehicle).launchProjectile(Fireball.class, player.getLocation().getDirection().normalize().multiply(1.5));
      fireball.setShooter(player);
      fireball.setYield(1.5f);
      fireball.setIsIncendiary(true);
   }

   
   
   


}


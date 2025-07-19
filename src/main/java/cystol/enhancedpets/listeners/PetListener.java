package cystol.enhancedpets.listeners;

import cystol.enhancedpets.Enhancedpets;
import cystol.enhancedpets.data.BehaviorMode;
import cystol.enhancedpets.data.PetData;
import cystol.enhancedpets.manager.PetManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
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
         this.plugin.getLogger().info("Managed pet " + name + " (UUID: " + petUUID + ") died. Unregistering.");
         this.petManager.unregisterPet(petUUID);
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
      if (!plugin.getConfigManager().isShiftDoubleClickGUI()) return;
      if (e.getHand() != EquipmentSlot.HAND) return;

      Player p = e.getPlayer();
      if (!p.isSneaking()) return;

      Entity target = e.getRightClicked();
      if (!(target instanceof Tameable pet)) return;
      if (!pet.isTamed()) return;
      if (!pet.getOwnerUniqueId().equals(p.getUniqueId())) return;

      
      e.setCancelled(true);

      UUID uuid = p.getUniqueId();
      PendingClick prev = pending.get(uuid);

      
      if (prev != null && !prev.isExpired() && prev.entity.equals(target)) {
         pending.remove(uuid);
         plugin.getServer().getScheduler().runTask(plugin, () ->
                 plugin.getGuiManager().openPetMenu(p, target.getUniqueId())
         );
         return;
      }

      
      boolean sitting = (target instanceof Sittable s) && s.isSitting();
      pending.put(uuid, new PendingClick(target, sitting));

      plugin.getServer().getScheduler().runTaskLater(plugin, task -> {
         PendingClick pc = pending.remove(uuid);
         if (pc == null || pc.isExpired()) return;

         
         if (pc.entity.isValid() && pc.entity instanceof Sittable s) {
            s.setSitting(!pc.wasSitting);
         }
      }, 5L); 
   }


}

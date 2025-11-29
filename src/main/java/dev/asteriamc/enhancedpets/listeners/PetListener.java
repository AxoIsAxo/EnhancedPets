package dev.asteriamc.enhancedpets.listeners;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PetListener implements Listener {
    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final ShiftClickTracker shiftTracker = new ShiftClickTracker();
    private final Map<UUID, PendingClick> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ghastMountTime = new HashMap<>();
    private final Map<UUID, Long> ghastFireballCooldown = new HashMap<>();

    public PetListener(Enhancedpets plugin) {
        this.plugin = plugin;
        this.petManager = plugin.getPetManager();
    }

    private static String formatEntityType(EntityType type) {
        String name = type.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onEntityTame(EntityTameEvent event) {
        if (event.getEntity() instanceof Tameable pet && event.getOwner() instanceof Player) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                AnimalTamer owner = pet.getOwner();
                if (owner != null && pet.isValid() && pet.isTamed() && !this.petManager.isManagedPet(pet.getUniqueId())) {
                    this.petManager.registerPet(pet);
                }
            }, 1L);
        }
    }

    public void forgetPlayer(UUID playerId) {
        pending.remove(playerId);
        ghastMountTime.remove(playerId);
        ghastFireballCooldown.remove(playerId);
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (this.petManager.isManagedPet(entity.getUniqueId()) && entity instanceof LivingEntity livingEntity) {
            UUID petUUID = entity.getUniqueId();
            PetData data = this.petManager.getPetData(petUUID);
            String name = data != null ? data.getDisplayName() : "Unknown Pet";
            this.plugin.debugLog("Managed pet " + name + " (UUID: " + petUUID + ") died. Marking as dead.");
            this.petManager.unregisterPet(livingEntity);
        } else if (entity instanceof Tameable t && t.isTamed() && t.getOwnerUniqueId() != null && entity instanceof LivingEntity le) {
            UUID ownerUUID = t.getOwnerUniqueId();
            UUID petUUID = t.getUniqueId();
            String fallbackName = entity.getCustomName();
            if (fallbackName != null) fallbackName = ChatColor.stripColor(fallbackName);
            if (fallbackName == null || fallbackName.isEmpty()) fallbackName = formatEntityType(entity.getType());
            PetData snapshot = new PetData(petUUID, ownerUUID, entity.getType(), fallbackName);
            this.plugin.getPetManager().captureMetadata(snapshot, le);
            this.plugin.getPetManager().markPetDeadOffline(ownerUUID, petUUID, entity.getType(), fallbackName, snapshot.getMetadata());
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
                        this.plugin.getLogger().fine("Managed pet " + data.getDisplayName() + " became invalid after unleash. Keeping record.");
                    } else {
                        // NEW: replace getOwnerUniqueId()
                        AnimalTamer ownerTamer = pet.getOwner();
                        UUID currentOwner = ownerTamer != null ? ownerTamer.getUniqueId() : null;
                        
                        if (currentOwner == null || !currentOwner.equals(data.getOwnerUUID())) {
                            this.plugin.getLogger().warning("Pet " + data.getDisplayName() + " owner mismatch on unleash. Restoring owner if possible.");
                            Player owner = Bukkit.getPlayer(data.getOwnerUUID());
                            if (owner != null) {
                                pet.setOwner(owner);
                                pet.setTamed(true);
                            }
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
            if (entity instanceof Tameable pet && pet.isTamed() && pet.getOwner() != null && !this.petManager.isManagedPet(pet.getUniqueId())) {
                this.plugin
                        .getLogger()
                        .log(Level.FINE, "Found unmanaged tamed pet {0} ({1}) in loaded chunk. Registering...", new Object[]{pet.getType(), pet.getUniqueId()});
                this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                    if (pet.isValid() && pet.isTamed() && pet.getOwner() != null && !this.petManager.isManagedPet(pet.getUniqueId())) {
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
                                        this.plugin.debugLog("Registering newly bred pet " + babyToCheck.getType() + " (UUID: " + babyToCheck.getUniqueId() + ")");
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
                    plugin.debugLog("Detected name tag rename for pet " + petUUID + ". Synced name to '" + newName + "'.");
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
                            
                            if (plugin.getPetManager().isManagedPet(target.getUniqueId())) {
                                PetData tpd = plugin.getPetManager().getPetData(target.getUniqueId());
                                if (tpd != null && (tpd.getOwnerUUID().equals(petData.getOwnerUUID()) || petData.isFriendlyPlayer(tpd.getOwnerUUID()))) {
                                    event.setTarget(null);
                                    event.setCancelled(true);
                                    return;
                                }
                            }

                            if (petData.getMode() == BehaviorMode.PASSIVE) {
                                event.setTarget(null);
                                event.setCancelled(true);
                            } else if (petData.isFriendlyPlayer(target.getUniqueId())) {
                                event.setTarget(null);
                                event.setCancelled(true);
                            } else if (target instanceof Player && petData.isProtectedFromPlayers()) {
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
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        
        if (this.petManager.isManagedPet(event.getDamager().getUniqueId())) {
            PetData petData = this.petManager.getPetData(event.getDamager().getUniqueId());
            if (petData == null) return;

            
            if (petData.getMode() == BehaviorMode.PASSIVE) {
                event.setCancelled(true);
                return;
            }

            
            if (plugin.getPetManager().isManagedPet(victim.getUniqueId())) {
                PetData vpd = plugin.getPetManager().getPetData(victim.getUniqueId());
                if (vpd != null && (vpd.getOwnerUUID().equals(petData.getOwnerUUID()) || petData.isFriendlyPlayer(vpd.getOwnerUUID()))) {
                    event.setCancelled(true);
                    return;
                }
            }

            
            if (petData.isFriendlyPlayer(victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            
            if (victim instanceof Player && petData.isProtectedFromPlayers()) {
                event.setCancelled(true);
                return;
            }
        }

        
        if (victim instanceof Tameable victimPet && this.petManager.isManagedPet(victimPet.getUniqueId())) {
            PetData victimData = this.petManager.getPetData(victimPet.getUniqueId());
            if (victimData == null) return;

            if (victimData.isProtectedFromPlayers()) {
                if (damager instanceof Player) {
                    event.setCancelled(true);
                    return;
                }
                if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player) {
                    event.setCancelled(true);
                }
            }
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
        ItemStack item = e.getHand() == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (item.getType() != Material.AIR) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        if (!p.isSneaking()) return;

        Entity targetEntity = e.getRightClicked();
        if (!(targetEntity instanceof Tameable pet)) return;
        if (!pet.isTamed()) return;

        boolean isOwner = pet.getOwnerUniqueId() != null && pet.getOwnerUniqueId().equals(p.getUniqueId());
        boolean adminOverride = !isOwner && p.hasPermission("enhancedpets.admin"); 
        if (!isOwner && !adminOverride) return; 

        e.setCancelled(true);

        UUID uuid = p.getUniqueId();
        PendingClick prev = pending.get(uuid);

        if (prev != null && !prev.isExpired() && prev.entity.equals(targetEntity)) {
            pending.remove(uuid);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (adminOverride) { 
                    plugin.getGuiManager().setViewerOwnerOverride(p.getUniqueId(), pet.getOwnerUniqueId());
                }
                plugin.getGuiManager().openPetMenu(p, targetEntity.getUniqueId());
            });
            return;
        }

        boolean sitting = (targetEntity instanceof Sittable s) && s.isSitting();
        pending.put(uuid, new PendingClick(targetEntity, sitting, adminOverride)); 

        plugin.getServer().getScheduler().runTaskLater(plugin, task -> {
            PendingClick pc = pending.remove(uuid);
            if (pc == null || pc.isExpired()) return;

            if (pc.entity.isValid() && pc.entity instanceof Sittable s) {
                
                if (!pc.adminOverride) {
                    s.setSitting(!pc.wasSitting);
                }
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && event.getVehicle().getType().name().equalsIgnoreCase("HAPPY_GHAST")) {
            ghastMountTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (!plugin.getConfigManager().isHappyGhastFireballEnabled()) {
            return;
        }
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
        Fireball fireball = ((LivingEntity) vehicle).launchProjectile(Fireball.class, player.getLocation().getDirection().normalize().multiply(1.5));
        fireball.setShooter(player);
        fireball.setYield(1.5f);
        fireball.setIsIncendiary(true);
    }

    private static final class PendingClick { 
        final Entity entity;
        final boolean wasSitting;
        final boolean adminOverride; 
        final long expiry;

        PendingClick(Entity e, boolean sitting, boolean adminOverride) { 
            this.entity = e;
            this.wasSitting = sitting;
            this.adminOverride = adminOverride; 
            this.expiry = System.currentTimeMillis() + 250L;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }
    }


}



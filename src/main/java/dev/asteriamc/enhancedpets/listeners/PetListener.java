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
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;

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
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (event.getEntity() instanceof Tameable pet && event.getOwner() instanceof Player) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                if (pet.isValid() && pet.isTamed() && pet.getOwnerUniqueId() != null
                        && !this.petManager.isManagedPet(pet.getUniqueId())) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (this.petManager.isManagedPet(entity.getUniqueId()) && entity instanceof LivingEntity livingEntity) {
            UUID petUUID = entity.getUniqueId();
            PetData data = this.petManager.getPetData(petUUID);
            String name = data != null ? data.getDisplayName() : "Unknown Pet";

            // Notify Owner
            if (data != null) {
                Player owner = Bukkit.getPlayer(data.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    org.bukkit.Location loc = entity.getLocation();
                    String locStr = String.format("x=%d, y=%d, z=%d", loc.getBlockX(), loc.getBlockY(),
                            loc.getBlockZ());
                    plugin.getLanguageManager().sendReplacements(owner, "event.death", "pet", name, "location", locStr);
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 0.5f);
                }
            }

            this.plugin.debugLog("Managed pet " + name + " (UUID: " + petUUID + ") died. Marking as dead.");
            this.petManager.unregisterPet(livingEntity);
        } else if (entity instanceof Tameable t && t.isTamed() && t.getOwnerUniqueId() != null
                && entity instanceof LivingEntity le) {
            UUID ownerUUID = t.getOwnerUniqueId();
            UUID petUUID = t.getUniqueId();
            String fallbackName = entity.getCustomName();
            if (fallbackName != null)
                fallbackName = ChatColor.stripColor(fallbackName);
            if (fallbackName == null || fallbackName.isEmpty())
                fallbackName = formatEntityType(entity.getType());
            PetData snapshot = new PetData(petUUID, ownerUUID, entity.getType(), fallbackName);
            this.plugin.getPetManager().captureMetadata(snapshot, le);
            this.plugin.getPetManager().markPetDeadOffline(ownerUUID, petUUID, entity.getType(), fallbackName,
                    snapshot.getMetadata());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityUnleash(EntityUnleashEvent event) {
        if (event.getEntity() instanceof Tameable pet && this.petManager.isManagedPet(pet.getUniqueId())) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                PetData data = this.petManager.getPetData(pet.getUniqueId());
                if (data != null) {
                    if (!pet.isValid()) {
                        this.plugin.getLogger().fine("Managed pet " + data.getDisplayName()
                                + " became invalid after unleash. Keeping record.");
                    } else {
                        UUID currentOwner = pet.getOwnerUniqueId();
                        if (currentOwner == null || !currentOwner.equals(data.getOwnerUUID())) {
                            this.plugin.getLogger().warning("Pet " + data.getDisplayName()
                                    + " owner mismatch on unleash. Restoring owner if possible.");
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Tameable pet && this.petManager.isManagedPet(pet.getUniqueId())) {
            PetData data = this.petManager.getPetData(pet.getUniqueId());
            if (data != null) {
                // If pet is stationed or has an explicit target, prevent it from teleporting to
                // owner
                // Usually owner-teleport happens with Cause.UNKNOWN or sometimes
                // PLUGIN/ENDER_PEARL depending on server version/fork.
                // We want to allow teleports if they are explicitly done by our plugin (e.g.
                // GUI summon).
                // GUI summon likely uses PLUGIN cause. We might need a flag in PetData or a
                // metadata tag to allow "Force Teleport".
                // For now, let's block UNKNOWN which is the common "teleport to owner because
                // too far" cause in vanilla.
                // Also block PLUGIN if it's not our explicit force.
                // Actually, the easiest way to block "teleport to owner" without blocking "GUI
                // summon" is to check distance to owner?
                // No, "teleport to owner" happens when owner is far.
                // Let's assume our GUI summon uses standard teleport(), which is PLUGIN.
                // Creating a "just teleported" set might be needed if we want to allow GUI
                // teleports but block others.
                // But typically, simply blocking UNKNOWN covers vanilla following behavior.

                if (data.getStationLocation() != null || data.getExplicitTargetUUID() != null) {
                    // Check for forced teleport (e.g. from GUI summon)
                    if (pet.hasMetadata("force_teleport")) {
                        pet.removeMetadata("force_teleport", plugin);
                        return; // Allow teleport
                    }

                    // Block teleport if changing world OR distance is significant (> 8 blocks)
                    if (event.getFrom().getWorld() != event.getTo().getWorld() ||
                            event.getFrom().distanceSquared(event.getTo()) > 64) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Tameable pet && pet.isTamed() && pet.getOwnerUniqueId() != null
                    && !this.petManager.isManagedPet(pet.getUniqueId())) {
                this.plugin
                        .getLogger()
                        .log(Level.FINE, "Found unmanaged tamed pet {0} ({1}) in loaded chunk. Registering...",
                                new Object[] { pet.getType(), pet.getUniqueId() });
                this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                    if (pet.isValid() && pet.isTamed() && pet.getOwnerUniqueId() != null
                            && !this.petManager.isManagedPet(pet.getUniqueId())) {
                        this.petManager.registerPet(pet);
                    }
                }, 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.getEntity() instanceof Tameable babyPet) {
            UUID babyUUID = babyPet.getUniqueId();

            this.plugin
                    .getServer()
                    .getScheduler()
                    .runTaskLater(
                            this.plugin,
                            () -> {
                                if (this.plugin.getServer().getEntity(babyUUID) instanceof Tameable babyToCheck
                                        && babyToCheck.isValid()) {
                                    if (babyToCheck.isTamed()
                                            && babyToCheck.getOwnerUniqueId() != null
                                            && !this.petManager.isManagedPet(babyToCheck.getUniqueId())) {
                                        this.plugin.debugLog("Registering newly bred pet " + babyToCheck.getType()
                                                + " (UUID: " + babyToCheck.getUniqueId() + ")");
                                        this.petManager.registerPet(babyToCheck);
                                    }
                                }
                            },
                            2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPetRenameWithNameTag(PlayerInteractEntityEvent event) {
        ItemStack itemInHand = event.getPlayer().getInventory().getItem(event.getHand());

        if (itemInHand != null && itemInHand.getType() == Material.NAME_TAG && itemInHand.hasItemMeta()
                && itemInHand.getItemMeta().hasDisplayName()) {
            Entity clickedEntity = event.getRightClicked();

            if (clickedEntity instanceof Tameable && petManager.isManagedPet(clickedEntity.getUniqueId())) {
                UUID petUUID = clickedEntity.getUniqueId();
                PetData petData = petManager.getPetData(petUUID);

                if (petData != null) {
                    String newName = ChatColor.stripColor(itemInHand.getItemMeta().getDisplayName());
                    petData.setDisplayName(newName);
                    petManager.updatePetData(petData);
                    plugin.debugLog(
                            "Detected name tag rename for pet " + petUUID + ". Synced name to '" + newName + "'.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
                                if (tpd != null && (tpd.getOwnerUUID().equals(petData.getOwnerUUID())
                                        || petData.isFriendlyPlayer(tpd.getOwnerUUID()))) {
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
                            } else if (pet instanceof Wolf && target instanceof Creeper
                                    && this.plugin.getConfigManager().getDogCreeperBehavior().equals("FLEE")) {
                                event.setTarget(null);
                                event.setCancelled(true);
                            } else {
                                if (pet instanceof Cat && this.plugin.getConfigManager().isCatsAttackHostiles()
                                        && target instanceof Monster) {

                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        // Target Selection Mode Handler
        if (damager instanceof Player player) {
            Map<UUID, UUID> awaitingTarget = plugin.getPetGUIListener().getAwaitingTargetSelectionMap();
            if (awaitingTarget.containsKey(player.getUniqueId())) {
                UUID petUUID = awaitingTarget.get(player.getUniqueId());
                event.setCancelled(true);

                PetData petData = petManager.getPetData(petUUID);
                if (petData != null) {
                    if (!isValidSelectionTarget(victim)) {
                        plugin.getLanguageManager().sendMessage(player, "event.target_selection_invalid");
                        return;
                    }

                    petData.setExplicitTargetUUID(victim.getUniqueId());
                    petData.setStationLocation(null);
                    petManager.updatePetData(petData);

                    String tName = victim.getName();
                    if (victim instanceof Player p)
                        tName = p.getName();
                    else if (victim.getCustomName() != null)
                        tName = victim.getCustomName();
                    else
                        tName = victim.getType().name();

                    plugin.getLanguageManager().sendReplacements(player, "event.target_locked", "pet",
                            petData.getDisplayName(), "target", tName);

                    plugin.getGuiManager().openPetMenu(player, petUUID);
                    awaitingTarget.remove(player.getUniqueId());
                }
                return;
            }
        }

        if (this.petManager.isManagedPet(event.getDamager().getUniqueId())) {
            PetData petData = this.petManager.getPetData(event.getDamager().getUniqueId());
            if (petData == null)
                return;

            if (petData.getMode() == BehaviorMode.PASSIVE) {
                event.setCancelled(true);
                return;
            }

            if (plugin.getPetManager().isManagedPet(victim.getUniqueId())) {
                PetData vpd = plugin.getPetManager().getPetData(victim.getUniqueId());
                if (vpd != null && (vpd.getOwnerUUID().equals(petData.getOwnerUUID())
                        || petData.isFriendlyPlayer(vpd.getOwnerUUID()))) {
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
            if (victimData == null)
                return;

            if (victimData.isProtectedFromPlayers()) {
                if (damager instanceof Player p) {
                    // Allow owner to hurt their own pet even if protected
                    if (victimData.getOwnerUUID().equals(p.getUniqueId())) {
                        return;
                    }
                    event.setCancelled(true);
                    return;
                }
                if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
                    // Allow owner to hurt their own pet with projectiles
                    if (victimData.getOwnerUUID().equals(p.getUniqueId())) {
                        return;
                    }
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity))
            return;

        if (!this.petManager.isManagedPet(livingEntity.getUniqueId()))
            return;

        PetData data = this.petManager.getPetData(livingEntity.getUniqueId());
        if (data == null)
            return;

        Player owner = Bukkit.getPlayer(data.getOwnerUUID());
        if (owner == null || !owner.isOnline())
            return;

        if (livingEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null)
            return;

        double maxHealth = livingEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        double currentHealth = livingEntity.getHealth();
        double finalDamage = event.getFinalDamage();
        double newHealth = currentHealth - finalDamage;

        if (newHealth <= 0)
            return; // Let death event handle it

        double pctBefore = currentHealth / maxHealth;
        double pctAfter = newHealth / maxHealth;

        // Check thresholds crossing downwards (Exclusive checks ensures we only send
        // the
        // most severe warning)
        if (pctBefore > 0.05 && pctAfter <= 0.05) {
            plugin.getLanguageManager().sendReplacements(owner, "event.hp_danger", "pet", data.getDisplayName());
            owner.playSound(owner.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        } else if (pctBefore > 0.10 && pctAfter <= 0.10) {
            plugin.getLanguageManager().sendReplacements(owner, "event.hp_critical", "pet", data.getDisplayName());
            owner.playSound(owner.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        } else if (pctBefore > 0.25 && pctAfter <= 0.25) {
            plugin.getLanguageManager().sendReplacements(owner, "event.hp_warning", "pet", data.getDisplayName());
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
                    plugin.getLanguageManager().sendMessage(player, "event.ghast_tame_success");
                } else {
                    plugin.getLanguageManager().sendMessage(player, "event.ghast_tame_fail");
                }
            } else {
                plugin.getLanguageManager().sendMessage(player, "event.ghast_already_tamed");
            }
            return;
        }
        if (!plugin.getConfigManager().isShiftDoubleClickGUI())
            return;
        ItemStack item = e.getHand() == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (item.getType() != Material.AIR)
            return;
        if (e.getHand() != EquipmentSlot.HAND)
            return;

        Player p = e.getPlayer();
        if (!p.isSneaking())
            return;

        Entity targetEntity = e.getRightClicked();
        if (!(targetEntity instanceof Tameable pet))
            return;
        if (!pet.isTamed())
            return;

        boolean isOwner = pet.getOwnerUniqueId() != null && pet.getOwnerUniqueId().equals(p.getUniqueId());
        boolean adminOverride = !isOwner && p.hasPermission("enhancedpets.admin");
        if (!isOwner && !adminOverride)
            return;

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
            if (pc == null || pc.isExpired())
                return;

            if (pc.entity.isValid() && pc.entity instanceof Sittable s) {

                if (!pc.adminOverride) {
                    s.setSitting(!pc.wasSitting);
                }
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player
                && event.getVehicle().getType().name().equalsIgnoreCase("HAPPY_GHAST")) {
            ghastMountTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Map<UUID, UUID> awaitingTarget = plugin.getPetGUIListener().getAwaitingTargetSelectionMap();

        if (!awaitingTarget.containsKey(player.getUniqueId()))
            return;

        if (event.getAction().name().contains("RIGHT_CLICK")) {
            UUID petUUID = awaitingTarget.remove(player.getUniqueId());
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "Target selection cancelled.");

            if (petManager.getPetData(petUUID) != null) {
                plugin.getGuiManager().openPetMenu(player, petUUID);
            }
        } else if (event.getAction().name().contains("LEFT_CLICK")) {
            event.setCancelled(true);
            // Raytrace because they didn't hit an entity directly (or logic handled here)

            UUID petUUID = awaitingTarget.get(player.getUniqueId());
            org.bukkit.util.RayTraceResult result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    50,
                    e -> e instanceof LivingEntity
                            && !e.getUniqueId().equals(player.getUniqueId())
                            && !e.getUniqueId().equals(petUUID));

            if (result != null && result.getHitEntity() != null) {
                Entity target = result.getHitEntity();
                // Validate
                if (!isValidSelectionTarget(target)) {
                    plugin.getLanguageManager().sendMessage(player, "event.target_selection_invalid");
                    return;
                }

                // Success
                awaitingTarget.remove(player.getUniqueId());
                PetData petData = petManager.getPetData(petUUID);
                if (petData != null) {
                    petData.setExplicitTargetUUID(target.getUniqueId());
                    petData.setStationLocation(null);
                    petManager.updatePetData(petData);

                    String tName = target.getName();
                    if (target instanceof Player p)
                        tName = p.getName();
                    else if (target.getCustomName() != null)
                        tName = target.getCustomName();
                    else
                        tName = target.getType().name();

                    plugin.getLanguageManager().sendReplacements(player, "event.target_locked", "pet",
                            petData.getDisplayName(), "target", tName);

                    plugin.getGuiManager().openPetMenu(player, petUUID);
                }
            } else {
                plugin.getLanguageManager().sendMessage(player, "event.target_selection_none");
            }
        }
    }

    private boolean isValidSelectionTarget(Entity entity) {
        if (!(entity instanceof LivingEntity le))
            return false;
        if (entity instanceof Player)
            return true;
        if (isHostile(le))
            return true;
        if (isAnimal(le))
            return true;
        return false;
    }

    private boolean isHostile(LivingEntity entity) {
        if (entity instanceof Monster)
            return true;
        if (entity instanceof Slime)
            return true;
        if (entity instanceof Ghast)
            return true;
        if (entity instanceof Phantom)
            return true;
        if (entity instanceof Shulker)
            return true;
        return false;
    }

    private boolean isAnimal(LivingEntity entity) {
        if (entity instanceof Animals)
            return true;
        if (entity instanceof WaterMob)
            return true;
        if (entity instanceof Ambient)
            return true;
        return false;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING)
            return;
        Player player = event.getPlayer();
        if (!plugin.getConfigManager().isHappyGhastFireballEnabled()) {
            return;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle == null || !vehicle.getType().name().equalsIgnoreCase("HAPPY_GHAST"))
            return;
        PetData petData = petManager.getPetData(vehicle.getUniqueId());
        if (petData == null || !petData.getOwnerUUID().equals(player.getUniqueId()))
            return;

        if (ghastMountTime.containsKey(player.getUniqueId())) {
            long mountTime = ghastMountTime.get(player.getUniqueId());
            if (System.currentTimeMillis() - mountTime < 1000)
                return;
            ghastMountTime.remove(player.getUniqueId());
        }
        long now = System.currentTimeMillis();
        if (ghastFireballCooldown.containsKey(player.getUniqueId())) {
            long last = ghastFireballCooldown.get(player.getUniqueId());
            if (now - last < 1000)
                return;
        }
        ghastFireballCooldown.put(player.getUniqueId(), now);
        Fireball fireball = ((LivingEntity) vehicle).launchProjectile(Fireball.class,
                player.getLocation().getDirection().normalize().multiply(1.5));
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
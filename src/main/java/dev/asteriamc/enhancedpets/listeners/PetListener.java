package dev.asteriamc.enhancedpets.listeners;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.CreeperBehavior;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PetListener implements Listener {
    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final ShiftClickTracker shiftTracker = new ShiftClickTracker();
    private final Map<UUID, PendingClick> pending = new ConcurrentHashMap<>();

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
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (this.petManager.isManagedPet(entity.getUniqueId()) && entity instanceof LivingEntity livingEntity) {
            UUID petUUID = entity.getUniqueId();
            PetData data = this.petManager.getPetData(petUUID);
            String name = data != null ? data.getDisplayName()
                    : plugin.getLanguageManager().getString("pet.unknown_name");

            // Notify Owner
            if (data != null) {
                Player owner = Bukkit.getPlayer(data.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    org.bukkit.Location loc = entity.getLocation();
                    String locStr = plugin.getLanguageManager().getStringReplacements("misc.location_short",
                            "x", String.valueOf(loc.getBlockX()),
                            "y", String.valueOf(loc.getBlockY()),
                            "z", String.valueOf(loc.getBlockZ()));
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
            if (fallbackName == null || fallbackName.isEmpty()) {
                // Append short UUID for uniqueness
                String shortId = petUUID.toString().substring(0, 4);
                fallbackName = plugin.getLanguageManager().getStringReplacements("pet.fallback_name_format", "type",
                        formatEntityType(entity.getType()), "id", shortId);
            }
            PetData snapshot = new PetData(petUUID, ownerUUID, entity.getType(), fallbackName);
            this.plugin.getPetManager().captureMetadata(snapshot, le);
            this.plugin.getPetManager().markPetDeadOffline(ownerUUID, petUUID, entity.getType(), fallbackName,
                    snapshot.getMetadata());
        }

        // Fix: Target Neutralized Logic
        // If the checking entity was a target of any pet, clear it
        UUID deadUUID = entity.getUniqueId();
        for (PetData petData : this.petManager.getAllPetData()) {
            if (deadUUID.equals(petData.getExplicitTargetUUID())) {
                petData.setExplicitTargetUUID(null);
                petData.setStationLocation(null); // Usually stationing clears target, but if target dies, we just reset
                this.petManager.updatePetData(petData);

                Player owner = Bukkit.getPlayer(petData.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    this.plugin.getLanguageManager().sendReplacements(owner, "event.target_neutralized", "pet",
                            petData.getDisplayName(), "target", entity.getName());
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Creeper creeper) {
            UUID creeperUUID = creeper.getUniqueId();

            // Check if this creeper was a target for any pet
            for (PetData petData : this.petManager.getAllPetData()) {
                if (creeperUUID.equals(petData.getExplicitTargetUUID())) {
                    petData.setExplicitTargetUUID(null);
                    petData.setStationLocation(null);
                    this.petManager.updatePetData(petData);

                    Player owner = Bukkit.getPlayer(petData.getOwnerUUID());
                    if (owner != null && owner.isOnline()) {
                        this.plugin.getLanguageManager().sendReplacements(owner, "event.target_neutralized", "pet",
                                petData.getDisplayName(), "target", "Creeper");
                        owner.playSound(owner.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                    }

                    // Stop the pet from attacking
                    Entity petEntity = Bukkit.getEntity(petData.getPetUUID());
                    if (petEntity instanceof Creature c) {
                        c.setTarget(null);
                    }
                }
            }
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
                // Check for forced teleport (e.g. from GUI summon) - always allow
                if (pet.hasMetadata("force_teleport")) {
                    pet.removeMetadata("force_teleport", plugin);
                    return; // Allow teleport
                }

                // Stationed pets: Allow teleport - the GUI summon updates station location
                // We don't block stationed pets here anymore.

                // Explicit Target: Block natural teleport to owner if targeting something
                if (data.getExplicitTargetUUID() != null) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Cache eviction for offline owners: if an offline owner's last pet unloads,
        // clear their cached data
        Chunk chunk = event.getChunk();
        Set<UUID> offlineOwnersInChunk = new HashSet<>();

        for (Entity entity : chunk.getEntities()) {
            if (this.petManager.isManagedPet(entity.getUniqueId())) {
                PetData data = this.petManager.getPetData(entity.getUniqueId());
                if (data != null && !this.petManager.isOwnerLoaded(data.getOwnerUUID())) {
                    offlineOwnersInChunk.add(data.getOwnerUUID());
                }
            }
        }

        // For each offline owner, check if they have any remaining pets in loaded
        // chunks
        for (UUID offlineOwner : offlineOwnersInChunk) {
            boolean hasLoadedPets = false;
            for (PetData pet : this.petManager.getPetsOwnedBy(offlineOwner)) {
                Entity petEntity = Bukkit.getEntity(pet.getPetUUID());
                // If any pet exists and is in a loaded chunk (not in this unloading chunk),
                // keep data
                if (petEntity != null && petEntity.isValid() && !petEntity.getLocation().getChunk().equals(chunk)) {
                    hasLoadedPets = true;
                    break;
                }
            }

            if (!hasLoadedPets) {
                // Evict all data for this offline owner
                this.petManager.evictOfflineOwnerData(offlineOwner);
                this.plugin.debugLog("Evicted cached pet data for offline owner: " + offlineOwner);
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
                            } else if (target instanceof Creeper) {
                                // Per-pet creeper behavior
                                CreeperBehavior cb = petData.getCreeperBehavior();
                                if (cb == CreeperBehavior.FLEE || cb == CreeperBehavior.IGNORE) {
                                    // Don't target creepers in FLEE or IGNORE mode
                                    event.setTarget(null);
                                    event.setCancelled(true);
                                } else if (cb == CreeperBehavior.NEUTRAL) {
                                    // NEUTRAL: Only attack if owner attacked the creeper (handled elsewhere)
                                    // Block automatic targeting here
                                    event.setTarget(null);
                                    event.setCancelled(true);
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
            Map<UUID, Boolean> awaitingBatchTarget = plugin.getPetGUIListener().getAwaitingBatchTargetSelectionMap();

            if (awaitingTarget.containsKey(player.getUniqueId())) {
                UUID petUUID = awaitingTarget.get(player.getUniqueId());
                event.setCancelled(true);

                PetData petData = petManager.getPetData(petUUID);
                if (petData != null) {
                    if (!isValidSelectionTarget(victim)) {
                        plugin.getLanguageManager().sendMessage(player, "event.target_selection_invalid");
                        return;
                    }

                    String tName = victim.getName();
                    if (victim instanceof Player p)
                        tName = p.getName();
                    else if (victim.getCustomName() != null)
                        tName = victim.getCustomName();
                    else
                        tName = victim.getType().name();

                    petData.setExplicitTargetUUID(victim.getUniqueId());
                    petData.setExplicitTargetName(tName);
                    petData.setStationLocation(null);
                    petManager.updatePetData(petData);

                    plugin.getLanguageManager().sendReplacements(player, "event.target_locked", "pet",
                            petData.getDisplayName(), "target", tName);

                    plugin.getGuiManager().openPetMenu(player, petUUID);
                    awaitingTarget.remove(player.getUniqueId());
                }
                return;
            } else if (awaitingBatchTarget.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                if (!isValidSelectionTarget(victim)) {
                    plugin.getLanguageManager().sendMessage(player, "event.target_selection_invalid");
                    return;
                }

                Set<UUID> selectedPets = plugin.getGuiManager().getBatchActionsGUI().getPlayerSelections()
                        .get(player.getUniqueId());
                if (selectedPets != null && !selectedPets.isEmpty()) {
                    int count = 0;
                    for (UUID pid : selectedPets) {
                        PetData pd = petManager.getPetData(pid);
                        if (pd != null && plugin.getGuiManager().canAttack(pd.getEntityType())) {
                            String tName = victim.getName();
                            if (victim instanceof Player p)
                                tName = p.getName();
                            else if (victim.getCustomName() != null)
                                tName = victim.getCustomName();
                            else
                                tName = victim.getType().name();

                            pd.setExplicitTargetUUID(victim.getUniqueId());
                            pd.setExplicitTargetName(tName);
                            pd.setStationLocation(null);
                            petManager.updatePetData(pd);
                            count++;
                        }
                    }
                    String tName = victim.getName();
                    if (victim instanceof Player p)
                        tName = p.getName();
                    else if (victim.getCustomName() != null)
                        tName = victim.getCustomName();
                    else
                        tName = victim.getType().name();

                    plugin.getLanguageManager().sendReplacements(player, "event.batch_target_locked", "count",
                            String.valueOf(count), "target", tName);
                    plugin.getGuiManager().openBatchManagementMenu(player, selectedPets);
                }
                awaitingBatchTarget.remove(player.getUniqueId());
                return;
            }

            // NEUTRAL Creeper Behavior: When player attacks a creeper, pets with NEUTRAL
            // mode target it
            if (victim instanceof Creeper creeper) {
                for (PetData petData : petManager.getPetsOwnedBy(player.getUniqueId())) {
                    if (petData.getCreeperBehavior() != CreeperBehavior.NEUTRAL)
                        continue;
                    if (petData.getMode() == BehaviorMode.PASSIVE)
                        continue;
                    if (petData.isDead() || petData.isStored())
                        continue;

                    Entity petEntity = Bukkit.getEntity(petData.getPetUUID());
                    if (petEntity instanceof Creature petCreature && petEntity.isValid()) {
                        // Only trigger if pet is reasonably close (within 32 blocks)
                        if (petCreature.getLocation().distanceSquared(creeper.getLocation()) <= 1024) {
                            petCreature.setTarget(creeper);
                        }
                    }
                }
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
        Entity targetEntity = e.getRightClicked();

        // [Security] Access Control for Managed Pets
        if (petManager.isManagedPet(targetEntity.getUniqueId())) {
            PetData pd = petManager.getPetData(targetEntity.getUniqueId());
            if (pd != null) {
                boolean isAllowed = pd.isPublicAccess() ||
                        pd.getOwnerUUID().equals(player.getUniqueId()) ||
                        pd.isFriendlyPlayer(player.getUniqueId()) ||
                        player.hasPermission("enhancedpets.admin");

                if (!isAllowed) {
                    e.setCancelled(true);
                    // Only send message if they are interacting with a rideable/inventory entity
                    // to avoid spamming for simple right-clicks on non-interactable pets?
                    // Actually, right-clicking usually implies intent.
                    plugin.getLanguageManager().sendMessage(player, "event.ride_denied");
                    return;
                }
            }
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

        // targetEntity is already defined at start of method.
        // Entity targetEntity = e.getRightClicked();

        // Check if it's a tameable pet OR a managed non-tameable pet (like Happy Ghast)
        boolean isTameablePet = false;
        boolean isNonTameableManagedPet = false;
        UUID petOwnerUUID = null;

        if (targetEntity instanceof Tameable pet && pet.isTamed()) {
            isTameablePet = true;
            petOwnerUUID = pet.getOwnerUniqueId();
        } else if (petManager.isManagedPet(targetEntity.getUniqueId())) {
            // Non-tameable managed pet (e.g., Happy Ghast)
            isNonTameableManagedPet = true;
            var petData = petManager.getPetData(targetEntity.getUniqueId());
            if (petData != null) {
                petOwnerUUID = petData.getOwnerUUID();
            }
        }

        if (!isTameablePet && !isNonTameableManagedPet)
            return;
        if (petOwnerUUID == null)
            return;

        boolean isOwner = petOwnerUUID.equals(p.getUniqueId());
        boolean adminOverride = !isOwner && p.hasPermission("enhancedpets.admin");
        if (!isOwner && !adminOverride)
            return;

        e.setCancelled(true);

        UUID uuid = p.getUniqueId();
        PendingClick prev = pending.get(uuid);

        if (prev != null && !prev.isExpired() && prev.entity.equals(targetEntity)) {
            pending.remove(uuid);
            final UUID finalPetOwnerUUID = petOwnerUUID;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (adminOverride) {
                    plugin.getGuiManager().setViewerOwnerOverride(p.getUniqueId(), finalPetOwnerUUID);
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

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Map<UUID, UUID> awaitingTarget = plugin.getPetGUIListener().getAwaitingTargetSelectionMap();
        Map<UUID, Boolean> awaitingBatchTarget = plugin.getPetGUIListener().getAwaitingBatchTargetSelectionMap();

        if (!awaitingTarget.containsKey(player.getUniqueId()) && !awaitingBatchTarget.containsKey(player.getUniqueId()))
            return;

        if (event.getAction().name().contains("RIGHT_CLICK")) {
            boolean isBatch = awaitingBatchTarget.containsKey(player.getUniqueId());
            UUID petUUID = awaitingTarget.remove(player.getUniqueId());
            awaitingBatchTarget.remove(player.getUniqueId());

            event.setCancelled(true);
            plugin.getLanguageManager().sendMessage(player, "gui.target_selection_cancelled");

            if (isBatch) {
                Set<UUID> selectedPets = plugin.getGuiManager().getBatchActionsGUI().getPlayerSelections()
                        .get(player.getUniqueId());
                if (selectedPets != null) {
                    plugin.getGuiManager().openBatchManagementMenu(player, selectedPets);
                } else {
                    plugin.getGuiManager().openMainMenu(player);
                }
            } else if (petUUID != null && petManager.getPetData(petUUID) != null) {
                plugin.getGuiManager().openPetMenu(player, petUUID);
            }
        } else if (event.getAction().name().contains("LEFT_CLICK")) {
            event.setCancelled(true);
            // Raytrace because they didn't hit an entity directly (or logic handled here)

            boolean isBatch = awaitingBatchTarget.containsKey(player.getUniqueId());
            UUID petUUID = awaitingTarget.get(player.getUniqueId());

            org.bukkit.util.RayTraceResult result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    50,
                    e -> e instanceof LivingEntity
                            && !e.getUniqueId().equals(player.getUniqueId())
                            && (petUUID == null || !e.getUniqueId().equals(petUUID)));

            if (result != null && result.getHitEntity() != null) {
                Entity target = result.getHitEntity();
                // Validate
                if (!isValidSelectionTarget(target)) {
                    plugin.getLanguageManager().sendMessage(player, "event.target_selection_invalid");
                    return;
                }

                if (isBatch) {
                    awaitingBatchTarget.remove(player.getUniqueId());
                    Set<UUID> selectedPets = plugin.getGuiManager().getBatchActionsGUI().getPlayerSelections()
                            .get(player.getUniqueId());
                    if (selectedPets != null && !selectedPets.isEmpty()) {
                        int count = 0;
                        for (UUID pid : selectedPets) {
                            PetData pd = petManager.getPetData(pid);
                            if (pd != null && plugin.getGuiManager().canAttack(pd.getEntityType())) {
                                String tName = target.getName();
                                if (target instanceof Player p)
                                    tName = p.getName();
                                else if (target.getCustomName() != null)
                                    tName = target.getCustomName();
                                else
                                    tName = target.getType().name();

                                pd.setExplicitTargetUUID(target.getUniqueId());
                                pd.setExplicitTargetName(tName);
                                pd.setStationLocation(null);
                                petManager.updatePetData(pd);
                                count++;
                            }
                        }
                        String tName = target.getName();
                        if (target instanceof Player p)
                            tName = p.getName();
                        else if (target.getCustomName() != null)
                            tName = target.getCustomName();
                        else
                            tName = target.getType().name();

                        plugin.getLanguageManager().sendReplacements(player, "event.batch_target_locked", "count",
                                String.valueOf(count), "target", tName);
                        plugin.getGuiManager().openBatchManagementMenu(player, selectedPets);
                    } else {
                        plugin.getLanguageManager().sendMessage(player, "gui.batch_no_selection");
                        plugin.getGuiManager().openMainMenu(player);
                    }
                } else {
                    // Success Single
                    awaitingTarget.remove(player.getUniqueId());
                    PetData petData = petManager.getPetData(petUUID);
                    if (petData != null) {
                        String tName = target.getName();
                        if (target instanceof Player p)
                            tName = p.getName();
                        else if (target.getCustomName() != null)
                            tName = target.getCustomName();
                        else
                            tName = target.getType().name();

                        petData.setExplicitTargetUUID(target.getUniqueId());
                        petData.setExplicitTargetName(tName);
                        petData.setStationLocation(null);
                        petManager.updatePetData(petData);

                        plugin.getLanguageManager().sendReplacements(player, "event.target_locked", "pet",
                                petData.getDisplayName(), "target", tName);

                        plugin.getGuiManager().openPetMenu(player, petUUID);
                    }
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && event.getVehicle() instanceof LivingEntity vehicle) {
            if (petManager.isManagedPet(vehicle.getUniqueId())) {
                PetData petData = petManager.getPetData(vehicle.getUniqueId());
                if (petData != null) {
                    // Check access
                    if (petData.isPublicAccess())
                        return; // Open to all
                    if (petData.getOwnerUUID().equals(player.getUniqueId()))
                        return; // Owner
                    if (petData.isFriendlyPlayer(player.getUniqueId()))
                        return; // Friendly (Trusted)
                    if (player.hasPermission("enhancedpets.admin"))
                        return; // Admin

                    event.setCancelled(true);
                    plugin.getLanguageManager().sendMessage(player, "event.ride_denied");
                }
            }
        }
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
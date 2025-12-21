package dev.asteriamc.enhancedpets.tasks;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PetTargetingTask extends BukkitRunnable {
    private static final int PETS_PER_TICK = 50;
    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final double scanRadius;
    private final double verticalScanRadius;
    private int rrIndex = 0;

    public PetTargetingTask(Enhancedpets plugin, PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.scanRadius = 16.0;
        this.verticalScanRadius = 8.0;
    }

    @Override
    public void run() {
        List<PetData> pets = new ArrayList<>();
        // Filter pets that need active targeting logic
        for (PetData pd : this.petManager.getAllPetData()) {
            if (pd.getMode() == BehaviorMode.AGGRESSIVE
                    || pd.getStationLocation() != null
                    || pd.getExplicitTargetUUID() != null) {
                pets.add(pd);
            }
        }
        if (pets.isEmpty())
            return;

        int size = pets.size();
        int processed = 0;

        while (processed < PETS_PER_TICK && processed < size) {
            PetData petData = pets.get(rrIndex % size);
            rrIndex++;
            processed++;

            Entity entity = Bukkit.getEntity(petData.getPetUUID());
            if (!(entity instanceof Creature petCreature) || !entity.isValid() || entity.isDead())
                continue;

            // 1. Explicit Target Handling
            if (petData.getExplicitTargetUUID() != null) {
                handleExplicitTarget(petCreature, petData);
                continue; // Skip other behaviors if locked on target
            }

            // 2. Station Feature Handling
            if (petData.getStationLocation() != null) {
                handleStationBehavior(petCreature, petData);
                continue; // Skip Aggressive fallback if Statioend (Station handles its own aggression)
            }

            // 3. Fallback: Aggressive Mode
            if (petData.getMode() == BehaviorMode.AGGRESSIVE) {
                handleAggressiveBehavior(petCreature, petData);
            }
        }
    }

    private void handleExplicitTarget(Creature pet, PetData petData) {
        // Explicit Target pets must always stand to hunt
        if (pet instanceof Sittable s && s.isSitting())
            s.setSitting(false);

        Entity targetEntity = Bukkit.getEntity(petData.getExplicitTargetUUID());

        // 1. Mission Complete Check: If target is dead, clear the contract
        if (targetEntity != null && targetEntity.isDead()) {
            petData.setExplicitTargetUUID(null);
            petData.setStationLocation(null); // Ensure clean state
            petManager.updatePetData(petData);

            pet.setTarget(null);

            // Visual/Audio Feedback
            pet.getWorld().playSound(pet.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            Player owner = Bukkit.getPlayer(petData.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                String tName = targetEntity.getName();
                if (targetEntity instanceof Player p)
                    tName = p.getName();
                owner.sendMessage(ChatColor.GREEN + "[Pet] Target " + ChatColor.RED + tName + ChatColor.GREEN
                        + " neutralized. Returning to you.");
            }
            return;
        }

        // Validation (Unloaded or Invalid but not dead)
        if (targetEntity == null || !targetEntity.isValid()) {
            // Target might be unloaded. We wait.
            // Do NOT clear target here, as we want to resume hunt if they load back in.
            return;
        }

        if (!(targetEntity instanceof LivingEntity targetLiving))
            return;

        // Player Target Check: Path Chunks Loaded
        if (targetLiving instanceof Player) {
            if (!isPathChunksLoaded(pet.getLocation(), targetLiving.getLocation())) {
                // Path not loaded. Do not target.
                return;
            }
        }
        // Mob Target Check: In radius of player?
        else {
            // "for entity mob target it targets loaded entities in user specified radius
            // near players"
            // We assume user specified radius of the targeting feature, let's use a
            // reasonable default or global config.
            // Since we don't have a specific radius stored for 'mob target', we'll use
            // scanRadius.
            if (!isMobNearAnyPlayer(targetLiving, scanRadius)) {
                return;
            }
        }

        // Feature: Visual Feedback for explicit targeting
        pet.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, pet.getEyeLocation().add(0, 0.5, 0), 1, 0, 0,
                0, 0);

        // Feature: Long Range Navigation & Speed Boost
        double distSq = pet.getLocation().distanceSquared(targetLiving.getLocation());
        if (distSq > 400) { // > 20 blocks
            // Use navigation for long distance
            pet.getPathfinder().moveTo(targetLiving.getLocation());
            // Give speed boost to catch up
            pet.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 40, 1));
        } else {
            // Close enough for standard targeting
            pet.setTarget(targetLiving);
        }
    }

    private void handleStationBehavior(Creature pet, PetData petData) {
        Location station = petData.getStationLocation();
        if (station == null || station.getWorld() == null || !station.getWorld().equals(pet.getWorld())) {
            // Wrong world or invalid station.
            return;
        }

        Sittable sittable = (pet instanceof Sittable) ? (Sittable) pet : null;
        double r = petData.getStationRadius();
        double leashRangeSq = (r * 1.5) * (r * 1.5);
        double distSq = pet.getLocation().distanceSquared(station);

        // Leash Logic: If too far from station, ignore targets and return
        if (distSq > leashRangeSq) {
            if (sittable != null && sittable.isSitting())
                sittable.setSitting(false);
            pet.setTarget(null);
            pet.getPathfinder().moveTo(station);
            return;
        }

        // Passive Mode Check: If Passive, do not attack/scan. Just stay at station.
        if (petData.getMode() == BehaviorMode.PASSIVE) {
            pet.setTarget(null);
            if (distSq > 4) {
                if (sittable != null && sittable.isSitting())
                    sittable.setSitting(false);
                pet.getPathfinder().moveTo(station);
            } else {
                // Sit if at station and passive
                if (sittable != null && !sittable.isSitting())
                    sittable.setSitting(true);
            }
            return;
        }

        // Check if current target is valid
        LivingEntity currentTarget = pet.getTarget();
        if (currentTarget != null && currentTarget.isValid() && !currentTarget.isDead()) {
            // Is target within station radius?
            if (currentTarget.getLocation().distanceSquared(station) <= (r * r)) {
                if (sittable != null && sittable.isSitting())
                    sittable.setSitting(false); // Ensure standing to fight
                return; // Keep attacking
            }
        }

        // Scan for new targets at station
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        // We scan entities near the STATION, not the pet
        for (Entity e : station.getWorld().getNearbyEntities(station, r, r, r)) {
            if (!(e instanceof LivingEntity target))
                continue;
            if (e.equals(pet))
                continue;
            if (!isValidTarget(pet, petData, target))
                continue;

            // Filter by station settings
            boolean validStationTarget = false;
            Set<String> types = petData.getStationTargetTypes();
            if (types.contains("PLAYER") && target instanceof Player)
                validStationTarget = true;
            if (types.contains("MOB") && isHostile(target))
                validStationTarget = true;
            if (types.contains("ANIMAL") && isAnimal(target))
                validStationTarget = true;
            // Also support broad match if set is empty? Or default to nothing?
            // User said "targets like mobs or players".

            if (!validStationTarget)
                continue;

            double d = e.getLocation().distanceSquared(station);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = target;
            }
        }

        if (best != null) {
            if (sittable != null && sittable.isSitting())
                sittable.setSitting(false); // Stand to engage
            pet.setTarget(best);
        } else {
            // No target.
            if (distSq > 4) {
                // Return to station if far
                if (sittable != null && sittable.isSitting())
                    sittable.setSitting(false);
                pet.getPathfinder().moveTo(station);
            } else {
                // At station, no target -> Sit
                if (sittable != null && !sittable.isSitting())
                    sittable.setSitting(true);
            }
        }
    }

    private boolean isHostile(LivingEntity entity) {
        if (entity instanceof Monster)
            return true;
        if (entity instanceof Slime)
            return true; // Covers MagmaCube
        if (entity instanceof Ghast)
            return true;
        if (entity instanceof Phantom)
            return true;
        if (entity instanceof Shulker)
            return true;
        // Hoglin/Zoglin usually implement Monster or Enemy, but explicit check if
        // needed.
        // For now, this covers the vast majority of non-Monster hostiles.
        return false;
    }

    private boolean isAnimal(LivingEntity entity) {
        if (entity instanceof Animals)
            return true; // Sheep, Cow, Pig, Wolf, etc.
        if (entity instanceof WaterMob)
            return true; // Squid, Fish, Dolphin
        if (entity instanceof Ambient)
            return true; // Bat
        return false;
    }

    private void handleAggressiveBehavior(Creature petCreature, PetData petData) {
        LivingEntity currentTarget = petCreature.getTarget();
        if (currentTarget != null && currentTarget.isValid() && !currentTarget.isDead())
            return;

        LivingEntity bestTarget = null;
        double bestTargetDistanceSq = Double.MAX_VALUE;

        for (Entity nearby : petCreature.getNearbyEntities(this.scanRadius, this.verticalScanRadius, this.scanRadius)) {
            if (!(nearby instanceof LivingEntity target))
                continue;
            if (!isValidTarget(petCreature, petData, target))
                continue;

            // Filter by aggressive settings
            boolean validAggressiveTarget = false;
            Set<String> types = petData.getAggressiveTargetTypes();
            if (types.contains("PLAYER") && target instanceof Player)
                validAggressiveTarget = true;
            if (types.contains("MOB") && isHostile(target))
                validAggressiveTarget = true;
            if (types.contains("ANIMAL") && isAnimal(target))
                validAggressiveTarget = true;

            if (!validAggressiveTarget)
                continue;

            double distanceSq = petCreature.getLocation().distanceSquared(target.getLocation());
            if (distanceSq < bestTargetDistanceSq) {
                bestTarget = target;
                bestTargetDistanceSq = distanceSq;
            }
        }

        if (bestTarget != null) {
            petCreature.setTarget(bestTarget);
        }
    }

    // Common validation logic
    private boolean isValidTarget(Creature pet, PetData ownerData, LivingEntity target) {
        if (target.equals(pet) || target.isDead() || !target.isValid())
            return false;

        // Check Managed Pet
        if (plugin.getPetManager().isManagedPet(target.getUniqueId())) {
            PetData tpd = plugin.getPetManager().getPetData(target.getUniqueId());
            if (tpd != null) {
                if (tpd.getOwnerUUID().equals(ownerData.getOwnerUUID()))
                    return false; // Own pet
                if (ownerData.isFriendlyPlayer(tpd.getOwnerUUID()))
                    return false; // Friend's pet
            }
        } else if (target instanceof Tameable nearbyTameable && nearbyTameable.isTamed()
                && ownerData.getOwnerUUID().equals(nearbyTameable.getOwnerUniqueId())) {
            return false;
        }

        // Check Owner/Friendly
        if (target.getUniqueId().equals(ownerData.getOwnerUUID()))
            return false;
        if (ownerData.isFriendlyPlayer(target.getUniqueId()))
            return false;

        // Check Player Protection
        if (target instanceof Player && ownerData.isProtectedFromPlayers())
            return false;
        if (target instanceof Player p && p.getGameMode() == GameMode.SPECTATOR)
            return false;

        // LOS
        if (!pet.hasLineOfSight(target))
            return false;

        return true;
    }

    private boolean isPathChunksLoaded(Location from, Location to) {
        if (from.getWorld() != to.getWorld())
            return false;

        World world = from.getWorld();
        Vector vec = to.toVector().subtract(from.toVector());
        double length = vec.length();
        vec.normalize();

        // Sample points every 16 blocks (Chunk width) to ensure we hit chunks
        for (double d = 0; d < length; d += 16.0) {
            Vector point = from.toVector().add(vec.clone().multiply(d));
            if (!world.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)) {
                return false;
            }
        }
        // Check end point
        return world.isChunkLoaded(to.getBlockX() >> 4, to.getBlockZ() >> 4);
    }

    private boolean isMobNearAnyPlayer(LivingEntity mob, double radius) {
        // This can be expensive if we scan all players.
        // Optimization: Check mob's nearby entities for players.
        for (Entity e : mob.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player)
                return true;
        }
        return false;
    }
}

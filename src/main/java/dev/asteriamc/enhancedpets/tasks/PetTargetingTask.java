package dev.asteriamc.enhancedpets.tasks;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

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
        // Collect aggressive pets
        java.util.List<PetData> pets = new java.util.ArrayList<>();
        for (PetData pd : this.petManager.getAllPetData()) {
            if (pd.getMode() == BehaviorMode.AGGRESSIVE) {
                pets.add(pd);
            }
        }
        if (pets.isEmpty()) return;

        int size = pets.size();
        int processed = 0;

        while (processed < PETS_PER_TICK && processed < size) {
            PetData petData = pets.get(rrIndex % size);
            rrIndex++;
            processed++;

            Entity entity = Bukkit.getEntity(petData.getPetUUID());
            if (!(entity instanceof Creature petCreature) || !entity.isValid() || entity.isDead()) continue;

            LivingEntity currentTarget = petCreature.getTarget();
            if (currentTarget != null && currentTarget.isValid() && !currentTarget.isDead()) continue;

            LivingEntity bestTarget = null;
            double bestTargetDistanceSq = Double.MAX_VALUE;

            for (Entity nearby : petCreature.getNearbyEntities(this.scanRadius, this.verticalScanRadius, this.scanRadius)) {
                if (!(nearby instanceof LivingEntity target)) continue;
                if (target.equals(petCreature) || target.isDead() || !target.isValid()) continue;

                // Skip owner, friendly players, and owner's other tamed entities
                if (target.getUniqueId().equals(petData.getOwnerUUID())) continue;
                if (petData.isFriendlyPlayer(target.getUniqueId())) continue;
                if (target instanceof Tameable nearbyTameable && nearbyTameable.isTamed()
                        && petData.getOwnerUUID().equals(nearbyTameable.getOwnerUniqueId())) continue;

                // Respect mutual non-aggression: don't target players if protected
                if (target instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
                if (target instanceof Player && petData.isProtectedFromPlayers()) continue;

                if (!petCreature.hasLineOfSight(target)) continue;

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
    }
}
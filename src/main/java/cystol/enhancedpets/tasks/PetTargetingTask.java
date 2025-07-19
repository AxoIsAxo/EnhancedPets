package cystol.enhancedpets.tasks;

import cystol.enhancedpets.Enhancedpets;
import cystol.enhancedpets.data.BehaviorMode;
import cystol.enhancedpets.data.PetData;
import cystol.enhancedpets.manager.PetManager;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.scheduler.BukkitRunnable;

public class PetTargetingTask extends BukkitRunnable {
   private final Enhancedpets plugin;
   private final PetManager petManager;
   private final double scanRadius;
   private final double verticalScanRadius;

   public PetTargetingTask(Enhancedpets plugin, PetManager petManager) {
      this.plugin = plugin;
      this.petManager = petManager;
      this.scanRadius = 16.0;
      this.verticalScanRadius = 8.0;
   }

   public void run() {
      for (PetData petData : this.petManager.getAllPetData()) {
         if (petData.getMode() == BehaviorMode.AGGRESSIVE) {
            Entity entity = Bukkit.getEntity(petData.getPetUUID());
            if (entity instanceof Creature petCreature && entity.isValid() && !entity.isDead()) {
               LivingEntity currentTarget = petCreature.getTarget();
               if (currentTarget == null || !currentTarget.isValid() || currentTarget.isDead()) {
                  List<Entity> nearbyEntities = petCreature.getNearbyEntities(this.scanRadius, this.verticalScanRadius, this.scanRadius);
                  LivingEntity bestTarget = null;
                  double bestTargetDistanceSq = Double.MAX_VALUE;

                  for (Entity nearby : nearbyEntities) {
                     if (nearby instanceof LivingEntity target
                        && !target.equals(petCreature)
                        && !target.isDead()
                        && target.isValid()
                        && !target.getUniqueId().equals(petData.getOwnerUUID())
                        && !petData.isFriendlyPlayer(target.getUniqueId())
                        && !(
                           target instanceof Tameable nearbyTameable
                              && nearbyTameable.isTamed()
                              && petData.getOwnerUUID().equals(nearbyTameable.getOwnerUniqueId())
                        )
                        && (!(target instanceof Player) || ((Player)target).getGameMode() != GameMode.SPECTATOR)
                        && petCreature.hasLineOfSight(target)) {
                        double distanceSq = petCreature.getLocation().distanceSquared(target.getLocation());
                        if (distanceSq < bestTargetDistanceSq) {
                           bestTarget = target;
                           bestTargetDistanceSq = distanceSq;
                        }
                     }
                  }

                  if (bestTarget != null) {
                     this.plugin
                        .getLogger()
                        .log(
                           Level.FINE,
                           "Aggressive pet {0} ({1}) targeting {2} ({3})",
                           new Object[]{petData.getDisplayName(), petCreature.getName(), bestTarget.getType(), bestTarget.getName()}
                        );
                     petCreature.setTarget(bestTarget);
                  } else if (petCreature.getTarget() != null) {
                  }
               }
            }
         }
      }
   }
}

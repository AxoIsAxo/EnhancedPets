package dev.asteriamc.enhancedpets.tasks;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;

public class GrowthGuardTask extends BukkitRunnable {
    private final PetManager petManager;

    public GrowthGuardTask(Enhancedpets plugin) {
        this.petManager = plugin.getPetManager();
    }

    @Override
    public void run() {
        petManager.getAllPetData().forEach(data -> {
            if (!data.isGrowthPaused()) return;
            Entity e = Bukkit.getEntity(data.getPetUUID());
            if (e instanceof Ageable a && !a.isAdult()) {
                a.setAge(Integer.MIN_VALUE); 
            }
        });
    }
}
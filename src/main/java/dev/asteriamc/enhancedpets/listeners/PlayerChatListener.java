package dev.asteriamc.enhancedpets.listeners;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.gui.PetGUIListener;
import dev.asteriamc.enhancedpets.gui.PetManagerGUI;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerChatListener implements Listener {
    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final PetManagerGUI guiManager;
    private final PetGUIListener guiListener;

    public PlayerChatListener(Enhancedpets plugin, PetManager petManager, PetManagerGUI guiManager,
            PetGUIListener guiListener) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.guiManager = guiManager;
        this.guiListener = guiListener;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String input = event.getMessage();

        Map<UUID, UUID> renameInputMap = this.guiListener.getAwaitingRenameInputMap();
        if (renameInputMap.containsKey(playerUUID)) {
            event.setCancelled(true);
            UUID petContextUUID = renameInputMap.remove(playerUUID);
            handleRenameInput(player, input, petContextUUID);
            return;
        }

        Map<UUID, UUID> friendlyInputMap = this.guiListener.getAwaitingFriendlyInputMap();
        if (friendlyInputMap.containsKey(playerUUID)) {
            event.setCancelled(true);
            UUID petContextUUID = friendlyInputMap.remove(playerUUID);
            handleSingleFriendlyInput(player, input, petContextUUID);
            return;
        }

        Map<UUID, Boolean> batchFriendlyInputMap = this.guiListener.getAwaitingBatchFriendlyInputMap();
        if (batchFriendlyInputMap.containsKey(playerUUID)) {
            event.setCancelled(true);
            batchFriendlyInputMap.remove(playerUUID);
            handleBatchFriendlyInput(player, input);
            return;
        }

        Map<UUID, UUID> targetInputMap = this.guiListener.getAwaitingTargetInputMap();
        if (targetInputMap.containsKey(playerUUID)) {
            event.setCancelled(true);
            UUID petContextUUID = targetInputMap.remove(playerUUID);
            handleTargetInput(player, input, petContextUUID);
        }
    }

    private void handleRenameInput(Player player, String input, UUID petContextUUID) {
        if (input.equalsIgnoreCase("cancel")) {
            plugin.getLanguageManager().sendMessage(player, "chat.rename_cancel");
            this.plugin.getServer().getScheduler().runTask(this.plugin,
                    () -> this.guiManager.openPetMenu(player, petContextUUID));
            return;
        }

        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            PetData petData = this.petManager.getPetData(petContextUUID);
            if (petData == null) {
                plugin.getLanguageManager().sendMessage(player, "chat.rename_error");
                this.guiManager.openMainMenu(player);
                return;
            }

            if (input.matches("^[a-zA-Z0-9_-]+$")) {
                String oldName = petData.getDisplayName();
                petData.setDisplayName(input);
                Entity petEntity = Bukkit.getEntity(petContextUUID);
                if (petEntity instanceof LivingEntity) {
                    petEntity.setCustomName(ChatColor.translateAlternateColorCodes('&', input));
                }
                this.petManager.updatePetData(petData);
                plugin.getLanguageManager().sendReplacements(player, "chat.rename_success", "old", oldName, "new",
                        input);
            } else {
                plugin.getLanguageManager().sendMessage(player, "chat.rename_invalid");
            }
            this.guiManager.openPetMenu(player, petContextUUID);
        });
    }

    private void handleSingleFriendlyInput(Player player, String input, UUID petContextUUID) {
        if (input.equalsIgnoreCase("cancel")) {
            plugin.getLanguageManager().sendMessage(player, "chat.friendly_cancel");

            this.plugin.getServer().getScheduler().runTask(this.plugin,
                    () -> this.guiManager.openFriendlyPlayerMenu(player, petContextUUID, 0));
            return;
        }

        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            PetData petData = this.petManager.getPetData(petContextUUID);
            if (petData == null) {
                plugin.getLanguageManager().sendMessage(player, "chat.data_lost");
                this.guiManager.openMainMenu(player);
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(input);
            if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
                plugin.getLanguageManager().sendReplacements(player, "chat.player_not_found", "player", input);
                this.guiListener.getAwaitingFriendlyInputMap().put(player.getUniqueId(), petContextUUID);
                plugin.getLanguageManager().sendMessage(player, "chat.try_again");
                return;
            }

            UUID targetUUID = targetPlayer.getUniqueId();
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : input;
            if (targetUUID.equals(petData.getOwnerUUID())) {
                plugin.getLanguageManager().sendMessage(player, "chat.friendly_owner");
            } else if (petData.isFriendlyPlayer(targetUUID)) {
                plugin.getLanguageManager().sendReplacements(player, "chat.friendly_already", "player", targetName);
            } else {
                petData.addFriendlyPlayer(targetUUID);
                this.petManager.updatePetData(petData);
                plugin.getLanguageManager().sendReplacements(player, "chat.friendly_success", "player", targetName,
                        "pet", petData.getDisplayName());
            }

            this.guiManager.openFriendlyPlayerMenu(player, petContextUUID, 0);
        });
    }

    private void handleBatchFriendlyInput(Player player, String input) {
        Set<UUID> selectedPets = this.guiManager.getBatchActionsGUI().getPlayerSelections().get(player.getUniqueId());
        if (selectedPets == null || selectedPets.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "gui.selection_lost");
            return;
        }

        if (input.equalsIgnoreCase("cancel")) {
            plugin.getLanguageManager().sendMessage(player, "chat.friendly_cancel");
            this.plugin.getServer().getScheduler().runTask(this.plugin,
                    () -> this.guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, 0));
            return;
        }

        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(input);
            if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
                plugin.getLanguageManager().sendReplacements(player, "chat.player_not_found", "player", input);
                this.guiListener.getAwaitingBatchFriendlyInputMap().put(player.getUniqueId(), true);
                plugin.getLanguageManager().sendMessage(player, "chat.try_again");
                return;
            }

            UUID targetUUID = targetPlayer.getUniqueId();
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : input;
            int addCount = 0;
            for (UUID petUUID : selectedPets) {
                PetData petData = this.petManager.getPetData(petUUID);
                if (petData != null && !petData.isFriendlyPlayer(targetUUID)
                        && !petData.getOwnerUUID().equals(targetUUID)) {
                    petData.addFriendlyPlayer(targetUUID);
                    addCount++;
                }
            }
            java.util.List<PetData> toSave = selectedPets.stream()
                    .map(this.petManager::getPetData)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
            this.petManager.saveAllPetData(toSave);
            plugin.getLanguageManager().sendReplacements(player, "chat.friendly_batch_success", "player", targetName,
                    "count", String.valueOf(addCount));
            this.guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, 0);
        });
    }

    private void handleTargetInput(Player player, String input, UUID petContextUUID) {
        if (input.equalsIgnoreCase("cancel")) {
            plugin.getLanguageManager().sendMessage(player, "chat.target_cancel");
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> this.guiManager.openPetMenu(player, petContextUUID));
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PetData petData = this.petManager.getPetData(petContextUUID);
            if (petData == null) {
                plugin.getLanguageManager().sendMessage(player, "chat.data_lost");
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                plugin.getLanguageManager().sendReplacements(player, "chat.player_not_found", "player", input);
                guiListener.getAwaitingTargetInputMap().put(player.getUniqueId(), petContextUUID);
                plugin.getLanguageManager().sendMessage(player, "chat.try_again");
                return;
            }

            petData.setExplicitTargetUUID(target.getUniqueId());
            petData.setStationLocation(null); // Clear station if targeting
            petManager.updatePetData(petData);

            plugin.getLanguageManager().sendReplacements(player, "chat.target_set", "pet", petData.getDisplayName(),
                    "target", target.getName());
            this.guiManager.openPetMenu(player, petContextUUID);
        });
    }
}

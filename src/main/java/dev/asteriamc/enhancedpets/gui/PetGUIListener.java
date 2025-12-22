package dev.asteriamc.enhancedpets.gui;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class PetGUIListener implements Listener {
    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final PetManagerGUI guiManager;
    private final BatchActionsGUI batchActionsGUI;
    private final Map<UUID, UUID> awaitingFriendlyInput = new HashMap<>();
    private final Map<UUID, UUID> awaitingRenameInput = new HashMap<>();
    private final Map<UUID, Boolean> awaitingBatchFriendlyInput = new HashMap<>();
    private final Map<UUID, UUID> awaitingTargetInput = new HashMap<>();
    private final Map<UUID, UUID> awaitingTargetSelection = new HashMap<>();

    public PetGUIListener(Enhancedpets plugin, PetManagerGUI guiManager) {
        this.plugin = plugin;
        this.petManager = plugin.getPetManager();
        this.guiManager = guiManager;
        this.batchActionsGUI = guiManager.getBatchActionsGUI();
    }

    public Map<UUID, UUID> getAwaitingFriendlyInputMap() {
        return this.awaitingFriendlyInput;
    }

    public Map<UUID, UUID> getAwaitingRenameInputMap() {
        return this.awaitingRenameInput;
    }

    public Map<UUID, Boolean> getAwaitingBatchFriendlyInputMap() {
        return this.awaitingBatchFriendlyInput;
    }

    public Map<UUID, UUID> getAwaitingTargetInputMap() {
        return this.awaitingTargetInput;
    }

    public Map<UUID, UUID> getAwaitingTargetSelectionMap() {
        return this.awaitingTargetSelection;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (!(event.getInventory().getHolder() instanceof PetInventoryHolder))
            return;

        if (event.isShiftClick() && event.isRightClick()) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.SKELETON_SKULL) {
                event.setCancelled(true);
                PersistentDataContainer data = clickedItem.getItemMeta().getPersistentDataContainer();
                String petUUIDString = data.get(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING);
                if (petUUIDString != null) {
                    UUID petUUID = UUID.fromString(petUUIDString);
                    PetData petData = petManager.getPetData(petUUID);
                    if (petData != null && petData.isDead()) {
                        petManager.freePetCompletely(petUUID);
                        plugin.getLanguageManager().sendReplacements(player, "gui.dead_removed", "pet",
                                petData.getDisplayName());
                        guiManager.openMainMenu(player);
                        return;
                    }
                }
            }
        }

        PetInventoryHolder holder = (PetInventoryHolder) event.getInventory().getHolder();
        if (holder.getMenuType() == PetInventoryHolder.MenuType.CONFIRM_ACTION) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR)
                return;
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null)
                return;
            PersistentDataContainer data = meta.getPersistentDataContainer();
            String action = data.get(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING);
            if (action != null) {
                handleRegularAction(player, action, data, event);
            }
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.isShiftClick())
                event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null)
            return;

        PersistentDataContainer data = meta.getPersistentDataContainer();

        String batchAction = data.get(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING);
        if (batchAction != null) {
            handleBatchAction(player, batchAction, data);
            return;
        }

        String action = data.get(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING);
        if (action != null) {
            handleRegularAction(player, action, data, event);
            return;
        }

        String mainPetUUIDString = data.get(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING);
        if (mainPetUUIDString != null) {
            this.guiManager.openPetMenu(player, UUID.fromString(mainPetUUIDString));
        }
    }

    private void handleBatchAction(Player player, String batchAction, PersistentDataContainer data) {
        Set<UUID> selectedPets = batchActionsGUI.getPlayerSelections().computeIfAbsent(player.getUniqueId(),
                k -> new HashSet<>());
        String typeName = data.get(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING);
        EntityType petType = typeName != null ? EntityType.valueOf(typeName) : null;
        Integer page = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);

        switch (batchAction) {
            case "open_type_select" -> batchActionsGUI.openPetTypeSelectionMenu(player);
            case "select_pet_type" -> {
                if (petType != null) {
                    selectedPets.clear();
                    batchActionsGUI.openPetSelectionMenu(player, petType, 0);
                }
            }
            case "batch_select_page" -> {
                if (petType != null && page != null) {
                    batchActionsGUI.openPetSelectionMenu(player, petType, page);
                }
            }
            case "toggle_pet_selection" -> {
                String petUUIDString = data.get(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING);
                Integer currentPage = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
                if (petUUIDString != null && petType != null && currentPage != null) {
                    UUID petUUID = UUID.fromString(petUUIDString);
                    if (selectedPets.contains(petUUID)) {
                        selectedPets.remove(petUUID);
                    } else {
                        selectedPets.add(petUUID);
                    }
                    batchActionsGUI.openPetSelectionMenu(player, petType, currentPage);
                }
            }
            case "select_all", "select_none" -> {
                if (petType != null) {
                    UUID owner = guiManager.getEffectiveOwner(player);
                    List<UUID> petsOfType = petManager.getPetsOwnedBy(owner).stream()
                            .filter(p -> p.getEntityType() == petType)
                            .map(PetData::getPetUUID)
                            .toList();
                    if (batchAction.equals("select_all")) {
                        selectedPets.addAll(petsOfType);
                    } else {
                        selectedPets.removeAll(petsOfType);
                    }
                    batchActionsGUI.openPetSelectionMenu(player, petType, 0);
                }
            }
            case "open_batch_manage" -> {
                if (selectedPets.isEmpty()) {
                    plugin.getLanguageManager().sendMessage(player, "gui.batch_no_selection");
                } else {
                    guiManager.openBatchManagementMenu(player, selectedPets);
                }
            }
            case "batch_remove_dead" -> {
                if (petType != null) {
                    UUID owner = guiManager.getEffectiveOwner(player);
                    List<PetData> deadPets = petManager.getPetsOwnedBy(owner).stream()

                            .filter(p -> p.getEntityType() == petType && p.isDead())
                            .toList();
                    if (deadPets.isEmpty()) {
                        plugin.getLanguageManager().sendMessage(player, "gui.batch_no_dead");
                    } else {
                        guiManager.openBatchConfirmRemoveDeadMenu(player, petType, deadPets.size());
                    }
                }
            }
            case "batch_confirm_remove_dead" -> {
                if (petType != null) {
                    UUID owner = guiManager.getEffectiveOwner(player);
                    List<PetData> deadPets = petManager.getPetsOwnedBy(owner).stream()
                            .filter(p -> p.getEntityType() == petType && p.isDead())
                            .toList();
                    if (!deadPets.isEmpty()) {
                        deadPets.forEach(p -> petManager.freePetCompletely(p.getPetUUID()));
                        plugin.getLanguageManager().sendReplacements(player, "gui.batch_remove_dead_success", "count",
                                String.valueOf(deadPets.size()));
                    }
                    batchActionsGUI.openPetSelectionMenu(player, petType, 0);
                }
            }
            case "open_pet_select" -> {
                if (petType != null) {
                    batchActionsGUI.openPetSelectionMenu(player, petType, 0);
                }
            }
        }
    }

    private void handleRegularAction(Player player, String action, PersistentDataContainer data,
            InventoryClickEvent event) {
        String petUUIDString = data.get(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING);
        UUID petUUID = (petUUIDString != null) ? UUID.fromString(petUUIDString) : null;
        Set<UUID> selectedPets = batchActionsGUI.getPlayerSelections().get(player.getUniqueId());

        switch (action) {
            case "main_page" -> {
                Integer targetPage = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
                if (targetPage != null)
                    guiManager.openMainMenu(player, targetPage);
                return;
            }
            case "back_to_main" -> {
                guiManager.openMainMenu(player);
                return;
            }
            case "batch_actions" -> {
                batchActionsGUI.openPetTypeSelectionMenu(player);
                return;
            }
            case "back_to_pet", "cancel_free" -> {
                if (petUUID != null)
                    guiManager.openPetMenu(player, petUUID);
                return;
            }
            case "open_batch_manage" -> {
                if (selectedPets != null && !selectedPets.isEmpty()) {
                    guiManager.openBatchManagementMenu(player, selectedPets);
                } else {
                    guiManager.openMainMenu(player);
                }
                return;
            }
            case "scan_for_pets" -> {
                player.closeInventory();
                UUID override = plugin.getGuiManager().getViewerOwnerOverride(player.getUniqueId());
                if (override != null && !override.equals(player.getUniqueId())) {
                    plugin.getLanguageManager().sendMessage(player, "gui.scan_not_owner");
                    guiManager.openMainMenu(player);
                    return;
                }
                plugin.getLanguageManager().sendMessage(player, "gui.scan_start");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int foundCount = petManager.scanAndRegisterPetsForOwner(player);
                    if (foundCount > 0) {
                        plugin.getLanguageManager().sendReplacements(player, "gui.scan_success", "count",
                                String.valueOf(foundCount));
                        guiManager.openMainMenu(player);
                    } else {
                        plugin.getLanguageManager().sendMessage(player, "gui.scan_none");
                    }
                });
                return;
            }
            case "do_store_pet" -> {
                // Handle from Store GUI
                if (petUUID == null)
                    return;
                PetData pd = petManager.getPetData(petUUID);
                if (pd == null || pd.isStored()) {
                    plugin.getLanguageManager().sendMessage(player, "gui.store_fail");
                    return;
                }
                Entity petEntity = Bukkit.getEntity(petUUID);
                if (petEntity != null && petEntity.isValid()) {
                    if (petEntity instanceof LivingEntity le) {
                        petManager.captureMetadata(pd, le);
                    }
                    petEntity.remove();
                }
                pd.setStored(true);
                pd.setStationLocation(null);
                pd.setExplicitTargetUUID(null);
                petManager.updatePetData(pd);
                plugin.getLanguageManager().sendReplacements(player, "gui.store_success", "pet", pd.getDisplayName());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
                guiManager.openStorePetMenu(player);
                return;
            }
            case "do_withdraw_pet" -> {
                // Handle from Withdraw GUI
                if (petUUID == null)
                    return;
                PetData pd = petManager.getPetData(petUUID);
                if (pd == null || !pd.isStored()) {
                    plugin.getLanguageManager().sendMessage(player, "gui.withdraw_fail");
                    return;
                }
                // Respawn the pet
                // Respawn the pet
                org.bukkit.entity.LivingEntity newPet = (org.bukkit.entity.LivingEntity) player.getWorld().spawnEntity(
                        player.getLocation(), pd.getEntityType());

                // Update Pet Data with new UUID
                PetData newData = petManager.updatePetId(pd, newPet.getUniqueId());

                if (newPet instanceof org.bukkit.entity.Tameable t) {
                    // Use the actual owner from PetData, not the current viewer
                    org.bukkit.OfflinePlayer actualOwner = Bukkit.getOfflinePlayer(newData.getOwnerUUID());
                    t.setOwner(actualOwner);
                    t.setTamed(true);
                }

                petManager.applyMetadata(newPet, newData);

                newData.setStored(false);
                petManager.updatePetData(newData);

                plugin.getLanguageManager().sendReplacements(player, "gui.withdraw_success", "pet",
                        newData.getDisplayName());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
                guiManager.openWithdrawPetMenu(player);
                return;
            }
        }

        if (petUUID != null && "pet_header".equals(action)) {
            PetData petData = petManager.getPetData(petUUID);
            if (petData == null) { /* ... error handling ... */
                return;
            }

            if (event.isRightClick()) {

                petData.setFavorite(!petData.isFavorite());
                petManager.updatePetData(petData);
                if (petData.isFavorite()) {
                    plugin.getLanguageManager().sendReplacements(player, "gui.favorite_added", "pet",
                            petData.getDisplayName());
                } else {
                    plugin.getLanguageManager().sendReplacements(player, "gui.favorite_removed", "pet",
                            petData.getDisplayName());
                }
                guiManager.openPetMenu(player, petUUID);
                return;
            } else if (event.isLeftClick()) {

                guiManager.openCustomizationMenu(player, petUUID);
                return;
            }
        }

        if (action.startsWith("batch_")) {
            if (selectedPets == null || selectedPets.isEmpty()) {
                plugin.getLanguageManager().sendMessage(player, "gui.selection_lost");
                guiManager.openMainMenu(player);
                return;
            }
            handleBatchManagementAction(player, action, data, selectedPets, event);
            return;
        }

        if (petUUID != null) {
            PetData petData = petManager.getPetData(petUUID);
            if (petData == null) {
                plugin.getLanguageManager().sendReplacements(player, "gui.pet_not_found", "pet", "Pet");
                guiManager.openMainMenu(player);
                return;
            }
            handleSinglePetAction(player, action, petData, data, event);
        }
    }

    public void forgetPlayer(UUID playerId) {
        awaitingFriendlyInput.remove(playerId);
        awaitingRenameInput.remove(playerId);
        awaitingBatchFriendlyInput.remove(playerId);
        awaitingTargetInput.remove(playerId);
        awaitingTargetSelection.remove(playerId);
        batchActionsGUI.clearSelections(playerId);
    }

    private void handleBatchManagementAction(Player player, String action, PersistentDataContainer data,
            Set<UUID> selectedPets, InventoryClickEvent event) {
        if (selectedPets == null || selectedPets.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "gui.batch_no_selection");
            guiManager.openMainMenu(player);
            return;
        }
        List<PetData> petDataList = selectedPets.stream().map(petManager::getPetData).filter(Objects::nonNull).toList();

        switch (action) {
            case "batch_set_mode_PASSIVE", "batch_set_mode_NEUTRAL", "batch_set_mode_AGGRESSIVE" -> {
                BehaviorMode newMode = BehaviorMode.valueOf(action.substring(15));

                if (newMode == BehaviorMode.AGGRESSIVE) {
                    String typeToToggle = null;
                    if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT) {
                        typeToToggle = "MOB";
                    } else if (event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
                        typeToToggle = "ANIMAL";
                    } else if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                        typeToToggle = "PLAYER";
                    }

                    if (typeToToggle != null) {
                        boolean allHave = true;
                        for (PetData pd : petDataList) {
                            if (!pd.getAggressiveTargetTypes().contains(typeToToggle)) {
                                allHave = false;
                                break;
                            }
                        }

                        for (PetData pd : petDataList) {
                            Set<String> types = pd.getAggressiveTargetTypes();
                            if (allHave) {
                                types.remove(typeToToggle);
                            } else {
                                types.add(typeToToggle);
                            }
                        }
                        petManager.saveAllPetData(petDataList);
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                        guiManager.openBatchManagementMenu(player, selectedPets);
                        return;
                    }

                    if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT) {
                        return;
                    }
                }

                petDataList.forEach(pd -> pd.setMode(newMode));
                petManager.saveAllPetData(petDataList);
                if (newMode != BehaviorMode.AGGRESSIVE) {
                    petDataList.stream()
                            .map(pd -> Bukkit.getEntity(pd.getPetUUID()))
                            .filter(e -> e instanceof Creature)
                            .forEach(e -> ((Creature) e).setTarget(null));
                }
                plugin.getLanguageManager().sendReplacements(player, "gui.batch_mode_set", "count",
                        String.valueOf(petDataList.size()), "mode", newMode.name());
                guiManager.openBatchManagementMenu(player, selectedPets);
            }
            case "batch_toggle_growth_pause" -> {
                java.util.List<UUID> babyUUIDs = selectedPets.stream()
                        .map(Bukkit::getEntity)
                        .filter(e -> e instanceof Ageable a && !a.isAdult())
                        .map(Entity::getUniqueId)
                        .toList();

                if (babyUUIDs.isEmpty()) {
                    plugin.getLanguageManager().sendMessage(player, "gui.batch_no_babies");
                    guiManager.openBatchManagementMenu(player, selectedPets);
                    break;
                }

                long pausedCount = babyUUIDs.stream()
                        .map(petManager::getPetData)
                        .filter(Objects::nonNull)
                        .filter(PetData::isGrowthPaused)
                        .count();

                boolean shouldPause = pausedCount < babyUUIDs.size();
                int changed = 0;
                for (UUID id : babyUUIDs) {
                    PetData pd = petManager.getPetData(id);
                    if (pd != null) {
                        plugin.getPetManager().setGrowthPaused(id, shouldPause);
                        changed++;
                    }
                }

                if (shouldPause) {
                    plugin.getLanguageManager().sendReplacements(player, "gui.batch_growth_paused", "count",
                            String.valueOf(changed));
                } else {
                    plugin.getLanguageManager().sendReplacements(player, "gui.batch_growth_resumed", "count",
                            String.valueOf(changed));
                }
                guiManager.openBatchManagementMenu(player, selectedPets);
            }
            case "batch_toggle_favorite" -> {
                long favoriteCount = petDataList.stream().filter(PetData::isFavorite).count();
                boolean makeFavorite = favoriteCount < petDataList.size();
                petDataList.forEach(pd -> pd.setFavorite(makeFavorite));
                petManager.saveAllPetData(petDataList);
                if (makeFavorite) {
                    plugin.getLanguageManager().sendReplacements(player, "gui.batch_favorite_marked", "count",
                            String.valueOf(petDataList.size()));
                } else {
                    plugin.getLanguageManager().sendReplacements(player, "gui.batch_favorite_unmarked", "count",
                            String.valueOf(petDataList.size()));
                }
                guiManager.openBatchManagementMenu(player, selectedPets);
            }
            case "batch_teleport" -> {
                int summoned = (int) selectedPets.stream()
                        .map(Bukkit::getEntity)
                        .filter(e -> e != null && e.isValid())
                        .peek(e -> e.teleport(player.getLocation()))
                        .count();
                plugin.getLanguageManager().sendReplacements(player, "gui.batch_summoned", "count",
                        String.valueOf(summoned));
            }
            case "batch_calm" -> {
                int calmed = (int) selectedPets.stream()
                        .map(Bukkit::getEntity)
                        .filter(e -> e instanceof Creature)
                        .peek(e -> {
                            ((Creature) e).setTarget(null);
                            if (e instanceof Wolf w)
                                w.setAngry(false);
                        }).count();
                plugin.getLanguageManager().sendReplacements(player, "gui.batch_calmed", "count",
                        String.valueOf(calmed));
            }
            case "batch_toggle_sit" -> {
                List<Sittable> sittables = selectedPets.stream().map(Bukkit::getEntity)
                        .filter(e -> e instanceof Sittable).map(e -> (Sittable) e).toList();
                if (!sittables.isEmpty()) {
                    long sittingCount = sittables.stream().filter(Sittable::isSitting).count();
                    boolean shouldSit = sittingCount < sittables.size();
                    sittables.forEach(s -> s.setSitting(shouldSit));
                    if (shouldSit) {
                        plugin.getLanguageManager().sendReplacements(player, "gui.batch_sit", "count",
                                String.valueOf(sittables.size()));
                    } else {
                        plugin.getLanguageManager().sendReplacements(player, "gui.batch_stand", "count",
                                String.valueOf(sittables.size()));
                    }
                    guiManager.openBatchManagementMenu(player, selectedPets);
                }
            }
            case "batch_free_pet_prompt" -> guiManager.openBatchConfirmFreeMenu(player, selectedPets);
            case "batch_confirm_free" -> {
                player.closeInventory();
                int count = selectedPets.size();
                selectedPets.forEach(petManager::freePetCompletely);
                batchActionsGUI.getPlayerSelections().remove(player.getUniqueId());
                plugin.getLanguageManager().sendReplacements(player, "gui.batch_freed", "count", String.valueOf(count));
            }
            case "batch_manage_friendly" -> guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, 0);
            case "batch_friendly_page" -> {
                Integer page = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
                if (page != null)
                    guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, page);
            }
            case "add_batch_friendly_prompt" -> {
                awaitingBatchFriendlyInput.put(player.getUniqueId(), true);
                player.closeInventory();
                plugin.getLanguageManager().sendMessage(player, "gui.batch_friendly_prompt");
            }
            case "remove_batch_friendly" -> {
                String targetUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
                if (targetUUIDString != null) {
                    UUID targetUUID = UUID.fromString(targetUUIDString);
                    petDataList.forEach(pd -> pd.removeFriendlyPlayer(targetUUID));
                    petManager.saveAllPetData(petDataList);
                    plugin.getLanguageManager().sendReplacements(player, "gui.batch_friendly_removed",
                            "player", Bukkit.getOfflinePlayer(targetUUID).getName(), "count",
                            String.valueOf(petDataList.size()));
                    guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, 0);
                }
            }
            case "batch_open_transfer" -> guiManager.openBatchTransferMenu(player, selectedPets);
            case "batch_transfer_to_player" -> {
                String targetUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
                if (targetUUIDString != null) {
                    Player targetPlayer = Bukkit.getPlayer(UUID.fromString(targetUUIDString));
                    if (targetPlayer == null || !targetPlayer.isOnline()) {
                        plugin.getLanguageManager().sendMessage(player, "gui.transfer_offline");
                        return;
                    }
                    player.closeInventory();
                    String oldOwnerName = player.getName();
                    petDataList.forEach(pd -> {
                        pd.setOwnerUUID(targetPlayer.getUniqueId());
                        if (Bukkit.getEntity(pd.getPetUUID()) instanceof Tameable t) {
                            t.setOwner(targetPlayer);
                        }
                    });
                    petManager.saveAllPetData(petDataList);
                    plugin.getLanguageManager().sendReplacements(player, "gui.transfer_batch_sender", "count",
                            String.valueOf(petDataList.size()), "target", targetPlayer.getName());
                    plugin.getLanguageManager().sendReplacements(targetPlayer, "gui.transfer_batch_receiver", "count",
                            String.valueOf(petDataList.size()), "sender", oldOwnerName);
                }
            }
            case "batch_toggle_protection" -> {
                long protectedCount = petDataList.stream().filter(PetData::isProtectedFromPlayers).count();
                boolean makeProtected = protectedCount < petDataList.size();
                petDataList.forEach(pd -> pd.setProtectedFromPlayers(makeProtected));
                petManager.saveAllPetData(petDataList);
                if (makeProtected) {
                    plugin.getLanguageManager().sendReplacements(player, "gui.batch_protection_enabled", "count",
                            String.valueOf(petDataList.size()));
                } else {
                    plugin.getLanguageManager().sendReplacements(player, "gui.batch_protection_disabled", "count",
                            String.valueOf(petDataList.size()));
                }
                guiManager.openBatchManagementMenu(player, selectedPets);
            }
        }
    }

    private void handleSinglePetAction(Player player, String action, PetData petData, PersistentDataContainer data,
            InventoryClickEvent event) {
        UUID petUUID = petData.getPetUUID();
        switch (action) {
            case "set_mode_PASSIVE", "set_mode_NEUTRAL", "set_mode_AGGRESSIVE" -> {
                if (player.hasPermission("enhancedpets.use")) {
                    BehaviorMode newMode = BehaviorMode.valueOf(action.substring(9));

                    // Configuration Logic (Aggressive Mode Button Only)
                    // If we clicked the Aggressive button, check for config toggles
                    if (newMode == BehaviorMode.AGGRESSIVE) {
                        String typeToToggle = null;

                        // Shift-Left = Mobs
                        // Right-Click = Animals
                        // Shift-Right = Players
                        if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT) {
                            typeToToggle = "MOB";
                        } else if (event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
                            typeToToggle = "ANIMAL";
                        } else if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                            typeToToggle = "PLAYER";
                        }

                        if (typeToToggle != null) {
                            Set<String> types = petData.getAggressiveTargetTypes();
                            if (types.contains(typeToToggle)) {
                                types.remove(typeToToggle);
                            } else {
                                types.add(typeToToggle);
                            }
                            petManager.updatePetData(petData);
                            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                            guiManager.openPetMenu(player, petUUID);
                            return; // Stop here, do not change mode
                        }
                    }

                    // Activation Logic (Plain Left Click)
                    // "only plain left click switches to agressive mode"
                    if (event.getClick() == org.bukkit.event.inventory.ClickType.LEFT) {
                        if (petData.getMode() != newMode) {
                            petData.setMode(newMode);
                            petManager.updatePetData(petData);
                            plugin.getLanguageManager().sendReplacements(player, "gui.mode_set", "pet",
                                    petData.getDisplayName(), "mode", newMode.name());
                            if (newMode != BehaviorMode.AGGRESSIVE && Bukkit.getEntity(petUUID) instanceof Creature c) {
                                c.setTarget(null);
                            }
                        }
                        guiManager.openPetMenu(player, petUUID);
                    }
                }
            }
            case "confirm_free_pet_prompt" -> guiManager.openConfirmFreeMenu(player, petUUID);
            case "manage_friendly" -> guiManager.openFriendlyPlayerMenu(player, petUUID, 0);
            case "toggle_growth_pause" -> {
                boolean paused = petData.isGrowthPaused();
                plugin.getPetManager().setGrowthPaused(petUUID, !paused);
                if (paused) {
                    plugin.getLanguageManager().sendReplacements(player, "gui.growth_resumed", "pet",
                            petData.getDisplayName());
                } else {
                    plugin.getLanguageManager().sendReplacements(player, "gui.growth_paused", "pet",
                            petData.getDisplayName());
                }
                guiManager.openPetMenu(player, petUUID);
            }
            case "teleport_pet" -> {
                Entity petEntity = Bukkit.getEntity(petUUID);
                if (petEntity != null && petEntity.isValid()) {
                    // Mark as forced teleport to bypass PetListener station checks
                    petEntity.setMetadata("force_teleport", new FixedMetadataValue(plugin, true));
                    petEntity.teleport(player.getLocation());

                    // If pet is stationed, update station location to here
                    if (petData.getStationLocation() != null) {
                        petData.setStationLocation(player.getLocation());
                        petManager.updatePetData(petData);
                        plugin.getLanguageManager().sendMessage(player, "gui.station_updated");
                    }

                    plugin.getLanguageManager().sendReplacements(player, "gui.summon_success", "pet",
                            petData.getDisplayName());
                } else {
                    plugin.getLanguageManager().sendReplacements(player, "gui.pet_not_found", "pet",
                            petData.getDisplayName());
                }
            }
            case "free_pet" -> {
                petManager.freePetCompletely(petUUID);
                plugin.getLanguageManager().sendReplacements(player, "gui.freed", "pet", petData.getDisplayName());
                guiManager.openMainMenu(player);
            }
            case "friendly_page" -> {
                Integer friendlyPage = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
                if (friendlyPage != null)
                    guiManager.openFriendlyPlayerMenu(player, petUUID, friendlyPage);
            }
            case "rename_pet_prompt" -> {
                if (event.isShiftClick()) {
                    String oldName = petData.getDisplayName();
                    String newDefaultName = petManager.assignNewDefaultName(petData);
                    petData.setDisplayName(newDefaultName);
                    Entity petEntity = Bukkit.getEntity(petUUID);
                    if (petEntity != null) {
                        petEntity.setCustomName(null);
                    }
                    petManager.updatePetData(petData);
                    plugin.getLanguageManager().sendReplacements(player, "gui.rename_reset", "old", oldName, "new",
                            newDefaultName);
                    guiManager.openPetMenu(player, petUUID);
                } else {
                    awaitingRenameInput.put(player.getUniqueId(), petUUID);
                    player.closeInventory();
                    plugin.getLanguageManager().sendReplacements(player, "gui.rename_prompt", "pet",
                            petData.getDisplayName());
                }
            }

            case "open_transfer" -> guiManager.openTransferMenu(player, petUUID);
            case "transfer_to_player" -> {
                String targetUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
                if (targetUUIDString != null) {
                    Player targetPlayer = Bukkit.getPlayer(UUID.fromString(targetUUIDString));
                    if (targetPlayer == null || !targetPlayer.isOnline()) {
                        plugin.getLanguageManager().sendMessage(player, "gui.transfer_offline");
                        guiManager.openTransferMenu(player, petUUID);
                        return;
                    }
                    String oldOwnerName = player.getName();
                    petData.setOwnerUUID(targetPlayer.getUniqueId());
                    petManager.updatePetData(petData);
                    if (Bukkit.getEntity(petUUID) instanceof Tameable t)
                        t.setOwner(targetPlayer);
                    plugin.getLanguageManager().sendReplacements(player, "gui.transfer_sender", "pet",
                            petData.getDisplayName(), "target", targetPlayer.getName());
                    plugin.getLanguageManager().sendReplacements(targetPlayer, "gui.transfer_receiver", "pet",
                            petData.getDisplayName(), "sender", oldOwnerName);
                    player.closeInventory();
                }
            }
            case "toggle_sit" -> {
                if (Bukkit.getEntity(petUUID) instanceof Sittable s) {
                    s.setSitting(!s.isSitting());
                    plugin.getLanguageManager().sendReplacements(player, "gui.sit_toggled", "pet",
                            petData.getDisplayName(), "state",
                            s.isSitting() ? plugin.getLanguageManager().getString("gui.sitting_state")
                                    : plugin.getLanguageManager().getString("gui.standing_state"));
                    guiManager.openPetMenu(player, petUUID);
                }
            }
            case "calm_pet" -> {
                if (Bukkit.getEntity(petUUID) instanceof Creature c) {
                    c.setTarget(null);
                    if (c instanceof Wolf w)
                        w.setAngry(false);
                    plugin.getLanguageManager().sendReplacements(player, "gui.calm_success", "pet",
                            petData.getDisplayName());
                } else {
                    plugin.getLanguageManager().sendReplacements(player, "gui.pet_not_found", "pet",
                            petData.getDisplayName());
                }
            }
            case "confirm_free" -> {
                String petDisplayName = petData.getDisplayName();
                petManager.freePetCompletely(petUUID);
                plugin.getLanguageManager().sendReplacements(player, "gui.freed", "pet", petDisplayName);
                guiManager.openMainMenu(player);
            }
            case "add_friendly_prompt" -> {
                awaitingFriendlyInput.put(player.getUniqueId(), petUUID);
                player.closeInventory();
                plugin.getLanguageManager().sendReplacements(player, "gui.friendly_prompt", "pet",
                        petData.getDisplayName());
            }
            case "remove_friendly" -> {
                String targetFriendUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY,
                        PersistentDataType.STRING);
                if (targetFriendUUIDString != null) {
                    UUID targetFriendUUID = UUID.fromString(targetFriendUUIDString);
                    petData.removeFriendlyPlayer(targetFriendUUID);
                    petManager.updatePetData(petData);
                    plugin.getLanguageManager().sendReplacements(player, "gui.friendly_removed",
                            "player", Bukkit.getOfflinePlayer(targetFriendUUID).getName(), "pet",
                            petData.getDisplayName());
                    guiManager.openFriendlyPlayerMenu(player, petUUID, 0);
                }
            }
            case "confirm_revive_pet" -> openConfirmMenu(player, petUUID, true);
            case "confirm_remove_pet" -> openConfirmMenu(player, petUUID, false);
            case "do_revive_pet" -> {
                if (!petData.isDead()) {
                    plugin.getLanguageManager().sendMessage(player, "gui.not_dead");
                    guiManager.openPetMenu(player, petData.getPetUUID());
                    return;
                }
                Material requiredItem = plugin.getConfigManager().getReviveItem();
                int requiredAmount = plugin.getConfigManager().getReviveItemAmount();
                boolean requireMainHand = plugin.getConfigManager().isReviveItemRequireMainHand();

                if (requireMainHand) {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType() != requiredItem || hand.getAmount() < requiredAmount) {
                        plugin.getLanguageManager().sendReplacements(player, "gui.need_nether_star",
                                "amount", String.valueOf(requiredAmount),
                                "item", requiredItem.name(),
                                "hand", "in your main hand ");
                        guiManager.openPetMenu(player, petData.getPetUUID());
                        return;
                    }
                    // Consume Cost
                    hand.setAmount(hand.getAmount() - requiredAmount);
                } else {
                    if (!player.getInventory().containsAtLeast(new ItemStack(requiredItem), requiredAmount)) {
                        plugin.getLanguageManager().sendReplacements(player, "gui.need_nether_star",
                                "amount", String.valueOf(requiredAmount),
                                "item", requiredItem.name(),
                                "hand", "");
                        guiManager.openPetMenu(player, petData.getPetUUID());
                        return;
                    }
                    // Consume Cost
                    player.getInventory().removeItem(new ItemStack(requiredItem, requiredAmount));
                }

                player.closeInventory(); // Close completely to transition

                // Spawn Logic
                LivingEntity newPet = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(),
                        petData.getEntityType());

                // Manager Revival (Data Transfer + Core Effects)
                petManager.revivePet(petData, newPet); // Also plays TOTEM sound/particle from Manager

                // Player Gratification
                plugin.getLanguageManager().sendReplacements(player, "gui.revive_success", "pet",
                        petData.getDisplayName());

                // Delay opening new menu to ensure smooth transition and data sync
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (newPet.isValid()) {
                        guiManager.openPetMenu(player, newPet.getUniqueId());
                    }
                }, 10L); // 0.5s delay
            }
            case "do_remove_pet" -> {
                petManager.freePetCompletely(petUUID);
                plugin.getLanguageManager().sendReplacements(player, "gui.remove_permanent", "pet",
                        petData.getDisplayName());
                guiManager.openMainMenu(player);
            }
            case "cancel_confirm" -> guiManager.openPetMenu(player, petUUID);

            case "set_display_icon" -> {
                if (event.isShiftClick()) {
                    petData.setCustomIconMaterial(null);
                    petManager.updatePetData(petData);
                    plugin.getLanguageManager().sendReplacements(player, "gui.icon_reset", "pet",
                            petData.getDisplayName());
                } else {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand == null || hand.getType().isAir()) {
                        plugin.getLanguageManager().sendMessage(player, "gui.icon_no_item");
                    } else {
                        petData.setCustomIconMaterial(hand.getType().name());
                        petManager.updatePetData(petData);
                        plugin.getLanguageManager().sendReplacements(player, "gui.icon_updated", "pet",
                                petData.getDisplayName());
                    }
                }

                guiManager.openPetMenu(player, petUUID);
            }

            case "set_display_color" -> {
                if (event.isShiftClick()) {
                    petData.setDisplayColor(null);
                    petManager.updatePetData(petData);
                    plugin.getLanguageManager().sendReplacements(player, "gui.color_reset", "pet",
                            petData.getDisplayName());

                    guiManager.openPetMenu(player, petUUID);
                } else {
                    guiManager.openColorPicker(player, petUUID);
                }
            }
            case "choose_color" -> {
                String colorName = data.get(PetManagerGUI.COLOR_KEY, PersistentDataType.STRING);
                if (colorName == null || colorName.isEmpty()) {
                    plugin.getLanguageManager().sendMessage(player, "gui.color_invalid");
                } else {
                    petData.setDisplayColor(colorName);
                    petManager.updatePetData(petData);
                    plugin.getLanguageManager().sendReplacements(player, "gui.color_updated", "pet",
                            petData.getDisplayName(), "color", colorName);
                }
                guiManager.openPetMenu(player, petUUID);
            }

            case "toggle_mutual_protection" -> {
                boolean nowProtected = !petData.isProtectedFromPlayers();
                petData.setProtectedFromPlayers(nowProtected);
                petManager.updatePetData(petData);
                if (nowProtected) {
                    plugin.getLanguageManager().sendReplacements(player, "gui.protection_enabled", "pet",
                            petData.getDisplayName());
                } else {
                    plugin.getLanguageManager().sendReplacements(player, "gui.protection_disabled", "pet",
                            petData.getDisplayName());
                }
                guiManager.openPetMenu(player, petUUID);
            }
            case "toggle_station" -> {
                // Station Logic Refactored: Allow config modification at any time
                if (event.isShiftClick()) {
                    // Cycle Radius (Always allowed, updates live or future)
                    double r = petData.getStationRadius();
                    r += 5.0;
                    if (r > 25.0)
                        r = 5.0;
                    petData.setStationRadius(r);

                    // Defaults check just in case
                    if (petData.getStationTargetTypes() == null || petData.getStationTargetTypes().isEmpty()) {
                        petData.setStationTargetTypes(new HashSet<>(Arrays.asList("PLAYER", "MOB")));
                    }

                    petManager.updatePetData(petData);
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                    plugin.getLanguageManager().sendReplacements(player, "gui.station_radius", "radius",
                            String.valueOf((int) r));
                } else if (event.isRightClick()) {
                    // Cycle Target Types (Full Combinatorics)
                    // Order: Mob -> Animal -> Player -> Mob+Player -> Mob+Animal -> Player+Animal
                    // -> All -> Mob...
                    Set<String> current = petData.getStationTargetTypes();
                    Set<String> types = (current == null) ? new HashSet<>() : new HashSet<>(current);
                    String nextDesc;

                    boolean hasMob = types.contains("MOB");
                    boolean hasAnimal = types.contains("ANIMAL");
                    boolean hasPlayer = types.contains("PLAYER");

                    types.clear();

                    // State Machine for granular control
                    if (hasMob && !hasAnimal && !hasPlayer) {
                        // [1] MobOnly -> AnimalOnly
                        types.add("ANIMAL");
                        nextDesc = plugin.getLanguageManager().getString("menus.target_animals_only");
                    } else if (!hasMob && hasAnimal && !hasPlayer) {
                        // [2] AnimalOnly -> PlayerOnly
                        types.add("PLAYER");
                        nextDesc = plugin.getLanguageManager().getString("menus.target_players_only");
                    } else if (!hasMob && !hasAnimal && hasPlayer) {
                        // [3] PlayerOnly -> Mob+Player (Common Defense)
                        types.add("MOB");
                        types.add("PLAYER");
                        nextDesc = plugin.getLanguageManager().getString("menus.target_mobs_players");
                    } else if (hasMob && !hasAnimal && hasPlayer) {
                        // [4] Mob+Player -> Mob+Animal (Farming/Grinding)
                        types.add("MOB");
                        types.add("ANIMAL");
                        nextDesc = plugin.getLanguageManager().getString("menus.target_mobs_animals");
                    } else if (hasMob && hasAnimal && !hasPlayer) {
                        // [5] Mob+Animal -> Player+Animal (Niche)
                        types.add("PLAYER");
                        types.add("ANIMAL");
                        nextDesc = plugin.getLanguageManager().getString("menus.target_players_animals");
                    } else if (!hasMob && hasAnimal && hasPlayer) {
                        // [6] Player+Animal -> All (Total Defense)
                        types.add("MOB");
                        types.add("ANIMAL");
                        types.add("PLAYER");
                        nextDesc = plugin.getLanguageManager().getString("menus.target_everything");
                    } else {
                        // [7] All (or invalid/empty) -> MobOnly (Reset)
                        types.add("MOB");
                        nextDesc = plugin.getLanguageManager().getString("menus.target_mobs_only");
                    }

                    petData.setStationTargetTypes(types);
                    petManager.updatePetData(petData);
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                    plugin.getLanguageManager().sendReplacements(player, "gui.station_targets", "targets", nextDesc);
                } else {
                    // Left Click -> Toggle ON/OFF
                    if (petData.getStationLocation() == null) {
                        // Turn ON
                        petData.setStationLocation(player.getLocation());

                        // Defaults Validation
                        if (petData.getStationRadius() <= 0.1)
                            petData.setStationRadius(10.0);
                        if (petData.getStationTargetTypes() == null || petData.getStationTargetTypes().isEmpty()) {
                            petData.setStationTargetTypes(new HashSet<>(Arrays.asList("PLAYER", "MOB")));
                        }

                        petData.setExplicitTargetUUID(null);
                        petManager.updatePetData(petData);
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 1f);
                        plugin.getLanguageManager().sendReplacements(player, "gui.station_active", "pet",
                                petData.getDisplayName());
                    } else {
                        // Turn OFF
                        petData.setStationLocation(null);
                        petManager.updatePetData(petData);
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 0.5f);
                        plugin.getLanguageManager().sendReplacements(player, "gui.station_inactive", "pet",
                                petData.getDisplayName());
                    }
                }
                guiManager.openPetMenu(player, petUUID);
            }
            case "toggle_target_prompt" -> {
                if (petData.getExplicitTargetUUID() != null) {
                    petData.setExplicitTargetUUID(null);
                    petManager.updatePetData(petData);
                    plugin.getLanguageManager().sendReplacements(player, "gui.target_cleared", "pet",
                            petData.getDisplayName());
                    guiManager.openPetMenu(player, petUUID);
                } else {
                    if (event.isRightClick()) {
                        // Start Target Selection Mode
                        player.closeInventory();
                        awaitingTargetSelection.put(player.getUniqueId(), petUUID);

                        plugin.getLanguageManager().sendMessage(player, "gui.target_selection_mode");
                    } else {
                        // Left Click -> Chat Prompt
                        awaitingTargetInput.put(player.getUniqueId(), petUUID);
                        player.closeInventory();
                        plugin.getLanguageManager().sendReplacements(player, "gui.target_prompt", "pet",
                                petData.getDisplayName());
                    }
                }
            }
            case "heal_pet" -> {
                Entity petEntity = Bukkit.getEntity(petUUID);
                if (!(petEntity instanceof LivingEntity le) || !petEntity.isValid()) {
                    plugin.getLanguageManager().sendMessage(player, "gui.heal_not_loaded");
                    return;
                }

                double maxHp = le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                double currentHp = le.getHealth();
                double missingHp = maxHp - currentHp;

                if (missingHp <= 0) {
                    plugin.getLanguageManager().sendReplacements(player, "gui.heal_full", "pet",
                            petData.getDisplayName());
                    return;
                }

                int xpCostPer = 100;
                if (event.isShiftClick()) {
                    // Full Heal
                    int requiredXp = (int) Math.ceil(missingHp) * xpCostPer;
                    int playerXp = player.getTotalExperience();
                    if (playerXp < requiredXp) {
                        plugin.getLanguageManager().sendReplacements(player, "gui.heal_no_xp", "needed",
                                String.valueOf(requiredXp), "current", String.valueOf(playerXp));
                        return;
                    }
                    player.giveExp(-requiredXp);
                    le.setHealth(maxHp);
                    plugin.getLanguageManager().sendReplacements(player, "gui.heal_success_full", "pet",
                            petData.getDisplayName(), "xp", String.valueOf(requiredXp));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                } else {
                    // Single HP heal
                    int requiredXp = xpCostPer;
                    int playerXp = player.getTotalExperience();
                    if (playerXp < requiredXp) {
                        plugin.getLanguageManager().sendReplacements(player, "gui.heal_no_xp", "needed",
                                String.valueOf(requiredXp), "current", String.valueOf(playerXp));
                        return;
                    }
                    player.giveExp(-requiredXp);
                    le.setHealth(Math.min(currentHp + 1, maxHp));
                    plugin.getLanguageManager().sendReplacements(player, "gui.heal_success_single", "pet",
                            petData.getDisplayName());
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
                }
                // Refresh GUI by reopening
                guiManager.openPetMenu(player, petUUID);
            }
            case "store_pet" -> {
                if (petData.isStored()) {
                    plugin.getLanguageManager().sendReplacements(player, "gui.store_already", "pet",
                            petData.getDisplayName());
                    return;
                }

                Entity petEntity = Bukkit.getEntity(petUUID);
                if (petEntity == null || !petEntity.isValid()) {
                    plugin.getLanguageManager().sendMessage(player, "gui.store_nearby_only");
                    return;
                }

                if (petEntity instanceof LivingEntity le) {
                    petManager.captureMetadata(petData, le);
                }
                petEntity.remove();

                petData.setStored(true);
                petData.setStationLocation(null); // Clear station on store
                petData.setExplicitTargetUUID(null); // Clear target on store
                petManager.updatePetData(petData);

                plugin.getLanguageManager().sendReplacements(player, "gui.store_success_prompt", "pet",
                        petData.getDisplayName());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
                player.closeInventory();
                guiManager.openMainMenu(player);
            }
        }
    }

    private void openConfirmMenu(Player player, UUID petUUID, boolean isRevive) {
        String title = isRevive ? plugin.getLanguageManager().getString("menus.confirm_revival_title")
                : plugin.getLanguageManager().getString("menus.confirm_removal_title");
        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.CONFIRM_ACTION), 27,
                title);
        ItemStack confirm = new ItemStack(isRevive ? plugin.getConfigManager().getReviveItem() : Material.BARRIER);
        ItemMeta meta = confirm.getItemMeta();
        meta.setDisplayName(title);
        meta.getPersistentDataContainer().set(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING,
                isRevive ? "do_revive_pet" : "do_remove_pet");
        meta.getPersistentDataContainer().set(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING,
                petUUID.toString());
        confirm.setItemMeta(meta);
        gui.setItem(11, confirm);
        ItemStack cancel = new ItemStack(Material.ARROW);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(plugin.getLanguageManager().getString("menus.cancel_btn"));
        cancelMeta.getPersistentDataContainer().set(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING,
                "cancel_confirm");
        cancelMeta.getPersistentDataContainer().set(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING,
                petUUID.toString());
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);
        player.openInventory(gui);
    }
}
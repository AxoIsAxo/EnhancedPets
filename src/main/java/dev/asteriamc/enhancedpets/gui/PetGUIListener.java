package dev.asteriamc.enhancedpets.gui;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import java.util.*;

public class PetGUIListener implements Listener {
    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final PetManagerGUI guiManager;
    private final BatchActionsGUI batchActionsGUI;
    private final Map<UUID, UUID> awaitingFriendlyInput = new HashMap<>();
    private final Map<UUID, UUID> awaitingRenameInput = new HashMap<>();
    private final Map<UUID, Boolean> awaitingBatchFriendlyInput = new HashMap<>();

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        boolean isPetGui = title.startsWith(ChatColor.DARK_AQUA.toString())
                || title.startsWith(ChatColor.DARK_RED + "Confirm Free:")
                || title.startsWith(ChatColor.GREEN + "Confirm Revival")
                || title.startsWith(ChatColor.DARK_RED + "Confirm Remove:")
                || title.startsWith(ChatColor.RED + "Confirm Removal");

        if (!isPetGui) return;

        
        if (title.equals(PetManagerGUI.MAIN_MENU_TITLE) && event.isShiftClick() && event.isRightClick()) {
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
                        player.sendMessage(ChatColor.GREEN + "Removed dead pet record for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + ".");
                        guiManager.openMainMenu(player);
                        return;
                    }
                }
            }
        }

        if (title.startsWith(ChatColor.GREEN + "Confirm Revival") || title.startsWith(ChatColor.RED + "Confirm Removal")) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null) return;
            PersistentDataContainer data = meta.getPersistentDataContainer();
            String action = data.get(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING);
            if (action != null) {
                handleRegularAction(player, action, data, title, event);
            }
            return;
        }

        
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer data = meta.getPersistentDataContainer();

        String batchAction = data.get(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING);
        if (batchAction != null) {
            handleBatchAction(player, batchAction, data, title);
            return;
        }

        String action = data.get(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING);
        if (action != null) {
            handleRegularAction(player, action, data, title, event);
            return;
        }

        String mainPetUUIDString = data.get(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING);
        if (title.equals(PetManagerGUI.MAIN_MENU_TITLE) && mainPetUUIDString != null) {
            this.guiManager.openPetMenu(player, UUID.fromString(mainPetUUIDString));
        }
    }

    private void handleBatchAction(Player player, String batchAction, PersistentDataContainer data, String title) {
        Set<UUID> selectedPets = batchActionsGUI.getPlayerSelections().computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        String typeName = data.get(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING);
        EntityType petType = typeName != null ? EntityType.valueOf(typeName) : null;
        Integer page = data.get(BatchActionsGUI.PAGE_KEY, PersistentDataType.INTEGER);

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
                Integer currentPage = data.get(BatchActionsGUI.PAGE_KEY, PersistentDataType.INTEGER);
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
                    player.sendMessage(ChatColor.RED + "You must select at least one pet to manage.");
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
                        player.sendMessage(ChatColor.YELLOW + "No dead pets of this type found.");
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
                        player.sendMessage(ChatColor.GREEN + "Successfully removed " + deadPets.size() + " dead pet record(s).");
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

    private void handleRegularAction(Player player, String action, PersistentDataContainer data, String title, InventoryClickEvent event) {
        String petUUIDString = data.get(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING);
        UUID petUUID = (petUUIDString != null) ? UUID.fromString(petUUIDString) : null;
        Set<UUID> selectedPets = batchActionsGUI.getPlayerSelections().get(player.getUniqueId());

        switch (action) {
            case "main_page" -> {
                Integer targetPage = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
                if (targetPage != null) guiManager.openMainMenu(player, targetPage);
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
                if (petUUID != null) guiManager.openPetMenu(player, petUUID);
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
                    player.sendMessage(ChatColor.RED + "Scan is only available for your own pets while viewing as another player.");
                    guiManager.openMainMenu(player);
                    return;
                }
                player.sendMessage(ChatColor.YELLOW + "Scanning for your unregistered pets...");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int foundCount = petManager.scanAndRegisterPetsForOwner(player);
                    if (foundCount > 0) {
                        player.sendMessage(ChatColor.GREEN + "Success! Found and registered " + foundCount + " new pet(s).");
                        guiManager.openMainMenu(player);
                    } else {
                        player.sendMessage(ChatColor.GREEN + "Scan complete. No new unregistered pets were found in loaded areas.");
                    }
                });
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
                player.sendMessage(petData.isFavorite()
                        ? ChatColor.GREEN + "Marked " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + " as a favorite!"
                        : ChatColor.YELLOW + "Removed " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + " from favorites.");
                guiManager.openPetMenu(player, petUUID);
                return;
            } else if (event.isLeftClick()) {
                
                guiManager.openCustomizationMenu(player, petUUID);
                return;
            }
        }

        if (action.startsWith("batch_")) {
            if (selectedPets == null || selectedPets.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Your pet selection was lost. Please start again.");
                guiManager.openMainMenu(player);
                return;
            }
            handleBatchManagementAction(player, action, data, selectedPets);
            return;
        }

        if (petUUID != null) {
            PetData petData = petManager.getPetData(petUUID);
            if (petData == null) {
                player.sendMessage(ChatColor.RED + "This pet no longer exists.");
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
        batchActionsGUI.clearSelections(playerId);
    }

    private void handleBatchManagementAction(Player player, String action, PersistentDataContainer data, Set<UUID> selectedPets) {
        if (selectedPets == null || selectedPets.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No pets selected or selection lost.");
            guiManager.openMainMenu(player);
            return;
        }
        List<PetData> petDataList = selectedPets.stream().map(petManager::getPetData).filter(Objects::nonNull).toList();

        switch (action) {
            case "batch_set_mode_PASSIVE", "batch_set_mode_NEUTRAL", "batch_set_mode_AGGRESSIVE" -> {
                BehaviorMode newMode = BehaviorMode.valueOf(action.substring(15));
                petDataList.forEach(pd -> pd.setMode(newMode));
                petManager.saveAllPetData(petDataList);
                if (newMode != BehaviorMode.AGGRESSIVE) {
                    petDataList.stream()
                            .map(pd -> Bukkit.getEntity(pd.getPetUUID()))
                            .filter(e -> e instanceof Creature)
                            .forEach(e -> ((Creature) e).setTarget(null));
                }
                player.sendMessage(ChatColor.GREEN + "Set mode for " + petDataList.size() + " pets to " + ChatColor.YELLOW + newMode.name() + ".");
                guiManager.openBatchManagementMenu(player, selectedPets);
            }
            case "batch_toggle_growth_pause" -> {
                java.util.List<UUID> babyUUIDs = selectedPets.stream()
                        .map(Bukkit::getEntity)
                        .filter(e -> e instanceof Ageable a && !a.isAdult())
                        .map(Entity::getUniqueId)
                        .toList();

                if (babyUUIDs.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No babies in your selection.");
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
                    player.sendMessage(ChatColor.GREEN + "Paused growth for " + changed + " baby pet(s).");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Resumed growth for " + changed + " baby pet(s).");
                }
                guiManager.openBatchManagementMenu(player, selectedPets);
            }
            case "batch_toggle_favorite" -> {
                long favoriteCount = petDataList.stream().filter(PetData::isFavorite).count();
                boolean makeFavorite = favoriteCount < petDataList.size();
                petDataList.forEach(pd -> pd.setFavorite(makeFavorite));
                petManager.saveAllPetData(petDataList);
                player.sendMessage(ChatColor.GREEN + (makeFavorite ? "Marked" : "Unmarked") + " " + petDataList.size() + " pets as favorites.");
                guiManager.openBatchManagementMenu(player, selectedPets);
            }
            case "batch_teleport" -> {
                int summoned = (int) selectedPets.stream()
                        .map(Bukkit::getEntity)
                        .filter(e -> e != null && e.isValid())
                        .peek(e -> e.teleport(player.getLocation()))
                        .count();
                player.sendMessage(ChatColor.GREEN + "Summoned " + summoned + " pets!");
            }
            case "batch_calm" -> {
                int calmed = (int) selectedPets.stream()
                        .map(Bukkit::getEntity)
                        .filter(e -> e instanceof Creature)
                        .peek(e -> {
                            ((Creature) e).setTarget(null);
                            if (e instanceof Wolf w) w.setAngry(false);
                        }).count();
                player.sendMessage(ChatColor.GREEN + "Calmed " + calmed + " pets.");
            }
            case "batch_toggle_sit" -> {
                List<Sittable> sittables = selectedPets.stream().map(Bukkit::getEntity).filter(e -> e instanceof Sittable).map(e -> (Sittable) e).toList();
                if (!sittables.isEmpty()) {
                    long sittingCount = sittables.stream().filter(Sittable::isSitting).count();
                    boolean shouldSit = sittingCount < sittables.size();
                    sittables.forEach(s -> s.setSitting(shouldSit));
                    player.sendMessage(ChatColor.GREEN + "Told " + sittables.size() + " pets to " + (shouldSit ? "sit." : "stand."));
                    guiManager.openBatchManagementMenu(player, selectedPets);
                }
            }
            case "batch_free_pet_prompt" -> guiManager.openBatchConfirmFreeMenu(player, selectedPets);
            case "batch_confirm_free" -> {
                player.closeInventory();
                int count = selectedPets.size();
                selectedPets.forEach(petManager::freePetCompletely);
                batchActionsGUI.getPlayerSelections().remove(player.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + "You have freed " + count + " pets.");
            }
            case "batch_manage_friendly" -> guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, 0);
            case "batch_friendly_page" -> {
                Integer page = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
                if (page != null) guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, page);
            }
            case "add_batch_friendly_prompt" -> {
                awaitingBatchFriendlyInput.put(player.getUniqueId(), true);
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "Please type the name of the player to add as friendly for all selected pets.");
                player.sendMessage(ChatColor.GRAY + "(Type 'cancel' to abort)");
            }
            case "remove_batch_friendly" -> {
                String targetUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
                if (targetUUIDString != null) {
                    UUID targetUUID = UUID.fromString(targetUUIDString);
                    petDataList.forEach(pd -> pd.removeFriendlyPlayer(targetUUID));
                    petManager.saveAllPetData(petDataList);
                    player.sendMessage(ChatColor.GREEN + "Removed " + Bukkit.getOfflinePlayer(targetUUID).getName() + " from " + petDataList.size() + " pets' friendly lists.");
                    guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, 0);
                }
            }
            case "batch_open_transfer" -> guiManager.openBatchTransferMenu(player, selectedPets);
            case "batch_transfer_to_player" -> {
                String targetUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
                if (targetUUIDString != null) {
                    Player targetPlayer = Bukkit.getPlayer(UUID.fromString(targetUUIDString));
                    if (targetPlayer == null || !targetPlayer.isOnline()) {
                        player.sendMessage(ChatColor.RED + "That player is no longer online!");
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
                    player.sendMessage(ChatColor.GREEN + "You transferred " + petDataList.size() + " pets to " + ChatColor.YELLOW + targetPlayer.getName());
                    targetPlayer.sendMessage(ChatColor.GREEN + "You received " + petDataList.size() + " pets from " + ChatColor.YELLOW + oldOwnerName);
                }
            }
            case "batch_toggle_protection" -> {
                long protectedCount = petDataList.stream().filter(PetData::isProtectedFromPlayers).count();
                boolean makeProtected = protectedCount < petDataList.size();
                petDataList.forEach(pd -> pd.setProtectedFromPlayers(makeProtected));
                petManager.saveAllPetData(petDataList);
                player.sendMessage((makeProtected ? ChatColor.GREEN + "Enabled" : ChatColor.YELLOW + "Disabled")
                        + ChatColor.GREEN + " Mutual Non-Aggression for " + petDataList.size() + " pets.");
                guiManager.openBatchManagementMenu(player, selectedPets);
            }
        }
    }

    private void handleSinglePetAction(Player player, String action, PetData petData, PersistentDataContainer data, InventoryClickEvent event) {
        UUID petUUID = petData.getPetUUID();
        switch (action) {
            case "set_mode_PASSIVE", "set_mode_NEUTRAL", "set_mode_AGGRESSIVE" -> {
                if (player.hasPermission("enhancedpets.use")) {
                    BehaviorMode newMode = BehaviorMode.valueOf(action.substring(9));
                    if (petData.getMode() != newMode) {
                        petData.setMode(newMode);
                        petManager.updatePetData(petData);
                        player.sendMessage(ChatColor.GREEN + "Set " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + "'s mode to " + ChatColor.YELLOW + newMode.name());
                        if (newMode != BehaviorMode.AGGRESSIVE && Bukkit.getEntity(petUUID) instanceof Creature c) {
                            c.setTarget(null);
                        }
                    }
                    guiManager.openPetMenu(player, petUUID);
                }
            }
            case "confirm_free_pet_prompt" -> guiManager.openConfirmFreeMenu(player, petUUID);
            case "manage_friendly" -> guiManager.openFriendlyPlayerMenu(player, petUUID, 0);
            case "toggle_growth_pause" -> {
                boolean paused = petData.isGrowthPaused();
                plugin.getPetManager().setGrowthPaused(petUUID, !paused);
                player.sendMessage(ChatColor.GREEN + (paused ? "Resumed" : "Paused") +
                        " growth for " + petData.getDisplayName());
                guiManager.openPetMenu(player, petUUID);
            }
            case "teleport_pet" -> {
                Entity petEntity = Bukkit.getEntity(petUUID);
                if (petEntity != null && petEntity.isValid()) {
                    petEntity.teleport(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Summoned " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + "!");
                } else {
                    player.sendMessage(ChatColor.RED + "Could not find " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.RED + ". Is it loaded in the world?");
                }
            }
            case "free_pet" -> {
                petManager.freePetCompletely(petUUID);
                player.sendMessage(ChatColor.YELLOW + "You have freed " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + ".");
                guiManager.openMainMenu(player);
            }
            case "friendly_page" -> {
                Integer friendlyPage = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
                if (friendlyPage != null) guiManager.openFriendlyPlayerMenu(player, petUUID, friendlyPage);
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
                    player.sendMessage(ChatColor.YELLOW + "Reset name of " + ChatColor.AQUA + oldName + ChatColor.YELLOW + " to " + ChatColor.AQUA + newDefaultName + ChatColor.YELLOW + ".");
                    guiManager.openPetMenu(player, petUUID);
                } else {
                    awaitingRenameInput.put(player.getUniqueId(), petUUID);
                    player.closeInventory();
                    player.sendMessage(ChatColor.GOLD + "Enter a new name for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GOLD + " in chat.");
                    player.sendMessage(ChatColor.GRAY + "Allowed characters: A-Z, a-z, 0-9, _, -");
                    player.sendMessage(ChatColor.GRAY + "Using other characters will cancel the rename.");
                    player.sendMessage(ChatColor.GRAY + "Type 'cancel' to abort.");
                }
            }

            case "open_transfer" -> guiManager.openTransferMenu(player, petUUID);
            case "transfer_to_player" -> {
                String targetUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
                if (targetUUIDString != null) {
                    Player targetPlayer = Bukkit.getPlayer(UUID.fromString(targetUUIDString));
                    if (targetPlayer == null || !targetPlayer.isOnline()) {
                        player.sendMessage(ChatColor.RED + "That player is no longer online!");
                        guiManager.openTransferMenu(player, petUUID);
                        return;
                    }
                    String oldOwnerName = player.getName();
                    petData.setOwnerUUID(targetPlayer.getUniqueId());
                    petManager.updatePetData(petData);
                    if (Bukkit.getEntity(petUUID) instanceof Tameable t) t.setOwner(targetPlayer);
                    player.sendMessage(ChatColor.GREEN + "You transferred " + ChatColor.AQUA + petData.getDisplayName() + " to " + ChatColor.YELLOW + targetPlayer.getName());
                    targetPlayer.sendMessage(ChatColor.GREEN + "You received " + ChatColor.AQUA + petData.getDisplayName() + " from " + ChatColor.YELLOW + oldOwnerName);
                    player.closeInventory();
                }
            }
            case "toggle_sit" -> {
                if (Bukkit.getEntity(petUUID) instanceof Sittable s) {
                    s.setSitting(!s.isSitting());
                    player.sendMessage(ChatColor.GREEN + petData.getDisplayName() + " is now " + (s.isSitting() ? "sitting." : "standing."));
                    guiManager.openPetMenu(player, petUUID);
                }
            }
            case "calm_pet" -> {
                if (Bukkit.getEntity(petUUID) instanceof Creature c) {
                    c.setTarget(null);
                    if (c instanceof Wolf w) w.setAngry(false);
                    player.sendMessage(ChatColor.GREEN + "Calmed " + ChatColor.AQUA + petData.getDisplayName() + ".");
                } else {
                    player.sendMessage(ChatColor.RED + "Could not find " + petData.getDisplayName() + " in the world.");
                }
            }
            case "confirm_free" -> {
                String petDisplayName = petData.getDisplayName();
                petManager.freePetCompletely(petUUID);
                player.sendMessage(ChatColor.YELLOW + "You have freed " + ChatColor.AQUA + petDisplayName + ".");
                guiManager.openMainMenu(player);
            }
            case "add_friendly_prompt" -> {
                awaitingFriendlyInput.put(player.getUniqueId(), petUUID);
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "Please type the name of the player to add as friendly for " + ChatColor.AQUA + petData.getDisplayName() + ".");
                player.sendMessage(ChatColor.GRAY + "(Type 'cancel' to abort)");
            }
            case "remove_friendly" -> {
                String targetFriendUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
                if (targetFriendUUIDString != null) {
                    UUID targetFriendUUID = UUID.fromString(targetFriendUUIDString);
                    petData.removeFriendlyPlayer(targetFriendUUID);
                    petManager.updatePetData(petData);
                    player.sendMessage(ChatColor.GREEN + "Removed " + Bukkit.getOfflinePlayer(targetFriendUUID).getName() + " from " + petData.getDisplayName() + "'s friendly list.");
                    guiManager.openFriendlyPlayerMenu(player, petUUID, 0);
                }
            }
            case "confirm_revive_pet" -> openConfirmMenu(player, petUUID, true);
            case "confirm_remove_pet" -> openConfirmMenu(player, petUUID, false);
            case "do_revive_pet" -> {
                if (!petData.isDead()) {
                    player.sendMessage(ChatColor.RED + "This pet is not dead.");
                    guiManager.openPetMenu(player, petData.getPetUUID());
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() != Material.NETHER_STAR) {
                    player.sendMessage(ChatColor.RED + "You need a Nether Star in your main hand to revive this pet.");
                    guiManager.openPetMenu(player, petData.getPetUUID());
                    return;
                }
                hand.setAmount(hand.getAmount() - 1);
                LivingEntity newPet = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), petData.getEntityType());
                petManager.revivePet(petData, newPet);
                player.sendMessage(ChatColor.GREEN + "You have revived " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + "!");
                guiManager.openPetMenu(player, newPet.getUniqueId());
            }
            case "do_remove_pet" -> {
                petManager.freePetCompletely(petUUID);
                player.sendMessage(ChatColor.YELLOW + "You have permanently deleted " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + ".");
                guiManager.openMainMenu(player);
            }
            case "cancel_confirm" -> guiManager.openPetMenu(player, petUUID);

            
            case "set_display_icon" -> {
                if (event.isShiftClick()) {
                    petData.setCustomIconMaterial(null);
                    petManager.updatePetData(petData);
                    player.sendMessage(ChatColor.YELLOW + "Reset icon for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + " to default.");
                } else {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand == null || hand.getType().isAir()) {
                        player.sendMessage(ChatColor.RED + "Hold an item in your main hand to set as icon.");
                    } else {
                        petData.setCustomIconMaterial(hand.getType().name());
                        petManager.updatePetData(petData);
                        player.sendMessage(ChatColor.GREEN + "Set icon for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + " to " + ChatColor.YELLOW + hand.getType().name() + ChatColor.GREEN + ".");
                    }
                }
                
                guiManager.openPetMenu(player, petUUID);
            }

            
            case "set_display_color" -> {
                if (event.isShiftClick()) {
                    petData.setDisplayColor(null);
                    petManager.updatePetData(petData);
                    player.sendMessage(ChatColor.YELLOW + "Reset color for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + " to default.");
                    
                    guiManager.openPetMenu(player, petUUID);
                } else {
                    guiManager.openColorPicker(player, petUUID);
                }
            }
            case "choose_color" -> {
                String colorName = data.get(PetManagerGUI.COLOR_KEY, PersistentDataType.STRING);
                if (colorName == null || colorName.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Invalid color selection.");
                } else {
                    petData.setDisplayColor(colorName);
                    petManager.updatePetData(petData);
                    player.sendMessage(ChatColor.GREEN + "Set color for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + " to " + ChatColor.YELLOW + colorName + ChatColor.GREEN + ".");
                }
                guiManager.openPetMenu(player, petUUID);
            }

            case "toggle_mutual_protection" -> {
                boolean nowProtected = !petData.isProtectedFromPlayers();
                petData.setProtectedFromPlayers(nowProtected);
                petManager.updatePetData(petData);
                if (nowProtected) {
                    player.sendMessage(ChatColor.GREEN + "Enabled Mutual Non-Aggression for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + ".");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Disabled Mutual Non-Aggression for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + ".");
                }
                guiManager.openPetMenu(player, petUUID);
            }
        }
    }

    private void openConfirmMenu(Player player, UUID petUUID, boolean isRevive) {
        Inventory gui = Bukkit.createInventory(player, 27, (isRevive ? ChatColor.GREEN + "Confirm Revival" : ChatColor.RED + "Confirm Removal"));
        ItemStack confirm = new ItemStack(isRevive ? Material.NETHER_STAR : Material.BARRIER);
        ItemMeta meta = confirm.getItemMeta();
        meta.setDisplayName(isRevive ? ChatColor.GREEN + "Confirm Revival" : ChatColor.RED + "Confirm Removal");
        meta.getPersistentDataContainer().set(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING, isRevive ? "do_revive_pet" : "do_remove_pet");
        meta.getPersistentDataContainer().set(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
        confirm.setItemMeta(meta);
        gui.setItem(11, confirm);
        ItemStack cancel = new ItemStack(Material.ARROW);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.YELLOW + "Cancel");
        cancelMeta.getPersistentDataContainer().set(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING, "cancel_confirm");
        cancelMeta.getPersistentDataContainer().set(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);
        player.openInventory(gui);
    }
}
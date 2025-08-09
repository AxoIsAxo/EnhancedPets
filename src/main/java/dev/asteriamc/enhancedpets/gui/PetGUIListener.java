package dev.asteriamc.enhancedpets.gui;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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

   public Map<UUID, UUID> getAwaitingFriendlyInputMap() { return this.awaitingFriendlyInput; }
   public Map<UUID, UUID> getAwaitingRenameInputMap() { return this.awaitingRenameInput; }
   public Map<UUID, Boolean> getAwaitingBatchFriendlyInputMap() { return this.awaitingBatchFriendlyInput; }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;

      String title = event.getView().getTitle();
      boolean isPetGui = title.startsWith(ChatColor.DARK_AQUA.toString())
              || title.startsWith(ChatColor.DARK_RED + "Confirm Free:")
              || title.startsWith(ChatColor.GREEN + "Confirm Revival")
              || title.startsWith(ChatColor.RED + "Confirm Removal");

      if (!isPetGui) return;
      
      if (title.startsWith(ChatColor.GREEN + "Confirm Revival") || title.startsWith(ChatColor.RED + "Confirm Removal")) {
         event.setCancelled(true);
         ItemStack clickedItem = event.getCurrentItem();
         if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
         ItemMeta meta = clickedItem.getItemMeta();
         if (meta == null) return;
         PersistentDataContainer data = meta.getPersistentDataContainer();
         String action = data.get(PetManagerGUI.ACTION_KEY, PersistentDataType.STRING);
         if (action != null) {
            handleRegularAction(player, action, data, title);
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
         handleRegularAction(player, action, data, title);
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
         case "open_type_select":
            batchActionsGUI.openPetTypeSelectionMenu(player);
            break;
         case "select_pet_type":
            if (petType != null) {
               selectedPets.clear();
               batchActionsGUI.openPetSelectionMenu(player, petType, 0);
            }
            break;
         case "batch_select_page":
            if (petType != null && page != null) {
               batchActionsGUI.openPetSelectionMenu(player, petType, page);
            }
            break;
         case "toggle_pet_selection":
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
         break;
         case "select_all":
         case "select_none":
            if (petType != null) {
               List<UUID> petsOfType = petManager.getPetsOwnedBy(player.getUniqueId()).stream()
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
            break;
         case "open_batch_manage":
            if (selectedPets.isEmpty()) {
               player.sendMessage(ChatColor.RED + "You must select at least one pet to manage.");
            } else {
               guiManager.openBatchManagementMenu(player, selectedPets);
            }
            break;
         case "open_pet_select":
            if (petType != null) {
               batchActionsGUI.openPetSelectionMenu(player, petType, 0);
            }
            break;
      }
   }


   private void handleRegularAction(Player player, String action, PersistentDataContainer data, String title) {
      
      String petUUIDString = data.get(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING);
      UUID petUUID = (petUUIDString != null) ? UUID.fromString(petUUIDString) : null;
      Set<UUID> selectedPets = batchActionsGUI.getPlayerSelections().get(player.getUniqueId());

      
      switch (action) {

         case "main_page":
            Integer targetPage = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
            if (targetPage != null) guiManager.openMainMenu(player, targetPage);
            return;
         case "back_to_main":
            guiManager.openMainMenu(player);
            return;
         case "batch_actions": 
            batchActionsGUI.openPetTypeSelectionMenu(player);
            return;
         case "back_to_pet":
         case "cancel_free": 
            if (petUUID != null) guiManager.openPetMenu(player, petUUID);
            return;
         case "open_batch_manage": 
            if (selectedPets != null && !selectedPets.isEmpty()) {
               guiManager.openBatchManagementMenu(player, selectedPets);
            } else {
               guiManager.openMainMenu(player); 
            }
            return;
         case "scan_for_pets":
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Scanning for your unregistered pets...");

            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
               int foundCount = petManager.scanAndRegisterPetsForOwner(player);

               
               Bukkit.getScheduler().runTask(plugin, () -> {
                  if (foundCount > 0) {
                     player.sendMessage(ChatColor.GREEN + "Success! Found and registered " + foundCount + " new pet(s).");
                     guiManager.openMainMenu(player); 
                  } else {
                     player.sendMessage(ChatColor.GREEN + "Scan complete. No new unregistered pets were found in loaded areas.");
                  }
               });
            });
            return;

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
         handleSinglePetAction(player, action, petData, data);
      }
   }

   private void handleBatchManagementAction(Player player, String action, PersistentDataContainer data, Set<UUID> selectedPets) {
      if (selectedPets == null || selectedPets.isEmpty()) {
         player.sendMessage(ChatColor.RED + "No pets selected or selection lost.");
         guiManager.openMainMenu(player);
         return;
      }
      List<PetData> petDataList = selectedPets.stream().map(petManager::getPetData).filter(Objects::nonNull).toList();

      switch(action) {
         case "batch_set_mode_PASSIVE":
         case "batch_set_mode_NEUTRAL":
         case "batch_set_mode_AGGRESSIVE":
            
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
            break;
         case "batch_toggle_growth_pause":
            selectedPets.stream()
                    .map(petManager::getPetData)
                    .filter(Objects::nonNull)
                    .forEach(pd -> plugin.getPetManager().setGrowthPaused(pd.getPetUUID(), !pd.isGrowthPaused()));
            player.sendMessage(ChatColor.GREEN + "Toggled growth pause for all selected babies.");
            guiManager.openBatchManagementMenu(player, selectedPets);
            break;
         case "batch_toggle_favorite":
            long favoriteCount = petDataList.stream().filter(PetData::isFavorite).count();
            boolean makeFavorite = favoriteCount < petDataList.size();
            petDataList.forEach(pd -> {
               pd.setFavorite(makeFavorite);
            });
            petManager.saveAllPetData(petDataList);
            player.sendMessage(ChatColor.GREEN + (makeFavorite ? "Marked" : "Unmarked") + " " + petDataList.size() + " pets as favorites.");
            guiManager.openBatchManagementMenu(player, selectedPets);
            break;
         case "batch_teleport":
            int summoned = (int) selectedPets.stream()
                    .map(Bukkit::getEntity)
                    .filter(e -> e != null && e.isValid())
                    .peek(e -> e.teleport(player.getLocation()))
                    .count();
            player.sendMessage(ChatColor.GREEN + "Summoned " + summoned + " pets!");
            break;
         case "batch_calm":
            int calmed = (int) selectedPets.stream()
                    .map(Bukkit::getEntity)
                    .filter(e -> e instanceof Creature)
                    .peek(e -> {
                       ((Creature)e).setTarget(null);
                       if (e instanceof Wolf w) w.setAngry(false);
                    }).count();
            player.sendMessage(ChatColor.GREEN + "Calmed " + calmed + " pets.");
            break;
         case "batch_toggle_sit":
            List<Sittable> sittables = selectedPets.stream().map(Bukkit::getEntity).filter(e -> e instanceof Sittable).map(e -> (Sittable)e).toList();
            if (!sittables.isEmpty()) {
               long sittingCount = sittables.stream().filter(Sittable::isSitting).count();
               boolean shouldSit = sittingCount < sittables.size();
               sittables.forEach(s -> s.setSitting(shouldSit));
               player.sendMessage(ChatColor.GREEN + "Told " + sittables.size() + " pets to " + (shouldSit ? "sit." : "stand."));
               guiManager.openBatchManagementMenu(player, selectedPets);
            }
            break;
         case "batch_free_pet_prompt":
            guiManager.openBatchConfirmFreeMenu(player, selectedPets);
            break;
         case "batch_confirm_free":
            player.closeInventory();
            int count = selectedPets.size();
            selectedPets.forEach(petManager::freePet);
            batchActionsGUI.getPlayerSelections().remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "You have freed " + count + " pets.");
            break;
         case "batch_manage_friendly":
            guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, 0);
            break;
         case "batch_friendly_page":
            Integer page = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
            if (page != null) guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, page);
            break;
         case "add_batch_friendly_prompt":
            awaitingBatchFriendlyInput.put(player.getUniqueId(), true);
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "Please type the name of the player to add as friendly for all selected pets.");
            player.sendMessage(ChatColor.GRAY + "(Type 'cancel' to abort)");
            break;
         case "remove_batch_friendly":
            String targetUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
            if (targetUUIDString != null) {
               UUID targetUUID = UUID.fromString(targetUUIDString);
               petDataList.forEach(pd -> pd.removeFriendlyPlayer(targetUUID));
               petManager.saveAllPetData(petDataList);
               player.sendMessage(ChatColor.GREEN + "Removed " + Bukkit.getOfflinePlayer(targetUUID).getName() + " from " + petDataList.size() + " pets' friendly lists.");
               guiManager.openBatchFriendlyPlayerMenu(player, selectedPets, 0);
            }
            break;
         case "batch_open_transfer":
            guiManager.openBatchTransferMenu(player, selectedPets);
            break;
         case "batch_transfer_to_player":
            targetUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
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
            break;
      }
   }


   private void handleSinglePetAction(Player player, String action, PetData petData, PersistentDataContainer data) {
      UUID petUUID = petData.getPetUUID();
      switch (action) {
         case "set_mode_PASSIVE":
         case "set_mode_NEUTRAL":
         case "set_mode_AGGRESSIVE":
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
            break;
         case "confirm_free_pet_prompt":
            guiManager.openConfirmFreeMenu(player, petUUID);
            break;
         case "manage_friendly":
            guiManager.openFriendlyPlayerMenu(player, petUUID, 0);
            break;

         case "toggle_growth_pause":
            boolean paused = petData.isGrowthPaused();
            plugin.getPetManager().setGrowthPaused(petUUID, !paused);
            player.sendMessage(ChatColor.GREEN + (paused ? "Resumed" : "Paused") +
                    " growth for " + petData.getDisplayName());
            guiManager.openPetMenu(player, petUUID);
            break;

         case "teleport_pet":
            Entity petEntity = Bukkit.getEntity(petUUID);
            if (petEntity != null && petEntity.isValid()) {
               petEntity.teleport(player.getLocation());
               player.sendMessage(ChatColor.GREEN + "Summoned " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + "!");
            } else {
               player.sendMessage(ChatColor.RED + "Could not find " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.RED + ". Is it loaded in the world?");
            }
            break;
         case "free_pet":
            petManager.freePetCompletely(petUUID);
            player.sendMessage(ChatColor.YELLOW + "You have freed " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + ".");
            guiManager.openMainMenu(player);
            break;
         case "friendly_page":
            Integer friendlyPage = data.get(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER);
            if (friendlyPage != null) guiManager.openFriendlyPlayerMenu(player, petUUID, friendlyPage);
            break;
         case "toggle_favorite":
            petData.setFavorite(!petData.isFavorite());
            petManager.updatePetData(petData);
            player.sendMessage(petData.isFavorite() ? ChatColor.GREEN + "Marked " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GREEN + " as a favorite!" : ChatColor.YELLOW + "Removed " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + " from favorites.");
            guiManager.openPetMenu(player, petUUID);
            break;
         case "rename_pet_prompt":
            awaitingRenameInput.put(player.getUniqueId(), petUUID);
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "Enter a new name for " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.GOLD + " in chat.");
            player.sendMessage(ChatColor.GRAY + "Allowed characters: A-Z, a-z, 0-9, _, -");
            player.sendMessage(ChatColor.GRAY + "Any other character (e.g., a space) will reset the name.");
            player.sendMessage(ChatColor.GRAY + "Type 'cancel' to abort.");
            break;
         case "open_transfer":
            guiManager.openTransferMenu(player, petUUID);
            break;
         case "transfer_to_player":
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
            break;
         case "toggle_sit":
            if (Bukkit.getEntity(petUUID) instanceof Sittable s) {
               s.setSitting(!s.isSitting());
               player.sendMessage(ChatColor.GREEN + petData.getDisplayName() + " is now " + (s.isSitting() ? "sitting." : "standing."));
               guiManager.openPetMenu(player, petUUID);
            }
            break;
         case "calm_pet":
            if (Bukkit.getEntity(petUUID) instanceof Creature c) {
               c.setTarget(null);
               if (c instanceof Wolf w) w.setAngry(false);
               player.sendMessage(ChatColor.GREEN + "Calmed " + ChatColor.AQUA + petData.getDisplayName() + ".");
            } else {
               player.sendMessage(ChatColor.RED + "Could not find " + petData.getDisplayName() + " in the world.");
            }
            break;
         case "confirm_free":
            String petDisplayName = petData.getDisplayName();
            
            petManager.freePetCompletely(petUUID);
            player.sendMessage(ChatColor.YELLOW + "You have freed " + ChatColor.AQUA + petDisplayName + ".");
            
            guiManager.openMainMenu(player);
            break;
         case "add_friendly_prompt":
            awaitingFriendlyInput.put(player.getUniqueId(), petUUID);
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "Please type the name of the player to add as friendly for " + ChatColor.AQUA + petData.getDisplayName() + ".");
            player.sendMessage(ChatColor.GRAY + "(Type 'cancel' to abort)");
            break;
         case "remove_friendly":
            String targetFriendUUIDString = data.get(PetManagerGUI.TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING);
            if (targetFriendUUIDString != null) {
               UUID targetFriendUUID = UUID.fromString(targetFriendUUIDString);
               petData.removeFriendlyPlayer(targetFriendUUID);
               petManager.updatePetData(petData);
               player.sendMessage(ChatColor.GREEN + "Removed " + Bukkit.getOfflinePlayer(targetFriendUUID).getName() + " from " + petData.getDisplayName() + "'s friendly list.");
               guiManager.openFriendlyPlayerMenu(player, petUUID, 0);
            }
            break;
         case "confirm_revive_pet":
            openConfirmMenu(player, petUUID, true);
            break;
         case "confirm_remove_pet":
            openConfirmMenu(player, petUUID, false);
            break;
         case "do_revive_pet":
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
            break;
         case "do_remove_pet":
            petManager.freePetCompletely(petUUID);
            player.sendMessage(ChatColor.YELLOW + "You have permanently deleted " + ChatColor.AQUA + petData.getDisplayName() + ChatColor.YELLOW + ".");
            guiManager.openMainMenu(player);
            break;
         case "cancel_confirm":
            guiManager.openPetMenu(player, petUUID);
            break;
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
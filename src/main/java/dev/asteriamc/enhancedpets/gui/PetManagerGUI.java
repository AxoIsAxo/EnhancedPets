package dev.asteriamc.enhancedpets.gui;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;

import java.util.*;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

public class PetManagerGUI {
   private final Enhancedpets plugin;
   private final PetManager petManager;
   private final BatchActionsGUI batchActionsGUI;
   public static final NamespacedKey PET_UUID_KEY = new NamespacedKey(Enhancedpets.getInstance(), "pet_uuid");
   public static final NamespacedKey ACTION_KEY = new NamespacedKey(Enhancedpets.getInstance(), "gui_action");
   public static final NamespacedKey TARGET_PLAYER_UUID_KEY = new NamespacedKey(Enhancedpets.getInstance(), "target_player_uuid");
   public static final String MAIN_MENU_TITLE = ChatColor.DARK_AQUA + "Your Enhanced Pets";
   public static final String PET_MENU_TITLE_PREFIX = ChatColor.DARK_AQUA + "Manage Pet: ";
   public static final String FRIENDLY_MENU_TITLE_PREFIX = ChatColor.DARK_AQUA + "Friendly Players: ";
   public static final NamespacedKey PAGE_KEY = new NamespacedKey(Enhancedpets.getInstance(), "gui_page");

   public PetManagerGUI(Enhancedpets plugin) {
      this.plugin = plugin;
      this.petManager = plugin.getPetManager();
      this.batchActionsGUI = new BatchActionsGUI(plugin, this);
   }

   public BatchActionsGUI getBatchActionsGUI() {
      return this.batchActionsGUI;
   }

   public void openMainMenu(Player player) {
      openMainMenu(player, 0);
   }

   public void openMainMenu(Player player, int page) {
      List<PetData> pets = this.petManager.getPetsOwnedBy(player.getUniqueId());

      
      boolean didUpdate = false;
      for (PetData petData : pets) {
         Entity petEntity = Bukkit.getEntity(petData.getPetUUID());
         if (petEntity != null && petEntity.isValid() && petEntity.getCustomName() != null) {
            String gameName = ChatColor.stripColor(petEntity.getCustomName());
            if (!gameName.isEmpty() && !gameName.equals(petData.getDisplayName())) {
               petData.setDisplayName(gameName);
               didUpdate = true;
            }
         }
      }
      if (didUpdate) {
         this.petManager.saveAllPetData(pets);
      }

      
      pets.sort(Comparator.comparing(PetData::isFavorite).reversed()
              .thenComparing(p -> p.getEntityType().name()) 
              .thenComparing((p1, p2) -> {
                 String name1 = p1.getDisplayName();
                 String name2 = p2.getDisplayName();
                 Integer id1 = extractPetIdFromName(name1);
                 Integer id2 = extractPetIdFromName(name2);
                 if (id1 != null && id2 != null) {
                    return Integer.compare(id1, id2);
                 }
                 return String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
              }));

      final int petsPerPage = 45;
      final int totalPets = pets.size();

      
      if (totalPets <= petsPerPage && page == 0) {
         
         int rows = (int) Math.ceil((double) totalPets / 9.0);
         int invSize = Math.max(27, (rows + 1) * 9); 

         Inventory gui = Bukkit.createInventory(player, invSize, MAIN_MENU_TITLE);

         if (pets.isEmpty()) {
            gui.setItem(13, this.createItem(Material.BARRIER, ChatColor.RED + "No Pets Found",
                    Collections.singletonList(ChatColor.GRAY + "Tame some wolves or cats!")));
         } else {
            for (int i = 0; i < pets.size(); i++) {
               gui.setItem(i, this.createPetItem(pets.get(i)));
            }

            
            gui.setItem(invSize - 8, createActionButton(Material.COMPASS, ChatColor.AQUA + "Scan for My Pets", "scan_for_pets", null,
                    Arrays.asList(ChatColor.GRAY + "Scans loaded areas for your unregistered pets.", "", ChatColor.YELLOW + "Click to scan and sync.")));
            gui.setItem(invSize - 5, this.createItem(Material.PAPER, ChatColor.AQUA + "Page 1 / 1", Collections.singletonList(ChatColor.GRAY + "Total Pets: " + totalPets)));
            gui.setItem(invSize - 2, createActionButton(Material.HOPPER, ChatColor.GOLD + "Batch Actions", "batch_actions", null,
                    Collections.singletonList(ChatColor.GRAY + "Manage multiple pets at once.")));
         }
         player.openInventory(gui);

      } else {
         
         int totalPages = (int) Math.ceil((double) totalPets / petsPerPage);
         page = Math.max(0, Math.min(page, totalPages - 1));
         int invSize = 54;

         Inventory gui = Bukkit.createInventory(player, invSize, MAIN_MENU_TITLE);
         int startIndex = page * petsPerPage;
         int endIndex = Math.min(startIndex + petsPerPage, totalPets);
         List<PetData> petsToShow = pets.subList(startIndex, endIndex);

         for (int i = 0; i < petsToShow.size(); i++) {
            gui.setItem(i, this.createPetItem(petsToShow.get(i)));
         }

         
         if (page > 0) {
            gui.setItem(invSize - 9, this.createNavigationButton(Material.ARROW, ChatColor.GREEN + "Previous Page", "main_page", page - 1, null));
         }
         if (endIndex < totalPets) {
            gui.setItem(invSize - 1, this.createNavigationButton(Material.ARROW, ChatColor.GREEN + "Next Page", "main_page", page + 1, null));
         }
         gui.setItem(invSize - 2, createActionButton(Material.HOPPER, ChatColor.GOLD + "Batch Actions", "batch_actions", null,
                 Collections.singletonList(ChatColor.GRAY + "Manage multiple pets at once.")));
         gui.setItem(invSize - 8, createActionButton(Material.COMPASS, ChatColor.AQUA + "Scan for My Pets", "scan_for_pets", null,
                 Arrays.asList(ChatColor.GRAY + "Scans loaded areas for your unregistered pets.", "", ChatColor.YELLOW + "Click to scan and sync.")));
         gui.setItem(invSize - 5, this.createItem(Material.PAPER, ChatColor.AQUA + "Page " + (page + 1) + " / " + totalPages,
                 Collections.singletonList(ChatColor.GRAY + "Total Pets: " + totalPets)));

         player.openInventory(gui);
      }
   }

   public void openBatchManagementMenu(Player player, Set<UUID> selectedPetUUIDs) {
      if (selectedPetUUIDs == null || selectedPetUUIDs.isEmpty()) {
         player.sendMessage(ChatColor.RED + "You haven't selected any pets to manage.");
         this.openMainMenu(player);
         return;
      }

      List<PetData> selectedPets = selectedPetUUIDs.stream()
              .map(petManager::getPetData)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      String title = ChatColor.DARK_AQUA + "Batch Manage " + selectedPets.size() + " Pets";
      Inventory gui = Bukkit.createInventory(player, 27, title);

      
      long favoriteCount = selectedPets.stream().filter(PetData::isFavorite).count();
      boolean allFavorites = favoriteCount == selectedPets.size();
      boolean anyFavorites = favoriteCount > 0;

      BehaviorMode commonMode = selectedPets.stream().map(PetData::getMode).distinct().count() == 1
              ? selectedPets.get(0).getMode() : BehaviorMode.BATCH;

      List<Entity> petEntities = selectedPets.stream().map(p -> Bukkit.getEntity(p.getPetUUID())).filter(Objects::nonNull).toList();
      boolean allCanSit = !petEntities.isEmpty() && petEntities.stream().allMatch(e -> e instanceof Sittable);
      long sittingCount = petEntities.stream().filter(e -> e instanceof Sittable s && s.isSitting()).count();
      boolean allSitting = allCanSit && sittingCount == petEntities.size();
      boolean anySitting = allCanSit && sittingCount > 0;


      
      PetData batchData = new PetData(null, player.getUniqueId(), null, "Batch");
      batchData.setMode(commonMode);

      
      String favoriteDisplayName = (anyFavorites ? ChatColor.GOLD + "★ " : "") + ChatColor.YELLOW + ChatColor.BOLD + "Managing " + selectedPets.size() + " Pets";
      gui.setItem(4, this.createActionButton(Material.HOPPER, favoriteDisplayName, "batch_toggle_favorite", null,
              Arrays.asList(
                      ChatColor.GRAY + "Selected: " + ChatColor.WHITE + selectedPets.size() + " pets",
                      "",
                      allFavorites ? ChatColor.YELLOW + "Click to remove all from favorites" : ChatColor.GREEN + "Click to mark all as favorite"
              )
      ));

      
      List<Ageable> babies = selectedPetUUIDs.stream()
              .map(Bukkit::getEntity)
              .filter(e -> e instanceof Ageable)
              .map(e -> (Ageable) e)
              .filter(a -> !a.isAdult())
              .toList();

      if (babies.size() == selectedPetUUIDs.size()) {          
         long pausedCount = selectedPetUUIDs.stream()
                 .map(petManager::getPetData)
                 .filter(java.util.Objects::nonNull)
                 .filter(PetData::isGrowthPaused)
                 .count();
         long totalBabies = selectedPetUUIDs.stream()
                 .map(Bukkit::getEntity)
                 .filter(e -> e instanceof Ageable a && !a.isAdult())
                 .count();

         String status;
         if (pausedCount == totalBabies && totalBabies > 0) {
            status = "All Paused";
         } else if (pausedCount > 0 && pausedCount < totalBabies) {
            status = "Some Paused";
         } else {
            status = "All Growing";
         }

         Material mat = pausedCount == totalBabies ? Material.GOLDEN_CARROT : Material.CARROT;
         gui.setItem(24, createActionButton(
                 mat,
                 (pausedCount == totalBabies ? ChatColor.GREEN : ChatColor.YELLOW) + "Toggle Growth",
                 "batch_toggle_growth_pause",
                 null,
                 List.of(
                         ChatColor.GRAY + "Current: " + status,
                         "",
                         ChatColor.YELLOW + "Click to toggle all"
                 )
         ));
      }


      gui.setItem(11, this.createModeButton(Material.FEATHER, "Set Passive", BehaviorMode.PASSIVE, batchData));
      gui.setItem(13, this.createModeButton(Material.IRON_SWORD, "Set Neutral", BehaviorMode.NEUTRAL, batchData));
      gui.setItem(15, this.createModeButton(Material.DIAMOND_SWORD, "Set Aggressive", BehaviorMode.AGGRESSIVE, batchData));

      gui.setItem(19, this.createActionButton(Material.PLAYER_HEAD, ChatColor.GREEN + "Manage Friendly Players", "batch_manage_friendly", null, Collections.singletonList(ChatColor.GRAY + "Manage common friendly players.")));
      gui.setItem(21, this.createActionButton(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "Teleport Pets to You", "batch_teleport", null, Collections.singletonList(ChatColor.GRAY + "Summons all selected pets.")));
      gui.setItem(22, this.createActionButton(Material.LEAD, ChatColor.GOLD + "Transfer Pets", "batch_open_transfer", null, Arrays.asList(ChatColor.GRAY + "Give selected pets to another player", ChatColor.YELLOW + "⚠ This cannot be undone!")));

      if (allCanSit) {
         String sitStandName = allSitting ? ChatColor.GREEN + "Make Pets Stand" : ChatColor.YELLOW + "Make Pets Sit";
         String sitStandStatus = "Current: " + (allSitting ? "All Sitting" : anySitting ? "Mixed" : "All Standing");
         gui.setItem(23, this.createActionButton(allSitting ? Material.ARMOR_STAND : Material.SADDLE, sitStandName, "batch_toggle_sit", null, Collections.singletonList(ChatColor.GRAY + sitStandStatus)));
      }

      gui.setItem(20, this.createActionButton(Material.MILK_BUCKET, ChatColor.AQUA + "Calm Pets", "batch_calm", null, Collections.singletonList(ChatColor.GRAY + "Clears targets for all selected pets.")));
      gui.setItem(25, this.createActionButton(Material.BARRIER, ChatColor.RED + "Free Selected Pets", "batch_free_pet_prompt", null, Arrays.asList(ChatColor.DARK_RED + "" + ChatColor.BOLD + "WARNING:", ChatColor.RED + "This is permanent!")));

      EntityType type = selectedPets.get(0).getEntityType();
      ItemStack backButton = new ItemStack(Material.ARROW);
      ItemMeta meta = backButton.getItemMeta();
      meta.setDisplayName(ChatColor.YELLOW + "Back to Pet Selection");
      meta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING, "open_pet_select");
      meta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING, type.name());
      backButton.setItemMeta(meta);
      gui.setItem(26, backButton);

      player.openInventory(gui);
   }

   public void openBatchConfirmFreeMenu(Player player, Set<UUID> selectedPetUUIDs) {
      String title = ChatColor.DARK_RED + "Confirm Free: " + selectedPetUUIDs.size() + " Pets";
      Inventory gui = Bukkit.createInventory(player, 27, title);

      gui.setItem(13, this.createItem(
              Material.HOPPER,
              ChatColor.YELLOW + "Free " + selectedPetUUIDs.size() + " Pets",
              Arrays.asList(
                      ChatColor.DARK_RED + "⚠ WARNING ⚠",
                      ChatColor.RED + "This action cannot be undone!",
                      ChatColor.RED + "The selected pets will be permanently freed."
              )
      ));

      gui.setItem(11, this.createActionButton(
              Material.LIME_WOOL,
              ChatColor.GREEN + "Cancel",
              "open_batch_manage", 
              null,
              Collections.singletonList(ChatColor.GRAY + "Keep your pets")
      ));

      gui.setItem(15, this.createActionButton(
              Material.RED_WOOL,
              ChatColor.DARK_RED + "Confirm Free Pets",
              "batch_confirm_free", 
              null,
              Arrays.asList(
                      ChatColor.RED + "Click to permanently free these pets",
                      ChatColor.DARK_RED + "This cannot be undone!"
              )
      ));

      player.openInventory(gui);
   }

   public void openBatchFriendlyPlayerMenu(Player player, Set<UUID> selectedPetUUIDs, int page) {
      String title = ChatColor.DARK_AQUA + "Batch Friendly: " + selectedPetUUIDs.size() + " Pets";
      Inventory gui = Bukkit.createInventory(player, 54, title);

      
      List<PetData> pets = selectedPetUUIDs.stream().map(petManager::getPetData).filter(Objects::nonNull).toList();
      Set<UUID> commonFriendlies = pets.isEmpty() ? new HashSet<>() : new HashSet<>(pets.get(0).getFriendlyPlayers());
      for (int i = 1; i < pets.size(); i++) {
         commonFriendlies.retainAll(pets.get(i).getFriendlyPlayers());
      }
      List<UUID> friendlyList = new ArrayList<>(commonFriendlies);

      
      int itemsPerPage = 45;
      int totalPages = Math.max(1, (int) Math.ceil((double) friendlyList.size() / itemsPerPage));
      page = Math.max(0, Math.min(page, totalPages - 1));
      int startIndex = page * itemsPerPage;
      int endIndex = Math.min(startIndex + itemsPerPage, friendlyList.size());

      
      for (int i = startIndex; i < endIndex; i++) {
         UUID friendlyUUID = friendlyList.get(i);
         OfflinePlayer friendly = Bukkit.getOfflinePlayer(friendlyUUID);
         ItemStack head = this.createPlayerHead(friendlyUUID, ChatColor.YELLOW + friendly.getName(), "remove_batch_friendly", null, Collections.singletonList(ChatColor.RED + "Click to remove from all selected pets"));
         gui.setItem(i - startIndex, head);
      }

      
      if (page > 0) {
         gui.setItem(45, createActionButton(Material.ARROW, ChatColor.GREEN + "Previous Page", "batch_friendly_page", null, null));
      }
      gui.setItem(48, createActionButton(Material.ANVIL, ChatColor.GREEN + "Add Friendly Player", "add_batch_friendly_prompt", null, Collections.singletonList(ChatColor.GRAY + "Adds a player as friendly to ALL selected pets.")));
      if (endIndex < friendlyList.size()) {
         gui.setItem(50, createActionButton(Material.ARROW, ChatColor.GREEN + "Next Page", "batch_friendly_page", null, null));
      }
      gui.setItem(53, createActionButton(Material.ARROW, ChatColor.YELLOW + "Back to Batch Management", "open_batch_manage", null, null));

      player.openInventory(gui);
   }

   public void openBatchTransferMenu(Player player, Set<UUID> selectedPetUUIDs) {
      String title = ChatColor.DARK_AQUA + "Transfer " + selectedPetUUIDs.size() + " Pets";

      List<Player> eligiblePlayers = Bukkit.getOnlinePlayers().stream()
              .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
              .map(p -> (Player) p) 
              .sorted(Comparator.comparing(Player::getName))
              .toList();

      
      int rows = (int) Math.ceil((double) eligiblePlayers.size() / 9.0);
      int invSize = Math.max(18, (rows + 1) * 9); 
      if (eligiblePlayers.isEmpty()) invSize = 27;
      invSize = Math.min(54, invSize); 

      Inventory gui = Bukkit.createInventory(player, invSize, title);

      if (eligiblePlayers.isEmpty()) {
         gui.setItem(invSize == 27 ? 13 : 22, this.createItem(Material.BARRIER, ChatColor.RED + "No Players Online", Collections.singletonList(ChatColor.GRAY + "No one to transfer to!")));
      } else {
         for (int i = 0; i < eligiblePlayers.size(); i++) {
            if (i >= 45) break;
            Player target = eligiblePlayers.get(i);
            ItemStack head = this.createPlayerHead(target.getUniqueId(), ChatColor.YELLOW + target.getName(), "batch_transfer_to_player", null, Arrays.asList(ChatColor.GRAY + "Click to transfer " + selectedPetUUIDs.size() + " pets", ChatColor.GRAY + "to " + target.getName(), "", ChatColor.YELLOW + "⚠ This cannot be undone!"));
            gui.setItem(i, head);
         }
      }
      
      gui.setItem(invSize - 1, this.createActionButton(Material.ARROW, ChatColor.YELLOW + "Back to Batch Management", "open_batch_manage", null, null));
      player.openInventory(gui);
   }

   

   public void openPetMenu(Player player, UUID petUUID) {
      PetData petData = this.petManager.getPetData(petUUID);
      if (petData == null) {
         player.sendMessage(ChatColor.RED + "Error: Could not find data for that pet. It might have been freed or died.");
         this.openMainMenu(player);
      } else {
         String title = PET_MENU_TITLE_PREFIX + ChatColor.AQUA + petData.getDisplayName();
         if (title.length() > 32) {
            title = title.substring(0, 29) + "...";
         }

         Inventory gui = Bukkit.createInventory(player, 27, title);
         boolean isFavorite = petData.isFavorite();
         String favoriteDisplayName = (isFavorite ? ChatColor.GOLD + "★ " : "") + ChatColor.YELLOW + ChatColor.BOLD + petData.getDisplayName();
         // Dead pet: show skeleton skull and only allow revival
         if (petData.isDead()) {
            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "[DEAD] " + petData.getDisplayName());
            meta.setLore(List.of(
               ChatColor.DARK_RED + "This pet is dead!",
               ChatColor.GRAY + "Choose an action below."
            ));
            skull.setItemMeta(meta);
            gui.setItem(13, skull);
            // Revive button
            gui.setItem(11, this.createActionButton(
               Material.NETHER_STAR,
               ChatColor.GREEN + "Revive Pet",
               "confirm_revive_pet",
               petUUID,
               List.of(ChatColor.GRAY + "Revive this pet with a Nether Star.")
            ));
            // Remove button
            gui.setItem(15, this.createActionButton(
               Material.BARRIER,
               ChatColor.RED + "Remove Pet",
               "confirm_remove_pet",
               petUUID,
               List.of(ChatColor.GRAY + "Permanently delete this pet.")
            ));
            player.openInventory(gui);
            return;
         }

         gui.setItem(4, this.createActionButton(
                 this.getPetMaterial(petData.getEntityType()),
                 favoriteDisplayName,
                 "toggle_favorite",
                 petUUID,
                 Arrays.asList(
                         ChatColor.GRAY + "Type: " + ChatColor.WHITE + petData.getEntityType().name(),
                         ChatColor.GRAY + "Mode: " + ChatColor.AQUA + petData.getMode().name(),
                         "",
                         isFavorite ? ChatColor.YELLOW + "Click to remove from favorites" : ChatColor.GREEN + "Click to mark as favorite"
                 )
         ));

         Entity petEnt = Bukkit.getEntity(petUUID);
         if (petEnt instanceof Ageable a && !a.isAdult()) {
            boolean paused = petData.isGrowthPaused();
            gui.setItem(8, createActionButton(
                    paused ? Material.GOLDEN_CARROT : Material.CARROT,
                    (paused ? ChatColor.GREEN : ChatColor.YELLOW) + "Pause Growth",
                    "toggle_growth_pause",
                    petUUID,
                    List.of(
                            ChatColor.GRAY + "Current: " + (paused ? "Paused" : "Growing"),
                            "",
                            ChatColor.YELLOW + "Click to toggle"
                    )
            ));
         }

         gui.setItem(11, this.createModeButton(Material.FEATHER, "Set Passive", BehaviorMode.PASSIVE, petData));
         gui.setItem(13, this.createModeButton(Material.IRON_SWORD, "Set Neutral", BehaviorMode.NEUTRAL, petData));
         gui.setItem(15, this.createModeButton(Material.DIAMOND_SWORD, "Set Aggressive", BehaviorMode.AGGRESSIVE, petData));
         gui.setItem(
                 19,
                 this.createActionButton(
                         Material.PLAYER_HEAD,
                         ChatColor.GREEN + "Manage Friendly Players",
                         "manage_friendly",
                         petData.getPetUUID(),
                         Collections.singletonList(ChatColor.GRAY + "Set players this pet will never attack.")
                 )
         );
         gui.setItem(
                 21,
                 this.createActionButton(
                         Material.ENDER_PEARL,
                         ChatColor.LIGHT_PURPLE + "Teleport Pet to You",
                         "teleport_pet",
                         petData.getPetUUID(),
                         Collections.singletonList(ChatColor.GRAY + "Summons this pet to your location.")
                 )
         );

         gui.setItem(22, this.createActionButton(
                 Material.LEAD,
                 ChatColor.GOLD + "Transfer Pet",
                 "open_transfer",
                 petData.getPetUUID(),
                 Arrays.asList(
                         ChatColor.GRAY + "Give this pet to another player",
                         ChatColor.YELLOW + "⚠ This cannot be undone!"
                 )
         ));

         Entity petEntity = Bukkit.getEntity(petUUID);
         boolean canSit = petEntity instanceof Sittable;

         if (canSit) {
            boolean isSitting = false;
            if (petEntity instanceof Sittable s) {
               isSitting = s.isSitting();
            }

            gui.setItem(23, this.createActionButton(
                    isSitting ? Material.ARMOR_STAND : Material.SADDLE,
                    isSitting ? ChatColor.GREEN + "Make Pet Stand" : ChatColor.YELLOW + "Make Pet Sit",
                    "toggle_sit",
                    petData.getPetUUID(),
                    Collections.singletonList(ChatColor.GRAY + "Current: " + (isSitting ? "Sitting" : "Standing"))
            ));
         }

         gui.setItem(20, this.createActionButton(
                 Material.MILK_BUCKET,
                 ChatColor.AQUA + "Calm Pet",
                 "calm_pet",
                 petData.getPetUUID(),
                 Arrays.asList(
                         ChatColor.GRAY + "Clears the pet's current target",
                         ChatColor.GRAY + "and resets any anger"
                 )
         ));

         gui.setItem(24, this.createActionButton(
                 Material.ANVIL,
                 ChatColor.GREEN + "Rename Pet",
                 "rename_pet_prompt",
                 petData.getPetUUID(),
                 Collections.singletonList(ChatColor.GRAY + "Change your pet's name via chat.")
         ));

         gui.setItem(
                 25,
                 this.createActionButton(
                         Material.BARRIER,
                         ChatColor.RED + "Free This Pet",
                         "free_pet",
                         petData.getPetUUID(),
                         Arrays.asList("" + ChatColor.DARK_RED + ChatColor.BOLD + "WARNING:", ChatColor.RED + "This is permanent!")
                 )
         );
         gui.setItem(26, this.createActionButton(Material.ARROW, ChatColor.YELLOW + "Back to Pet List", "back_to_main", null, null));

         player.openInventory(gui);
      }
   }

   public static Integer extractPetIdFromName(String name) {
      int lastHashIndex = name.lastIndexOf(" #");
      
      if (lastHashIndex != -1 && lastHashIndex < name.length() - 2) {
         String numberPart = name.substring(lastHashIndex + 2);
         try {
            return Integer.parseInt(numberPart);
         } catch (NumberFormatException e) {
            
            return null;
         }
      }
      
      return null;
   }

   public void openConfirmFreeMenu(Player player, UUID petUUID) {
      PetData petData = this.petManager.getPetData(petUUID);
      if (petData == null) {
         player.sendMessage(ChatColor.RED + "Error: Could not find data for that pet.");
         this.openMainMenu(player);
         return;
      }

      String title = ChatColor.DARK_RED + "Confirm Free: " + ChatColor.RED + petData.getDisplayName();
      if (title.length() > 32) {
         title = title.substring(0, 29) + "...";
      }

      Inventory gui = Bukkit.createInventory(player, 27, title);

      gui.setItem(13, this.createItem(
              this.getPetMaterial(petData.getEntityType()),
              ChatColor.YELLOW + petData.getDisplayName(),
              Arrays.asList(
                      ChatColor.GRAY + "Type: " + ChatColor.WHITE + petData.getEntityType().name(),
                      "",
                      ChatColor.DARK_RED + "⚠ WARNING ⚠",
                      ChatColor.RED + "This action cannot be undone!",
                      ChatColor.RED + "The pet will be permanently freed."
              ),
              petUUID
      ));

      gui.setItem(11, this.createActionButton(
              Material.LIME_WOOL,
              ChatColor.GREEN + "Cancel",
              "cancel_free",
              petUUID,
              Collections.singletonList(ChatColor.GRAY + "Keep your pet")
      ));

      gui.setItem(15, this.createActionButton(
              Material.RED_WOOL,
              ChatColor.DARK_RED + "Confirm Free Pet",
              "confirm_free",
              petUUID,
              Arrays.asList(
                      ChatColor.RED + "Click to permanently free this pet",
                      ChatColor.DARK_RED + "This cannot be undone!"
              )
      ));

      player.openInventory(gui);
   }

   public void openTransferMenu(Player player, UUID petUUID) {
      PetData petData = this.petManager.getPetData(petUUID);
      if (petData == null) {
         player.sendMessage(ChatColor.RED + "Error: Could not find data for that pet.");
         this.openMainMenu(player);
         return;
      }

      String title = ChatColor.DARK_AQUA + "Transfer: " + ChatColor.AQUA + petData.getDisplayName();
      if (title.length() > 32) {
         title = title.substring(0, 29) + "...";
      }

      List<Player> eligiblePlayers = Bukkit.getOnlinePlayers().stream()
              .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
              .sorted(Comparator.comparing(Player::getName))
              .collect(Collectors.toList());

      
      int rows = (int) Math.ceil((double) eligiblePlayers.size() / 9.0);
      int invSize = Math.max(18, (rows + 1) * 9); 
      if (eligiblePlayers.isEmpty()) invSize = 27;
      invSize = Math.min(54, invSize); 

      Inventory gui = Bukkit.createInventory(player, invSize, title);

      if (eligiblePlayers.isEmpty()) {
         gui.setItem(invSize == 27 ? 13 : 22, this.createItem(
                 Material.BARRIER,
                 ChatColor.RED + "No Players Online",
                 Collections.singletonList(ChatColor.GRAY + "No one to transfer to!")
         ));
      } else {
         for (int i = 0; i < eligiblePlayers.size(); i++) {
            if (i >= 45) break; 
            Player target = eligiblePlayers.get(i);
            ItemStack head = this.createPlayerHead(target.getUniqueId(), ChatColor.YELLOW + target.getName(), "transfer_to_player", petUUID,
                    Arrays.asList(ChatColor.GRAY + "Click to transfer " + petData.getDisplayName(), ChatColor.GRAY + "to " + target.getName(), "", ChatColor.YELLOW + "⚠ This cannot be undone!"));
            gui.setItem(i, head);
         }
      }

      
      gui.setItem(invSize - 1, this.createActionButton(Material.ARROW, ChatColor.YELLOW + "Back to Pet Management", "back_to_pet", petUUID, null));
      player.openInventory(gui);
   }

   public void openFriendlyPlayerMenu(Player player, UUID petUUID, int page) {
      PetData petData = this.petManager.getPetData(petUUID);
      if (petData == null) {
         player.sendMessage(ChatColor.RED + "Error: Could not find data for that pet.");
         this.openMainMenu(player);
         return;
      }

      String title = FRIENDLY_MENU_TITLE_PREFIX + ChatColor.AQUA + petData.getDisplayName();
      if (title.length() > 32) {
         title = title.substring(0, 29) + "...";
      }

      List<UUID> friendlyList = new ArrayList<>(petData.getFriendlyPlayers());

      int itemsPerPage = 45;
      int totalPages = Math.max(1, (int) Math.ceil(friendlyList.size() / (double) itemsPerPage));
      page = Math.max(0, Math.min(page, totalPages - 1));

      int startIndex = page * itemsPerPage;
      int endIndex = Math.min(startIndex + itemsPerPage, friendlyList.size());

      Inventory gui = Bukkit.createInventory(player, 54, title);

      int slot = 0;
      for (int i = startIndex; i < endIndex; i++) {
         UUID friendlyUUID = friendlyList.get(i);
         OfflinePlayer friendly = Bukkit.getOfflinePlayer(friendlyUUID);
         String name = friendly.getName() != null ? friendly.getName() : friendlyUUID.toString().substring(0, 8);
         ItemStack head = this.createPlayerHead(
                 friendlyUUID,
                 ChatColor.YELLOW + name,
                 "remove_friendly",
                 petUUID,
                 Collections.singletonList(ChatColor.RED + "Click to remove from friendly list")
         );
         gui.setItem(slot++, head);
      }

      if (page > 0) {
         gui.setItem(45, this.createFriendlyNavButton(Material.ARROW, ChatColor.GREEN + "Previous Page", "friendly_page", petUUID, page - 1));
      }

      gui.setItem(48, this.createActionButton(Material.ANVIL, ChatColor.GREEN + "Add Friendly Player", "add_friendly_prompt", petUUID, Collections.singletonList(ChatColor.GRAY + "Click to type a player name in chat.")));

      if (endIndex < friendlyList.size()) {
         gui.setItem(50, this.createFriendlyNavButton(Material.ARROW, ChatColor.GREEN + "Next Page", "friendly_page", petUUID, page + 1));
      }

      gui.setItem(53, this.createActionButton(Material.ARROW, ChatColor.YELLOW + "Back to Pet Management", "back_to_pet", petUUID, null));

      player.openInventory(gui);
   }

   private ItemStack createNavigationButton(Material material, String name, String action, int targetPage, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      if (lore != null && !lore.isEmpty()) meta.setLore(lore);
      meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
      meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, targetPage);
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createFriendlyNavButton(Material material, String name, String action, UUID petUUID, int targetPage) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
      meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
      meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, targetPage);
      item.setItemMeta(meta);
      return item;
   }

   public ItemStack createActionButton(Material material, String name, String action, UUID petUUID, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      if (lore != null && !lore.isEmpty()) meta.setLore(lore);
      meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
      if (petUUID != null) {
         meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
      }
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createPetItem(PetData petData) {
      Entity petEntity = Bukkit.getEntity(petData.getPetUUID());
      if (petData.isDead()) {
         ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
         SkullMeta meta = (SkullMeta) skull.getItemMeta();
         meta.setDisplayName(ChatColor.RED + "[DEAD] " + petData.getDisplayName());
         meta.setLore(List.of(
            ChatColor.DARK_RED + "This pet is dead!",
            ChatColor.GRAY + "Click to view revival or removal options."
         ));
         meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petData.getPetUUID().toString());
         skull.setItemMeta(meta);
         return skull;
      }
      Material mat = this.getPetMaterial(petData.getEntityType());
      ItemStack item = new ItemStack(mat);
      ItemMeta meta = item.getItemMeta();
      String displayName = (petData.isFavorite() ? ChatColor.GOLD + "★ " : "") + ChatColor.AQUA + petData.getDisplayName();
      meta.setDisplayName(displayName);
      List<String> lore = new java.util.ArrayList<>();
      lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + petData.getEntityType().name());
      lore.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + petData.getMode().name());
      int friendlyCount = petData.getFriendlyPlayers().size();
      if (friendlyCount > 0) {
         lore.add("" + ChatColor.GREEN + friendlyCount + " Friendly Player" + (friendlyCount == 1 ? "" : "s"));
      }
      // Add baby label if pet is a baby
      if (petEntity instanceof Ageable ageable && !ageable.isAdult()) {
         lore.add(ChatColor.LIGHT_PURPLE + "Baby");
      }
      lore.add("");
      lore.add(ChatColor.YELLOW + "Click to manage this pet.");
      meta.setLore(lore);
      meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petData.getPetUUID().toString());
      item.setItemMeta(meta);
      return item;
   }

   public ItemStack createModeButton(Material material, String name, BehaviorMode mode, PetData currentPetData) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         boolean isActive = currentPetData.getMode() == mode;
         meta.setDisplayName((isActive ? "" + ChatColor.GREEN + ChatColor.BOLD : currentPetData.getMode() == BehaviorMode.BATCH ? ChatColor.AQUA : ChatColor.YELLOW) + name);

         List<String> lore = new ArrayList<>();
         switch (mode) {
            case PASSIVE: lore.add(ChatColor.GRAY + "Pet will not attack any targets."); break;
            case NEUTRAL: lore.add(ChatColor.GRAY + "Pet defends owner and attacks"); lore.add(ChatColor.GRAY + "owner's targets (vanilla)."); break;
            case AGGRESSIVE: lore.add(ChatColor.GRAY + "Pet attacks nearby hostiles"); lore.add(ChatColor.GRAY + "proactively (if possible)."); break;
            default: break;
         }

         if (isActive) {
            lore.add("");
            lore.add(ChatColor.DARK_GREEN + "▶ " + ChatColor.GREEN + "Currently Active");
         } else if (currentPetData.getMode() == BehaviorMode.BATCH) {
            lore.add("");
            lore.add(ChatColor.AQUA + "Selection has mixed modes.");
            lore.add(ChatColor.YELLOW + "Click to set all to this mode.");
         } else {
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to activate this mode.");
         }

         meta.setLore(lore);
         meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

         String action;
         if (currentPetData.getPetUUID() == null) {
            action = "batch_set_mode_" + mode.name();
         } else {
            action = "set_mode_" + mode.name();
         }
         meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);

         if (currentPetData.getPetUUID() != null) {
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, currentPetData.getPetUUID().toString());
         }
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createPlayerHead(UUID playerUUID, String name, String action, UUID petContextUUID, List<String> lore) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)item.getItemMeta();
      if (meta != null) {
         OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerUUID);
         meta.setOwningPlayer(targetPlayer);
         meta.setDisplayName(name);
         if (lore != null && !lore.isEmpty()) meta.setLore(lore);
         meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
         if (petContextUUID != null) { 
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petContextUUID.toString());
         }
         meta.getPersistentDataContainer().set(TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING, playerUUID.toString());
         item.setItemMeta(meta);
      }
      return item;
   }

   public ItemStack createItem(Material material, String name, List<String> lore) {
      return this.createItem(material, name, lore, null);
   }

   public ItemStack createItem(Material material, String name, List<String> lore, UUID petUUIDContext) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         if (lore != null && !lore.isEmpty()) meta.setLore(lore);
         if (petUUIDContext != null) {
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUIDContext.toString());
         }
         meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
         item.setItemMeta(meta);
      }
      return item;
   }

   public Material getPetMaterial(EntityType type) {
      return switch (type) {
         case WOLF -> Material.WOLF_SPAWN_EGG;
         case CAT -> Material.CAT_SPAWN_EGG;
         case PARROT -> Material.PARROT_SPAWN_EGG;
         default -> Material.NAME_TAG;
      };
   }
}
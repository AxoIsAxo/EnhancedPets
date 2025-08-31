package dev.asteriamc.enhancedpets.gui;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class PetManagerGUI {
    public static final NamespacedKey PET_UUID_KEY = new NamespacedKey(Enhancedpets.getInstance(), "pet_uuid");
    public static final NamespacedKey ACTION_KEY = new NamespacedKey(Enhancedpets.getInstance(), "gui_action");
    public static final NamespacedKey TARGET_PLAYER_UUID_KEY = new NamespacedKey(Enhancedpets.getInstance(), "target_player_uuid");
    public static final String MAIN_MENU_TITLE = ChatColor.DARK_AQUA + "Your Enhanced Pets";
    public static final String PET_MENU_TITLE_PREFIX = ChatColor.DARK_AQUA + "Manage Pet: ";
    public static final String FRIENDLY_MENU_TITLE_PREFIX = ChatColor.DARK_AQUA + "Friendly Players: ";
    public static final NamespacedKey PAGE_KEY = new NamespacedKey(Enhancedpets.getInstance(), "gui_page");
    
    public static final NamespacedKey COLOR_KEY = new NamespacedKey(Enhancedpets.getInstance(), "display_color");

    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final BatchActionsGUI batchActionsGUI;
    private final Map<UUID, UUID> viewerOwnerOverride = new java.util.concurrent.ConcurrentHashMap<>();

    public PetManagerGUI(Enhancedpets plugin) {
        this.plugin = plugin;
        this.petManager = plugin.getPetManager();
        this.batchActionsGUI = new BatchActionsGUI(plugin, this);
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

    public BatchActionsGUI getBatchActionsGUI() {
        return this.batchActionsGUI;
    }

    public void openMainMenu(Player player) {
        openMainMenu(player, 0);
    }

    public void setViewerOwnerOverride(UUID viewer, UUID owner) { 
        if (viewer != null && owner != null) viewerOwnerOverride.put(viewer, owner);
    }
    public void clearViewerOwnerOverride(UUID viewer) { 
        if (viewer != null) viewerOwnerOverride.remove(viewer);
    }
    public UUID getViewerOwnerOverride(UUID viewer) { 
        return viewerOwnerOverride.get(viewer);
    }
    public UUID getEffectiveOwner(Player viewer) { 
        UUID o = getViewerOwnerOverride(viewer.getUniqueId());
        return o != null ? o : viewer.getUniqueId();
    }


    public void openMainMenu(Player player, int page) {
        UUID effectiveOwner = getEffectiveOwner(player); 
        List<PetData> pets = this.petManager.getPetsOwnedBy(effectiveOwner);

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

                if (effectiveOwner.equals(player.getUniqueId())) {
                    gui.setItem(invSize - 8, createActionButton(Material.COMPASS, ChatColor.AQUA + "Scan for My Pets", "scan_for_pets", null,
                            Arrays.asList(ChatColor.GRAY + "Scans loaded areas for your unregistered pets.", "", ChatColor.YELLOW + "Click to scan and sync.")));
                }
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
            if (effectiveOwner.equals(player.getUniqueId())) {
                gui.setItem(invSize - 8, createActionButton(Material.COMPASS, ChatColor.AQUA + "Scan for My Pets", "scan_for_pets", null,
                        Arrays.asList(ChatColor.GRAY + "Scans loaded areas for your unregistered pets.", "", ChatColor.YELLOW + "Click to scan and sync.")));
            }
            gui.setItem(invSize - 5, this.createItem(Material.PAPER, ChatColor.AQUA + "Page " + (page + 1) + " / " + totalPages,
                    Collections.singletonList(ChatColor.GRAY + "Total Pets: " + totalPets)));
            gui.setItem(invSize - 2, createActionButton(Material.HOPPER, ChatColor.GOLD + "Batch Actions", "batch_actions", null,
                    Collections.singletonList(ChatColor.GRAY + "Manage multiple pets at once.")));

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
        Inventory gui = Bukkit.createInventory(player, 54, title);

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

        long protectedCount = selectedPets.stream().filter(PetData::isProtectedFromPlayers).count();
        boolean allProtected = protectedCount == selectedPets.size();
        boolean anyProtected = protectedCount > 0;

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

        List<UUID> babyUUIDs = selectedPetUUIDs.stream()
                .map(Bukkit::getEntity)
                .filter(e -> e instanceof Ageable a && !a.isAdult())
                .map(Entity::getUniqueId)
                .toList();

        if (!babyUUIDs.isEmpty()) {
            long pausedCount = babyUUIDs.stream()
                    .map(petManager::getPetData)
                    .filter(Objects::nonNull)
                    .filter(PetData::isGrowthPaused)
                    .count();
            int totalBabies = babyUUIDs.size();

            String status;
            if (pausedCount == totalBabies) {
                status = "All Paused";
            } else if (pausedCount > 0) {
                status = "Some Paused";
            } else {
                status = "All Growing";
            }

            Material mat = pausedCount == totalBabies ? Material.GOLDEN_CARROT : Material.CARROT;
            gui.setItem(26, createActionButton(
                    mat,
                    (pausedCount == totalBabies ? ChatColor.GREEN : ChatColor.YELLOW) + "Toggle Growth (Babies)",
                    "batch_toggle_growth_pause",
                    null,
                    List.of(
                            ChatColor.GRAY + "Current: " + status,
                            ChatColor.GRAY + "Affects selected babies only.",
                            "",
                            ChatColor.YELLOW + "Click to toggle all babies"
                    )
            ));
        }

        gui.setItem(11, this.createModeButton(Material.FEATHER, "Set Passive", BehaviorMode.PASSIVE, batchData));
        gui.setItem(13, this.createModeButton(Material.IRON_SWORD, "Set Neutral", BehaviorMode.NEUTRAL, batchData));
        gui.setItem(15, this.createModeButton(Material.DIAMOND_SWORD, "Set Aggressive", BehaviorMode.AGGRESSIVE, batchData));

        if (allCanSit) {
            String sitStandName = allSitting ? ChatColor.GREEN + "Make Pets Stand" : ChatColor.YELLOW + "Make Pets Sit";
            String sitStandStatus = "Current: " + (allSitting ? "All Sitting" : anySitting ? "Mixed" : "All Standing");
            gui.setItem(20, this.createActionButton(allSitting ? Material.ARMOR_STAND : Material.SADDLE, sitStandName, "batch_toggle_sit", null, Collections.singletonList(ChatColor.GRAY + sitStandStatus)));
        }

        gui.setItem(22, this.createActionButton(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "Teleport Pets to You", "batch_teleport", null, Collections.singletonList(ChatColor.GRAY + "Summons all selected pets.")));
        gui.setItem(24, this.createActionButton(Material.MILK_BUCKET, ChatColor.AQUA + "Calm Pets", "batch_calm", null, Collections.singletonList(ChatColor.GRAY + "Clears targets for all selected pets.")));

        String protName = allProtected
                ? ChatColor.YELLOW + "Disable Mutual Non-Aggression"
                : ChatColor.GREEN + "Enable Mutual Non-Aggression";
        String protStatus = "Current: " + (allProtected ? "All Protected" : anyProtected ? "Mixed" : "All Unprotected");
        gui.setItem(28, this.createActionButton(
                Material.SHIELD,
                protName,
                "batch_toggle_protection",
                null,
                Arrays.asList(ChatColor.GRAY + protStatus, "", ChatColor.GRAY + "Players cannot damage these pets,", ChatColor.GRAY + "and they will not attack players.")
        ));

        gui.setItem(30, this.createActionButton(Material.PLAYER_HEAD, ChatColor.GREEN + "Manage Friendly Players", "batch_manage_friendly", null, Collections.singletonList(ChatColor.GRAY + "Manage common friendly players.")));
        gui.setItem(32, this.createActionButton(Material.LEAD, ChatColor.GOLD + "Transfer Pets", "batch_open_transfer", null, Arrays.asList(ChatColor.GRAY + "Give selected pets to another player", ChatColor.YELLOW + "⚠ This cannot be undone!")));

        EntityType type = selectedPets.get(0).getEntityType();
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta meta = backButton.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Back to Pet Selection");
        meta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING, "open_pet_select");
        meta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING, type.name());
        backButton.setItemMeta(meta);
        gui.setItem(45, backButton);

        gui.setItem(53, this.createActionButton(Material.BARRIER, ChatColor.RED + "Free Selected Pets", "batch_free_pet_prompt", null, Arrays.asList(ChatColor.DARK_RED + "" + ChatColor.BOLD + "WARNING:", ChatColor.RED + "This is permanent!")));

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
        Set<UUID> commonFriendlies = pets.isEmpty() ? new HashSet<>()
                : new HashSet<>(pets.get(0).getFriendlyPlayers());
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
                .filter(p -> !p.getUniqueId().equals(getEffectiveOwner(player)))
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
            return;
        }

        String title = PET_MENU_TITLE_PREFIX + ChatColor.AQUA + petData.getDisplayName();
        if (title.length() > 32) {
            title = title.substring(0, 29) + "...";
        }

        Inventory gui = Bukkit.createInventory(player, 54, title);
        boolean isFavorite = petData.isFavorite();
        boolean protectionEnabled = petData.isProtectedFromPlayers();
        ChatColor nameColor = getNameColor(petData);
        Material headerIcon = getDisplayMaterialForPet(petData);

        if (petData.isDead()) {
            gui = Bukkit.createInventory(player, 27, title);
            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "[DEAD] " + petData.getDisplayName());
            meta.setLore(List.of(
                    ChatColor.DARK_RED + "This pet is dead!",
                    ChatColor.GRAY + "Choose an action below."
            ));
            skull.setItemMeta(meta);
            gui.setItem(13, skull);
            gui.setItem(11, this.createActionButton(
                    Material.NETHER_STAR,
                    ChatColor.GREEN + "Revive Pet",
                    "confirm_revive_pet",
                    petUUID,
                    List.of(ChatColor.GRAY + "Revive this pet with a Nether Star.")
            ));
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

        Entity petEntity = Bukkit.getEntity(petUUID);

        List<String> headerLore = new ArrayList<>();
        headerLore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + petData.getEntityType().name());
        headerLore.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + petData.getMode().name());
        if (petEntity instanceof LivingEntity livingEntity && petEntity.isValid()) {
            double health = livingEntity.getHealth();
            double maxHealth = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            headerLore.add(ChatColor.RED + "Health: " + ChatColor.WHITE + String.format("%.1f", health) + " / " + String.format("%.1f", maxHealth));
        } else {
            headerLore.add(ChatColor.GRAY + "Health: Unknown (Unloaded)");
        }
        headerLore.add(ChatColor.GRAY + "Protection: " + (protectionEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        int friendlyCount = petData.getFriendlyPlayers().size();
        if (friendlyCount > 0) {
            headerLore.add("" + ChatColor.GREEN + friendlyCount + " Friendly Player" + (friendlyCount == 1 ? "" : "s"));
        }
        if (petEntity instanceof Ageable ageable && !ageable.isAdult()) {
            headerLore.add(ChatColor.LIGHT_PURPLE + "Baby");
        }
        headerLore.add("");
        headerLore.add(ChatColor.YELLOW + "Left-click: Customize Display"); 
        headerLore.add(ChatColor.YELLOW + "Right-click: Toggle Favorite");

        
        ItemStack header = new ItemStack(headerIcon);
        ItemMeta hMeta = header.getItemMeta();
        String favoriteStar = isFavorite ? ChatColor.GOLD + "★ " : "";
        hMeta.setDisplayName(favoriteStar + nameColor + petData.getDisplayName());
        hMeta.setLore(headerLore);
        hMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "pet_header");
        hMeta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
        hMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        header.setItemMeta(hMeta);
        gui.setItem(4, header);

        
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

        boolean canSit = petEntity instanceof Sittable;
        if (canSit) {
            boolean isSitting = false;
            if (petEntity instanceof Sittable s) {
                isSitting = s.isSitting();
            }
            gui.setItem(20, this.createActionButton(
                    isSitting ? Material.ARMOR_STAND : Material.SADDLE,
                    isSitting ? ChatColor.GREEN + "Make Pet Stand" : ChatColor.YELLOW + "Make Pet Sit",
                    "toggle_sit",
                    petData.getPetUUID(),
                    Collections.singletonList(ChatColor.GRAY + "Current: " + (isSitting ? "Sitting" : "Standing"))
            ));
        }

        gui.setItem(22, this.createActionButton(
                Material.ENDER_PEARL,
                ChatColor.LIGHT_PURPLE + "Teleport Pet to You",
                "teleport_pet",
                petData.getPetUUID(),
                Collections.singletonList(ChatColor.GRAY + "Summons this pet to your location.")
        ));

        gui.setItem(24, this.createActionButton(
                Material.MILK_BUCKET,
                ChatColor.AQUA + "Calm Pet",
                "calm_pet",
                petData.getPetUUID(),
                Arrays.asList(
                        ChatColor.GRAY + "Clears the pet's current target",
                        ChatColor.GRAY + "and resets any anger"
                )
        ));

        gui.setItem(29, this.createActionButton(
                Material.ANVIL,
                ChatColor.GREEN + "Rename Pet",
                "rename_pet_prompt",
                petData.getPetUUID(),
                Arrays.asList(
                        ChatColor.GRAY + "Left-click: Change your pet's name via chat.",
                        ChatColor.GRAY + "Shift-click: Reset to default name."
                )
        ));

        gui.setItem(31, this.createActionButton(
                Material.PLAYER_HEAD,
                ChatColor.GREEN + "Manage Friendly Players",
                "manage_friendly",
                petData.getPetUUID(),
                Collections.singletonList(ChatColor.GRAY + "Set players this pet will never attack.")
        ));

        gui.setItem(33, this.createActionButton(
                Material.LEAD,
                ChatColor.GOLD + "Transfer Pet",
                "open_transfer",
                petData.getPetUUID(),
                Arrays.asList(
                        ChatColor.GRAY + "Give this pet to another player",
                        ChatColor.YELLOW + "⚠ This cannot be undone!"
                )
        ));

        String protName = protectionEnabled
                ? ChatColor.YELLOW + "Disable Mutual Non-Aggression"
                : ChatColor.GREEN + "Enable Mutual Non-Aggression";
        List<String> protLore = new ArrayList<>();
        protLore.add(ChatColor.GRAY + "Current: " + (protectionEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        protLore.add("");
        protLore.add(ChatColor.GRAY + "Players cannot damage this pet,");
        protLore.add(ChatColor.GRAY + "and this pet will not attack players.");
        gui.setItem(35, this.createActionButton(
                Material.SHIELD,
                protName,
                "toggle_mutual_protection",
                petData.getPetUUID(),
                protLore
        ));

        


        gui.setItem(49, this.createActionButton(Material.ARROW, ChatColor.YELLOW + "Back to Pet List", "back_to_main", null, null));

        gui.setItem(53, this.createActionButton(
                Material.BARRIER,
                ChatColor.RED + "Free This Pet",
                "confirm_free_pet_prompt",
                petData.getPetUUID(),
                Arrays.asList("" + ChatColor.DARK_RED + ChatColor.BOLD + "WARNING:", ChatColor.RED + "This is permanent!")
        ));

        player.openInventory(gui);
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
                .filter(p -> !p.getUniqueId().equals(getEffectiveOwner(player)))
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


    
    public void openColorPicker(Player player, UUID petUUID) {
        PetData petData = petManager.getPetData(petUUID);
        if (petData == null) {
            player.sendMessage(ChatColor.RED + "Error: Pet not found.");
            openMainMenu(player);
            return;
        }
        String title = ChatColor.DARK_AQUA + "Pick Color: " + petData.getDisplayName();
        if (title.length() > 32) title = ChatColor.DARK_AQUA + "Pick Color";

        Inventory gui = Bukkit.createInventory(player, 27, title);

        
        LinkedHashMap<Material, ChatColor> choices = new LinkedHashMap<>();
        choices.put(Material.WHITE_DYE, ChatColor.WHITE);
        choices.put(Material.ORANGE_DYE, ChatColor.GOLD);
        choices.put(Material.MAGENTA_DYE, ChatColor.LIGHT_PURPLE);
        choices.put(Material.LIGHT_BLUE_DYE, ChatColor.AQUA);
        choices.put(Material.YELLOW_DYE, ChatColor.YELLOW);
        choices.put(Material.LIME_DYE, ChatColor.GREEN);
        choices.put(Material.PINK_DYE, ChatColor.LIGHT_PURPLE);
        choices.put(Material.GRAY_DYE, ChatColor.GRAY);
        choices.put(Material.LIGHT_GRAY_DYE, ChatColor.WHITE);
        choices.put(Material.CYAN_DYE, ChatColor.DARK_AQUA);
        choices.put(Material.PURPLE_DYE, ChatColor.DARK_PURPLE);
        choices.put(Material.BLUE_DYE, ChatColor.BLUE);
        choices.put(Material.BROWN_DYE, ChatColor.GOLD);
        choices.put(Material.GREEN_DYE, ChatColor.DARK_GREEN);
        choices.put(Material.RED_DYE, ChatColor.RED);
        choices.put(Material.BLACK_DYE, ChatColor.BLACK);

        int i = 0;
        for (Map.Entry<Material, ChatColor> entry : choices.entrySet()) {
            if (i >= 26) break; 
            Material mat = entry.getKey();
            ChatColor color = entry.getValue();
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color + color.name());
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to set this color."));
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "choose_color");
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
            meta.getPersistentDataContainer().set(COLOR_KEY, PersistentDataType.STRING, color.name());
            item.setItemMeta(meta);
            gui.setItem(i++, item);
        }

        
        gui.setItem(26, createActionButton(Material.ARROW, ChatColor.YELLOW + "Back", "back_to_pet", petUUID, null));

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

    public void openBatchConfirmRemoveDeadMenu(Player player, EntityType petType, int deadPetCount) {
        String typeName = petType.name().toLowerCase().replace('_', ' ');
        String title = ChatColor.DARK_RED + "Confirm Remove: Dead " + typeName + "s";
        Inventory gui = Bukkit.createInventory(player, 27, title);

        ItemStack info = createItem(
                Material.SKELETON_SKULL,
                ChatColor.YELLOW + "Remove " + deadPetCount + " Dead Pet Record(s)",
                Arrays.asList(
                        ChatColor.GRAY + "Pet Type: " + ChatColor.WHITE + typeName,
                        "",
                        ChatColor.DARK_RED + "⚠ WARNING ⚠",
                        ChatColor.RED + "This action is permanent and cannot be undone."
                )
        );
        gui.setItem(13, info);

        ItemStack cancel = new ItemStack(Material.LIME_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.GREEN + "Cancel");
        cancelMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Keep the pet records."));
        cancelMeta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING, "open_pet_select");
        cancelMeta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING, petType.name());
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);

        ItemStack confirm = new ItemStack(Material.RED_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.DARK_RED + "Confirm Removal");
        confirmMeta.setLore(Collections.singletonList(ChatColor.RED + "Click to permanently remove these records."));
        confirmMeta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING, "batch_confirm_remove_dead");
        confirmMeta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING, petType.name());
        confirm.setItemMeta(confirmMeta);
        gui.setItem(11, confirm);

        player.openInventory(gui);
    }

    public ItemStack createPetItem(PetData petData) {
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

        Material mat = getDisplayMaterialForPet(petData);
        ChatColor nameColor = getNameColor(petData);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String displayName = (petData.isFavorite() ? ChatColor.GOLD + "★ " : "") + nameColor + petData.getDisplayName();
        meta.setDisplayName(displayName);
        List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + petData.getEntityType().name());
        lore.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + petData.getMode().name());

        if (petEntity instanceof LivingEntity livingEntity && petEntity.isValid()) {
            double health = livingEntity.getHealth();
            double maxHealth = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            lore.add(ChatColor.RED + "Health: " + ChatColor.WHITE + String.format("%.1f", health) + " / " + String.format("%.1f", maxHealth));
        } else {
            lore.add(ChatColor.GRAY + "Health: Unknown (Unloaded)");
        }

        lore.add(ChatColor.GRAY + "Protection: " + (petData.isProtectedFromPlayers() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        int friendlyCount = petData.getFriendlyPlayers().size();
        if (friendlyCount > 0) {
            lore.add("" + ChatColor.GREEN + friendlyCount + " Friendly Player" + (friendlyCount == 1 ? "" : "s"));
        }
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
                case PASSIVE -> lore.add(ChatColor.GRAY + "Pet will not attack any targets.");
                case NEUTRAL -> {
                    lore.add(ChatColor.GRAY + "Pet defends owner and attacks");
                    lore.add(ChatColor.GRAY + "owner's targets (vanilla).");
                }
                case AGGRESSIVE -> {
                    lore.add(ChatColor.GRAY + "Pet attacks nearby hostiles");
                    lore.add(ChatColor.GRAY + "proactively (if possible).");
                }
                default -> {
                }
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
        SkullMeta meta = (SkullMeta) item.getItemMeta();
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

    
    private ChatColor getNameColor(PetData data) {
        String c = data.getDisplayColor();
        if (c == null || c.isEmpty()) return ChatColor.AQUA;
        try {
            return ChatColor.valueOf(c);
        } catch (IllegalArgumentException ex) {
            return ChatColor.AQUA;
        }
    }

    
    private Material getDisplayMaterialForPet(PetData data) {
        if (data.getCustomIconMaterial() != null) {
            try {
                return Material.valueOf(data.getCustomIconMaterial());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return getPetMaterial(data.getEntityType());
    }

    public Material getPetMaterial(EntityType type) {
        return switch (type) {
            case WOLF -> Material.WOLF_SPAWN_EGG;
            case CAT -> Material.CAT_SPAWN_EGG;
            case PARROT -> Material.PARROT_SPAWN_EGG;
            
            case HORSE -> Material.HORSE_SPAWN_EGG;
            case DONKEY -> Material.DONKEY_SPAWN_EGG;
            case MULE -> Material.MULE_SPAWN_EGG;
            case LLAMA -> Material.LLAMA_SPAWN_EGG;
            case TRADER_LLAMA -> Material.TRADER_LLAMA_SPAWN_EGG;
            case SKELETON_HORSE -> Material.SKELETON_HORSE_SPAWN_EGG;
            case ZOMBIE_HORSE -> Material.ZOMBIE_HORSE_SPAWN_EGG;
            default -> Material.NAME_TAG;
        };
    }

    public void openCustomizationMenu(Player player, UUID petUUID) {
        PetData petData = petManager.getPetData(petUUID);
        if (petData == null) {
            player.sendMessage(ChatColor.RED + "Error: Pet not found.");
            openMainMenu(player);
            return;
        }
        String title = ChatColor.DARK_AQUA + "Customize: " + petData.getDisplayName();
        if (title.length() > 32) title = ChatColor.DARK_AQUA + "Customize Pet";

        Inventory gui = Bukkit.createInventory(player, 45, title);

        
        gui.setItem(4, createItem(
                getDisplayMaterialForPet(petData),
                getNameColor(petData) + petData.getDisplayName(),
                Collections.singletonList(ChatColor.GRAY + "Select an option below.")
        ));

        
        gui.setItem(20, createActionButton(
                Material.ITEM_FRAME,
                ChatColor.AQUA + "Set Display Icon",
                "set_display_icon",
                petUUID,
                Arrays.asList(
                        ChatColor.GRAY + "Left-click: Use the item in your main hand",
                        ChatColor.GRAY + "as this pet's icon.",
                        ChatColor.GRAY + "Shift-click: Reset to default."
                )
        ));

        
        gui.setItem(24, createActionButton(
                Material.WHITE_DYE,
                ChatColor.AQUA + "Edit Name Color",
                "set_display_color",
                petUUID,
                Arrays.asList(
                        ChatColor.GRAY + "Left-click: Pick a color",
                        ChatColor.GRAY + "Shift-click: Reset to default color."
                )
        ));

        
        gui.setItem(44, createActionButton(Material.ARROW, ChatColor.YELLOW + "Back to Pet Management", "back_to_pet", petUUID, null));

        player.openInventory(gui);
    }

}
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
    public static final NamespacedKey TARGET_PLAYER_UUID_KEY = new NamespacedKey(Enhancedpets.getInstance(),
            "target_player_uuid");
    public static final String MAIN_MENU_TITLE = "menus.main_title";
    public static final String PET_MENU_TITLE_PREFIX = "menus.pet_title_prefix"; // Unused now
    public static final String FRIENDLY_MENU_TITLE_PREFIX = "menus.friendly_title_prefix"; // Unused now
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
        if (viewer != null && owner != null)
            viewerOwnerOverride.put(viewer, owner);
    }

    public void clearViewerOwnerOverride(UUID viewer) {
        if (viewer != null)
            viewerOwnerOverride.remove(viewer);
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

            Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.MAIN_MENU),
                    invSize,
                    plugin.getLanguageManager().getString(MAIN_MENU_TITLE));

            if (pets.isEmpty()) {
                gui.setItem(13,
                        this.createItem(Material.BARRIER, plugin.getLanguageManager().getString("menus.no_pets_name"),
                                plugin.getLanguageManager().getStringList("menus.no_pets_lore")));
            } else {
                for (int i = 0; i < pets.size(); i++) {
                    gui.setItem(i, this.createPetItem(pets.get(i)));
                }

                if (effectiveOwner.equals(player.getUniqueId())) {
                    gui.setItem(invSize - 8,
                            createActionButton(Material.COMPASS,
                                    plugin.getLanguageManager().getString("menus.scan_button_name"), "scan_for_pets",
                                    null,
                                    plugin.getLanguageManager().getStringList("menus.scan_button_lore")));
                }
                gui.setItem(invSize - 5,
                        this.createItem(Material.PAPER,
                                plugin.getLanguageManager().getStringReplacements("menus.page_count_name", "page", "1",
                                        "total",
                                        "1"),
                                plugin.getLanguageManager().getStringListReplacements("menus.page_count_lore", "count",
                                        String.valueOf(totalPets))));
                gui.setItem(invSize - 2,
                        createActionButton(Material.HOPPER,
                                plugin.getLanguageManager().getString("menus.batch_button_name"), "batch_actions", null,
                                plugin.getLanguageManager().getStringList("menus.batch_button_lore")));
            }
            player.openInventory(gui);
        } else {
            int totalPages = (int) Math.ceil((double) totalPets / petsPerPage);
            page = Math.max(0, Math.min(page, totalPages - 1));
            int invSize = 54;

            Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.MAIN_MENU),
                    invSize,
                    plugin.getLanguageManager().getString(MAIN_MENU_TITLE));
            int startIndex = page * petsPerPage;
            int endIndex = Math.min(startIndex + petsPerPage, totalPets);
            List<PetData> petsToShow = pets.subList(startIndex, endIndex);

            for (int i = 0; i < petsToShow.size(); i++) {
                gui.setItem(i, this.createPetItem(petsToShow.get(i)));
            }

            if (page > 0) {
                gui.setItem(invSize - 9,
                        this.createNavigationButton(Material.ARROW,
                                plugin.getLanguageManager().getString("menus.prev_page"),
                                "main_page", page - 1, null));
            }
            if (endIndex < totalPets) {
                gui.setItem(invSize - 1,
                        this.createNavigationButton(Material.ARROW,
                                plugin.getLanguageManager().getString("menus.next_page"),
                                "main_page", page + 1, null));
            }
            if (effectiveOwner.equals(player.getUniqueId())) {
                gui.setItem(invSize - 8,
                        createActionButton(Material.COMPASS,
                                plugin.getLanguageManager().getString("menus.scan_button_name"), "scan_for_pets", null,
                                plugin.getLanguageManager().getStringList("menus.scan_button_lore")));
            }
            gui.setItem(invSize - 5,
                    this.createItem(Material.PAPER,
                            plugin.getLanguageManager().getStringReplacements("menus.page_count_name", "page",
                                    String.valueOf(page + 1), "total", String.valueOf(totalPages)),
                            plugin.getLanguageManager().getStringListReplacements("menus.page_count_lore", "count",
                                    String.valueOf(totalPets))));
            gui.setItem(invSize - 2,
                    createActionButton(Material.HOPPER,
                            plugin.getLanguageManager().getString("menus.batch_button_name"), "batch_actions", null,
                            plugin.getLanguageManager().getStringList("menus.batch_button_lore")));

            player.openInventory(gui);
        }
    }

    public void openBatchManagementMenu(Player player, Set<UUID> selectedPetUUIDs) {
        if (selectedPetUUIDs == null || selectedPetUUIDs.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "gui.batch_no_selection");
            this.openMainMenu(player);
            return;
        }

        List<PetData> selectedPets = selectedPetUUIDs.stream()
                .map(petManager::getPetData)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String title = plugin.getLanguageManager().getStringReplacements("menus.batch_title", "count",
                String.valueOf(selectedPets.size()));
        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_MANAGE), 54,
                title);

        long favoriteCount = selectedPets.stream().filter(PetData::isFavorite).count();
        boolean anyFavorites = favoriteCount > 0;

        BehaviorMode commonMode = selectedPets.stream().map(PetData::getMode).distinct().count() == 1
                ? selectedPets.get(0).getMode()
                : BehaviorMode.BATCH;

        List<Entity> petEntities = selectedPets.stream().map(p -> Bukkit.getEntity(p.getPetUUID()))
                .filter(Objects::nonNull).toList();
        boolean allCanSit = !petEntities.isEmpty() && petEntities.stream().allMatch(e -> e instanceof Sittable);
        long sittingCount = petEntities.stream().filter(e -> e instanceof Sittable s && s.isSitting()).count();
        boolean allSitting = allCanSit && sittingCount == petEntities.size();
        boolean anySitting = allCanSit && sittingCount > 0;

        long protectedCount = selectedPets.stream().filter(PetData::isProtectedFromPlayers).count();
        boolean allProtected = protectedCount == selectedPets.size();
        boolean anyProtected = protectedCount > 0;

        PetData batchData = new PetData(null, player.getUniqueId(), null, "Batch");
        batchData.setMode(commonMode);

        boolean allMob = selectedPets.stream().allMatch(p -> p.getAggressiveTargetTypes().contains("MOB"));
        boolean allAnimal = selectedPets.stream().allMatch(p -> p.getAggressiveTargetTypes().contains("ANIMAL"));
        boolean allPlayer = selectedPets.stream().allMatch(p -> p.getAggressiveTargetTypes().contains("PLAYER"));

        Set<String> batchTypes = new HashSet<>();
        if (allMob)
            batchTypes.add("MOB");
        if (allAnimal)
            batchTypes.add("ANIMAL");
        if (allPlayer)
            batchTypes.add("PLAYER");
        batchData.setAggressiveTargetTypes(batchTypes);

        String favoriteDisplayName = (anyFavorites ? ChatColor.GOLD + "★ " : "") + plugin.getLanguageManager()
                .getStringReplacements("menus.managing_header", "count", String.valueOf(selectedPets.size()));
        gui.setItem(4, this.createActionButton(Material.HOPPER, favoriteDisplayName, "batch_toggle_favorite", null,
                plugin.getLanguageManager().getStringListReplacements("menus.managing_header_lore", "count",
                        String.valueOf(selectedPets.size()))));

        List<UUID> babyUUIDs = selectedPetUUIDs.stream()
                .map(Bukkit::getEntity)
                .filter(e -> e instanceof Ageable a && !a.isAdult())
                .map(Entity::getUniqueId)
                .toList();

        List<ItemStack> actionItems = new ArrayList<>();

        // 1. Sit (if applicable)
        if (allCanSit) {
            String sitStandName = allSitting ? plugin.getLanguageManager().getString("menus.make_stand")
                    : plugin.getLanguageManager().getString("menus.make_sit");
            String sitStandStatus = (allSitting ? plugin.getLanguageManager().getString("menus.all_sitting")
                    : anySitting ? plugin.getLanguageManager().getString("menus.mixed")
                            : plugin.getLanguageManager().getString("menus.all_standing"));
            actionItems.add(this.createActionButton(allSitting ? Material.ARMOR_STAND : Material.SADDLE, sitStandName,
                    "batch_toggle_sit", null,
                    plugin.getLanguageManager().getStringListReplacements("menus.sit_status_lore", "status",
                            sitStandStatus)));
        }

        // 2. Teleport (Always)
        actionItems.add(this.createActionButton(Material.ENDER_PEARL,
                plugin.getLanguageManager().getString("menus.teleport_pets"),
                "batch_teleport", null, plugin.getLanguageManager().getStringList("menus.teleport_pets_lore")));

        // 3. Calm (Always)
        actionItems.add(
                this.createActionButton(Material.MILK_BUCKET, plugin.getLanguageManager().getString("menus.calm_pets"),
                        "batch_calm", null,
                        plugin.getLanguageManager().getStringList("menus.calm_pets_lore")));

        // 4. Growth (if babies)
        if (!babyUUIDs.isEmpty()) {
            long pausedCount = babyUUIDs.stream()
                    .map(petManager::getPetData)
                    .filter(Objects::nonNull)
                    .filter(PetData::isGrowthPaused)
                    .count();
            int totalBabies = babyUUIDs.size();

            String status;
            if (pausedCount == totalBabies) {
                status = plugin.getLanguageManager().getString("menus.all_paused");
            } else if (pausedCount > 0) {
                status = plugin.getLanguageManager().getString("menus.mixed");
            } else {
                status = plugin.getLanguageManager().getString("menus.all_growing");
            }

            Material mat = pausedCount == totalBabies ? Material.GOLDEN_CARROT : Material.CARROT;
            actionItems.add(createActionButton(
                    mat,
                    plugin.getLanguageManager().getStringReplacements("menus.toggle_growth_baby_name", "color",
                            (pausedCount == totalBabies ? "&a" : "&e")),
                    "batch_toggle_growth_pause",
                    null,
                    plugin.getLanguageManager().getStringListReplacements("menus.toggle_growth_baby_lore", "status",
                            status)));
        }

        // Place Action Items Symmetrically
        int[] actionSlots;
        if (actionItems.size() == 4)
            actionSlots = new int[] { 19, 21, 23, 25 };
        else if (actionItems.size() == 3)
            actionSlots = new int[] { 20, 22, 24 };
        else
            actionSlots = new int[] { 21, 23 }; // Minimum 2 (Teleport, Calm)

        for (int i = 0; i < actionItems.size(); i++) {
            if (i < actionSlots.length)
                gui.setItem(actionSlots[i], actionItems.get(i));
        }

        gui.setItem(11, this.createModeButton(Material.FEATHER,
                plugin.getLanguageManager().getString("menus.set_passive"), BehaviorMode.PASSIVE, batchData));
        gui.setItem(13, this.createModeButton(Material.IRON_SWORD,
                plugin.getLanguageManager().getString("menus.set_neutral"), BehaviorMode.NEUTRAL, batchData));
        gui.setItem(15,
                this.createModeButton(Material.DIAMOND_SWORD,
                        plugin.getLanguageManager().getString("menus.set_aggressive"), BehaviorMode.AGGRESSIVE,
                        batchData));

        String protName = allProtected
                ? plugin.getLanguageManager().getString("menus.disable_protection")
                : plugin.getLanguageManager().getString("menus.enable_protection");
        String protStatus = (allProtected ? plugin.getLanguageManager().getString("menus.all_protected")
                : anyProtected ? plugin.getLanguageManager().getString("menus.mixed")
                        : plugin.getLanguageManager().getString("menus.all_unprotected"));
        gui.setItem(29, this.createActionButton(
                Material.SHIELD,
                protName,
                "batch_toggle_protection",
                null,
                plugin.getLanguageManager().getStringListReplacements("menus.protection_lore", "status", protStatus)));

        gui.setItem(31,
                this.createActionButton(Material.PLAYER_HEAD,
                        plugin.getLanguageManager().getString("menus.manage_friendly"),
                        "batch_manage_friendly", null,
                        plugin.getLanguageManager().getStringList("menus.manage_friendly_lore")));
        gui.setItem(33,
                this.createActionButton(Material.LEAD, plugin.getLanguageManager().getString("menus.transfer_pets"),
                        "batch_open_transfer", null,
                        plugin.getLanguageManager().getStringList("menus.transfer_pets_lore")));

        EntityType type = selectedPets.get(0).getEntityType();
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta meta = backButton.getItemMeta();
        meta.setDisplayName(plugin.getLanguageManager().getString("menus.back_selection"));
        meta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING,
                "open_pet_select");
        meta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING, type.name());
        backButton.setItemMeta(meta);
        gui.setItem(45, backButton);

        gui.setItem(53,
                this.createActionButton(Material.BARRIER, plugin.getLanguageManager().getString("menus.free_selected"),
                        "batch_free_pet_prompt",
                        null, plugin.getLanguageManager().getStringList("menus.free_selected_lore")));

        player.openInventory(gui);
    }

    public void openBatchConfirmFreeMenu(Player player, Set<UUID> selectedPetUUIDs) {
        String title = plugin.getLanguageManager().getStringReplacements("menus.confirm_free_title", "count",
                String.valueOf(selectedPetUUIDs.size()));
        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_CONFIRM_FREE),
                27, title);

        gui.setItem(13, this.createItem(
                Material.HOPPER,
                plugin.getLanguageManager().getStringReplacements("menus.free_info_name", "count",
                        String.valueOf(selectedPetUUIDs.size())),
                plugin.getLanguageManager().getStringList("menus.free_info_lore")));

        gui.setItem(11, this.createActionButton(
                Material.LIME_WOOL,
                plugin.getLanguageManager().getString("menus.cancel_free"),
                "open_batch_manage",
                null,
                plugin.getLanguageManager().getStringList("menus.cancel_free_lore")));

        gui.setItem(15, this.createActionButton(
                Material.RED_WOOL,
                plugin.getLanguageManager().getString("menus.confirm_free_btn"),
                "batch_confirm_free",
                null,
                plugin.getLanguageManager().getStringList("menus.confirm_free_btn_lore")));

        player.openInventory(gui);
    }

    public void openBatchFriendlyPlayerMenu(Player player, Set<UUID> selectedPetUUIDs, int page) {
        String title = plugin.getLanguageManager().getStringReplacements("menus.batch_friendly_title", "count",
                String.valueOf(selectedPetUUIDs.size()));
        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_FRIENDLY), 54,
                title);

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
            ItemStack head = this.createPlayerHead(friendlyUUID, ChatColor.YELLOW + friendly.getName(),
                    "remove_batch_friendly", null,
                    plugin.getLanguageManager().getStringList("menus.remove_friendly_lore"));
            gui.setItem(i - startIndex, head);
        }

        if (page > 0) {
            gui.setItem(45,
                    createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.prev_page"),
                            "batch_friendly_page",
                            null, null));
        }
        gui.setItem(48,
                createActionButton(Material.ANVIL, plugin.getLanguageManager().getString("menus.add_friendly"),
                        "add_batch_friendly_prompt",
                        null,
                        plugin.getLanguageManager().getStringList("menus.add_friendly_lore")));
        if (endIndex < friendlyList.size()) {
            gui.setItem(50,
                    createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.next_page"),
                            "batch_friendly_page",
                            null, null));
        }
        gui.setItem(53, createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.back_batch"),
                "open_batch_manage", null, null));

        player.openInventory(gui);
    }

    public void openBatchTransferMenu(Player player, Set<UUID> selectedPetUUIDs) {
        String title = plugin.getLanguageManager().getStringReplacements("menus.transfer_title", "count",
                String.valueOf(selectedPetUUIDs.size()));

        List<Player> eligiblePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(getEffectiveOwner(player)))
                .map(p -> (Player) p)
                .sorted(Comparator.comparing(Player::getName))
                .toList();

        int rows = (int) Math.ceil((double) eligiblePlayers.size() / 9.0);
        int invSize = Math.max(18, (rows + 1) * 9);
        if (eligiblePlayers.isEmpty())
            invSize = 27;
        invSize = Math.min(54, invSize);

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_TRANSFER),
                invSize, title);

        if (eligiblePlayers.isEmpty()) {
            gui.setItem(invSize == 27 ? 13 : 22,
                    this.createItem(Material.BARRIER, plugin.getLanguageManager().getString("menus.no_players"),
                            plugin.getLanguageManager().getStringList("menus.no_players_lore")));
        } else {
            for (int i = 0; i < eligiblePlayers.size(); i++) {
                if (i >= 45)
                    break;
                Player target = eligiblePlayers.get(i);
                ItemStack head = this.createPlayerHead(target.getUniqueId(), ChatColor.YELLOW + target.getName(),
                        "batch_transfer_to_player", null,
                        plugin.getLanguageManager().getStringListReplacements("menus.transfer_player_lore", "count",
                                String.valueOf(selectedPetUUIDs.size()), "player", target.getName()));
                gui.setItem(i, head);
            }
        }

        gui.setItem(invSize - 1,
                this.createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.back_batch"),
                        "open_batch_manage", null, null));
        player.openInventory(gui);
    }

    public void openPetMenu(Player player, UUID petUUID) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
            return;
        }

        String title = plugin.getLanguageManager().getStringReplacements("menus.pet_title", "name",
                petData.getDisplayName());
        if (title.length() > 32) {
            title = title.substring(0, 29) + "...";
        }

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.PET_MENU), 54, title);
        boolean isFavorite = petData.isFavorite();
        boolean protectionEnabled = petData.isProtectedFromPlayers();
        ChatColor nameColor = getNameColor(petData);
        Material headerIcon = getDisplayMaterialForPet(petData);

        if (petData.isDead()) {
            gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.PET_MENU), 27, title);
            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setDisplayName(plugin.getLanguageManager().getStringReplacements("menus.dead_pet_name", "name",
                    petData.getDisplayName()));
            meta.setLore(plugin.getLanguageManager().getStringListReplacements("menus.dead_pet_lore",
                    "item", plugin.getConfigManager().getReviveItem().name(),
                    "amount", String.valueOf(plugin.getConfigManager().getReviveItemAmount())));
            skull.setItemMeta(meta);
            gui.setItem(13, skull);
            gui.setItem(11, this.createActionButton(
                    plugin.getConfigManager().getReviveItem(),
                    plugin.getLanguageManager().getString("menus.revive_pet"),
                    "confirm_revive_pet",
                    petUUID,
                    plugin.getLanguageManager().getStringListReplacements("menus.revive_pet_lore",
                            "item", plugin.getConfigManager().getReviveItem().name(),
                            "amount", String.valueOf(plugin.getConfigManager().getReviveItemAmount()))));
            gui.setItem(15, this.createActionButton(
                    Material.BARRIER,
                    plugin.getLanguageManager().getString("menus.remove_pet"),
                    "confirm_remove_pet",
                    petUUID,
                    plugin.getLanguageManager().getStringList("menus.remove_pet_lore")));
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
            headerLore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.header_lore_alive", "health",
                    String.format("%.1f", health),
                    "max_health", String.format("%.1f", maxHealth)));
        } else {
            headerLore.addAll(plugin.getLanguageManager().getStringList("menus.header_lore_dead_state"));
        }
        headerLore.add(plugin.getLanguageManager().getStringReplacements("menus.header_protection", "status",
                (protectionEnabled ? plugin.getLanguageManager().getString("menus.enabled")
                        : plugin.getLanguageManager().getString("menus.disabled"))));

        int friendlyCount = petData.getFriendlyPlayers().size();
        if (friendlyCount > 0) {
            headerLore.add(plugin.getLanguageManager().getStringReplacements("menus.header_friendly_count", "count",
                    String.valueOf(friendlyCount)));
        }
        if (petEntity instanceof Ageable ageable && !ageable.isAdult()) {
            headerLore.add(plugin.getLanguageManager().getString("menus.header_baby"));
        }
        headerLore.add("");
        headerLore.addAll(plugin.getLanguageManager().getStringList("menus.header_instructions"));

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
                    plugin.getLanguageManager().getStringReplacements("menus.toggle_growth_name", "color",
                            (paused ? "&a" : "&e")),
                    "toggle_growth_pause",
                    petUUID,
                    plugin.getLanguageManager().getStringListReplacements("menus.toggle_growth_lore", "status",
                            (paused ? plugin.getLanguageManager().getString("menus.paused")
                                    : plugin.getLanguageManager().getString("menus.growing")))));
        }

        gui.setItem(11, this.createModeButton(Material.FEATHER,
                plugin.getLanguageManager().getString("menus.set_passive"), BehaviorMode.PASSIVE, petData));
        gui.setItem(13, this.createModeButton(Material.IRON_SWORD,
                plugin.getLanguageManager().getString("menus.set_neutral"), BehaviorMode.NEUTRAL, petData));
        gui.setItem(15,
                this.createModeButton(Material.DIAMOND_SWORD,
                        plugin.getLanguageManager().getString("menus.set_aggressive"), BehaviorMode.AGGRESSIVE,
                        petData));

        boolean canSit = petEntity instanceof Sittable;
        if (canSit) {
            boolean isSitting = false;
            if (petEntity instanceof Sittable s) {
                isSitting = s.isSitting();
            }
            gui.setItem(20, this.createActionButton(
                    isSitting ? Material.ARMOR_STAND : Material.SADDLE,
                    isSitting ? plugin.getLanguageManager().getString("menus.make_stand")
                            : plugin.getLanguageManager().getString("menus.make_sit"),
                    "toggle_sit",
                    petData.getPetUUID(),
                    plugin.getLanguageManager().getStringListReplacements("menus.sit_status_lore", "status",
                            (isSitting ? plugin.getLanguageManager().getString("menus.sitting")
                                    : plugin.getLanguageManager().getString("menus.standing")))));
        }

        gui.setItem(22, this.createActionButton(
                Material.ENDER_PEARL,
                plugin.getLanguageManager().getString("menus.teleport_pet"),
                "teleport_pet",
                petData.getPetUUID(),
                plugin.getLanguageManager().getStringList("menus.teleport_pet_lore")));

        gui.setItem(24, this.createActionButton(
                Material.MILK_BUCKET,
                plugin.getLanguageManager().getString("menus.calm_pet"),
                "calm_pet",
                petData.getPetUUID(),
                plugin.getLanguageManager().getStringList("menus.calm_pet_lore")));

        gui.setItem(29, this.createActionButton(
                Material.ANVIL,
                plugin.getLanguageManager().getString("menus.rename_pet"),
                "rename_pet_prompt",
                petData.getPetUUID(),
                plugin.getLanguageManager().getStringList("menus.rename_pet_lore")));

        gui.setItem(31, this.createActionButton(
                Material.PLAYER_HEAD,
                plugin.getLanguageManager().getString("menus.manage_friendly"),
                "manage_friendly",
                petData.getPetUUID(),
                plugin.getLanguageManager().getStringList("menus.manage_friendly_lore_single")));

        gui.setItem(33, this.createActionButton(
                Material.LEAD,
                plugin.getLanguageManager().getString("menus.transfer_pet"),
                "open_transfer",
                petData.getPetUUID(),
                plugin.getLanguageManager().getStringList("menus.transfer_pet_lore")));

        String protName = protectionEnabled
                ? plugin.getLanguageManager().getString("menus.disable_protection")
                : plugin.getLanguageManager().getString("menus.enable_protection");
        List<String> protLore = new ArrayList<>();
        protLore.add(plugin.getLanguageManager().getStringReplacements("menus.protection_status", "status",
                (protectionEnabled ? plugin.getLanguageManager().getString("menus.enabled")
                        : plugin.getLanguageManager().getString("menus.disabled"))));
        protLore.add("");
        protLore.addAll(plugin.getLanguageManager().getStringList("menus.protection_desc"));
        gui.setItem(35, this.createActionButton(
                Material.SHIELD,
                protName,
                "toggle_mutual_protection",
                petData.getPetUUID(),
                protLore));

        // Station Button
        boolean isStationed = petData.getStationLocation() != null;
        String currentRadius = String.format("%.0fm", petData.getStationRadius());

        Set<String> types = petData.getStationTargetTypes();
        if (types == null)
            types = new HashSet<>();

        boolean hasMob = types.contains("MOB");
        boolean hasPlayer = types.contains("PLAYER");
        boolean hasAnimal = types.contains("ANIMAL");

        String currentTypes;
        if (hasMob && hasPlayer && hasAnimal) {
            currentTypes = plugin.getLanguageManager().getString("menus.target_everything");
        } else if (hasMob && hasPlayer) {
            currentTypes = plugin.getLanguageManager().getString("menus.target_mobs_players");
        } else if (hasMob && hasAnimal) {
            currentTypes = plugin.getLanguageManager().getString("menus.target_mobs_animals");
        } else if (hasPlayer && hasAnimal) {
            currentTypes = plugin.getLanguageManager().getString("menus.target_players_animals");
        } else if (hasMob) {
            currentTypes = plugin.getLanguageManager().getString("menus.target_mobs_only");
        } else if (hasPlayer) {
            currentTypes = plugin.getLanguageManager().getString("menus.target_players_only");
        } else if (hasAnimal) {
            currentTypes = plugin.getLanguageManager().getString("menus.target_animals_only");
        } else {
            currentTypes = plugin.getLanguageManager().getString("menus.target_none");
        }

        List<String> stationLore = new ArrayList<>();
        stationLore.add(isStationed ? plugin.getLanguageManager().getString("menus.station_active")
                : plugin.getLanguageManager().getString("menus.station_inactive"));
        stationLore.add("");
        stationLore.add(plugin.getLanguageManager().getString("menus.station_settings"));
        stationLore.add(plugin.getLanguageManager().getStringReplacements("menus.station_radius", "radius",
                String.valueOf(currentRadius)));
        stationLore.add(plugin.getLanguageManager().getStringReplacements("menus.station_targets", "targets",
                currentTypes));
        stationLore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.station_click_lore",
                "action", (isStationed ? plugin.getLanguageManager().getString("menus.unstation_action")
                        : plugin.getLanguageManager().getString("menus.station_action"))));

        gui.setItem(38, this.createActionButton(
                isStationed ? Material.CAMPFIRE : Material.SOUL_CAMPFIRE,
                isStationed ? plugin.getLanguageManager().getString("menus.modify_station")
                        : plugin.getLanguageManager().getString("menus.station_here"),
                "toggle_station",
                petData.getPetUUID(),
                stationLore));

        // Target Button
        boolean hasExplicitTarget = petData.getExplicitTargetUUID() != null;
        String targetName = plugin.getLanguageManager().getString("menus.target_none");
        if (hasExplicitTarget) {
            Entity t = Bukkit.getEntity(petData.getExplicitTargetUUID());
            if (t != null)
                targetName = t.getName();
            else
                targetName = plugin.getLanguageManager().getString("menus.unloaded_unknown");
        }

        List<String> targetLore = new ArrayList<>();
        targetLore.add(plugin.getLanguageManager().getStringReplacements("menus.target_current", "target", targetName));
        targetLore.addAll(plugin.getLanguageManager().getStringList("menus.target_click_lore"));

        gui.setItem(40, this.createActionButton(
                Material.CROSSBOW,
                hasExplicitTarget ? plugin.getLanguageManager().getString("menus.clear_target")
                        : plugin.getLanguageManager().getString("menus.set_target"),
                "toggle_target_prompt",
                petData.getPetUUID(),
                targetLore));

        // Heal Button
        double currentHp = 0, maxHp = 0;
        if (petEntity instanceof LivingEntity le && petEntity.isValid()) {
            currentHp = le.getHealth();
            maxHp = le.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        double missingHp = maxHp - currentHp;
        int healCost = (int) Math.ceil(missingHp) * 100;

        gui.setItem(42, this.createActionButton(
                Material.GOLDEN_APPLE,
                plugin.getLanguageManager().getString("menus.heal_pet"),
                "heal_pet",
                petData.getPetUUID(),
                plugin.getLanguageManager().getStringListReplacements("menus.heal_lore",
                        "health", String.format("%.1f", currentHp),
                        "max_health", String.format("%.1f", maxHp),
                        "cost", String.valueOf(healCost))));

        // Store Button
        gui.setItem(44, this.createActionButton(
                Material.ENDER_CHEST,
                plugin.getLanguageManager().getString("menus.store_pet"),
                "store_pet",
                petData.getPetUUID(),
                plugin.getLanguageManager().getStringList("menus.store_lore")));

        gui.setItem(49,
                this.createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.back_to_list"),
                        "back_to_main",
                        null, null));

        gui.setItem(53, this.createActionButton(
                Material.BARRIER,
                plugin.getLanguageManager().getString("menus.free_pet"),
                "confirm_free_pet_prompt",
                petData.getPetUUID(),
                plugin.getLanguageManager().getStringList("menus.free_pet_lore")));

        player.openInventory(gui);
    }

    public void openConfirmFreeMenu(Player player, UUID petUUID) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
            return;
        }

        String title = plugin.getLanguageManager().getStringReplacements("menus.confirm_free_single_title", "name",
                petData.getDisplayName());
        if (title.length() > 32) {
            title = title.substring(0, 29) + "...";
        }

        Inventory gui = Bukkit.createInventory(player, 27, title);

        gui.setItem(13, this.createItem(
                this.getPetMaterial(petData.getEntityType()),
                plugin.getLanguageManager().getStringReplacements("menus.free_info_single_name", "name",
                        petData.getDisplayName()),
                plugin.getLanguageManager().getStringListReplacements("menus.free_info_single_lore", "type",
                        petData.getEntityType().name()),
                petUUID));

        gui.setItem(11, this.createActionButton(
                Material.LIME_WOOL,
                plugin.getLanguageManager().getString("menus.cancel_free"),
                "cancel_free",
                petUUID,
                plugin.getLanguageManager().getStringList("menus.cancel_free_single_lore")));

        gui.setItem(15, this.createActionButton(
                Material.RED_WOOL,
                plugin.getLanguageManager().getString("menus.confirm_free_btn"),
                "confirm_free",
                petUUID,
                plugin.getLanguageManager().getStringList("menus.confirm_free_single_btn_lore")));

        player.openInventory(gui);
    }

    public void openTransferMenu(Player player, UUID petUUID) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
            return;
        }

        String title = plugin.getLanguageManager().getStringReplacements("menus.transfer_single_title", "name",
                petData.getDisplayName());
        if (title.length() > 32) {
            title = title.substring(0, 29) + "...";
        }

        List<Player> eligiblePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(getEffectiveOwner(player)))
                .sorted(Comparator.comparing(Player::getName))
                .collect(Collectors.toList());

        int rows = (int) Math.ceil((double) eligiblePlayers.size() / 9.0);
        int invSize = Math.max(18, (rows + 1) * 9);
        if (eligiblePlayers.isEmpty())
            invSize = 27;
        invSize = Math.min(54, invSize);

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.TRANSFER), invSize,
                title);

        if (eligiblePlayers.isEmpty()) {
            gui.setItem(invSize == 27 ? 13 : 22, this.createItem(
                    Material.BARRIER,
                    plugin.getLanguageManager().getString("menus.no_players"),
                    plugin.getLanguageManager().getStringList("menus.no_players_lore")));
        } else {
            for (int i = 0; i < eligiblePlayers.size(); i++) {
                if (i >= 45)
                    break;
                Player target = eligiblePlayers.get(i);
                ItemStack head = this.createPlayerHead(target.getUniqueId(), ChatColor.YELLOW + target.getName(),
                        "transfer_to_player", petUUID,
                        plugin.getLanguageManager().getStringListReplacements("menus.transfer_single_player_lore",
                                "name", petData.getDisplayName(), "player", target.getName()));
                gui.setItem(i, head);
            }
        }

        gui.setItem(invSize - 1,
                this.createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.back_pet"),
                        "back_to_pet", petUUID, null));
        player.openInventory(gui);
    }

    public void openFriendlyPlayerMenu(Player player, UUID petUUID, int page) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
            return;
        }

        String title = plugin.getLanguageManager().getStringReplacements("menus.friendly_title", "name",
                petData.getDisplayName());
        if (title.length() > 32) {
            title = title.substring(0, 29) + "...";
        }

        List<UUID> friendlyList = new ArrayList<>(petData.getFriendlyPlayers());

        int itemsPerPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil(friendlyList.size() / (double) itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, friendlyList.size());

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.FRIENDLY), 54, title);

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
                    plugin.getLanguageManager().getStringList("menus.remove_friendly_lore"));
            gui.setItem(slot++, head);
        }

        if (page > 0) {
            gui.setItem(45,
                    this.createFriendlyNavButton(Material.ARROW,
                            plugin.getLanguageManager().getString("menus.prev_page"),
                            "friendly_page", petUUID, page - 1));
        }

        gui.setItem(48,
                this.createActionButton(Material.ANVIL, plugin.getLanguageManager().getString("menus.add_friendly"),
                        "add_friendly_prompt",
                        petUUID, plugin.getLanguageManager().getStringList("menus.add_friendly_lore_single")));

        if (endIndex < friendlyList.size()) {
            gui.setItem(50,
                    this.createFriendlyNavButton(Material.ARROW,
                            plugin.getLanguageManager().getString("menus.next_page"), "friendly_page",
                            petUUID, page + 1));
        }

        gui.setItem(53, this.createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.back_pet"),
                "back_to_pet", petUUID, null));

        player.openInventory(gui);
    }

    public void openColorPicker(Player player, UUID petUUID) {
        PetData petData = petManager.getPetData(petUUID);
        if (petData == null) {
            plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            openMainMenu(player);
            return;
        }
        String title = plugin.getLanguageManager().getStringReplacements("menus.pick_color_title", "name",
                petData.getDisplayName());
        if (title.length() > 32)
            title = plugin.getLanguageManager().getString("menus.pick_color_short");

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.COLOR_PICKER), 27,
                title);

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
            if (i >= 26)
                break;
            Material mat = entry.getKey();
            ChatColor color = entry.getValue();
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color + color.name());
            meta.setLore(plugin.getLanguageManager().getStringList("menus.pick_color_lore"));
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "choose_color");
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
            meta.getPersistentDataContainer().set(COLOR_KEY, PersistentDataType.STRING, color.name());
            item.setItemMeta(meta);
            gui.setItem(i++, item);
        }

        gui.setItem(26, createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.back"),
                "back_to_pet", petUUID, null));

        player.openInventory(gui);
    }

    private ItemStack createNavigationButton(Material material, String name, String action, int targetPage,
            List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty())
            meta.setLore(lore);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, targetPage);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFriendlyNavButton(Material material, String name, String action, UUID petUUID,
            int targetPage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
        meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, targetPage);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createActionButton(Material material, String name, String action, UUID petUUID,
            List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty())
            meta.setLore(lore);
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
        String title = plugin.getLanguageManager().getStringReplacements("menus.batch_remove_dead_title", "type",
                typeName);
        Inventory gui = Bukkit.createInventory(
                new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_CONFIRM_REMOVE_DEAD), 27, title);

        ItemStack info = createItem(
                Material.SKELETON_SKULL,
                plugin.getLanguageManager().getStringReplacements("menus.batch_remove_dead_name", "count",
                        String.valueOf(deadPetCount)),
                plugin.getLanguageManager().getStringListReplacements("menus.batch_remove_dead_lore", "type",
                        typeName));
        gui.setItem(13, info);

        ItemStack cancel = new ItemStack(Material.LIME_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(plugin.getLanguageManager().getString("menus.cancel"));
        cancelMeta.setLore(plugin.getLanguageManager().getStringList("menus.cancel_remove_dead_lore"));
        cancelMeta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING,
                "open_pet_select");
        cancelMeta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING,
                petType.name());
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);

        ItemStack confirm = new ItemStack(Material.RED_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(plugin.getLanguageManager().getString("menus.confirm_remove_dead"));
        confirmMeta.setLore(plugin.getLanguageManager().getStringList("menus.confirm_remove_dead_lore"));
        confirmMeta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING,
                "batch_confirm_remove_dead");
        confirmMeta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING,
                petType.name());
        confirm.setItemMeta(confirmMeta);
        gui.setItem(11, confirm);

        player.openInventory(gui);
    }

    public ItemStack createPetItem(PetData petData) {
        Entity petEntity = Bukkit.getEntity(petData.getPetUUID());

        // Dead Pet Handler
        if (petData.isDead()) {
            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setDisplayName(plugin.getLanguageManager().getStringReplacements("menus.dead_pet_item_name", "name",
                    petData.getDisplayName()));
            meta.setLore(plugin.getLanguageManager().getStringList("menus.dead_pet_item_lore"));
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                    petData.getPetUUID().toString());
            skull.setItemMeta(meta);
            return skull;
        }

        // Stored Pet Handler
        if (petData.isStored()) {
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta meta = chest.getItemMeta();
            meta.setDisplayName(plugin.getLanguageManager().getStringReplacements("menus.stored_pet_item_name", "name",
                    petData.getDisplayName()));
            meta.setLore(plugin.getLanguageManager().getStringListReplacements("menus.stored_pet_item_lore", "type",
                    petData.getEntityType().name()));
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                    petData.getPetUUID().toString());
            chest.setItemMeta(meta);
            return chest;
        }

        Material mat = getDisplayMaterialForPet(petData);
        ChatColor nameColor = getNameColor(petData);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String displayName = (petData.isFavorite() ? ChatColor.GOLD + "★ " : "") + nameColor + petData.getDisplayName();
        meta.setDisplayName(displayName);
        List<String> lore = new java.util.ArrayList<>();
        lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_basic", "type",
                petData.getEntityType().name()));

        // Variant Info (Wolf, Cat, Parrot, etc.)
        String variant = getVariantFromMetadata(petData);
        if (variant != null) {
            lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_variant", "variant",
                    variant));
        }

        lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_mode", "mode",
                petData.getMode().name()));

        if (petEntity instanceof LivingEntity livingEntity && petEntity.isValid()) {
            double health = livingEntity.getHealth();
            double maxHealth = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_health", "health",
                    String.format("%.1f", health), "max_health", String.format("%.1f", maxHealth)));
        } else {
            lore.addAll(plugin.getLanguageManager().getStringList("menus.pet_item_lore_health_unknown"));
        }

        lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_protection", "status",
                (petData.isProtectedFromPlayers() ? plugin.getLanguageManager().getString("menus.enabled")
                        : plugin.getLanguageManager().getString("menus.disabled"))));

        int friendlyCount = petData.getFriendlyPlayers().size();
        if (friendlyCount > 0) {
            lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_friendly",
                    "count", String.valueOf(friendlyCount),
                    "s", (friendlyCount == 1 ? "" : "s")));
        }
        if (petEntity instanceof Ageable ageable && !ageable.isAdult()) {
            lore.addAll(plugin.getLanguageManager().getStringList("menus.pet_item_lore_baby"));
        }

        // Station Status
        if (petData.getStationLocation() != null) {
            Location st = petData.getStationLocation();
            String tTypes = formatTargetTypes(petData.getStationTargetTypes());
            lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_station",
                    "x", String.valueOf(st.getBlockX()),
                    "y", String.valueOf(st.getBlockY()),
                    "z", String.valueOf(st.getBlockZ()),
                    "radius", String.valueOf((int) petData.getStationRadius()),
                    "target_types", tTypes));
        }

        // Explicit Target Status
        if (petData.getExplicitTargetUUID() != null) {
            Entity target = Bukkit.getEntity(petData.getExplicitTargetUUID());
            String tName = plugin.getLanguageManager().getString("menus.unloaded_unknown");
            if (target != null) {
                tName = target.getName();
                if (target instanceof Player p)
                    tName = p.getName();
            }
            lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_target", "target",
                    tName));
        }

        lore.add("");
        lore.addAll(plugin.getLanguageManager().getStringList("menus.pet_item_lore_footer"));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petData.getPetUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    private String getVariantFromMetadata(PetData petData) {
        Map<String, Object> md = petData.getMetadata();
        if (md == null)
            return null;

        // Wolf Variant
        if (md.containsKey("wolfVariant")) {
            String raw = (String) md.get("wolfVariant");
            // Format: "minecraft:pale" -> "Pale"
            if (raw.contains(":"))
                raw = raw.substring(raw.indexOf(":") + 1);
            return raw.substring(0, 1).toUpperCase() + raw.substring(1).toLowerCase();
        }
        // Cat Type
        if (md.containsKey("catType")) {
            return (String) md.get("catType");
        }
        // Parrot Variant
        if (md.containsKey("parrotVariant")) {
            return (String) md.get("parrotVariant");
        }
        // Horse Color
        if (md.containsKey("horseColor")) {
            return (String) md.get("horseColor");
        }
        // Llama Color
        if (md.containsKey("llamaColor")) {
            return (String) md.get("llamaColor");
        }
        return null;
    }

    private String formatTargetTypes(Set<String> types) {
        if (types == null || types.isEmpty())
            return plugin.getLanguageManager().getString("menus.target_none");
        boolean p = types.contains("PLAYER");
        boolean m = types.contains("MOB");
        boolean a = types.contains("ANIMAL");
        if (p && m && a)
            return plugin.getLanguageManager().getString("menus.target_everything");
        if (p && m)
            return plugin.getLanguageManager().getString("menus.target_mobs_players");
        if (m && a)
            return plugin.getLanguageManager().getString("menus.target_mobs_animals");
        if (p && a)
            return plugin.getLanguageManager().getString("menus.target_players_animals");
        if (p)
            return plugin.getLanguageManager().getString("menus.target_players_only");
        if (m)
            return plugin.getLanguageManager().getString("menus.target_mobs_only");
        if (a)
            return plugin.getLanguageManager().getString("menus.target_animals_only");
        return plugin.getLanguageManager().getString("menus.target_custom");
    }

    public ItemStack createModeButton(Material material, String name, BehaviorMode mode, PetData currentPetData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean isActive = currentPetData.getMode() == mode;
            meta.setDisplayName((isActive ? "" + ChatColor.GREEN + ChatColor.BOLD
                    : currentPetData.getMode() == BehaviorMode.BATCH ? ChatColor.AQUA : ChatColor.YELLOW) + name);

            List<String> lore = new ArrayList<>();
            switch (mode) {
                case PASSIVE -> lore.addAll(plugin.getLanguageManager().getStringList("menus.mode_passive_desc"));
                case NEUTRAL -> lore.addAll(plugin.getLanguageManager().getStringList("menus.mode_neutral_desc"));
                case AGGRESSIVE -> lore.addAll(plugin.getLanguageManager().getStringList("menus.mode_aggressive_desc"));
                default -> {
                }
            }

            if (isActive) {
                lore.add("");
                lore.addAll(plugin.getLanguageManager().getStringList("menus.mode_active"));
            } else if (currentPetData.getMode() == BehaviorMode.BATCH) {
                lore.add("");
                lore.addAll(plugin.getLanguageManager().getStringList("menus.mode_batch_mixed"));
            } else {
                lore.add("");
                lore.addAll(plugin.getLanguageManager().getStringList("menus.mode_inactive"));
            }

            if (mode == BehaviorMode.AGGRESSIVE) {
                Set<String> types = currentPetData.getAggressiveTargetTypes();
                boolean mob = types.contains("MOB");
                boolean animal = types.contains("ANIMAL");
                boolean player = types.contains("PLAYER");
                lore.add("");
                lore.addAll(plugin.getLanguageManager().getStringListReplacements("menus.aggressive_lore_config",
                        "mob_status",
                        (mob ? plugin.getLanguageManager().getString("menus.enabled")
                                : plugin.getLanguageManager().getString("menus.disabled")),
                        "animal_status",
                        (animal ? plugin.getLanguageManager().getString("menus.enabled")
                                : plugin.getLanguageManager().getString("menus.disabled")),
                        "player_status", (player ? plugin.getLanguageManager().getString("menus.enabled")
                                : plugin.getLanguageManager().getString("menus.disabled"))));
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
                meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                        currentPetData.getPetUUID().toString());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPlayerHead(UUID playerUUID, String name, String action, UUID petContextUUID,
            List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerUUID);
            meta.setOwningPlayer(targetPlayer);
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty())
                meta.setLore(lore);
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
            if (petContextUUID != null) {
                meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                        petContextUUID.toString());
            }
            meta.getPersistentDataContainer().set(TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING,
                    playerUUID.toString());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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
            if (lore != null && !lore.isEmpty())
                meta.setLore(lore);
            if (petUUIDContext != null) {
                meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                        petUUIDContext.toString());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ChatColor getNameColor(PetData data) {
        String c = data.getDisplayColor();
        if (c == null || c.isEmpty())
            return ChatColor.AQUA;
        try {
            return ChatColor.valueOf(c);
        } catch (IllegalArgumentException ex) {
            return ChatColor.AQUA;
        }
    }

    public Material getDisplayMaterialForPet(PetData data) {
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
            plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            openMainMenu(player);
            return;
        }
        String title = plugin.getLanguageManager().getStringReplacements("menus.customize_title", "name",
                petData.getDisplayName());
        if (title.length() > 32)
            title = plugin.getLanguageManager().getString("menus.customize_title_short");

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.CUSTOMIZE), 45,
                title);

        gui.setItem(4, createItem(
                getDisplayMaterialForPet(petData),
                getNameColor(petData) + petData.getDisplayName(),
                plugin.getLanguageManager().getStringList("menus.customize_header_lore"))); // Assuming this key exists

        gui.setItem(20, createActionButton(
                Material.ITEM_FRAME,
                plugin.getLanguageManager().getString("menus.set_display_icon"),
                "set_display_icon",
                petUUID,
                plugin.getLanguageManager().getStringList("menus.set_display_icon_lore")));

        gui.setItem(24, createActionButton(
                Material.WHITE_DYE,
                plugin.getLanguageManager().getString("menus.edit_name_color"),
                "set_display_color",
                petUUID,
                plugin.getLanguageManager().getStringList("menus.edit_name_color_lore")));

        gui.setItem(44,
                createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.back_pet"),
                        "back_to_pet",
                        petUUID, null));

        player.openInventory(gui);
    }

    public void openStorePetMenu(Player player) {
        UUID ownerUUID = getEffectiveOwner(player);
        List<PetData> activePets = petManager.getPetsOwnedBy(ownerUUID).stream()
                .filter(p -> !p.isDead() && !p.isStored())
                .filter(p -> Bukkit.getEntity(p.getPetUUID()) != null)
                .collect(Collectors.toList());

        String title = plugin.getLanguageManager().getString("menus.store_pet_title");
        int size = Math.max(9, (int) Math.ceil(activePets.size() / 9.0) * 9);
        if (size > 54)
            size = 54;
        if (size < 9)
            size = 9;

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.STORE_PET), size,
                title);

        for (int i = 0; i < Math.min(activePets.size(), size); i++) {
            PetData pet = activePets.get(i);
            ItemStack item = new ItemStack(Material.ENDER_CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.getLanguageManager().getStringReplacements("menus.store_pet_item_name", "name",
                    pet.getDisplayName()));
            meta.setLore(plugin.getLanguageManager().getStringListReplacements("menus.store_pet_item_lore", "type",
                    formatEntityType(pet.getEntityType())));
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "do_store_pet");
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, pet.getPetUUID().toString());
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        if (activePets.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName(plugin.getLanguageManager().getString("menus.no_active_pets"));
            empty.setItemMeta(meta);
            gui.setItem(4, empty);
        }

        player.openInventory(gui);
    }

    public void openWithdrawPetMenu(Player player) {
        UUID ownerUUID = getEffectiveOwner(player);
        List<PetData> storedPets = petManager.getPetsOwnedBy(ownerUUID).stream()
                .filter(PetData::isStored)
                .collect(Collectors.toList());

        String title = plugin.getLanguageManager().getString("menus.withdraw_pet_title");
        int size = Math.max(9, (int) Math.ceil(storedPets.size() / 9.0) * 9);
        if (size > 54)
            size = 54;
        if (size < 9)
            size = 9;

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.WITHDRAW_PET), size,
                title);

        for (int i = 0; i < Math.min(storedPets.size(), size); i++) {
            PetData pet = storedPets.get(i);
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.getLanguageManager().getStringReplacements("menus.withdraw_pet_item_name",
                    "name", pet.getDisplayName()));
            meta.setLore(plugin.getLanguageManager().getStringListReplacements("menus.withdraw_pet_item_lore", "type",
                    formatEntityType(pet.getEntityType())));
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "do_withdraw_pet");
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, pet.getPetUUID().toString());
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        if (storedPets.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName(plugin.getLanguageManager().getString("menus.no_stored_pets"));
            empty.setItemMeta(meta);
            gui.setItem(4, empty);
        }

        player.openInventory(gui);
    }

    private String formatEntityType(EntityType type) {
        String name = type.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return formatted.toString().trim();
    }
}
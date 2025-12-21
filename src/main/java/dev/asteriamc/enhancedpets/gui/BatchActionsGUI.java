package dev.asteriamc.enhancedpets.gui;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class BatchActionsGUI {

    public static final NamespacedKey BATCH_ACTION_KEY = new NamespacedKey(Enhancedpets.getInstance(), "batch_action");
    public static final NamespacedKey PET_TYPE_KEY = new NamespacedKey(Enhancedpets.getInstance(), "pet_type");
    // Removed duplicate PAGE_KEY - accessing PetManagerGUI.PAGE_KEY directly

    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final PetManagerGUI mainGui;
    private final Map<UUID, Set<UUID>> playerSelections = new HashMap<>();

    public BatchActionsGUI(Enhancedpets plugin, PetManagerGUI mainGui) {
        this.plugin = plugin;
        this.petManager = plugin.getPetManager();
        this.mainGui = mainGui;
    }

    public void clearSelections(UUID playerId) {
        playerSelections.remove(playerId);
    }

    public void openPetTypeSelectionMenu(Player player) {
        UUID owner = mainGui.getEffectiveOwner(player);
        List<PetData> allPets = petManager.getPetsOwnedBy(owner);
        List<EntityType> petTypes = allPets.stream()
                .map(PetData::getEntityType)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());

        int rows = (int) Math.ceil((double) petTypes.size() / 9.0);
        int invSize = Math.max(18, (rows + 1) * 9);
        if (petTypes.isEmpty())
            invSize = 27;
        invSize = Math.min(54, invSize);

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_TYPE_SELECT),
                invSize,
                plugin.getLanguageManager().getString("menus.batch_type_title"));

        if (petTypes.isEmpty()) {
            gui.setItem(13,
                    mainGui.createItem(Material.BARRIER, plugin.getLanguageManager().getString("menus.no_types_found"),
                            plugin.getLanguageManager().getStringList("menus.no_types_found_lore")));
        } else {
            for (int i = 0; i < petTypes.size(); i++) {
                EntityType type = petTypes.get(i);
                long count = allPets.stream().filter(p -> p.getEntityType() == type).count();
                ItemStack item = createTypeSelectionItem(type, count);
                gui.setItem(i, item);
            }
        }

        gui.setItem(invSize - 1,
                mainGui.createActionButton(Material.ARROW, plugin.getLanguageManager().getString("menus.back_main"),
                        "back_to_main", null, null));
        player.openInventory(gui);
    }

    public void openPetSelectionMenu(Player player, EntityType petType, int page) {
        playerSelections.putIfAbsent(player.getUniqueId(), new HashSet<>());
        Set<UUID> selectedPets = playerSelections.get(player.getUniqueId());

        UUID owner = mainGui.getEffectiveOwner(player);
        List<PetData> petsOfType = petManager.getPetsOwnedBy(owner).stream()
                .filter(p -> p.getEntityType() == petType)
                .sorted(Comparator.comparing(PetData::isFavorite).reversed()
                        .thenComparing((p1, p2) -> {
                            String name1 = p1.getDisplayName();
                            String name2 = p2.getDisplayName();

                            Integer id1 = PetManagerGUI.extractPetIdFromName(name1);
                            Integer id2 = PetManagerGUI.extractPetIdFromName(name2);

                            if (id1 != null && id2 != null) {
                                return Integer.compare(id1, id2);
                            }

                            return String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
                        }))
                .collect(Collectors.toList());

        int petsPerPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) petsOfType.size() / petsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String niceTitle = formatEntityType(petType);
        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_PET_SELECT), 54,
                plugin.getLanguageManager().getStringReplacements("menus.batch_select_title", "type", niceTitle, "s",
                        "s"));

        int startIndex = page * petsPerPage;
        for (int i = 0; i < petsPerPage; i++) {
            int petIndex = startIndex + i;
            if (petIndex < petsOfType.size()) {
                PetData pet = petsOfType.get(petIndex);
                boolean isSelected = selectedPets.contains(pet.getPetUUID());
                gui.setItem(i, createPetSelectionItem(pet, isSelected, page));
            }
        }

        gui.setItem(45, createNavButton(Material.OAK_DOOR, plugin.getLanguageManager().getString("menus.back_type"),
                "open_type_select", petType, -1));

        if (page > 0) {
            gui.setItem(46,
                    createNavButton(Material.ARROW, plugin.getLanguageManager().getString("menus.prev_page"),
                            "batch_select_page",
                            petType, page - 1));
        }

        gui.setItem(48,
                createSelectionButton(Material.RED_STAINED_GLASS_PANE,
                        plugin.getLanguageManager().getString("menus.select_none"),
                        "select_none", petType));
        gui.setItem(50,
                createSelectionButton(Material.LIME_STAINED_GLASS_PANE,
                        plugin.getLanguageManager().getString("menus.select_all"),
                        "select_all", petType));

        if (petsOfType.stream().anyMatch(PetData::isDead)) {
            gui.setItem(51,
                    createSelectionButton(Material.SKELETON_SKULL,
                            plugin.getLanguageManager().getString("menus.remove_all_dead"),
                            "batch_remove_dead", petType));
        }
        if ((page + 1) < totalPages) {
            gui.setItem(52,
                    createNavButton(Material.ARROW, plugin.getLanguageManager().getString("menus.next_page"),
                            "batch_select_page", petType,
                            page + 1));
        }

        ItemStack nextStepButton = new ItemStack(Material.GREEN_WOOL);
        ItemMeta nextMeta = nextStepButton.getItemMeta();
        nextMeta.setDisplayName(plugin.getLanguageManager().getString("menus.next_step"));
        String nextStatus = selectedPets.isEmpty() ? plugin.getLanguageManager().getString("menus.next_step_error")
                : plugin.getLanguageManager().getString("menus.next_step_success");
        nextMeta.setLore(plugin.getLanguageManager().getStringListReplacements("menus.next_step_lore",
                "count", String.valueOf(selectedPets.size()),
                "status", nextStatus));
        nextMeta.getPersistentDataContainer().set(BATCH_ACTION_KEY, PersistentDataType.STRING, "open_batch_manage");
        nextMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        nextStepButton.setItemMeta(nextMeta);
        gui.setItem(53, nextStepButton);

        player.openInventory(gui);
    }

    public Map<UUID, Set<UUID>> getPlayerSelections() {
        return playerSelections;
    }

    private ItemStack createTypeSelectionItem(EntityType type, long count) {
        Material mat = mainGui.getPetMaterial(type);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + formatEntityType(type));
            meta.setLore(plugin.getLanguageManager().getStringListReplacements("menus.type_lore", "count",
                    String.valueOf(count)));
            meta.getPersistentDataContainer().set(BATCH_ACTION_KEY, PersistentDataType.STRING, "select_pet_type");
            meta.getPersistentDataContainer().set(PET_TYPE_KEY, PersistentDataType.STRING, type.name());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPetSelectionItem(PetData petData, boolean isSelected, int currentPage) {
        Material mat = isSelected ? mainGui.getPetMaterial(petData.getEntityType()) : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = (petData.isFavorite() ? ChatColor.GOLD + "★ " : "") + ChatColor.AQUA
                    + petData.getDisplayName();
            meta.setDisplayName(displayName);

            List<String> lore = plugin.getLanguageManager().getStringListReplacements("menus.pet_selection_lore",
                    "status", (isSelected ? plugin.getLanguageManager().getString("menus.status_selected")
                            : plugin.getLanguageManager().getString("menus.status_not_selected")));
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(PetManagerGUI.PET_UUID_KEY, PersistentDataType.STRING,
                    petData.getPetUUID().toString());
            meta.getPersistentDataContainer().set(BATCH_ACTION_KEY, PersistentDataType.STRING, "toggle_pet_selection");
            meta.getPersistentDataContainer().set(PET_TYPE_KEY, PersistentDataType.STRING,
                    petData.getEntityType().name());

            meta.getPersistentDataContainer().set(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER, currentPage);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavButton(Material material, String name, String action, EntityType petType, int page) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(BATCH_ACTION_KEY, PersistentDataType.STRING, action);
        if (petType != null) {
            meta.getPersistentDataContainer().set(PET_TYPE_KEY, PersistentDataType.STRING, petType.name());
        }
        if (page != -1) {
            meta.getPersistentDataContainer().set(PetManagerGUI.PAGE_KEY, PersistentDataType.INTEGER, page);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSelectionButton(Material material, String name, String action, EntityType petType) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(BATCH_ACTION_KEY, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(PET_TYPE_KEY, PersistentDataType.STRING, petType.name());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
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

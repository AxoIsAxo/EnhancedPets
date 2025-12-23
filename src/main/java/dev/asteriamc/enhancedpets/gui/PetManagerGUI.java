package dev.asteriamc.enhancedpets.gui;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.BehaviorMode;
import dev.asteriamc.enhancedpets.data.CreeperBehavior;
import dev.asteriamc.enhancedpets.data.PetData;
import dev.asteriamc.enhancedpets.manager.DriedGhastEntry;
import dev.asteriamc.enhancedpets.manager.PetManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PetManagerGUI {
    public static final NamespacedKey PET_UUID_KEY = new NamespacedKey(Enhancedpets.getInstance(), "pet_uuid");
    public static final NamespacedKey ACTION_KEY = new NamespacedKey(Enhancedpets.getInstance(), "gui_action");
    public static final NamespacedKey TARGET_PLAYER_UUID_KEY = new NamespacedKey(Enhancedpets.getInstance(),
            "target_player_uuid");
    public static final String MAIN_MENU_TITLE = "menus.main_title";
    public static final String PET_MENU_TITLE_PREFIX = "menus.pet_title_prefix";
    public static final String FRIENDLY_MENU_TITLE_PREFIX = "menus.friendly_title_prefix";
    public static final NamespacedKey PAGE_KEY = new NamespacedKey(Enhancedpets.getInstance(), "gui_page");
    public static final NamespacedKey COLOR_KEY = new NamespacedKey(Enhancedpets.getInstance(), "display_color");
    private final Enhancedpets plugin;
    private final PetManager petManager;
    private final BatchActionsGUI batchActionsGUI;
    private final Map<UUID, UUID> viewerOwnerOverride = new ConcurrentHashMap<>();

    public PetManagerGUI(Enhancedpets plugin) {
        this.plugin = plugin;
        this.petManager = plugin.getPetManager();
        this.batchActionsGUI = new BatchActionsGUI(plugin, this);
    }

    private Component toComponent(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    private List<Component> toComponentList(List<String> texts) {
        return texts == null ? null : texts.stream().map(this::toComponent).collect(Collectors.toList());
    }

    public boolean canAttack(EntityType type) {
        return switch (type) {
            case SLIME, GHAST, MAGMA_CUBE, SHULKER, WOLF, SNOWMAN, IRON_GOLEM, POLAR_BEAR, LLAMA, PHANTOM, CAT,
                    TRADER_LLAMA, FOX, BEE, AXOLOTL ->
                true;
            default -> false;
        };
    }

    public boolean isRideable(EntityType type) {
        // Use string check for HAPPY_GHAST as it may not exist in older API versions
        if (type.name().equalsIgnoreCase("HAPPY_GHAST")) {
            return true;
        }
        return switch (type) {
            case SKELETON_HORSE, ZOMBIE_HORSE, DONKEY, MULE, PIG, HORSE, LLAMA, TRADER_LLAMA, STRIDER -> true;
            default -> false;
        };
    }

    private int[] getSymmetricalSlots(int itemCount, int rowStart) {
        return switch (itemCount) {
            case 1 -> new int[] { rowStart + 4 };
            case 2 -> new int[] { rowStart + 3, rowStart + 5 };
            case 3 -> new int[] { rowStart + 2, rowStart + 4, rowStart + 6 };
            case 4 -> new int[] { rowStart + 1, rowStart + 3, rowStart + 5, rowStart + 7 };
            case 5 -> new int[] { rowStart + 1, rowStart + 2, rowStart + 4, rowStart + 6, rowStart + 7 };
            case 6 -> new int[] { rowStart + 1, rowStart + 2, rowStart + 3, rowStart + 5, rowStart + 6, rowStart + 7 };
            case 7 -> new int[] { rowStart + 1, rowStart + 2, rowStart + 3, rowStart + 4, rowStart + 5, rowStart + 6,
                    rowStart + 7 };
            case 8 -> new int[] { rowStart, rowStart + 1, rowStart + 2, rowStart + 3, rowStart + 5, rowStart + 6,
                    rowStart + 7, rowStart + 8 };
            case 9 -> new int[] { rowStart, rowStart + 1, rowStart + 2, rowStart + 3, rowStart + 4, rowStart + 5,
                    rowStart + 6, rowStart + 7, rowStart + 8 };
            default -> new int[] { rowStart + 4 };
        };
    }

    public static Integer extractPetIdFromName(String name) {
        int lastHashIndex = name.lastIndexOf(" #");
        if (lastHashIndex != -1 && lastHashIndex < name.length() - 2) {
            String numberPart = name.substring(lastHashIndex + 2);

            try {
                return Integer.parseInt(numberPart);
            } catch (NumberFormatException var4) {
                return null;
            }
        } else {
            return null;
        }
    }

    public BatchActionsGUI getBatchActionsGUI() {
        return this.batchActionsGUI;
    }

    public void openMainMenu(Player player) {
        this.openMainMenu(player, 0);
    }

    public void setViewerOwnerOverride(UUID viewer, UUID owner) {
        if (viewer != null && owner != null) {
            this.viewerOwnerOverride.put(viewer, owner);
        }
    }

    public void clearViewerOwnerOverride(UUID viewer) {
        if (viewer != null) {
            this.viewerOwnerOverride.remove(viewer);
        }
    }

    public UUID getViewerOwnerOverride(UUID viewer) {
        return this.viewerOwnerOverride.get(viewer);
    }

    public UUID getEffectiveOwner(Player viewer) {
        UUID o = this.getViewerOwnerOverride(viewer.getUniqueId());
        return o != null ? o : viewer.getUniqueId();
    }

    public void openMainMenu(Player player, int page) {
        UUID effectiveOwner = this.getEffectiveOwner(player);

        // FIX: Check if pet data is still loading - wait before showing GUI
        // This prevents the "Schrödinger's Pet" bug where GUI shows empty during load
        if (this.petManager.isOwnerLoading(effectiveOwner)) {
            // Data is still loading, retry after a short delay
            final int retryPage = page;
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (player.isOnline()) {
                    this.openMainMenu(player, retryPage);
                }
            }, 5L); // Wait 5 ticks (0.25 seconds)
            return;
        }

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

        pets.sort(Comparator.comparing(PetData::isFavorite).reversed().thenComparing(p -> p.getEntityType().name())
                .thenComparing((p1, p2) -> {
                    String name1 = p1.getDisplayName();
                    String name2 = p2.getDisplayName();
                    Integer id1 = extractPetIdFromName(name1);
                    Integer id2 = extractPetIdFromName(name2);
                    return id1 != null && id2 != null ? Integer.compare(id1, id2)
                            : String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
                }));
        List<DriedGhastEntry> incubating = this.plugin.getDriedGhastTracker().getEntriesForPlayer(effectiveOwner);
        List<Object> allItems = new ArrayList<>();
        if (effectiveOwner.equals(player.getUniqueId())) {
            player.getNearbyEntities(10.0, 10.0, 10.0)
                    .stream()
                    .filter(e -> e instanceof LivingEntity)
                    .map(e -> (LivingEntity) e)
                    .filter(LivingEntity::isLeashed)
                    .filter(e -> Objects.equals(e.getLeashHolder(), player))
                    .filter(e -> !this.petManager.isManagedPet(e.getUniqueId()))
                    .filter(e -> !(e instanceof Tameable))
                    .sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                    .forEach(le -> allItems.add(this.createAdoptionItem(le)));
        }

        allItems.addAll(incubating);
        allItems.addAll(pets);
        int itemsPerPage = 45;
        int totalItems = allItems.size();
        if (totalItems <= itemsPerPage && page == 0) {
            int rows = (int) Math.ceil(totalItems / 9.0);
            int invSize = Math.max(27, (rows + 1) * 9);
            Inventory gui = Bukkit.createInventory(
                    new PetInventoryHolder(PetInventoryHolder.MenuType.MAIN_MENU), invSize,
                    this.plugin.getLanguageManager().getString("menus.main_title"));
            if (allItems.isEmpty()) {
                gui.setItem(
                        13,
                        this.createItem(
                                Material.BARRIER,
                                this.plugin.getLanguageManager().getString("menus.no_pets_name"),
                                this.plugin.getLanguageManager().getStringList("menus.no_pets_lore")));
            } else {
                for (int i = 0; i < allItems.size(); i++) {
                    Object itemObj = allItems.get(i);
                    if (itemObj instanceof PetData) {
                        gui.setItem(i, this.createPetItem((PetData) itemObj));
                    } else if (itemObj instanceof DriedGhastEntry) {
                        gui.setItem(i, this.createIncubatorItem((DriedGhastEntry) itemObj));
                    } else if (itemObj instanceof ItemStack is) {
                        gui.setItem(i, is);
                    }
                }

                if (effectiveOwner.equals(player.getUniqueId())) {
                    gui.setItem(
                            invSize - 8,
                            this.createActionButton(
                                    Material.COMPASS,
                                    this.plugin.getLanguageManager().getString("menus.scan_button_name"),
                                    "scan_for_pets",
                                    null,
                                    this.plugin.getLanguageManager().getStringList("menus.scan_button_lore")));
                }

                gui.setItem(
                        invSize - 5,
                        this.createItem(
                                Material.PAPER,
                                this.plugin.getLanguageManager().getStringReplacements("menus.page_count_name", "page",
                                        "1", "total", "1"),
                                this.plugin.getLanguageManager().getStringListReplacements("menus.page_count_lore",
                                        "count", String.valueOf(totalItems))));
                gui.setItem(
                        invSize - 2,
                        this.createActionButton(
                                Material.HOPPER,
                                this.plugin.getLanguageManager().getString("menus.batch_button_name"),
                                "batch_actions",
                                null,
                                this.plugin.getLanguageManager().getStringList("menus.batch_button_lore")));
            }

            player.openInventory(gui);
        } else {
            int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
            page = Math.max(0, Math.min(page, totalPages - 1));
            int invSize = 54;
            Inventory gui = Bukkit.createInventory(
                    new PetInventoryHolder(PetInventoryHolder.MenuType.MAIN_MENU), invSize,
                    this.plugin.getLanguageManager().getString("menus.main_title"));
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
            List<Object> itemsToShow = allItems.subList(startIndex, endIndex);

            for (int ix = 0; ix < itemsToShow.size(); ix++) {
                Object itemObj = itemsToShow.get(ix);
                if (itemObj instanceof PetData) {
                    gui.setItem(ix, this.createPetItem((PetData) itemObj));
                } else if (itemObj instanceof DriedGhastEntry) {
                    gui.setItem(ix, this.createIncubatorItem((DriedGhastEntry) itemObj));
                } else if (itemObj instanceof ItemStack is) {
                    gui.setItem(ix, is);
                }
            }

            if (page > 0) {
                gui.setItem(
                        invSize - 9,
                        this.createNavigationButton(Material.ARROW,
                                this.plugin.getLanguageManager().getString("menus.prev_page"), "main_page", page - 1,
                                null));
            }

            if (endIndex < totalItems) {
                gui.setItem(
                        invSize - 1,
                        this.createNavigationButton(Material.ARROW,
                                this.plugin.getLanguageManager().getString("menus.next_page"), "main_page", page + 1,
                                null));
            }

            if (effectiveOwner.equals(player.getUniqueId())) {
                gui.setItem(
                        invSize - 8,
                        this.createActionButton(
                                Material.COMPASS,
                                this.plugin.getLanguageManager().getString("menus.scan_button_name"),
                                "scan_for_pets",
                                null,
                                this.plugin.getLanguageManager().getStringList("menus.scan_button_lore")));
            }

            gui.setItem(
                    invSize - 5,
                    this.createItem(
                            Material.PAPER,
                            this.plugin
                                    .getLanguageManager()
                                    .getStringReplacements("menus.page_count_name", "page", String.valueOf(page + 1),
                                            "total", String.valueOf(totalPages)),
                            this.plugin.getLanguageManager().getStringListReplacements("menus.page_count_lore", "count",
                                    String.valueOf(totalItems))));
            gui.setItem(
                    invSize - 2,
                    this.createActionButton(
                            Material.HOPPER,
                            this.plugin.getLanguageManager().getString("menus.batch_button_name"),
                            "batch_actions",
                            null,
                            this.plugin.getLanguageManager().getStringList("menus.batch_button_lore")));
            player.openInventory(gui);
        }
    }

    private ItemStack createAdoptionItem(LivingEntity entity) {
        Material eggMat = this.getPetMaterial(entity.getType());
        if (eggMat == Material.NAME_TAG) {
            eggMat = Material.LEAD;
        }

        ItemStack item = new ItemStack(eggMat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.toComponent(
                this.plugin.getLanguageManager().getStringReplacements("menus.adoption_offer.title", "type",
                        entity.getType().name())));
        List<String> lore = new ArrayList<>();
        lore.add(this.plugin.getLanguageManager().getString("menus.adoption_offer.status"));
        lore.addAll(this.plugin.getLanguageManager().getStringList("menus.adoption_offer.click_lore"));
        lore.add(this.plugin.getLanguageManager().getStringReplacements("menus.adoption_offer.uuid", "uuid",
                entity.getUniqueId().toString().substring(0, 8)));
        meta.lore(this.toComponentList(lore));
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ENCHANTS });
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(ACTION_KEY, PersistentDataType.STRING, "adopt_leashed");
        data.set(PET_UUID_KEY, PersistentDataType.STRING, entity.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createIncubatorItem(DriedGhastEntry entry) {
        ItemStack item = new ItemStack(Material.GHAST_TEAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.toComponent(this.plugin.getLanguageManager().getString("menus.incubator.title")));
        Block block = entry.getLocation().getBlock();
        boolean isWaterlogged = false;
        boolean blockExists = block.getType().name().equalsIgnoreCase("DRIED_GHAST");
        BlockData var8;
        if (blockExists && (var8 = block.getBlockData()) instanceof Waterlogged) {
            Waterlogged waterloggedData = (Waterlogged) var8;
            isWaterlogged = waterloggedData.isWaterlogged();
        }

        List<String> lore = new ArrayList<>();
        if (!blockExists) {
            lore.add(this.plugin.getLanguageManager().getString("menus.incubator.block_not_found"));
            lore.add(this.plugin.getLanguageManager().getString("menus.incubator.block_not_found_lore"));
        } else {
            if (isWaterlogged) {
                lore.add(this.plugin.getLanguageManager().getString("menus.incubator.waterlogged_yes"));
            } else {
                lore.add(this.plugin.getLanguageManager().getString("menus.incubator.waterlogged_no"));
                lore.add(this.plugin.getLanguageManager().getString("menus.incubator.waterlogged_warn"));
            }

            lore.add(
                    this.plugin
                            .getLanguageManager()
                            .getStringReplacements(
                                    "menus.incubator.location",
                                    "world",
                                    entry.getLocation().getWorld().getName(),
                                    "x",
                                    String.valueOf(entry.getLocation().getBlockX()),
                                    "y",
                                    String.valueOf(entry.getLocation().getBlockY()),
                                    "z",
                                    String.valueOf(entry.getLocation().getBlockZ())));
            lore.add("");
            int randomTickSpeed = 3;

            try {
                Integer rts = (Integer) entry.getLocation().getWorld().getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
                if (rts != null && rts > 0) {
                    randomTickSpeed = rts;
                }
            } catch (Exception var25) {
            }

            long baseTimeMs = 1200000L;
            long scaledTotalTime = (long) (baseTimeMs * (3.0 / randomTickSpeed));
            long minTimeRemaining = 60000L;
            if (!isWaterlogged) {
                lore.add(this.plugin.getLanguageManager().getString("menus.incubator.time_not_incubating"));
                lore.add(this.plugin.getLanguageManager().getString("menus.incubator.add_water"));
            } else {
                long waterloggedTime = entry.getWaterloggedDuration();
                long remaining = Math.max(minTimeRemaining, scaledTotalTime - waterloggedTime);
                long minutes = remaining / 1000L / 60L;
                long seconds = remaining / 1000L % 60L;
                if (remaining <= minTimeRemaining) {
                    lore.add(this.plugin.getLanguageManager().getString("menus.incubator.time_soon"));
                    lore.add(this.plugin.getLanguageManager().getString("menus.incubator.hatching_soon"));
                } else {
                    lore.add(
                            this.plugin.getLanguageManager().getStringReplacements("menus.incubator.est_time", "min",
                                    String.valueOf(minutes), "sec", String.valueOf(seconds)));
                }

                if (randomTickSpeed != 3) {
                    lore.add(this.plugin.getLanguageManager().getStringReplacements("menus.incubator.tick_speed",
                            "speed",
                            String.valueOf(randomTickSpeed)));
                }
            }
        }

        meta.lore(this.toComponentList(lore));
        item.setItemMeta(meta);
        return item;
    }

    public void openBatchManagementMenu(Player player, Set<UUID> selectedPetUUIDs) {
        if (selectedPetUUIDs != null && !selectedPetUUIDs.isEmpty()) {
            List<PetData> selectedPets = selectedPetUUIDs.stream().map(this.petManager::getPetData)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            String title = this.plugin.getLanguageManager().getStringReplacements("menus.batch_title", "count",
                    String.valueOf(selectedPets.size()));
            Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_MANAGE), 54,
                    title);
            long favoriteCount = selectedPets.stream().filter(PetData::isFavorite).count();
            boolean anyFavorites = favoriteCount > 0L;
            BehaviorMode commonMode = selectedPets.stream().map(PetData::getMode).distinct().count() == 1L
                    ? selectedPets.get(0).getMode()
                    : BehaviorMode.BATCH;
            List<Entity> petEntities = selectedPets.stream().map(p -> Bukkit.getEntity(p.getPetUUID()))
                    .filter(Objects::nonNull).toList();
            boolean allCanSit = !petEntities.isEmpty() && petEntities.stream().allMatch(e -> e instanceof Sittable);
            long sittingCount = petEntities.stream().filter(e -> e instanceof Sittable s && s.isSitting()).count();
            boolean allSitting = allCanSit && sittingCount == petEntities.size();
            boolean anySitting = allCanSit && sittingCount > 0L;
            long protectedCount = selectedPets.stream().filter(PetData::isProtectedFromPlayers).count();
            boolean allProtected = protectedCount == selectedPets.size();
            boolean anyProtected = protectedCount > 0L;
            PetData batchData = new PetData(null, player.getUniqueId(), null, "Batch");
            batchData.setMode(commonMode);
            boolean allMob = selectedPets.stream().allMatch(p -> p.getAggressiveTargetTypes().contains("MOB"));
            boolean allAnimal = selectedPets.stream().allMatch(p -> p.getAggressiveTargetTypes().contains("ANIMAL"));
            boolean allPlayer = selectedPets.stream().allMatch(p -> p.getAggressiveTargetTypes().contains("PLAYER"));
            Set<String> batchTypes = new HashSet<>();
            if (allMob) {
                batchTypes.add("MOB");
            }

            if (allAnimal) {
                batchTypes.add("ANIMAL");
            }

            if (allPlayer) {
                batchTypes.add("PLAYER");
            }

            batchData.setAggressiveTargetTypes(batchTypes);
            String favoriteDisplayName = (anyFavorites ? this.plugin.getLanguageManager().getString("misc.star_symbol")
                    : "")
                    + this.plugin.getLanguageManager().getStringReplacements("menus.managing_header", "count",
                            String.valueOf(selectedPets.size()));
            gui.setItem(
                    4,
                    this.createActionButton(
                            Material.HOPPER,
                            favoriteDisplayName,
                            "batch_toggle_favorite",
                            null,
                            this.plugin.getLanguageManager().getStringListReplacements("menus.managing_header_lore",
                                    "count", String.valueOf(selectedPets.size()))));
            List<UUID> babyUUIDs = selectedPetUUIDs.stream()
                    .<Entity>map(Bukkit::getEntity)
                    .filter(e -> e instanceof Ageable a && !a.isAdult())
                    .<UUID>map(Entity::getUniqueId)
                    .toList();
            List<ItemStack> actionItems = new ArrayList<>();
            if (allCanSit) {
                String sitStandName = allSitting
                        ? this.plugin.getLanguageManager().getString("menus.make_stand")
                        : this.plugin.getLanguageManager().getString("menus.make_sit");
                String sitStandStatus = allSitting
                        ? this.plugin.getLanguageManager().getString("menus.all_sitting")
                        : (anySitting ? this.plugin.getLanguageManager().getString("menus.mixed")
                                : this.plugin.getLanguageManager().getString("menus.all_standing"));
                actionItems.add(
                        this.createActionButton(
                                allSitting ? Material.ARMOR_STAND : Material.SADDLE,
                                sitStandName,
                                "batch_toggle_sit",
                                null,
                                this.plugin.getLanguageManager().getStringListReplacements("menus.sit_status_lore",
                                        "status", sitStandStatus)));
            }

            actionItems.add(
                    this.createActionButton(
                            Material.ENDER_PEARL,
                            this.plugin.getLanguageManager().getString("menus.teleport_pets"),
                            "batch_teleport",
                            null,
                            this.plugin.getLanguageManager().getStringList("menus.teleport_pets_lore")));
            boolean anyCanAttack = selectedPets.stream().anyMatch(p -> this.canAttack(p.getEntityType()));
            if (anyCanAttack) {
                actionItems.add(
                        this.createActionButton(
                                Material.MILK_BUCKET,
                                this.plugin.getLanguageManager().getString("menus.calm_pets"),
                                "batch_calm",
                                null,
                                this.plugin.getLanguageManager().getStringList("menus.calm_pets_lore")));
            }

            if (anyCanAttack) {
                boolean anyHasTarget = selectedPets.stream().anyMatch(p -> p.getExplicitTargetUUID() != null);
                boolean allHasTarget = selectedPets.stream().allMatch(p -> p.getExplicitTargetUUID() != null);
                String commonTargetName = null;
                UUID firstTarget = null;
                boolean allSameTarget = true;

                for (PetData pd : selectedPets) {
                    if (pd.getExplicitTargetUUID() == null) {
                        allSameTarget = false;
                        break;
                    }

                    if (firstTarget == null) {
                        firstTarget = pd.getExplicitTargetUUID();
                    } else if (!firstTarget.equals(pd.getExplicitTargetUUID())) {
                        allSameTarget = false;
                        break;
                    }
                }

                if (allSameTarget && firstTarget != null) {
                    final UUID targetToCheck = firstTarget;
                    Entity e = Bukkit.getEntity(firstTarget);
                    if (e != null) {
                        commonTargetName = e.getName();
                    } else {
                        // Entity is unloaded - try to get name from stored PetData first
                        String storedName = selectedPets.stream()
                                .filter(pd -> pd.getExplicitTargetUUID() != null
                                        && pd.getExplicitTargetUUID().equals(targetToCheck))
                                .map(PetData::getExplicitTargetName)
                                .filter(n -> n != null && !n.isEmpty())
                                .findFirst()
                                .orElse(null);

                        if (storedName != null) {
                            commonTargetName = storedName
                                    + this.plugin.getLanguageManager().getString("misc.unloaded_suffix");
                        } else {
                            // Fallback: check if it's a player
                            OfflinePlayer op = Bukkit.getOfflinePlayer(firstTarget);
                            if (op.hasPlayedBefore() || op.isOnline()) {
                                commonTargetName = op.getName() != null ? op.getName()
                                        : this.plugin.getLanguageManager().getString("misc.unknown");
                            } else {
                                commonTargetName = this.plugin.getLanguageManager().getString("misc.unknown_unloaded");
                            }
                        }
                    }
                }

                if (anyHasTarget) {
                    List<String> lore = new ArrayList<>();
                    if (allHasTarget) {
                        lore.add(this.plugin.getLanguageManager().getString("gui.batch_all_active_targets"));
                    } else {
                        lore.add(this.plugin.getLanguageManager().getString("gui.batch_active_targets"));
                    }

                    if (commonTargetName != null) {
                        lore.add(this.plugin.getLanguageManager().getStringReplacements("gui.batch_common_target",
                                "target", commonTargetName));
                    }

                    lore.add("");
                    lore.add(this.plugin.getLanguageManager().getString("menus.batch_clear_targets_action"));
                    lore.add(this.plugin.getLanguageManager().getString("menus.batch_set_targets_action"));
                    actionItems.add(
                            this.createActionButton(
                                    Material.CROSSBOW,
                                    this.plugin.getLanguageManager().getString("menus.manage_targets"),
                                    "batch_target_logic", null, lore));
                } else {
                    actionItems.add(
                            this.createActionButton(
                                    Material.CROSSBOW,
                                    this.plugin.getLanguageManager().getString("menus.set_target"),
                                    "batch_toggle_target_prompt",
                                    null,
                                    this.plugin.getLanguageManager().getStringList("menus.target_click_lore")));
                }
            }

            actionItems.add(
                    this.createActionButton(
                            Material.GOLDEN_APPLE,
                            this.plugin.getLanguageManager().getString("menus.batch_heal_title"),
                            "batch_heal",
                            null,
                            this.plugin
                                    .getLanguageManager()
                                    .getStringListReplacements("menus.batch_heal_lore", "cost",
                                            String.valueOf(this.plugin.getConfigManager().getHealCost()))));
            if (!babyUUIDs.isEmpty()) {
                long pausedCount = babyUUIDs.stream().map(this.petManager::getPetData).filter(Objects::nonNull)
                        .filter(PetData::isGrowthPaused).count();
                int totalBabies = babyUUIDs.size();
                String status;
                if (pausedCount == totalBabies) {
                    status = this.plugin.getLanguageManager().getString("menus.all_paused");
                } else if (pausedCount > 0L) {
                    status = this.plugin.getLanguageManager().getString("menus.mixed");
                } else {
                    status = this.plugin.getLanguageManager().getString("menus.all_growing");
                }

                Material mat = pausedCount == totalBabies ? Material.GOLDEN_CARROT : Material.CARROT;
                actionItems.add(
                        this.createActionButton(
                                mat,
                                this.plugin.getLanguageManager().getStringReplacements("menus.toggle_growth_baby_name",
                                        "color", pausedCount == totalBabies ? "&a" : "&e"),
                                "batch_toggle_growth_pause",
                                null,
                                this.plugin.getLanguageManager()
                                        .getStringListReplacements("menus.toggle_growth_baby_lore", "status", status)));
            }

            int[] actionSlots;
            if (actionItems.size() >= 6) {
                if (actionItems.size() == 6) {
                    actionSlots = new int[] { 19, 20, 21, 23, 24, 25 };
                } else {
                    actionSlots = new int[] { 19, 20, 21, 22, 23, 24, 25 };
                }
            } else if (actionItems.size() == 5) {
                actionSlots = new int[] { 19, 20, 22, 24, 25 };
            } else if (actionItems.size() == 4) {
                actionSlots = new int[] { 19, 21, 23, 25 };
            } else if (actionItems.size() == 3) {
                actionSlots = new int[] { 20, 22, 24 };
            } else {
                actionSlots = new int[] { 21, 23 };
            }

            for (int i = 0; i < actionItems.size(); i++) {
                if (i < actionSlots.length) {
                    gui.setItem(actionSlots[i], actionItems.get(i));
                }
            }

            if (anyCanAttack) {
                gui.setItem(
                        11,
                        this.createModeButton(Material.FEATHER,
                                this.plugin.getLanguageManager().getString("menus.set_passive"), BehaviorMode.PASSIVE,
                                batchData));
                gui.setItem(
                        13,
                        this.createModeButton(Material.IRON_SWORD,
                                this.plugin.getLanguageManager().getString("menus.set_neutral"), BehaviorMode.NEUTRAL,
                                batchData));
                gui.setItem(
                        15,
                        this.createModeButton(
                                Material.DIAMOND_SWORD,
                                this.plugin.getLanguageManager().getString("menus.set_aggressive"),
                                BehaviorMode.AGGRESSIVE, batchData));
            }

            String protName;
            if (anyCanAttack) {
                protName = allProtected
                        ? this.plugin.getLanguageManager().getString("menus.disable_protection")
                        : this.plugin.getLanguageManager().getString("menus.enable_protection");
            } else {
                protName = allProtected
                        ? this.plugin.getLanguageManager().getString("menus.disable_protection")
                        : this.plugin.getLanguageManager().getString("menus.enable_protection");
            }

            String protStatus = allProtected
                    ? this.plugin.getLanguageManager().getString("menus.all_protected")
                    : (anyProtected ? this.plugin.getLanguageManager().getString("menus.mixed")
                            : this.plugin.getLanguageManager().getString("menus.all_unprotected"));
            gui.setItem(
                    29,
                    this.createActionButton(
                            Material.SHIELD,
                            protName,
                            "batch_toggle_protection",
                            null,
                            this.plugin.getLanguageManager().getStringListReplacements("menus.protection_lore",
                                    "status", protStatus)));

            // Only show manage friendly/trusted riders for applicable pet types
            boolean anyRideable = selectedPets.stream().anyMatch(p -> this.isRideable(p.getEntityType()));
            if (anyRideable) {
                // Show "Manage Trusted Riders" for rideable pets
                gui.setItem(
                        31,
                        this.createActionButton(
                                Material.PLAYER_HEAD,
                                this.plugin.getLanguageManager().getString("menus.manage_trusted_riders"),
                                "batch_manage_friendly",
                                null,
                                this.plugin.getLanguageManager().getStringList("menus.manage_trusted_riders_lore")));
            } else if (anyCanAttack) {
                // Show "Manage Friendly Players" for combat pets
                gui.setItem(
                        31,
                        this.createActionButton(
                                Material.PLAYER_HEAD,
                                this.plugin.getLanguageManager().getString("menus.manage_friendly"),
                                "batch_manage_friendly",
                                null,
                                this.plugin.getLanguageManager().getStringList("menus.manage_friendly_lore")));
            }
            // If neither rideable nor combat, slot 31 stays empty
            gui.setItem(
                    33,
                    this.createActionButton(
                            Material.LEAD,
                            this.plugin.getLanguageManager().getString("menus.transfer_pets"),
                            "batch_open_transfer",
                            null,
                            this.plugin.getLanguageManager().getStringList("menus.transfer_pets_lore")));
            EntityType type = selectedPets.get(0).getEntityType();
            ItemStack backButton = new ItemStack(Material.ARROW);
            ItemMeta meta = backButton.getItemMeta();
            meta.displayName(this.toComponent(this.plugin.getLanguageManager().getString("menus.back_selection")));
            meta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING,
                    "open_pet_select");
            meta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING, type.name());
            backButton.setItemMeta(meta);
            gui.setItem(45, backButton);
            gui.setItem(
                    53,
                    this.createActionButton(
                            Material.BARRIER,
                            this.plugin.getLanguageManager().getString("menus.free_selected"),
                            "batch_free_pet_prompt",
                            null,
                            this.plugin.getLanguageManager().getStringList("menus.free_selected_lore")));
            player.openInventory(gui);
        } else {
            this.plugin.getLanguageManager().sendMessage(player, "gui.batch_no_selection");
            this.openMainMenu(player);
        }
    }

    public void openBatchConfirmFreeMenu(Player player, Set<UUID> selectedPetUUIDs) {
        String title = this.plugin.getLanguageManager().getStringReplacements("menus.confirm_free_title", "count",
                String.valueOf(selectedPetUUIDs.size()));
        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_CONFIRM_FREE),
                27, title);
        gui.setItem(
                13,
                this.createItem(
                        Material.HOPPER,
                        this.plugin.getLanguageManager().getStringReplacements("menus.free_info_name", "count",
                                String.valueOf(selectedPetUUIDs.size())),
                        this.plugin.getLanguageManager().getStringList("menus.free_info_lore")));
        gui.setItem(
                11,
                this.createActionButton(
                        Material.LIME_WOOL,
                        this.plugin.getLanguageManager().getString("menus.cancel_free"),
                        "open_batch_manage",
                        null,
                        this.plugin.getLanguageManager().getStringList("menus.cancel_free_lore")));
        gui.setItem(
                15,
                this.createActionButton(
                        Material.RED_WOOL,
                        this.plugin.getLanguageManager().getString("menus.confirm_free_btn"),
                        "batch_confirm_free",
                        null,
                        this.plugin.getLanguageManager().getStringList("menus.confirm_free_btn_lore")));
        player.openInventory(gui);
    }

    public void openBatchFriendlyPlayerMenu(Player player, Set<UUID> selectedPetUUIDs, int page) {
        String title = this.plugin.getLanguageManager().getStringReplacements("menus.batch_friendly_title", "count",
                String.valueOf(selectedPetUUIDs.size()));
        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_FRIENDLY), 54,
                title);
        List<PetData> pets = selectedPetUUIDs.stream().map(this.petManager::getPetData).filter(Objects::nonNull)
                .toList();
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
            ItemStack head = this.createPlayerHead(
                    friendlyUUID,
                    ChatColor.YELLOW + friendly.getName(),
                    "remove_batch_friendly",
                    null,
                    this.plugin.getLanguageManager().getStringList("menus.remove_friendly_lore"));
            gui.setItem(i - startIndex, head);
        }

        if (page > 0) {
            gui.setItem(
                    45,
                    this.createActionButton(Material.ARROW,
                            this.plugin.getLanguageManager().getString("menus.prev_page"), "batch_friendly_page", null,
                            null));
        }

        gui.setItem(
                48,
                this.createActionButton(
                        Material.ANVIL,
                        this.plugin.getLanguageManager().getString("menus.add_friendly"),
                        "add_batch_friendly_prompt",
                        null,
                        this.plugin.getLanguageManager().getStringList("menus.add_friendly_lore")));
        if (endIndex < friendlyList.size()) {
            gui.setItem(
                    50,
                    this.createActionButton(Material.ARROW,
                            this.plugin.getLanguageManager().getString("menus.next_page"), "batch_friendly_page", null,
                            null));
        }

        gui.setItem(53, this.createActionButton(Material.ARROW,
                this.plugin.getLanguageManager().getString("menus.back_batch"), "open_batch_manage", null, null));
        player.openInventory(gui);
    }

    public void openBatchTransferMenu(Player player, Set<UUID> selectedPetUUIDs) {
        String title = this.plugin.getLanguageManager().getStringReplacements("menus.transfer_title", "count",
                String.valueOf(selectedPetUUIDs.size()));
        List<Player> eligiblePlayers = Bukkit.getOnlinePlayers()
                .stream()
                .filter(p -> !p.getUniqueId().equals(this.getEffectiveOwner(player)))
                .map(p -> (Player) p)
                .sorted(Comparator.comparing(HumanEntity::getName))
                .toList();
        int rows = (int) Math.ceil(eligiblePlayers.size() / 9.0);
        int invSize = Math.max(18, (rows + 1) * 9);
        if (eligiblePlayers.isEmpty()) {
            invSize = 27;
        }

        invSize = Math.min(54, invSize);
        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_TRANSFER),
                invSize, title);
        if (eligiblePlayers.isEmpty()) {
            gui.setItem(
                    invSize == 27 ? 13 : 22,
                    this.createItem(
                            Material.BARRIER,
                            this.plugin.getLanguageManager().getString("menus.no_players"),
                            this.plugin.getLanguageManager().getStringList("menus.no_players_lore")));
        } else {
            for (int i = 0; i < eligiblePlayers.size() && i < 45; i++) {
                Player target = eligiblePlayers.get(i);
                ItemStack head = this.createPlayerHead(
                        target.getUniqueId(),
                        ChatColor.YELLOW + target.getName(),
                        "batch_transfer_to_player",
                        null,
                        this.plugin
                                .getLanguageManager()
                                .getStringListReplacements("menus.transfer_player_lore", "count",
                                        String.valueOf(selectedPetUUIDs.size()), "player", target.getName()));
                gui.setItem(i, head);
            }
        }

        gui.setItem(
                invSize - 1,
                this.createActionButton(Material.ARROW, this.plugin.getLanguageManager().getString("menus.back_batch"),
                        "open_batch_manage", null, null));
        player.openInventory(gui);
    }

    public void openPetMenu(Player player, UUID petUUID) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            this.plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
        } else {
            String title = this.plugin.getLanguageManager().getStringReplacements("menus.pet_title", "name",
                    petData.getDisplayName());
            if (title.length() > 32) {
                title = title.substring(0, 29) + "...";
            }

            Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.PET_MENU), 54,
                    title);
            boolean isFavorite = petData.isFavorite();
            boolean protectionEnabled = petData.isProtectedFromPlayers();
            ChatColor nameColor = this.getNameColor(petData);
            Material headerIcon = this.getDisplayMaterialForPet(petData);
            if (petData.isDead()) {
                gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.PET_MENU), 27, title);
                ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                meta.displayName(
                        this.toComponent(this.plugin.getLanguageManager().getStringReplacements("menus.dead_pet_name",
                                "name", petData.getDisplayName())));
                meta.lore(this.toComponentList(
                        this.plugin
                                .getLanguageManager()
                                .getStringListReplacements(
                                        "menus.dead_pet_lore",
                                        "item",
                                        this.plugin.getConfigManager().getReviveItem().name(),
                                        "amount",
                                        String.valueOf(this.plugin.getConfigManager().getReviveItemAmount()))));
                skull.setItemMeta(meta);
                gui.setItem(13, skull);
                gui.setItem(
                        11,
                        this.createActionButton(
                                this.plugin.getConfigManager().getReviveItem(),
                                this.plugin.getLanguageManager().getString("menus.revive_pet"),
                                "confirm_revive_pet",
                                petUUID,
                                this.plugin
                                        .getLanguageManager()
                                        .getStringListReplacements(
                                                "menus.revive_pet_lore",
                                                "item",
                                                this.plugin.getConfigManager().getReviveItem().name(),
                                                "amount",
                                                String.valueOf(this.plugin.getConfigManager().getReviveItemAmount()))));
                gui.setItem(
                        15,
                        this.createActionButton(
                                Material.BARRIER,
                                this.plugin.getLanguageManager().getString("menus.remove_pet"),
                                "confirm_remove_pet",
                                petUUID,
                                this.plugin.getLanguageManager().getStringList("menus.remove_pet_lore")));
                player.openInventory(gui);
            } else {
                Entity petEntity = Bukkit.getEntity(petUUID);
                List<String> headerLore = new ArrayList<>();
                headerLore.addAll(this.plugin.getLanguageManager().getStringListReplacements(
                        "menus.pet_item_lore_basic", "type", this.formatEntityType(petData.getEntityType())));
                headerLore.addAll(this.plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_mode",
                        "mode", petData.getMode().name()));
                if (petEntity instanceof LivingEntity livingEntity && petEntity.isValid()) {
                    double health = livingEntity.getHealth();
                    double maxHealth = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    headerLore.addAll(
                            this.plugin
                                    .getLanguageManager()
                                    .getStringListReplacements(
                                            "menus.header_lore_alive", "health", String.format("%.1f", health),
                                            "max_health", String.format("%.1f", maxHealth)));
                } else {
                    headerLore.addAll(this.plugin.getLanguageManager().getStringList("menus.header_lore_dead_state"));
                }

                headerLore.add(
                        this.plugin
                                .getLanguageManager()
                                .getStringReplacements(
                                        "menus.header_protection",
                                        "status",
                                        protectionEnabled
                                                ? this.plugin.getLanguageManager().getString("menus.enabled")
                                                : this.plugin.getLanguageManager().getString("menus.disabled")));
                int friendlyCount = petData.getFriendlyPlayers().size();
                if (friendlyCount > 0) {
                    headerLore.add(this.plugin.getLanguageManager().getStringReplacements("menus.header_friendly_count",
                            "count", String.valueOf(friendlyCount)));
                }

                if (petEntity instanceof Ageable ageable && !ageable.isAdult()) {
                    headerLore.add(this.plugin.getLanguageManager().getString("menus.header_baby"));
                }

                // Breed cooldown display for adult animals
                if (petEntity instanceof Ageable ageable && ageable.isAdult() && ageable.getAge() > 0) {
                    int ticksRemaining = ageable.getAge();
                    int secondsRemaining = ticksRemaining / 20;
                    int minutes = secondsRemaining / 60;
                    int seconds = secondsRemaining % 60;
                    String timeFormatted = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
                    headerLore.add(this.plugin.getLanguageManager().getStringReplacements(
                            "menus.header_breed_cooldown", "time", timeFormatted));
                }

                headerLore.add("");
                headerLore.addAll(this.plugin.getLanguageManager().getStringList("menus.header_instructions"));
                ItemStack header = new ItemStack(headerIcon);
                ItemMeta hMeta = header.getItemMeta();
                String favoriteStar = isFavorite ? ChatColor.GOLD + "★ " : "";
                hMeta.displayName(this.toComponent(favoriteStar + nameColor + petData.getDisplayName()));
                hMeta.lore(this.toComponentList(headerLore));
                hMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "pet_header");
                hMeta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
                hMeta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ATTRIBUTES });
                header.setItemMeta(hMeta);
                gui.setItem(4, header);
                Entity petEnt = Bukkit.getEntity(petUUID);
                if (petEnt instanceof Ageable a && !a.isAdult()) {
                    boolean paused = petData.isGrowthPaused();
                    gui.setItem(
                            8,
                            this.createActionButton(
                                    paused ? Material.GOLDEN_CARROT : Material.CARROT,
                                    this.plugin.getLanguageManager().getStringReplacements("menus.toggle_growth_name",
                                            "color", paused ? "&a" : "&e"),
                                    "toggle_growth_pause",
                                    petUUID,
                                    this.plugin
                                            .getLanguageManager()
                                            .getStringListReplacements(
                                                    "menus.toggle_growth_lore",
                                                    "status",
                                                    paused ? this.plugin.getLanguageManager().getString("menus.paused")
                                                            : this.plugin.getLanguageManager()
                                                                    .getString("menus.growing"))));
                }

                boolean canAttack = this.canAttack(petData.getEntityType());
                boolean isRideable = this.isRideable(petData.getEntityType());
                boolean canSit = petEntity instanceof Sittable;
                int guiSize = canAttack ? 54 : 45;
                gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.PET_MENU), guiSize,
                        title);
                gui.setItem(4, header);
                if (petEnt instanceof Ageable a && !a.isAdult()) {
                    boolean paused = petData.isGrowthPaused();
                    gui.setItem(
                            8,
                            this.createActionButton(
                                    paused ? Material.GOLDEN_CARROT : Material.CARROT,
                                    this.plugin.getLanguageManager().getStringReplacements("menus.toggle_growth_name",
                                            "color", paused ? "&a" : "&e"),
                                    "toggle_growth_pause",
                                    petUUID,
                                    this.plugin
                                            .getLanguageManager()
                                            .getStringListReplacements(
                                                    "menus.toggle_growth_lore",
                                                    "status",
                                                    paused ? this.plugin.getLanguageManager().getString("menus.paused")
                                                            : this.plugin.getLanguageManager()
                                                                    .getString("menus.growing"))));
                }

                if (canAttack) {
                    gui.setItem(
                            11,
                            this.createModeButton(Material.FEATHER,
                                    this.plugin.getLanguageManager().getString("menus.set_passive"),
                                    BehaviorMode.PASSIVE, petData));
                    gui.setItem(
                            13,
                            this.createModeButton(Material.IRON_SWORD,
                                    this.plugin.getLanguageManager().getString("menus.set_neutral"),
                                    BehaviorMode.NEUTRAL, petData));
                    gui.setItem(
                            15,
                            this.createModeButton(
                                    Material.DIAMOND_SWORD,
                                    this.plugin.getLanguageManager().getString("menus.set_aggressive"),
                                    BehaviorMode.AGGRESSIVE, petData));
                }

                List<ItemStack> row3Items = new ArrayList<>();
                if (canSit) {
                    boolean isSitting = false;
                    if (petEntity instanceof Sittable s) {
                        isSitting = s.isSitting();
                    }

                    row3Items.add(
                            this.createActionButton(
                                    isSitting ? Material.ARMOR_STAND : Material.SADDLE,
                                    isSitting ? this.plugin.getLanguageManager().getString("menus.make_stand")
                                            : this.plugin.getLanguageManager().getString("menus.make_sit"),
                                    "toggle_sit",
                                    petData.getPetUUID(),
                                    this.plugin
                                            .getLanguageManager()
                                            .getStringListReplacements(
                                                    "menus.sit_status_lore",
                                                    "status",
                                                    isSitting
                                                            ? this.plugin.getLanguageManager()
                                                                    .getString("menus.sitting")
                                                            : this.plugin.getLanguageManager()
                                                                    .getString("menus.standing"))));
                }

                row3Items.add(
                        this.createActionButton(
                                Material.ENDER_PEARL,
                                this.plugin.getLanguageManager().getString("menus.teleport_pet"),
                                "teleport_pet",
                                petData.getPetUUID(),
                                this.plugin.getLanguageManager().getStringList("menus.teleport_pet_lore")));
                if (canAttack) {
                    row3Items.add(
                            this.createActionButton(
                                    Material.MILK_BUCKET,
                                    this.plugin.getLanguageManager().getString("menus.calm_pet"),
                                    "calm_pet",
                                    petData.getPetUUID(),
                                    this.plugin.getLanguageManager().getStringList("menus.calm_pet_lore")));
                }

                int[] row3Slots = this.getSymmetricalSlots(row3Items.size(), 18);

                for (int i = 0; i < row3Items.size(); i++) {
                    gui.setItem(row3Slots[i], row3Items.get(i));
                }

                List<ItemStack> row4Items = new ArrayList<>();
                row4Items.add(
                        this.createActionButton(
                                Material.ANVIL,
                                this.plugin.getLanguageManager().getString("menus.rename_pet"),
                                "rename_pet_prompt",
                                petData.getPetUUID(),
                                this.plugin.getLanguageManager().getStringList("menus.rename_pet_lore")));
                if (isRideable) {
                    row4Items.add(
                            this.createActionButton(
                                    Material.PLAYER_HEAD,
                                    this.plugin.getLanguageManager().getString("menus.manage_trusted_riders"),
                                    "manage_friendly",
                                    petData.getPetUUID(),
                                    this.plugin.getLanguageManager()
                                            .getStringList("menus.manage_trusted_riders_lore")));
                } else if (canAttack) {
                    row4Items.add(
                            this.createActionButton(
                                    Material.PLAYER_HEAD,
                                    this.plugin.getLanguageManager().getString("menus.manage_friendly"),
                                    "manage_friendly",
                                    petData.getPetUUID(),
                                    this.plugin.getLanguageManager()
                                            .getStringList("menus.manage_friendly_lore_single")));
                }

                row4Items.add(
                        this.createActionButton(
                                Material.LEAD,
                                this.plugin.getLanguageManager().getString("menus.transfer_pet"),
                                "open_transfer",
                                petData.getPetUUID(),
                                this.plugin.getLanguageManager().getStringList("menus.transfer_pet_lore")));
                String protName = protectionEnabled
                        ? this.plugin.getLanguageManager().getString("menus.disable_protection")
                        : this.plugin.getLanguageManager().getString("menus.enable_protection");
                List<String> protLore = new ArrayList<>();
                protLore.add(
                        this.plugin
                                .getLanguageManager()
                                .getStringReplacements(
                                        "menus.protection_status",
                                        "status",
                                        protectionEnabled
                                                ? this.plugin.getLanguageManager().getString("menus.enabled")
                                                : this.plugin.getLanguageManager().getString("menus.disabled")));
                protLore.add("");
                protLore.addAll(this.plugin.getLanguageManager().getStringList("menus.protection_desc"));
                row4Items.add(this.createActionButton(Material.SHIELD, protName, "toggle_mutual_protection",
                        petData.getPetUUID(), protLore));
                int[] row4Slots = this.getSymmetricalSlots(row4Items.size(), 27);

                for (int i = 0; i < row4Items.size(); i++) {
                    gui.setItem(row4Slots[i], row4Items.get(i));
                }

                if (canAttack) {
                    List<ItemStack> row5Items = new ArrayList<>();
                    boolean isStationed = petData.getStationLocation() != null;
                    String currentRadius = String.format("%.0fm", petData.getStationRadius());
                    Set<String> types = petData.getStationTargetTypes();
                    if (types == null) {
                        types = new HashSet<>();
                    }

                    String currentTypes = this.formatTargetTypes(types);
                    List<String> stationLore = new ArrayList<>();
                    stationLore.add(
                            isStationed
                                    ? this.plugin.getLanguageManager().getString("menus.station_active")
                                    : this.plugin.getLanguageManager().getString("menus.station_inactive"));
                    stationLore.add("");
                    stationLore.add(this.plugin.getLanguageManager().getString("menus.station_settings"));
                    stationLore.add(this.plugin.getLanguageManager().getStringReplacements("menus.station_radius",
                            "radius", String.valueOf(currentRadius)));
                    stationLore.add(this.plugin.getLanguageManager().getStringReplacements("menus.station_targets",
                            "targets", currentTypes));
                    stationLore.addAll(
                            this.plugin
                                    .getLanguageManager()
                                    .getStringListReplacements(
                                            "menus.station_click_lore",
                                            "action",
                                            isStationed
                                                    ? this.plugin.getLanguageManager()
                                                            .getString("menus.unstation_action")
                                                    : this.plugin.getLanguageManager()
                                                            .getString("menus.station_action")));
                    row5Items.add(
                            this.createActionButton(
                                    isStationed ? Material.CAMPFIRE : Material.SOUL_CAMPFIRE,
                                    isStationed
                                            ? this.plugin.getLanguageManager().getString("menus.modify_station")
                                            : this.plugin.getLanguageManager().getString("menus.station_here"),
                                    "toggle_station",
                                    petData.getPetUUID(),
                                    stationLore));
                    boolean hasExplicitTarget = petData.getExplicitTargetUUID() != null;
                    String targetName = this.plugin.getLanguageManager().getString("menus.target_none");
                    if (hasExplicitTarget) {
                        Entity t = Bukkit.getEntity(petData.getExplicitTargetUUID());
                        if (t != null) {
                            targetName = t.getName();
                        } else {
                            targetName = this.plugin.getLanguageManager().getString("menus.unloaded_unknown");
                        }
                    }

                    List<String> targetLore = new ArrayList<>();
                    targetLore.add(this.plugin.getLanguageManager().getStringReplacements("menus.target_current",
                            "target", targetName));
                    targetLore.addAll(this.plugin.getLanguageManager().getStringList("menus.target_click_lore"));
                    row5Items.add(
                            this.createActionButton(
                                    Material.CROSSBOW,
                                    hasExplicitTarget
                                            ? this.plugin.getLanguageManager().getString("menus.clear_target")
                                            : this.plugin.getLanguageManager().getString("menus.set_target"),
                                    "toggle_target_prompt",
                                    petData.getPetUUID(),
                                    targetLore));
                    CreeperBehavior creeperBehavior = petData.getCreeperBehavior();

                    String creeperStatusKey = switch (creeperBehavior) {
                        case NEUTRAL -> "menus.creeper_behavior_neutral";
                        case FLEE -> "menus.creeper_behavior_flee";
                        case IGNORE -> "menus.creeper_behavior_ignore";
                    };
                    String creeperStatus = this.plugin.getLanguageManager().getString(creeperStatusKey);
                    List<String> creeperLore = new ArrayList<>();
                    creeperLore.addAll(this.plugin.getLanguageManager()
                            .getStringListReplacements("menus.creeper_behavior_lore", "status", creeperStatus));
                    if (petData.getEntityType() == EntityType.CAT) {
                        creeperLore.addAll(
                                this.plugin.getLanguageManager().getStringList("menus.creeper_behavior_cat_note"));
                    }

                    row5Items.add(
                            this.createActionButton(
                                    Material.CREEPER_HEAD,
                                    this.plugin.getLanguageManager().getString("menus.creeper_behavior_title"),
                                    "cycle_creeper_behavior",
                                    petData.getPetUUID(),
                                    creeperLore));
                    int[] row5Slots = this.getSymmetricalSlots(row5Items.size(), 36);

                    for (int i = 0; i < row5Items.size(); i++) {
                        gui.setItem(row5Slots[i], row5Items.get(i));
                    }
                } else if (isRideable) {
                    gui.setItem(31, this.createAccessItem(petData));
                }

                double currentHp = 0.0;
                double maxHp = 0.0;
                if (petEntity instanceof LivingEntity le && petEntity.isValid()) {
                    currentHp = le.getHealth();
                    maxHp = le.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                }

                double missingHp = maxHp - currentHp;
                int healCost = (int) Math.ceil(missingHp) * this.plugin.getConfigManager().getHealCost();
                int bottomRowStart = guiSize - 9;
                gui.setItem(
                        bottomRowStart + 2,
                        this.createActionButton(
                                Material.GOLDEN_APPLE,
                                this.plugin.getLanguageManager().getString("menus.heal_pet"),
                                "heal_pet",
                                petData.getPetUUID(),
                                this.plugin
                                        .getLanguageManager()
                                        .getStringListReplacements(
                                                "menus.heal_lore",
                                                "health",
                                                String.format("%.1f", currentHp),
                                                "max_health",
                                                String.format("%.1f", maxHp),
                                                "cost",
                                                String.valueOf(healCost))));
                gui.setItem(
                        bottomRowStart + 4,
                        this.createActionButton(Material.ARROW,
                                this.plugin.getLanguageManager().getString("menus.back_to_list"), "back_to_main", null,
                                null));
                gui.setItem(
                        bottomRowStart + 6,
                        this.createActionButton(
                                Material.ENDER_CHEST,
                                this.plugin.getLanguageManager().getString("menus.store_pet"),
                                "store_pet",
                                petData.getPetUUID(),
                                this.plugin.getLanguageManager().getStringList("menus.store_lore")));
                gui.setItem(
                        bottomRowStart + 8,
                        this.createActionButton(
                                Material.BARRIER,
                                this.plugin.getLanguageManager().getString("menus.free_pet"),
                                "confirm_free_pet_prompt",
                                petData.getPetUUID(),
                                this.plugin.getLanguageManager().getStringList("menus.free_pet_lore")));
                player.openInventory(gui);
            }
        }
    }

    public void openConfirmFreeMenu(Player player, UUID petUUID) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            this.plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
        } else {
            String title = this.plugin.getLanguageManager().getStringReplacements("menus.confirm_free_single_title",
                    "name", petData.getDisplayName());
            if (title.length() > 32) {
                title = title.substring(0, 29) + "...";
            }

            Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.CONFIRM_FREE), 27,
                    title);
            gui.setItem(
                    13,
                    this.createItem(
                            this.getPetMaterial(petData.getEntityType()),
                            this.plugin.getLanguageManager().getStringReplacements("menus.free_info_single_name",
                                    "name", petData.getDisplayName()),
                            this.plugin.getLanguageManager().getStringListReplacements("menus.free_info_single_lore",
                                    "type", petData.getEntityType().name()),
                            petUUID));
            gui.setItem(
                    11,
                    this.createActionButton(
                            Material.LIME_WOOL,
                            this.plugin.getLanguageManager().getString("menus.cancel_free"),
                            "cancel_free",
                            petUUID,
                            this.plugin.getLanguageManager().getStringList("menus.cancel_free_single_lore")));
            gui.setItem(
                    15,
                    this.createActionButton(
                            Material.RED_WOOL,
                            this.plugin.getLanguageManager().getString("menus.confirm_free_btn"),
                            "confirm_free",
                            petUUID,
                            this.plugin.getLanguageManager().getStringList("menus.confirm_free_single_btn_lore")));
            player.openInventory(gui);
        }
    }

    public void openTransferMenu(Player player, UUID petUUID) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            this.plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
        } else {
            String title = this.plugin.getLanguageManager().getStringReplacements("menus.transfer_single_title", "pet",
                    petData.getDisplayName());
            if (title.length() > 32) {
                title = title.substring(0, 29) + "...";
            }

            List<Player> eligiblePlayers = Bukkit.getOnlinePlayers()
                    .stream()
                    .filter(p -> !p.getUniqueId().equals(this.getEffectiveOwner(player)))
                    .sorted(Comparator.comparing(HumanEntity::getName))
                    .collect(Collectors.toList());
            int rows = (int) Math.ceil(eligiblePlayers.size() / 9.0);
            int invSize = Math.max(18, (rows + 1) * 9);
            if (eligiblePlayers.isEmpty()) {
                invSize = 27;
            }

            invSize = Math.min(54, invSize);
            Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.TRANSFER),
                    invSize, title);
            if (eligiblePlayers.isEmpty()) {
                gui.setItem(
                        invSize == 27 ? 13 : 22,
                        this.createItem(
                                Material.BARRIER,
                                this.plugin.getLanguageManager().getString("menus.no_players"),
                                this.plugin.getLanguageManager().getStringList("menus.no_players_lore")));
            } else {
                for (int i = 0; i < eligiblePlayers.size() && i < 45; i++) {
                    Player target = eligiblePlayers.get(i);
                    ItemStack head = this.createPlayerHead(
                            target.getUniqueId(),
                            ChatColor.YELLOW + target.getName(),
                            "transfer_to_player",
                            petUUID,
                            this.plugin
                                    .getLanguageManager()
                                    .getStringListReplacements("menus.transfer_single_player_lore", "name",
                                            petData.getDisplayName(), "player", target.getName()));
                    gui.setItem(i, head);
                }
            }

            gui.setItem(
                    invSize - 1,
                    this.createActionButton(Material.ARROW,
                            this.plugin.getLanguageManager().getString("menus.back_pet"), "back_to_pet", petUUID,
                            null));
            player.openInventory(gui);
        }
    }

    public void openFriendlyPlayerMenu(Player player, UUID petUUID, int page) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            this.plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
        } else {
            String title = this.plugin.getLanguageManager().getStringReplacements("menus.friendly_title", "name",
                    petData.getDisplayName());
            if (title.length() > 32) {
                title = title.substring(0, 29) + "...";
            }

            List<UUID> friendlyList = new ArrayList<>(petData.getFriendlyPlayers());
            int itemsPerPage = 45;
            int totalPages = Math.max(1, (int) Math.ceil((double) friendlyList.size() / itemsPerPage));
            page = Math.max(0, Math.min(page, totalPages - 1));
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, friendlyList.size());
            Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.FRIENDLY), 54,
                    title);
            int slot = 0;

            for (int i = startIndex; i < endIndex; i++) {
                UUID friendlyUUID = friendlyList.get(i);
                OfflinePlayer friendly = Bukkit.getOfflinePlayer(friendlyUUID);
                String name = friendly.getName() != null ? friendly.getName() : friendlyUUID.toString().substring(0, 8);
                ItemStack head = this.createPlayerHead(
                        friendlyUUID, ChatColor.YELLOW + name, "remove_friendly", petUUID,
                        this.plugin.getLanguageManager().getStringList("menus.remove_friendly_lore"));
                gui.setItem(slot++, head);
            }

            if (page > 0) {
                gui.setItem(
                        45,
                        this.createFriendlyNavButton(Material.ARROW,
                                this.plugin.getLanguageManager().getString("menus.prev_page"), "friendly_page", petUUID,
                                page - 1));
            }

            gui.setItem(
                    48,
                    this.createActionButton(
                            Material.ANVIL,
                            this.plugin.getLanguageManager().getString("menus.add_friendly"),
                            "add_friendly_prompt",
                            petUUID,
                            this.plugin.getLanguageManager().getStringList("menus.add_friendly_lore_single")));
            if (endIndex < friendlyList.size()) {
                gui.setItem(
                        50,
                        this.createFriendlyNavButton(Material.ARROW,
                                this.plugin.getLanguageManager().getString("menus.next_page"), "friendly_page", petUUID,
                                page + 1));
            }

            gui.setItem(53, this.createActionButton(Material.ARROW,
                    this.plugin.getLanguageManager().getString("menus.back_pet"), "back_to_pet", petUUID, null));
            player.openInventory(gui);
        }
    }

    public void openColorPicker(Player player, UUID petUUID) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            this.plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
        } else {
            String title = this.plugin.getLanguageManager().getStringReplacements("menus.pick_color_title", "name",
                    petData.getDisplayName());
            if (title.length() > 32) {
                title = this.plugin.getLanguageManager().getString("menus.pick_color_short");
            }

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

            for (Entry<Material, ChatColor> entry : choices.entrySet()) {
                if (i >= 26) {
                    break;
                }

                Material mat = entry.getKey();
                ChatColor color = entry.getValue();
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(this.toComponent(color + color.name()));
                meta.lore(
                        this.toComponentList(this.plugin.getLanguageManager().getStringList("menus.pick_color_lore")));
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "choose_color");
                meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
                meta.getPersistentDataContainer().set(COLOR_KEY, PersistentDataType.STRING, color.name());
                item.setItemMeta(meta);
                gui.setItem(i++, item);
            }

            gui.setItem(26, this.createActionButton(Material.ARROW,
                    this.plugin.getLanguageManager().getString("menus.back"), "back_to_pet", petUUID, null));
            player.openInventory(gui);
        }
    }

    private ItemStack createNavigationButton(Material material, String name, String action, int targetPage,
            List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.toComponent(name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(this.toComponentList(lore));
        }

        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, targetPage);
        meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ATTRIBUTES });
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFriendlyNavButton(Material material, String name, String action, UUID petUUID,
            int targetPage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.toComponent(name));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
        meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, targetPage);
        meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ATTRIBUTES });
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createActionButton(Material material, String name, String action, UUID petUUID,
            List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.toComponent(name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(this.toComponentList(lore));
        }

        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        if (petUUID != null) {
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petUUID.toString());
        }

        meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ATTRIBUTES });
        item.setItemMeta(meta);
        return item;
    }

    public void openBatchConfirmRemoveDeadMenu(Player player, EntityType petType, int deadPetCount) {
        String typeName = this.formatEntityType(petType);
        String title = this.plugin.getLanguageManager().getStringReplacements("menus.batch_remove_dead_title", "type",
                typeName);
        Inventory gui = Bukkit.createInventory(
                new PetInventoryHolder(PetInventoryHolder.MenuType.BATCH_CONFIRM_REMOVE_DEAD), 27,
                this.toComponent(title));
        ItemStack info = this.createItem(
                Material.SKELETON_SKULL,
                this.plugin.getLanguageManager().getStringReplacements("menus.batch_remove_dead_name", "count",
                        String.valueOf(deadPetCount)),
                this.plugin.getLanguageManager().getStringListReplacements("menus.batch_remove_dead_lore", "type",
                        typeName));
        gui.setItem(13, info);
        ItemStack cancel = new ItemStack(Material.LIME_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(this.toComponent(this.plugin.getLanguageManager().getString("menus.cancel")));
        cancelMeta.lore(
                this.toComponentList(this.plugin.getLanguageManager().getStringList("menus.cancel_remove_dead_lore")));
        cancelMeta.getPersistentDataContainer().set(BatchActionsGUI.BATCH_ACTION_KEY, PersistentDataType.STRING,
                "open_pet_select");
        cancelMeta.getPersistentDataContainer().set(BatchActionsGUI.PET_TYPE_KEY, PersistentDataType.STRING,
                petType.name());
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);
        ItemStack confirm = new ItemStack(Material.RED_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta
                .displayName(this.toComponent(this.plugin.getLanguageManager().getString("menus.confirm_remove_dead")));
        confirmMeta.lore(
                this.toComponentList(this.plugin.getLanguageManager().getStringList("menus.confirm_remove_dead_lore")));
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
        if (petData.isDead()) {
            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.displayName(
                    this.toComponent(this.plugin.getLanguageManager().getStringReplacements("menus.dead_pet_item_name",
                            "name", petData.getDisplayName())));
            meta.lore(this.toComponentList(this.plugin.getLanguageManager().getStringList("menus.dead_pet_item_lore")));
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                    petData.getPetUUID().toString());
            skull.setItemMeta(meta);
            return skull;
        } else if (petData.isStored()) {
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta meta = chest.getItemMeta();
            meta.displayName(this
                    .toComponent(this.plugin.getLanguageManager().getStringReplacements("menus.stored_pet_item_name",
                            "name", petData.getDisplayName())));
            meta.lore(this.toComponentList(
                    this.plugin.getLanguageManager().getStringListReplacements("menus.stored_pet_item_lore",
                            "type", petData.getEntityType().name())));
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                    petData.getPetUUID().toString());
            chest.setItemMeta(meta);
            return chest;
        } else {
            Material mat = this.getDisplayMaterialForPet(petData);
            ChatColor nameColor = this.getNameColor(petData);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            String displayName = (petData.isFavorite() ? ChatColor.GOLD + "★ " : "") + nameColor
                    + petData.getDisplayName();
            meta.displayName(this.toComponent(displayName));
            List<String> lore = new ArrayList<>();
            lore.addAll(this.plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_basic", "type",
                    this.formatEntityType(petData.getEntityType())));
            String variant = this.getVariantFromMetadata(petData);
            if (variant != null) {
                lore.addAll(this.plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_variant",
                        "variant", variant));
            }

            lore.addAll(this.plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_mode", "mode",
                    petData.getMode().name()));
            if (petEntity instanceof LivingEntity livingEntity && petEntity.isValid()) {
                double health = livingEntity.getHealth();
                double maxHealth = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                lore.addAll(
                        this.plugin
                                .getLanguageManager()
                                .getStringListReplacements(
                                        "menus.pet_item_lore_health", "health", String.format("%.1f", health),
                                        "max_health", String.format("%.1f", maxHealth)));
            } else {
                lore.addAll(this.plugin.getLanguageManager().getStringList("menus.pet_item_lore_health_unknown"));
            }

            lore.addAll(
                    this.plugin
                            .getLanguageManager()
                            .getStringListReplacements(
                                    "menus.pet_item_lore_protection",
                                    "status",
                                    petData.isProtectedFromPlayers()
                                            ? this.plugin.getLanguageManager().getString("menus.enabled")
                                            : this.plugin.getLanguageManager().getString("menus.disabled")));
            int friendlyCount = petData.getFriendlyPlayers().size();
            if (friendlyCount > 0) {
                String key = this.isRideable(petData.getEntityType()) ? "menus.pet_item_lore_managed"
                        : "menus.pet_item_lore_friendly";
                lore.addAll(
                        this.plugin.getLanguageManager().getStringListReplacements(key, "count",
                                String.valueOf(friendlyCount), "s", friendlyCount == 1 ? "" : "s"));
            }

            if (petEntity instanceof Ageable ageable && !ageable.isAdult()) {
                lore.addAll(this.plugin.getLanguageManager().getStringList("menus.pet_item_lore_baby"));
            }

            // Breed cooldown display for adult animals
            if (petEntity instanceof Ageable ageable && ageable.isAdult() && ageable.getAge() > 0) {
                int ticksRemaining = ageable.getAge();
                int secondsRemaining = ticksRemaining / 20;
                int minutes = secondsRemaining / 60;
                int seconds = secondsRemaining % 60;
                String timeFormatted = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
                lore.addAll(this.plugin.getLanguageManager().getStringListReplacements(
                        "menus.pet_item_lore_breed_cooldown", "time", timeFormatted));
            }

            if (petData.getStationLocation() != null) {
                Location st = petData.getStationLocation();
                String tTypes = this.formatTargetTypes(petData.getStationTargetTypes());
                lore.addAll(
                        this.plugin
                                .getLanguageManager()
                                .getStringListReplacements(
                                        "menus.pet_item_lore_station",
                                        "x",
                                        String.valueOf(st.getBlockX()),
                                        "y",
                                        String.valueOf(st.getBlockY()),
                                        "z",
                                        String.valueOf(st.getBlockZ()),
                                        "radius",
                                        String.valueOf((int) petData.getStationRadius()),
                                        "target_types",
                                        tTypes));
            }

            if (petData.getExplicitTargetUUID() != null) {
                Entity target = Bukkit.getEntity(petData.getExplicitTargetUUID());
                String tName = this.plugin.getLanguageManager().getString("menus.unloaded_unknown");
                if (target != null) {
                    tName = target.getName();
                    if (target instanceof Player p) {
                        tName = p.getName();
                    }
                }

                lore.addAll(this.plugin.getLanguageManager().getStringListReplacements("menus.pet_item_lore_target",
                        "target", tName));
            }

            lore.add("");
            lore.addAll(this.plugin.getLanguageManager().getStringList("menus.pet_item_lore_footer"));
            meta.lore(this.toComponentList(lore));
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                    petData.getPetUUID().toString());
            item.setItemMeta(meta);
            return item;
        }
    }

    private ItemStack createAccessItem(PetData petData) {
        boolean isPublic = petData.isPublicAccess();
        ItemStack item = new ItemStack(isPublic ? Material.OAK_DOOR : Material.IRON_DOOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.toComponent(this.plugin.getLanguageManager().getString("menus.toggle_access")));
        String status = isPublic
                ? this.plugin.getLanguageManager().getString("menus.access_public")
                : this.plugin.getLanguageManager().getString("menus.access_private");
        meta.lore(this.toComponentList(this.plugin.getLanguageManager()
                .getStringListReplacements("menus.toggle_access_lore", "status", status)));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "toggle_access");
        meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, petData.getPetUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    private String getVariantFromMetadata(PetData petData) {
        Map<String, Object> md = petData.getMetadata();
        if (md == null) {
            return null;
        } else if (md.containsKey("wolfVariant")) {
            String raw = (String) md.get("wolfVariant");
            if (raw.contains(":")) {
                raw = raw.substring(raw.indexOf(":") + 1);
            }

            return raw.substring(0, 1).toUpperCase() + raw.substring(1).toLowerCase();
        } else if (md.containsKey("catType")) {
            return (String) md.get("catType");
        } else if (md.containsKey("parrotVariant")) {
            return (String) md.get("parrotVariant");
        } else if (md.containsKey("horseColor")) {
            return (String) md.get("horseColor");
        } else {
            return md.containsKey("llamaColor") ? (String) md.get("llamaColor") : null;
        }
    }

    private String formatTargetTypes(Set<String> types) {
        if (types != null && !types.isEmpty()) {
            boolean p = types.contains("PLAYER");
            boolean m = types.contains("MOB");
            boolean a = types.contains("ANIMAL");
            if (p && m && a) {
                return this.plugin.getLanguageManager().getString("menus.target_everything");
            } else if (p && m) {
                return this.plugin.getLanguageManager().getString("menus.target_mobs_players");
            } else if (m && a) {
                return this.plugin.getLanguageManager().getString("menus.target_mobs_animals");
            } else if (p && a) {
                return this.plugin.getLanguageManager().getString("menus.target_players_animals");
            } else if (p) {
                return this.plugin.getLanguageManager().getString("menus.target_players_only");
            } else if (m) {
                return this.plugin.getLanguageManager().getString("menus.target_mobs_only");
            } else {
                return a
                        ? this.plugin.getLanguageManager().getString("menus.target_animals_only")
                        : this.plugin.getLanguageManager().getString("menus.target_custom");
            }
        } else {
            return this.plugin.getLanguageManager().getString("menus.target_none");
        }
    }

    public ItemStack createModeButton(Material material, String name, BehaviorMode mode, PetData currentPetData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean isActive = currentPetData.getMode() == mode;
            meta.displayName(
                    this.toComponent(
                            (isActive ? "" + ChatColor.GREEN + ChatColor.BOLD
                                    : (currentPetData.getMode() == BehaviorMode.BATCH ? ChatColor.AQUA
                                            : ChatColor.YELLOW))
                                    + name));
            List<String> lore = new ArrayList<>();
            switch (mode) {
                case PASSIVE:
                    lore.addAll(this.plugin.getLanguageManager().getStringList("menus.mode_passive_desc"));
                    break;
                case NEUTRAL:
                    lore.addAll(this.plugin.getLanguageManager().getStringList("menus.mode_neutral_desc"));
                    break;
                case AGGRESSIVE:
                    lore.addAll(this.plugin.getLanguageManager().getStringList("menus.mode_aggressive_desc"));
                    break;
                case BATCH:
                    // BATCH mode is a placeholder for multi-selection displays
                    break;
            }

            if (isActive) {
                lore.add("");
                lore.addAll(this.plugin.getLanguageManager().getStringList("menus.mode_active"));
            } else if (currentPetData.getMode() == BehaviorMode.BATCH) {
                lore.add("");
                lore.addAll(this.plugin.getLanguageManager().getStringList("menus.mode_batch_mixed"));
            } else {
                lore.add("");
                lore.addAll(this.plugin.getLanguageManager().getStringList("menus.mode_inactive"));
            }

            if (mode == BehaviorMode.AGGRESSIVE) {
                Set<String> types = currentPetData.getAggressiveTargetTypes();
                boolean mob = types.contains("MOB");
                boolean animal = types.contains("ANIMAL");
                boolean player = types.contains("PLAYER");
                lore.add("");
                lore.addAll(
                        this.plugin
                                .getLanguageManager()
                                .getStringListReplacements(
                                        "menus.aggressive_lore_config",
                                        "mob_status",
                                        mob ? this.plugin.getLanguageManager().getString("menus.enabled")
                                                : this.plugin.getLanguageManager().getString("menus.disabled"),
                                        "animal_status",
                                        animal ? this.plugin.getLanguageManager().getString("menus.enabled")
                                                : this.plugin.getLanguageManager().getString("menus.disabled"),
                                        "player_status",
                                        player ? this.plugin.getLanguageManager().getString("menus.enabled")
                                                : this.plugin.getLanguageManager().getString("menus.disabled")));
            }

            meta.lore(this.toComponentList(lore));
            meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ATTRIBUTES });
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
            meta.displayName(this.toComponent(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(this.toComponentList(lore));
            }

            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
            if (petContextUUID != null) {
                meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                        petContextUUID.toString());
            }

            meta.getPersistentDataContainer().set(TARGET_PLAYER_UUID_KEY, PersistentDataType.STRING,
                    playerUUID.toString());
            meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ATTRIBUTES });
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
            meta.displayName(this.toComponent(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(this.toComponentList(lore));
            }

            if (petUUIDContext != null) {
                meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING,
                        petUUIDContext.toString());
            }

            meta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ATTRIBUTES });
            item.setItemMeta(meta);
        }

        return item;
    }

    public ChatColor getNameColor(PetData data) {
        String c = data.getDisplayColor();
        if (c != null && !c.isEmpty()) {
            try {
                return ChatColor.valueOf(c);
            } catch (IllegalArgumentException var4) {
                return ChatColor.AQUA;
            }
        } else {
            return ChatColor.AQUA;
        }
    }

    public Material getDisplayMaterialForPet(PetData data) {
        if (data.getCustomIconMaterial() != null) {
            try {
                return Material.valueOf(data.getCustomIconMaterial());
            } catch (IllegalArgumentException var3) {
            }
        }

        return this.getPetMaterial(data.getEntityType());
    }

    public Material getPetMaterial(EntityType type) {
        String typeName = type.name();

        // Special handling for custom or non-standard types
        if (typeName.equalsIgnoreCase("HAPPY_GHAST")) {
            Material happyEgg = Material.getMaterial("HAPPY_GHAST_SPAWN_EGG");
            return happyEgg != null ? happyEgg : Material.GHAST_SPAWN_EGG;
        }

        // Try to find a matching spawn egg dynamically
        Material eggMat = Material.getMaterial(typeName + "_SPAWN_EGG");
        if (eggMat != null) {
            return eggMat;
        }

        // Specific overrides for entities that don't have spawn eggs or need unique
        // icons
        return switch (type) {
            case SKELETON_HORSE -> Material.SKELETON_HORSE_SPAWN_EGG;
            case ZOMBIE_HORSE -> Material.ZOMBIE_HORSE_SPAWN_EGG;
            case DONKEY -> Material.DONKEY_SPAWN_EGG;
            case MULE -> Material.MULE_SPAWN_EGG;
            case WOLF -> Material.WOLF_SPAWN_EGG;
            case HORSE -> Material.HORSE_SPAWN_EGG;
            case LLAMA -> Material.LLAMA_SPAWN_EGG;
            case PARROT -> Material.PARROT_SPAWN_EGG;
            case CAT -> Material.CAT_SPAWN_EGG;
            case TRADER_LLAMA -> Material.TRADER_LLAMA_SPAWN_EGG;
            case SNOWMAN -> Material.SNOWBALL;
            case IRON_GOLEM -> Material.IRON_BLOCK;
            case WITHER -> Material.NETHER_STAR;
            case ENDER_DRAGON -> Material.DRAGON_EGG;
            default -> Material.NAME_TAG;
        };
    }

    public void openCustomizationMenu(Player player, UUID petUUID) {
        PetData petData = this.petManager.getPetData(petUUID);
        if (petData == null) {
            this.plugin.getLanguageManager().sendMessage(player, "gui.pet_data_error");
            this.openMainMenu(player);
        } else {
            String title = this.plugin.getLanguageManager().getStringReplacements("menus.customize_title", "name",
                    petData.getDisplayName());
            if (title.length() > 32) {
                title = this.plugin.getLanguageManager().getString("menus.customize_title_short");
            }

            Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.CUSTOMIZE), 45,
                    this.toComponent(title));
            gui.setItem(
                    4,
                    this.createItem(
                            this.getDisplayMaterialForPet(petData),
                            this.getNameColor(petData) + petData.getDisplayName(),
                            this.plugin.getLanguageManager().getStringList("menus.customize_header_lore")));
            gui.setItem(
                    20,
                    this.createActionButton(
                            Material.ITEM_FRAME,
                            this.plugin.getLanguageManager().getString("menus.set_display_icon"),
                            "set_display_icon",
                            petUUID,
                            this.plugin.getLanguageManager().getStringList("menus.set_display_icon_lore")));
            gui.setItem(
                    24,
                    this.createActionButton(
                            Material.WHITE_DYE,
                            this.plugin.getLanguageManager().getString("menus.edit_name_color"),
                            "set_display_color",
                            petUUID,
                            this.plugin.getLanguageManager().getStringList("menus.edit_name_color_lore")));
            gui.setItem(44, this.createActionButton(Material.ARROW,
                    this.plugin.getLanguageManager().getString("menus.back_pet"), "back_to_pet", petUUID, null));
            player.openInventory(gui);
        }
    }

    public void openStorePetMenu(Player player) {
        UUID ownerUUID = this.getEffectiveOwner(player);
        List<PetData> activePets = this.petManager
                .getPetsOwnedBy(ownerUUID)
                .stream()
                .filter(p -> !p.isDead() && !p.isStored())
                .filter(p -> Bukkit.getEntity(p.getPetUUID()) != null)
                .collect(Collectors.toList());
        String title = this.plugin.getLanguageManager().getString("menus.store_pet_title");
        int size = Math.max(9, (int) Math.ceil(activePets.size() / 9.0) * 9);
        if (size > 54) {
            size = 54;
        }

        if (size < 9) {
            size = 9;
        }

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.STORE_PET), size,
                this.toComponent(title));

        for (int i = 0; i < Math.min(activePets.size(), size); i++) {
            PetData pet = activePets.get(i);
            ItemStack item = new ItemStack(Material.ENDER_CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(this.toComponent(this.plugin.getLanguageManager()
                    .getStringReplacements("menus.store_pet_item_name", "name", pet.getDisplayName())));
            meta.lore(
                    this.toComponentList(
                            this.plugin.getLanguageManager().getStringListReplacements("menus.store_pet_item_lore",
                                    "type", this.formatEntityType(pet.getEntityType()))));
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "do_store_pet");
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, pet.getPetUUID().toString());
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        if (activePets.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(this.toComponent(this.plugin.getLanguageManager().getString("menus.no_active_pets")));
            empty.setItemMeta(meta);
            gui.setItem(4, empty);
        }

        player.openInventory(gui);
    }

    public void openWithdrawPetMenu(Player player) {
        UUID ownerUUID = this.getEffectiveOwner(player);
        List<PetData> storedPets = this.petManager.getPetsOwnedBy(ownerUUID).stream().filter(PetData::isStored)
                .collect(Collectors.toList());
        String title = this.plugin.getLanguageManager().getString("menus.withdraw_pet_title");
        int size = Math.max(9, (int) Math.ceil(storedPets.size() / 9.0) * 9);
        if (size > 54) {
            size = 54;
        }

        if (size < 9) {
            size = 9;
        }

        Inventory gui = Bukkit.createInventory(new PetInventoryHolder(PetInventoryHolder.MenuType.WITHDRAW_PET), size,
                this.toComponent(title));

        for (int i = 0; i < Math.min(storedPets.size(), size); i++) {
            PetData pet = storedPets.get(i);
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(
                    this.toComponent(this.plugin.getLanguageManager()
                            .getStringReplacements("menus.withdraw_pet_item_name", "name", pet.getDisplayName())));
            meta.lore(
                    this.toComponentList(
                            this.plugin.getLanguageManager().getStringListReplacements("menus.withdraw_pet_item_lore",
                                    "type", this.formatEntityType(pet.getEntityType()))));
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "do_withdraw_pet");
            meta.getPersistentDataContainer().set(PET_UUID_KEY, PersistentDataType.STRING, pet.getPetUUID().toString());
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        if (storedPets.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(this.toComponent(this.plugin.getLanguageManager().getString("menus.no_stored_pets")));
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

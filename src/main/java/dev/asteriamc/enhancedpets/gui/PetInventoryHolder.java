package dev.asteriamc.enhancedpets.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PetInventoryHolder implements InventoryHolder {
    private final MenuType menuType;

    public PetInventoryHolder(MenuType menuType) {
        this.menuType = menuType;
    }

    public MenuType getMenuType() {
        return menuType;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public enum MenuType {
        MAIN_MENU,
        PET_MENU,
        BATCH_MANAGE,
        BATCH_CONFIRM_FREE,
        BATCH_FRIENDLY,
        BATCH_TRANSFER,
        CONFIRM_FREE,
        TRANSFER,
        FRIENDLY,
        COLOR_PICKER,
        CUSTOMIZE,
        STORE_PET,
        WITHDRAW_PET,
        BATCH_TYPE_SELECT,
        BATCH_PET_SELECT,
        BATCH_CONFIRM_REMOVE_DEAD,
        CONFIRM_ACTION
    }
}

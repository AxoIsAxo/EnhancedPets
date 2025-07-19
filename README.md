# EnhancedPets

Your ultimate Minecraft pet management solution!

---

## Thanks to:

- AxoIsAxo
- cystol

---

## Overview

EnhancedPets is a powerful Spigot/PaperMC plugin designed to give Minecraft players exquisite control over their loyal companions. Move beyond basic commands with an intuitive in-game GUI that centralizes all your pet management requirements, whether you have one pet or an entire army.

---

## Key Features

### Graphical User Interface (GUI)

*   **Easy Access:** Open your personal pet management GUI simply by typing `/pets` (or aliases like `/pet`, `/mypets`, `/ep`) or by performing a quick **Shift + Double-Right-Click** on any pet you own.
*   **Comprehensive Pet List:** Lists all your tamed Wolves, Cats, and Parrots, neatly organized.
*   **Intelligent Layout:** Supports pagination for players with numerous pets and dynamically resizes to perfectly fit shorter lists.

### Per-Pet Management

Seamlessly manage each of your individual pets with a dedicated set of powerful options:

*   **Rename Pet:** Give your pets unique names via a chat prompt or by using an anvil-renamed Name Tag directly in-game – the plugin will automatically sync the name.
*   **Behavior Modes:** Assign specific behaviors:
    *   **Passive:** Your pet will not attack any targets, ever.
    *   **Neutral:** Standard vanilla behavior – pets defend you and attack your targets.
    *   **Aggressive:** Pets will proactively seek out and attack nearby hostile mobs (if possible).
*   **Teleport to You:** Instantly summon your pet to your current location, useful for lost pets or quick repositioning.
*   **Toggle Baby Growth:** Pause or resume the growth of your baby pets, keeping them at their adorable small size indefinitely.
*   **Sit / Stand:** Command your sittable pets to sit or stand at will.
*   **Calm Pet:** Clear your pet's current target and instantly reset any aggressive behavior or wolf anger.
*   **Favorite Pets:** Mark beloved pets as 'favorites' for easier identification and sorting within the GUI.
*   **Free Pet:** Permanently release a pet, with a confirmation prompt to prevent accidental loss.

### Enhanced Pet Behaviors

Beyond GUI actions, EnhancedPets introduces custom logic for improved pet interactions:

*   **Custom Cat Aggression:** (Configurable) Allow tamed cats to actively attack nearby hostile mobs.
*   **Dog Creeper Behavior:** (Configurable) Customize how your tamed wolves react to creepers:
    *   **NEUTRAL:** Vanilla behavior (they ignore creepers unless their owner is attacked).
    *   **ATTACK:** Dogs will actively target and attack creepers.
    *   **FLEE:** Dogs will try to run away from nearby creepers and will not attack them.
*   **Legacy Ocelot Taming:** (Configurable) Re-enable the pre-1.14 method of taming Ocelots into cats using raw Cod or Salmon.
*   **Growth Guard Task:** A dedicated task runs to ensure pets whose growth is paused remain at their current age, even through plugin reloads or server restarts.

### Streamlined Batch Actions

Got many pets? Manage them all at once! Select any combination of pets from different types and apply actions globally:

*   **Group Management:** Apply most per-pet actions (modes, teleport, sit, calm, favorite, free) to your entire selected group simultaneously.
*   **Batch Growth Control:** Quickly pause or resume growth for all selected baby pets.

### Friendly Player Management

Grant other players friendly status with your pets, preventing accidental attacks:

*   **Per-Pet Friends:** Add or remove specific players whom a pet will never attack.
*   **Batch Friends:** Efficiently add/remove friends across all selected pets.

### Pet Transfer System

Easily exchange pets between players:

*   **Individual & Group Transfers:** Securely transfer a single pet or an entire batch of selected pets to another *online* player. **(Warning: This cannot be undone!)**

### Quality of Life & Automation

*   **Pet Scanner:** A "Scan for My Pets" button in the main GUI scans loaded areas for any tamed pets belonging to you that the plugin might have missed, then automatically registers them.
*   **Auto-Name Sync:** Automatically detects name changes applied to your pets with Name Tags in-game and updates the plugin's records.
*   **Auto-Cleanup:** Automatically removes data for pets that have died or been untamed from the plugin's registry.

---

## Commands & Permissions

| Command              | Description                                                          | Permission             | Default Level | Aliases              |
| :------------------- | :------------------------------------------------------------------- | :--------------------- | :------------ | :------------------- |
| `/pets`              | Opens your personal pet management GUI.                              | `enhancedpets.use`     | `true`        | `/pet`, `/mypets`, `/ep` |
| `/pets reload`       | Reloads the plugin's configuration files (not pet data).             | `enhancedpets.reload`  | `op`          |                      |
| (No direct command)  | Grants access to all administrative features (includes reload).      | `enhancedpets.admin`   | `op`          |                      |

---

## Configuration

The plugin generates a `config.yml` in your `plugins/EnhancedPets/` folder. Here are the key configurable options:

*   `cats-attack-hostiles`: (boolean) `true` to make tamed cats actively attack hostile mobs, `false` for vanilla behavior.
*   `dog-creeper-behavior`: (string) How dogs react to creepers. Options: `NEUTRAL`, `ATTACK`, `FLEE`.
*   `ocelot-taming-legacy-style`: (boolean) `true` to allow pre-1.14 ocelot taming (raw fish to transform into a cat), `false` for vanilla.
*   `shift-doubleclick-pet-gui`: (boolean) `true` to enable GUI opening by Shift + Double-Right-Clicking your pet, `false` to disable.

**_Note:_** Do not manually edit the `next-pet-id` or `pet-data` sections in `config.yml`, as these are managed automatically by the plugin.

---

## Installation

1.  Download the latest `EnhancedPets.jar` from the GitHub releases page.
2.  Place the `EnhancedPets.jar` file into your server's `plugins/` folder.
3.  Restart or reload your server.

---

## Support & Contributing

Having issues or got a great idea? Feel free to open an issue on the GitHub repository.

Contributions via Pull Requests are always welcome!

`we are working on adding helpful comments to the code :D`

---

## License

IDK

---

## Github
https://github.com/AxoIsAxo/EnhancedPets

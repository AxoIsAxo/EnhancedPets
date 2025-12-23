# ✨ EnhancedPets — Enhanced Pet System ✨

![Version](https://img.shields.io/badge/Version-v1.2.0-blue.svg)
![Compatibility](https://img.shields.io/badge/MC%20Version-1.17%E2%80%941.21.x-orange.svg)
![Java](https://img.shields.io/badge/Java-17%2B-RED)
![Discord Support](https://img.shields.io/discord/b7BVkJ56mR?label=Discord&logo=discord&color=7289DA)

> Enhance the vanilla pet experience now without mental gymnastics!\
> Wolves, cats, parrots, and more — but smarter, friendlier, and easier to manage.

Thanks to:
- cystol
- AxoIsAxo

---

## 📚 Overview

EnhancedPets makes vanilla pets actually useful and pleasant to manage. Players get a clean GUI, quick actions, and batch tools; admins get simple config, autosaving, and resilient storage.

- Player-friendly: intuitive menus, shift-double right-click to open pet GUI, safe confirmations
- Admin-friendly: flat JSON storage per player, autosave, quick config reload, migration from legacy config
- Devs who keep uttering bad jokes periodically at your service (Use the Issues section)

---

## 💡 Feature Highlights

### 💎 Pet Modes
### 💎 Pet Modes
- **Passive, Neutral, Aggressive** — switch instantly from the GUI (Left-Click).
- **Aggressive Mode Config:** Fine-tune what your pet targets in Aggressive mode!
    - **Shift-Left-Click:** Toggle Mob targeting.
    - **Right-Click:** Toggle Animal targeting.
    - **Shift-Right-Click:** Toggle Player targeting.
- AKA 'we vibe', 'default', and 'try me.'

### 🧭 Teleport & Calm
- Summon any pet to you
- Clear targets/anger with a click

### 🏕️ Stationing System (Guard Duty) — NEW!
Your pets can now be assigned to **guard a specific location**:
- **Station Here:** Anchor pets to a location using the Campfire icon in the GUI or `/pet station [radius]`.
- **Smart Leashing:** Pets will guard the area but automatically return to the station if they wander too far (1.5x the radius).
- **Customizable Guard Radius:** Cycle the guarding radius between 5m and 25m (Shift-click the station button).
- **Target Filtering:** Cycle what the pet guards against: Mobs Only, Animals Only, Players Only, Mobs & Animals, Mobs & Players, or Everything (Right-click the station button).
- **Auto-Sit:** Pets will automatically sit when they return to their station and there are no enemies.
- **Pre-Configuration:** You can set the radius and targets *before* activating station mode for precise control!

### 🎯 Explicit Targeting System (The Hunt) — NEW!
Go beyond "Aggressive Mode" with a manual **lock-on** system:
- **Manual Lock-on:** Use the Crossbow icon in the GUI or `/pet target <playername|mob>` to force your pets to hunt a specific entity.
- **Target Selection Mode:** Right-click the Target button in the GUI to enter selection mode. Then, simply Left-Click an entity in the world to lock on (or Right-Click to cancel).
- **Raytracing:** Using the `/pet target mob` command allows you to lock onto a mob just by looking at it.
- **Enhanced Navigation:** Hunting pets get a **Speed Boost** and use advanced long-range pathfinding (over 20 blocks) to track targets.
- **Visual Effects:** Hunting pets spawn **Soul Fire Flame** particles to show they are on a "mission."
- **Kill Confirmation:** The owner gets a notification and a sound effect when their specific target is neutralized, and the pet returns.

### ❤️‍🩹 XP-Based Healing System — NEW!
- **Healing via XP:** Pets can now be healed using the owner's experience points (Golden Apple icon in the GUI).
- **Costs:** It costs **100 XP per 1 HP** (half heart).
- **Bulk Healing:** Shift-clicking the heal button performs a "Full Heal," calculating the total XP cost automatically.

### 📦 Pet Storage (The "Ender-Pet" System) — NEW!
- **Store:** You can completely despawn a pet and store it "in the cloud" (Ender Chest icon in the GUI or `/pet store`).
- **Withdraw:** Use `/pet withdraw` or the dedicated GUI to spawn your stored pets back into the world.
- **Persistence:** All metadata (variants, health, colors, collar color, inventory) is preserved while the pet is stored.
- Stored pets appear in the main menu with a Chest icon.

### 🏷️ Rename (with validation)
- Rename via chat (A–Z, 0–9, _ and -), or reset to a clean default if invalid
- **Reset Mechanic:** Shift-click the "Rename" button to automatically reset to a generated default name (e.g., "Wolf #42").
- **Automatic Name Sync:** If you rename a pet in-game with a standard Name Tag, the plugin detects this and updates the GUI name to match!

### ⭐ Favorites
- Pin pets you care about — favorites bubble to the top
- *You should use this on all your pets lest you a monster*

### 🪑 Sit / Stand
- Toggle sitting for sittable pets (wolves, cats)
- More micromanagement for our dear users

### 🌱 Baby Growth Control
- Pause baby growth (per-pet and batch); protected by a guard task

### 👥 Friendly Players (Whitelist)
- Add players your pet should never attack; manage per-pet or in batch
- Apes together stronk

### 🤝 Mutual Non-Aggression
- Upon enabling this, pets won't attack players (even in aggressive mode)
- In return, players can't hurt said pets (through direct attacks)
- **Owner Exception:** Owners can still damage their own protected pets.
- Peace treaty goes hard! Use this feature irresponsibly... unless dog army

### 🎨 Pet Display Customization
- **Custom Icons:** Set any item in your hand as your pet's display icon in the GUI! (Shift-click to reset).
- **16 Name Colors:** Pick a unique color for each pet's display name from a full 16-color picker menu! (Shift-click to reset).

### 🧺 Batch Actions
### 🧺 Batch Actions
- Select a type (e.g., Wolves) → pick pets → manage your army:
    - **Mass Mode:** Set all to Passive, Neutral, or Aggressive.
    - **Utility:** Mass Sit/Stand, Teleport, or Calm.
    - **Growth:** Pause/Resume growth for all babies.
    - **Management:** Toggle Favorites, Mutual Protection, or Manage Friendlies.
    - **Ownership:** Mass Transfer to another player or Release (Free).

### 🪦 Dead Pet Flow
- When a pet dies, it stays in the GUI as a skeleton skull
- Revive with a Nether Star (metadata restoration like collar color, variants, health, etc.), or delete permanently

### 🧭 Scan & Sync
- Button to scan loaded chunks for your previously unmanaged tamed pets and add them

### 💾 Robust Storage
- Per-player JSON files; automatic autosave (every 2 minutes); one-time migration from legacy config.yml

### 🔁 Reload Safe
- `/pets reload` updates config and transparently restarts internal tasks

### 🐙 Happy Ghast (1.21.6)
- If your server is 1.21.6+:
    - Place a **Dried Ghast** block and keep it **waterlogged** for ~20 minutes (Vanilla Mechanic Unchanged, But we do track the block and its progress wohoo)
    - When it hatches, the baby Happy Ghast is **automatically registered** as your pet
    - Track incubation progress in the pet menu (shows remaining time)
    - Manage via the same GUI and batch tools
    - Ride it with a harness once it grows up!
    - Don't torture your Happy Ghasts, Kids

Note on Aggressive mode: Pets in Aggressive mode proactively look for nearby valid targets they can see and that aren't friendly or owned by you. This is not limited to "hostiles only."\
Note on other stuff: Passive Aggressive mode is in the works and will be released if you don't fix your lifestyle

---

## 🖼️ Screens & UX
Everything beautifully designed to match aesthetic and usability (Real)

- Main menu shows all your pets, sorted by favorites → type → name/ID
- Per-pet management screen with quick actions and safety confirmations
- Batch menus for type selection, pet selection, and mass-management

(Images coming soon!) (Maybe GIFs)

---

## ⚙️ Installation

Prebuilt releases
1. Download the latest enhancedpets.jar
2. Drop it into plugins/
3. Restart your server

Build from source (Maven)
1. git clone https://github.com/AxoIsAxo/EnhancedPets.git
2. cd EnhancedPets
3. mvn clean install
4. Copy target/enhancedpets-xxx.jar to plugins/
5. Restart your server

Requirements
- Java 17+
- Spigot/Paper 1.17–1.21.x (some new features—like wolf variants—are best on 1.21+)

---

## ⌨️ Commands & Permissions

### Commands
**Aliases:** `/pet`, `/mypets`, `/ep`

| Command | Description |
|---------|-------------|
| `/pets` | Opens the main Pets GUI. |
| `/pets reload` | Reloads the plugin configuration. |
| `/pets store` | Opens the GUI to store an active pet. |
| `/pets withdraw` | Opens the GUI to withdraw a stored pet. |
| `/pets station [radius]` | Stations all your pets at your current location with an optional radius. |
| `/pets unstation` | Releases all your pets from their stations. |
| `/pets target <player\|mob>` | Orders all your pets to attack a specific player or the mob you're looking at. |
| `/pets untarget` | Clears the explicit target for all your pets. |
| `/petadmin <player>` | **(Admin)** Opens the pet management GUI for another player. |

### Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `enhancedpets.use` | Use `/pets` and the GUI. | `true` |
| `enhancedpets.reload` | Allow `/pets reload`. | `op` |
| `enhancedpets.admin` | Use `/petadmin` and Shift+Double Right-Click any pet to manage it. | `op` |

### Quick Tips
- **Shift + double right-click** your own tamed pet to open its GUI instantly (configurable).
- **Admins** with `enhancedpets.admin` can Shift + double right-click *any* pet on the server to manage it.

---

## 🛠️ Config

Default config.yml

```yml
# Configuration for EnhancedPets

# Should cats actively attack any nearby hostile mob?
cats-attack-hostiles: false

# How should dogs react to creepers?
# NEUTRAL: Vanilla behavior (ignore unless owner is attacked by creeper).
# ATTACK: Dogs will actively target and attack creepers.
# FLEE: Dogs will try to run away from nearby creepers and won't attack them.
dog-creeper-behavior: NEUTRAL # Can be NEUTRAL, ATTACK, or FLEE

# Re-enable pre-1.14 Ocelot taming?
# If true, right-clicking an untamed adult Ocelot with raw Cod or Salmon
# will consume the fish and transform the Ocelot into a tamed Cat.
ocelot-taming-legacy-style: false # Default to false to maintain vanilla behavior

# NEW: Shift double right-click any pet you own to open its GUI?
# If true, right-clicking within a window of 250ms on your OWN pet will open the GUI/
shift-doubleclick-pet-gui: true

# Allow players riding the custom "Happy Ghast" pet to shoot fireballs?
# If true, players can left-click while riding their ghast to shoot a small fireball.
happy-ghast-fireball: true

# Setup the item for reviving a pet.
revive-item: NETHER_STAR

#Require advanced logs? want to check if there's a bug?
#Enable this to receive tons of helpful messages from the plugin in the server console
debug: false

#END OF CONFIGURATION
```

Notes
- `/pets reload` reloads configuration (pet data is stored in JSON and not reloaded)
- On enable, the plugin will migrate any old inline "pet-data" from config.yml into playerdata/*.json (one-time)

---

## 🔍 Data & Autosave
Meticulously Tested to ensure loss only where no one is looking (jk its pretty good)

- Each player's pets are saved in plugins/EnhancedPets/playerdata/<player-uuid>.json
- Autosave runs asynchronously every 2 minutes
- Saves are also debounced per owner when changes happen (quick, safe, and grouped)

---

## 🧪 What gets restored on revive/withdraw?

Everything that can be seen of course, The pets are brought back to life in their prior glory.
When a dead pet is revived or a stored pet is withdrawn, EnhancedPets restores everything it can!:

<details>
  <summary><b>Click at your own discretion: may cause questions about nature of existence and recreation leading to lack of authenticity</b></summary>

- **Age and sitting state**
- **Custom name + visibility**
- **Health / max health**
- **Wolf collar color + 1.21+ variant (via registry)**
- **Cat type, collar color, lying down**
- **Parrot variant**
- **Major horse/llama traits (movement, color, strength, inventory, saddle, armor, chest, etc.)**
- **Axolotl, Rabbit, Sheep, Frog variants**
- **Fox type**
- **And more!**

Should you find an attribute you feel is yet to be etched in our restoration list, I humbly request you to let us know post-haste
</details>



---

## 🧠 Tips for Players

- Use **Favorites (★)** to quickly pin your main companions to the TOP
- Use **Batch Actions** to handle big stables in seconds
- Use **Station** to set up guard dogs at your base entrance!
- Use **Target** to send your wolf army after a specific enemy!
- Use **Store/Withdraw** to keep your pets safe when exploring dangerous areas.
- "Scan for My Pets" helps if you tamed animals before installing the plugin (Use every 10 seconds to prove OCD (medically certified))
- Reviving costs a **Nether Star** — plan accordingly!
- Healing costs **XP** — a full heal on a damaged pet can be expensive!

---

## ⚠️ Known Notes

- Aggressive mode defaults to attacking all hostile mobs, but can be configured to target Players, Animals, or Mobs specifically via the GUI.
- The config option "ocelot-taming-legacy-style" is reserved for future expansion
- Station and Target modes are mutually exclusive. Setting a target clears the station, and vice-versa.

---

## 🤝 Support

- Questions, ideas, or need help? Join our Discord: https://discord.gg/b7BVkJ56mR (WE NEED YOU PEEPS)

---

## 🙌 Credits

- Code & design: cystol, AxoIsAxo
- Community feedback and testing: You 💙

Bring your pets to life — slightly more effectively than vanilla.
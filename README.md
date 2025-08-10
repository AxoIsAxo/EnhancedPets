# ✨ EnhancedPets — Enhanced Pet System ✨

![Version](https://img.shields.io/badge/Version-v1.1.0-blue.svg)
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

- 💎 Pet Modes
    - Passive, Neutral, Aggressive — switch instantly from the GUI
    - AKA ‘we vibe’, ‘default’, and ‘try me.
- 🧭 Teleport & Calm
    - Summon any pet to you
    - clear targets/anger with a click
- 🏷️ Rename (with validation)
    - Rename via chat (A–Z, 0–9, _ and -), or reset to a clean default if invalid
    - Bypassing nametag requirements since 2025
- ⭐ Favorites
    - Pin pets you care about — favorites bubble to the top
    - *You should use this on all your pets lest you a monster*
- 🪑 Sit / Stand
    - Toggle sitting for sittable pets (wolves, cats)
    - More micromanagement for our dear users
- 🌱 Baby Growth Control
    - Pause baby growth (per-pet and batch); protected by a guard task
- 👥 Friendly Players (Whitelist)
    - Add players your pet should never attack; manage per-pet or in batch
    - Apes together stronk
- 🤝 Mutual Non-Aggression
  - Upon enabling this, pets won't attack players (even in aggressive mode)
  - In return players cant hurt said pets (Through direct attacks)
  - peace treaty goes hard! use this feature irresponsibly... unless dog army
- 🧺 Batch Actions
    - Select a type (e.g., Wolves) → pick pets → do things at scale:
    - Set modes, toggle favorites, sit/stand, teleport, calm, manage friendlies, transfer, or free
- 🪦 Dead Pet Flow
    - When a pet dies, it stays in the GUI as a skeleton skull
    - Revive with a Nether Star (metadata restoration like collar color, variants, health, etc.), or delete permanently
- 🧭 Scan & Sync
    - Button to scan loaded chunks for your previously unmanaged tamed pets and add them
- 💾 Robust Storage
    - Per-player JSON files; automatic autosave (every 2 minutes); one-time migration from legacy config.yml
- 🔁 Reload Safe
    - /pets reload updates config and transparently restarts internal tasks
- 🐙 Happy Ghast (1.21.6)
    - If your server is 1.21.6+:
        - Right-click with a Snowball to tame (20% chance each try)
        - Manage via the same GUI and batch tools
        - Ride it and left-click to shoot a fireball (cooldown)
        - Dont torture your Happy Ghasts Kids

Note on Aggressive mode: Pets in Aggressive mode proactively look for nearby valid targets they can see and that aren’t friendly or owned by you. This is not limited to “hostiles only.”\
Note on other stuff: Passive Aggressive mode is in the works and will be released if you dont fix your lifestyle
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

Commands
- /pets — opens the Pets GUI
- /pets reload — reloads the plugin configuration

Permissions
- enhancedpets.use — use /pets and the GUI (default: true)
- enhancedpets.reload — allow /pets reload (default: op)
- enhancedpets.admin — reserved for future admin features (default: op)

Quick Tip
- Shift + double right-click your own tamed pet to open its GUI instantly (configurable)

---

## 🛠️ Config

Default config.yml

```yml
# Should cats actively attack any nearby hostile mob?
cats-attack-hostiles: false

# How should dogs react to creepers?
# NEUTRAL: Vanilla behavior (ignore unless owner is attacked by a creeper).
# ATTACK: Dogs will actively target creepers more readily. (Behavior depends on mode.)
# FLEE: Dogs won’t target creepers (they’ll cancel targeting attempts).
dog-creeper-behavior: NEUTRAL # NEUTRAL, ATTACK, or FLEE

# Legacy ocelot taming behavior (reserved/experimental).
ocelot-taming-legacy-style: false

# Shift-double right-click your OWN pet to open the GUI?
shift-doubleclick-pet-gui: true
```

Notes
- /pets reload reloads configuration (pet data is stored in JSON and not reloaded)
- On enable, the plugin will migrate any old inline “pet-data” from config.yml into playerdata/*.json (one-time)

---

## 🔍 Data & Autosave
Meticulously Tested to ensure loss only where no one is looking (jk its pretty good)

- Each player’s pets are saved in plugins/EnhancedPets/playerdata/<player-uuid>.json
- Autosave runs asynchronously every 2 minutes
- Saves are also debounced per owner when changes happen (quick, safe, and grouped)

---

## 🧪 What gets restored on revive?

Everything that can be seen of course, The pets are brought back to life in their prior glory.
When a dead pet is revived, EnhancedPets restores everything it can!:

<details>
  <summary><b>Click at your own discretion: may cause questions about nature of existence and recreation leading to lack of authenticity</b></summary>

- **Age and sitting state**
- **Custom name + visibility**
- **Health / max health**
- **Wolf collar color + 1.21+ variant (via registry)**
- **Cat type, collar color, lying down**
- **Parrot variant**
- **Major horse/llama traits (movement, color, strength, etc.)**

Should you find an attribute you feel is yet to be etched in our restoration list, I humbly request you to let us know post-haste
</details>



---

## 🧠 Tips for Players

- Use Favorites (★) to quickly pin your main companions to the TOP
- Use Batch Actions to handle big stables in seconds
- “Scan for My Pets” helps if you tamed animals before installing the plugin (Use every 10 seconds to prove OCD (medically certified))
- Reviving costs a Nether Star — plan accordingly!

---

## ⚠️ Known Notes

- Aggressive mode picks “valid nearby” targets in line-of-sight that aren’t friendly or yours; it’s not restricted to monsters only
- The config option “ocelot-taming-legacy-style” is reserved for future expansion

---

## 🤝 Support

- Questions, ideas, or need help? Join our Discord: https://discord.gg/b7BVkJ56mR (WE NEED YOU PEEPS)

---

## 🙌 Credits

- Code & design: cystol, AxoIsAxo
- Community feedback and testing: You 💙

Bring your pets to life — slightly more effectively than vanilla.
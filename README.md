# EnhancedPets — Enhanced Pet System

![Version](https://img.shields.io/badge/Version-v1.3.0-blue.svg)
![Compatibility](https://img.shields.io/badge/MC%20Version-1.17%E2%80%941.21.x-orange.svg)
![Java](https://img.shields.io/badge/Java-17%2B-RED)
![Discord Support](https://img.shields.io/discord/b7BVkJ56mR?label=Discord&logo=discord&color=7289DA)

> Enhance the vanilla pet experience now without mental gymnastics!\
> Wolves, cats, parrots, and more — but smarter, friendlier, and easier to manage.
> By Easier i mean navigate through 19338798 (Hyperbolic) Menus to manage your pet's very Soul :D :D

Thanks to:
- cystol
- AxoIsAxo

---

## Trust me bro, you want this.
### Source: This summary

EnhancedPets makes vanilla pets actually useful and pleasant to manage. Players get a clean GUI, quick actions, and batch tools; admins get simple config, autosaving, and resilient storage.

- Player-friendly: intuitive menus, shift-double right-click to open pet GUI, safe confirmations
- Admin-friendly: flat JSON storage per player, autosave, quick config reload
- Devs who keep uttering bad jokes periodically at your service (Use the Issues section)

---

## Features 

### Pet Modes
Switch behavior instantly from the GUI:
- **Passive** — "we vibe"
- **Neutral** — default vanilla behavior
- **Aggressive** — "try me"

**Aggressive Mode Target Configuration:**
Each pet remembers what it should target in Aggressive mode. Configure via clicks on the Aggressive button:
- **Shift-Left-Click:** Toggle Mob targeting
- **Right-Click:** Toggle Animal targeting
- **Shift-Right-Click:** Toggle Player targeting

Example: Want your wolf to attack only players and mobs, but leave animals alone? Toggle off Animals. The button shows current targets with colored indicators.

---

### Creeper Behavior (Per-Pet)
Each pet can have its own reaction to those green walking bombs:
- **Neutral:** Default. Pets only attack creepers if you hit one first (like vanilla wolves).
- **Flee:** Pets actively run away from creepers within 3 blocks. They won't attack creepers under any circumstance.
- **Ignore:** Creepers become invisible to your pet's AI. No attacking, no fleeing, just vibes.

Access via the Creeper Head button in the pet menu. Cycles through: Neutral > Flee > Ignore > Neutral...

*(Note: Cats are inherently feared by creepers in vanilla, so Flee has no additional effect on them. They're already winning.)*

---

### Leash Adoption (Non-Tameable Pets)
Got a cool mob on a leash? Make it yours.

When you open the pet menu, any leashed non-tameable entity near you appears as an "adoption offer." Click to register it as your pet. Works for:
- Llamas (non-trader)
- Any leashaeable mob that isn't already tameable

The leash stays, but now you can manage them through the GUI like any other pet. Finally, a use for that random cow you've been dragging around for 3000 blocks.

---

### Public Access (Rideable Pets)
For horses, llamas, striders, Happy Ghasts and other rideable friends:

- **Public Access ON:** Anyone can ride and access the inventory. Party bus mode.
- **Public Access OFF:** Only you and your trusted players (from Friendly list) can interact. VIP access only.

Toggle via the Eye of Ender button in the pet menu. Unauthorized riders get denied with a message.

---

### Stationing System (Guard Duty) (This is the official good boy feature)
Anchor pets to guard a specific location:

**How it works:**
1. Left-click the Campfire button to station the pet at your current location
2. Or use `/pet station [radius]` to station all your pets

**Configuration (can be set BEFORE or AFTER stationing):**
- **Shift-click:** Cycle guard radius (5m, 10m, 15m, 20m, 25m)
- **Right-click:** Cycle target types through 7 combinations:
  - Mobs Only
  - Animals Only
  - Players Only
  - Mobs & Players
  - Mobs & Animals
  - Players & Animals
  - Everything

**Behavior:**
- Pets attack valid targets within the station radius
- If pets wander past 1.5x the radius, they auto-return
- When no targets exist, pets sit at the station point
- Summoning a stationed pet updates the station to your new location

---

### Explicit Targeting (The Hunt) (A.K.A. The Assassin Mode)
Go beyond Aggressive mode with manual lock-on:

**Single Pet:**
- Left-click Crossbow button: Enter target selection mode. Left-click an entity in the world to lock on, right-click to cancel.
- Right-click Crossbow button: Chat prompt to type a player name.
- If a target is already set, clicking clears it.

**Command:** `/pet target <playername|mob>` — locks all your pets onto a target. Use "mob" to raytrace whatever you're looking at.

**Hunting Behavior:**
- Pets get Speed II boost when target is >20 blocks away
- Soul Fire Flame particles spawn on hunting pets (so you know they're on the job)
- Long-range pathfinding for distant targets
- Kill confirmation: Sound + message when target is neutralized
- 30-second timeout if target is unloaded/unreachable

Your personal wolf hit squad, essentially.

---

### XP-Based Healing
Heal pets using your experience points:

- **Click:** Heal 1 HP (cost: configurable, default 100 XP points per HP)
- **Shift-Click:** Full heal (calculates total cost automatically)

Indicated by the Golden Apple button. If you're broke on XP, the game tells you how much more you need.

---

### Pet Storage (Ender-Pet System)
Despawn pets and store them "in the cloud":

- **Store:** Ender Chest button in pet menu, or `/pet store`
- **Withdraw:** `/pet withdraw` opens a GUI of stored pets

Stored pets appear in the main menu with a chest icon overlay. All metadata (variants, health, colors, collar color, inventory for horses) is preserved.

Perfect for keeping your favorite wolf safe while you explore the End.

---

### Rename (with validation)
- Click Name Tag button: Type new name in chat (A–Z, 0–9, _, -)
- Shift-Click: Reset to default generated name (e.g., "Wolf #42")

If you rename a pet in-game with a standard Name Tag, the plugin auto-syncs the GUI name.

---

### Favorites
Star button toggles favorite status. Favorites bubble to the top of the list.

*You should use this on all your pets lest you a monster*

---

### Sit / Stand
Feather button toggles sitting for sittable pets (wolves, cats, etc.)

More micromanagement for our dear users.

---

### Baby Growth Control
Cake button pauses/resumes baby growth. A background task enforces this even across restarts.

---

### Friendly Players (Whitelist)
Add players your pet should never attack, even in Aggressive mode. Per-pet or batch.

Cuz apes together stronk.

---

### Mutual Non-Aggression (Protection Toggle)
Shield button. When enabled:
- Pet won't attack players (even in Aggressive mode)
- Players can't hurt the pet (direct attacks blocked)
- Owner exception: You can still damage your own protected pets

Peace treaty goes hard! Use this feature irresponsibly... unless dog army.

---

### Pet Display Customization
- **Icon:** Hold any item in main hand, click the GUI item to set it as the pet's display icon. Shift-click to reset.
- **Color:** Pick from 16 colors for the pet's display name. Shift-click to reset.

---

### Batch Actions
Select pets by type, then manage your army:

1. Open Batch menu (Hopper button in main menu)
2. Pick a pet type (e.g., Wolves)
3. Select/deselect individual pets or use Select All/None
4. Open batch management

**Available Batch Actions:**
- **Modes:** Set all to Passive/Neutral/Aggressive (with same click-config as single pet)
- **Sit/Stand:** Toggle sitting state
- **Teleport:** Summon all to you
- **Calm:** Clear targets and anger
- **Growth Pause:** Pause/resume baby growth
- **Favorites:** Mark/unmark all as favorites
- **Protection:** Toggle mutual protection
- **Friendly Players:** Add/remove from whitelist across all selected
- **Targeting:** Enter target selection mode for batch
- **Transfer:** Transfer all selected to another player
- **Free:** Release all selected (with confirmation)
- **Heal:** XP-heal all selected (click = 1HP each, shift = full heal all)
- **Remove Dead:** Permanently delete all dead pets of the selected type

---

### Dead Pet Necromancy (not literal)
When a pet dies:
- Shows in GUI as a skull icon
- Click for options: Revive (costs configurable item) or Remove permanently
- Revival restores metadata: collar color, variants, health, inventory

Death notifications include sound and location info.

---

### Scan & Sync
Compass button scans loaded chunks for your unmanaged tamed pets and registers them.

Helps if you tamed animals before installing the plugin.

---

### Happy Ghast (1.21.6+)
If your server is 1.21.6+:
- Place a Dried Ghast block and keep it waterlogged (~20 minutes vanilla mechanic)
- The plugin tracks the block and its placer
- When it hatches, the baby Happy Ghast is auto-registered as your pet
- Track incubation progress in the main menu (special item shows remaining time)
- Manage via the same GUI and batch tools
- Rideable once grown

Don't torture your Happy Ghasts, Kids.
- Yes we do not allow you to use happy ghasts are fireball throwing machines cuz its AGAINST the LLLLLLLLLLLore

---

### HP Notifications
When your pet takes damage:
- **25% HP:** Warning message
- **10% HP:** Critical warning + ping sound
- **5% HP:** DANGER + louder ping

You'll know when to panic.

---

## Commands & Permissions

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
- **Shift + double right-click** your own tamed pet to open its GUI instantly (configurable)
- **Admins** with `enhancedpets.admin` can Shift + double right-click *any* pet on the server to manage it

---

## Config

**Default `config.yml`**

```yml
# Configuration for EnhancedPets

# Re-enable pre-1.14 Ocelot taming?
# If true, right-clicking an untamed adult Ocelot with raw Cod or Salmon
# will consume the fish and transform the Ocelot into a tamed Cat.
ocelot-taming-legacy-style: false

# Shift double right-click any pet you own to open its GUI?
shift-doubleclick-pet-gui: true

# Item required to revive dead pets
revive-item: NETHER_STAR
revive-item-amount: 1
revive-item-require-mainhand: true

# Cost (in XP points, not levels) to heal 1 HP of a pet
pet-heal-cost: 100

# Enable debug logs in console
debug: false

#END OF CONFIGURATION
```

**Notes:**
- Creeper behavior is configured **per-pet** via the GUI, not globally.
- Per-player JSON storage in `playerdata/<uuid>.json`.
- Autosave runs every 2 minutes.

---

## What gets restored on revive/withdraw?

When a dead pet is revived or a stored pet is withdrawn, everything possible is restored:

<details>
  <summary><b>Click at your own discretion: may cause questions about nature of existence and recreation leading to lack of authenticity</b></summary>

- Age and sitting state
- Custom name + visibility
- Health / max health
- Wolf collar color + 1.21+ variant
- Cat type, collar color, lying down
- Parrot variant
- Horse/llama traits (movement, color, strength, inventory, saddle, armor, chest, etc.)
- Axolotl, Rabbit, Sheep, Frog variants
- Fox type
- And more!

Should you find an attribute you feel is yet to be etched in our restoration list, I humbly request you to let us know post-haste
</details>

---

## Tips for Players

- Use **Favorites** to pin main companions to top
- Use **Batch Actions** to handle big stables in seconds
- Use **Station** to set up guard dogs at your base entrance
- Use **Target** to send your wolf army after a specific enemy
- Use **Store/Withdraw** to keep pets safe when exploring dangerous areas
- "Scan for My Pets" helps if you tamed animals before installing the plugin (use every 10 seconds to prove OCD)
- Reviving costs a Nether Star by default — plan accordingly
- Healing costs XP — a full heal on a damaged pet can be expensive

---

## Known Notes

- Aggressive mode proactively targets valid entities. Configure what counts as "valid" via the target toggles.
- Station and Target modes are mutually exclusive. Setting a target clears the station, and vice-versa.
- The `ocelot-taming-legacy-style` option re-enables pre-1.14 style Ocelot taming.

---

## Support

- Questions, ideas, or need help? Join our Discord: https://discord.gg/b7BVkJ56mR (WE NEED YOU PEEPS)

---

## Credits

- Code & design: cystol, AxoIsAxo
- Community feedback and testing: You

Bring your pets to life — slightly more effectively than vanilla.
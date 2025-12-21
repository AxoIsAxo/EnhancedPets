package dev.asteriamc.enhancedpets.commands;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.gui.PetManagerGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PetCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final Enhancedpets plugin;
    private final PetManagerGUI guiManager;

    public PetCommand(Enhancedpets plugin, PetManagerGUI guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();

        if (command.getName().equalsIgnoreCase("petadmin")) {
            if (sender.hasPermission("enhancedpets.admin") && args.length == 1) {
                return null; // Return null to show online players
            }
            return completions;
        }

        if (args.length == 1) {
            if (sender.hasPermission("enhancedpets.use")) {
                String[] subcmds = { "station", "unstation", "target", "untarget", "store", "withdraw" };
                for (String s : subcmds) {
                    if (s.toLowerCase().startsWith(args[0].toLowerCase()))
                        completions.add(s);
                }
            }
            if (sender.hasPermission("enhancedpets.reload")) {
                if ("reload".startsWith(args[0].toLowerCase()))
                    completions.add("reload");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("station") && sender.hasPermission("enhancedpets.use")) {
                completions.add("10");
                completions.add("15");
                completions.add("20");
            } else if (args[0].equalsIgnoreCase("target") && sender.hasPermission("enhancedpets.use")) {
                completions.add("mob");
                return null; // Show players
            }
        }

        return completions;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("petadmin")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            if (!player.hasPermission("enhancedpets.admin")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /" + label + " <player>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.isOnline() && !target.hasPlayedBefore()) {
                player.sendMessage(
                        ChatColor.RED + "Player '" + ChatColor.YELLOW + args[0] + ChatColor.RED + "' does not exist.");
                return true;
            }

            if (!plugin.getPetManager().isOwnerLoaded(target.getUniqueId())) {
                plugin.getPetManager().loadPetsForPlayer(target.getUniqueId());
            }
            guiManager.setViewerOwnerOverride(player.getUniqueId(), target.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> guiManager.openMainMenu(player), 3L);
            return true;
        }

        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("enhancedpets.reload")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to reload this plugin.");
                    return true;
                } else {
                    this.plugin.reloadPluginConfig(sender);
                    return true;
                }
            }

            if (sender instanceof Player player) {
                if (!player.hasPermission("enhancedpets.use")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }

                // Station Command
                if (args[0].equalsIgnoreCase("station")) {
                    // Usage: /pet station <radius>
                    double radius = 10.0;
                    if (args.length > 1) {
                        try {
                            radius = Double.parseDouble(args[1]);
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "Invalid radius.");
                            return true;
                        }
                    }

                    int count = 0;
                    for (dev.asteriamc.enhancedpets.data.PetData pet : plugin.getPetManager()
                            .getPetsOwnedBy(player.getUniqueId())) {
                        pet.setStationLocation(player.getLocation());
                        pet.setStationRadius(radius);
                        // Default targets
                        java.util.Set<String> types = new java.util.HashSet<>();
                        types.add("PLAYER");
                        types.add("MOB");
                        pet.setStationTargetTypes(types);

                        // Clear explicit target so station works
                        pet.setExplicitTargetUUID(null);

                        plugin.getPetManager().updatePetData(pet);
                        count++;
                    }
                    player.sendMessage(ChatColor.GREEN + "Stationed " + count + " pets at your location (Radius: "
                            + radius + ").");
                    return true;
                }

                if (args[0].equalsIgnoreCase("unstation")) {
                    int count = 0;
                    for (dev.asteriamc.enhancedpets.data.PetData pet : plugin.getPetManager()
                            .getPetsOwnedBy(player.getUniqueId())) {
                        pet.setStationLocation(null);
                        plugin.getPetManager().updatePetData(pet);
                        count++;
                    }
                    player.sendMessage(ChatColor.GREEN + "Released " + count + " pets from station.");
                    return true;
                }

                // Target Command
                if (args[0].equalsIgnoreCase("target")) {
                    // Usage: /pet target <playername> OR /pet target mob
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /pet target <player|mob>");
                        return true;
                    }

                    java.util.UUID targetUUID = null;

                    if (args[1].equalsIgnoreCase("mob")) {
                        // Raytrace
                        org.bukkit.util.RayTraceResult result = player.getWorld().rayTraceEntities(
                                player.getEyeLocation(), player.getEyeLocation().getDirection(), 20,
                                e -> e instanceof org.bukkit.entity.LivingEntity && e != player);
                        if (result != null && result.getHitEntity() != null) {
                            targetUUID = result.getHitEntity().getUniqueId();
                            player.sendMessage(ChatColor.GREEN + "Target locked: " + result.getHitEntity().getName());
                        } else {
                            player.sendMessage(ChatColor.RED + "No mob found in line of sight.");
                            return true;
                        }
                    } else {
                        // Assume player name
                        Player targetP = Bukkit.getPlayer(args[1]);
                        if (targetP == null) {
                            player.sendMessage(ChatColor.RED + "Player not found.");
                            return true;
                        }
                        targetUUID = targetP.getUniqueId();
                        player.sendMessage(ChatColor.GREEN + "Target locked: " + targetP.getName());
                    }

                    if (targetUUID != null) {
                        int count = 0;
                        for (dev.asteriamc.enhancedpets.data.PetData pet : plugin.getPetManager()
                                .getPetsOwnedBy(player.getUniqueId())) {
                            pet.setExplicitTargetUUID(targetUUID);
                            plugin.getPetManager().updatePetData(pet);
                            count++;
                        }
                        player.sendMessage(ChatColor.GREEN + "Ordered " + count + " pets to attack target.");
                    }
                    return true;
                }

                if (args[0].equalsIgnoreCase("untarget")) {
                    int count = 0;
                    for (dev.asteriamc.enhancedpets.data.PetData pet : plugin.getPetManager()
                            .getPetsOwnedBy(player.getUniqueId())) {
                        pet.setExplicitTargetUUID(null);
                        plugin.getPetManager().updatePetData(pet);
                        count++;
                    }
                    player.sendMessage(ChatColor.GREEN + "Cleared targets for " + count + " pets.");
                    return true;
                }

                // Store Command - Opens GUI with active pets to store
                if (args[0].equalsIgnoreCase("store")) {
                    guiManager.openStorePetMenu(player);
                    return true;
                }

                // Withdraw Command - Opens GUI with stored pets to withdraw
                if (args[0].equalsIgnoreCase("withdraw")) {
                    guiManager.openWithdrawPetMenu(player);
                    return true;
                }
            }
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("enhancedpets.use")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                } else {
                    this.guiManager.clearViewerOwnerOverride(player.getUniqueId());
                    this.guiManager.openMainMenu(player);
                    return true;
                }
            } else {
                sender.sendMessage(ChatColor.RED + "The pet GUI can only be opened by a player. Use '/" + label
                        + " reload' to reload.");
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid usage.");
            return true;
        }
    }
}

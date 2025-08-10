package dev.asteriamc.enhancedpets.commands;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.gui.PetManagerGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PetCommand implements CommandExecutor {
    private final Enhancedpets plugin;
    private final PetManagerGUI guiManager;

    public PetCommand(Enhancedpets plugin, PetManagerGUI guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("enhancedpets.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload this plugin.");
                return true;
            } else {
                this.plugin.reloadPluginConfig(sender);
                return true;
            }
        } else if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("enhancedpets.use")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                } else {
                    this.guiManager.openMainMenu(player);
                    return true;
                }
            } else {
                sender.sendMessage(ChatColor.RED + "The pet GUI can only be opened by a player. Use '/" + label + " reload' to reload.");
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid usage. Use '/" + label + "' to open the GUI or '/" + label + " reload' to reload the config.");
            return true;
        }
    }
}
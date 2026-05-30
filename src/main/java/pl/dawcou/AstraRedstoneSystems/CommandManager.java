package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CommandManager implements org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {

    private final AstraRS plugin;
    private final SelectionManager selectionManager;

    public CommandManager(AstraRS plugin, SelectionManager selectionManager) {
        this.plugin = plugin;
        this.selectionManager = selectionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("astraredstonesystems") || label.equalsIgnoreCase("ars")) {

            // --- RELOAD (Twoje wiadomości i logika) ---
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!player.hasPermission("astralogicgates.reload")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.setLanguageManager(new LanguageManager(plugin));
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("reload-success"));
                return true;
            }

            // --- INFO (Twoje wiadomości) ---
            if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
                player.sendMessage("§7------------ " + plugin.PREFIX + " §7----------");
                player.sendMessage("§aPlugin created by: §eDawcoU");
                player.sendMessage("§aPlugin version: §ev" + plugin.getPluginMeta().getVersion());
                player.sendMessage("");
                player.sendMessage("§6Copyright © 2026 DawcoU All rights reserved");
                player.sendMessage("§7-----------------------");
                return true;
            }

            // --- SELECTOR ---
            if (args.length == 1 && args[0].equalsIgnoreCase("selector")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                selectionManager.giveSelector(player);
                return true;
            }

            // --- CUT ---
            if (args.length == 1 && args[0].equalsIgnoreCase("cut")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                selectionManager.cutSelection(player);
                return true;
            }

            // --- COPY ---
            if (args.length == 1 && args[0].equalsIgnoreCase("copy")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                selectionManager.copySelection(player);
                return true;
            }

            // --- PASTE ---
            if (args.length == 1 && args[0].equalsIgnoreCase("paste")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                selectionManager.pasteSelection(player);
                return true;
            }

            // --- UNDO ---
            if (args[0].equalsIgnoreCase("undo")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }

                // Wywołujemy logikę cofania
                selectionManager.undoPaste(player);
                return true;
            }

            // --- REDO ---
            if (args[0].equalsIgnoreCase("redo")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }

                // Wywołujemy logikę cofania
                selectionManager.redoPaste(player);
                return true;
            }

            // --- ROTATE ---
            if (args[0].equalsIgnoreCase("rotate")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }

                // Sprawdzamy, czy gracz podał stopnie (np. /alg rotate 90)
                if (args.length < 2) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("invalid-rotate-angle"));
                    return true;
                }

                try {
                    int degrees = Integer.parseInt(args[1]);

                    // Walidacja: tylko 90, -90, 180 są sensowne dla bramek
                    if (degrees != 90 && degrees != -90 && degrees != 180) {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("invalid-rotate-angle"));
                        return true;
                    }

                    // WYWOŁUJEMY ROTACJĘ
                    selectionManager.rotateSelection(player, degrees);

                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("invalid-rotate-angle"));
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> hints = new ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("astraredstonesystems") || cmd.equalsIgnoreCase("ars")) {
            if (args.length == 1) {
                // Podpowiedzi dla głównej komendy
                Arrays.asList("info", "reload", "selector", "cut", "paste", "copy", "rotate", "undo", "redo")
                        .forEach(a -> {
                            if (a.startsWith(args[0].toLowerCase())) hints.add(a);
                        });

            } else if (args.length == 2) {
                // Podpowiedzi dla drugiego argumentu
                List<String> subArgs = switch (args[0].toLowerCase()) {
                    case "rotate" -> Arrays.asList("90", "-90", "180");
                    default -> Collections.emptyList();
                };

                // Używamy toLowerCase() dla bezpieczeństwa, choć przy liczbach to formalność
                subArgs.forEach(t -> {
                    if (t.startsWith(args[1].toLowerCase())) hints.add(t);
                });
            }
        }
        return hints;
    }
}
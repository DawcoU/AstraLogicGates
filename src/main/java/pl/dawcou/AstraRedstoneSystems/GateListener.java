package pl.dawcou.AstraRedstoneSystems;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;

import java.util.*;

public class GateListener implements Listener {

    private final AstraRS plugin;
    private final Map<UUID, String> editingPlayers = new HashMap<>();

    public GateListener(AstraRS plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block brokenBlock = e.getBlock();
        String locStr = GateUtils.locToStr(brokenBlock.getLocation());
        String originalPath = "gates." + locStr;
        FileConfiguration config = plugin.getGatesConfig();

        // 1. Sprawdzamy, czy to w ogóle jest w configu
        if (!config.contains(originalPath)) return;

        String currentPath = originalPath;
        Block mainBlock = brokenBlock;

        // 2. Obsługa "Dziecka" (odnogi Synchronizera)
        if (config.contains(originalPath + ".parent")) {
            String parentLocStr = config.getString(originalPath + ".parent");

            // Zanim usuniemy dziecko z configu, znajdźmy rodzica
            Location pLoc = GateUtils.strToLoc(parentLocStr);
            if (pLoc != null) {
                mainBlock = pLoc.getBlock(); // Teraz mainBlock to środek synchronizera
                currentPath = "gates." + parentLocStr;
            }

            // Usuwamy wpis dziecka z configu
            config.set(originalPath, null);
        }

        // 3. Logika usuwania głównej bramki (lub środka synchronizera)
        if (config.contains(currentPath)) {
            String type = config.getString(currentPath + ".type", "UNKNOWN");
            String dirName = config.getString(currentPath + ".out", "NORTH");
            BlockFace faceOut = BlockFace.valueOf(dirName);
            Location parentLoc = GateUtils.strToLoc(currentPath.replace("gates.", ""));

            // --- FAZA 1: GASZENIE WYJŚĆ (Zanim usuniemy cokolwiek z configu!) ---
            if (type.equals("CABLE_DATA")) {
                // Kable nie mają wyjść, więc nic nie gasimy.
            } else if ("SYNCHRONIZER".equals(type)) {
                String sideLLoc = config.getString(currentPath + ".sideL");
                String sideRLoc = config.getString(currentPath + ".sideR");

                if (sideLLoc != null) {
                    Location sL = GateUtils.strToLoc(sideLLoc);
                    // Usunięto podwójne DOWN - updateOutput sam schodzi pod ziemię
                    if (sL != null) GateUtils.updateOutput(plugin, "gates." + sideLLoc, sL.getBlock().getRelative(faceOut), false);
                }
                if (sideRLoc != null) {
                    Location sR = GateUtils.strToLoc(sideRLoc);
                    if (sR != null) GateUtils.updateOutput(plugin, "gates." + sideRLoc, sR.getBlock().getRelative(faceOut), false);
                }

                // Gasimy też środek synchronizera (ważne, by przywrócić tam blok!)
                if (parentLoc != null) GateUtils.updateOutput(plugin, currentPath, parentLoc.getBlock().getRelative(faceOut), false);
            } else {
                if (parentLoc != null) {
                    GateUtils.updateOutput(plugin, currentPath, parentLoc.getBlock().getRelative(faceOut), false);
                }
            }

            // --- FAZA 2: FIZYCZNE USUWANIE BLOKÓW ---
            if ("SYNCHRONIZER".equals(type)) {
                String sideLLoc = config.getString(currentPath + ".sideL");
                String sideRLoc = config.getString(currentPath + ".sideR");

                if (sideLLoc != null) {
                    Location loc = GateUtils.strToLoc(sideLLoc);
                    if (loc != null) {
                        loc.getBlock().setType(Material.AIR);
                        config.set("gates." + sideLLoc, null);
                    }
                }

                if (sideRLoc != null) {
                    Location loc = GateUtils.strToLoc(sideRLoc);
                    if (loc != null) {
                        loc.getBlock().setType(Material.AIR);
                        config.set("gates." + sideRLoc, null);
                    }
                }
            }

            // KLUCZOWY FIX: Jeśli rozwaliliśmy odnogę, musimy fizycznie usunąć środek!
            if (mainBlock != brokenBlock) {
                mainBlock.setType(Material.AIR);
            }

            // Obsługa Displaya
            if ("DISPLAY".equals(type)) {
                String uuidStr = config.getString(currentPath + ".displayUUID");
                if (uuidStr != null && !uuidStr.isEmpty()) {
                    try {
                        Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));
                        if (entity != null) entity.remove();
                    } catch (Exception ignored) {}
                }
            }

            // Drop przedmiotu
            List<String> savedLore = config.getStringList(currentPath + ".lore");
            ItemStack item = new ItemStack(mainBlock.getType() == Material.AIR ? brokenBlock.getType() : mainBlock.getType());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // 1. Pobieramy prefiks z pliku językowego
                String langPrefix = plugin.getLanguageManager().getMessage("gate-item-name-prefix");
                if (langPrefix == null || langPrefix.isEmpty()) {
                    langPrefix = "&4Bramka: &c";
                }

                // 2. Deserializujemy tekst
                net.kyori.adventure.text.Component nameComponent = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(langPrefix + type.toUpperCase());

                // 3. BLOKADA POCHYLENIA TEKSTU (Wyłączamy kursywę Adventure)
                nameComponent = nameComponent.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);

                meta.displayName(nameComponent);

                // Konwertujemy lore z configu na tradycyjne, czyste Stringi z sekcjami kolorów (§)
                List<String> formattedLore = new ArrayList<>();
                for (String line : savedLore) {
                    String formattedLine = org.bukkit.ChatColor.translateAlternateColorCodes('&', line);
                    if (!formattedLine.startsWith("§r")) {
                        formattedLine = "§r" + formattedLine;
                    }
                    formattedLore.add(formattedLine);
                }
                meta.setLore(formattedLore);

                // --- WPISANIE DANYCH PDC PRZED USUNIĘCIEM BLOKU ---
                org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey(plugin, "gate_type");
                meta.getPersistentDataContainer().set(typeKey, org.bukkit.persistence.PersistentDataType.STRING, type.toUpperCase());

                item.setItemMeta(meta);
            }
            brokenBlock.getWorld().dropItemNaturally(brokenBlock.getLocation(), item);

            // --- FINALIZACJA ---
            config.set(currentPath, null); // Rodzic na samym końcu!
            plugin.saveGates();

            e.setDropItems(false);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("gate-removed", "{TYPE}", type));
        }
    }

    @EventHandler
    public void onPowerBlockBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.REDSTONE_BLOCK) return;

        ConfigurationSection gates = plugin.getGatesConfig().getConfigurationSection("gates");
        if (gates == null) return;

        Location brokenLoc = e.getBlock().getLocation();

        for (String key : gates.getKeys(false)) {
            String path = "gates." + key;
            Location gateLoc = GateUtils.strToLoc(key);
            String type = plugin.getGatesConfig().getString(path + ".type", "");

            if (type.equalsIgnoreCase("CABLE_DATA")) continue;

            String outStr = plugin.getGatesConfig().getString(path + ".out");
            if (gateLoc == null || outStr == null) continue;

            BlockFace out = BlockFace.valueOf(outStr.toUpperCase());
            Block gateBlock = gateLoc.getBlock();

            // 1. Sprawdzanie głównego wyjścia (dla wszystkich bramek)
            if (isPowerBlock(brokenLoc, gateBlock.getRelative(out))) {
                cancelEvent(e);
                return;
            }

            // 2. Specjalne sprawdzanie dla Synchronizera (boki)
            if (type.equalsIgnoreCase("SYNCHRONIZER")) {
                BlockFace right = GateUtils.rotate90(out);
                BlockFace left = right.getOppositeFace();

                if (isPowerBlock(brokenLoc, gateBlock.getRelative(right).getRelative(out)) ||
                        isPowerBlock(brokenLoc, gateBlock.getRelative(left).getRelative(out))) {
                    cancelEvent(e);
                    return;
                }
            }
        }
    }

    // Pomocnicza metoda, żeby nie pisać 10 razy tego samego porównania loc
    private boolean isPowerBlock(Location broken, Block targetPowerBlock) {
        Location targetLoc = targetPowerBlock.getRelative(BlockFace.DOWN).getLocation();
        return broken.getWorld().equals(targetLoc.getWorld()) &&
                broken.getBlockX() == targetLoc.getBlockX() &&
                broken.getBlockY() == targetLoc.getBlockY() &&
                broken.getBlockZ() == targetLoc.getBlockZ();
    }

    private void cancelEvent(BlockBreakEvent e) {
        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("power-block-break-deny"));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // 1. SPRAWDZAMY, CZY PRZEDMIOT MA NASZ UKRYTY KLUCZ TYPU BRAMKI
        org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey(plugin, "gate_type");
        if (!meta.getPersistentDataContainer().has(typeKey, org.bukkit.persistence.PersistentDataType.STRING)) return;

        // 2. ODCZYTUJEMY TYP (Zawsze wielkimi literami, np. "NOT", "TRANSISTOR")
        String type = meta.getPersistentDataContainer().get(typeKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (type == null || type.isEmpty()) return;

        // --- LOGIKA STAWIANIA BRAMKI ---
        Block block = e.getBlock();
        String path = "gates." + GateUtils.locToStr(block.getLocation());

        FileConfiguration config = plugin.getGatesConfig();

        // Zawsze zapisujemy typ, bo to podstawa
        BlockFace outFace = GateUtils.getDirection(e.getPlayer());
        config.set(path + ".type", type.toUpperCase());

        // --- WYJĄTEK DLA KABLA ---
        if (type.equals("CABLE_DATA")) {
            // KABEL: Nie potrzebuje kierunku (out), stanu (state) ani zapisu bloku pod spodem
            config.set(path + ".current_out", "0");
            config.set(path + ".power", 0);

        } else if (type.equals("DISPLAY")) {
            config.set(path + ".out", outFace.name());
            config.set(path + ".current_out", "0");

            // Ustalenie bloku wyjściowego na podstawie outFace
            Block target = block.getRelative(outFace);
            Block outputBlock = target.getRelative(BlockFace.DOWN);
            config.set(path + ".oldBlock", outputBlock.getType().name());

        } else {
            // INNE BRAMKI: Używają outFace do ustalenia wyjścia
            config.set(path + ".out", outFace.name());
            config.set(path + ".state", false);
            config.set(path + ".current_out", "0");

            // Ustalenie bloku wyjściowego na podstawie outFace
            Block target = block.getRelative(outFace);
            Block outputBlock = target.getRelative(BlockFace.DOWN);
            config.set(path + ".oldBlock", outputBlock.getType().name());
        }

        // Zapisujemy Lore dla potrzeb onBreak (odzyskiwanie przedmiotu)
        if (meta.hasLore()) {
            // 1. Pobieramy komponenty z lore i bezpiecznie mapujemy je na zwykłe Stringi z kolorami '&'
            java.util.List<String> plainLore = meta.lore() != null ? meta.lore().stream()
                    .map(comp -> net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(comp))
                    .toList() : new java.util.ArrayList<>();

            // 2. Zapisujemy czystą listę Stringów bezpośrednio do pliku logic_gates.yml
            config.set(path + ".lore", plainLore);
        }

        // --- LOGIKA SPECJALNA DLA TYPÓW (SYNCHRONIZER / DISPLAY) ---
        if ("SYNCHRONIZER".equals(type)) {
            BlockFace left = GateUtils.rotate90(outFace).getOppositeFace();
            BlockFace right = GateUtils.rotate90(outFace);

            Block leftSide = block.getRelative(left);
            Block rightSide = block.getRelative(right);

            leftSide.setType(block.getType());
            rightSide.setType(block.getType());

            String locL = GateUtils.locToStr(leftSide.getLocation());
            String locR = GateUtils.locToStr(rightSide.getLocation());
            String locMain = GateUtils.locToStr(block.getLocation());

            Block targetL = leftSide.getRelative(outFace);
            config.set("gates." + locL + ".oldBlock", targetL.getRelative(BlockFace.DOWN).getType().name());

            Block targetR = rightSide.getRelative(outFace);
            config.set("gates." + locR + ".oldBlock", targetR.getRelative(BlockFace.DOWN).getType().name());

            Block targetMain = block.getRelative(outFace);
            config.set(path + ".oldBlock", targetMain.getRelative(BlockFace.DOWN).getType().name());

            config.set("gates." + locL + ".out", outFace.name());
            config.set("gates." + locR + ".out", outFace.name());
            config.set("gates." + locL + ".parent", locMain);
            config.set("gates." + locR + ".parent", locMain);
            config.set(path + ".sideL", locL);
            config.set(path + ".sideR", locR);
        }

        if ("DISPLAY".equals(type)) {
            String outName = config.getString(path + ".out", "NORTH");
            String uuid = GateUtils.createDisplay(block.getLocation(), outName);
            config.set(path + ".displayUUID", uuid);
        }

        // --- KROK 1: NAJPIERW USTAWIAMY WARTOŚCI DOMYŚLNE (Zawsze, niezależnie od Lore) ---
        if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
            config.set(path + ".interval", 0);
            config.set(path + ".next_tick", 0);
        } else if (type.matches("SENSOR")) {
            config.set(path + ".radius", 5); // Bezpieczny backup, zawsze dostanie 5 na start!
        } else if ("COUNTER".equals(type)) {
            config.set(path + ".score_limit", 10);
            config.set(path + ".count", 0);
        } else if ("RANDOM_NUMBER".equals(type)) {
            config.set(path + ".min", 0);
            config.set(path + ".max", 10);
        } else if (type.matches("NUMBER_GATE|CABLE_DATA|DECODER")) {
            config.set(path + ".value", 0);
        } else if (type.matches("DISK_GATE|RAM_GATE")) {
            config.set(path + ".value", "");
        }

        // --- KROK 2: NADPISYWANIE Z LORE (Jeśli gracz ma niestandardowe ustawienia w przedmiocie) ---
        if (meta.lore() != null) {
            for (net.kyori.adventure.text.Component componentLine : meta.lore()) {
                String cleanLine = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(componentLine);

                if (cleanLine.contains("Channel: ") || cleanLine.contains("Kanał: ")) {
                    String channelVal = cleanLine.contains("Channel: ") ? cleanLine.replace("Channel: ", "") : cleanLine.replace("Kanał: ", "");
                    config.set(path + ".channel", channelVal.trim());
                }
                else if (cleanLine.contains("min: ")) {
                    String digits = cleanLine.replaceAll("\\D+", "");
                    if (!digits.isEmpty()) config.set(path + ".min", Long.parseLong(digits));
                }
                else if (cleanLine.contains("max: ")) {
                    String digits = cleanLine.replaceAll("\\D+", "");
                    if (!digits.isEmpty()) config.set(path + ".max", Long.parseLong(digits));
                }
                else if (cleanLine.contains("Value: ") || cleanLine.contains("Wartość: ")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
                    java.util.regex.Matcher matcher = pattern.matcher(cleanLine);
                    if (matcher.find()) {
                        long val = Long.parseLong(matcher.group());
                        if (type.equals("NUMBER_GATE") || type.equals("DECODER")) {
                            config.set(path + ".value", val);
                        }
                    }
                }
                else if (cleanLine.contains("Range: ") || cleanLine.contains("Zasięg: ")) {
                    // BEZPIECZNE PARSOWANIE RADIUSA: Szuka pierwszej czystej cyfry, ignorując kolory i śmieci
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
                    java.util.regex.Matcher matcher = pattern.matcher(cleanLine);
                    if (matcher.find()) {
                        int val = Integer.parseInt(matcher.group());
                        config.set(path + ".radius", val);
                    }
                }
                else if (cleanLine.contains("Text: ") || cleanLine.contains("Tekst: ")) {
                    String textVal = cleanLine.contains("Text: ") ? cleanLine.replace("Text: ", "") : cleanLine.replace("Tekst: ", "");
                    if (type.equals("STRING_GATE") || type.equals("STRING_DECODER")) {
                        config.set(path + ".value", textVal.trim());
                    }
                }
                else if (cleanLine.contains("Mode: ") || cleanLine.contains("Tryb: ")) {
                    String rawMode = cleanLine.contains("Mode: ") ? cleanLine.replace("Mode: ", "").trim() : cleanLine.replace("Tryb: ", "").trim();

                    switch (type) {
                        case "MATH" -> {
                            String modeCode = switch (rawMode) {
                                case "Subtract" -> "SUB";
                                case "Multiply" -> "MUL";
                                case "Divide"   -> "DIV";
                                case "Power"    -> "POW";
                                default         -> "ADD";
                            };
                            config.set(path + ".mode", modeCode);
                        }
                        case "STRING_COMPARATOR" -> {
                            config.set(path + ".mode", rawMode.toUpperCase());
                        }
                        default -> {
                            config.set(path + ".mode", rawMode);
                        }
                    }
                }
                else if (cleanLine.contains("Limit: ")) {
                    String digits = cleanLine.replaceAll("\\D+", "");
                    if (!digits.isEmpty()) {
                        config.set(path + ".score_limit", Long.parseLong(digits));
                        config.set(path + ".count", 0L);
                    }
                }
                else if (cleanLine.contains("Time: ") || cleanLine.contains("Czas: ")) {
                    String timeStr = cleanLine.contains("Time: ") ? cleanLine.replace("Time: ", "").trim() : cleanLine.replace("Czas: ", "").trim();
                    int ticks = 20;

                    if (timeStr.endsWith("s")) {
                        String cleanNum = timeStr.replace("s", "").replaceAll("[^0-9.]", "");
                        if (!cleanNum.isEmpty()) {
                            ticks = (int) (Double.parseDouble(cleanNum) * 20);
                        }
                    } else {
                        String cleanNum = timeStr.replace("t", "").replaceAll("\\D+", "");
                        if (!cleanNum.isEmpty()) {
                            ticks = Integer.parseInt(cleanNum);
                        }
                    }
                    config.set(path + ".interval", ticks);
                }
            }
        }

        plugin.saveGates();

        e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("gate-placed", "{TYPE}", type)
                .replace("{OUT}", outFace.name()));
    }

    @EventHandler
    public void onGateInteract(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;

        String path = "gates." + GateUtils.locToStr(block.getLocation());
        if (!plugin.getGatesConfig().contains(path)) return;

        String type = plugin.getGatesConfig().getString(path + ".type", "");
        Player p = e.getPlayer();

        if (type.matches("COUNTER|CLOCK|CLOCK_GATE|REPEATER|SENSOR|NUMBER_GATE|DECODER|MATH|COMPARATOR|RANDOM_NUMBER")) {
            editingPlayers.put(p.getUniqueId(), path);

            p.sendMessage("");
            // Manager sam dołoży prefix
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("edit-mode-start", "{TYPE}", type));

            // Instrukcje - tutaj zazwyczaj nie dajemy prefixu, żeby czat był czytelny
            switch (type) {
                case "NUMBER_GATE", "DECODER" -> p.sendMessage(plugin.getLanguageManager().getMessage("edit-value-info"));
                case "MATH", "COMPARATOR" -> p.sendMessage(plugin.getLanguageManager().getMessage("edit-mode-info"));
                case "RANDOM_NUMBER" -> p.sendMessage(plugin.getLanguageManager().getMessage("edit-random-info"));
                case "COUNTER" -> p.sendMessage(plugin.getLanguageManager().getMessage("edit-limit-info").replace("{CURRENT}", String.valueOf(plugin.getGatesConfig().getInt(path + ".score_limit"))));
                case "SENSOR" -> p.sendMessage(plugin.getLanguageManager().getMessage("edit-range-info").replace("{CURRENT}", String.valueOf(plugin.getGatesConfig().getInt(path + ".radius"))));
                default -> p.sendMessage(plugin.getLanguageManager().getMessage("edit-time-info"));
            }

            p.sendMessage(plugin.getLanguageManager().getMessage("edit-cancel-info"));
            p.sendMessage("");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!editingPlayers.containsKey(p.getUniqueId())) return;

        String path = editingPlayers.get(p.getUniqueId());
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        String msgLower = msg.toLowerCase();
        String type = plugin.getGatesConfig().getString(path + ".type", "");
        e.setCancelled(true);

        if (msgLower.equals("cancel") || msgLower.equals("anuluj")) {
            editingPlayers.remove(p.getUniqueId());
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("edit-cancelled"));
            return;
        }

        try {
            boolean success = false;
            String langKey = "";
            String placeholder = "";
            String value = "";

            if (msgLower.startsWith("value: ") || msgLower.startsWith("val: ") || msgLower.startsWith("wartosc: ") || msgLower.startsWith("wart: ")) {
                String valPart = msg.substring(msg.indexOf(":") + 1).trim();
                int val = Integer.parseInt(valPart);
                plugin.getGatesConfig().set(path + ".value", val);
                langKey = "value-set"; placeholder = "{VAL}"; value = String.valueOf(val);
                success = true;
            }
            else if (msgLower.startsWith("mode: ") || msgLower.startsWith("tryb: ")) {
                String mode = msg.substring(msg.indexOf(":") + 1).trim().toUpperCase();
                if (type.equals("MATH")) {
                    mode = switch (mode) {
                        case "ADD", "+" -> "ADD";
                        case "SUB", "-" -> "SUB";
                        case "MUL", "*" -> "MUL";
                        case "DIV", "/" -> "DIV";
                        case "POW", "^" -> "POW";
                        default -> "ADD";
                    };
                }
                plugin.getGatesConfig().set(path + ".mode", mode);
                langKey = "mode-set"; placeholder = "{MODE}"; value = mode;
                success = true;
            }
            else if (msgLower.startsWith("limit: ")) {
                int val = Integer.parseInt(msg.substring(msg.indexOf(":") + 1).trim());
                plugin.getGatesConfig().set(path + ".score_limit", val);
                langKey = "limit-set"; placeholder = "{VAL}"; value = String.valueOf(val);
                success = true;
            }
            else if (msgLower.startsWith("min: ")) {
                int val = Integer.parseInt(msg.substring(msg.indexOf(":") + 1).trim());
                plugin.getGatesConfig().set(path + ".min", val);
                langKey = "min-set"; placeholder = "{VAL}"; value = String.valueOf(val);
                success = true;
            }
            else if (msgLower.startsWith("max: ")) {
                int val = Integer.parseInt(msg.substring(msg.indexOf(":") + 1).trim());
                plugin.getGatesConfig().set(path + ".max", val);
                langKey = "max-set"; placeholder = "{VAL}"; value = String.valueOf(val);
                success = true;
            }
            else if (msgLower.startsWith("time: ") || msgLower.startsWith("czas: ")) {
                String valStr = msg.substring(msg.indexOf(":") + 1).trim();
                int interval = valStr.endsWith("s")
                        ? (int) (Double.parseDouble(valStr.replace("s", "")) * 20)
                        : Integer.parseInt(valStr.replace("t", ""));

                plugin.getGatesConfig().set(path + ".interval", interval);
                langKey = "time-set"; placeholder = "{VAL}"; value = String.valueOf(interval);
                success = true;
            }

            if (success) {
                plugin.saveGates();
                p.sendMessage(plugin.getLanguageManager().getWithPrefix(langKey, placeholder, value));
                editingPlayers.remove(p.getUniqueId());
            } else {
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("edit-unknown-command"));
            }

        } catch (Exception ex) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("edit-format-error"));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // Update Checker
        if (plugin.getConfig().getBoolean("settings.check-updates", true) && p.hasPermission("astrars.update")) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                new UpdateChecker(plugin).getVersion(version -> {
                    if (!plugin.getDescription().getVersion().equals(version)) {
                        // Powrót do wątku gracza (Sync)
                        p.getScheduler().run(plugin, stask -> {
                            plugin.getNoticeManager().sendUpdateNotice(p, version);
                        }, null);
                    }
                });
            });
        }
    }
}
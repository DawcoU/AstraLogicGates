package pl.dawcou.AstraRedstoneSystems.gates.types;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraRedstoneSystems.system.AstraRS;
import pl.dawcou.AstraRedstoneSystems.utils.GateValidator;
import pl.dawcou.AstraRedstoneSystems.utils.GateUtils;

public class StringGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    public StringGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runStringGates() {
        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gatesSection = config.getConfigurationSection("gates");
        if (gatesSection == null) return;

        boolean debug = plugin.getConfig().getBoolean("settings.debug-mode", false);

        for (String key : gatesSection.getKeys(false)) {
            String path = "gates." + key;
            if (!validator.isValid(path, config)) continue;

            Location loc = GateUtils.strToLoc(key);
            if (loc == null) continue;

            Block gate = loc.getBlock();
            String type = config.getString(path + ".type", "").toUpperCase();

            // --- INICJALIZACJA KIERUNKÓW ---
            String outName = config.getString(path + ".out", "NORTH");
            BlockFace out = BlockFace.valueOf(outName.toUpperCase());
            BlockFace back = out.getOppositeFace();
            BlockFace right = GateUtils.rotate90(out);
            BlockFace left = right.getOppositeFace();
            Block target = gate.getRelative(out);

            boolean currentState = config.getBoolean(path + ".state", false);
            String sL = "";
            String sR = "";
            String sBack = "";

            // --- EFEKTY WIZUALNE STATUSU (ZABEZPIECZONE WARUNKAMI) ---

            // 1. Cząsteczki WYJŚCIA - odpalamy tylko dla STRING_GATE STRING_COMPARATOR i STRING_DECODER
            if (type.matches("STRING_GATE|STRING_COMPARATOR|STRING_DECODER")) {
                GateUtils.spawnStatusParticle(gate, out, currentState);
            }

            // 2. Cząsteczki WEJŚCIA Z TYŁU
            boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
            if (type.matches("STRING_GATE|STRING_DECODER")) {
                sBack = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                GateUtils.spawnStatusParticle(gate, back, pBack || !sBack.isEmpty());
            }

            // 3. Cząsteczki BOCZNE - ściśle tylko i wyłącznie dla COMPARATORA
            if (type.equals("STRING_COMPARATOR")) {
                sL = GateUtils.getStringFrom(gate.getRelative(left), left.getOppositeFace(), plugin);
                sR = GateUtils.getStringFrom(gate.getRelative(right), right.getOppositeFace(), plugin);
                GateUtils.spawnStatusParticle(gate, left, !sL.isEmpty());
                GateUtils.spawnStatusParticle(gate, right, !sR.isEmpty());
            }

            // --- LOGIKA BRAMEK ---
            switch (type) {
                case "STRING_GATE" -> {
                    String storedText = config.getString(path + ".value", "");
                    String finalText = pBack ? storedText : "";
                    String lastOut = config.getString(path + ".current_out", "");

                    if (!finalText.equals(lastOut)) {
                        if (debug) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§dSTRING_GATE §7na §e" + key + " §7wypuścił tekst: §5\"" + finalText + "\""
                            );
                        }
                        config.set(path + ".current_out", finalText);
                        config.set(path + ".state", pBack);
                        GateUtils.updateOutput(plugin, path, target, pBack);
                    }
                }

                case "STRING_COMPARATOR" -> {
                    // 1. Pobieranie danych z lewej (główny tekst) i prawej (tekst do porównania/próg)
                    String mode = config.getString(path + ".mode", "EQUALS").toUpperCase();

                    // OCHRONA: Jeśli główny lewy string jest pusty, natychmiast gasimy bramkę
                    if (sL.isEmpty()) {
                        String currentOut = config.getString(path + ".current_out", "");

                        if (!currentOut.isEmpty()) {
                            config.set(path + ".current_out", "");
                            config.set(path + ".state", false);
                            GateUtils.updateOutput(plugin, path, target, false);
                        }
                        continue;
                    }

                    // 2. Logika porównania
                    boolean result = switch (mode) {
                        case "EQUALS"              -> sL.equals(sR);
                        case "EQUALS_IGNORE_CASE"  -> sL.equalsIgnoreCase(sR);
                        case "CONTAINS"            -> sL.contains(sR);
                        case "STARTS_WITH"         -> sL.startsWith(sR);
                        case "ENDS_WITH"           -> sL.endsWith(sR);
                        default                    -> false;
                    };

                    // 3. Ustalenie co leci na wyjście danych
                    String finalVal = result ? sL : "";
                    String lastOutStr = config.getString(path + ".current_out", "");

                    // 4. Aktualizacja stanu i wyjścia
                    if (!finalVal.equals(lastOutStr)) {
                        if (debug) {
                            String matchColor = result ? "§a" : "§c";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§dSTRING_COMP §7na §e" + key + " §7wynik (§6" + mode + "§7): " + matchColor + (result ? "TRUE" : "FALSE") + " §8[Wysyła: \"" + finalVal + "\"]"
                            );
                        }
                        config.set(path + ".current_out", finalVal);
                        config.set(path + ".state", result);

                        // Przekazujemy wynik logiczny do metody Redstone'a
                        GateUtils.updateOutput(plugin, path, target, result);
                    }
                }

                case "STRING_DECODER" -> {
                    String targetValue = config.getString(path + ".value", "");

                    boolean isMatch = !sBack.isEmpty() && sBack.equals(targetValue);
                    String finalVal = isMatch ? "1" : "";

                    String lastOut = config.getString(path + ".current_out", "");

                    if (!finalVal.equals(lastOut)) {
                        if (debug) {
                            plugin.getLogger().info("[AstraDebug] STRING_DECODER " + key + " -> '" + sBack + "'");
                        }
                        config.set(path + ".current_out", finalVal);
                        config.set(path + ".state", isMatch);
                        GateUtils.updateOutput(plugin, path, target, isMatch);
                    }
                }
            }
        }
    }
}
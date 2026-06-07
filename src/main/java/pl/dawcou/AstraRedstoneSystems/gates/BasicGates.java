package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraRedstoneSystems.AstraRS;
import pl.dawcou.AstraRedstoneSystems.GateValidator;
import pl.dawcou.AstraRedstoneSystems.GateUtils;

public class BasicGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    public BasicGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runBasicGates() {
        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gatesSection = config.getConfigurationSection("gates");
        if (gatesSection == null) return;

        for (String key : gatesSection.getKeys(false)) {
            String path = "gates." + key;
            if (!validator.isValid(path, config)) continue;

            Location loc = GateUtils.strToLoc(key);
            if (loc == null) continue;

            Block gate = loc.getBlock();
            String type = config.getString(path + ".type", "").toUpperCase();

            // UJEDNOLICONE ZMIENNE
            BlockFace out = BlockFace.valueOf(config.getString(path + ".out", "NORTH").toUpperCase());
            BlockFace back = out.getOppositeFace();
            BlockFace s1 = GateUtils.rotate90(out);
            BlockFace s2 = s1.getOppositeFace();

            boolean currentState = config.getBoolean(path + ".state", false);
            Block target = gate.getRelative(out);

            // POBIERANIE SYGNAŁÓW
            boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
            boolean pS1 = GateUtils.getPowerAt(gate.getRelative(s1)) > 0;
            boolean pS2 = GateUtils.getPowerAt(gate.getRelative(s2)) > 0;

            // --- PARTICLE STATUSU (WEJŚCIA) ---
            // Usunęliśmy stąd rysowanie przodu (out), zostały tylko wejścia!
            if (type.matches("OR|NOR|AND|NAND|XOR|XNOR|NIMPLY|IMPLY|MUX")) {
                GateUtils.spawnStatusParticle(gate, s1, pS1);
                GateUtils.spawnStatusParticle(gate, s2, pS2);
            }
            if (type.matches("NOT|BUFFER|NIMPLY|IMPLY|MUX|PULSER")) {
                GateUtils.spawnStatusParticle(gate, back, pBack);
            }

            // LOGIKA
            boolean newState = false;

            newState = switch (type) {
                case "NOT" -> !pBack;
                case "OR" -> (pS1 || pS2 || pBack);
                case "NOR" -> !(pS1 || pS2 || pBack);
                case "AND" -> (pS1 && pS2);
                case "NAND" -> !(pS1 && pS2);
                case "XOR" -> (pS1 ^ pS2);
                case "XNOR" -> (pS1 == pS2);
                case "IMPLY" -> !pBack || (pS1 || pS2);
                case "NIMPLY" -> pBack && !(pS1 || pS2);
                case "BUFFER" -> pBack;
                case "MUX" -> pBack ? pS1 : pS2;
                case "PULSER" -> {
                    boolean in = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean lastIn = config.getBoolean(path + ".last_input_state", false);
                    boolean result = false;

                    if (in && !lastIn) {
                        result = true;
                    }
                    config.set(path + ".last_input_state", in);
                    yield result;
                }
                case "SYNCHRONIZER" -> {
                    boolean pA = GateUtils.getPowerAt(gate.getRelative(s1).getRelative(back)) > 0;
                    boolean pB = GateUtils.getPowerAt(gate.getRelative(s2).getRelative(back)) > 0;

                    GateUtils.spawnStatusParticle(gate.getRelative(s1), back, pA);
                    GateUtils.spawnStatusParticle(gate.getRelative(s2), back, pB);

                    yield (pA && pB);
                }
                default -> currentState;
            };

            // --- UNIWERSALNA AKTUALIZACJA ---
            if (type.equals("SYNCHRONIZER")) {
                GateUtils.spawnStatusParticle(gate.getRelative(s1), out, newState);
                GateUtils.spawnStatusParticle(gate.getRelative(s2), out, newState);

                String sideLLoc = config.getString(path + ".sideL");
                String sideRLoc = config.getString(path + ".sideR");

                if (sideLLoc != null) {
                    GateUtils.updateOutput(plugin, "gates." + sideLLoc, gate.getRelative(s1).getRelative(out), newState);
                }
                if (sideRLoc != null) {
                    GateUtils.updateOutput(plugin, "gates." + sideRLoc, gate.getRelative(s2).getRelative(out), newState);
                }
            } else {
                // 1. Najpierw aktualizujemy blok fizyczny w świecie
                GateUtils.updateOutput(plugin, path, target, newState);

                // 2. Zapisujemy świeży, obliczony stan do configu
                config.set(path + ".state", newState);

                // 3. JEDYNE I SŁUSZNE MIEJSCE NA DYMEK Z PRZODU NA BAZIE AKTUALNEGO newState! 🟢
                if (type.matches("OR|NOR|AND|NAND|XOR|XNOR|NOT|BUFFER|IMPLY|NIMPLY|MUX|PULSER")) {
                    GateUtils.spawnStatusParticle(gate, out, newState);
                }
            }
        }
    }
}
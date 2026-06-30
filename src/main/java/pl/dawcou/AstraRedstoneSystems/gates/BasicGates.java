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
            BlockFace right = GateUtils.rotate90(out);
            BlockFace left = right.getOppositeFace();

            boolean currentState = config.getBoolean(path + ".state", false);
            Block target = gate.getRelative(out);

            // POBIERANIE SYGNAŁÓW
            boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
            boolean pRight = GateUtils.getPowerAt(gate.getRelative(right)) > 0;
            boolean pLeft = GateUtils.getPowerAt(gate.getRelative(left)) > 0;

            // --- PARTICLE STATUSU (WEJŚCIA) ---
            if (type.matches("OR|NOR|AND|NAND|XOR|XNOR|NIMPLY|IMPLY|MUX")) {
                GateUtils.spawnStatusParticle(gate, right, pRight);
                GateUtils.spawnStatusParticle(gate, left, pLeft);
            }
            if (type.matches("NOT|BUFFER|NIMPLY|IMPLY|MUX")) {
                GateUtils.spawnStatusParticle(gate, back, pBack);
            }

            // LOGIKA
            boolean newState = switch (type) {
                case "NOT" -> !pBack;
                case "OR" -> (pRight || pLeft || pBack);
                case "NOR" -> !(pRight || pLeft || pBack);
                case "AND" -> (pRight && pLeft);
                case "NAND" -> !(pRight && pLeft);
                case "XOR" -> (pRight ^ pLeft);
                case "XNOR" -> (pRight == pLeft);
                case "IMPLY" -> !pBack || (pRight || pLeft);
                case "NIMPLY" -> pBack && !(pRight || pLeft);
                case "BUFFER" -> pBack;
                case "MUX" -> pBack ? pRight : pLeft;
                case "SYNCHRONIZER" -> {
                    boolean pA = GateUtils.getPowerAt(gate.getRelative(right).getRelative(back)) > 0;
                    boolean pB = GateUtils.getPowerAt(gate.getRelative(left).getRelative(back)) > 0;

                    GateUtils.spawnStatusParticle(gate.getRelative(right), back, pA);
                    GateUtils.spawnStatusParticle(gate.getRelative(left), back, pB);

                    yield (pA && pB);
                }
                default -> currentState;
            };

            // --- UNIWERSALNA AKTUALIZACJA ---
            if (type.equals("SYNCHRONIZER")) {
                GateUtils.spawnStatusParticle(gate.getRelative(right), out, newState);
                GateUtils.spawnStatusParticle(gate.getRelative(left), out, newState);

                String sideLLoc = config.getString(path + ".sideL");
                String sideRLoc = config.getString(path + ".sideR");

                if (sideLLoc != null) {
                    GateUtils.updateOutput(plugin, "gates." + sideLLoc, gate.getRelative(right).getRelative(out), newState);
                }
                if (sideRLoc != null) {
                    GateUtils.updateOutput(plugin, "gates." + sideRLoc, gate.getRelative(left).getRelative(out), newState);
                }
            } else {
                if (newState != currentState) {
                    GateUtils.updateOutput(plugin, path, target, newState);
                    config.set(path + ".state", newState);
                }

                // 3. JEDYNE I SŁUSZNE MIEJSCE NA DYMEK Z PRZODU NA BAZIE AKTUALNEGO newState
                if (type.matches("OR|NOR|AND|NAND|XOR|XNOR|NOT|BUFFER|IMPLY|NIMPLY|MUX|PULSER")) {
                    GateUtils.spawnStatusParticle(gate, out, newState);
                }
            }
        }
    }
}
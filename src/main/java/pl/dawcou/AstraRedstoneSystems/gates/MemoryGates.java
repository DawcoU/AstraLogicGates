package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraRedstoneSystems.AstraRS;
import pl.dawcou.AstraRedstoneSystems.GateValidator;
import pl.dawcou.AstraRedstoneSystems.GateUtils;

public class MemoryGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    public MemoryGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runMemoryGates() {
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

            // UJEDNOLICONE ZMIENNE KIERUNKOWE
            BlockFace out = BlockFace.valueOf(config.getString(path + ".out", "NORTH").toUpperCase());
            BlockFace back = out.getOppositeFace();
            BlockFace right = GateUtils.rotate90(out);
            BlockFace left = right.getOppositeFace();

            Block target = gate.getRelative(out);

            // POBIERANIE SYGNAŁÓW WEJŚCIOWYCH
            boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
            boolean pRight = GateUtils.getPowerAt(gate.getRelative(right)) > 0;
            boolean pLeft = GateUtils.getPowerAt(gate.getRelative(left)) > 0;

            boolean oldState = config.getBoolean(path + ".state", false);
            boolean newState = oldState;

            // LOGIKA BRAMEK
            switch (type) {
                case "LATCH" -> {
                    // RS Latch: right = Set, left = Reset
                    if (pRight) newState = true;
                    else if (pLeft) newState = false;
                }
                case "MEMORY_CELL" -> {
                    // Tył = Data, Prawo = Write, Lewo = Read

                    boolean memory = config.getBoolean(path + ".memory", false);

                    // ZAPIS
                    if (pRight) {
                        memory = pBack;
                        config.set(path + ".memory", memory);
                    }

                    // ODCZYT
                    newState = memory && pLeft;
                }
                case "TFF" -> {
                    // Toggle Flip-Flop: Zbocze narastające na tyłach
                    boolean lastIn = config.getBoolean(path + ".lastInput", false);
                    if (pBack != lastIn) {
                        if (pBack) { // Jeśli to zbocze narastające (wejście prądu)
                            newState = !oldState;
                        }
                        config.set(path + ".lastInput", pBack);
                    }
                }
            }

            if (newState != oldState) {
                config.set(path + ".state", newState);
                GateUtils.updateOutput(plugin, path, target, newState);
            }

            if (type.matches("LATCH|MEMORY_CELL|TFF")) {
                GateUtils.spawnStatusParticle(gate, out, newState);
            }
            if (type.matches("LATCH|MEMORY_CELL")) {
                GateUtils.spawnStatusParticle(gate, right, pRight);
                GateUtils.spawnStatusParticle(gate, left, pLeft);
            }
            if (type.matches("TFF|MEMORY_CELL")) {
                GateUtils.spawnStatusParticle(gate, back, pBack);
            }
        }
    }
}
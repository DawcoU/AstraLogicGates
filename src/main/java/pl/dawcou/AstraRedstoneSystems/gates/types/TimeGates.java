package pl.dawcou.AstraRedstoneSystems.gates.types;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

import pl.dawcou.AstraRedstoneSystems.system.AstraRS;
import pl.dawcou.AstraRedstoneSystems.utils.GateValidator;
import pl.dawcou.AstraRedstoneSystems.utils.GateUtils;

public class TimeGates {

    private final AstraRS plugin;
    private final GateValidator validator;
    private final Map<String, ScheduledTask> repeaterTasks = new HashMap<>();

    public TimeGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runTimeGates() {
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

            BlockFace out = BlockFace.valueOf(config.getString(path + ".out", "NORTH").toUpperCase());
            BlockFace back = out.getOppositeFace();
            BlockFace right = GateUtils.rotate90(out);
            BlockFace left = right.getOppositeFace();

            boolean currentState = config.getBoolean(path + ".state", false);
            Block target = gate.getRelative(out);

            // --- PARTICLE STATUSU & POBIERANIE SYGNAŁU ---
            if (type.matches("CLOCK_GATE|CLOCK|REPEATER|PULSER")) {
                // Wyjście główne
                GateUtils.spawnStatusParticle(gate, out, currentState);
            }

            boolean pBack = false;
            if (type.matches("CLOCK_GATE|REPEATER|PULSER")) {
                pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                GateUtils.spawnStatusParticle(gate, back, pBack);
            }

            // --- LOGIKA ---
            switch (type) {
                case "SYNCHRONIZER" -> {
                    boolean pRight = GateUtils.getPowerAt(gate.getRelative(right).getRelative(back)) > 0;
                    boolean pLeft = GateUtils.getPowerAt(gate.getRelative(left).getRelative(back)) > 0;
                    boolean ready = pRight && pLeft;

                    if (ready != currentState) {
                        config.set(path + ".state", ready);
                        GateUtils.updateOutput(plugin, path + "_L", gate.getRelative(right).getRelative(out), ready);
                        GateUtils.updateOutput(plugin, path + "_R", gate.getRelative(left).getRelative(out), ready);
                    }
                    GateUtils.spawnStatusParticle(gate.getRelative(right), back, pRight);
                    GateUtils.spawnStatusParticle(gate.getRelative(left), back, pLeft);
                }

                case "REPEATER" -> {
                    if (pBack != config.getBoolean(path + ".last_in", false)) {
                        config.set(path + ".last_in", pBack);

                        if (repeaterTasks.containsKey(path)) {
                            repeaterTasks.get(path).cancel();
                        }

                        int delay = config.getInt(path + ".interval", 20);

                        final boolean finalPower = pBack;

                        ScheduledTask task = plugin.getServer().getRegionScheduler().runDelayed(plugin, gate.getLocation(), scheduledTask -> {
                            config.set(path + ".state", finalPower);
                            GateUtils.updateOutput(plugin, path, target, finalPower);
                            repeaterTasks.remove(path);
                        }, (long) delay);

                        repeaterTasks.put(path, task);
                    }
                }

                case "PULSER" -> {
                    boolean lastIn = config.getBoolean(path + ".lastInput", false);
                    boolean result = pBack && !lastIn;

                    config.set(path + ".lastInput", pBack);

                    if (result != currentState) {
                        config.set(path + ".state", result);
                        GateUtils.updateOutput(plugin, path, target, result);
                    }
                }

                case "CLOCK", "CLOCK_GATE" -> {
                    int interval = config.getInt(path + ".interval", 20);
                    boolean hasPower = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean enabled = "CLOCK".equals(type) || hasPower;

                    if (!enabled) {
                        if (currentState) {
                            GateUtils.updateOutput(plugin, path, target, false);
                            config.set(path + ".state", false);
                        }
                        config.set(path + ".next_tick", 0);
                    } else {
                        int nt = config.getInt(path + ".next_tick", 0) + 1;
                        if (nt >= interval) {
                            boolean newState = !currentState;
                            config.set(path + ".state", newState);

                            GateUtils.updateOutput(plugin, path, target, newState);
                            nt = 0;
                        }
                        config.set(path + ".next_tick", nt);
                    }
                }
            }
        }
    }
}
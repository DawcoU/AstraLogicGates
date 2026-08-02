package pl.dawcou.AstraRedstoneSystems.gates.types;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.AstraRedstoneSystems.system.AstraRS;
import pl.dawcou.AstraRedstoneSystems.utils.GateValidator;
import pl.dawcou.AstraRedstoneSystems.utils.GateUtils;

import java.util.HashSet;
import java.util.Set;

public class SpaceGates {
    private final AstraRS plugin;
    private final GateValidator validator;
    private long lastCleanup = 0;

    public SpaceGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runSpaceGates() {
        FileConfiguration config = plugin.getGatesConfig();

        // --- CZYŚCICIEL MARTWYCH KANAŁÓW (co 5 minut) ---
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanup > 300000) { // 300,000 ms = 5 min
            cleanDeadChannels(config);
            lastCleanup = currentTime;
        }

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
            Block target = gate.getRelative(out);

            // --- SWITCH LOGIKI ---
            switch (type) {
                case "SENDER" -> {
                    String rawChannels = config.getString(path + ".channel", "default");
                    String[] splitChannels = rawChannels.split(",");

                    // 1. Pobieranie danych
                    String incomingData = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    int traditionalPower = GateUtils.getPowerAt(gate.getRelative(back));

                    // 2. Protokół: Ustalamy co ma lecieć na magistralę
                    String signalToBroadcast = null;

                    if (!incomingData.isEmpty() && !incomingData.equals("-2147483648") && !incomingData.equals("-9223372036854775808")) {
                        signalToBroadcast = incomingData;
                    } else if (traditionalPower > 0) {
                        signalToBroadcast = "_REDSTONE_";
                    }

                    // 3. Sprawdzanie zmiany stanu
                    String lastTransmitted = config.getString(path + ".current_out", "");
                    String currentVal = (signalToBroadcast != null) ? signalToBroadcast : "";

                    if (!currentVal.equals(lastTransmitted)) {
                        config.set(path + ".current_out", currentVal);
                        config.set(path + ".state", signalToBroadcast != null);

                        for (String chan : splitChannels) {
                            String trimmed = chan.trim();
                            config.set("channels." + trimmed, signalToBroadcast);
                            config.set("active_channels." + trimmed, signalToBroadcast != null);
                        }
                    }

                    // 4. Bezpieczne sprawdzanie listenerów
                    boolean hasListener = false;
                    for (String chan : splitChannels) {
                        if (config.getBoolean("active_channels." + chan.trim(), false)) {
                            hasListener = true;
                            break;
                        }
                    }

                    // 5. Particle
                    boolean isTransmitting = (signalToBroadcast != null) && hasListener;
                    GateUtils.spawnStatusParticle(gate, out, isTransmitting);
                    GateUtils.spawnStatusParticle(gate, back, signalToBroadcast != null);
                }

                case "RECEIVER" -> {
                    String channel = config.getString(path + ".channel", "default").trim().replace(" ", "");
                    String payload = config.getString("channels." + channel, "");

                    boolean shouldOutput = false;
                    String dataToPass = "";

                    if (payload.equals("_REDSTONE_")) {
                        // To jest tylko prąd, nie traktuj tego jako dane!
                        shouldOutput = true;
                        dataToPass = ""; // Puste dane, sam sygnał
                    } else if (!payload.isEmpty()) {
                        // To są realne dane
                        shouldOutput = true;
                        dataToPass = payload;
                    }

                    // Zapis stanu
                    config.set(path + ".current_out", dataToPass);
                    config.set(path + ".state", shouldOutput);

                    // Update Output
                    GateUtils.updateOutput(plugin, path, target, shouldOutput);
                    GateUtils.spawnStatusParticle(gate, out, shouldOutput);
                }

                case "SENSOR" -> {
                    // Rzutujemy na double, żeby Paper idealnie obliczył wektory zasięgu wokół bloku
                    double radius = config.getInt(path + ".radius", 5);
                    boolean found = false;

                    // Pobieramy tylko graczy w promieniu – to na pewno zadziała i nie zlaguje serwera
                    java.util.Collection<Player> players = gate.getWorld().getNearbyPlayers(gate.getLocation(), radius, radius, radius);
                    if (!players.isEmpty()) {
                        found = true;
                    }

                    if (found != config.getBoolean(path + ".state", false)) {
                        config.set(path + ".state", found);
                        GateUtils.updateOutput(plugin, path, target, found);
                    }

                    GateUtils.spawnStatusParticle(gate, out, found);
                }
            }
        }
    }

    private void cleanDeadChannels(FileConfiguration config) {
        ConfigurationSection gates = config.getConfigurationSection("gates");
        if (gates == null) return;

        Set<String> usedChannels = new HashSet<>();
        // Skanujemy wszystkie bramki, żeby zobaczyć jakie kanały są w użyciu
        for (String key : gates.getKeys(false)) {
            String ch = config.getString("gates." + key + ".channel");
            if (ch != null) {
                for (String split : ch.split(",")) {
                    usedChannels.add(split.trim());
                }
            }
        }

        // Usuwamy z sekcji channels i active_channels to, czego nie ma w bramkach
        String[] sections = {"channels", "active_channels"};
        for (String secName : sections) {
            ConfigurationSection section = config.getConfigurationSection(secName);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if (!usedChannels.contains(key)) {
                        config.set(secName + "." + key, null);
                    }
                }
            }
        }
    }
}
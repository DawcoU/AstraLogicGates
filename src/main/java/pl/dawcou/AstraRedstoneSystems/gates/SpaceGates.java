package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import pl.dawcou.AstraRedstoneSystems.AstraRS;
import pl.dawcou.AstraRedstoneSystems.GateValidator;
import pl.dawcou.AstraRedstoneSystems.GateUtils;

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

            boolean currentState = config.getBoolean(path + ".state", false);
            Block target = gate.getRelative(out);
            boolean newState = currentState;

            // --- SWITCH LOGIKI ---
            switch (type) {
                case "SENDER":
                    String rawChannels = config.getString(path + ".channel", "default");
                    String[] splitChannels = rawChannels.split(",");
                    boolean hasListener = false;

                    // 1. Pobieramy uniwersalny sygnał jako String z tyłu (obsłuży kable danych, liczby, wszystko)
                    String incomingData = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                    // 2. Jeśli z tyłu nie ma sygnału tekstowego, sprawdzamy tradycyjny redstone / dźwignię
                    if (incomingData.isEmpty()) {
                        int traditionalPower = GateUtils.getPowerAt(gate.getRelative(back));
                        if (traditionalPower > 0) {
                            incomingData = "1"; // Zamieniamy na String "1" dla kompatybilności binarnej
                        }
                    }

                    // 🔥 ZMIANA: Sygnał istnieje, jeśli string nie jest pusty. Zera "0" NIE ignorujemy!
                    boolean hasValue = !incomingData.isEmpty();

                    // 3. Rozsyłamy dane na kanały jako czysty String
                    for (String chan : splitChannels) {
                        String trimmed = chan.trim();

                        // Konsekwentnie zapisujemy jako tekst na kanale
                        config.set("channels." + trimmed, incomingData);

                        if (config.getBoolean("active_channels." + trimmed, false)) hasListener = true;
                        config.set("active_channels." + trimmed, null);
                    }

                    boolean isTransmitting = hasValue && hasListener;
                    GateUtils.spawnStatusParticle(gate, out, isTransmitting);
                    GateUtils.spawnStatusParticle(gate, back, hasValue);
                    newState = false;
                    break;

                case "RECEIVER":
                    String channel = config.getString(path + ".channel", "default").trim().replace(" ", "");
                    config.set("active_channels." + channel, true);

                    // 🔥 ZMIANA: Pobieramy dane z kanału zawsze jako String! Domyślnie pustka ""
                    String transmittedData = config.getString("channels." + channel, "");

                    // Transmisja jest aktywna, jeśli kanał nie jest pusty
                    boolean hasReceivedPower = !transmittedData.isEmpty();

                    newState = hasReceivedPower;

                    // Pobieramy poprzednią wartość z configu odbiornika zawsze jako String
                    String lastOut = config.getString(path + ".current_out", "");

                    // Porównujemy czyste Stringi – koniec z błędami typów (Integer vs String)!
                    if (!transmittedData.equals(lastOut)) {
                        config.set(path + ".current_out", transmittedData);
                        config.set(path + ".state", hasReceivedPower);

                        // Przekazujemy stan do aktualizacji bloku przed odbiornikiem
                        GateUtils.updateOutput(plugin, path, target, hasReceivedPower);
                    }

                    GateUtils.spawnStatusParticle(gate, out, newState);
                    break;

                case "SENSOR":
                    int radius = config.getInt(path + ".interval", 5);
                    boolean found = false;
                    for (Entity entity : gate.getWorld().getNearbyEntities(gate.getLocation(), radius, radius, radius)) {
                        if (entity instanceof Player) {
                            found = true;
                            break;
                        }
                    }
                    newState = found;
                    GateUtils.spawnStatusParticle(gate, out, newState);
                    break;
            }

            // --- UNIWERSALNA AKTUALIZACJA ---
            if (newState != currentState) {
                config.set(path + ".state", newState);
                GateUtils.updateOutput(plugin, path, target, newState);
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
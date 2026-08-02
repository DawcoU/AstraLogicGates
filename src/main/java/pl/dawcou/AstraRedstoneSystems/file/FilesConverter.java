package pl.dawcou.AstraRedstoneSystems.file;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.AstraRedstoneSystems.system.AstraRS;

import java.io.File;
import java.io.IOException;

public class FilesConverter {

    private final AstraRS plugin;

    public FilesConverter(AstraRS plugin) {
        this.plugin = plugin;
    }

    // Główna metoda, która zarządza wszystkimi konwersjami
    public void runAllMigrations() {
        // Konwersja bramek
        migrateFile("gates.yml", "logic_gates.yml");
        renameGates();
    }

    // Uniwersalna metoda do przenoszenia plików
    private void migrateFile(String oldName, String newName) {
        File oldFile = new File(plugin.getDataFolder(), oldName);
        File newFile = new File(plugin.getDataFolder(), newName);

        if (oldFile.exists() && !newFile.exists()) {
            plugin.getNoticeManager().sendMigrationNotice(oldName, newName);

            try {
                FileConfiguration data = YamlConfiguration.loadConfiguration(oldFile);
                data.save(newFile);

                // Zmiana nazwy na .old
                File backup = new File(plugin.getDataFolder(), oldName + ".old");
                if (oldFile.renameTo(backup)) {
                    plugin.getNoticeManager().sendSuccessNotice(oldName);
                }
            } catch (IOException e) {
                plugin.getNoticeManager().sendErrorNotice(oldName);
                e.printStackTrace();
            }
        }
    }

    private void renameGates() {
        File gatesFile = new File(plugin.getDataFolder(), "logic_gates.yml");

        if (gatesFile.exists()) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(gatesFile);
                ConfigurationSection gatesSection = config.getConfigurationSection("gates");

                if (gatesSection != null) {
                    boolean changesMade = false;

                    // Przejeżdżamy po lokalizacjach bramek
                    for (String key : gatesSection.getKeys(false)) {
                        ConfigurationSection singleGate = gatesSection.getConfigurationSection(key);
                        if (singleGate == null) continue;

                        if (singleGate.contains("type")) {
                            String currentType = singleGate.getString("type");

                            if ("VARIABLE_GATE".equals(currentType)) {
                                singleGate.set("type", "DISK_GATE");
                                changesMade = true;
                            }
                        }
                    }

                    if (changesMade) {
                        config.save(gatesFile);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
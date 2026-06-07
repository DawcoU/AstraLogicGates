package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
}
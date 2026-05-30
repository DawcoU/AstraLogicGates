package pl.dawcou.AstraRedstoneSystems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {

    private final JavaPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupFiles(); // Najpierw upewniamy się, że pliki są na dysku
        reload();     // Potem ładujemy je do RAMu
    }

    public void reload() {
        // 1. Czyścimy mapę, żeby nie dublować przy przeładowaniu
        messages.clear();

        String lang = plugin.getConfig().getString("settings.language", "pl");
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        if (!langFile.exists()) {
            langFile = new File(plugin.getDataFolder(), "languages/pl.yml");
        }

        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);

        // 2. Pobieramy sekcję "messages" z pliku YAML
        ConfigurationSection msgSection = langConfig.getConfigurationSection("messages");

        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                String msg = msgSection.getString(key);
                if (msg != null) {
                    // Konwertujemy stary format '&' na format Adventure (MiniMessage kompatybilny ze starymi kolorami)
                    messages.put(key, parseToLegacy(msg));
                }
            }
        } else {
            // Jeśli plik nie ma sekcji "messages:", czytamy wszystko z głównego poziomu
            for (String key : langConfig.getKeys(false)) {
                if (langConfig.isString(key)) {
                    messages.put(key, parseToLegacy(langConfig.getString(key)));
                }
            }
        }
    }

    private void setupFiles() {
        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) langFolder.mkdirs();

        String[] defaultLangs = {"pl.yml", "en.yml"};
        for (String langFile : defaultLangs) {
            File file = new File(langFolder, langFile);
            if (!file.exists()) {
                plugin.saveResource("languages/" + langFile, false);
            }
        }
    }

    /**
     * Pomocnicza metoda zamieniająca tagi MiniMessage (i opcjonalnie stare kody '&')
     * na tradycyjny format kolorów (§), który zwracamy jako String do wiadomości i configów.
     */
    private String parseToLegacy(String text) {
        if (text == null) return "";

        // Upewniamy się, że ampersandy są ujednolicone do paragrafów §
        String formatted = text.replace("&", "§");

        // Jeśli tekst zawiera tagi MiniMessage, musimy go bezpiecznie przeparsować
        if (formatted.contains("<") && formatted.contains(">")) {
            // HACK: Przekształcamy stare kolory (§) na format akceptowalny przez MiniMessage
            // Dzięki temu MiniMessage nie wyrzuci błędu o wykryciu starego formatowania!
            String hexCompat = MiniMessage.miniMessage().serialize(
                    LegacyComponentSerializer.legacySection().deserialize(formatted)
            );

            // Teraz bezpiecznie parsujemy całość i zwracamy jako stary format systemowy
            Component parsed = MiniMessage.miniMessage().deserialize(hexCompat);
            return LegacyComponentSerializer.legacySection().serialize(parsed);
        }

        return formatted;
    }

    // Pobiera czystą wiadomość z mapy
    public String getMessage(String path) {
        return messages.getOrDefault(path, "§cMissing string: " + path);
    }

    public String getWithPrefix(String path) {
        // Konwertujemy prefix za pomocą parseToLegacy na wypadek, gdyby AstraLogin.PREFIX zawierał tagi HEX
        return parseToLegacy(AstraRS.PREFIX) + " " + getMessage(path);
    }

    // Metoda z placeholderem (np. do {COUNT})
    public String getWithPrefix(String path, String placeholder, String value) {
        String msg = getMessage(path).replace(placeholder, value);
        return parseToLegacy(AstraRS.PREFIX) + " " + msg;
    }
}
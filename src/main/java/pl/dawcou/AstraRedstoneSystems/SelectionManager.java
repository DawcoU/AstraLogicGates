package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SelectionManager implements Listener {

    private final AstraRS plugin;
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Map<UUID, List<Location>> pasteHistory = new HashMap<>();
    private final Map<UUID, Map<Location, ConfigurationSection>> redoHistory = new HashMap<>();
    private final Map<UUID, Map<org.bukkit.util.Vector, ConfigurationSection>> clipboard = new HashMap<>();

    public SelectionManager(AstraRS plugin) {
        this.plugin = plugin;
    }

    // Metoda do dawania świecącego patyka
    public void giveSelector(Player player) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        if (meta != null) {
            // Pobieramy nazwę selektora bezpośrednio z klasy językowej
            String langName = plugin.getLanguageManager().getMessage("selector-item-name");
            if (langName == null || langName.isEmpty()) {
                langName = "&dSelektor Bramek"; // Backup, gdyby klucza zabrakło
            }

            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(langName));

            // ZASZYWANIE DANYCH W PRZEDMIOCIE (PDC)
            // Tworzymy unikalny klucz logiczny
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "item_type");
            meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, "gate_selector");

            // Efekt świecenia
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            stick.setItemMeta(meta);
        }

        player.getInventory().addItem(stick);
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("receive-selector"));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Szybki powrót, jeśli gracz klika powietrze pusta ręką
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta currentMeta = item.getItemMeta();
        if (currentMeta == null) return;

        // 1. SPRAWDZAMY UKRYTE DANE PDC ZAMIAST TEXTU NAZWY
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "item_type");
        if (!currentMeta.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) return;

        String itemType = currentMeta.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (!"gate_selector".equals(itemType)) return;

        // 2. POTĘŻNA BLOKADA BEZPIECZEŃSTWA (PERMISJA)
        // Sprawdzamy ją OD RAZU po wykryciu, że to na pewno jest nasz selektor
        if (!player.hasPermission("astrars.admin")) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
            return;
        }

        // Jeśli nie kliknął bloku (np. kliknął powietrze), przerywamy dalszą logikę pozycji
        if (event.getClickedBlock() == null) return;

        // Blokujemy domyślne akcje Minecrafta dla patyka (np. uderzanie bloków)
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        Location clickedLoc = event.getClickedBlock().getLocation();

        if (!selections.containsKey(uuid)) selections.put(uuid, new Location[2]);
        Location[] playerSelections = selections.get(uuid);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Sprawdzamy, czy pozycja 1 jest inna niż kliknięta
            if (playerSelections[0] == null || !playerSelections[0].equals(clickedLoc)) {
                playerSelections[0] = clickedLoc;
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("position-1-selected"));
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Sprawdzamy, czy pozycja 2 jest inna niż kliknięta
            if (playerSelections[1] == null || !playerSelections[1].equals(clickedLoc)) {
                playerSelections[1] = clickedLoc;
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("position-2-selected"));
            }
        }
    }

    public void cutSelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (!selections.containsKey(uuid) || selections.get(uuid)[0] == null || selections.get(uuid)[1] == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("both-positions-required"));
            return;
        }

        Location loc1 = selections.get(uuid)[0];
        Location loc2 = selections.get(uuid)[1];
        World world = loc1.getWorld();

        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        // 1. USUWANIE WSZYSTKICH BLOKÓW W OBSZARZE
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        // 2. USUWANIE LOGIKI BRAMEK Z CONFIGU
        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gates = config.getConfigurationSection("gates");
        int removedGates = 0;

        if (gates != null) {
            for (String key : gates.getKeys(false)) {
                try {
                    Location gateLoc = GateUtils.strToLoc(key);
                    if (gateLoc == null) continue;

                    // SPRAWDZENIE ŚWIATA! (Bardzo ważne, żeby nie usuwać bramek z innych światów o tych samych kordach)
                    if (!gateLoc.getWorld().equals(world)) continue;

                    int gx = gateLoc.getBlockX();
                    int gy = gateLoc.getBlockY();
                    int gz = gateLoc.getBlockZ();

                    if (gx >= minX && gx <= maxX && gy >= minY && gy <= maxY && gz >= minZ && gz <= maxZ) {
                        ConfigurationSection data = gates.getConfigurationSection(key);
                        if (data != null) {

                            // --- DODATKOWE SPRZĄTANIE (np. dla DISPLAY) ---
                            String type = data.getString("type", "");
                            if ("DISPLAY".equals(type)) {
                                String uuidStr = data.getString("displayUUID");
                                if (uuidStr != null) {
                                    try {
                                        Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));
                                        if (entity != null) entity.remove();
                                    } catch (Exception ignored) {}
                                }
                            }

                            // Logika dla Synchronizera
                            if (data.contains("sideL")) {
                                config.set("gates." + data.getString("sideL"), null);
                                config.set("gates." + data.getString("sideR"), null);
                            }
                        }

                        config.set("gates." + key, null);
                        removedGates++;
                    }
                } catch (Exception ex) {
                    // Ciche pomijanie błędnych wpisów
                }
            }
            plugin.saveGates();
        }

        String msg = plugin.getLanguageManager().getWithPrefix("cut-out-area");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(removedGates)));
    }

    // COPY
    public void copySelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (selections.get(uuid) == null || selections.get(uuid)[0] == null || selections.get(uuid)[1] == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("both-positions-required"));
            return;
        }

        Location loc1 = selections.get(uuid)[0];
        Location loc2 = selections.get(uuid)[1];
        Location playerLoc = player.getLocation().getBlock().getLocation();

        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        ConfigurationSection gates = plugin.getGatesConfig().getConfigurationSection("gates");
        Map<org.bukkit.util.Vector, ConfigurationSection> playerClipboard = new HashMap<>();

        if (gates != null) {
            for (String key : gates.getKeys(false)) {
                Location gateLoc = GateUtils.strToLoc(key);
                if (gateLoc == null) continue; // Ignorujemy śmieci, które nie są lokacją

                int gx = gateLoc.getBlockX();
                int gy = gateLoc.getBlockY();
                int gz = gateLoc.getBlockZ();

                // 2. Sprawdzamy, czy blok jest w zaznaczeniu
                if (gx >= minX && gx <= maxX && gy >= minY && gy <= maxY && gz >= minZ && gz <= maxZ) {
                    ConfigurationSection originalGate = gates.getConfigurationSection(key);
                    if (originalGate == null) continue;

                    // --- GŁĘBOKA KOPIA ---
                    ConfigurationSection copyOfData = new MemoryConfiguration();
                    for (String gateKey : originalGate.getKeys(true)) {
                        copyOfData.set(gateKey, originalGate.get(gateKey));
                    }

                    // RESETOWANIE DANYCH TYMCZASOWYCH (Bardzo ważne!)
                    copyOfData.set("state", false);
                    copyOfData.set("current_out", 0); // Czyścimy sygnał z kabli przy kopiowaniu!
                    copyOfData.set("power", 0);       // Resetujemy moc magistrali danych
                    copyOfData.set("oldBlock", null);

                    // Pobieramy materiał bloku
                    Material actualMaterial = gateLoc.getBlock().getType();
                    copyOfData.set("block_type", actualMaterial.name());

                    // 3. Obliczamy offset względem gracza
                    org.bukkit.util.Vector offset = new org.bukkit.util.Vector(gx, gy, gz).subtract(playerLoc.toVector());
                    playerClipboard.put(offset, copyOfData);
                }
            }
        }

        // Zapisujemy pod UUID gracza
        clipboard.put(uuid, playerClipboard);

        String msg = plugin.getLanguageManager().getWithPrefix("copy-success");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(playerClipboard.size())));
    }

    // PASTE
    public void pasteSelection(Player player) {
        UUID uuid = player.getUniqueId();
        List<Location> lastPaste = new ArrayList<>();

        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard-empty"));
            return;
        }

        // Pobieramy lokalizację bloku, na którym stoi gracz
        Location playerLoc = player.getLocation().getBlock().getLocation();
        FileConfiguration config = plugin.getGatesConfig();

        int pastedCount = 0;
        for (Map.Entry<org.bukkit.util.Vector, ConfigurationSection> entry : clipboard.get(uuid).entrySet()) {
            // Obliczamy nową lokalizację na podstawie offsetu ze schowka
            Location newLoc = playerLoc.clone().add(entry.getKey());
            ConfigurationSection gateData = entry.getValue();

            // 1. Pobranie materiału i stawianie bloku
            // Wywalamy nieistniejące zmienne "offset" i "gateMaterial"
            String materialName = gateData.getString("block_type", "RED_CONCRETE");
            Material mat;
            try {
                mat = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                mat = Material.RED_CONCRETE; // Backup, gdyby nazwa materiału była błędna
            }

            newLoc.getBlock().setType(mat);

            lastPaste.add(newLoc);

            // 2. Zapis logiki pod NOWYM adresem
            gateData.set("block_type", null);

            config.set("gates." + GateUtils.locToStr(newLoc), gateData);

            pastedCount++;
        }

        pasteHistory.put(uuid, lastPaste);

        plugin.saveGates();

        String msg = plugin.getLanguageManager().getWithPrefix("paste-success");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(pastedCount)));
    }

    public void undoPaste(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pasteHistory.containsKey(uuid) || pasteHistory.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("nothing-to-undo"));
            return;
        }

        List<Location> locations = pasteHistory.get(uuid);
        Map<Location, ConfigurationSection> redoData = new HashMap<>();
        FileConfiguration config = plugin.getGatesConfig();

        for (Location loc : locations) {
            String locKey = "gates." + GateUtils.locToStr(loc);
            ConfigurationSection data = config.getConfigurationSection(locKey);

            if (data != null) {
                // Robimy kopię danych
                ConfigurationSection copyOfData = new MemoryConfiguration();
                for (String key : data.getKeys(true)) {
                    copyOfData.set(key, data.get(key));
                }

                copyOfData.set("cached_block_type", loc.getBlock().getType().name());

                // Zapisujemy: Dokładna Lokacja -> Dane
                redoData.put(loc, copyOfData);

                // Czyścimy świat i config
                loc.getBlock().setType(Material.AIR);

                // LOGIKA SPECJALNA: Jeśli to RODZIC (środek Synchronizera), musimy wyczyścić boki
                // To na wypadek, gdyby boki nie znalazły się na liście 'locations'
                if (data.contains("sideL")) {
                    config.set("gates." + data.getString("sideL"), null);
                }
                if (data.contains("sideR")) {
                    config.set("gates." + data.getString("sideR"), null);
                }

                config.set(locKey, null);
            }
        }

        redoHistory.put(uuid, redoData);
        pasteHistory.remove(uuid);

        plugin.saveGates();

        int count = locations.size();

        player.sendMessage(plugin.getLanguageManager().getWithPrefix("undo-success", "{COUNT}", String.valueOf(count)));
    }

    public void redoPaste(Player player) {
        UUID uuid = player.getUniqueId();
        if (!redoHistory.containsKey(uuid) || redoHistory.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("nothing-to-redo"));
            return;
        }

        Map<Location, ConfigurationSection> redoData = redoHistory.get(uuid);
        FileConfiguration config = plugin.getGatesConfig();
        List<Location> restoredLocations = new ArrayList<>();

        for (Map.Entry<Location, ConfigurationSection> entry : redoData.entrySet()) {
            Location loc = entry.getKey();
            ConfigurationSection data = entry.getValue();

            // 1. Odczytujemy typ z bufora pamięci
            String matName = data.getString("cached_block_type", "WHITE_CONCRETE");
            loc.getBlock().setType(Material.matchMaterial(matName));

            // 2. Usuwamy ten klucz z obiektu, żeby nie wleciał do gates.yml przy zapisie
            data.set("cached_block_type", null);

            // 2. Wrzucamy z powrotem do configu pod stare kordy
            config.set("gates." + GateUtils.locToStr(loc), data);

            restoredLocations.add(loc);
        }

        // Zapisujemy nową historię do UNDO (żeby po redo można było znowu zrobić undo)
        pasteHistory.put(uuid, restoredLocations);
        redoHistory.remove(uuid);

        plugin.saveGates();
        // Na końcu metody redoPaste:
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("redo-success", "{COUNT}", String.valueOf(restoredLocations.size())));
    }

    public void rotateSelection(Player player, int degrees) {
        UUID uuid = player.getUniqueId();
        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard-empty"));
            return;
        }

        // Normalizacja stopni (np. -90 to 270)
        int angle = (degrees % 360 + 360) % 360;
        if (angle == 0) return;

        Map<org.bukkit.util.Vector, ConfigurationSection> oldClipboard = clipboard.get(uuid);
        Map<org.bukkit.util.Vector, ConfigurationSection> rotatedClipboard = new HashMap<>();

        for (Map.Entry<org.bukkit.util.Vector, ConfigurationSection> entry : oldClipboard.entrySet()) {
            org.bukkit.util.Vector vec = entry.getKey();
            ConfigurationSection data = entry.getValue();

            // 1. OBRACANIE WEKTORA (Offsetu)
            double x = vec.getX();
            double z = vec.getZ();
            double newX = x;
            double newZ = z;

            double radians = Math.toRadians(angle);
            newX = x * Math.cos(radians) - z * Math.sin(radians);
            newZ = x * Math.sin(radians) + z * Math.cos(radians);

            org.bukkit.util.Vector rotatedVec = new org.bukkit.util.Vector(Math.round(newX), vec.getY(), Math.round(newZ));

            // 2. OBRACANIE KIERUNKU BRAMKI (pole "out")
            String currentOutStr = data.getString("out", "NORTH").toUpperCase();
            BlockFace currentOut = BlockFace.valueOf(currentOutStr);
            BlockFace rotatedOut = rotateFace(currentOut, angle);

            data.set("out", rotatedOut.name());

            rotatedClipboard.put(rotatedVec, data);
        }

        clipboard.put(uuid, rotatedClipboard);
        String msg = plugin.getLanguageManager().getWithPrefix("rotate-success");
        player.sendMessage(msg.replace("{DEGREE}", String.valueOf(degrees)));
    }

    // Pomocnicza metoda do obracania BlockFace
    private BlockFace rotateFace(BlockFace face, int degrees) {
        // Ilość kroków o 90 stopni zgodnie z ruchem wskazówek zegara
        int steps = (degrees / 90) % 4;
        if (steps < 0) steps += 4;

        BlockFace current = face;
        for (int i = 0; i < steps; i++) {
            current = switch (current) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                default -> current;
            };
        }
        return current;
    }
}
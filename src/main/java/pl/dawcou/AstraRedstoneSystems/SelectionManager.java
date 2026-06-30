package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SelectionManager implements Listener {

    private final AstraRS plugin;
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Map<UUID, Map<org.bukkit.util.Vector, ConfigurationSection>> clipboard = new HashMap<>();

    // Klasa pomocnicza do trzymania stanu bloku przed zmianami (wielopoziomowa historia)
    private static class BlockStateBackup {
        private final Location location;
        private final BlockData blockData;
        private final String[] frontSignLines;
        private final String[] backSignLines;
        private final ConfigurationSection gateConfigSnapshot;

        public BlockStateBackup(Location location, BlockData blockData, String[] frontSignLines, String[] backSignLines, ConfigurationSection gateConfigSnapshot) {
            this.location = location;
            this.blockData = blockData.clone();
            this.frontSignLines = frontSignLines;
            this.backSignLines = backSignLines;
            this.gateConfigSnapshot = gateConfigSnapshot;
        }
    }

    private final Map<UUID, LinkedList<List<BlockStateBackup>>> pasteHistory = new HashMap<>();
    private final Map<UUID, LinkedList<List<BlockStateBackup>>> redoHistory = new HashMap<>();
    private static final int MAX_HISTORY_SIZE = 10;

    public SelectionManager(AstraRS plugin) {
        this.plugin = plugin;
    }

    private void serializeBlockState(Block block, ConfigurationSection targetSection) {
        targetSection.set("block_data_str", block.getBlockData().getAsString());

        if (block.getState() instanceof Sign sign) {
            List<String> frontLines = new ArrayList<>();
            List<String> backLines = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                frontLines.add(sign.getSide(Side.FRONT).getLine(i));
                backLines.add(sign.getSide(Side.BACK).getLine(i));
            }
            targetSection.set("sign_front", frontLines);
            targetSection.set("sign_back", backLines);
        }
    }

    private void deserializeBlockState(Location loc, ConfigurationSection sourceSection) {
        String dataStr = sourceSection.getString("block_data_str");
        if (dataStr == null) {
            String legacyMat = sourceSection.getString("block_type", "AIR");
            loc.getBlock().setType(Material.matchMaterial(legacyMat) != null ? Material.matchMaterial(legacyMat) : Material.AIR, false);
            return;
        }

        try {
            BlockData data = Bukkit.createBlockData(dataStr);
            loc.getBlock().setBlockData(data, false);

            if (loc.getBlock().getState() instanceof Sign sign) {
                List<String> frontLines = sourceSection.getStringList("sign_front");
                List<String> backLines = sourceSection.getStringList("sign_back");
                for (int i = 0; i < 4; i++) {
                    if (i < frontLines.size()) sign.getSide(Side.FRONT).setLine(i, frontLines.get(i));
                    if (i < backLines.size()) sign.getSide(Side.BACK).setLine(i, backLines.get(i));
                }
                sign.update(true, false);
            }
        } catch (IllegalArgumentException e) {
            loc.getBlock().setType(Material.AIR, false);
        }
    }

    private BlockStateBackup createBackup(Location loc) {
        Block block = loc.getBlock();
        BlockData data = block.getBlockData();
        String[] front = null;
        String[] back = null;

        if (block.getState() instanceof Sign sign) {
            front = new String[4];
            back = new String[4];
            for (int i = 0; i < 4; i++) {
                front[i] = sign.getSide(Side.FRONT).getLine(i);
                back[i] = sign.getSide(Side.BACK).getLine(i);
            }
        }

        String locStr = "gates." + GateUtils.locToStr(loc);
        ConfigurationSection originalGate = plugin.getGatesConfig().getConfigurationSection(locStr);
        ConfigurationSection snapshot = null;
        if (originalGate != null) {
            snapshot = new MemoryConfiguration();
            for (String key : originalGate.getKeys(true)) {
                snapshot.set(key, originalGate.get(key));
            }
        }

        return new BlockStateBackup(loc, data, front, back, snapshot);
    }

    private void restoreBackup(BlockStateBackup backup) {
        Location loc = backup.location;
        loc.getBlock().setBlockData(backup.blockData, false);

        if (loc.getBlock().getState() instanceof Sign sign && backup.frontSignLines != null) {
            for (int i = 0; i < 4; i++) {
                sign.getSide(Side.FRONT).setLine(i, backup.frontSignLines[i]);
                sign.getSide(Side.BACK).setLine(i, backup.backSignLines[i]);
            }
            sign.update(true, false);
        }

        String locKey = "gates." + GateUtils.locToStr(loc);
        if (backup.gateConfigSnapshot != null) {
            plugin.getGatesConfig().set(locKey, backup.gateConfigSnapshot);
        } else {
            plugin.getGatesConfig().set(locKey, null);
        }
    }

    public void giveSelector(Player player) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        if (meta != null) {
            String langName = plugin.getLanguageManager().getMessage("selector-item-name");
            if (langName == null || langName.isEmpty()) {
                langName = "&dSelektor Bramek";
            }

            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(langName));

            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "item_type");
            meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, "gate_selector");

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

        if (item == null || !item.hasItemMeta()) return;

        ItemMeta currentMeta = item.getItemMeta();
        if (currentMeta == null) return;

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "item_type");
        if (!currentMeta.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) return;

        String itemType = currentMeta.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (!"gate_selector".equals(itemType)) return;

        if (!player.hasPermission("astrars.admin")) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
            return;
        }

        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        Location clickedLoc = event.getClickedBlock().getLocation();

        if (!selections.containsKey(uuid)) selections.put(uuid, new Location[2]);
        Location[] playerSelections = selections.get(uuid);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (playerSelections[0] == null || !playerSelections[0].equals(clickedLoc)) {
                playerSelections[0] = clickedLoc;
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("position-1-selected"));
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
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

        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gates = config.getConfigurationSection("gates");
        int removedGates = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location target = new Location(world, x, y, z);
                    String key = GateUtils.locToStr(target);

                    if (gates != null && gates.contains(key)) {
                        ConfigurationSection data = gates.getConfigurationSection(key);
                        if (data != null) {
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
                            if (data.contains("sideL")) {
                                config.set("gates." + data.getString("sideL"), null);
                                config.set("gates." + data.getString("sideR"), null);
                            }
                        }
                        config.set("gates." + key, null);
                        removedGates++;
                    }
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
        plugin.saveGates();

        String msg = plugin.getLanguageManager().getWithPrefix("cut-out-area");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(removedGates)));
    }

    public void copySelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (selections.get(uuid) == null || selections.get(uuid)[0] == null || selections.get(uuid)[1] == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("both-positions-required"));
            return;
        }

        Location loc1 = selections.get(uuid)[0];
        Location loc2 = selections.get(uuid)[1];
        Location playerLoc = player.getLocation().getBlock().getLocation();
        World world = loc1.getWorld();

        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        ConfigurationSection gates = plugin.getGatesConfig().getConfigurationSection("gates");
        Map<org.bukkit.util.Vector, ConfigurationSection> playerClipboard = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location currentLoc = new Location(world, x, y, z);
                    Block block = currentLoc.getBlock();
                    String locStr = GateUtils.locToStr(currentLoc);
                    Material actualMaterial = block.getType();

                    boolean isGate = (gates != null && gates.contains(locStr));

                    if (actualMaterial == Material.AIR && !isGate) {
                        continue;
                    }

                    ConfigurationSection copyOfData = new MemoryConfiguration();
                    serializeBlockState(block, copyOfData);

                    if (isGate) {
                        ConfigurationSection originalGate = gates.getConfigurationSection(locStr);
                        if (originalGate != null) {
                            for (String gateKey : originalGate.getKeys(true)) {
                                copyOfData.set(gateKey, originalGate.get(gateKey));
                            }
                            copyOfData.set("state", false);
                            copyOfData.set("current_out", 0);
                            copyOfData.set("power", 0);
                            copyOfData.set("last_decay", 0);
                            copyOfData.set("last_charge_tick", 0);

                            String gateType = copyOfData.getString("type");
                            if (gateType != null && gateType.equalsIgnoreCase("BATTERY")) {
                                copyOfData.set("last_decay", 0);
                                copyOfData.set("last_charge_tick", 0);
                            }
                        }
                    }

                    copyOfData.set("is_gate_logic", isGate);
                    org.bukkit.util.Vector offset = new org.bukkit.util.Vector(x, y, z).subtract(playerLoc.toVector());
                    playerClipboard.put(offset, copyOfData);
                }
            }
        }

        clipboard.put(uuid, playerClipboard);

        long actualGatesCount = playerClipboard.values().stream()
                .filter(section -> section.getBoolean("is_gate_logic", false))
                .count();

        String msg = plugin.getLanguageManager().getWithPrefix("copy-success");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(actualGatesCount)));
    }

    public void pasteSelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard-empty"));
            return;
        }

        Location playerLoc = player.getLocation().getBlock().getLocation();
        FileConfiguration config = plugin.getGatesConfig();
        List<BlockStateBackup> currentOperationBackup = new ArrayList<>();

        int pastedGatesCount = 0;

        List<Map.Entry<org.bukkit.util.Vector, ConfigurationSection>> entries = new ArrayList<>(clipboard.get(uuid).entrySet());
        entries.sort(Comparator.comparingInt(e -> {
            String dataStr = e.getValue().getString("block_data_str", "");
            if (dataStr.contains("sign") || dataStr.contains("button") || dataStr.contains("lever")) return 1;
            return 0;
        }));

        for (Map.Entry<org.bukkit.util.Vector, ConfigurationSection> entry : entries) {
            Location newLoc = playerLoc.clone().add(entry.getKey());
            ConfigurationSection gateData = entry.getValue();

            currentOperationBackup.add(createBackup(newLoc));

            boolean isGate = gateData.getBoolean("is_gate_logic", false);
            deserializeBlockState(newLoc, gateData);

            String locKey = "gates." + GateUtils.locToStr(newLoc);
            if (isGate) {
                ConfigurationSection saveData = new MemoryConfiguration();
                for (String key : gateData.getKeys(true)) {
                    if (key.equals("block_data_str") || key.equals("sign_front") || key.equals("sign_back") || key.equals("is_gate_logic")) {
                        continue;
                    }
                    saveData.set(key, gateData.get(key));
                }
                config.set(locKey, saveData);
                pastedGatesCount++;
            } else {
                config.set(locKey, null);
            }
        }

        pasteHistory.computeIfAbsent(uuid, k -> new LinkedList<>()).addFirst(currentOperationBackup);
        if (pasteHistory.get(uuid).size() > MAX_HISTORY_SIZE) {
            pasteHistory.get(uuid).removeLast();
        }
        redoHistory.remove(uuid);

        plugin.saveGates();

        String msg = plugin.getLanguageManager().getWithPrefix("paste-success");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(pastedGatesCount)));
    }

    public void undoPaste(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pasteHistory.containsKey(uuid) || pasteHistory.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("nothing-to-undo"));
            return;
        }

        List<BlockStateBackup> lastOperation = pasteHistory.get(uuid).removeFirst();
        List<BlockStateBackup> redoBackup = new ArrayList<>();

        for (BlockStateBackup blockBackup : lastOperation) {
            redoBackup.add(createBackup(blockBackup.location));
        }

        for (int i = lastOperation.size() - 1; i >= 0; i--) {
            restoreBackup(lastOperation.get(i));
        }

        redoHistory.computeIfAbsent(uuid, k -> new LinkedList<>()).addFirst(redoBackup);
        if (redoHistory.get(uuid).size() > MAX_HISTORY_SIZE) {
            redoHistory.get(uuid).removeLast();
        }

        plugin.saveGates();
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("undo-success", "{COUNT}", String.valueOf(lastOperation.size())));
    }

    public void redoPaste(Player player) {
        UUID uuid = player.getUniqueId();
        if (!redoHistory.containsKey(uuid) || redoHistory.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("nothing-to-redo"));
            return;
        }

        List<BlockStateBackup> redoOperation = redoHistory.get(uuid).removeFirst();
        List<BlockStateBackup> undoBackup = new ArrayList<>();

        for (BlockStateBackup blockBackup : redoOperation) {
            undoBackup.add(createBackup(blockBackup.location));
        }

        for (BlockStateBackup blockBackup : redoOperation) {
            restoreBackup(blockBackup);
        }

        pasteHistory.computeIfAbsent(uuid, k -> new LinkedList<>()).addFirst(undoBackup);

        plugin.saveGates();
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("redo-success", "{COUNT}", String.valueOf(redoOperation.size())));
    }

    public void rotateSelection(Player player, int degrees) {
        UUID uuid = player.getUniqueId();
        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard-empty"));
            return;
        }

        int angle = (degrees % 360 + 360) % 360;
        if (angle == 0) return;

        Map<org.bukkit.util.Vector, ConfigurationSection> oldClipboard = clipboard.get(uuid);
        Map<org.bukkit.util.Vector, ConfigurationSection> rotatedClipboard = new HashMap<>();

        for (Map.Entry<org.bukkit.util.Vector, ConfigurationSection> entry : oldClipboard.entrySet()) {
            org.bukkit.util.Vector vec = entry.getKey();
            ConfigurationSection data = entry.getValue();

            double x = vec.getX();
            double z = vec.getZ();
            double radians = Math.toRadians(angle);
            double newX = x * Math.cos(radians) - z * Math.sin(radians);
            double newZ = x * Math.sin(radians) + z * Math.cos(radians);

            org.bukkit.util.Vector rotatedVec = new org.bukkit.util.Vector(Math.round(newX), vec.getY(), Math.round(newZ));

            if (data.contains("out")) {
                String currentOutStr = data.getString("out", "NORTH").toUpperCase();
                try {
                    BlockFace currentOut = BlockFace.valueOf(currentOutStr);
                    data.set("out", rotateFace(currentOut, angle).name());
                } catch (IllegalArgumentException ignored) {}
            }

            String dataStr = data.getString("block_data_str");
            if (dataStr != null) {
                data.set("block_data_str", rotateBlockDataString(dataStr, angle));
            }

            rotatedClipboard.put(rotatedVec, data);
        }

        clipboard.put(uuid, rotatedClipboard);
        String msg = plugin.getLanguageManager().getWithPrefix("rotate-success");
        player.sendMessage(msg.replace("{DEGREE}", String.valueOf(degrees)));
    }

    private BlockFace rotateFace(BlockFace face, int degrees) {
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

    private String rotateBlockDataString(String dataStr, int degrees) {
        if (!dataStr.contains("facing=")) return dataStr;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            if (dataStr.contains("facing=" + face.name().toLowerCase())) {
                BlockFace rotated = rotateFace(face, degrees);
                return dataStr.replace("facing=" + face.name().toLowerCase(), "facing=" + rotated.name().toLowerCase());
            }
        }
        return dataStr;
    }

    public void saveClipboardToFile(Player player, String schemaName) {
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        File schemaFile = new File(schematicsDir, schemaName.toLowerCase() + ".yml");
        UUID uuid = player.getUniqueId();

        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard-empty"));
            return;
        }

        if (schemaFile.exists()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-file-exists").replace("%NAME%", schemaName));
            return;
        }

        Map<org.bukkit.util.Vector, ConfigurationSection> playerClipboard = clipboard.get(uuid);
        long gatesCount = playerClipboard.values().stream().filter(s -> s.getBoolean("is_gate_logic", false)).count();

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            if (!schematicsDir.exists()) schematicsDir.mkdirs();
            FileConfiguration schemaConfig = new YamlConfiguration();

            for (Map.Entry<org.bukkit.util.Vector, ConfigurationSection> entry : playerClipboard.entrySet()) {
                String vecKey = entry.getKey().getBlockX() + "_" + entry.getKey().getBlockY() + "_" + entry.getKey().getBlockZ();
                ConfigurationSection fileGateSection = schemaConfig.createSection("schematic_gates." + vecKey);
                for (String key : entry.getValue().getKeys(true)) {
                    if ("displayUUID".equals(key)) continue;
                    fileGateSection.set(key, entry.getValue().get(key));
                }
            }

            try {
                schemaConfig.save(schemaFile);
                plugin.getServer().getGlobalRegionScheduler().run(plugin, vtask -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-save-success").replace("%NAME%", schemaName).replace("%COUNT%", String.valueOf(gatesCount)));
                });
            } catch (IOException e) {
                plugin.getServer().getGlobalRegionScheduler().run(plugin, vtask -> player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-save-error")));
                e.printStackTrace();
            }
        });
    }

    public void loadClipboardFromFile(Player player, String schemaName) {
        File schemaFile = new File(new File(plugin.getDataFolder(), "schematics"), schemaName.toLowerCase() + ".yml");

        if (!schemaFile.exists()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-not-found").replace("%NAME%", schemaName).replace("{NAME}", schemaName));
            return;
        }

        FileConfiguration schemaConfig = YamlConfiguration.loadConfiguration(schemaFile);
        ConfigurationSection schematicGates = schemaConfig.getConfigurationSection("schematic_gates");

        if (schematicGates == null || schematicGates.getKeys(false).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-corrupted"));
            return;
        }

        Map<org.bukkit.util.Vector, ConfigurationSection> loadedClipboard = new HashMap<>();
        int loadedGatesCount = 0;

        for (String key : schematicGates.getKeys(false)) {
            String[] parts = key.split("_");
            if (parts.length != 3) continue;

            try {
                org.bukkit.util.Vector vec = new org.bukkit.util.Vector(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                ConfigurationSection fileData = schematicGates.getConfigurationSection(key);
                if (fileData == null) continue;

                ConfigurationSection memoryData = new MemoryConfiguration();
                for (String gateKey : fileData.getKeys(true)) {
                    memoryData.set(gateKey, fileData.get(gateKey));
                }

                if (memoryData.getBoolean("is_gate_logic", false)) loadedGatesCount++;
                loadedClipboard.put(vec, memoryData);
            } catch (NumberFormatException ignored) {}
        }

        clipboard.put(player.getUniqueId(), loadedClipboard);
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-load-success").replace("%NAME%", schemaName).replace("%COUNT%", String.valueOf(loadedGatesCount)));
    }

    public void deleteClipboardFile(Player player, String schemaName) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            File schematicsDir = new File(plugin.getDataFolder(), "schematics");
            File schemaFile = new File(schematicsDir, schemaName + ".yml");

            if (!schemaFile.exists()) {
                plugin.getServer().getGlobalRegionScheduler().run(plugin, vtask -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-not-found").replace("%NAME%", schemaName));
                });
                return;
            }

            if (schemaFile.delete()) {
                plugin.getServer().getGlobalRegionScheduler().run(plugin, vtask -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-deleted").replace("%NAME%", schemaName));
                });
            } else {
                plugin.getServer().getGlobalRegionScheduler().run(plugin, vtask -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema-delete-error").replace("%NAME%", schemaName));
                });
            }
        });
    }
}
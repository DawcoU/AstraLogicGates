package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.List;
import java.util.UUID;

public class GateUtils {

    public static int getPowerAt(Block b) {
        if (b == null || b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR) {
            return 0;
        }
        Material type = b.getType();
        if (type == Material.REDSTONE_WIRE) {
            return ((org.bukkit.block.data.type.RedstoneWire) b.getBlockData()).getPower();
        }
        if (type == Material.REDSTONE_BLOCK) {
            return 15;
        }
        if (type == Material.REDSTONE_TORCH || type == Material.REDSTONE_WALL_TORCH) {
            if (b.getBlockData() instanceof org.bukkit.block.data.Lightable torch) {
                return torch.isLit() ? 15 : 0;
            }
        }
        return b.getBlockPower();
    }

    public static String getStringFrom(Block block, BlockFace fromFace, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        FileConfiguration config = plugin.getGatesConfig();
        String path = "gates." + key;

        if (!config.contains(path)) return "";

        // 1. Sprawdzamy link (bezprzewodowy)
        int wireless = config.getInt(path + ".link_input", Integer.MIN_VALUE);

        // Jeśli jest sygnał bezprzewodowy, zwróć go
        if (wireless != Integer.MIN_VALUE) {
            return String.valueOf(wireless);
        }

        // 2. Sprawdzamy fizyczny (lokalny)
        String physical = config.getString(path + ".current_out", "");

        // Jeśli fizyczny jest pusty, to nie ma sygnału
        if (physical.isEmpty()) return "";

        // Sprawdzamy, czy to nie jest "NO_SIGNAL" w formie Stringa
        try {
            String cleanVal = physical.replaceAll("[^0-9\\-]", "");

            if (!cleanVal.isEmpty() && !cleanVal.equals("-")) {
                if (Integer.parseInt(cleanVal) == Integer.MIN_VALUE) {
                    return "";
                }
            }
        } catch (NumberFormatException e) {
            return "";
        }

        return physical;
    }

    public static int getNumberFrom(Block block, BlockFace fromFace, AstraRS plugin) {
        // Korzystamy z jednego źródła prawdy
        String val = getStringFrom(block, fromFace, plugin);

        if (val.isEmpty()) return Integer.MIN_VALUE;

        try {
            String cleanVal = val.replaceAll("[^0-9\\-]", "");

            // Jeśli po wyczyszczeniu został pusty tekst (np. ktoś wysłał same litery "ABC")
            if (cleanVal.isEmpty() || cleanVal.equals("-")) {
                return Integer.MIN_VALUE;
            }

            return Integer.parseInt(cleanVal);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE; // W razie jakiegokolwiek błędu zwracamy brak danych
        }
    }

    public static int getCustomOrRedstonePower(AstraRS plugin, Block block) {
        String key = locToStr(block.getLocation());
        String path = "gates." + key;

        if (plugin.getGatesConfig().contains(path)) {
            // Wyciągamy jako String i parsujemy bezpiecznie!
            String val = plugin.getGatesConfig().getString(path + ".current_out", "0");
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return block.getBlockPower();
    }

    public static BlockFace rotate90(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    public static BlockFace getDirection(Player p) {
        float y = p.getLocation().getYaw();
        if (y < 0) y += 360;
        if (y >= 315 || y < 45) return BlockFace.SOUTH;
        if (y >= 45 && y < 135) return BlockFace.WEST;
        if (y >= 135 && y < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    public static String locToStr(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public static Location strToLoc(String s) {
        try {
            String[] p = s.split(",");
            return new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception e) {
            return null;
        }
    }

    public static void spawnStatusParticle(Block gate, BlockFace face, boolean active) {
        Location loc = gate.getLocation().add(0.5, 0.5, 0.5);
        loc.add(face.getDirection().multiply(0.51));
        org.bukkit.Particle.DustOptions dust = active ?
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1.4F) :
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.4F);
        gate.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, loc, 2, 0, 0, 0, 0, dust);
    }

    public static void updateDisplayNumber(AstraRS plugin, String uuidStr, String value) {
        if (uuidStr == null || uuidStr.isEmpty()) return;

        // Jeśli nie ma wartości (null lub pusta), dajemy po prostu zwykłe "0"
        if (value == null || value.isEmpty()) {
            value = "0";
        }

        try {
            UUID uuid = UUID.fromString(uuidStr);
            Entity entity = Bukkit.getEntity(uuid);

            if (entity instanceof TextDisplay display) {
                display.setText("§f" + value);
            }
        } catch (Exception e) {
            // Ciche ignorowanie, jeśli UUID jest trefne
        }
    }

    public static void updateOutput(AstraRS plugin, String path, Block target, boolean p) {
        FileConfiguration config = plugin.getGatesConfig();

        // 1. POBIERAMY TYP ZAPISANY W CONFIGU DLA TEJ ŚCIEŻKI
        String type = config.getString(path + ".type", "");

        // 3. BLOKADA - jeśli to kabel, wychodzimy ZANIM cokolwiek zmienimy
        if (type.equalsIgnoreCase("CABLE_DATA")) {
            return;
        }

        // Celujemy w blok POD tym, co podaliśmy jako target
        Block powerBlock = target.getRelative(BlockFace.DOWN);

        if (p) {
            // Zapisujemy co tam jest TERAZ (np. Grass), zanim damy redstone
            if (powerBlock.getType() != Material.REDSTONE_BLOCK) {
                config.set(path + ".oldBlock", powerBlock.getType().name());
                plugin.saveGates();
            }
            powerBlock.setType(Material.REDSTONE_BLOCK);
        } else {
            // Przywracamy z pamięci
            String matName = config.getString(path + ".oldBlock", "AIR");
            powerBlock.setType(Material.valueOf(matName));
        }
    }
}
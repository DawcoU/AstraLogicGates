package pl.dawcou.AstraRedstoneSystems.utils;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import pl.dawcou.AstraRedstoneSystems.system.AstraRS;

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

        // --- NOWY WARUNEK: Dźwignie, guziki, płyty naciskowe ---
        if (b.getBlockData() instanceof org.bukkit.block.data.Powerable powerable) {
            return powerable.isPowered() ? 15 : 0;
        }

        return b.getBlockPower();
    }

    public static String getStringFrom(Block block, BlockFace fromFace, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        FileConfiguration config = plugin.getGatesConfig();
        String path = "gates." + key;

        if (!config.contains(path)) return "";

        // 1. Sprawdzamy link (bezprzewodowy)
        long wireless = config.getLong(path + ".link_input", Long.MIN_VALUE);

        // Jeśli jest sygnał bezprzewodowy, zwróć go
        if (wireless != Long.MIN_VALUE) {
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
                if (Long.parseLong(cleanVal) == Long.MIN_VALUE) {
                    return "";
                }
            }
        } catch (NumberFormatException e) {
            return "";
        }

        return physical;
    }

    public static long getNumberFrom(Block block, BlockFace fromFace, AstraRS plugin) {
        // Korzystamy z jednego źródła prawdy
        String val = getStringFrom(block, fromFace, plugin);

        if (val.isEmpty()) return Long.MIN_VALUE;

        try {
            String cleanVal = val.replaceAll("[^0-9\\-]", "");

            // Jeśli po wyczyszczeniu został pusty tekst (np. ktoś wysłał same litery "ABC")
            if (cleanVal.isEmpty() || cleanVal.equals("-")) {
                return Long.MIN_VALUE;
            }

            return Long.parseLong(cleanVal);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return Long.MIN_VALUE; // W razie jakiegokolwiek błędu zwracamy brak danych
        }
    }

    public static long getCustomOrRedstonePower(AstraRS plugin, Block block) {
        String key = locToStr(block.getLocation());
        String path = "gates." + key;

        if (plugin.getGatesConfig().contains(path)) {
            // Wyciągamy jako String i parsujemy bezpiecznie!
            String val = plugin.getGatesConfig().getString(path + ".current_out", "0");
            try {
                return Long.parseLong(val);
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

    public static void spawnStatusParticle(Block gate, BlockFace face, Boolean active) {
        try {
            Location loc = gate.getLocation().add(0.5, 0.5, 0.5);
            loc.add(face.getDirection().multiply(0.51));

            // Określamy kolor na podstawie stanu: true -> LIME, false -> RED
            Color color;
            if (active) {
                color = Color.LIME; // Stan włączony 🟢
            } else {
                color = Color.RED;  // Stan wyłączony 🔴
            }

            Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1.4F);

            // Uniwersalny i bezpieczny spawn cząsteczek (obsługuje DUST i stary REDSTONE w try-catch)
            try {
                Particle dustParticle = Particle.valueOf("DUST");
                gate.getWorld().spawnParticle(dustParticle, loc, 3, 0, 0, 0, 0, dust);
            } catch (IllegalArgumentException e) {
                gate.getWorld().spawnParticle(Particle.valueOf("REDSTONE"), loc, 3, 0, 0, 0, 0, dust);
            }
        } catch (Exception e) {
            // Zabezpieczenie przed uwaleniem pętli
        }
    }

    public static String createDisplay(Location blockLoc, String outName) {
        World world = blockLoc.getWorld();
        if (world == null) return "";

        Location displayLoc = blockLoc.clone().add(0.5, 2.0, 0.5);

        switch (outName.toUpperCase()) {
            case "NORTH" -> displayLoc.add(0, 0, -2.0);
            case "SOUTH" -> displayLoc.add(0, 0, 2.0);
            case "EAST"  -> displayLoc.add(2.0, 0, 0);
            case "WEST"  -> displayLoc.add(-2.0, 0, 0);
        }

        TextDisplay textDisplay = world.spawn(displayLoc, TextDisplay.class);

        textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        textDisplay.setShadowed(false);
        textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
        textDisplay.setBillboard(TextDisplay.Billboard.CENTER);

        Transformation transformation = textDisplay.getTransformation();
        transformation.getScale().set(4f, 4f, 4f);
        textDisplay.setTransformation(transformation);

        return textDisplay.getUniqueId().toString();
    }

    public static String validateDisplay(FileConfiguration config, String path, Location gateLoc) {
        String uuidStr = config.getString(path + ".displayUUID");
        String outName = config.getString(path + ".out", "NORTH");

        // Obliczamy lokalizację do szybkiego sprawdzenia chunku
        Location displayLoc = gateLoc.clone().add(0.5, 2.0, 0.5);
        switch (outName.toUpperCase()) {
            case "NORTH" -> displayLoc.add(0, 0, -2.0);
            case "SOUTH" -> displayLoc.add(0, 0, 2.0);
            case "EAST"  -> displayLoc.add(2.0, 0, 0);
            case "WEST"  -> displayLoc.add(-2.0, 0, 0);
        }

        // Blokada anty-lagowa: jeśli chunk jest niezaładowany, nie dotykamy encji
        if (!displayLoc.getChunk().isLoaded()) {
            return uuidStr != null ? uuidStr : "";
        }

        // Sprawdzamy czy istnieje
        try {
            if (uuidStr != null && !uuidStr.isEmpty()) {
                Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));

                if (entity instanceof TextDisplay) {
                    return uuidStr;
                }
            }
        } catch (Exception ignored) {}

        // Jeśli chunk jest załadowany, ale nie ma hologramu -> tworzymy od nowa
        return createDisplay(gateLoc, outName);
    }

    public static void updateDisplayNumber(AstraRS plugin, String uuidStr, String value) {
        if (uuidStr == null || uuidStr.isEmpty()) return;

        try {
            UUID uuid = UUID.fromString(uuidStr);
            Entity entity = Bukkit.getEntity(uuid);

            if (entity instanceof TextDisplay display) {
                // Po prostu ustawiamy to, co dostaliśmy - łącznie z kolorami i "0"
                display.text(net.kyori.adventure.text.Component.text(value));
            }
        } catch (Exception e) {
            // Ciche ignorowanie
        }
    }

    public static void updateOutput(AstraRS plugin, String path, Block target, boolean p) {
        FileConfiguration config = plugin.getGatesConfig();
        String type = config.getString(path + ".type", "");

        if (type.equalsIgnoreCase("CABLE_DATA")) {
            return;
        }

        Block powerBlock = target.getRelative(BlockFace.DOWN);

        if (p) {
            if (powerBlock.getType() != Material.REDSTONE_BLOCK) {
                config.set(path + ".oldBlock", powerBlock.getType().name());
                powerBlock.setType(Material.REDSTONE_BLOCK); // Podmieniamy TYLKO jeśli to nie był redstone
            }
        } else {
            String matName = config.getString(path + ".oldBlock", "AIR");
            Material targetMaterial = Material.valueOf(matName);

            // POPRAWKA: Podmieniaj na stary blok TYLKO wtedy, gdy w świecie AKTUALNIE leży REDSTONE_BLOCK!
            // Jeśli leży tam coś innego (bo gracz to zmienił WorldEditem lub zniszczył), to nie ruszamy!
            if (powerBlock.getType() == Material.REDSTONE_BLOCK) {
                powerBlock.setType(targetMaterial);
            }
        }
    }
}
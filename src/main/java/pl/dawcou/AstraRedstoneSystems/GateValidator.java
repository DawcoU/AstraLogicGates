package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;

public class GateValidator {

    public boolean isValid(String path, FileConfiguration config) {
        String type = config.getString(path + ".type", "").toUpperCase();

        // 1. Podstawowa walidacja istnienia typu
        if (type.isEmpty()) return false;

        // Jeśli gracz ma błąd w pliku, wyłapujemy to tutaj i bezpiecznie odrzucamy bramkę
        String outName = config.getString(path + ".out", "NORTH").toUpperCase();
        try {
            BlockFace.valueOf(outName);
        } catch (IllegalArgumentException e) {
            return false;
        }

        // 3. Szczegółowa walidacja parametrów dla konkretnych typów bramek
        switch (type) {
            // ==========================================
            // BRAMKI LICZBOWE (NumberGates)
            // ==========================================
            case "COUNTER" -> {
                int limit = config.getInt(path + ".score_limit", 15);
                if (limit < 1 || limit > 1000) return false;
            }
            case "NUMBER_GATE", "DECODER" -> {
                if (!config.contains(path + ".value")) return false;
            }
            case "MATH" -> {
                // Sprawdzamy czy tryb działania jest prawidłowy
                String mode = config.getString(path + ".mode", "ADD").toUpperCase();
                if (!mode.matches("ADD|SUB|-|MUL|\\*|DIV|/|POW|\\^")) return false;
            }
            case "COMPARATOR" -> {
                String op = config.getString(path + ".mode", "==");
                if (!op.matches(">|<|==|!=|>=|<=")) return false;
            }
            case "RANDOM_NUMBER" -> {
                if (!config.contains(path + ".min") || !config.contains(path + ".max")) return false;
                int min = config.getInt(path + ".min");
                int max = config.getInt(path + ".max");
                if (min > max) return false;
            }

            // ==========================================
            // BRAMKI TEKSTOWE (StringGates)
            // ==========================================
            case "STRING_GATE", "STRING_DECODER" -> {
                if (!config.contains(path + ".value")) return false;
            }
            case "STRING_COMPARATOR" -> {
                String mode = config.getString(path + ".mode", "EQUALS").toUpperCase();
                if (!mode.matches("EQUALS|EQUALS_IGNORE_CASE|CONTAINS|STARTS_WITH|ENDS_WITH|EMPTY")) return false;
            }

            // ==========================================
            // POZOSTAŁE BRAMKI SYSTEMOWE
            // ==========================================
            case "SENSOR" -> {
                int radius = config.getInt(path + ".radius", 0);
                if (radius < 1 || radius > 15) return false;
            }
            case "REPEATER", "CLOCK", "CLOCK_GATE" -> {
                int interval = config.getInt(path + ".interval", 0);
                if (interval < 1 || interval > 200) return false;
            }
            case "SENDER", "RECEIVER" -> {
                String channel = config.getString(path + ".channel", "default");
                if (channel.isEmpty()) return false;
            }
        }

        return true;
    }
}
package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraRedstoneSystems.AstraRS;
import pl.dawcou.AstraRedstoneSystems.GateValidator;
import pl.dawcou.AstraRedstoneSystems.GateUtils;

import java.util.concurrent.ThreadLocalRandom;

public class NumberGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    public NumberGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runNumberGates() {
        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gatesSection = config.getConfigurationSection("gates");
        if (gatesSection == null) return;

        boolean debug = plugin.getConfig().getBoolean("settings.debug-mode", false);
        String prefix = "§8[§bAstraRedstoneSystems§8] §7[§dDebug§7] ";

        for (String key : gatesSection.getKeys(false)) {
            String path = "gates." + key;
            if (!validator.isValid(path, config)) continue;

            Location loc = GateUtils.strToLoc(key);
            if (loc == null) continue;

            Block gate = loc.getBlock();
            String type = config.getString(path + ".type", "").toUpperCase();

            if (!type.matches("NUMBER_GATE|COUNTER|BOOLEAN_GATE|MATH|COMPARATOR|DECODER|RANDOM_BOOLEAN|RANDOM_NUMBER|DECIMAL_ACCUMULATOR")) continue;

            // --- INICJALIZACJA KIERUNKÓW ---
            String outName = config.getString(path + ".out", "NORTH");
            BlockFace out = BlockFace.valueOf(outName.toUpperCase());
            BlockFace back = out.getOppositeFace();
            BlockFace s1 = GateUtils.rotate90(out);
            BlockFace s2 = s1.getOppositeFace();
            Block target = gate.getRelative(out);

            boolean currentState = config.getBoolean(path + ".state", false);

            // --- EFEKTY WIZUALNE STATUSU ---

            // 1. EFEKT WYJŚCIA (Wszystkie bramki logiczne oprócz kabla)
            if (type.matches("NUMBER_GATE|COUNTER|BOOLEAN_GATE|MATH|COMPARATOR|DECODER|RANDOM_BOOLEAN|RANDOM_NUMBER|DECIMAL_ACCUMULATOR")) {
                GateUtils.spawnStatusParticle(gate, out, currentState);
            }

            // 2. EFEKT WEJŚCIA Z TYŁU
            if (type.matches("NUMBER_GATE|BOOLEAN_GATE|DECODER|RANDOM_BOOLEAN|RANDOM_NUMBER|COUNTER|DECIMAL_ACCUMULATOR")) {
                boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;

                // Dodajemy DECIMAL_ACCUMULATOR tutaj
                if (type.equals("COUNTER") || type.equals("DECIMAL_ACCUMULATOR")) {
                    int vBack = GateUtils.getNumberFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    // Świeci jeśli idzie liczba LUB prąd
                    GateUtils.spawnStatusParticle(gate, back, vBack > 0 || pBack);
                } else {
                    GateUtils.spawnStatusParticle(gate, back, pBack);
                }
            }

            // 3. EFEKT WEJŚĆ BOCZNYCH
            if (type.matches("COUNTER|MATH|COMPARATOR|DECIMAL_ACCUMULATOR")) {
                BlockFace faceL = s2;
                BlockFace faceR = s1;

                if (type.equals("COUNTER") || type.equals("MATH") || type.equals("DECIMAL_ACCUMULATOR") || type.equals("COMPARATOR")) {
                    // LEWO
                    int vL = GateUtils.getNumberFrom(gate.getRelative(faceL), faceL.getOppositeFace(), plugin);
                    if (vL == 0 || vL == Integer.MIN_VALUE) vL = GateUtils.getPowerAt(gate.getRelative(faceL));
                    GateUtils.spawnStatusParticle(gate, faceL, vL > 0);

                    // PRAWO
                    int vR = GateUtils.getNumberFrom(gate.getRelative(faceR), faceR.getOppositeFace(), plugin);
                    if (vR == 0 || vR == Integer.MIN_VALUE) vR = GateUtils.getPowerAt(gate.getRelative(faceR));
                    GateUtils.spawnStatusParticle(gate, faceR, vR > 0);

                } else {
                    // Dla standardowych bramek (jeśli jakieś zostaną w przyszłości)
                    GateUtils.spawnStatusParticle(gate, faceL, GateUtils.getCustomOrRedstonePower(plugin, gate.getRelative(faceL)) > 0);
                    GateUtils.spawnStatusParticle(gate, faceR, GateUtils.getCustomOrRedstonePower(plugin, gate.getRelative(faceR)) > 0);
                }
            }

            // --- LOGIKA BRAMEK ---
            switch (type) {
                case "NUMBER_GATE" -> {
                    boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    int storedValue = config.getInt(path + ".value", 0);

                    // LOGIKA: Jeśli OFF -> Integer.MIN_VALUE, Jeśli ON -> storedValue
                    int valToSend = pBack ? storedValue : Integer.MIN_VALUE;
                    String currentResStr = String.valueOf(valToSend);

                    String lastOutStr = config.getString(path + ".current_out", String.valueOf(Integer.MIN_VALUE));

                    if (!currentResStr.equals(lastOutStr)) {
                        config.set(path + ".current_out", currentResStr);
                        config.set(path + ".state", pBack);
                        // GateUtils musi umieć odebrać inta i przekazać go dalej
                        GateUtils.updateOutput(plugin, path, target, pBack);
                    }
                }

                case "BOOLEAN_GATE" -> {
                    boolean hasPower = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    int valueToSend = hasPower ? 1 : 0;

                    // 1. Przygotowujemy uniwersalny String systemowy
                    String currentResStr = String.valueOf(valueToSend);

                    // 2. Pobieramy poprzednią wartość jako STRING (konsekwentnie!)
                    String lastOutStr = config.getString(path + ".current_out", "");

                    // 3. Porównujemy String z Stringiem, żeby uniknąć wiecznej pętli
                    if (!currentResStr.equals(lastOutStr)) {

                        if (debug) {
                            org.bukkit.Bukkit.getConsoleSender().sendMessage(
                                    prefix + "§3BOOLEAN_GATE §7na §e" + key + " §7-> Stan: " + (hasPower ? "§aWŁĄCZONY (1)" : "§cWYŁĄCZONY (0)")
                            );
                        }

                        // Zapisujemy jako String – bezpiecznie dla reszty sieci kabli danych
                        config.set(path + ".current_out", currentResStr);
                        config.set(path + ".state", hasPower);

                        GateUtils.updateOutput(plugin, path, target, hasPower);
                    }
                }

                case "COUNTER" -> {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = right.getOppositeFace();

                    // --- POBIERANIE DANYCH ---
                    int dataBack = GateUtils.getNumberFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    int dataLeft = GateUtils.getNumberFrom(gate.getRelative(left), left.getOppositeFace(), plugin);

                    // NORMALIZACJA:
                    if (dataBack == Integer.MIN_VALUE) dataBack = 0;
                    if (dataLeft == Integer.MIN_VALUE) dataLeft = 0;

                    boolean pB = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean pL = GateUtils.getPowerAt(gate.getRelative(left)) > 0;
                    boolean pR = GateUtils.getPowerAt(gate.getRelative(right)) > 0;

                    // --- POBIERANIE POPRZEDNICH STANÓW (Z CONFIGU) ---
                    int lastDataBack = config.getInt(path + ".last_data_back", 0);
                    int lastDataLeft = config.getInt(path + ".last_data_left", 0);
                    boolean lB = config.getBoolean(path + ".last_back", false);
                    boolean lL = config.getBoolean(path + ".last_left", false);
                    boolean lR = config.getBoolean(path + ".last_right", false);

                    int count = config.getInt(path + ".count", 0);
                    int limit = config.getInt(path + ".score_limit", 15);
                    boolean changed = false;

                    // --- LOGIKA: DODAWANIE (TYŁ) ---
                    if (dataBack > lastDataBack) {
                        count = Math.min(limit, count + (dataBack - lastDataBack));
                        changed = true;
                    }
                    else if (pB && !lB && count < limit) {
                        count++;
                        changed = true;
                    }

                    // --- LOGIKA: ODEJMOWANIE (LEWO) ---
                    if (dataLeft > lastDataLeft) {
                        count = Math.max(0, count - (dataLeft - lastDataLeft));
                        changed = true;
                    }
                    else if (pL && !lL && count > 0) {
                        count--;
                        changed = true;
                    }

                    // --- LOGIKA: RESET (PRAWO) ---
                    if (pR && !lR) {
                        count = 0;
                        changed = true;
                    }

                    // --- ZAPIS I AKTUALIZACJA ---
                    if (changed) {
                        config.set(path + ".count", count);

                        boolean finalState = (count > 0);
                        boolean previousState = config.getBoolean(path + ".state", false);

                        if (finalState != previousState) {
                            config.set(path + ".state", finalState);
                            GateUtils.updateOutput(plugin, path, target, finalState);
                        }

                        if (debug) {
                            org.bukkit.Bukkit.getConsoleSender().sendMessage(
                                    prefix + "§5COUNTER §7na §e" + key + " §7-> Nowy stan licznika: §d§l" + count + "§7/§5" + limit
                            );
                        }
                    }

                    String countStr = String.valueOf(count);
                    config.set(path + ".current_out", countStr);
                    config.set(path + ".last_data_back", dataBack);
                    config.set(path + ".last_data_left", dataLeft);
                    config.set(path + ".last_back", pB);
                    config.set(path + ".last_left", pL);
                    config.set(path + ".last_right", pR);
                }

                case "MATH" -> {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = right.getOppositeFace();

                    int vL_raw = GateUtils.getNumberFrom(gate.getRelative(left), left.getOppositeFace(), plugin);
                    int vR_raw = GateUtils.getNumberFrom(gate.getRelative(right), right.getOppositeFace(), plugin);

                    int vL = (vL_raw == Integer.MIN_VALUE) ? 0 : vL_raw;
                    int vR = (vR_raw == Integer.MIN_VALUE) ? 0 : vR_raw;

                    String m = config.getString(path + ".mode", "ADD").toUpperCase();

                    // Używamy LONG do bezpiecznych obliczeń
                    long calc = switch (m) {
                        case "SUB", "-" -> (long) vL - vR;
                        case "MUL", "*" -> (long) vL * vR;
                        case "DIV", "/" -> (vR != 0) ? (long) vL / vR : 0;
                        case "POW", "^" -> (long) Math.pow(vL, vR);
                        default -> (long) vL + vR;
                    };

                    // CLAMPING: Jeśli wynik jest poza zakresem INT, ustawiamy na granice
                    int result;
                    if (calc > Integer.MAX_VALUE) result = Integer.MAX_VALUE;
                    else if (calc < Integer.MIN_VALUE) result = Integer.MIN_VALUE; // Lub np. 0, jeśli wolisz
                    else result = (int) calc;

                    // Odczytujemy jako String. Domyślnie "" (pusty string to "brak wyniku")
                    String lastRes = config.getString(path + ".current_out", "");
                    String currentResStr = String.valueOf(result);

                    if (!currentResStr.equals(lastRes)) {
                        if (debug) {
                            org.bukkit.Bukkit.getConsoleSender().sendMessage(
                                    prefix + "§d§lMATH §e" + key + " §7wynik: §a§l" + result
                            );
                        }
                        config.set(path + ".current_out", currentResStr);
                        // Aktywna, jeśli wynik to cokolwiek poza 0 (zależnie od Twoich preferencji)
                        boolean isActive = (result != 0);
                        config.set(path + ".state", isActive);
                        GateUtils.updateOutput(plugin, path, target, isActive);
                    }
                }

                case "DECIMAL_ACCUMULATOR" -> {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = right.getOppositeFace();

                    boolean resetL = GateUtils.getPowerAt(gate.getRelative(left)) > 0;
                    boolean resetR = GateUtils.getPowerAt(gate.getRelative(right)) > 0;

                    // Pobieramy wartość
                    int inputVal = GateUtils.getNumberFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                    // 1. Reset
                    if (resetL || resetR) {
                        config.set(path + ".current_out", "0");
                        config.set(path + ".last_input", Integer.MIN_VALUE);
                        config.set(path + ".state", true);
                        GateUtils.updateOutput(plugin, path, target, true);
                    }

                    // 2. Logika APPEND
                    else {
                        // Pobieramy ostatni stan (domyślnie brak sygnału)
                        int lastInput = config.getInt(path + ".last_input", Integer.MIN_VALUE);

                        if (inputVal >= 0 && inputVal <= 9) {
                            if (lastInput == Integer.MIN_VALUE) {

                                String lastRes = config.getString(path + ".current_out", "0");
                                int currentVal = 0;
                                try {
                                    currentVal = Integer.parseInt(lastRes);
                                } catch (Exception e) {
                                    currentVal = 0;
                                }

                                int result = (currentVal * 10) + inputVal;

                                config.set(path + ".current_out", String.valueOf(result));
                                config.set(path + ".state", true);
                                GateUtils.updateOutput(plugin, path, target, true);

                                // Zapisujemy, że ten sygnał został już przetworzony
                                config.set(path + ".last_input", inputVal);
                            }
                        }
                        // Jeśli sygnał zniknął, resetujemy "last_input", żeby pozwolić na kolejny impuls
                        else if (inputVal == Integer.MIN_VALUE) {
                            config.set(path + ".last_input", Integer.MIN_VALUE);
                        }
                    }
                }

                case "COMPARATOR" -> {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = right.getOppositeFace();

                    int vL = GateUtils.getNumberFrom(gate.getRelative(left), left.getOppositeFace(), plugin);
                    int vR = GateUtils.getNumberFrom(gate.getRelative(right), right.getOppositeFace(), plugin);

                    // Jeśli lewe wejście (dane) to MIN_VALUE, od razu gasimy wyjście
                    if (vL == Integer.MIN_VALUE) {
                        if (!"MIN_VALUE".equals(config.getString(path + ".current_out", ""))) {
                            config.set(path + ".current_out", "MIN_VALUE");
                            config.set(path + ".state", false);
                            // Przekazujemy false, bo brak danych = brak sygnału
                            GateUtils.updateOutput(plugin, path, target, false);
                        }
                        return;
                    }

                    int valR = (vR == Integer.MIN_VALUE) ? 0 : vR;
                    String m = config.getString(path + ".mode", "==");

                    boolean result = switch (m) {
                        case ">"  -> vL > valR;
                        case "<"  -> vL < valR;
                        case "==" -> vL == valR;
                        case ">=" -> vL >= valR;
                        case "<=" -> vL <= valR;
                        case "!=" -> vL != valR;
                        default   -> false;
                    };

                    int finalVal = result ? vL : Integer.MIN_VALUE;
                    String currentResStr = String.valueOf(finalVal);
                    String lastOutStr = config.getString(path + ".current_out", "");

                    if (!currentResStr.equals(lastOutStr)) {
                        if (debug) {
                            String compColor = result ? "§a" : "§c";
                            org.bukkit.Bukkit.getConsoleSender().sendMessage(
                                    prefix + "§2COMPARATOR §7na §e" + key + " §7wynik (§6" + m + "§7): " + compColor + (result ? "TRUE" : "FALSE")
                            );
                        }
                        config.set(path + ".current_out", currentResStr);
                        config.set(path + ".state", result);

                        // POPRAWKA: Przekazujemy wynik logiczny (true/false) do metody Redstone'a
                        GateUtils.updateOutput(plugin, path, target, result);
                    }
                }

                case "DECODER" -> {
                    int incoming = GateUtils.getNumberFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    int targetValue = config.getInt(path + ".value", 0);
                    boolean isMatch = (incoming == targetValue && incoming != 0);
                    int finalVal = isMatch ? 1 : 0;

                    // 🔥 ZMIANA: Pełna konwersja na String przy zapisie i odczycie
                    String currentResStr = String.valueOf(finalVal);
                    String lastOutStr = config.getString(path + ".current_out", "");

                    if (!currentResStr.equals(lastOutStr)) {
                        if (debug) {
                            String matchColor = isMatch ? "§aTRAFIONY" : "§cBRAK DOPASOWANIA";
                            org.bukkit.Bukkit.getConsoleSender().sendMessage(
                                    prefix + "§eDECODER §7na §e" + key + " §7(Szukał: §6" + targetValue + "§7) -> Status: " + matchColor + " §7(Dostał: §b" + incoming + "§7)"
                            );
                        }
                        config.set(path + ".current_out", currentResStr);
                        config.set(path + ".state", isMatch);
                        GateUtils.updateOutput(plugin, path, target, isMatch);
                    }
                }

                case "RANDOM_BOOLEAN", "RANDOM_NUMBER" -> {
                    boolean in = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean lastIn = config.getBoolean(path + ".lastInput", false);

                    if (in && !lastIn) {
                        int result = type.equals("RANDOM_BOOLEAN")
                                ? (ThreadLocalRandom.current().nextBoolean() ? 1 : 0)
                                : ThreadLocalRandom.current().nextInt(config.getInt(path + ".min", 0), config.getInt(path + ".max", 15) + 1);

                        // SYSTEMOWA POPRAWKA ZAPISU ORAZ STANU (Obsługuje liczby ujemne!)
                        String currentResStr = String.valueOf(result);
                        boolean isActive = (result != 0);

                        if (debug) {
                            org.bukkit.Bukkit.getConsoleSender().sendMessage(
                                    prefix + "§a§lRANDOM §7[" + type + "] na §e" + key + " §7wylosował wartość: §b§l" + result
                            );
                        }

                        config.set(path + ".current_out", currentResStr);
                        config.set(path + ".state", isActive);
                        GateUtils.updateOutput(plugin, path, target, isActive);
                    }
                    config.set(path + ".lastInput", in);
                }
            }
        }
    }
}
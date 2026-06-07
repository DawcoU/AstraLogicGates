package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Bukkit;
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
                    long vBack = GateUtils.getNumberFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
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
                    long vL = GateUtils.getNumberFrom(gate.getRelative(faceL), faceL.getOppositeFace(), plugin);
                    if (vL == 0 || vL == Long.MIN_VALUE) vL = GateUtils.getPowerAt(gate.getRelative(faceL));
                    GateUtils.spawnStatusParticle(gate, faceL, vL > 0);

                    // PRAWO
                    long vR = GateUtils.getNumberFrom(gate.getRelative(faceR), faceR.getOppositeFace(), plugin);
                    if (vR == 0 || vR == Long.MIN_VALUE) vR = GateUtils.getPowerAt(gate.getRelative(faceR));
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
                    long storedValue = config.getLong(path + ".value", 0);

                    // LOGIKA: Jeśli OFF -> Long.MIN_VALUE, Jeśli ON -> storedValue
                    long valToSend = pBack ? storedValue : Long.MIN_VALUE;
                    String currentResStr = String.valueOf(valToSend);

                    String lastOutStr = config.getString(path + ".current_out", String.valueOf(Long.MIN_VALUE));

                    if (!currentResStr.equals(lastOutStr)) {
                        config.set(path + ".current_out", currentResStr);
                        config.set(path + ".state", pBack);
                        // GateUtils musi umieć odebrać longa i przekazać go dalej
                        GateUtils.updateOutput(plugin, path, target, pBack);
                    }
                }

                case "BOOLEAN_GATE" -> {
                    boolean hasPower = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    long valueToSend = hasPower ? 1L : 0L;

                    // 1. Przygotowujemy uniwersalny String systemowy
                    String currentResStr = String.valueOf(valueToSend);

                    // 2. Pobieramy poprzednią wartość jako STRING (konsekwentnie!)
                    String lastOutStr = config.getString(path + ".current_out", "");

                    // 3. Porównujemy String z Stringiem, żeby uniknąć wiecznej pętli
                    if (!currentResStr.equals(lastOutStr)) {

                        if (debug) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§3BOOLEAN_GATE §7na §e" + key + " §7-> Stan: " + (hasPower ? "§aWŁĄCZONY (1)" : "§cWYŁĄCZONY (0)")
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
                    long dataBack = GateUtils.getNumberFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    long dataLeft = GateUtils.getNumberFrom(gate.getRelative(left), left.getOppositeFace(), plugin);

                    // NORMALIZACJA:
                    if (dataBack == Long.MIN_VALUE) dataBack = 0;
                    if (dataLeft == Long.MIN_VALUE) dataLeft = 0;

                    boolean pB = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean pL = GateUtils.getPowerAt(gate.getRelative(left)) > 0;
                    boolean pR = GateUtils.getPowerAt(gate.getRelative(right)) > 0;

                    // --- POBIERANIE POPRZEDNICH STANÓW (Z CONFIGU) ---
                    long lastDataBack = config.getLong(path + ".last_data_back", 0);
                    long lastDataLeft = config.getLong(path + ".last_data_left", 0);
                    boolean lB = config.getBoolean(path + ".last_back", false);
                    boolean lL = config.getBoolean(path + ".last_left", false);
                    boolean lR = config.getBoolean(path + ".last_right", false);

                    long count = config.getLong(path + ".count", 0);
                    long limit = config.getLong(path + ".score_limit", 15);
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
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§5COUNTER §7na §e" + key + " §7-> Nowy stan licznika: §d§l" + count + "§7/§5" + limit
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

                    // 1. POBIERANIE DANYCH
                    long vL_raw = GateUtils.getNumberFrom(gate.getRelative(left), left.getOppositeFace(), plugin);
                    long vR_raw = GateUtils.getNumberFrom(gate.getRelative(right), right.getOppositeFace(), plugin);

                    // 2. NORMALIZACJA - Sprawdzamy Long.MIN_VALUE
                    long vL = (vL_raw == Long.MIN_VALUE) ? 0L : vL_raw;
                    long vR = (vR_raw == Long.MIN_VALUE) ? 0L : vR_raw;

                    String m = config.getString(path + ".mode", "ADD").toUpperCase();

                    // 3. OBLICZENIA - Czyste operacje na longach, bez żadnego rzutowania (long)
                    long result = switch (m) {
                        case "SUB", "-" -> vL - vR;
                        case "MUL", "*" -> vL * vR;
                        case "DIV", "/" -> (vR != 0) ? vL / vR : 0L;
                        // Przy potęgowaniu Math.pow zwraca double, więc musimy rzutować na (long)
                        case "POW", "^" -> (long) Math.pow(vL, vR);
                        default -> vL + vR;
                    };

                    // 4. ZAPIS I AKTUALIZACJA
                    String lastRes = config.getString(path + ".current_out", "");
                    String currentResStr = String.valueOf(result); // Teraz automatycznie sparsuje wielkiego longa

                    if (!currentResStr.equals(lastRes)) {
                        if (debug) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§d§lMATH §e" + key + " §7wynik: §a§l" + result
                            );
                        }
                        config.set(path + ".current_out", currentResStr);

                        boolean isActive = (result != 0L);
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
                    long inputVal = GateUtils.getNumberFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                    // 1. Reset
                    if (resetL || resetR) {
                        config.set(path + ".current_out", "0");
                        config.set(path + ".last_input", Long.MIN_VALUE);
                        config.set(path + ".state", true);
                        GateUtils.updateOutput(plugin, path, target, true);
                    }

                    // 2. Logika APPEND
                    else {
                        // Pobieramy ostatni stan (domyślnie brak sygnału)
                        long lastInput = config.getLong(path + ".last_input", Long.MIN_VALUE);

                        if (inputVal >= 0 && inputVal <= 9) {
                            if (lastInput == Long.MIN_VALUE) {

                                String lastRes = config.getString(path + ".current_out", "0");
                                long currentVal = 0;
                                try {
                                    currentVal = Long.parseLong(lastRes);
                                } catch (Exception e) {
                                    currentVal = 0;
                                }

                                long result = (currentVal * 10L) + inputVal;

                                config.set(path + ".current_out", String.valueOf(result));
                                config.set(path + ".state", true);
                                GateUtils.updateOutput(plugin, path, target, true);

                                // Zapisujemy, że ten sygnał został już przetworzony
                                config.set(path + ".last_input", inputVal);
                            }
                        }
                        // Jeśli sygnał zniknął, resetujemy "last_input", żeby pozwolić na kolejny impuls
                        else if (inputVal == Long.MIN_VALUE) {
                            config.set(path + ".last_input", Long.MIN_VALUE);
                        }
                    }
                }

                case "COMPARATOR" -> {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = right.getOppositeFace();

                    long vL = GateUtils.getNumberFrom(gate.getRelative(left), left.getOppositeFace(), plugin);
                    long vR = GateUtils.getNumberFrom(gate.getRelative(right), right.getOppositeFace(), plugin);

                    // Jeśli lewe wejście (dane) to MIN_VALUE, od razu gasimy wyjście
                    if (vL == Long.MIN_VALUE) {
                        if (!"MIN_VALUE".equals(config.getString(path + ".current_out", ""))) {
                            config.set(path + ".current_out", "MIN_VALUE");
                            config.set(path + ".state", false);
                            // Przekazujemy false, bo brak danych = brak sygnału
                            GateUtils.updateOutput(plugin, path, target, false);
                        }
                        break;
                    }

                    long valR = (vR == Long.MIN_VALUE) ? 0 : vR;
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

                    long finalVal = result ? vL : Long.MIN_VALUE;
                    String currentResStr = String.valueOf(finalVal);
                    String lastOutStr = config.getString(path + ".current_out", "");

                    if (!currentResStr.equals(lastOutStr)) {
                        if (debug) {
                            String compColor = result ? "§a" : "§c";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§2COMPARATOR §7na §e" + key + " §7wynik (§6" + m + "§7): " + compColor + (result ? "TRUE" : "FALSE")
                            );
                        }
                        config.set(path + ".current_out", currentResStr);
                        config.set(path + ".state", result);

                        // POPRAWKA: Przekazujemy wynik logiczny (true/false) do metody Redstone'a
                        GateUtils.updateOutput(plugin, path, target, result);
                    }
                }

                case "DECODER" -> {
                    long incoming = GateUtils.getNumberFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    long targetValue = config.getLong(path + ".value", 0);
                    boolean isMatch = (incoming == targetValue && incoming != 0);
                    long finalVal = isMatch ? 1L : 0L;

                    String currentResStr = String.valueOf(finalVal);
                    String lastOutStr = config.getString(path + ".current_out", "");

                    if (!currentResStr.equals(lastOutStr)) {
                        if (debug) {
                            String matchColor = isMatch ? "§aTRAFIONY" : "§cBRAK DOPASOWANIA";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§eDECODER §7na §e" + key + " §7(Szukał: §6" + targetValue + "§7) -> Status: " + matchColor + " §7(Dostał: §b" + incoming + "§7)"
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
                        long result = type.equals("RANDOM_BOOLEAN")
                                ? (ThreadLocalRandom.current().nextBoolean() ? 1L : 0L)
                                : ThreadLocalRandom.current().nextLong(config.getLong(path + ".min", 0), config.getLong(path + ".max", 15) + 1);

                        // SYSTEMOWA POPRAWKA ZAPISU ORAZ STANU (Obsługuje liczby ujemne!)
                        String currentResStr = String.valueOf(result);
                        boolean isActive = (result != 0);

                        if (debug) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§a§lRANDOM §7[" + type + "] na §e" + key + " §7wylosował wartość: §b§l" + result
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
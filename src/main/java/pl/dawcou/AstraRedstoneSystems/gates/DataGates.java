package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraRedstoneSystems.AstraRS;
import pl.dawcou.AstraRedstoneSystems.GateValidator;
import pl.dawcou.AstraRedstoneSystems.GateUtils;

public class DataGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    public DataGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runDataGates() {
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

            // --- INICJALIZACJA KIERUNKÓW ---
            BlockFace out;
            BlockFace back = null;
            Block target = null;
            boolean pBack = false;
            BlockFace right = null;
            BlockFace left = null;

            // Przypisujemy kierunki tylko wtedy, gdy bramka ich wymaga
            if (type.equals("DISPLAY") || type.equals("TRANSISTOR") || type.equals("DISK_GATE") || type.equals("RAM_GATE") || type.equals("BATTERY")) {
                String outName = config.getString(path + ".out", "NORTH");
                out = BlockFace.valueOf(outName.toUpperCase());
                back = out.getOppositeFace();
                target = gate.getRelative(out);

                // Wyliczamy boki na podstawie kierunku wyjścia (przodu) bramki
                right = GateUtils.rotate90(out);
                left = right.getOppositeFace();

                // --- EFEKTY WIZUALNE STATUSU ---
                boolean currentState = config.getBoolean(path + ".state", false);

                // 1. EFEKT WYJŚCIA (Włączony dla Transistora, Variable i teraz dla sprawnych Baterii)
                if (type.equals("TRANSISTOR") || type.equals("DISK_GATE") || type.equals("RAM_GATE") || type.equals("BATTERY")) {
                    GateUtils.spawnStatusParticle(gate, out, currentState);
                }

                // 2. EFEKT WEJŚCIA Z TYŁU (Zawsze dla wszystkich w tym bloku, w tym dla ładującej się baterii)
                pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                String vBack = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                boolean isActive = !vBack.isEmpty();

                GateUtils.spawnStatusParticle(gate, back, isActive || pBack);

                // 3. EFEKT BLOKADY Z BOKÓW (Tylko dla tych, które mają logikę blokowania)
                if (type.equals("TRANSISTOR") || type.equals("DISK_GATE") || type.equals("RAM_GATE")) {
                    boolean pLeft = GateUtils.getPowerAt(gate.getRelative(left)) > 0;
                    boolean pRight = GateUtils.getPowerAt(gate.getRelative(right)) > 0;

                    GateUtils.spawnStatusParticle(gate, left, pLeft);  // Lewy bok
                    GateUtils.spawnStatusParticle(gate, right, pRight); // Prawy bok
                }
            }

            // --- LOGIKA BRAMEK ---
            switch (type) {
                case "CABLE_DATA" -> {
                    String bestValue = "";
                    long maxPower = 0;
                    long myCurrentPower = config.getLong(path + ".power", 0);
                    String currentOut = config.getString(path + ".current_out", "");

                    for (BlockFace face : BlockFace.values()) {
                        if (!face.isCartesian()) continue;
                        Block neighbor = gate.getRelative(face);
                        String neighborPath = "gates." + GateUtils.locToStr(neighbor.getLocation());

                        if (!config.contains(neighborPath)) continue;

                        String nType = config.getString(neighborPath + ".type", "");

                        if (!nType.equals("CABLE_DATA")) {
                            boolean isActive = config.getBoolean(neighborPath + ".state", false);
                            if (!isActive) continue; // Ignorujemy tylko wyłączone bramki
                        }

                        String incoming = GateUtils.getStringFrom(neighbor, face.getOppositeFace(), plugin);

                        long neighborPower;
                        if (!nType.equals("CABLE_DATA")) {
                            String nOutStr = config.getString(neighborPath + ".out", "NORTH");
                            BlockFace nOut = BlockFace.valueOf(nOutStr.toUpperCase());
                            if (neighbor.getRelative(nOut).getLocation().equals(gate.getLocation())) {
                                neighborPower = 200;
                            } else {
                                neighborPower = 0;
                            }
                        } else {
                            neighborPower = config.getLong(neighborPath + ".power", 0);
                        }

                        // BLOKADA ANTY-ECHO / ANTY-SPAM:
                        // Jeśli sąsiad to kabel, ma taki sam tekst jak nasz obecny i jego moc jest MNIEJSZA lub równa naszej,
                        // to oznacza, że on żywi się NASZYM sygnałem! Ignorujemy go, żeby nie stworzyć pętli zwrotnej.
                        if (nType.equals("CABLE_DATA") && incoming.equals(currentOut) && neighborPower <= myCurrentPower) {
                            continue;
                        }

                        if (neighborPower > maxPower) {
                            maxPower = neighborPower;
                            bestValue = incoming;
                        }
                    }

                    long newPower = Math.max(0, maxPower - 1);

                    // Logika aktualizacji
                    if (!bestValue.equals(currentOut) || newPower != myCurrentPower) {
                        config.set(path + ".current_out", bestValue);
                        config.set(path + ".power", newPower);

                        if (debug) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§9CABLE_DATA §7na §e" + key +
                                            " §7zmienił stan: §b\"" + (bestValue.isEmpty() ? "PUSTY" : bestValue) + "\" §7(Moc: §3" + newPower + "§7)"
                            );
                        }
                    }

                    // Efekt wizualny
                    if (!bestValue.isEmpty() && newPower > 0) {
                        Location pLoc = gate.getLocation().clone().add(0.5, 1.1, 0.5);
                        gate.getWorld().spawnParticle(Particle.REDSTONE, pLoc, 2, 0, 0, 0, 0, new Particle.DustOptions(Color.AQUA, 1.0F));
                    }
                }

                case "DISPLAY" -> {
                    Location gateLoc = gate.getLocation();

                    // Jeśli chunk z bramką jest odładowany, nie robimy absolutnie nic
                    if (!gateLoc.getChunk().isLoaded()) {
                        return;
                    }

                    String rawData = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                    // Pobieramy stary UUID do porównania
                    String oldUuidStr = config.getString(path + ".displayUUID", "");
                    String uuidStr = GateUtils.validateDisplay(config, path, gateLoc);

                    config.set(path + ".displayUUID", uuidStr);

                    String lastOut = config.getString(path + ".current_out", "");
                    boolean isNewHologram = !uuidStr.equals(oldUuidStr);

                    // Aktualizacja tekstu tylko przy zmianie danych LUB nowym hologramie (w załadowanym chunku!)
                    if (!rawData.equals(lastOut) || isNewHologram) {

                        // Jeśli z jakiegoś powodu uuidStr jest pusty (bo chunk był odładowany), pomijamy update tekstu
                        if (uuidStr.isEmpty()) {
                            return;
                        }

                        String formatPattern = plugin.getConfig().getString(
                                "gates.data-gates.display.color", "{text}"
                        );

                        String finalValue = rawData.isEmpty() ? "" : rawData;
                        String textToParse = formatPattern.replace("{text}", finalValue);
                        String formattedData = plugin.getLanguageManager().parseToLegacy(textToParse);

                        GateUtils.updateDisplayNumber(plugin, uuidStr, formattedData);
                        config.set(path + ".current_out", rawData);

                        if (debug) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§fDISPLAY §7na §e" + key +
                                            " §7" + (isNewHologram ? "odtworzył hologram i ustawił" : "odświeżył") +
                                            " tekst na: " + formattedData
                            );
                        }
                    }
                }

                case "TRANSISTOR" -> {
                    // Próbujemy pobrać tekst z kabla z tyłu
                    String incomingString = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                    // Wybieramy co faktycznie płynie z tyłu
                    boolean incomingHasPower = !incomingString.isEmpty();

                    // Blokada z BOKÓW (lewy lub prawy) - używamy metody do zwykłego prądu!
                    long blockL = GateUtils.getPowerAt(gate.getRelative(left)); // Lewo
                    long blockR = GateUtils.getPowerAt(gate.getRelative(right)); // Prawo
                    boolean isBlocked = (blockL > 0 || blockR > 0);

                    // SYSTEMOWA POPRAWKA: Jeśli jest blokada lub brak sygnału z tyłu, wyjściem jest pustka ""
                    String result = (isBlocked || !incomingHasPower) ? "" : incomingString;
                    boolean hasPower = !result.isEmpty();

                    String lastOut = config.getString(path + ".current_out", "");
                    boolean lastState = config.getBoolean(path + ".state", false); // <--- POBIERAMY STARY STAN FIZYCZNY

                    // 1. Zapis tekstowy w configu i logi robimy, jeśli zmieniła się treść danych
                    if (!result.equals(lastOut)) {
                        if (debug) {
                            String statusColor = isBlocked ? "§c[BLOKADA]" : "§a[PRZEPŁYW]";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§6TRANSISTOR §7na §e" + key +
                                            " " + statusColor + " §7Sygnał z tyłu: §b\"" + (incomingString.isEmpty() ? "BRAK" : incomingString) + "\"" +
                                            " §7-> Wyjście: §d\"" + (result.isEmpty() ? "PUSTY" : result) + "\""
                            );
                        }
                        config.set(path + ".current_out", result);
                    }

                    // 2. FIZYCZNĄ AKTUALIZACJĘ ŚWIATA ROBIMY TYLKO WTEDY, GDY ZMIENIŁO SIĘ STAN WŁĄCZONY/WYŁĄCZONY!
                    if (hasPower != lastState) {
                        config.set(path + ".state", hasPower);
                        GateUtils.updateOutput(plugin, path, target, hasPower); // <--- ŚWIAT AKTUALIZUJE SIĘ TYLKO PRZY REALNEJ ZMIANIE SZYNY!
                    }
                }

                case "DISK_GATE" -> {
                    // 1. Sprawdzamy boki (right i left) pod kątem sygnału Reset
                    boolean reset = GateUtils.getPowerAt(gate.getRelative(right)) > 0 || GateUtils.getPowerAt(gate.getRelative(left)) > 0;

                    String currentStored = config.getString(path + ".value", "");
                    boolean previousState = config.getBoolean(path + ".state", false);

                    if (reset) {
                        // Odpalamy logikę resetu TYLKO jeśli bramka faktycznie NIE JEST jeszcze pusta
                        if (!currentStored.isEmpty() || previousState) {
                            config.set(path + ".value", "");
                            config.set(path + ".current_out", "");
                            config.set(path + ".state", false);

                            GateUtils.updateOutput(plugin, path, target, false);

                            if (debug) {
                                Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dDISK_GATE §e" + key + " §cZRESETOWANY sygnałem bocznym!");
                            }

                            // Wyczyszczone lokalnie, żeby sekcja poniżej zapisała czysty stan do pliku
                            currentStored = "";
                            config.set(path + ".value", "");
                        }
                    } else {
                        // AKCJA: Dane z tyłu zbieramy i zapisujemy TYLKO wtedy, gdy NIE MA RESETU!
                        // 2. Pobieramy dane z wejścia (tył) jako uniwersalny tekst/liczba
                        String incoming = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                        // 3. Logika "Zatrzymania" (Latch)
                        if (!incoming.isEmpty()) {
                            if (!incoming.equals(currentStored)) {
                                config.set(path + ".value", incoming);
                                currentStored = incoming;
                                if (debug) {
                                    Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dDISK_GATE §e" + key + " §7zapisała nową wartość: §b\"" + incoming + "\"");
                                }
                            }
                        }
                    }

                    // 4. Logika wyjścia - Zawsze sprawdzana, gwarantuje, że KAŻDA bramka zresetuje się równo!
                    boolean hasState = !currentStored.isEmpty();
                    previousState = config.getBoolean(path + ".state", false); // Pobieramy stan na nowo po if(reset)

                    if (hasState != previousState) {
                        config.set(path + ".state", hasState);
                        GateUtils.updateOutput(plugin, path, target, hasState);
                    }

                    // Synchronizacja uniwersalnego wyjścia tekstowego (To czyści current_out do spodu!)
                    if (!currentStored.equals(config.getString(path + ".current_out", ""))) {
                        config.set(path + ".current_out", currentStored);
                    }
                }

                case "RAM_GATE" -> {
                    // 1. Sprawdzamy boki (right i left) pod kątem sygnału Reset i zasilania
                    boolean reset = GateUtils.getPowerAt(gate.getRelative(left)) > 0;
                    boolean power = GateUtils.getPowerAt(gate.getRelative(right)) > 0;

                    String currentStored = config.getString(path + ".value", "");
                    boolean previousState = config.getBoolean(path + ".state", false);

                    if (reset || !power) {
                        // Odpalamy logikę resetu TYLKO jeśli bramka faktycznie NIE JEST jeszcze pusta
                        if (!currentStored.isEmpty() || previousState) {
                            GateUtils.updateOutput(plugin, path, target, false);

                            if (debug) {
                                Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dRAM_GATE §e" + key + " §cZRESETOWANY sygnałem bocznym!");
                            }

                            // Wyczyszczone lokalnie, żeby sekcja poniżej zapisała czysty stan do pliku
                            currentStored = "";
                            config.set(path + ".value", "");
                        }
                    } else {
                        // AKCJA: Dane z tyłu zbieramy i zapisujemy TYLKO wtedy, gdy NIE MA RESETU!
                        // 2. Pobieramy dane z wejścia (tył) jako uniwersalny tekst/liczba
                        String incoming = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                        // 3. Logika "Zatrzymania" (Latch)
                        if (!incoming.isEmpty()) {
                            if (!incoming.equals(currentStored)) {
                                config.set(path + ".value", incoming);
                                currentStored = incoming;
                                if (debug) {
                                    Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dRAM_GATE §e" + key + " §7zapisała nową wartość: §b\"" + incoming + "\"");
                                }
                            }
                        }
                    }

                    // 4. Logika wyjścia - Zawsze sprawdzana, gwarantuje, że KAŻDA bramka zresetuje się równo!
                    boolean hasState = !currentStored.isEmpty();
                    previousState = config.getBoolean(path + ".state", false); // Pobieramy stan na nowo po if(reset)

                    if (hasState != previousState) {
                        config.set(path + ".state", hasState);
                        GateUtils.updateOutput(plugin, path, target, hasState);
                    }

                    // Synchronizacja uniwersalnego wyjścia tekstowego (To czyści current_out do spodu!)
                    if (!currentStored.equals(config.getString(path + ".current_out", ""))) {
                        config.set(path + ".current_out", currentStored);
                    }
                }

                case "BATTERY" -> {
                    // --- ODCZYT I INICJALIZACJA DANYCH ---
                    long charge = config.getLong(path + ".charge", 0L);
                    long lastChargeTick = config.getLong(path + ".last_charge_tick", 0L);
                    long lastDecay = config.getLong(path + ".last_decay", 0L);
                    long currentTime = System.currentTimeMillis();

                    long configDecayMinutes = plugin.getConfig().getLong("gates.data-gates.battery.decay-time-minutes", 5L);
                    long decayInterval = configDecayMinutes * 60L * 1000L;

                    long configChargeSeconds = plugin.getConfig().getLong("gates.data-gates.battery.charge-time-seconds", 5L);
                    long chargeInterval = configChargeSeconds * 1000L;

                    // Jeśli bateria jest tworzona po raz pierwszy, ustawiamy czas decay na aktualny
                    if (lastDecay == 0L) {
                        config.set(path + ".last_decay", currentTime);
                        lastDecay = currentTime;
                    }

                    // --- SPRAWDZENIE CZY ŁADOWARKA JEST PODPIĘTA (TYŁ) ---
                    String vBack = GateUtils.getStringFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    boolean isInputPowered = pBack || !vBack.isEmpty();

                    // --- 1. MECHANIZM AUTOMATYCZNEGO ROZŁADOWYWANIA (Z priorytetem ładowania) ---
                    if (currentTime - lastDecay >= decayInterval) {
                        if (isInputPowered) {
                            // PRIORYTET: Ładowarka działa! Blokujemy spadek energii i przesuwamy czas na teraz
                            config.set(path + ".last_decay", currentTime);
                            if (debug) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§eBATTERY §7na §e" + key + " §bBlokada spadku energii - trwa ładowanie."
                                );
                            }
                        } else if (charge > 0L) {
                            // Brak ładowania, bateria ma prąd -> rozładowujemy o 1%
                            charge = Math.max(0L, charge - 1L);
                            config.set(path + ".charge", charge);
                            config.set(path + ".last_decay", currentTime);

                            if (debug) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§eBATTERY §7na §e" + key + " §cStraciła 1% energii (Brak zasilania). Stan: §b" + charge + "%"
                                );
                            }
                        } else {
                            // Bateria pusta i brak zasilania -> przesuwamy czas o interval (ochrona dysku)
                            config.set(path + ".last_decay", lastDecay + decayInterval);
                        }
                    }

                    // --- 2. MECHANIZM STAŁEGO ŁADOWANIA (Czas brany z nowej opcji w configu!) ---
                    if (isInputPowered && (currentTime - lastChargeTick >= chargeInterval)) {
                        if (charge < 100L) {
                            charge = Math.min(100L, charge + 1L);
                            config.set(path + ".charge", charge);
                            config.set(path + ".last_charge_tick", currentTime);

                            if (debug) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§eBATTERY §7na §e" + key + " §aŁadowanie sieciowe... Stan: §b" + charge + "%"
                                );
                            }
                        }
                    }

                    // --- 3. LOGIKA WYJŚCIA ---
                    boolean hasPower = charge > 0L;
                    boolean previousState = config.getBoolean(path + ".state", false);

                    if (hasPower != previousState) {
                        config.set(path + ".state", hasPower);
                        GateUtils.updateOutput(plugin, path, target, hasPower);

                        if (debug) {
                            String powerStatus = hasPower ? "§aWŁĄCZONE" : "§cWYŁĄCZONE (0%)";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§eBATTERY §7na §e" + key + " §7zmieniła stan wyjściowy na: " + powerStatus
                            );
                        }
                    }

                    // Synchronizacja uniwersalnego wyjścia tekstowego (dla kabli i wyświetlaczy)
                    String chargeString = charge + "%";
                    if (!chargeString.equals(config.getString(path + ".current_out", ""))) {
                        config.set(path + ".current_out", chargeString);
                    }
                }
            }
        }
    }
}
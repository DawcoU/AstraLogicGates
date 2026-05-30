package pl.dawcou.AstraRedstoneSystems;

import net.md_5.bungee.api.ChatColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import pl.dawcou.AstraRedstoneSystems.gates.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AstraRS extends JavaPlugin implements CommandExecutor, TabCompleter {

    public static final String PREFIX = ChatColor.of("#3277e6") + "["
            + ChatColor.of("#F2F2F2") + "Astra"
            + ChatColor.of("#FF2E2E") + "RS"
            + ChatColor.of("#3277e6") + "]";

    public static final String PREFIX2 = "§9[§fAstra§cRS§9]";

    private File gatesFile;
    private FileConfiguration gatesConfig;
    private final Map<UUID, Location> linkingSession = new HashMap<>();

    private GateValidator gateValidator;
    private BasicGates basicGates;
    private MemoryGates memoryGates;
    private TimeGates timeGates;
    private NumberGates numberGates;
    private StringGates stringGates;
    private DataGates dataGates;
    private SpaceGates spaceGates;

    private LanguageManager languageManager;
    private NoticeManager noticeManager;

    public void setLanguageManager(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public NoticeManager getNoticeManager() {
        return noticeManager;
    }

    public GateValidator getValidator() { return gateValidator; }

    public FileConfiguration getGatesConfig() {
        return gatesConfig;
    }

    public Map<UUID, Location> getLinkingSession() {
        return this.linkingSession;
    }

    @Override
    public void onEnable() {
        int pluginId = 31505;
        Metrics metrics = new Metrics(this, pluginId);

        this.gateValidator = new GateValidator();
        this.noticeManager = new NoticeManager(this);
        this.languageManager = new LanguageManager(this);

        this.basicGates = new BasicGates(this, gateValidator);
        this.memoryGates = new MemoryGates(this, gateValidator);
        this.timeGates = new TimeGates(this, gateValidator);
        this.numberGates = new NumberGates(this, gateValidator);
        this.stringGates = new StringGates(this, gateValidator);
        this.dataGates = new DataGates(this, gateValidator);
        this.spaceGates = new SpaceGates(this, gateValidator);

        SelectionManager selectionManager = new SelectionManager(this);
        FilesUpdater updater = new FilesUpdater(this);
        CommandManager commandHandler = new CommandManager (this, selectionManager);

        new FilesConverter(this).runAllMigrations();

        saveDefaultConfig();
        createGatesConfig();

        updater.check();

        this.languageManager.reload();

        // 2. Rejestrujesz TĘ SAMĄ instancję do eventów
        getServer().getPluginManager().registerEvents(selectionManager, this);
        getServer().getPluginManager().registerEvents(new GateListener(this), this);

        var cmdBramka = getCommand("bramka");
        if (cmdBramka != null) {
            cmdBramka.setExecutor(this);
            cmdBramka.setTabCompleter(this);
        }

        var cmdAlg = getCommand("astraredstonesystems");
        if (cmdAlg != null) {
            cmdAlg.setExecutor(commandHandler);
            cmdAlg.setTabCompleter(commandHandler);
        }

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (task) -> {

            // 2. Bardzo ważna kolejność!
            // Najpierw źródła liczb, potem kable, na końcu reszta
            numberGates.runNumberGates();
            stringGates.runStringGates();
            dataGates.runDataGates();

            basicGates.runBasicGates();
            memoryGates.runMemoryGates();
            timeGates.runTimeGates();
            spaceGates.runSpaceGates();
            
        }, 20L, 1L);

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (task) -> {
            saveGates();
        }, 1200L, 1200L);

        // Odpalamy scheduler asynchroniczny, który najpierw sprawdzi internet, a na koniec wypluje logo i status wersji!
        this.getServer().getAsyncScheduler().runNow(this, task -> {

            // Najpierw sprawdzamy aktualizacje, jeśli opcja jest włączona
            if (getConfig().getBoolean("settings.check-updates", true)) {
                new UpdateChecker(this).getVersion(version -> {
                    String currentVersion = this.getDescription().getVersion();

                    // 1. NAJPIERW DRUKUJEMY LOGO (Zawsze jako pierwsze, niezależnie od wyniku sieci)
                    noticeManager.sendStartupLogo();

                    // 2. ZARAZ POD LOGO DORZUCAMY INFO O WERSJI
                    if (currentVersion.equals(version)) {
                        noticeManager.sendVersionOk(version);
                    } else if (currentVersion.compareTo(version) > 0) {
                        noticeManager.sendDevNotice(currentVersion, version);
                    } else {
                        noticeManager.sendUpdateNotice(Bukkit.getConsoleSender(), version);
                    }
                });
            } else {
                // Jeśli admin wyłączył sprawdzanie aktualizacji, po prostu drukujemy samo logo!
                noticeManager.sendStartupLogo();
            }
        });
    }

    @Override
    public void onDisable() {
        // Zapisanie danych bramek z pamięci RAM na dysk
        saveGates();

        noticeManager.sendShutdownLogo();
    }

    private void createGatesConfig() {
        gatesFile = new File(getDataFolder(), "logic_gates.yml");
        if (!gatesFile.exists()) {
            if (gatesFile.getParentFile().mkdirs()) {
                try {
                    gatesFile.createNewFile();
                } catch (IOException ignored) {}
            }
        }
        gatesConfig = YamlConfiguration.loadConfiguration(gatesFile);
    }

    public void saveGates() {
        synchronized (this.gatesConfig) {
            try {
                gatesConfig.save(gatesFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("bramka")) {
            if (!player.hasPermission("astrars.gates")) {
                player.sendMessage(this.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(this.getLanguageManager().getWithPrefix("usage-gate-command"));
                return true;
            }

            String category = args[0].toLowerCase();
            String type = args[1].toUpperCase();
            Material mat = switch (category) {
                case "logic" -> switch (type) {
                    case "NOT", "NOR" -> Material.RED_CONCRETE;
                    case "AND", "OR", "BUFFER", "PULSER" -> Material.YELLOW_CONCRETE;
                    case "NAND", "XNOR", "NIMPLY" -> Material.ORANGE_CONCRETE;
                    case "XOR", "IMPLY", "MUX" -> Material.LIME_CONCRETE;
                    case "SYNCHRONIZER" -> Material.BROWN_CONCRETE;
                    default -> null;
                };
                case "memory" -> switch (type) {
                    case "LATCH" -> Material.CYAN_CONCRETE;
                    case "TFF" -> Material.LIGHT_BLUE_CONCRETE;
                    case "MEMORY_CELL", "MEMORY_READ" -> Material.BLUE_CONCRETE;
                    default -> null;
                };
                case "numbers" -> switch (type) {
                    case "MATH", "DECIMAL_ACCUMULATOR" -> Material.BLUE_CONCRETE;
                    case "COUNTER" -> Material.LIGHT_GRAY_CONCRETE;
                    case "COMPARATOR", "DECODER" -> Material.GRAY_CONCRETE;
                    case "RANDOM_BOOLEAN", "RANDOM_NUMBER" -> Material.CYAN_CONCRETE;
                    case "NUMBER_GATE", "BOOLEAN_GATE" -> Material.BROWN_CONCRETE;
                    default -> null;
                };
                case "string" -> switch (type) {
                    case "STRING_COMPARATOR", "STRING_DECODER" -> Material.GRAY_CONCRETE;
                    case "STRING_GATE" -> Material.BROWN_CONCRETE;
                    default -> null;
                };
                case "data" -> switch (type) {
                    case "CABLE_DATA" -> Material.BLACK_CONCRETE;
                    case "DISPLAY" -> Material.WHITE_CONCRETE;
                    case "TRANSISTOR" -> Material.RED_CONCRETE;
                    case "VARIABLE_GATE" -> Material.LIGHT_BLUE_CONCRETE;
                    case "BATTERY" -> Material.ORANGE_CONCRETE;
                    default -> null;
                };
                case "space" -> switch (type) {
                    case "SENDER" -> Material.MAGENTA_CONCRETE;
                    case "RECEIVER" -> Material.PURPLE_CONCRETE;
                    case "SENSOR" -> Material.PINK_CONCRETE;
                    default -> null;
                };
                case "time" -> switch (type) {
                    case "CLOCK" -> Material.GREEN_CONCRETE;
                    case "CLOCK_GATE" -> Material.LIME_CONCRETE;
                    case "REPEATER" -> Material.YELLOW_CONCRETE;
                    default -> null;
                };
                default -> null;
            };

            if (mat == null) {
                player.sendMessage(this.getLanguageManager().getWithPrefix("unknown-type-gate"));
                return true;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return true;

            meta.setDisplayName("§eBramka: §6" + type);
            List<String> lore = new ArrayList<>();

            if (type.equals("SENDER") || type.equals("RECEIVER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("need-channel"));
                    return true;
                }
                lore.add("§7Kanał: §f" + args[2].replace(" ", ""));

            } else if (type.equals("NUMBER_GATE") || type.equals("DECODER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-value"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("not-a-number"));
                    return true;
                }

                lore.add("§7Wartość: §f" + args[2]);

            } else if (type.equals("RANDOM_NUMBER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-range2"));
                    return true;
                }

                String val = args[2];

                if (val.contains("-")) {
                    String[] parts = val.split("-");

                    if (parts.length == 2) {
                        try {
                            int min = Integer.parseInt(parts[0]);
                            int max = Integer.parseInt(parts[1]);

                            // BLOKADA UJEMNYCH I BŁĘDNYCH ZAKRESÓW
                            if (min < 0 || max < 0) {
                                player.sendMessage(this.getLanguageManager().getWithPrefix("negative-number"));
                                return true;
                            }

                            if (min > max) {
                                player.sendMessage(this.getLanguageManager().getWithPrefix("min-greater-than-max"));
                                return true;
                            }

                            lore.add("§7min: §f" + min);
                            lore.add("§7max: §f" + max);

                        } catch (NumberFormatException e) {
                            player.sendMessage(this.getLanguageManager().getWithPrefix("not-a-number"));
                            return true;
                        }
                    } else {
                        player.sendMessage(this.getLanguageManager().getWithPrefix("wrong-format"));
                        return true;
                    }
                } else {
                    return true;
                }

            } else if (type.equals("MATH")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-mode"));
                    return true;
                }

                String modeName;
                switch (args[2]) {
                    case "+":
                    case "add":
                        modeName = "Add";
                        break;
                    case "-":
                    case "sub":
                        modeName = "Subtract";
                        break;
                    case "*":
                    case "x":
                    case "mul":
                        modeName = "Multiply";
                        break;
                    case "/":
                    case "div":
                        modeName = "Divide";
                        break;
                    case "^":
                    case "pow":
                        modeName = "Power";
                        break;
                    default:
                        modeName = "Add";
                        break;
                }

                lore.add("§7Tryb: §f" + modeName);

            } else if (type.equals("COMPARATOR")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-sign"));
                    return true;
                }

                String sign = args[2];

                if (!sign.matches(">|<|==|!=|>=|<=")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("invalid-sign"));
                    return true;
                }

                lore.add("§7Tryb: §f" + sign);

            } else if (type.equals("COUNTER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-limit"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("not-a-number"));
                    return true;
                }

                int val = Integer.parseInt(args[2]);

                if (val < 1 || val > 100) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("limit-range"));
                    return true;
                }

                lore.add("§7Limit: §f" + args[2]);

            } else if (type.equals("SENSOR")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-range"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("not-a-number"));
                    return true;
                }

                int val = Integer.parseInt(args[2]);

                if (val < 1 || val > 15) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("range-out-of-bounds"));
                    return true;
                }

                lore.add("§7Zasięg: §f" + args[2]);

            } else if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-delay"));
                    return true;
                }

                String input = args[2].toLowerCase();

                if (!input.endsWith("t") && !input.endsWith("s")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-unit"));
                    return true;
                }

                String numStr = input.substring(0, input.length() - 1);

                if (!numStr.matches("-?\\d+")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("invalid-time-format"));
                    return true;
                }

                int val = Integer.parseInt(numStr);

                if (input.endsWith("t")) {
                    if (val < 5 || val > 200) {
                        player.sendMessage(this.getLanguageManager().getWithPrefix("ticks-range-reapeter"));
                        return true;
                    }
                } else if (input.endsWith("s")) {
                    if (val < 1 || val > 10) {
                        player.sendMessage(this.getLanguageManager().getWithPrefix("seconds-range-reapeter"));
                        return true;
                    }
                }

                lore.add("§7Czas: §f" + input);

            } else if (type.equals("STRING_GATE") || type.equals("STRING_DECODER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-text"));
                    return true;
                }

                // Łączymy wszystkie argumenty od args[2] wzwyż
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    sb.append(args[i]).append(" ");
                }
                String textValue = sb.toString().trim();

                // Normalny, uniwersalny tekst dla obu bramek
                lore.add("§7Tekst: §f" + textValue);

            } else if (type.equals("STRING_COMPARATOR")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-string-mode"));
                    return true;
                }

                String mode = args[2].toUpperCase();

                // Sprawdzamy czy gracz wpisał poprawny tryb dla tekstu
                if (!mode.matches("==|EQUALS|EQUALS_IGNORE_CASE|=I|CONTAINS|STARTS_WITH|ENDS_WITH|EMPTY")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("invalid-string-sign"));
                    return true;
                }

                lore.add("§7Tryb: §f" + mode);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            player.sendMessage(this.getLanguageManager().getWithPrefix("gate-received", "{TYPE}", type));
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> hints = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("bramka")) {
            if (args.length == 1) {
                Arrays.asList("logic", "memory", "numbers", "string", "data", "space", "time").forEach(c -> {
                    if (c.startsWith(args[0].toLowerCase())) hints.add(c);
                });
            } else if (args.length == 2) {
                List<String> types = switch (args[0].toLowerCase()) {
                    case "logic" ->
                            Arrays.asList("NOT", "AND", "OR", "NOR", "NAND", "XOR", "XNOR", "NIMPLY", "IMPLY", "BUFFER", "MUX", "SYNCHRONIZER", "PULSER");
                    case "memory" -> Arrays.asList("LATCH", "TFF", "MEMORY_CELL", "MEMORY_READ");
                    case "numbers" ->
                            Arrays.asList("COUNTER", "RANDOM_BOOLEAN", "RANDOM_NUMBER", "NUMBER_GATE", "BOOLEAN_GATE", "MATH", "DECIMAL_ACCUMULATOR", "COMPARATOR", "DECODER");
                    case "string" ->
                            Arrays.asList("STRING_GATE", "STRING_COMPARATOR", "STRING_DECODER");
                    case "data" ->
                            Arrays.asList("CABLE_DATA", "DISPLAY", "TRANSISTOR", "VARIABLE_GATE", "BATTERY");
                    case "space" -> Arrays.asList("SENDER", "RECEIVER", "SENSOR");
                    case "time" -> Arrays.asList("CLOCK", "CLOCK_GATE", "REPEATER");
                    default -> Collections.emptyList();
                };
                types.forEach(t -> {
                    if (t.startsWith(args[1].toUpperCase())) hints.add(t);
                });
            } else if (args.length == 3) {
                String type = args[1].toUpperCase();
                if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) hints.addAll(Arrays.asList("10t", "1s"));
                else if (type.equals("MATH")) hints.addAll(Arrays.asList("+", "-", "x", "/", "^"));
                else if (type.equals("COMPARATOR")) hints.addAll(Arrays.asList(">", "<", "==", "!=", ">=", "<="));
                else if (type.equals("STRING_COMPARATOR")) hints.addAll(Arrays.asList("==", "EQUALS", "EQUALS_IGNORE_CASE", "=I", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "EMPTY"));
                else if (type.equals("SENSOR")) hints.add("5");
                else if (type.equals("COUNTER")) hints.add("10");
                else if (type.equals("NUMBER_GATE")) hints.add("1");
                else if (type.equals("RANDOM_NUMBER")) hints.addAll(Arrays.asList("0-5", "0-10"));
            }
        }
        return hints;
    }
}
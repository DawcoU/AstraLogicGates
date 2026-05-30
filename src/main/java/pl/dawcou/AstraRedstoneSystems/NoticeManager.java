package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class NoticeManager {

    private final AstraRS plugin;
    private final String PREFIX;
    private final String PREFIX2;

    public NoticeManager(AstraRS plugin) {
        this.plugin = plugin;
        this.PREFIX = AstraRS.PREFIX;
        this.PREFIX2 = AstraRS.PREFIX2;
    }

    // Pomocnicza metoda do pobierania języka
    private String getLang() {
        return plugin.getConfig().getString("language", "pl");
    }

    public void sendConfigUpdateNotice() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§aPomyślnie dopisano brakujące linijki do configu" : "§aSuccessfully added missing lines to the config";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cBłąd podczas zapisu configu: " : "§cError while saving config: ";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + error);
    }

    public void sendUpdateCheckError() {
        String msg = getLang().equalsIgnoreCase("pl") ? "§cNie udało się sprawdzić aktualizacji na Modrinth" : "§cFailed to check for updates on Modrinth";
        plugin.getLogger().warning(msg);
    }

    public void sendVersionOk(String version) {
        String msg = getLang().equalsIgnoreCase("pl") ? "§aAstraRedstoneSystems jest aktualny §f(§ev" + version + "§f)" : "§aAstraLogin is up to date §f(§ev" + version + "§f)";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendLangUpdateSuccess(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§aDodano brakujące linijki w pliku językowym:" :
                "§aAdded missing lines in the language file" ;
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg + " §e" + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg = getLang().equalsIgnoreCase("pl") ?
                "§cNie udało się zaktualizować pliku językowego (" + fileName + "):" :
                "§cFailed to update language file (" + fileName + "): ";
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl") ? "§eDostępna jest nowa wersja AstraRedstoneSystems: §fv" : "§eA new version of AstraLogicGates is available: §fv";
        String download = getLang().equalsIgnoreCase("pl") ? "§aPobierz: " : "§aDownload: ";
        target.sendMessage("");
        target.sendMessage("§7------------ " + PREFIX2 + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7----------------------------------------------");
        target.sendMessage("");
    }

    public void sendDevNotice(String currentVersion, String latestStable) {
        String devTitle = getLang().equalsIgnoreCase("pl") ? "§bUżywasz wersji testowej: §f§nv" : "§bYou are using a Development version: §f§nv";
        String stableInfo = getLang().equalsIgnoreCase("pl") ? "§eNa Modrinth najnowsza stabilna to: §fv" : "§eThe latest stable on Modrinth is: §fv";
        String warning = getLang().equalsIgnoreCase("pl") ? "§bUważaj na błędy, kod jest w fazie rozwoju!" : "§bWatch out for bugs, the code is in development!";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage(devTitle + currentVersion);
        Bukkit.getConsoleSender().sendMessage(stableInfo + latestStable);
        Bukkit.getConsoleSender().sendMessage(warning);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendMigrationNotice(String oldName, String newName) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§6Migracja: §f" + oldName + " §7-> §f" + newName + "..."
                : "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendSuccessNotice(String oldName) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§aPlik §f" + oldName + " §azostał przeniesiony"
                : "§aFile §f" + oldName + " §ahas been moved";
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendErrorNotice(String action) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§cBłąd podczas migracji pliku: §f" + action
                : "§cError during file migration: §f" + action;
        Bukkit.getConsoleSender().sendMessage(PREFIX2 + " " + msg);
    }

    public void sendStartupLogo() {
        String v = plugin.getDescription().getVersion();
        String version = getLang().equalsIgnoreCase("pl") ? "§6Wersja" : "§aVersion";
        String status = getLang().equalsIgnoreCase("pl") ? "§aWłączony" : "§aEnabled";
        String author = getLang().equalsIgnoreCase("pl") ? "§6   Autor: §e" : "§6   Author: §e";
        String statusLabel = getLang().equalsIgnoreCase("pl") ? "§6   Status: " : "§6   Status: ";

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6   " + version + " §ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(author + "DawcoU");
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendShutdownLogo() {
        String status = getLang().equalsIgnoreCase("pl") ? "§cWyłączony" : "§cDisabled";
        String farewell = getLang().equalsIgnoreCase("pl") ? "§eDo zobaczenia! :D" : "§eSee you! :D";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX2 + " §7---------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }
}
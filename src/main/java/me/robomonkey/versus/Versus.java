package me.robomonkey.versus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.samjakob.spigui.SpiGUI;
import me.robomonkey.versus.arena.ArenaManager;
import me.robomonkey.versus.arena.command.RootArenaCommand;
import me.robomonkey.versus.dependency.Dependencies;
import me.robomonkey.versus.duel.DuelManager;
import me.robomonkey.versus.duel.HorseHealManager;
import me.robomonkey.versus.duel.command.RootDuelCommand;
import me.robomonkey.versus.duel.command.RootSpectateCommand;
import me.robomonkey.versus.duel.playerdata.adapter.ConfigurationSerializableAdapter;
import me.robomonkey.versus.duel.playerdata.adapter.ItemStackAdapter;
import me.robomonkey.versus.duel.playerdata.adapter.ItemStackArrayAdapter;
import me.robomonkey.versus.settings.Setting;
import me.robomonkey.versus.settings.Settings;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Versus extends JavaPlugin {

    private HorseHealManager horseHealManager;
    private static Gson gson;
    private ArenaManager arenaManager;
    private DuelManager duelManager;
    private static Versus instance;
    private final static String prefix = "[Versus]";
    private static final int pluginId = 23279;
    public static SpiGUI spiGUI;
    private final Map<UUID, Long> horseHealCooldown = new HashMap<>();
    private final Map<UUID, Integer> horseHealTaskId = new HashMap<>();

    public static final double HEAL_PER_WHEAT = 10.0;      // 한 개의 밀짚으로 회복할 최대 체력
    public static final double HEAL_STEP = 1.0;            // 1틱마다 회복하는 체력
    public static final long HEAL_PERIOD_TICKS = 2L;       // BukkitRunnable 주기 (틱 단위)

    public static void log(String message) {
        Bukkit.getServer().getLogger().info(prefix + " " + message);
    }

    public static void error(String message) {
        log("Error: " + message);
    }

    public static Gson getGSON() {
        if (gson == null) {
            GsonBuilder builder = new GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .registerTypeAdapter(ConfigurationSerializable.class, new ConfigurationSerializableAdapter())
                    .registerTypeAdapter(ItemStack.class, new ItemStackAdapter())
                    .registerTypeAdapter(ItemStack[].class, new ItemStackArrayAdapter());
            gson = builder.create();
        }
        return gson;
    }

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static Versus getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        // **instance 먼저 초기화**
        instance = this;

        // Settings 초기화
        Settings.getInstance().registerConfig();

        // SpiGUI 초기화
        spiGUI = new SpiGUI(this);

        // 매니저 초기화
        duelManager = DuelManager.getInstance();
        arenaManager = ArenaManager.getInstance();
        arenaManager.loadArenas();

        // HorseHealManager 초기화
        horseHealManager = new HorseHealManager();

        // 커맨드 및 의존성, 통계 등록
        registerCommands();
        Dependencies.refresh(getServer());
        registerMetrics();

        log("Versus has been enabled!");

        // onEnable() 안에 추가
        Bukkit.getPluginManager().registerEvents(horseHealManager, this);
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) {
            arenaManager.saveAllArenas();
        }
    }

    public void registerCommands() {
        new RootArenaCommand();
        new RootVersusCommand();
        new RootDuelCommand();
        new RootSpectateCommand();
    }

    private void registerMetrics() {
        Metrics metrics = new Metrics(this, Versus.pluginId);
        List<Setting> noted = List.of(
                Setting.FIGHT_MUSIC_ENABLED,
                Setting.VICTORY_MUSIC_ENABLED,
                Setting.RETURN_WINNERS,
                Setting.RETURN_LOSERS,
                Setting.ANNOUNCE_DUELS,
                Setting.FIREWORKS_ENABLED,
                Setting.VICTORY_EFFECTS_ENABLED
        );
        noted.stream()
                .forEach(setting -> metrics.addCustomChart(
                        new SimplePie(setting.toString().toLowerCase(), () -> Settings.getStringVersion(setting))
                ));
    }

}


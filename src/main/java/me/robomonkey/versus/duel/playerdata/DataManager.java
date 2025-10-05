package me.robomonkey.versus.duel.playerdata;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.robomonkey.versus.Versus;
import me.robomonkey.versus.arena.Arena;
import me.robomonkey.versus.arena.data.LocationData;
import me.robomonkey.versus.util.JsonUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DataManager {
    private Map<UUID, PlayerData> dataMap;
    private File dataFile;
    private Gson inventoryGSON;
    private final Versus plugin; // plugin 필드 추가

    public enum DataType {
        LOCATION,
        INVENTORY
    }

    // 생성자에서 plugin 받도록 수정
    public DataManager(Versus plugin) {
        this.plugin = plugin;
        dataMap = new HashMap<>();
        dataFile = JsonUtil.getDataFile(plugin, "inventory.json");
        inventoryGSON = plugin.getGSON();
    }
    public void save(Player player, Arena currentArena) {
        PlayerData data = new PlayerData(player, currentArena);
        dataMap.put(player.getUniqueId(), data);
    }

    public void update(Player player, DataType type) {
        PlayerData data = get(player);
        if (data == null) return;
        if (type == DataType.LOCATION) {
            data.previousLocation = new LocationData(player.getLocation());
        }
        if (type == DataType.INVENTORY) {
            data.items = player.getInventory().getContents();
        }
    }

    private void remove(UUID id) {
        dataMap.remove(id);
        saveDataMap();
    }

    public PlayerData extractData(Player player) {
        PlayerData data = get(player);
        remove(player.getUniqueId());
        return data;
    }

    public PlayerData get(Player player) {
        return dataMap.get(player.getUniqueId());
    }

    public boolean contains(Player player) {
        return dataMap.containsKey(player.getUniqueId());
    }

    public void saveDataMap() {
        Type playerDataMapType = new TypeToken<Map<UUID, PlayerData>>() {}.getType();
        try (FileWriter writer = new FileWriter(dataFile)) {
            inventoryGSON.toJson(dataMap, playerDataMapType, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadDataMap() {
        plugin.getLogger().info("Loading playerdata from inventory.json");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Type playerDataMapType = new TypeToken<Map<UUID, PlayerData>>() {}.getType();
                FileReader reader = new FileReader(dataFile);
                Map<UUID, PlayerData> loadedMap = inventoryGSON.fromJson(reader, playerDataMapType);
                if (loadedMap != null) {
                    dataMap = loadedMap;
                    plugin.getLogger().info("Playerdata loaded successfully from file.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                plugin.getLogger().severe("Failed to read dueling playerdata.");
            }
        });
    }
}

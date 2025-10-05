package me.robomonkey.versus.kit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class KitManager {

    public KitData kitData;
    public static KitManager instance;

    public KitManager() {
        kitData = new KitData();
        verifyDefaultKit();
    }

    public static KitManager getInstance() {
        if (instance == null) {
            instance = new KitManager();
        }
        return instance;
    }

    /**
     * 전체 키트를 반환합니다.
     * DuelManager에서 키트 선택 GUI를 만들 때 사용
     */
    public List<Kit> getKits() {
        return getAllKits();
    }

    public List<Kit> getAllKits() {
        return kitData.getAllKits();
    }

    public static ItemStack[] getHorseSwordArmor() {
        ItemStack[] armor = new ItemStack[4];

        // 0=헬멧, 1=가슴, 2=바지, 3=신발
        armor[0] = addEnchant(new ItemStack(Material.DIAMOND_HELMET), Enchantment.PROTECTION_ENVIRONMENTAL, 2);
        armor[1] = addEnchant(new ItemStack(Material.DIAMOND_CHESTPLATE), Enchantment.PROTECTION_ENVIRONMENTAL, 2);
        armor[2] = addEnchant(new ItemStack(Material.DIAMOND_LEGGINGS), Enchantment.PROTECTION_ENVIRONMENTAL, 2);
        armor[3] = addEnchant(new ItemStack(Material.DIAMOND_BOOTS), Enchantment.PROTECTION_ENVIRONMENTAL, 2);

        return armor;
    }

    public static ItemStack[] getHorseBowArmor() {
        // HorseSword와 동일하게 보호2 다이아몬드
        return getHorseSwordArmor();
    }

    private static ItemStack addEnchant(ItemStack item, Enchantment ench, int level) {
        item.addEnchantment(ench, level);
        return item;
    }

    public static ItemStack[] getHorseSwordItems() {
        ItemStack[] items = new ItemStack[36]; // 인벤토리 안전 최대치

        // 무기
        items[0] = addEnchant(new ItemStack(Material.DIAMOND_SWORD), Enchantment.DAMAGE_ALL, 2);
        items[1] = addEnchant(new ItemStack(Material.DIAMOND_AXE), Enchantment.DAMAGE_UNDEAD, 1);
        // HAY_BLOCK
        items[2] = new ItemStack(Material.HAY_BLOCK, 64);

        // 🍎 황금사과 7개
        items[3] = new ItemStack(Material.GOLDEN_APPLE, 7);
        // 🍖 구운 돼지고기 32개
        items[4] = new ItemStack(Material.COOKED_PORKCHOP, 32);

        return items;

    }

    public static ItemStack[] getHorseBowItems() {
        ItemStack[] items = new ItemStack[36];

        // 활 (Power III, Infinity I)
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.ARROW_DAMAGE, 3);
        bow.addEnchantment(Enchantment.ARROW_INFINITE, 1);
        items[0] = bow;

        // 화살 1개
        items[9] = new ItemStack(Material.ARROW, 1);
        // HAY_BLOCK
        items[1] = new ItemStack(Material.HAY_BLOCK, 64);

        // 🍎 황금사과 7개
        items[2] = new ItemStack(Material.GOLDEN_APPLE, 7);
        // 🍖 구운 돼지고기 32개
        items[3] = new ItemStack(Material.COOKED_PORKCHOP, 32);

        return items;
    }

    public void add(String name, ItemStack[] items, ItemStack displayItem) {
        Kit kit = new Kit(name, items, displayItem);
        kitData.saveKit(kit);
    }

    public void add(String name, ItemStack[] items) {
        Kit kit = new Kit(name, items);
        kitData.saveKit(kit);
    }

    public boolean contains(String name) {
        return kitData.getKit(name) != null;
    }

    public Kit getKit(String kitName) {
        Kit retrievedKit = kitData.getKit(kitName);
        return retrievedKit == null ? getDefaultKit() : retrievedKit;
    }

    public boolean isKit(String kitName) {
        return kitData.getKit(kitName) != null;
    }

    public void remove(String kitName) {
        kitData.deleteKit(kitName);
    }

    void verifyDefaultKit() {
        if (kitData.getKit("Horse_Sword") == null) {
            add("Horse_Sword", getHorseSwordItems(), new ItemStack(Material.DIAMOND_SWORD));
        }
        if (kitData.getKit("Horse_Bow") == null) {
            add("Horse_Bow", getHorseBowItems(), new ItemStack(Material.BOW));
        }
    }


    // KitManager 또는 me.robomonkey.versus.util.HorseUtil 등에 넣기
    public static org.bukkit.entity.Horse spawnDuelHorse(org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline()) {
            Bukkit.getLogger().warning("[Versus] spawnDuelHorse: player is null/offline.");
            return null;
        }

        try {
            org.bukkit.World world = player.getWorld();
            if (world == null) {
                Bukkit.getLogger().warning("[Versus] spawnDuelHorse: player's world is null.");
                return null;
            }

            // 안전하게 생성
            org.bukkit.entity.Entity ent = world.spawnEntity(player.getLocation(), org.bukkit.entity.EntityType.HORSE);
            if (!(ent instanceof org.bukkit.entity.Horse horse)) {
                Bukkit.getLogger().warning("[Versus] spawnDuelHorse: spawned entity is not Horse, removing.");
                ent.remove();
                return null;
            }

            // 기본 세팅 (기존 요구사항 반영)
            horse.setTamed(true);
            horse.setOwner(player);
            horse.setAdult();
            horse.setRemoveWhenFarAway(false);
            horse.setPersistent(true);

            var speedAttr = horse.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(0.30);

            try { horse.setJumpStrength(1.0); } catch (Throwable ignored) {}

            var maxHpAttr = horse.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            if (maxHpAttr != null) maxHpAttr.setBaseValue(30.0);
            horse.setHealth(Math.min(30.0, horse.getMaxHealth()));

            // 안장 + 보호1 다이아 갑옷
            org.bukkit.inventory.ItemStack armor = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_HORSE_ARMOR);
            var meta = armor.getItemMeta();
            if (meta != null) meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            armor.setItemMeta(meta);
            horse.getInventory().setArmor(armor);
            horse.getInventory().setSaddle(new org.bukkit.inventory.ItemStack(org.bukkit.Material.SADDLE));

            Bukkit.getLogger().info("[Versus] spawnDuelHorse: spawned horse for " + player.getName() + " id=" + horse.getUniqueId());
            return horse;
        } catch (Throwable t) {
            Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "[Versus] spawnDuelHorse failed", t);
            return null;
        }
    }


    public Kit getDefaultKit() {
        return kitData.getKit("Default");
    }

}


package me.robomonkey.versus.duel;

import lonelibs.net.kyori.adventure.text.Component;
import lonelibs.net.kyori.adventure.text.format.NamedTextColor;
import me.robomonkey.versus.Versus;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HorseHealManager implements Listener {

    private final Versus plugin = Versus.getInstance();
    private final DuelManager duelManager = DuelManager.getInstance();

    private final Map<UUID, Long> horseHealCooldown = new HashMap<>();
    private final Map<UUID, Integer> horseHealTaskId = new HashMap<>();

    private static final double HEAL_PER_WHEAT = 9.0;      // 한 개의 밀짚으로 회복할 최대 체력
    private static final double HEAL_STEP = 0.5;            // 1틱마다 회복하는 체력
    private static final long HEAL_PERIOD_TICKS = 5L;       // BukkitRunnable 주기 (틱 단위)

    // -------------------- 💥 공격력 증가 처리 --------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;

        var kit = duelManager.playerKitSelection.get(player.getUniqueId());
        if (kit == null) return;

        String kitName = kit.getName().toUpperCase();

        // 검기마 키트일 경우 데미지 10% 증가
        if (kitName.equals("HORSE_SWORD")) {
            double original = e.getDamage();
            e.setDamage(original * 1.1); // 10% 증가
        }
    }

    // -------------------- 🐴 말 회복 처리 --------------------
    @EventHandler
    public void onWheatUseWhileRiding(PlayerInteractEvent e) {
        Action act = e.getAction();
        if (act != Action.RIGHT_CLICK_AIR && act != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack used = e.getHand() == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();

        if (used == null || used.getType() != Material.HAY_BLOCK) return;
        if (!(p.getVehicle() instanceof AbstractHorse horse)) return;

        // 키트 기반 쿨타임 계산
        int cdSec = getCooldownSeconds(p);

        long nowMs = System.currentTimeMillis();
        long until = horseHealCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (nowMs < until) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        // 인벤토리 아이템 감소
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            if (used.getAmount() <= 1) p.getInventory().setItemInOffHand(null);
            else used.setAmount(used.getAmount() - 1);
        } else {
            if (used.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
            else used.setAmount(used.getAmount() - 1);
        }
        p.updateInventory();

        // ★핫바 쿨다운 표시★
        p.setCooldown(Material.HAY_BLOCK, cdSec * 20); // tick 단위
        horseHealCooldown.put(p.getUniqueId(), nowMs + cdSec * 1000L);

        if (horseHealTaskId.containsKey(p.getUniqueId())) return;

        final double max = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        final double now = horse.getHealth();
        final double targetHeal = Math.min(HEAL_PER_WHEAT, max - now);

        BukkitRunnable task = new BukkitRunnable() {
            double healed = 0.0;

            @EventHandler
            public void onEatGoldenApple(PlayerItemConsumeEvent e) {
                Player p = e.getPlayer();
                ItemStack item = e.getItem();
                if (item == null) return;

                if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 6, 0)); // 6초, 신속 I
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void onInteractEntity(PlayerInteractEntityEvent e) {
                if (!(e.getRightClicked() instanceof AbstractHorse)) return;

                Player p = e.getPlayer();
                ItemStack used = (e.getHand() == EquipmentSlot.OFF_HAND)
                        ? p.getInventory().getItemInOffHand()
                        : p.getInventory().getItemInMainHand();
                if (used == null) return;

                Material t = used.getType();

                // 말에게 황금사과(일반/마법) 금지
                if (t == Material.GOLDEN_APPLE || t == Material.ENCHANTED_GOLDEN_APPLE) {
                    e.setCancelled(true);
                    return;
                }

                // 밀짚은 "말을 타고 있을 때"만
                if (t == Material.HAY_BLOCK && !(p.getVehicle() instanceof AbstractHorse)) {
                    e.setCancelled(true);
                }
            }

            @Override
            public void run() {
                if (!horse.isValid() || p.getVehicle() != horse) {
                    stop();
                    return;
                }

                double step = Math.min(HEAL_STEP, targetHeal - healed);
                if (step <= 0.0) {
                    stop();
                    return;
                }

                double cur = horse.getHealth();
                double next = Math.min(cur + step, max);
                if (next <= cur) {
                    stop();
                    return;
                }

                horse.setHealth(next);
                healed += (next - cur);

                if (healed >= targetHeal - 1e-6) stop();
            }

            private void stop() {
                Integer tid = horseHealTaskId.remove(p.getUniqueId());
                if (tid != null) cancel();
            }
        };

        int id = task.runTaskTimer(plugin, 0L, HEAL_PERIOD_TICKS).getTaskId();
        horseHealTaskId.put(p.getUniqueId(), id);
    }

    private int getCooldownSeconds(Player player) {
        var kit = duelManager.playerKitSelection.get(player.getUniqueId());
        if (kit == null) return 10; // 키트 미선택 또는 일반 상황

        String kitName = kit.getName().toUpperCase();
        return switch (kitName) {
            case "HORSE_SWORD" -> 10;
            case "HORSE_BOW"   -> 20;
            default -> 10;
        };
    }
}

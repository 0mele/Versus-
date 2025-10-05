package me.robomonkey.versus.duel;

import me.robomonkey.versus.duel.Duel;
import me.robomonkey.versus.duel.DuelManager;
import me.robomonkey.versus.kit.Kit;
import me.robomonkey.versus.kit.KitManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class KitSelectionListener implements Listener {

    private final DuelManager duelManager;

    public KitSelectionListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 키트 선택 GUI인지 확인
        if (!event.getView().getTitle().equals("§6키트 선택")) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // 듀얼 중인지 확인
        Duel duel = duelManager.getDuel(player);
        if (duel == null) {
            player.closeInventory();
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String kitName = clickedItem.getItemMeta().getDisplayName().replace("§a", "");

        // 키트 이름으로 키트 찾기
        Kit selectedKit = KitManager.getInstance().getKit(kitName);

        if (selectedKit != null) {
            duelManager.handleKitSelection(player, selectedKit, duel);
        }
    }
}

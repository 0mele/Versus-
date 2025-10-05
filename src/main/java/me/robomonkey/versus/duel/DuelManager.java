package me.robomonkey.versus.duel;

import me.robomonkey.versus.Versus;
import me.robomonkey.versus.arena.Arena;
import me.robomonkey.versus.arena.ArenaManager;
import me.robomonkey.versus.dependency.PAPIUtil;
import me.robomonkey.versus.duel.eventlisteners.*;
import me.robomonkey.versus.duel.playerdata.DataManager;
import me.robomonkey.versus.duel.playerdata.PlayerData;
import me.robomonkey.versus.duel.request.RequestManager;
import me.robomonkey.versus.kit.Kit;
import me.robomonkey.versus.kit.KitManager;
import me.robomonkey.versus.settings.Placeholder;
import me.robomonkey.versus.settings.Setting;
import me.robomonkey.versus.settings.Settings;
import me.robomonkey.versus.util.EffectUtil;
import me.robomonkey.versus.util.MessageUtil;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class DuelManager {
    // DuelManager 필드 (맨 위)
    public HashMap<UUID, Kit> playerKitSelection = new HashMap<>();
    private final java.util.Map<Duel, java.util.Set<java.util.UUID>> duelHorses = new java.util.HashMap<>();
    private static DuelManager instance;
    private HashMap<UUID, Duel> duelistMap = new HashMap<>();
    private HashMap<UUID, Boolean> playerKitReady = new HashMap<>();
    private ArenaManager arenaManager = ArenaManager.getInstance();
    private DataManager dataManager;
    private final Map<UUID, Duel> activeDuels = new HashMap<>();
    private Versus plugin = Versus.getInstance();

    private DuelManager() {
        instance = this;
        dataManager = new DataManager(plugin);
        dataManager.loadDataMap();
        registerListeners();
    }

    public static DuelManager getInstance() {
        if (instance == null) {
            new DuelManager();
        }
        return instance;
    }

    public void addDuel(Duel duel) {
        duel.getPlayers().forEach(player -> duelistMap.put(player.getUniqueId(), duel));
    }

    public void unregisterFromDuel(Player player) {
        duelistMap.remove(player.getUniqueId());
        playerKitSelection.remove(player.getUniqueId());
        playerKitReady.remove(player.getUniqueId());
    }

    public Map<UUID, Duel> getActiveDuels() {
        return activeDuels;
    }

    private void removeDuel(Duel duel) {
        duelistMap.values().removeIf(value -> value.equals(duel));
        // also clear any stored horse references just in case
        duelHorses.remove(duel);
    }

    public Duel getDuel(Player player) {
        return duelistMap.get(player.getUniqueId());
    }

    public void registerQuitter(Player quitter) {
        quitter.setHealth(0);
    }

    public boolean hasStoredData(Player player) {
        return dataManager.contains(player);
    }

    public boolean isDueling(Player player) {
        return duelistMap.containsKey(player.getUniqueId());
    }

    public boolean isMoving(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to.getX() != from.getX() || to.getY() != from.getY() || to.getZ() != from.getZ()) return true;
        return false;
    }

    public void restoreData(Player player, boolean isWinner) {
        if (!player.isOnline()) return;
        Versus.log("Attempting to restore data");
        if (!dataManager.contains(player)) return;
        PlayerData data = dataManager.extractData(player);
        player.setLevel(data.xpLevel);
        player.setExp(data.xpProgress);
        player.getInventory().setContents(data.items);
        restoreLocation(player, data, isWinner);
    }

    private void restoreLocation(Player player, PlayerData data, Boolean isWinner) {
        ReturnOption returnOption = isWinner ? Settings.getReturnOption(Setting.RETURN_WINNERS) : Settings.getReturnOption(Setting.RETURN_LOSERS);
        switch (returnOption) {
            case SPAWN:
                player.teleport(player.getWorld().getSpawnLocation());
                break;
            case PREVIOUS:
                player.teleport(data.previousLocation.toLocation());
                break;
            case SPECTATE:
                Arena respawnArena = arenaManager.getArena(data.arenaName);
                if (respawnArena == null) player.teleport(player.getWorld().getSpawnLocation());
                else player.teleport(respawnArena.getSpectateLocation());
                break;
            case CUSTOM:
                Location customLocation = isWinner ? Settings.getLocation(Setting.WINNER_RETURN_LOCATION): Settings.getLocation(Setting.LOSER_RETURN_LOCATION);
                if(customLocation == null) {
                    Versus.log("Custom respawn location is improperly formatted. "+player.getName()+" will return to their previous location, instead.");
                    player.teleport(data.previousLocation.toLocation());
                    return;
                }
                player.teleport(customLocation);
        }
    }

    public void setupDuel(Player playerOne, Player playerTwo) {
        Duel newDuel = createNewDuel(playerOne, playerTwo);
        newDuel.getPlayers().stream().forEach((player) -> dataManager.save(player, newDuel.getArena()));
        playerOne.teleport(newDuel.getArena().getSpawnLocationOne());
        playerTwo.teleport(newDuel.getArena().getSpawnLocationTwo());
        newDuel.getPlayers().stream().forEach((player) -> {
            dataManager.update(player, DataManager.DataType.INVENTORY);
            dataManager.saveDataMap();
            groomForDuel(player);
        });

        // 키트 선택 단계 시작
        playerKitReady.put(playerOne.getUniqueId(), false);
        playerKitReady.put(playerTwo.getUniqueId(), false);
        openKitSelectionGUI(playerOne, newDuel);
        openKitSelectionGUI(playerTwo, newDuel);

        playerOne.sendMessage("§e키트를 선택해주세요!");
        playerTwo.sendMessage("§e키트를 선택해주세요!");
    }

    private void openKitSelectionGUI(Player player, Duel duel) {
        // 인벤토리 생성
        Inventory gui = Bukkit.createInventory(null, 27, "§6키트 선택");

        // 사용 가능한 모든 키트 가져오기
        List<Kit> availableKits = KitManager.getInstance().getKits();

        int slot = 0;
        for (Kit kit : availableKits) {
            if (slot >= 27) break; // GUI 슬롯 초과 방지

            // GUI 아이템 생성
            ItemStack kitItem = kit.getDisplayItem() != null ? kit.getDisplayItem().clone() : new ItemStack(Material.CHEST);
            ItemMeta meta = kitItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a" + kit.getName());

                List<String> lore = new ArrayList<>();
                lore.add("§7클릭하여 선택");
                meta.setLore(lore);

                kitItem.setItemMeta(meta);
            }

            gui.setItem(slot, kitItem);
            slot++;
        }

        player.openInventory(gui);
    }


    public void handleKitSelection(Player player, Kit selectedKit, Duel duel) {
        playerKitSelection.put(player.getUniqueId(), selectedKit);
        playerKitReady.put(player.getUniqueId(), true);
        player.closeInventory();
        player.sendMessage("§a키트 '" + selectedKit.getName() + "'을(를) 선택했습니다!");

        // 두 플레이어 모두 선택했는지 확인
        checkBothPlayersReady(duel);
    }

    private void checkBothPlayersReady(Duel duel) {
        boolean allReady = duel.getPlayers().stream()
                .allMatch(player -> playerKitReady.getOrDefault(player.getUniqueId(), false));

        if (allReady) {
            // 선택된 키트로 듀얼 시작
            populateKits(duel);
            duel.startCountdown(() -> commenceDuel(duel));
            if (duel.isPublic()) announceDuelStart(duel);
        }
    }

    private void populateKits(Duel duel) {
        duel.getPlayers().forEach((player) -> {
            Kit selectedKit = playerKitSelection.get(player.getUniqueId());
            // 선택한 키트가 있으면 그것을, 없으면 아레나 기본 키트 사용
            Kit kit = selectedKit != null ? selectedKit : duel.getArena().getKit();
            player.getInventory().setContents(kit.getItems());
            String kitName = kit.getName();
            if ("Horse_Sword".equalsIgnoreCase(kitName) || "Horse_Bow".equalsIgnoreCase(kitName)) {
                // main thread 보장: 다음 틱에 안전하게 실행
                Bukkit.getScheduler().runTask(plugin, () -> {
                    org.bukkit.entity.Horse horse = KitManager.spawnDuelHorse(player);
                    if (horse != null) {
                        duelHorses.computeIfAbsent(duel, d -> new java.util.HashSet<>()).add(horse.getUniqueId());
                        try {
                            horse.addPassenger(player); // 플레이어를 태움
                        } catch (Throwable t) {
                            Bukkit.getLogger().warning("[Versus] failed to mount player on horse: " + t.getMessage());
                            // 안전하게 말 위치로 텔레포트 시도
                            player.teleport(horse.getLocation());
                        }
                    } else {
                        player.sendMessage("§c말 소환에 실패했습니다. 콘솔 로그를 확인하세요.");
                    }
                });
            }
        });
    }

    public void announceDuelStart(Duel duel) {
        String announcementMessage = Settings.getMessage(Setting.DUEL_START_ANNOUNCEMENT,
                Placeholder.of("%player_one%", PAPIUtil.getName(duel.getPlayers().get(0))),
                Placeholder.of("%player_two%", PAPIUtil.getName(duel.getPlayers().get(1))));
        String commandText = "/spectate " + duel.getPlayers().get(0).getName();
        TextComponent announcement;
        try {
            announcement = MessageUtil.getClickableMessageBetween(announcementMessage, commandText, commandText, "%button%");
        } catch (Exception e) {
            Versus.log("Config.yml option 'duel_start_announcement' is improperly configured. Please review. Using default value...");
            announcementMessage = Settings.getDefaultMessage(Setting.DUEL_START_ANNOUNCEMENT,
                    Placeholder.of("%player_one%", PAPIUtil.getName(duel.getPlayers().get(0))),
                    Placeholder.of("%player_two%", PAPIUtil.getName(duel.getPlayers().get(1))));
            announcement = MessageUtil.getClickableMessageBetween(announcementMessage, commandText, commandText, "%button%");
        }
        Bukkit.spigot().broadcast(announcement);
    }

    public void announceDuelEnd(Duel duel) {
        if (duel.getWinner() == null || duel.getLoser() == null) return;
        String announcementMessage = Settings.getMessage(Setting.DUEL_END_ANNOUNCEMENT,
                Placeholder.of("%winner%", PAPIUtil.getName(duel.getWinner())),
                Placeholder.of("%loser%", PAPIUtil.getName(duel.getLoser())));
        Bukkit.broadcastMessage(announcementMessage);
    }


    public void registerMoveEvent(Player player, PlayerMoveEvent event) {
        Duel currentDuel = getDuel(player);
        if (currentDuel.getState() == DuelState.COUNTDOWN && isMoving(event)) {
            event.setCancelled(true);
        }
    }

    /**
     * Only call after checking that ensuring that the player is currently in a duel with duelManager.duelFromPlayer(..);
     */
    public void registerDuelistDeath(Player loser, boolean fakeDeath) {
        Duel currentDuel = getDuel(loser);
        if (currentDuel.getState() == DuelState.COUNTDOWN) {
            undoCountdown(currentDuel);
        }
        if (fakeDeath) {
            restoreData(loser, false);
            resetAttributes(loser);
        }
        if (currentDuel.isActive()) {
            registerDuelCompletion(loser, currentDuel);
        }
    }

    private void registerDuelCompletion(Player loser, Duel duel) {
        Optional<Player> optionalWinner = duel.getPlayers().stream().filter(player -> !player.equals(loser)).findFirst();
        if (!optionalWinner.isPresent()) return;
        Player winner = optionalWinner.get();
        duel.end(winner, loser);
        stopDuel(duel);
    }

    private void stopDuel(Duel duel) {
        if (duel.isActive()) return;
        arenaManager.removeDuel(duel);
        duel.getPlayers().stream().filter(Player::isOnline).forEach(Player::stopAllSounds);
        Player loser = duel.getLoser();
        Player winner = duel.getWinner();
        unregisterFromDuel(loser);
        if (duel.isPublic()) announceDuelEnd(duel);

        // 3초 후 실행되는 작업 스케줄링
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // world 월드 가져오기
            World worldSpawn = Bukkit.getWorld("world");
            if (worldSpawn == null) {
                Versus.log("'world' 월드를 찾을 수 없습니다!");
                return;
            }
            Location spawnLocation = worldSpawn.getSpawnLocation();

            // 승자 처리
            if (winner != null && winner.isOnline()) {
                winner.setHealth(0); // 승자 죽이기
                resetAttributes(winner);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (winner.isOnline()) {
                        winner.spigot().respawn();
                        winner.teleport(spawnLocation);
                    }
                }, 1L); // 리스폰 후 텔레포트
            }

            // 패자 처리
            if (loser != null && loser.isOnline()) {
                resetAttributes(loser);
                loser.teleport(spawnLocation);
            }

            removeDuel(duel);
            RequestManager.getInstance().notifyDuelCompletion();
        }, 60L); // 3초 = 60틱

        // 기존 승리/패배 이펙트는 즉시 실행
        if (winner != null) {
            renderWinEffects(winner, duel);
        }
        if (loser != null) {
            renderLossEffects(loser);
        }

        // 듀얼에 소환된 말들 정리
        var horses = duelHorses.remove(duel);
        if (horses != null && !horses.isEmpty()) {
            Runnable remover = () -> {
                for (java.util.UUID hid : horses) {
                    boolean found = false;
                    // 모든 월드를 훑어서 UUID가 일치하는 엔티티 찾기
                    for (org.bukkit.World w : Bukkit.getWorlds()) {
                        for (org.bukkit.entity.Entity e : w.getEntities()) {
                            if (!e.getUniqueId().equals(hid)) continue;
                            found = true;
                            if (e instanceof org.bukkit.entity.Horse h && !h.isDead()) {
                                // 타고 있는 플레이어 강제 하차
                                for (org.bukkit.entity.Entity passenger : new java.util.ArrayList<>(h.getPassengers())) {
                                    if (passenger instanceof org.bukkit.entity.Player pl) pl.leaveVehicle();
                                }
                                h.remove();
                                Bukkit.getLogger().info("[Versus] Removed horse " + hid + " in world " + w.getName());
                            } else {
                                // 이미 죽었거나 Horse가 아닌 경우라면 그냥 제거 시도
                                e.remove();
                                Bukkit.getLogger().info("[Versus] Removed entity (not-a-live-horse) " + hid + " in world " + w.getName());
                            }
                            break; // 이 UUID에 해당하는 엔티티를 찾았으므로 해당 월드 루프 탈출
                        }
                        if (found) break;
                    }
                    if (!found) {
                        Bukkit.getLogger().warning("[Versus] Could not find horse entity for id " + hid);
                    }
                }
            };

            // 만약 현재 스레드가 메인 스레드가 아니면 main thread로 위임
            if (!Bukkit.isPrimaryThread()) {
                Bukkit.getScheduler().runTask(plugin, remover);
            } else {
                remover.run();
            }
        }


    }

    private Duel createNewDuel(Player playerOne, Player playerTwo) {
        Arena availableArena = arenaManager.getAvailableArena();
        Duel newDuel = new Duel(availableArena, playerOne, playerTwo);
        addDuel(newDuel);
        arenaManager.registerDuel(availableArena, newDuel);
        return newDuel;
    }

    private void commenceDuel(Duel duel) {
        duel.setState(DuelState.ACTIVE);
        handleStartEffects(duel);
    }

    private void handleStartEffects(Duel duel) {
        if (duel.isFireworksEnabled())
            EffectUtil.spawnFireWorks(duel.getArena().getCenterLocation(), 1, 10, duel.getFireworkColor());
        for (Player player : duel.getPlayers()) {
            EffectUtil.sendTitle(player, Settings.getMessage(Setting.DUEL_GO_MESSAGE), 20, true);
            player.setInvulnerable(false);
        }
    }

    private void groomForDuel(Player player) {
        resetAttributes(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 10));
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.setAllowFlight(false);
        player.setLevel(0);
        player.setExp(0);
    }

    private void registerListeners() {
        List.of(new CommandListener(),
                        new JoinEventListener(),
                        new RespawnEventListener(),
                        new InteractEventListener(),
                        new DeathEventListener(),
                        new MoveEventListener(),
                        new QuitEventListener(),
                        new FireworkExplosionListener(),
                        new BlockBreakListener(),
                        new BlockPlaceListener(),
                        new DamageEventListener(),
                        new KitSelectionListener(this))
                .forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, Versus.getInstance()));
    }

    private void undoCountdown(Duel duel) {
        duel.cancelCountdown();
        duel.getPlayers().forEach(EffectUtil::unfreezePlayer);
    }

    private void extricateWinner(Player player, Duel duel) {
        restoreData(player, true);
        resetAttributes(player);
        removeDuel(duel);
        RequestManager.getInstance().notifyDuelCompletion();
    }

    private void resetAttributes(Player player) {
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setInvulnerable(false);
        EffectUtil.unfreezePlayer(player);
        player.getActivePotionEffects().stream().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setItemOnCursor(null);
        player.closeInventory();
        player.stopAllSounds();
    }

    private void renderWinEffects(Player winner, Duel duel) {
        if (duel.isFireworksEnabled()) EffectUtil.spawnFireWorks(winner.getLocation(), 1, 50, duel.getFireworkColor());
        if (duel.isVictoryMusicEnabled()) EffectUtil.playSound(winner, duel.getVictorySong());
        winner.sendTitle(
                Settings.getMessage(Setting.VICTORY_TITLE_MESSAGE),
                Settings.getMessage(Setting.VICTORY_SUBTITLE_MESSAGE, Placeholder.of("%player%", PAPIUtil.getName(winner))), 20, 40, 20);
        if (duel.isVictoryEffectsEnabled()) {
            if (duel.isFireworksEnabled())
                EffectUtil.spawnFireWorksDelayed(winner.getLocation(), 3, 20, 20L, duel.getFireworkColor());
        }
    }

    private void renderLossEffects(Player loser) {
        loser.sendMessage(Settings.getMessage(Setting.DUEL_LOSS_MESSAGE, Placeholder.of("%player%", PAPIUtil.getName(loser))));
        loser.getWorld().strikeLightningEffect(loser.getLocation());
    }
}
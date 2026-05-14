package cn.superiormc.mythicrewards.listeners;

import cn.superiormc.mythicrewards.MythicRewards;
import cn.superiormc.mythicrewards.managers.ConfigManager;
import cn.superiormc.mythicrewards.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 存储一次战斗实体的伤害统计结果
 * 包括排行榜、总伤害、百分比等数据（构造时计算一次，后续查询直接缓存）
 */
public class TrackerResult {

    private final DamageTracker damageTracker;

    private final LivingEntity entity;

    private final double totalDamage;

    private String entityName;

    private final List<SinglePlayerResult> results;

    private final Map<Player, SinglePlayerResult> playerResultCache = new HashMap<>();

    private final Map<UUID, Integer> ritualPointsByPlayer;

    private final Map<UUID, Integer> ritualExtraRollsByPlayer;

    public TrackerResult(DamageTracker damageTracker, LivingEntity entity) {
        this(damageTracker, entity, new HashMap<>());
    }

    public TrackerResult(DamageTracker damageTracker, LivingEntity entity, Map<UUID, Integer> ritualPointsMap) {
        this.damageTracker = damageTracker;
        this.entity = entity;

        Map<UUID, Double> damageMap = damageTracker.getDamageMap(entity);
        this.totalDamage = damageMap.values().stream().mapToDouble(Double::doubleValue).sum();
        this.entityName = damageTracker.getName(entity);

        List<Map.Entry<UUID, Double>> sorted = damageMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .toList();

        List<SinglePlayerResult> list = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            int rank = i + 1;
            UUID uuid = sorted.get(i).getKey();
            double dmg = sorted.get(i).getValue();
            double percent = totalDamage > 0 ? (dmg / totalDamage * 100.0) : 0.0;

            Player player = Bukkit.getPlayer(uuid);
            String name = player != null
                    ? player.getName()
                    : ConfigManager.configManager.getString("placeholders.result.unknown-player");

            SinglePlayerResult spr = new SinglePlayerResult(rank, uuid, name, dmg, percent);
            list.add(spr);

            if (player != null) {
                playerResultCache.put(player, spr);
            }
        }

        this.results = Collections.unmodifiableList(list);
        this.ritualPointsByPlayer = Collections.unmodifiableMap(new HashMap<>(ritualPointsMap));
        this.ritualExtraRollsByPlayer = Collections.unmodifiableMap(buildRitualExtraRollsMap());
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public String getEntityName() {
        return entityName;
    }

    public List<SinglePlayerResult> getResults() {
        return results;
    }

    public int getResultSize() {
        return results.size();
    }

    public SinglePlayerResult getPlayerByRank(int rank) {
        if (rank <= 0 || rank > results.size()) {
            return null;
        }
        return results.get(rank - 1);
    }

    public String getPlayerNameByRank(int rank) {
        SinglePlayerResult r = getPlayerByRank(rank);
        return r != null ? r.playerName() : ConfigManager.configManager.getString("placeholders.result.unknown-player");
    }

    public double getDamageByRank(int rank) {
        SinglePlayerResult r = getPlayerByRank(rank);
        return r != null ? r.damage() : 0.0;
    }

    public double getPercentageByRank(int rank) {
        SinglePlayerResult r = getPlayerByRank(rank);
        return r != null ? r.percentage() : 0.0;
    }

    public int getPlayerRank(Player player) {
        SinglePlayerResult r = playerResultCache.get(player);
        return r != null ? r.rank() : -1;
    }

    public double getPlayerDamage(Player player) {
        SinglePlayerResult r = playerResultCache.get(player);
        return r != null ? r.damage() : 0.0;
    }

    public double getPlayerPercentage(Player player) {
        SinglePlayerResult r = playerResultCache.get(player);
        return r != null ? r.percentage() : 0.0;
    }

    public List<Player> getAllPlayers() {
        List<Player> players = new ArrayList<>();
        for (SinglePlayerResult r : results) {
            Player player = Bukkit.getPlayer(r.playerUUID());
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public int getPlayerRitualPoints(Player player) {
        if (player == null) {
            return 0;
        }
        return ritualPointsByPlayer.getOrDefault(player.getUniqueId(), 0);
    }

    public int getPlayerRitualExtraRolls(Player player) {
        if (player == null) {
            return 0;
        }
        return ritualExtraRollsByPlayer.getOrDefault(player.getUniqueId(), 0);
    }

    public int getPlayerRitualPointsByRank(int rank) {
        SinglePlayerResult r = getPlayerByRank(rank);
        if (r == null) {
            return 0;
        }
        return ritualPointsByPlayer.getOrDefault(r.playerUUID(), 0);
    }

    public int getPlayerRitualExtraRollsByRank(int rank) {
        SinglePlayerResult r = getPlayerByRank(rank);
        if (r == null) {
            return 0;
        }
        return ritualExtraRollsByPlayer.getOrDefault(r.playerUUID(), 0);
    }

    private Map<UUID, Integer> buildRitualExtraRollsMap() {
        Map<UUID, Integer> extraRolls = new HashMap<>();
        int pointsPerRoll = Math.max(1, ConfigManager.configManager.getInt("ritual-bonus.points-per-roll", 1));
        double minDamage = ConfigManager.configManager.config.getDouble("ritual-bonus.min-damage", 1.0D);
        boolean allowPresence = ConfigManager.configManager.getBoolean("ritual-bonus.allow-presence-fallback", true);
        double presenceRadius = ConfigManager.configManager.config.getDouble("ritual-bonus.presence-radius", 32.0D);
        double presenceRadiusSquared = presenceRadius * presenceRadius;
        Location entityLocation = entity.getLocation();

        for (SinglePlayerResult playerResult : results) {
            int points = ritualPointsByPlayer.getOrDefault(playerResult.playerUUID(), 0);
            if (points <= 0) {
                continue;
            }
            boolean eligible = playerResult.damage() >= minDamage;
            if (!eligible && allowPresence) {
                Player online = Bukkit.getPlayer(playerResult.playerUUID());
                if (online != null && online.getWorld().equals(entityLocation.getWorld())
                        && online.getLocation().distanceSquared(entityLocation) <= presenceRadiusSquared) {
                    eligible = true;
                }
            }
            if (!eligible) {
                continue;
            }
            extraRolls.put(playerResult.playerUUID(), Math.max(0, points / pointsPerRoll));
        }
        return extraRolls;
    }

    public String parseResultPlaceholders(String content) {
        if (content == null) {
            return "";
        }
        for (SinglePlayerResult r : results) {
            int rank = r.rank();
            content = content.replace("{player_" + rank + "}", r.playerName());
            content = content.replace("{damage_" + rank + "}", String.format(ConfigManager.configManager.getString("placeholders.result.damage-format"), r.damage()));
            content = content.replace("{percentage_" + rank + "}", String.format(ConfigManager.configManager.getString("placeholders.result.percentage-format"), r.percentage()));
            content = content.replace("{ritual-points_" + rank + "}", String.valueOf(getPlayerRitualPointsByRank(rank)));
            content = content.replace("{ritual-rolls_" + rank + "}", String.valueOf(getPlayerRitualExtraRollsByRank(rank)));
        }
        if (entityName != null) {
            content = content.replace("{entity-name}", entityName);
        }
        return content;
    }
}

record SinglePlayerResult(
        int rank,
        UUID playerUUID,
        String playerName,
        double damage,
        double percentage
) { }

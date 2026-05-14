package cn.superiormc.mythicrewards.objects.rule;
import cn.superiormc.mythicrewards.MythicRewards;
import cn.superiormc.mythicrewards.listeners.DamageTracker;
import cn.superiormc.mythicrewards.listeners.TrackerResult;
import cn.superiormc.mythicrewards.managers.MatchEntityManager;
import cn.superiormc.mythicrewards.objects.ObjectAction;
import cn.superiormc.mythicrewards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class ObjectSingleRule implements Comparable<ObjectSingleRule> {

    private final String id;

    private final YamlConfiguration config;

    private final ObjectAction generalActions;

    private final ObjectAction allActions;

    private Collection<ObjectAction> packActions;

    private final ObjectAction ritualBonusActions;

    private final DamageTracker damageTracker;

    private final long timeOutTicks;

    private final boolean preventVanillaDrops;

    private final int dropExp;

    public ObjectSingleRule(String id, YamlConfiguration config) {
        this.id = id;
        this.config = config;
        this.generalActions = new ObjectAction(config.getConfigurationSection("general-actions"));
        this.allActions = new ObjectAction(config.getConfigurationSection("all-actions"));
        this.ritualBonusActions = new ObjectAction(config.getConfigurationSection("ritual-bonus-actions"));
        this.damageTracker = new DamageTracker(this);
        this.timeOutTicks = config.getLong("time-out-ticks", 6000);
        this.preventVanillaDrops = config.getBoolean("prevent-vanilla-drops");
        this.dropExp = config.getInt("drop-exp", -1);
        initPackActions();
        //this.condition = new ObjectCondition(config.getConfigurationSection("conditions"));
    }

    private void initPackActions() {
        packActions = new ArrayList<>();
        ConfigurationSection packActionsSection = config.getConfigurationSection("pack-actions");
        if (packActionsSection == null) {
            return;
        }
        for (String key : packActionsSection.getKeys(false)) {
            if (key.equals("amount")) {
                continue;
            }
            if (packActionsSection.isConfigurationSection(key)) {
                ConfigurationSection actionSection = packActionsSection.getConfigurationSection(key);
                if (actionSection != null && !actionSection.contains("type")) {
                    ObjectAction action = new ObjectAction(actionSection);
                    packActions.add(action);
                }
            }
        }
        if (packActions.isEmpty()) {
            ObjectAction tempVal1 = new ObjectAction(config.getConfigurationSection("pack-actions"));
            packActions.add(tempVal1);
        }
        TextUtil.sendMessage(null, TextUtil.pluginPrefix() + " §fPack actions enabled for rule: " + id + ", total action amount: " + packActions.size() + ".");

    }

    public boolean getMatchEntity(LivingEntity entity) {
        ConfigurationSection section = config.getConfigurationSection("match-entity");
        return MatchEntityManager.matchEntityManager.getMatch(section, entity);
    }

    public void addDamage(LivingEntity entity, Player player, double damage) {
        damageTracker.addDamage(entity, player, damage);
    }

    public void startGiveAction(LivingEntity entity) {
        startGiveAction(entity, Map.of());
    }

    public void startGiveAction(LivingEntity entity, Map<UUID, Integer> ritualPointsMap) {
        TrackerResult trackerResult = new TrackerResult(damageTracker, entity, ritualPointsMap);
        List<Player> allPlayers = trackerResult.getAllPlayers();

        for (Player tempVal1 : allPlayers) {
            generalActions.runAllActions(tempVal1, trackerResult);
        }

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        for (Player tempVal2 : players) {
            allActions.runAllActions(tempVal2, trackerResult);
        }

        for (ObjectAction action : packActions) {
            int amount = action.getAmount();
            if (amount <= 0 || allPlayers.isEmpty()) {
                continue;
            }

            List<Player> availablePlayers = new ArrayList<>(allPlayers);

            for (int i = 0; i < amount && !availablePlayers.isEmpty(); i++) {
                Player selected = weightedRandomPick(availablePlayers, trackerResult);
                if (selected != null) {
                    action.runAllActions(selected, trackerResult);

                    availablePlayers.remove(selected);
                }
            }
        }

        if (!ritualBonusActions.isEmpty()) {
            for (Player player : allPlayers) {
                int extraRolls = trackerResult.getPlayerRitualExtraRolls(player);
                for (int i = 0; i < extraRolls; i++) {
                    ritualBonusActions.runAllActions(player, trackerResult);
                }
            }
        }
        sendSummaryMessage(entity, trackerResult, allPlayers);
        clearDamage(entity);
    }

    private void sendSummaryMessage(LivingEntity entity, TrackerResult trackerResult, List<Player> participants) {
        ConfigurationSection messageSection = config.getConfigurationSection("message-settings");
        if (messageSection == null) {
            return;
        }

        List<String> broadcastLines = messageSection.getStringList("broadcast");
        if (broadcastLines.isEmpty()) {
            return;
        }

        String audience = messageSection.getString("audience", "participants");
        Set<Player> recipients = new HashSet<>();
        if ("world".equalsIgnoreCase(audience)) {
            recipients.addAll(entity.getWorld().getPlayers());
        } else {
            recipients.addAll(participants);
        }
        if (recipients.isEmpty()) {
            return;
        }

        List<String> topPlayersLines = buildTopPlayersLines(messageSection, trackerResult);
        String personalDamageFormat = messageSection.getString("personal-damage", "<gray>Tu daño: </gray><green>{damage} ({percentage}%)</green>");
        String personalNoDamage = messageSection.getString("personal-no-damage", "<gray>No participaste en la batalla.</gray>");

        for (Player receiver : recipients) {
            String personalLine = buildPersonalLine(receiver, trackerResult, personalDamageFormat, personalNoDamage);
            for (String line : broadcastLines) {
                if (line.contains("{top_players}")) {
                    String prefix = line.replace("{top_players}", "").trim();
                    if (!prefix.isEmpty()) {
                        String parsedPrefix = prefix
                                .replace("{personal_damage}", personalLine)
                                .replace("{mob_display_name}", trackerResult.getEntityName() == null ? entity.getName() : trackerResult.getEntityName());
                        TextUtil.sendMessage(receiver, TextUtil.parse(TextUtil.withPAPI(parsedPrefix, receiver)));
                    }
                    for (String topLine : topPlayersLines) {
                        TextUtil.sendMessage(receiver, TextUtil.parse(TextUtil.withPAPI(topLine, receiver)));
                    }
                    continue;
                }

                String parsed = line
                        .replace("{personal_damage}", personalLine)
                        .replace("{mob_display_name}", trackerResult.getEntityName() == null ? entity.getName() : trackerResult.getEntityName());
                TextUtil.sendMessage(receiver, TextUtil.parse(TextUtil.withPAPI(parsed, receiver)));
            }
        }
    }

    private List<String> buildTopPlayersLines(ConfigurationSection messageSection, TrackerResult trackerResult) {
        ConfigurationSection topFormatsSection = messageSection.getConfigurationSection("top-formats");
        String first = "<yellow>1°</yellow><gray> ⏵</gray> {prefix}{player_name} <gray>⏵ </gray><yellow>{damage}</yellow>";
        String second = "<gold>2°</gold><gray> ⏵</gray> {prefix}{player_name} <gray>⏵ </gray><gold>{damage}</gold>";
        String third = "<red>3°</red><gray> ⏵</gray> {prefix}{player_name} <gray>⏵ </gray><red>{damage}</red>";
        String others = "<gray>{position}°</gray><gray> ⏵</gray> {prefix}{player_name} <gray>⏵ </gray><gray>{damage}</gray>";
        if (topFormatsSection != null) {
            first = topFormatsSection.getString("1", first);
            second = topFormatsSection.getString("2", second);
            third = topFormatsSection.getString("3", third);
            others = topFormatsSection.getString("others", others);
        }

        List<String> lines = new ArrayList<>();
        int topLimit = messageSection.getInt("top-limit", 3);
        int maxRank = Math.min(trackerResult.getResultSize(), Math.max(1, topLimit));
        for (int rank = 1; rank <= maxRank; rank++) {
            String format = switch (rank) {
                case 1 -> first;
                case 2 -> second;
                case 3 -> third;
                default -> others;
            };
            Player listedPlayer = Bukkit.getPlayerExact(trackerResult.getPlayerNameByRank(rank));
            String listedPrefix = listedPlayer != null ? TextUtil.withPAPI("%vault_prefix%", listedPlayer) : "";
            if ("%vault_prefix%".equalsIgnoreCase(listedPrefix)) {
                listedPrefix = "";
            }
            String line = format
                    .replace("{position}", String.valueOf(rank))
                    .replace("{player_name}", trackerResult.getPlayerNameByRank(rank))
                    .replace("{damage}", String.format("%.0f", trackerResult.getDamageByRank(rank)))
                    .replace("{percentage}", String.format("%.1f", trackerResult.getPercentageByRank(rank)))
                    .replace("{prefix}", listedPrefix);
            lines.add(line);
        }
        return lines;
    }

    private String buildPersonalLine(Player receiver, TrackerResult trackerResult, String personalDamageFormat, String personalNoDamage) {
        int rank = trackerResult.getPlayerRank(receiver);
        if (rank == -1) {
            return personalNoDamage;
        }
        return personalDamageFormat
                .replace("{position}", String.valueOf(rank))
                .replace("{damage}", String.format("%.0f", trackerResult.getPlayerDamage(receiver)))
                .replace("{percentage}", String.format("%.1f", trackerResult.getPlayerPercentage(receiver)));
    }

    private Player weightedRandomPick(List<Player> players, TrackerResult trackerResult) {
        double totalWeight = 0.0;
        for (Player p : players) {
            totalWeight += trackerResult.getPlayerPercentage(p);
        }
        if (totalWeight <= 0) return null;

        double rand = new Random().nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (Player p : players) {
            cumulative += trackerResult.getPlayerPercentage(p);
            if (rand <= cumulative) {
                return p;
            }
        }

        // 万一没有选中，返回最后一个
        return players.get(players.size() - 1);
    }

    public void clearDamage(LivingEntity entity) {
        damageTracker.clearDamage(entity);
    }

    public String getId() {
        return id;
    }

    public int getWeight() {
        return config.getInt("weight", 0);
    }

    public long getTimeOutTicks() {
        return timeOutTicks;
    }

    public boolean isPreventVanillaDrops() {
        return preventVanillaDrops;
    }

    public int getDropExp() {
        return dropExp;
    }

    @Override
    public int compareTo(@NotNull ObjectSingleRule otherPrefix) {
        if (getWeight() == otherPrefix.getWeight()) {
            int len1 = getId().length();
            int len2 = otherPrefix.getId().length();
            int minLength = Math.min(len1, len2);

            for (int i = 0; i < minLength; i++) {
                char c1 = getId().charAt(i);
                char c2 = otherPrefix.getId().charAt(i);

                if (c1 != c2) {
                    if (Character.isDigit(c1) && Character.isDigit(c2)) {
                        // 如果字符都是数字，则按照数字大小进行比较
                        return Integer.compare(Integer.parseInt(getId().substring(i)), Integer.parseInt(otherPrefix.getId().substring(i)));
                    } else {
                        // 否则，按照字符的unicode值进行比较
                        return c1 - c2;
                    }
                }
            }

            return len1 - len2;
        }
        return getWeight() - otherPrefix.getWeight();
    }

    @Override
    public String toString() {
        return getId();
    }
}

package cn.superiormc.mythicrewards.listeners;

import cn.superiormc.mythicrewards.utils.CommonUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RitualContributionUtil {

    private static final String CONTRIBUTORS_KEY = "vtalters:ritual_contributors";

    private RitualContributionUtil() {
    }

    public static Map<UUID, Integer> readRitualPoints(LivingEntity boss) {
        Map<UUID, Integer> result = new HashMap<>();
        if (boss == null) {
            return result;
        }
        PersistentDataContainer pdc = boss.getPersistentDataContainer();
        NamespacedKey contributorsKey = CommonUtil.parseNamespacedKey(CONTRIBUTORS_KEY);
        if (contributorsKey == null) {
            return result;
        }
        String encoded = pdc.get(contributorsKey, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return result;
        }

        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(parts[0]);
                int points = Integer.parseInt(parts[1]);
                if (points > 0) {
                    result.put(uuid, points);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }
}

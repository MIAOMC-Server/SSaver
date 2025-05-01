package com.miaomc.statisticsSaver.Listener;

import com.alibaba.fastjson.JSONObject;
import com.miaomc.statisticsSaver.StatisticsSaver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;

public class savePlayerData implements Listener {

    private final StatisticsSaver plugin;

    // 缓存常用的统计类型，提高性能
    private static final Statistic[] UNTYPED_STATISTICS;
    private static final Statistic[] BLOCK_STATISTICS = {
            Statistic.MINE_BLOCK,
            Statistic.USE_ITEM
    };
    private static final Statistic[] ITEM_STATISTICS = {
            Statistic.USE_ITEM,
            Statistic.BREAK_ITEM,
            Statistic.CRAFT_ITEM
    };
    private static final Statistic[] ENTITY_STATISTICS = {
            Statistic.KILL_ENTITY,
            Statistic.ENTITY_KILLED_BY
    };

    static {
        // 预先筛选所有无类型的统计
        UNTYPED_STATISTICS = getUntypedStatistics();
    }

    private static Statistic[] getUntypedStatistics() {
        return java.util.Arrays.stream(Statistic.values())
                .filter(stat -> stat.getType() == Statistic.Type.UNTYPED)
                .toArray(Statistic[]::new);
    }

    public savePlayerData(StatisticsSaver plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("玩家数据保存监听器已注册");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        savePlayerStatistics(player);
    }

    /**
     * 保存玩家的所有统计数据
     *
     * @param player 要保存数据的玩家
     */
    private void savePlayerStatistics(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        try {
            // 获取服务器的Minecraft版本，增加健壮性
            String dataVersion = getMcVersion();

            // 创建JSON对象存储所有统计数据
            JSONObject statisticsData = new JSONObject();
            statisticsData.put("playerName", playerName);

            // 收集各类统计数据
            statisticsData.put("general", collectGeneralStatistics(player, playerName));
            statisticsData.put("blocks", collectBlockStatistics(player));
            statisticsData.put("entities", collectEntityStatistics(player));
            statisticsData.put("items", collectItemStatistics(player));

            // 保存统计数据到数据库
            plugin.getMySQL().saveData(uuid.toString(), statisticsData, dataVersion)
                    .thenAccept(success -> {
                        if (success) {
                            plugin.getLogger().info("成功保存玩家 " + playerName + " 的统计数据 (版本: " + dataVersion + ")");
                        } else {
                            plugin.getLogger().warning("保存玩家 " + playerName + " 的统计数据失败，请检查数据库连接");
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE, "处理玩家 " + playerName + " 的统计数据保存结果时发生异常", ex);
                        return null;
                    });

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "保存玩家 " + playerName + " 的统计数据时发生错误", e);
        }
    }

    /**
     * 获取Minecraft版本号，增加错误处理
     *
     * @return Minecraft版本号
     */
    private String getMcVersion() {
        try {
            String version = Bukkit.getBukkitVersion();
            String[] parts = version.split("-");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                return parts[0];
            }
            // 如果无法正确解析，返回原始版本字符串
            return version;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "无法解析Minecraft版本号，使用默认值", e);
            return "unknown";
        }
    }

    /**
     * 收集通用统计数据
     *
     * @param player     玩家
     * @param playerName 玩家名称（用于日志）
     * @return 包含通用统计的JSON对象
     */
    private JSONObject collectGeneralStatistics(Player player, String playerName) {
        JSONObject generalStats = new JSONObject();
        for (Statistic stat : UNTYPED_STATISTICS) {
            try {
                generalStats.put(stat.name(), player.getStatistic(stat));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "获取玩家 " + playerName + " 的统计数据 " + stat.name() + " 时出错", e);
            }
        }
        return generalStats;
    }

    /**
     * 收集方块相关统计数据
     *
     * @param player 玩家
     * @return 包含方块统计的JSON对象
     */
    private JSONObject collectBlockStatistics(Player player) {
        JSONObject blockStats = new JSONObject();

        for (Material material : Material.values()) {
            // 跳过旧版材质和非方块材质
            if (material.isLegacy() || !material.isBlock()) {
                continue;
            }

            try {
                for (Statistic stat : BLOCK_STATISTICS) {
                    int count = player.getStatistic(stat, material);
                    if (count > 0) {
                        String key = stat == Statistic.MINE_BLOCK ?
                                "MINE_" + material.name() :
                                "PLACE_" + material.name();
                        blockStats.put(key, count);
                    }
                }
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                // 仅在调试模式下记录详细错误
                if (plugin.getConfig().getBoolean("settings.debugMode", false)) {
                    plugin.getLogger().log(Level.FINE, "处理方块统计时出错: " + material.name(), e);
                }
            }
        }

        return blockStats;
    }

    /**
     * 收集实体相关统计数据
     *
     * @param player 玩家
     * @return 包含实体统计的JSON对象
     */
    private JSONObject collectEntityStatistics(Player player) {
        JSONObject entityStats = new JSONObject();

        for (EntityType entityType : EntityType.values()) {
            try {
                for (Statistic stat : ENTITY_STATISTICS) {
                    int count = player.getStatistic(stat, entityType);
                    if (count > 0) {
                        String prefix = stat == Statistic.KILL_ENTITY ? "KILL_" : "KILLED_BY_";
                        entityStats.put(prefix + entityType.name(), count);
                    }
                }
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                // 仅在调试模式下记录详细错误
                if (plugin.getConfig().getBoolean("settings.debugMode", false)) {
                    plugin.getLogger().log(Level.FINE, "处理实体统计时出错: " + entityType.name(), e);
                }
            }
        }

        return entityStats;
    }

    /**
     * 收集物品相关统计数据
     *
     * @param player 玩家
     * @return 包含物品统计的JSON对象
     */
    private JSONObject collectItemStatistics(Player player) {
        JSONObject itemStats = new JSONObject();

        for (Material material : Material.values()) {
            // 跳过旧版材质
            if (material.isLegacy()) {
                continue;
            }

            try {
                for (Statistic stat : ITEM_STATISTICS) {
                    int count = player.getStatistic(stat, material);
                    if (count > 0) {
                        String prefix;
                        if (stat == Statistic.USE_ITEM) prefix = "USE_";
                        else if (stat == Statistic.BREAK_ITEM) prefix = "BREAK_";
                        else prefix = "CRAFT_";

                        itemStats.put(prefix + material.name(), count);
                    }
                }
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                // 仅在调试模式下记录详细错误
                if (plugin.getConfig().getBoolean("settings.debugMode", false)) {
                    plugin.getLogger().log(Level.FINE, "处理物品统计时出错: " + material.name(), e);
                }
            }
        }

        return itemStats;
    }
}
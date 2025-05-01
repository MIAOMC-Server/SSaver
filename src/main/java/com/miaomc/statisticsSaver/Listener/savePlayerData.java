package com.miaomc.statisticsSaver.Listener;

import com.alibaba.fastjson.JSONObject;
import com.miaomc.statisticsSaver.StatisticsSaver;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;

public class savePlayerData implements Listener {

    private final StatisticsSaver plugin;
    private static final String DATA_VERSION = "1.0";

    public savePlayerData(StatisticsSaver plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("玩家数据保存监听器已注册");
    }

    @EventHandler
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
            // 创建JSON对象存储所有统计数据
            JSONObject statisticsData = new JSONObject();
            statisticsData.put("playerName", playerName);

            // 添加普通统计数据
            JSONObject generalStats = new JSONObject();
            for (Statistic stat : Statistic.values()) {
                try {
                    if (stat.getType() == Statistic.Type.UNTYPED) {
                        generalStats.put(stat.name(), player.getStatistic(stat));
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "获取玩家 " + playerName + " 的统计数据 " + stat.name() + " 时出错", e);
                }
            }
            statisticsData.put("general", generalStats);

            // 添加方块相关统计
            JSONObject blockStats = new JSONObject();
            for (Material material : Material.values()) {
                if (material.isBlock()) {
                    try {
                        // 挖掘统计
                        int mineCount = player.getStatistic(Statistic.MINE_BLOCK, material);
                        if (mineCount > 0) {
                            blockStats.put("MINE_" + material.name(), mineCount);
                        }

                        // 放置统计
                        int placeCount = player.getStatistic(Statistic.USE_ITEM, material);
                        if (placeCount > 0) {
                            blockStats.put("PLACE_" + material.name(), placeCount);
                        }
                    } catch (Exception e) {
                        // 忽略不支持的方块统计
                    }
                }
            }
            statisticsData.put("blocks", blockStats);

            // 添加实体相关统计
            JSONObject entityStats = new JSONObject();
            for (EntityType entityType : EntityType.values()) {
                try {
                    // 击杀统计
                    int killCount = player.getStatistic(Statistic.KILL_ENTITY, entityType);
                    if (killCount > 0) {
                        entityStats.put("KILL_" + entityType.name(), killCount);
                    }

                    // 被杀统计
                    int deathCount = player.getStatistic(Statistic.ENTITY_KILLED_BY, entityType);
                    if (deathCount > 0) {
                        entityStats.put("KILLED_BY_" + entityType.name(), deathCount);
                    }
                } catch (Exception e) {
                    // 忽略不支持的实体统计
                }
            }
            statisticsData.put("entities", entityStats);

            // 物品相关统计
            JSONObject itemStats = new JSONObject();
            for (Material material : Material.values()) {
                try {
                    // 使用/消耗物品统计
                    int useCount = player.getStatistic(Statistic.USE_ITEM, material);
                    if (useCount > 0) {
                        itemStats.put("USE_" + material.name(), useCount);
                    }

                    // 物品破损统计
                    int breakCount = player.getStatistic(Statistic.BREAK_ITEM, material);
                    if (breakCount > 0) {
                        itemStats.put("BREAK_" + material.name(), breakCount);
                    }

                    // 合成物品统计
                    int craftCount = player.getStatistic(Statistic.CRAFT_ITEM, material);
                    if (craftCount > 0) {
                        itemStats.put("CRAFT_" + material.name(), craftCount);
                    }
                } catch (Exception e) {
                    // 忽略不支持的物品统计
                }
            }
            statisticsData.put("items", itemStats);

            // 保存统计数据到数据库
            plugin.getMySQL().insertData(uuid.toString(), statisticsData, DATA_VERSION)
                    .thenAccept(success -> {
                        if (success) {
                            plugin.getLogger().info("成功保存玩家 " + playerName + " 的统计数据");
                        } else {
                            plugin.getLogger().warning("保存玩家 " + playerName + " 的统计数据失败");
                        }
                    });

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "保存玩家 " + playerName + " 的统计数据时发生错误", e);
        }
    }
}
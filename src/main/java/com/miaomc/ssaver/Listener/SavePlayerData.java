package com.miaomc.ssaver.Listener;

import com.alibaba.fastjson.JSONObject;
import com.miaomc.ssaver.SSaver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SavePlayerData implements Listener {

    private final SSaver plugin;

    private final Map<UUID, Long> playerJoinTimes = new ConcurrentHashMap<>();

    // 缓存常用地统计类型，提高性能
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerJoinTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public SavePlayerData(SSaver plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("玩家数据保存监听器已注册");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 保存玩家数据
        savePlayerStatistics(player);
    }

    /**
     * 保存玩家的统计数据
     *
     * @param player 玩家
     */
    public void savePlayerStatistics(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // 获取玩家加入时的时间戳
        Long joinTime = playerJoinTimes.remove(uuid);
        if (joinTime == null) {
            plugin.getLogger().warning("无法获取玩家 " + player.getName() + " 的加入时间，跳过统计");
            return;
        }

        // 计算在线时间（秒）
        long sessionTimeInSeconds = Math.max(0, (System.currentTimeMillis() - joinTime) / 1000);

        try {
            // 从数据库获取现有数据
            JSONObject existingData = plugin.getMySQL().getPlayerData(uuid.toString()).join();
            if (existingData == null) {
                existingData = new JSONObject();
            }

            // 获取或创建 meta 对象
            JSONObject meta = existingData.getJSONObject("meta");
            if (meta == null) {
                meta = new JSONObject();
            }

            // 累加在线时间
            long totalOnlineTime = meta.getLongValue("onlineTimeInSeconds") + sessionTimeInSeconds;
            meta.put("onlineTimeInSeconds", totalOnlineTime);
            meta.put("firstJoinDate", player.getFirstPlayed());
            meta.put("playerName", playerName);
            existingData.put("meta", meta);

            // 检查是否达到最小在线时间
            long MINIMUM_SESSION_TIME = plugin.getConfig().getLong("settings.minSessionTime", 60);
            if (sessionTimeInSeconds >= MINIMUM_SESSION_TIME) {
                // 更新统计数据
                existingData.put("general", collectGeneralStatistics(player, playerName));
                existingData.put("blocks", collectBlockStatistics(player));
                existingData.put("entities", collectEntityStatistics(player));
                existingData.put("items", collectItemStatistics(player));
            }

            // 保存更新后的数据
            plugin.getMySQL().saveData(uuid.toString(), existingData, getMcVersion());
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
        boolean hasMineBlock = false;
        boolean hasPlaceBlock = false;

        for (Statistic stat : UNTYPED_STATISTICS) {
            try {
                generalStats.put(stat.name(), player.getStatistic(stat));
                if (stat == Statistic.MINE_BLOCK) {
                    hasMineBlock = true;
                }
                if (stat == Statistic.USE_ITEM) {
                    hasPlaceBlock = true;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "获取玩家 " + playerName + " 的统计数据 " + stat.name() + " 时出错", e);
            }
        }

        // 只有在 UNTYPED_STATISTICS 中不包含 MINE_BLOCK 时才添加
        if (!hasMineBlock) {
            try {
                int totalMined = 0;
                for (Material material : VALID_BLOCK_MATERIALS) {
                    try {
                        totalMined += player.getStatistic(Statistic.MINE_BLOCK, material);
                    } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
                        // 忽略不支持的方块材质
                    }
                }
                generalStats.put("TOTAL_BLOCKS_MINED", totalMined);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "获取玩家 " + playerName + " 的方块挖掘总数时出错", e);
            }
        }

        // 添加方块放置总数的统计
        if (!hasPlaceBlock) {
            try {
                int totalPlaced = 0;
                for (Material material : VALID_BLOCK_MATERIALS) {
                    try {
                        totalPlaced += player.getStatistic(Statistic.USE_ITEM, material);
                    } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
                        // 忽略不支持的方块材质
                    }
                }
                generalStats.put("TOTAL_BLOCKS_PLACED", totalPlaced);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "获取玩家 " + playerName + " 的方块放置总数时出错", e);
            }
        }

        return generalStats;
    }

    private static final Material[] VALID_BLOCK_MATERIALS;

    static {
        // 在类加载时初始化有效方块材质列表
        VALID_BLOCK_MATERIALS = java.util.Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy() && material.isBlock())
                .toArray(Material[]::new);
    }

    /**
     * 收集方块相关统计数据
     *
     * @param player 玩家
     * @return 包含方块统计的JSON对象
     */
    private JSONObject collectBlockStatistics(Player player) {
        JSONObject blockStats = new JSONObject();

        for (Material material : VALID_BLOCK_MATERIALS) {
            try {
                for (Statistic stat : BLOCK_STATISTICS) {
                    int count = player.getStatistic(stat, material);
                    if (count > 0) {
                        String key = stat == Statistic.MINE_BLOCK ?
                                "MINE_" + material.name() :
                                "USE_" + material.name();  // 使用 USE 而不是 PLACE
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
            if (material.isLegacy() || !material.isItem()) {
                continue;  // 跳过旧版材质和非物品材质
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
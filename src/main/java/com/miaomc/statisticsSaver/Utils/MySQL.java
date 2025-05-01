package com.miaomc.statisticsSaver.Utils;

import com.alibaba.fastjson.JSONObject;
import com.miaomc.statisticsSaver.StatisticsSaver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MySQL {
    private final StatisticsSaver plugin;
    private HikariDataSource dataSource;
    private final String tableName;
    private final String serverName;

    // 构造方法
    public MySQL(StatisticsSaver plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        String configTableName = config.getString("database.tableName");
        this.tableName = (configTableName == null || configTableName.isEmpty()) ? "playerStatistics" : configTableName;
        this.serverName = config.getString("settings.serverName", "root");
        setupPool();
    }

    // 设置连接池
    private void setupPool() {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String dbName = config.getString("database.name", "minecraft");
        String username = config.getString("database.username", "root");
        String password = config.getString("database.password", "");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&autoReconnect=true");
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setConnectionTimeout(30000);

        try {
            dataSource = new HikariDataSource(hikariConfig);
        } catch (Exception e) {
            plugin.getLogger().severe("无法建立数据库连接: " + e.getMessage());
        }
    }

    // 测试数据库连接
    public boolean testConnection() {
        if (dataSource == null) {
            return false;
        }

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                return false;
            }

            // 检查并初始化表结构
            checkAndInitializeTable(connection);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("数据库连接测试失败: " + e.getMessage());
            return false;
        }
    }

    // 初始化数据库表
    public void initialize() {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                checkAndInitializeTable(connection);
            } catch (SQLException e) {
                plugin.getLogger().severe("无法初始化数据库表: " + e.getMessage());
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "初始化数据库时发生错误", ex);
            return null;
        });
    }

    // 检查表是否存在，如果不存在则创建，检查表结构
    private void checkAndInitializeTable(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        boolean tableExists;

        try (ResultSet tables = meta.getTables(null, null, tableName, null)) {
            tableExists = tables.next();
        }

        if (!tableExists) {
            createTable(connection);
        } else {
            verifyAndUpdateColumns(connection);
        }
    }

    // 创建表
    private void createTable(Connection connection) throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "uuid VARCHAR(36) NOT NULL, "
                + "serverName VARCHAR(50) NOT NULL, "
                + "data LONGTEXT NOT NULL, "
                + "dataVersion VARCHAR(20) NOT NULL, "
                + "updateDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "createDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_uuid (uuid)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableSQL);
            plugin.getLogger().info("成功创建数据表 " + tableName);
        }
    }

    // 验证并更新表列
    private void verifyAndUpdateColumns(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();

        try (ResultSet columns = meta.getColumns(null, null, tableName, null)) {
            boolean hasUUID = false;
            boolean hasServerName = false;
            boolean hasData = false;
            boolean hasDataVersion = false;
            boolean hasUpdateDate = false;
            boolean hasCreateDate = false;

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME").toLowerCase();
                switch (columnName) {
                    case "uuid":
                        hasUUID = true;
                        break;
                    case "servername":
                        hasServerName = true;
                        break;
                    case "data":
                        hasData = true;
                        break;
                    case "dataversion":
                        hasDataVersion = true;
                        break;
                    case "updatedate":
                        hasUpdateDate = true;
                        break;
                    case "createdate":
                        hasCreateDate = true;
                        break;
                }
            }

            // 添加缺少的列
            try (Statement statement = connection.createStatement()) {
                if (!hasUUID) {
                    statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN uuid VARCHAR(36) NOT NULL");
                }
                if (!hasServerName) {
                    statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN serverName VARCHAR(50) NOT NULL");
                }
                if (!hasData) {
                    statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN data LONGTEXT NOT NULL");
                }
                if (!hasDataVersion) {
                    statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN dataVersion VARCHAR(20) NOT NULL");
                }
                if (!hasUpdateDate) {
                    statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN updateDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                }
                if (!hasCreateDate) {
                    statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN createDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                }
            }
        }
    }

    /**
     * 插入数据到数据库
     * 根据配置决定是同步还是异步执行
     *
     * @param uuid        玩家UUID
     * @param data        玩家数据
     * @param dataVersion 数据版本
     * @return 操作结果的Future
     */
    public CompletableFuture<Boolean> insertData(String uuid, JSONObject data, String dataVersion) {
        if (plugin.getConfig().getBoolean("settings.saveAsync", true)) {
            return insertDataAsync(uuid, data, dataVersion);
        } else {
            return doInsertData(uuid, data.toJSONString(), dataVersion);
        }
    }

    /**
     * 执行实际的数据插入操作
     *
     * @param uuid        玩家UUID
     * @param jsonData    JSON字符串数据
     * @param dataVersion 数据版本
     * @return 操作结果的Future
     */
    private CompletableFuture<Boolean> doInsertData(String uuid, String jsonData, String dataVersion) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + tableName + " (uuid, serverName, data, dataVersion) VALUES (?, ?, ?, ?)";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid);
                statement.setString(2, serverName);
                statement.setString(3, jsonData);
                statement.setString(4, dataVersion);

                int rowsInserted = statement.executeUpdate();

                if (plugin.getConfig().getBoolean("settings.showSaveMessages", true)) {
                    plugin.getLogger().info("已保存玩家 " + uuid + " 在服务器 " + serverName + " 的数据");
                }

                return rowsInserted > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "保存玩家 " + uuid + " 的数据失败", e);
                return false;
            }
        });
    }

    /**
     * 使用Bukkit调度器异步插入数据
     *
     * @param uuid        玩家UUID
     * @param data        玩家数据
     * @param dataVersion 数据版本
     * @return 操作结果的Future
     */
    private CompletableFuture<Boolean> insertDataAsync(String uuid, JSONObject data, String dataVersion) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                String sql = "INSERT INTO " + tableName + " (uuid, serverName, data, dataVersion) VALUES (?, ?, ?, ?)";

                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {

                    statement.setString(1, uuid);
                    statement.setString(2, serverName);
                    statement.setString(3, data.toJSONString());
                    statement.setString(4, dataVersion);

                    int rowsInserted = statement.executeUpdate();

                    if (plugin.getConfig().getBoolean("settings.showSaveMessages", true)) {
                        plugin.getLogger().info("已异步保存玩家 " + uuid + " 在服务器 " + serverName + " 的数据");
                    }

                    future.complete(rowsInserted > 0);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "异步保存玩家 " + uuid + " 的数据失败", e);
                    future.complete(false);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    // 关闭连接池
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("数据库连接池已关闭");
        }
    }

    // MySQL.java 中添加重新初始化连接池的方法
    public void reInitialize() {
        // 如果连接池存在且已关闭，重新创建
        if (dataSource == null || dataSource.isClosed()) {
            setupPool();
        }

        if (testConnection()) {
            initialize();
            plugin.getLogger().info("数据库连接池已重新初始化");
        }
    }
}
package com.miaomc.statisticsSaver;

import com.miaomc.statisticsSaver.Commands.SsaverCommand;
import com.miaomc.statisticsSaver.Listener.SavePlayerData;
import com.miaomc.statisticsSaver.Utils.MySQL;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatisticsSaver extends JavaPlugin {

    private MySQL mySQL;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 初始化MySQL
        this.mySQL = new MySQL(this);
        if (!mySQL.testConnection()) {
            getLogger().severe("数据库连接失败，插件将被禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        mySQL.initialize();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new SavePlayerData(this), this);

        // 创建命令处理器实例
        SsaverCommand commandHandler = new SsaverCommand(this);

        // 使用调度器延迟注册命令
        getServer().getScheduler().runTask(this, () -> {
            commandHandler.register();
            getLogger().info("命令已注册");
        });

        getLogger().info("StatisticsSaver 插件已启用！");
    }

    @Override
    public void onDisable() {
        // 关闭MySQL连接
        if (mySQL != null) {
            mySQL.close();
        }
        getLogger().info("StatisticsSaver 插件已禁用！");
    }

    /**
     * 获取MySQL实例
     *
     * @return MySQL实例
     */
    public MySQL getMySQL() {
        return mySQL;
    }
}
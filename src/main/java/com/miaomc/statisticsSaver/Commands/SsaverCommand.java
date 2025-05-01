package com.miaomc.statisticsSaver.Commands;

import com.miaomc.statisticsSaver.StatisticsSaver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SsaverCommand implements CommandExecutor, TabCompleter {

    private final StatisticsSaver plugin;

    public SsaverCommand(StatisticsSaver plugin) {
        this.plugin = plugin;
        // 不在构造函数中设置执行器，移到外部处理
    }

    // 注册命令方法，由主类调用
    public void register() {
        Command command = new Command("ssaver") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
                return SsaverCommand.this.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NotNull [] args) {
                return Objects.requireNonNull(SsaverCommand.this.onTabComplete(sender, this, alias, args));
            }
        };

        command.setDescription("StatisticsSaver 命令");
        command.setUsage("/ssaver <reload|help>");

        // 注册到命令映射中
        plugin.getServer().getCommandMap().register("statisticssaver", command);
        plugin.getLogger().info("命令 'ssaver' 已注册");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // 命令处理逻辑保持不变
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("statisticssaver.admin")) {
                    sender.sendMessage("§c你没有权限执行此命令！");
                    return true;
                }
                plugin.reloadConfig();

                // 关闭并重新初始化MySQL连接池
                plugin.getMySQL().close();
                plugin.getMySQL().reInitialize();

                if (!plugin.getMySQL().testConnection()) {
                    sender.sendMessage("§c重新加载配置后连接数据库失败，请检查配置！");
                    return true;
                }

                sender.sendMessage("§a配置已重新加载！");
                return true;
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage("§c未知命令。使用 /ssaver help 查看帮助。");
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6StatisticsSaver 命令帮助：");
        sender.sendMessage("§e/ssaver reload §7- 重新加载配置");
        sender.sendMessage("§e/ssaver help §7- 显示此帮助");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("statisticssaver.admin")) {
                completions.add("reload");
            }
            completions.add("help");

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
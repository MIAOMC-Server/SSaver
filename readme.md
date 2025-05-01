# StatisticsSaver

StatisticsSaver 是一个用于 Minecraft Paper 服务器的插件，主要功能是保存玩家统计数据。此插件可以将玩家的统计数据保存到
MySQL 数据库中，可配合后端获取玩家游戏信息，便于在网站上展示。

## 功能特点

- 自动保存玩家统计数据到 MySQL 数据库
- 支持通过命令重新加载配置
- 简单易用的命令接口

## 系统要求

- Java 21 或更高版本
- Paper 1.21 或兼容版本
- MySQL 数据库

## 安装方法

1. 下载最新版本的 StatisticsSaver.jar
2. 将 JAR 文件放入服务器的 `plugins` 目录
3. 启动或重启服务器
4. 编辑生成的配置文件，设置数据库连接参数
5. 使用 `/ssaver reload` 命令重新加载配置

## 配置文件

插件首次运行时会生成默认配置文件。您需要修改以下关键设置：

```yaml
mysql:
  host: localhost
  port: 3306
  database: minecraft_stats
  username: root
  password: your_password
  tablePrefix: stats_
```

## 命令和权限

### 命令

- `/ssaver help` - 显示帮助信息
- `/ssaver reload` - 重新加载插件配置（需要权限）

### 权限

- `statisticssaver.admin` - 允许使用管理命令（如 reload）

## 开发信息

本项目使用 Maven 构建，开发环境要求：

- Java 17
- Maven 3.6+
- 依赖：Paper API 1.21.4

## 许可证

MIT License

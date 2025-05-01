# StatisticsSaver

StatisticsSaver 是一个用于 Minecraft Paper 服务器的插件，主要功能是保存玩家统计数据。此插件可以将玩家的统计数据保存到
MySQL 数据库中，方便服务器管理员进行数据分析和备份。

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
  host: localhost # 数据库主机地址
  port: 3306 # 数据库端口
  database: minecraft_stats # 数据库名称
  username: root # 数据库用户名
  password: your_password # 数据库密码
  tableName: playerStatistics # 创建的数据表的名称
settings:
  serverName: root # 用于区分子服
  showSaveMessages: true # 控制台是否输出保存信息
  saveAsync: true # 是否异步保存数据
  minSessionTime: 60 #最小停留时间，单位秒(在该时段内退出不会出发保存操作，防止频繁保存)
```

## 命令和权限

### 命令

- `/ssaver help` - 显示帮助信息
- `/ssaver reload` - 重新加载插件配置（需要权限）

### 权限

- `statisticssaver.admin` - 允许使用管理命令（如 reload）
- `statisticssaver.track` - 默认开，是否允许玩家被统计

## 开发信息

本项目使用 Maven 构建，开发环境要求：

- Java 21
- Maven 3.6+
- 依赖：Paper API 1.21.4

## 许可证

MIT License
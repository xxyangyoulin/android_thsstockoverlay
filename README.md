# 同花顺交易计划浮窗

[![Tag Build](https://github.com/xxyangyoulin/android_thsstockoverlay/actions/workflows/tag-build.yml/badge.svg)](https://github.com/xxyangyoulin/android_thsstockoverlay/actions/workflows/tag-build.yml)

一款 Android 本地交易计划工具。应用通过无障碍服务识别同花顺当前打开的股票详情页，并在浮窗中展示该股票的待执行计划和纪律提醒。

## 功能

- 按日期创建和编辑股票交易计划
- 同一股票每天最多保存一条计划
- 计划支持待执行、已执行和已作废状态
- 按日期分组展示全部计划
- 识别同花顺当前股票并展示对应计划浮窗
- 浮窗支持拖拽、左右吸边和点击进入计划详情
- 支持调整浮窗大小、边距、字体、颜色和透明度
- 可单独暂停浮窗，不需要关闭无障碍服务
- 数据保存在设备本地

只有状态为“待执行”的计划会显示在浮窗中。当前股票没有待执行计划时，不显示浮窗和操作圆点。

## 安装

从 [Releases](https://github.com/xxyangyoulin/android_thsstockoverlay/releases) 下载最新的 `app-debug.apk` 并安装。

应用要求 Android 8.0 或更高版本。

## 使用

1. 在首页创建股票计划，填写股票代码、名称、计划日期和操作内容。
2. 点击首页的“开启”，进入系统无障碍设置。
3. 找到“同花顺股票识别”并开启服务。
4. 返回应用，确认“显示计划浮窗”开关已开启。
5. 打开同花顺并进入股票详情页，应用会展示该股票的待执行计划。

无障碍授权由 Android 系统管理。应用不会使用 root 权限，也不能在应用内直接启停无障碍服务。

## 本地构建

需要 JDK 17 和 Android SDK：

```bash
./gradlew assembleDebug
```

APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 自动发布

推送 Git 标签后，GitHub Actions 会自动构建 Debug APK、上传构建产物并创建对应的 GitHub Release：

```bash
git tag -a v1.0.2 -m "v1.0.2"
git push origin v1.0.2
```

工作流配置见 [tag-build.yml](.github/workflows/tag-build.yml)。

## 说明

本项目用于记录和提醒个人交易计划，不提供投资建议。交易决策及风险由使用者自行承担。

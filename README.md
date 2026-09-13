# 记账本（JiZhangBen）

一个本地优先的安卓记账应用。界面使用 HTML/CSS/JavaScript，外壳使用 Android WebView + Java，所有账目默认只保存在手机本地。

当前开源版本对应正式版 14，版本号为 **2.10**。

## 下载安装包

不想自己编译的话，可以直接下载预构建 APK：

- [jizhangben-v2.10.1.apk](https://github.com/56u7/jizhangben/raw/main/dist/jizhangben-v2.10.1.apk)

如果上面的链接打开后不是直接下载，请进入仓库的 `dist` 目录，点击
`jizhangben-v2.10.1.apk`，再点 **Download raw file**。

当前 APK 的 SHA-256：

```text
72329769AE84BCDDCCD773B5CE75D320B13CB76160AB43EF4F3F7CB1429D9AF8
```

## 功能

- 记一笔：收入 / 支出、分类、金额、日期、备注
- 每日预算：按本月收入或当前结余分摊，可自定义天数
- 明细列表：按周 / 月查看，右滑标星、左滑删除、长按不计入预算
- 批量选择：长按“本周 / 本月”进入多选删除
- 日历视图与统计视图：分类支出占比、月度对比
- 导入账单：自动识别微信 / 支付宝导出的 xlsx / CSV
- 支付捕获：读取微信 / 支付宝支付通知和交易提醒，先进入“待导入账目”
- 待导入账目：修改、标星、删除、不计入预算，确认后写入账本
- 清理导入账单：按来源和月份单独清理微信 / 支付宝记录
- 导出 Excel / CSV，完整备份与恢复

## 环境要求

- Android 7.0（API 24）及以上
- JDK 17
- Android SDK 34
- Gradle 8.7（仓库包含 Gradle Wrapper）

## 构建

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
```

构建前请确保已安装 Android SDK 34，并设置 `ANDROID_HOME` 或创建不提交到仓库的 `local.properties`：

```properties
sdk.dir=/path/to/android-sdk
```

生成未签名 release 包：

```bash
./gradlew assembleRelease
```

如果你要发布自己的安装包，请使用自己的签名密钥，不要使用他人的密钥。

## 项目结构

```
app/src/main/assets/index.html     # 应用界面与业务逻辑
app/src/main/assets/xlsx.full.min.js
app/src/main/java/...              # Android 外壳、通知监听、文件读写
app/src/main/res/                  # 图标、布局、字符串
```

## 隐私

应用不包含服务器、不上传账目。支付通知只在本地解析，详见 [PRIVACY.md](PRIVACY.md)。

## 免责声明

本项目是个人开源项目，与微信、支付宝及其关联公司没有官方关系。微信、支付宝是其各自权利人的商标。

## 许可证

[MIT License](LICENSE)

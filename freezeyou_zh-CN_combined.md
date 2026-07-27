# 自冻 FreezeYou — сводный документ по сайту (zh-CN)

Собрано из markdown-дампа сайта freezeyou (ветка zh-CN) после очистки от навигационного меню, ссылок на скачивание дистрибутива и раздела благодарностей — как контент, не несущий смысла для понимания продукта. Changelog оставлен в исходном виде (обрезан на V10.16-pre2 — это предел того, что было в исходном файле сайта).

Позиционирование с главной страницы: 自冻 = «冻结|加速|省电» (заморозка | ускорение | экономия энергии). Установочный пакет менее 5 МБ, акцент на открытый функционал и кастомизацию.

---

## 1. Введение и функции

[#](#介绍) 介绍

省电！省电！我不要一天三充！⚡

 流畅！流畅！我不要生硬动画！💨

 安静！安静！我不要无端打扰！🔇

[#](#功能) 功能

- 支持 免ROOT(DPM)、ROOT(HIDE)、ROOT(DISABLE)、系统应用(DISABLE)、系统应用(DISABLE_USER)、系统应用(DISABLE_UNTIL_USED) 等模式
- 支持计划任务
- 支持定时任务
- 支持条件任务
- 支持延时任务
- 支持一键冻结
- 支持一键解冻
- 支持离开冻结
- 支持分类查看
- 支持通知栏瓷块一键冻结解冻锁屏
- 支持锁屏后自动冻结
- 支持多种快捷方式操作
- 支持多种解冻/冻结方式
- 支持更换界面风格
- 支持备份与恢复
- 等更多功能！

[#](#权限相关) 权限相关

- 无障碍服务 API： 监听前台应用程序变化（离开应用时、打开应用时），以及避免冻结前台应用程序。
- 设备管理员 API： 用于 Profile Owner 模式、Device Owner 模式以及锁屏。
- 蓝牙相关： 用于在计划任务中启用与禁用蓝牙。
- Wi-Fi 相关： 用于在计划任务中启用与禁用 Wi-Fi 。
- 获取手机状态与身份： 用于在计划任务中启用与禁用蜂窝移动数据。
- 变更组件启用状态： 用于系统应用模式。
- 查询所有软件包： 用于列出设备上的所有软件包。
- 设定精确的闹钟：用于时间触发的计划任务。
- 请求忽略电池优化： 用于在某些设备上获得更好的时间触发任务的体验。

---

## 2. Предупреждение

WARNING

不知道的不要冻结（变更状态），除非已经做好了发生各种糟糕的事情的心理准备。

使用自冻FreezeYou时，请谨慎操作，尤其是涉及到冻结与解冻的部分。如操作不当，可能导致系统异常、无法正常使用或无法正常开机等问题。

自冻FreezeYou

冻结与解冻

总之，不知道的不要冻结（变更状态），除非已经做好了发生各种糟糕的事情的心理准备。

介绍 下载

---

## 3. Быстрый старт (как пользоваться)

[#](#快速入门) 快速入门

`自冻FreezeYou`的功能非常之多，这里对一部分内容做些简短的介绍，方便快速入门。

[#](#授予权限) 授予权限

`自冻FreezeYou`中的一些功能，需要一些特殊权限，就比如`冻结与解冻`就需要特殊授权才能正常使用（若不使用相关功能，可不授予权限），目前，如要使用`冻结与解冻`功能，需要保证以下至少一个权限已经或能够授予`自冻FreezeYou`，并在`更多设置` - `冻结与解冻` - `选择冻结解冻模式`中选中相应的模式：

[#](#冻结与解冻) 冻结与解冻 格外谨慎

启动`自冻FreezeYou`，待主界面列表载入完成后，点击相应的应用，选择`冻结/解冻/启动`即可进行冻结与解冻操作。

[#](#分类查看) 分类查看 1.13+

默认情况下，启动`自冻FreezeYou`后首页会直接展示全部的应用，这时，如果想要寻找一些应用，有时会比较麻烦，那么，可以点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，点击`查看模式(分类查看)`，即可根据需要分类进行查看。

[#](#快速搜索) 快速搜索 2.13+

启动`自冻FreezeYou`后，在显示的主界面中，点击顶端附近的`搜索`，即可进行快速搜索。

 如，输入`A`即会立即筛选并列出该分类中所有名称中包含`A`或`a`的应用（不区分大小写）。

[#](#计划任务) 计划任务 6.0+

计划任务的功能比较多，也较为复杂，这里我们单独进行 → [介绍](/zh-CN/guide/schedules.html)。

[#](#更换界面风格) 更换界面风格 4.0+

启动`自冻FreezeYou`，点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，选择`更多设置`，选择`外观`，点击`界面风格`即可修改。

[#](#备份与还原) 备份与还原 8.8+

启动`自冻FreezeYou`，点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，选择`更多设置`，再选择`备份与还原`，点击`导出`即可将当前的设置、计划任务等数据导出，点击`导入`，会读入下方输入框中的数据，整理后供选择需要导入的数据项。

[#](#通知栏瓷块) 通知栏瓷块

通过通知栏瓷块，可点击瓷块快速执行操作。下拉通知栏，点击`编辑`，将相应的瓷块设为显示，然后完成编辑后即可使用（需要设备支持）。**可用瓷块：**

- 一键冻结
- 一键解冻
- 一键锁屏

[#](#一键冻结) 一键冻结

`一键冻结`会对每一个存在于`一键冻结列表`中的应用执行`冻结`操作，使用前，需要先将需要被执行的应用添加到`一键冻结列表`中（点击主界面列表中的相应应用，选择`加入/移出`，即可添加）。**使用途径：**

- 启动`自冻FreezeYou`，点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，选择`立即执行`，最后选择`一键冻结`即可。
- 启动`自冻FreezeYou`，点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，选择`快捷方式`，再选择`一键冻结`，即可在桌面上通过快捷方式进行`一键冻结`。
- 唤出桌面的`添加小部件`或是`添加微件`或是`添加小工具`菜单，选择`自冻FreezeYou`，再选择`一键冻结`，即可在桌面上通过快捷方式进行`一键冻结`。

[#](#一键解冻) 一键解冻

`一键解冻`会对每一个存在于`一键解冻列表`中的应用执行`解冻`操作，使用前，需要先将需要被执行的应用添加到`一键解冻列表`中（点击主界面列表中的相应应用，选择`加入/移出`，即可添加）。**使用途径：**

- 启动`自冻FreezeYou`，点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，选择`立即执行`，最后选择`一键解冻`即可。
- 启动`自冻FreezeYou`，点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，选择`快捷方式`，再选择`一键解冻`，即可在桌面上通过快捷方式进行`一键解冻`。
- 唤出桌面的`添加小部件`或是`添加微件`或是`添加小工具`菜单，选择`自冻FreezeYou`，再选择`一键解冻`，即可在桌面上通过快捷方式进行`一键解冻`。

[#](#离开冻结) 离开冻结

*建议使用 计划任务替代*

启动

`自冻FreezeYou`，点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，选择`更多设置`，选择`自动化`，再勾选`离开冻结`即可，离开在`离开冻结列表`（点击主界面列表中的相应应用，选择`加入/移出`，即可添加）里的相应应用时对应的应用会被冻结。[#](#锁屏后一键冻结) 锁屏后一键冻结

*建议使用 计划任务替代*

启动

`自冻FreezeYou`，点击右上角的`⋮`或是右下角的`+`或是设备上的`≡`，唤出菜单，选择`更多设置`，选择`自动化`，再勾选`锁屏后一键冻结`即可，锁屏后会执行`一键冻结`。

---

## 4. Включение без-ROOT режима (DPM)

[#](#启用免root) 启用免ROOT

部分功能需要授予该特殊权限才可正常使用，如无需要则可直接跳过。

[#](#风险提示) 风险提示

- 目前已累计收到两例用户反馈，反应出现启用免ROOT后设备图形锁（图案密码）无端发生变化导致无法解锁的情况，目前仍未查明原因，其中一台设备为 Samsung S7 edge 原厂系统 ，另一台情况不明（疑似同为 Samsung 设备）。因此，如有重要资料请各位在操作前备份，以防意外情况发生导致不必要的麻烦。
- 鉴于这部分 Samsung 设备发生的问题，建议启用前先关闭图形锁、密码锁等类似内容。
- 在启用免ROOT后，冻结应用（尤其是系统应用）前，请注意，某些系统某些情况下在对个别应用执行冻结操作后会导致系统出现一些异常——比如莫名卡顿、无法正常开机等问题。因此，请尽可能的在一定安全限度内进行操作以确保操作安全，避免产生不必要的麻烦。
- 某些设备某些系统在启用免ROOT后会导致系统自带的应用多开、应用分身无法正常使用（我们收集到的数据反应主要集中在 Android 8.0 系统中，非系统应用的第三方应用提供的分身、多开未见受此影响）。

[#](#所需材料) 所需材料

- ADB工具（下方提供）
- 一些代码（下方提供）
- 系统版本不小于 Android 5.0 且已安装最新版`自冻FreezeYou`的设备（自备）

[#](#操作方法) 操作方法

- 找到设备系统设置中的`开发者选项`（没有的可以试试多点几下`关于手机`，或搜索`"您的设备型号" + 开发者选项`）
- 开启`开发者选项`中的`Android 调试`或`USB 调试`并将设备与具备 ADB 工具的计算机连接
- 完整解压缩先前下载的压缩包（.zip 格式）
- 如果想使用 `profile-owners`而不是`device-owners`，编辑`apply.xx`文件，并使用`adb shell dpm set-profile-owners cf.playhi.freezeyou/.DeviceAdminReceiver`替换`adb shell dpm set-device-owners`这一行。
- Linux用户执行解压后的`apply.sh`，Windows 用户执行解压后的`apply.cmd`或`apply`
- 如果`正在尝试启用免ROOT模式......`下方的提示包含`Success:`，应该就成功了。如果没有成功，可以前往[免ROOT疑难解答](/zh-CN/faq/mroot.html)寻找相应的类似情况的解决方案尝试解决。
- 总是失败？ → [免ROOT疑难解答](/zh-CN/faq/mroot.html)

[#](#核心代码) 核心代码

```
adb shell dpm set-device-owner cf.playhi.freezeyou/.DeviceAdminReceiver
```
```
adb shell dpm set-profile-owner cf.playhi.freezeyou/.DeviceAdminReceiver
```
[#](#操作截图) 操作截图

---

## 5. Плановые задачи — язык команд

#  计划任务

##  通用任务命令

###  可用命令

- `okff`：一键冻结。
- `okuf`：一键解冻。
- `ff`：冻结。
- `uf`：解冻。
- `es`：启用某设置项- *（自6.2版本可用 *- `wifi`；自7.1版本可用- `cd`（蜂窝移动数据网络）；自7.3版本可用- `bluetooth`）
。
- `ds`：关闭某设置项- *（自6.2版本可用 *- `wifi`；自7.1版本可用- `cd`（蜂窝移动数据网络）；自7.3版本可用- `bluetooth`）
。
- `st`：显示一条提示。
- `sn`8.6+：在通知栏显示一条通知。
- `sp`：打开指定应用。
- `su`：根据 Uri 打开指定应用。
- `lg`7.2+：打印一条 ERROR 级别的 LOG ，一般情况下无使用需求。
- `ls`8.7+：锁定屏幕。

###  追加参数（可选）

###  内部变量

- `[ppkgn]`7.4+：先前应用程序包名。仅 触发器为 离开应用时、打开应用时 时可使用，正式使用前可在相关任务中，使用- `st [ppkgn]`了解相关内容，减小意外冻结的可能。
- `[cpkgn]`7.4+：当前应用程序包名。仅 触发器为 离开应用时、打开应用时、解冻应用时、冻结应用时 时可使用，正式使用前可在相关任务中，使用- `st [cpkgn]`了解相关内容，减小意外冻结的可能。

###  使用示例

####  okff

####  okuf

- `okuf`：立即执行一键解冻。
- `okuf -d 10`：延后 10秒 执行一键解冻。

####  ff

- `ff com.tencent.mobileqq`：冻结包名为- `com.tencent.mobileqq`(QQ) 的应用程序。
- `ff com.tencent.mobileqq,@5oiR55qE5YiX6KGo`：冻结包名为- `com.tencent.mobileqq`(QQ) 和- `存在于别名为 5oiR55qE5YiX6KGo 的我的自选中的`的应用程序。
- `ff com.tencent.mobileqq,com.tencent.mm`：冻结包名为- `com.tencent.mobileqq`(QQ) 和- `com.tencent.mm`(微信) 的应用程序。
- `ff com.tencent.mobileqq,com.tencent.mm,com.taobao.taobao`：冻结包名为- `com.tencent.mobileqq`(QQ) 和- `com.tencent.mm`(微信) 和- `com.taobao.taobao`(淘宝) 的应用程序。
- `ff -d 3600 com.tencent.mobileqq`：延后 3600秒 冻结包名为- `com.tencent.mobileqq`(QQ) 的应用程序。

####  uf

- `uf com.tencent.mobileqq`：解冻包名为- `com.tencent.mobileqq`(QQ) 的应用程序。
- `uf com.tencent.mobileqq,com.tencent.mm`：解冻包名为- `com.tencent.mobileqq`(QQ) 和- `com.tencent.mm`(微信) 的应用程序。
- `uf com.tencent.mobileqq,@5oiR55qE5YiX6KGo`：解冻包名为- `com.tencent.mobileqq`(QQ) 和- `存在于别名为 5oiR55qE5YiX6KGo 的我的自选中的`的应用程序。
- `uf com.tencent.mobileqq,com.tencent.mm,com.taobao.taobao`：解冻包名为- `com.tencent.mobileqq`(QQ) 和- `com.tencent.mm`(微信) 和- `com.taobao.taobao`(淘宝) 的应用程序。

####  es

- `es wifi`：启用 WiFi 。
- `es -d 20 wifi`：延后 20秒 启用 WiFi 。
- `es wifi,cd`：启用 WiFi 和 蜂窝移动数据网络 。
- `es wifi;okuf;uf com.tencent.mobileqq`：启用 WiFi 、执行 一键解冻 并 解冻包名为- `com.tencent.mobileqq`(QQ) 的应用程序。

####  ds

- `ds wifi`：关闭 WiFi 。
- `ds cd`：关闭 蜂窝移动数据网络 。
- `ds wifi;okff`：关闭 WiFi 并执行 一键冻结。
- `ds -d 15 wifi;okff`：延后 15秒 关闭 WiFi 并 立即执行 一键冻结。

####  st

- `st 这是一条提示`：显示一条 Toast 提示，内容为- `这是一条提示`。

####  sn

- `sn 通知标题,通知内容`：在通知栏显示一条通知。

####  sp

- `sp com.tencent.mobileqq`：打开 QQ （包名为- `com.tencent.mobileqq`）。
- `sp com.tencent.mobileqq,com.tencent.mm`：打开 QQ 和 微信 （包名为- `com.tencent.mobileqq`和- `com.tencent.mm`）。

####  su

####  lg

- `lg 10086`：输出一条 ERROR 级别的 LOG ，内容为 10086 。

####  ls

##  触发器附加参数

###  使用前言

- 部分触发器无须附加参数（如果填写会被忽略）。
- 部分触发器可以填写附加参数（非必须）。
- 部分触发器必须提供符合条件的附加参数，否则无法正常执行。

###  参数要求

- `打开屏幕时`：目前无附加参数。
- `关闭屏幕时`：目前无附加参数。
- `打开应用时`：- `7.0及以前版本`- *必须附加 *- `应用包名`；
- `自 7.0`- *可附加 *- `应用包名`、- `我的列表`
- `(V9.2)`
- *在附加了应用包名的情况下，正常情况下，打开XX应用时会取消所有属于离开XX应用时已部署但尚未执行的延时任务。*
- `离开应用时`：可附加- `应用包名`、- `我的列表`- `(V9.2)`
- *在附加了应用包名的情况下，正常情况下，离开XX应用时会取消所有属于打开XX应用时已部署但尚未执行的延时任务。*
- `解冻应用时`：可附加- `应用包名`，如无附加，则解冻任意应用程序均执行。
- `冻结应用时`：可附加- `应用包名`，如无附加，则冻结任意应用程序均执行。

###  可用参数

- `应用包名`：例如- `com.tencent.mobileqq`。
- `我的列表`：例如- `@5oiR55qE5YiX6KGo`。

###  使用实例

- 选择 `打开应用时`，附加参数填写`com.tencent.mobileqq`，则会在运行`QQ`时执行预设置的`任务`。
- 选择 `打开应用时`，附加参数填写`com.tencent.mobileqq,com.tencent.mm`，则会在运行`QQ`或`微信`时执行预设置的`任务`。
- 选择 `打开应用时`，附加参数填写`com.tencent.mobileqq,@5oiR55qE5YiX6KGo`，则会在运行`QQ`或`存在于别名为 5oiR55qE5YiX6KGo 的列表中的应用程序`时执行预设置的`任务`。
- 选择 `打开应用时`，附加参数填写`当前使用的桌面的包名`，则会在**返回桌面**时执行预设置的`任务`。
- 选择 `离开应用时`，附加参数不填写任何内容，则会在**离开任意应用程序**时执行预设置的`任务`。

##  疑难解答

---

## 6. API — обзор

#
API
自冻 FreezeYou
提供了一些接口供外部调用，具体情况如下：
通过
URI
冻结解冻应用程序
通过
Provider
进行冻结解冻以及数据查询等操作
通过
startActivity
冻结解冻应用程序
后续如新增一些对外接口以及接口能力，此页也会进行更新。
通过 URI 冻结解冻应用程序

## 6.1 API — через Provider

#  通过 Provider 进行冻结解冻以及数据查询等操作

##  版本要求

- **自冻(FreezeYou)**版本不小于- **9.0**。
- 部分需要更高版本（已标注）。

##  授权范围

- 获取当前 **自冻(FreezeYou)**的运行模式、获取已冻结应用列表、获取是否可通过**自冻(FreezeYou)**安装应用9.2+ 、进行冻结应用操作、进行解冻应用操作。

##  声明权限

按需在 `AndroidManifest.xml` 中声明权限，并适时请求授予权限。

###  普通权限

无需额外请求授予权限。

####  查询各项状态数据

包括`获取应用是否被冻结`、`获取当前运行模式`。

```
<uses-permission android:name="cf.playhi.freezeyou.permission.QUERY_STATUS" />
```

###  危险权限

需要适时额外请求授予权限。

####  进行解冻应用操作

```
<uses-permission android:name="cf.playhi.freezeyou.permission.ENABLE_APPLICATIONS" />
```

####  进行冻结应用操作

```
<uses-permission android:name="cf.playhi.freezeyou.permission.DISABLE_APPLICATIONS" />
```

##  代码示例

###  获取当前运行模式

```
Bundle resultBundle = getContentResolver().call(
    Uri.parse("content://cf.playhi.freezeyou.export.QUERY"), 
    "QUERY_MODE", null, new Bundle()
);
String currentMode = resultBundle.getString("currentMode", "Failed");
```

###  获取应用是否被冻结

```
Bundle willBeSend = new Bundle();
willBeSend.putString("packageName", packageName);
Bundle resultBundle = getContentResolver().call(
    Uri.parse("content://cf.playhi.freezeyou.export.QUERY"), 
    "QUERY_FREEZE_STATUS", null, willBeSend
);
int resultStatusCode = resultBundle.getInt("status", 123456);
```

###  进行解冻应用操作

```
Bundle willBeSend = new Bundle();
willBeSend.putString("packageName", pkgName);
Bundle resultBundle = getContentResolver().call(
    Uri.parse("content://cf.playhi.freezeyou.export.UNFREEZE"), 
    "MODE_AUTO", null, willBeSend
);
int resultCode = resultBundle.getInt("result", 123456);
```

###  进行冻结应用操作

```
Bundle willBeSend = new Bundle();
willBeSend.putString("packageName", pkgName);
Bundle resultBundle = getContentResolver().call(
    Uri.parse("content://cf.playhi.freezeyou.export.FREEZE"), 
    "MODE_AUTO", null, willBeSend
);
int resultCode = resultBundle.getInt("result", 123456);
```

##  参数细节

###  获取当前运行模式

####  请求

| 参数 | 值 | 
|---|
| Uri | `Uri.parse("content://cf.playhi.freezeyou.export.QUERY")` | 
| Method | `QUERY_MODE` | 
| Extras | 空 `Bundle` | 

####  返回

如果对应键值为 `null`，则检查请求时的 `Method` 以及 `Extras` 是否为 `null` 。

 数据存于 `Bundle` 中的键 `currentMode`，类型为 `String`。

| 取得值 | 意义 | 
|---|
| dpm | DPM（免ROOT）模式（ROOT模式可能可用） | 
| root | ROOT模式（DPM模式不可用） | 
| unavailable | DPM 与 ROOT 模式均不可用 | 

###  获取当前运行模式 V210.11+

####  请求

| 参数 | 值 | 
|---|
| Uri | `Uri.parse("content://cf.playhi.freezeyou.export.QUERY")` | 
| Method | `QUERY_MODE_V2` | 
| Extras | 空 `Bundle` | 

####  返回

如果对应键值为 `null`，则检查请求时的 `Method` 以及 `Extras` 是否为 `null` 。

 数据存于 `Bundle` 中的键 `currentMode`，类型为 `String`。

| 取得值 | 意义 | 
|---|
| MODE_DPM | 当前运行于 `DPM 模式` | 
| MODE_ROOT_DISABLE_ENABLE | 当前运行于 `ROOT DISABLE 模式` | 
| MODE_ROOT_HIDE_UNHIDE | 当前运行于 `ROOT HIDE 模式` | 
| MODE_LEGACY_AUTO | 当前运行于 `遗留的 DPM 与 ROOT DISABLE 自动选择模式` | 
| MODE_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED | 当前运行于 `系统应用 DISABLE_UNTIL_USED 模式` | 
| MODE_SYSTEM_APP_ENABLE_DISABLE_USER | 当前运行于 `系统应用 DISABLE_USER 模式` | 
| MODE_SYSTEM_APP_ENABLE_DISABLE | 当前运行于 `系统应用 DISABLE 模式` | 
| MODE_PROFILE_OWNER11.3+ | 当前运行于 `配置文件所有者（Profile Owner）模式` | 
| MODE_UNKNOWN | 当前运行于 `未知模式` | 

###  获取应用是否被冻结

####  请求

| 参数 | 值 | 
|---|
| Uri | `Uri.parse("content://cf.playhi.freezeyou.export.QUERY")` | 
| Method | `QUERY_FREEZE_STATUS` | 
| Extras | `Bundle`，键`packageName`必须包含被查询的应用包名 | 

####  返回

如果对应键值为 `null`，则检查请求时的 `Method` 以及 `Extras` 是否为 `null` 。

 数据存于 `Bundle` 中的键 `status`，类型为 `int`。

| 取得值 | 意义 | 
|---|
| -2 | `Bundle`中的键`packageName`的值为`null` | 
| -1 | **自冻**内部错误 | 
| 0 | 未冻结 | 
| 1 | ROOT 模式冻结 | 
| 2 | DPM 模式冻结 | 
| 3 | DPM + ROOT 双模式冻结 | 
| 998 | 没有找到对应应用 | 

###  进行解冻应用操作

####  请求

| 参数 | 值 | 
|---|
| Uri | `Uri.parse("content://cf.playhi.freezeyou.export.UNFREEZE")` | 
| Method | `MODE_AUTO`或`MODE_ROOT`或`MODE_MROOT` | 
| Extras | `Bundle`，键`packageName`必须包含被解冻的应用包名 | 

####  返回

如果对应键值为 `null`，则检查请求时的 `Method` 以及 `Extras` 是否为 `null` 。

 数据存于 `Bundle` 中的键 `result`，类型为 `int`。

| 取得值 | 意义 | 
|---|
| -4 | ROOT 模式解冻失败 | 
| -3 | DPM 模式解冻失败 | 
| -2 | `Bundle`中的键`packageName`的值为`null` | 
| -1 | **自冻**内部错误 | 
| 0 | 解冻成功 | 
| 998 | 没有找到对应应用 | 
| 999 | 检查发现未冻结，无需解冻 | 

###  进行解冻应用操作 V210.11+

####  请求

| 参数 | 值 | 
|---|
| Uri | `Uri.parse("content://cf.playhi.freezeyou.export.UNFREEZE")` | 
| Method | `MODE_DPM`、`MODE_ROOT_DISABLE_ENABLE`、`MODE_ROOT_HIDE_UNHIDE`、`MODE_LEGACY_AUTO`、`MODE_SYSTEM_APP_ENABLE_DISABLE`、`MODE_SYSTEM_APP_ENABLE_DISABLE_USER`、`MODE_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED`、`MODE_PROFILE_OWNER`11.3+ | 
| Extras | `Bundle`，键`packageName`必须包含被解冻的应用包名 | 

####  返回

如果对应键值为 `null`，则检查请求时的 `Method` 以及 `Extras` 是否为 `null` 。

 数据存于 `Bundle` 中的键 `result`，类型为 `int`。

| 取得值 | 意义 | 
|---|
| -10 | 配置文件所有者（Profile Owner）模式系统内部错误 | 
| -9 | 不是配置文件所有者（Profile Owner） | 
| -8 | 不是系统应用 | 
| -7 | 没有该冻结解冻模式 | 
| -6 | 无 DPM 权限 | 
| -5 | DPM 模式系统内部错误 | 
| -4 | 无 ROOT 权限 | 
| -3 | 设备 Android 版本过低，不支持该 `Method` | 
| -2 | `Bundle`中的键`packageName`的值为`null` | 
| -1 | **自冻**内部错误 | 
| 0 | 解冻成功 | 
| 1 | 没有发生异常，一般情况下为成功 | 

###  进行冻结应用操作

####  请求

| 参数 | 值 | 
|---|
| Uri | `Uri.parse("content://cf.playhi.freezeyou.export.FREEZE")` | 
| Method | `MODE_AUTO`或`MODE_ROOT`或`MODE_MROOT` | 
| Extras | `Bundle`，键`packageName`必须包含被解冻的应用包名 | 

####  返回

如果对应键值为 `null`，则检查请求时的 `Method` 以及 `Extras` 是否为 `null` 。

 数据存于 `Bundle` 中的键 `result`，类型为 `int`。

| 取得值 | 意义 | 
|---|
| -2 | `Bundle`中的键`packageName`的值为`null` | 
| -1 | **自冻**内部错误 | 
| 0 | 冻结成功 | 
| 998 | 没有找到对应应用 | 
| 999 | 检查发现未解冻，无需冻结 | 

###  进行冻结应用操作 V210.11+

####  请求

| 参数 | 值 | 
|---|
| Uri | `Uri.parse("content://cf.playhi.freezeyou.export.FREEZE")` | 
| Method | `MODE_DPM`、`MODE_ROOT_DISABLE_ENABLE`、`MODE_ROOT_HIDE_UNHIDE`、`MODE_LEGACY_AUTO`、`MODE_SYSTEM_APP_ENABLE_DISABLE`、`MODE_SYSTEM_APP_ENABLE_DISABLE_USER`、`MODE_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED`、`MODE_PROFILE_OWNER`11.3+ | 
| Extras | `Bundle`，键`packageName`必须包含被解冻的应用包名 | 

####  返回

如果对应键值为 `null`，则检查请求时的 `Method` 以及 `Extras` 是否为 `null` 。

 数据存于 `Bundle` 中的键 `result`，类型为 `int`。

| 取得值 | 意义 | 
|---|
| -10 | 配置文件所有者（Profile Owner）模式系统内部错误 | 
| -9 | 不是配置文件所有者（Profile Owner） | 
| -8 | 不是系统应用 | 
| -7 | 没有该冻结解冻模式 | 
| -6 | 无 DPM 权限 | 
| -5 | DPM 模式系统内部错误 | 
| -4 | 无 ROOT 权限 | 
| -3 | 设备 Android 版本过低，不支持该 `Method` | 
| -2 | `Bundle`中的键`packageName`的值为`null` | 
| -1 | **自冻**内部错误 | 
| 0 | 冻结成功 | 
| 1 | 没有发生异常，一般情况下为成功 | 

###  获取是否可通过**自冻**安装应用9.2+

####  请求

| 参数 | 值 | 
|---|
| Uri | `Uri.parse("content://cf.playhi.freezeyou.export.QUERY")` | 
| Method | `QUERY_IF_CAN_INSTALL_APPLICATIONS_STATUS` | 
| Extras | 空 `Bundle` | 

####  返回

如果对应键值为 `null`，则检查请求时的 `Method` 以及 `Extras` 是否为 `null` 。

 数据存于 `Bundle` 中的键 `status`，类型为 `boolean 数组`。

| 取得值 | 意义 | 
|---|
| boolean 数组 | boolean[]{预估功能可用, 安装通道可用, 有ROOT权限, 有DPM权限} | 

##  开发样例

##  疑难解答

###  SecurityException

- 是否已经在 **Manifest**中声明了权限呢（`冻结\解冻应用`还需要类似请求敏感权限一样进行`requestPermissions`

###  Failed to find provider info for ...

```
<manifest>
    ...
    <queries>
        <provider
            android:authorities="cf.playhi.freezeyou.export.ExampleAuthority"
            android:exported="false" />
    </queries>
    ...
</manifest>
```

##  目前局限

- 需要在安装**自冻 FreezeYou**后再安装或更新（覆盖安装）使用相关权限的应用，否则可能会报 Exception （在 Android Google 的文档中有提及需要在请求前安装）。

##  需要帮助

## 6.2 API — через startActivity

#  通过 startActivity 冻结解冻应用程序

##  授权范围

##  如何使用

###  声明权限

- 需要在`AndroidManifest.xml`中声明权限（按需申请）- 获取已冻结应用列表```
<uses-permission android:name="cf.playhi.freezeyou.permission.GET_DISABLED_APPLICATIONS"/>
```
 
- 进行解冻应用```
<uses-permission android:name="cf.playhi.freezeyou.permission.ENABLE_APPLICATIONS"/>
```
 
- 进行冻结应用```
<uses-permission android:name="cf.playhi.freezeyou.permission.DISABLE_APPLICATIONS"/>
```
 
 

##  开发样例

##  疑难解答

###  ActivityNotFoundException

- 已安装的是老版本FreezeYou或未安装FreezeYou

###  SecurityException

- 是否已经在 **Manifest**中声明了权限呢（`冻结\解冻应用`还需要类似请求敏感权限一样进行`requestPermissions`

##  目前局限

- 需要在安装**FreezeYou**后再安装或更新（覆盖安装）使用相关权限的应用，否则可能会报 Exception （在 Android Google 的文档中有提及需要在请求前安装）

##  需要帮助

## 6.3 API — через URI

[#](#通过-uri-冻结解冻应用程序) 通过 URI 冻结解冻应用程序

[#](#授权范围) 授权范围

- 唤起 **FreezeYou**的**冻结/解冻/启动**对话框（出于安全考虑，首次**冻结/解冻/启动**具体操作必须由用户自主完成）。

[#](#如何使用) 如何使用

[#](#嵌入-html) 嵌入 HTML

[#](#请求-冻结-解冻-启动-应用包名) 请求 冻结/解冻/启动 [应用包名]

```
<a href="freezeyou://fuf/?pkgName=[应用包名][&action=[操作]]">请求 冻结/解冻/启动 [应用包名]</a>
```
[#](#请求-冻结-解冻-启动-usim卡应用) 请求 冻结/解冻/启动 USIM卡应用

```
<a href="freezeyou://fuf/?pkgName=com.android.stk">请求 冻结/解冻/启动 USIM卡应用</a>
```
[点此尝试 冻结/解冻/启动 USIM卡应用(com.android.stk)](freezeyou://fuf/?pkgName=com.android.stk)

[#](#请求-冻结-解冻-启动-usim卡应用-1) 请求 冻结/解冻/启动 USIM卡应用 8.3+

```
<a href="freezeyou://fuf/?pkgName=com.android.stk&action=fuf">请求 冻结/解冻/启动 USIM卡应用</a>
```
[点此尝试 冻结/解冻/启动 USIM卡应用(com.android.stk)](freezeyou://fuf/?pkgName=com.android.stk&action=fuf)

[#](#请求-解冻-usim卡应用) 请求 解冻 USIM卡应用 8.3+

```
<a href="freezeyou://fuf/?pkgName=com.android.stk&action=unfreeze">请求 解冻 USIM卡应用</a>
```
[点此尝试 解冻 USIM卡应用(com.android.stk)](freezeyou://fuf/?pkgName=com.android.stk&action=unfreeze)

[#](#请求-冻结-usim卡应用) 请求 冻结 USIM卡应用 8.3+

```
<a href="freezeyou://fuf/?pkgName=com.android.stk&action=freeze">请求 冻结 USIM卡应用</a>
```
[点此尝试 冻结 USIM卡应用(com.android.stk)](freezeyou://fuf/?pkgName=com.android.stk&action=freeze)

[#](#请求-解冻并启动-若已解冻则直接启动-usim卡应用) 请求 解冻并启动(若已解冻则直接启动) USIM卡应用 8.3+

```
<a href="freezeyou://fuf/?pkgName=com.android.stk&action=unFreezeAndRun">请求 解冻并启动(若已解冻则直接启动) USIM卡应用</a>
```
[点此尝试 解冻并启动(若已解冻则直接启动) USIM卡应用(com.android.stk)](freezeyou://fuf/?pkgName=com.android.stk&action=unFreezeAndRun)

[#](#应用间) 应用间

```
Uri webPage = Uri.parse("freezeyou://fuf/?pkgName=" + pkgName);
Intent intent = new Intent(Intent.ACTION_VIEW, webPage);
if (intent.resolveActivity(getPackageManager()) != null) {
   startActivity(intent);
} else {
   Toast.makeText(MainActivity.this, "无可用程序，是否已安装 FreezeYou 7.2 及以上版本呢？", Toast.LENGTH_LONG).show();
}
```

---

## 7. FAQ — повседневное использование

#  日常使用 - 疑难解答

##  点了冻结以后，对应的应用程序在桌面上找不到了

- 是的。正常情况下，无特殊处理的话是会消失的，但不必担心，解冻后对应的应用程序会再次出现的，相关数据**正常情况**下不会丢失。
- 那我怎么方便的打开对应的应用程序？可在 `自冻 FreezeYou`的应用列表中，创建对应应用程序的*桌面冻结解冻启动快捷方式*（建议在冻结前创建）。

##  部分应用解冻不了

- 有些应用是系统（含系统应用）执行了停用（冻结）的，目前在免ROOT模式下无法解冻。
- 免ROOT模式冻结的，在使用 **自冻FreezeYou**以**ROOT模式**时需要选择**HIDE/UNHIDE**模式解冻。

##  桌面为什么有三个图标

- 目前的新版本已经默认只显示一个图标了。
- 早期版本的图标有部分用户反馈夜间有些吓人，因此后来又加入了一个图标，同时又将早期版本的一个针对 Android 7.0 的圆形图标拆出单列，最终就出现了三个图标的壮观景象，不过不必担心，这三个图标可以在`更多设置`中自行选择开关，甚至可以全部关闭——使用拨号盘启动(需拨号软件支持，原生Android默认支持)。

##  我不小心把桌面的三个图标全关了，而我的桌面又不支持暗码启动 FreezeYou

- 啊哈，不必担心，进入`系统设置`，然后在`应用程序`中找到`自冻FreezeYou`，那个熟悉的`清除数据`是不是被`管理空间`吃掉了啊？这就对了，点一下`管理空间`，熟悉的一切就又回来啦！

##  一键锁屏后只能密码解锁了

- 是的，在**部分**设备上（尤其是依托于Google服务实现人脸或指纹解锁的设备上），出于安全因素考虑，系统是强制的使在通过设备管理器锁屏的情况下禁止人脸、指纹、以及SmartLock的（[谷歌官方解释在这里open in new window](https://issuetracker.google.com/issues/37010802#comment110)）。
- 自`5.2`版本开始，已ROOT的设备使用一键锁屏已能够不再影响 Smartlock 。

##  为什么桌面上的快捷方式右下角有个小标志，能不能去掉

- 自 Android 8.0 开始，Android 默认会在应用程序创建的快捷方式右下角加入来源应用程序的图标。
- 自 FreezeYou 5.3 版本开始，可以在桌面的添加小部件（微件）中找到 一键冻结、一键解冻、一键锁屏 ，这个是没有右下角的标志的。
- 自 FreezeYou 7.2 版本开始，可以通过添加桌面小部件（微件），找到 FreezeYou 分组，选择 冻结解冻启动 小部件，这个是没有右下角角标的，创建完成后使用时与创建桌面快捷方式等效。

##  应用列表里右侧的点是什么

- 浅灰色或无色是指该应用未冻结，如果是主题色（比如蓝色），是指对应的应用已经被冻结了。

##  锁屏后一键冻结似乎没有生效

- 是否在`更多设置`中开启了`锁屏后一键冻结`的功能呢？
- 是不是没有在系统或相关的管理软件中给予`自冻FreezeYou`后台服务的白名单呢？
- 推荐通过在`计划任务`中的`触发器任务`中，使用触发器`关闭屏幕时`达到类似效果。

##  离开冻结似乎无效诶

- 是否在`更多设置`中开启了`离开冻结`的功能呢？
- 有没有在系统设置中启用`自冻FreezeYou`的无障碍功能呢？
- 是不是没有在系统或相关的管理软件中给予`自冻FreezeYou`后台服务的白名单呢？

##  应用程序的图标似乎变成透明的了

- 在 Android 8.0 、8.1 的部分设备上发生了这个问题。如何避免呢？不要在应用程序被冻结的时候更新或覆盖安装对应的应用程序即可避免。

##  应用程序的图标变安卓机器人了

- 在 Android 8.0 、8.1 的部分设备上发生了这个问题。如何避免呢？不要在应用程序被冻结的时候更新或覆盖安装对应的应用程序即可避免。

##  **解冻启动** 和 **冻解启动** 有什么区别

- `单击功能`中的- `解冻启动`是解冻后启动（不会冻结），而- `冻解启动`是- `自动冻解`的一个衍生功能，会在解冻时提示或直接启动相应的应用程序。

##  桌面 一键冻结 一键解冻 快捷方式有时会没有效果

- 部分设备系统对应用有极其严格的限制，一般情况下，开启针对`自冻FreezeYou`的电池免优化以及允许自启动后，该问题消失。

##  无法在 自冻FreezeYou 中创建桌面快捷方式

- 是否在权限中允许`自冻FreezeYou`创建快捷方式了呢？
- 可以尝试使用`自冻FreezeYou`的`冻结/解冻/启动`桌面小部件达到同样效果。

##  应用分身、双开似乎用不了了

- 启用免ROOT以后，部分设备可能会出现系统自带的应用分身无法正常使用的情况，如若发生此情况，可以使用其他第三方分身软件代替，暂无已知的更好的解决办法，如有更好的解决方案，欢迎联系我们。

##  我开启了 缓存应用程序图标 但是列表加载仍然很慢

- 开启后第一次载入需要写入缓存，耗时会可能比较长的。
- 如果后续仍然加载时间较长，请注意是否清除了 `自冻(FreezeYou)`的缓存(Cache)数据，图标缓存是保存在缓存(Cache)数据中的，如果被清除，则需要重新生成。
- 如果没有清除缓存数据，也不是第一次载入，那很可能您的设备的小文件读取效率较低，无法快速完成读取，导致载入缓慢。这问题不大，稍稍多等一会儿即可。

##  主界面除了以一行一个的列表的方式展现，还有没有别的

- 可至 `更多设置`-`外观`-`首页格局`，更换，支持列表（默认）、网格。

##  可否跟随系统的暗色模式自动调整我设定的日常配色方案至暗色

- 至 `更多设置`-`外观`，勾选`允许跟随系统自动切换暗色模式`即可。

##  我想移除通知栏里的特定应用的快捷冻结的通知，可我划不掉

- 前往 `更多设置`-`通知栏`-`管理冻结解冻快捷通知`中选中相应的应用程序，移除通知即可。
- 这种情况一般是选中了 `更多设置`-`通知栏`-`冻结与解冻`中的`禁止滑动移除`选项，如不需要，则关闭后下次创建的通知栏快捷操作就可以被滑动移除了。

##  我（不）想让自冻FreezeYou出现在最近应用列表里

- 前往 `更多设置`-`常规`，调整`显示在最近任务列表`的启用与否即可。
- 一般情况下，使用返回键离开 `自冻`时，`显示在最近任务列表`的未启用状态能够得到体现。

##  能不能避免冻结尚有通知存在于通知栏的应用程序

- 至 `更多设置`-`冻结与解冻`，勾选`避免冻结有通知的应用程序`即可。

##  能不能避免冻结正在前台使用的应用程序

- 至 `更多设置`-`冻结与解冻`，勾选`避免冻结前台应用程序`即可。

##  如何管理我的自选

- 前往 `更多设置`-`管理空间`-`管理我的自选`，点击需要管理的自选即可管理。

##  如何卸载

- 如果`自冻`被设定为设备所有者（Device-Owner），首先点击`释放设备所有者（Device-Owner）权限`。
- 如果`自冻`被设定为配置文件所有者（Profile-Owner），首先点击`释放配置文件所有者（Profile-Owner）权限`。
- 然后前往`自冻`-`设置`-`危险区`点击`卸载`。

##  更多疑难解答

##  需要更多帮助

## 7.1 FAQ — без-ROOT режим

#  免ROOT - 疑难解答

##  提示

- 如果想要启用免ROOT，但在来到这里之前还没有尝试过启用免ROOT，建议先前往[启用免ROOT](/zh-CN/guide/enable-mroot.html)。

##  adb server version doesn't match this client

- **(仅限使用Windows系统的用户)**把解压后得到的adb文件，删去占用空间较大的那个(约2544KB)，再试试？
- 是否有PC端手机助手类软件占用了相关端口呢？可以先退出相关助手类软件（结束其PC端后台，含衍生内容）。

##  error: device unauthorized

##  java.lang.IllegalStateException: ... there are already several accounts ...

- 请检查您系统设置中账户中的账户是否全部删除了(需要全部删除，免ROOT启用后，可以再手动加回去)(实在删不掉的账户，可以试试断开网络连接以后删除，还不行的话可以试试先备份相关的应用数据以后卸载相关应用，成功后再恢复备份)，如果不清楚是哪个应用程序的账户没有清除干净，请在`“正在尝试启用免ROOT模式……”`上方，寻找`“当前设备账户信息：”`在`Accounts`组中，检查`type=`这一项，`=`后面的是应用程序包名，然后在**自冻 FreezeYou**列表中寻找该包名对应的程序即可获知账户来自哪个应用程序。
- 也可以尝试重启至**安全模式**（调出关机界面后，长按显示的“关机”按钮），再次尝试进行激活。

##  java.lang.IllegalStateException: ... there are already several users ...

- 请检查您系统设置中可见的其他用户是否已经删除了、分身应用是否已经关闭了(部分分身的实现利用的是 Android 自带的多用户功能，会影响免ROOT的启用)，如果还是失败，可以尝试 `adb shell pm remove-user [USER_ID]`该操作可能导致系统自带的分身功能无法正常使用。

##  java.lang.SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_DEVICE_ADMINS

##  好麻烦，有没有简单点的

##  激活以后，USB 调试之类的可以关掉吗？

- 正常情况下是可以的（目前还没有收到关闭后失效或无法关闭的情况），同时为了安全考虑，也建议激活完成后`关闭 USB 调试`。

##  启用以后能否卸载？

- 可以卸载，但可能需要先到 `更多设置`→`危险区`点击`解除免ROOT`，然后正常卸载即可。

##  更多疑难解答

##  需要更多帮助

## 7.2 FAQ — плановые задачи

#  计划任务 - 疑难解答

##  似乎计划任务没有生效

- `自冻(FreezeYou)`是否是最新版本呢？
- 是否将 `自冻(FreezeYou)`加入了系统的后台白名单呢？（包括但不限于允许后台运行、忽略电池优化）
- 是不是遗漏了任务中的空格呢？
- 是不是多打了任务中的空格呢？
- 逗号需要英文逗号 `,`，不要打成中文的啦！
- 注意大小写哈！

##  为什么有时执行的时间会稍晚些

- 自 Android 6.0 开始，Google 引入了新的省电模式，将应用程序们的一些任务整理到了一起执行，以期减少电量消耗，因此会导致一些任务的延迟执行。
- 由于毒瘤们一般都会高频甚至持续唤醒系统，因此在毒瘤们运行时， `自冻FreezeYou`的计划任务一般是能够按时执行的。

##  手打包名好麻烦

- 在 **应用列表**中，点击对应应用（默认情况下），会出现选择操作的界面，其中有`复制包名`的功能。
- 在 **应用列表**中，多选模式下（长按），可以在菜单中选择**格式化后复制**，然后再到需要输入的地方粘贴即可。6.7+

##  没有按照计划开启 Wi-Fi 啊

- 请查阅 **没有按照计划开启 Wi-Fi 啊**.
- 请检查是否开启了飞行模式。在部分设备上，飞行模式已开启的情况下，`自冻`无法打开或关闭 Wi-Fi，关闭飞行模式即可。

##  没有按照计划开启蜂窝移动数据网络啊

- 蜂窝移动数据网络 的开启目前仅支持在 `Android 4.4 及以下`或`具有 ROOT 权限`或`自冻FreezeYou 已被安装为高权限系统应用`的设备上使用。
- 检查是否授权 `开关移动网络`权限。

##  如何获得我的自选中的别名

- 请至 `更多设置`-`管理空间`-`管理我的自选`中点击相应的项，然后点击**复制别名**即可。

##  更多疑难解答

##  需要更多帮助

---

## 8. Changelog (историческая часть, с содержательными записями)

[#](#更新日志) 更新日志

[#](#v11) V11

[#](#v11-5-2022-05-30) V11.5(2022.05.30)

- 【新增】在 Android 12 及以上部分设备中，默认界面风格情况下，部分内容以 Material 3 呈现；
- 【优化】Shizuku 相关模式的部分处理；
- 【合并】合入一些翻译（感谢 [@tommynok](https://github.com/tommynok)）；
- 【更多】一些细节调整与优化。

[#](#v11-4-2022-05-16) V11.4(2022.05.16)

- 【新增】更多设置 - 冻结与解冻 - 播放冻结与解冻动画 （感谢 **@下 1.站、幸福**）；
- 【新增】更多设置 - 常规 - 在最近任务中包括冻结与解冻活动（感谢 **@下 1.站、幸福**）；
- 【调整】为避免在部分定制系统中，关闭所有三个图标后，通过拨号盘无法正常唤起软件，同时也无法于系统设置中找到“应用设定”以重新进入设置，导致无法正常使用软件，重新上线替换 `清除数据`按钮的`管理空间`。
- 【合并】合入一些翻译（感谢 [@tommynok](https://github.com/tommynok)）。

[#](#v11-3-2022-05-14) V11.3(2022.05.14)

- 【新增】Shizuku （DISABLE_USER）等模式（感谢 [@Droidphilev](https://github.com/Droidphilev)的[Issue #206](https://github.com/FreezeYou/FreezeYou/issues/206)）；
- 【新增】桌面快捷方式冻结解冻等部分情况下展示动画；
- 【新增】长按浮动按钮时的透明度变化（感谢 [@littlepony0](https://github.com/littlepony0)的[Issue #185](https://github.com/FreezeYou/FreezeYou/issues/185)）；
- 【修复】部分设备部分情况下桌面快捷冻结解冻无法在首次解冻时自行启动应用；
- 【合并】合入一些翻译（感谢 [@tommynok](https://github.com/tommynok)）；
- 【更多】一些细节调整与优化。

[#](#v11-2-2022-04-09) V11.2(2022.04.09)

- 【变更】移除 Google Play Core。(感谢 [IzzySoft](https://github.com/IzzySoft)的[Issue #216](https://github.com/FreezeYou/FreezeYou/issues/216))

[#](#v11-1-2022-04-08) V11.1(2022.04.08)

- 【新增】支持 配置文件所有者（Profile Owner） 模式；
- 【优化】备份与恢复支持更多配置项的导入导出；
- 【更多】细节优化与性能增强。

[#](#v11-0-2022-02-06) V11.0(2022.02.06)

- 【新增】创建冻结解冻桌面快捷方式 - 目标 中，选择目标时会显示相应的标题；
- 【新增】更多设置 - 外观 - 界面风格 - 黑色（与已存在的暗色相比更黑）；
- 【新增】更多设置 - 自动切换暗色模式时的界面风格 - 暗色、黑色；
- 【新增】查看模式 - 未冻用应（未冻结的用户应用）（感谢 **shunf4**的**PR #180**）；
- 【新增】排序方式 - 字母顺序、最近安装、最近更新（感谢 **shunf4**的**PR #180**）；
- 【新增】更多设置 - 安全 - 启用身份验证；
- 【新增】强行停止（感谢 **shunf4**的**PR #180**）；
- 【新增】快捷方式 - 强行停止；
- 【新增】快捷方式 - 快捷查看 - 系统预装、用户应用、未冻用应、我的自选（感谢 **shunf4**的**PR #180**）；
- 【优化】自检诊断的流程、稳定性与展示效果等；
- 【新增】自检诊断 - 刷新、帮助按钮；
- 【调整】自检诊断 - 未忽略电池优化 中，点击后直接唤起系统弹窗选择是否忽略而非跳转至系统设置手动找寻 `FreezeYou`后再点击忽略；
- 【调整】不再显示列表的分割线，且不设开关；
- 【修复】一键冻结、一键解冻对全部冻结解冻模式的支持；
- 【调整】移除 更多设置 - 外观 - 单击功能选择操作风格；
- 【新增】支持以包名搜索应用（感谢 **delphinuz**的**Issue #179**）；
- 【新增】更新与添加一些翻译（感谢 **tommynok**）；
- 【新增】启动画面（Splash Screen），仅 Android 12 （API 31）及更高版本设备中；
- 【优化】身份验证中一些导致响应迟钝的问题；
- 【调整】空间充足的情况下，菜单中的 查看模式 会以图标形式显示在 计划任务 按钮的边上，而不是折叠在菜单栏中（感谢 **littlepony0**的**Issue #185**）；
- 【新增】单应用的选项菜单中增加 转至市场；
- 【更多】一些细节与稳定性等优化。

[#](#v10-16-pre3-2022-01-19) V10.16-pre3(2022.01.19)

**测试版，很可能存在各种不稳定情况**

- 【新增】启动画面（Splash Screen），仅 Android 12 （API 31）及更高版本设备中；
- 【优化】身份验证中一些导致响应迟钝的问题。

[#](#v10-16-pre2-2021-12-23) V10.16-pre2(2021.12.23)

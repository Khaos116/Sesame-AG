# 主页编译时间展示设计

## 目标

在主页顶部直接展示当前 APK 的精确编译时间，方便区分真机安装的测试包。

## 界面

- 展示位置：主页“本应用开源免费，严禁倒卖”文字正下方、官方签名标识之前。
- 展示内容：`编译时间：yyyy-MM-dd HH:mm:ss`。
- 字体：使用 `MaterialTheme.typography.labelSmall`，比现有警示文字的 `titleSmall` 更小。
- 颜色：使用主题主色降低透明度得到淡蓝色效果，同时适配明暗主题。
- 间距：与警示文字保持少量顶部间距，不新增卡片或交互。

## 数据与实现范围

- 直接读取现有 `BuildConfig.BUILD_DATE` 和 `BuildConfig.BUILD_TIME`，两者已在 Gradle 中按 GMT+8 生成。
- 只修改主页 `HomeContent`，不增加 ViewModel 状态、运行时格式化或新组件。
- 在 `MyFix.md` 记录该本地定制，避免后续合并上游时丢失。

## 验证

- Debug/Release 均能编译。
- 主页显示的日期与时间来自对应 APK 的 `BuildConfig`。
- 原有警示、官方签名、模块状态和任务功能不受影响。

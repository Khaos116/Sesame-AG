# khaos116 本地定制维护说明

> 本文档用于上游代码更新并合并到 `my_dev` 后，指导 AI 恢复本分支的长期定制。
> 只处理作者为 `khaos116 <khaos116@qq.com>` 的修改；不要把上游提交、其他作者提交或 merge commit 当成本地定制。

## 1. 执行原则

1. 先完整保留并理解上游的新实现，再迁移本文列出的“行为目标”；不要直接用旧文件覆盖新文件。
2. 按类名、方法名和业务流程定位代码，不依赖本文记录时的行号。上游若已重构或删除文件，应把行为迁移到新的等价入口。
3. 上游已经提供等价能力时直接复用，只补缺失部分，避免恢复重复实现。
4. 不恢复本文“历史遗留”章节中的死代码。
5. 合并完成后必须检查 Git 差异并编译；涉及每日状态和 RPC 拦截的逻辑还要做最小行为验证。

用于核对作者提交的命令：

```bash
git log --no-merges --author="khaos116 <khaos116@qq.com>" --oneline <上游分支>..my_dev
git diff <上游分支>...my_dev
```

本文最后一次整理时的参考状态：`my_dev=f53fd257`，当时与 `origin/dev` 的 merge-base 为 `708263e3`。这些提交号只用于追溯，不应作为以后合并的固定基线。

## 2. 必须保留的定制

### 2.1 时间统一为 GMT+8

目的：设备位于非东八区时，每日重置、定时任务、签到窗口和统计日期仍按 GMT+8 计算。

核心实现：

- 保留 `util/MyUtils.kt` 中的 `MyUtils.getInstance()`。
- 该方法返回 `Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))`。
- 时间敏感代码不要直接使用系统默认时区的 `Calendar.getInstance()`。

当前需要保持该语义的文件/流程：

- `data/Statistics.kt`：统计初始化、重置和保存日期。
- `data/Status.kt`：当天零点、跨日检查和默认保存时间。
- `hook/ApplicationHook.kt`：日期状态、跨日更新、午夜任务和唤醒时间。
- `model/modelFieldExt/TimeModelField.kt`：时间点和日终截止判断。
- `util/TimeUtil.kt`：Calendar 构造、今日零点、日期格式化和周数。
- `util/TimeTrigger.kt`：当天秒数及当天触发时间换算。
- `util/Logback.kt`：下一次午夜日志切换时间。
- `entity/FriendWatch.kt`：好友数据是否跨日；若上游已删除该文件，迁移到新的好友刷新判断处，不要恢复旧文件。
- `task/EcoProtection/EcoProtection.kt`：星期判断。
- `task/antFarm/AntFarm.kt`、`AntFarmFamily.kt`：睡眠/唤醒、家庭时间段和早安消息窗口。
- `task/antForest/Privilege.kt`：签到时间；若上游已删除该文件，在新的签到流程中保留 GMT+8 语义。
- `task/antForest/RebornEnergyWeeklyPersistence.kt`：每周起始时间。
- `task/antOrchard/AntOrchard.kt`：7 点后的奖励领取。

上游目前存在 `LocaleSettingsApplier`，在简体中文模式下会把默认时区设为 `Asia/Shanghai`。合并时先检查它是否已经无条件保证业务时区；如果仍受语言开关控制，就继续保留上述显式 GMT+8 处理。不要仅因存在该类就删除本定制。

验证：把系统时区临时设为非 GMT+8，确认“今日”、午夜重置、签到/领奖窗口仍按北京时间计算。

### 2.2 按账号、按天停止重复异常 RPC

目的：某个账号的请求当天出现明确风控或服务异常后，当天不再反复请求；第二天自动恢复，账号之间互不影响。

核心文件：

- `util/MyDailySharedPreferences.kt`
  - SharedPreferences 文件名包含 UID。
  - 保存 GMT+8 的 `yyyyMMdd` 日期。
  - 每次读写前检查跨日，跨日后清空该账号的临时状态。
- `util/MyUtils.kt`
  - 缓存每个 UID 对应的 `DailySharedPreferences`。
  - 识别 `error/errorTip == 1009`，以及“验证后继续”“系统繁忙”“已经签到”“操作存在异常”“系统出错”等响应。
  - 提供模块级每日开关和 RPC 级每日异常查询/记录。
- RPC 总入口（当前为 `hook/rpc/bridge/AriverRpcBridge.kt`）
  - 请求前调用每日异常查询；命中时不再发起真实请求，并返回本地合成的错误响应。
  - 收到异常响应后记录当天状态。
  - 上游若再次拆分 RPC bridge，应把检查放到所有请求最终都会经过的共享入口，不要分别散落复制。

特殊规则：

- 请求方法包含 `.antfarm.` 时，不启用全局 RPC 当日拦截，避免影响喂鸡。
- `GreenFinance.kt` 使用 `CHANGE_KT1`：绿色经营出现指定异常后当天停止。
- `AntSports.kt` 使用 `CHANGE_KT2`：健康岛泡泡查询出现指定异常后当天停止。
- `AntFarm.kt` 的任务终态增加每日重试计数，避免一次未知状态就永久停止，同时限制重复执行次数。

验证至少覆盖：同账号同请求第二次被拦截、不同账号不互相影响、跨日后恢复、`.antfarm.` 请求不被全局拦截。

### 2.3 自动接受当前版本法律声明

目的：更新版本后不再重复弹出 LICENSE/法律声明确认。

- `MyUtils.自动同意LICENSE()` 返回 `true`。
- `data/Config.kt` 中以下流程都应把该值视为已接受：
  - 当前配置是否接受；
  - 更新当前配置接受状态；
  - 从指定用户配置读取接受状态；
  - 向指定用户配置写入接受状态。

如果上游重构了法律确认存储，只保留“自动视为当前版本已接受”的行为，不恢复旧版 `Config` 实现。

### 2.4 Gemini 答题定制

目的：使用能够正常返回答案的 Gemini 请求实现，并适配选择题。

当前行为：

- 运行实现是 `task/AnswerAI/GeminiAI.java`，实现 `AnswerAIInterface`。
- 请求地址以 `https://api.genai.gd.edu.kg/google` 为基础。
- 模型使用 `gemini-2.5-flash`。
- Prompt 要求只返回答案文字，不解释、不带标点。
- 请求启用 `google_search`，用于较新的常识题。
- 从 `candidates[0].content.parts[0].text` 取答案，清理标点后匹配候选项。
- `serve-debug/webui.py` 中 Gemini Token 字段文案同步为 `gemini-2.5-flash`。

合并时优先适配上游最新 `AnswerAIInterface`，不要为了保留旧 Java 文件而回退接口。严禁恢复“把 Gemini token 写入日志”的行为。

### 2.5 私有签名与 APK 归档

目的：本地 release 构建使用固定签名，并在构建后自动归档 arm64 APK。

- 保留项目根目录的 `xqe.jks`，并让 `app/build.gradle.kts` 的签名配置继续引用它。
- 签名参数沿用当前本地配置；不要把密码复制到本文档、日志或新的源码位置。
- `assembleRelease` 完成后，将 `app-arm64-v8a-release.apk` 复制到 `APK/Release/`。
- 文件名格式：`XQE_AG_<appVersion>_<GMT+8 yyyyMMdd_HHmm>.apk`。
- 源 APK 不存在时直接跳过复制，不让构建失败。

上游改变 variant、APK 名称或切换 Android Gradle Plugin 后，应适配新的构建产物 API，不要硬恢复已经失效的旧路径。

### 2.6 少量日志可读性调整

- `AntFarm.runSuspend()` 开始日志使用 `🟢`，结束日志使用 `🟥`。
- 这只是显示定制；若上游已有统一结构化日志，可不恢复重复日志。

## 3. 历史遗留：不要机械恢复

- `GeminiAI2.kt` 全文件已注释，不参与运行；不要在新版本重新添加。
- `MyUtils.kt` 中未使用的 `CHANGE_KT3`～`CHANGE_KT30` 占位注释不需要恢复。
- `MyUtils.是否开启绿色绿色经营()` 当前没有调用方，不需要恢复，除非以后明确启用该账号白名单。
- 早期提交修改过 `NewRpcBridge.kt`、`OldRpcBridge.kt`，当前有效行为已经迁移到 `AriverRpcBridge.kt`；以后继续找共享 RPC 入口，不要恢复旧 bridge 文件。
- 早期提交直接修改过 `GeminiAI.kt` 和 `AnswerAI.kt`，后来已由 Java 版实现取代；不要同时保留多套同名实现。
- 某些早期 `AntForest.kt` 的时区替换在当前分支已不再存在。合并后只检查实际仍然依赖本地时区的森林流程，不要全文件盲目替换。

## 4. 已修正的迁移问题：后续不要回退

以下问题已在合并 `dev@17099897` 时修正，后续迁移必须保留修正后的实现：

1. RPC 每日状态使用稳定的 SHA-256 摘要作为 SharedPreferences key；不要改回带随机 IV 的 `MyCryptoUtils.encrypt()`。
2. `GreenFinance.kt` 使用 `submitTick` 的真实响应 `obj` 判断并记录异常，不要传任务项 JSON。
3. `AntFarm.kt` 使用 `count >= 5` 限制最多 5 次，不要改回 `count > 5`。
4. `GeminiAI.java` 不记录 token，只记录请求结果或脱敏信息。
5. 签名文件和密码属于敏感资产。若仓库将公开，优先迁移到本地属性或 CI secret；本文不保存具体密码。

## 5. 已知高冲突区域

在本文整理时，较新的 `origin/dev` 已同时修改以下本地定制文件，后续合并要按语义处理冲突：

- `app/build.gradle.kts`
- `data/Config.kt`、`data/Status.kt`
- `hook/ApplicationHook.kt`
- `hook/rpc/bridge/AriverRpcBridge.kt`
- `model/modelFieldExt/TimeModelField.kt`
- `task/EcoProtection/EcoProtection.kt`
- `task/antFarm/AntFarm.kt`、`AntFarmFamily.kt`
- `task/antForest/RebornEnergyWeeklyPersistence.kt`
- `task/antOrchard/AntOrchard.kt`
- `task/antSports/AntSports.kt`
- `util/Logback.kt`、`TimeUtil.kt`
- `serve-debug/webui.py`

`FriendWatch.kt` 和 `Privilege.kt` 在当时的新上游中已删除。遇到 delete/modify 冲突时接受上游删除，再把必要的 GMT+8 行为迁移到新流程；不要为了消除冲突恢复整份旧文件。

## 6. 合并后的 AI 检查清单

- [ ] 只分析并恢复 `khaos116 <khaos116@qq.com>` 的定制。
- [ ] 上游代码先保留，所有修改按新结构做最小适配。
- [ ] GMT+8 的每日边界、定时窗口和统计逻辑仍成立。
- [ ] 每日异常状态按 UID 隔离、跨日清理，且 RPC key 可稳定复现。
- [ ] 全局 RPC 拦截位于共享入口，`.antfarm.` 明确豁免。
- [ ] 绿色经营和健康岛使用真实响应判断异常。
- [ ] 法律声明自动接受仍覆盖读取和写入路径。
- [ ] Gemini 使用目标模型且不记录 token。
- [ ] release 签名和 arm64 APK 归档仍可用，敏感值没有新增泄露。
- [ ] 不恢复注释版 Gemini、占位常量、无调用函数和已删除旧 bridge。
- [ ] 运行 `git diff --check`。
- [ ] 运行 `./gradlew :app:compileDebugKotlin`；构建链允许时再运行 release/assemble 验证。

## 7. 必须保留的定制：FPA 免 Root 支持

目的：让本分支使用 libxposed API 102 在 FPA 中免 Root 运行，同时保留 LSPosed API 102 的现有路径。这里的补丁兼容只针对 FPA，不把 LSPatch、NPatch 或未知框架自动视为受支持环境。

回归来源：`a7f3e6ce` 开始明确拒绝内置补丁运行时，`256e9c6d` 又删除旧入口并把运行范围收紧到 LSPosed API 102，后续 `17099897` 增加的模块身份回查也不适用于没有独立模块包的嵌入环境。

FPA 3.8 实测依据（APK：`C:\Users\USER\Desktop\FPA3.8.Apk`）：

- APK 内置 `extra/xp89.dex`、`xp100.dex`、`xp101.dex` 和 `xp102.dex`。它读取模块的 `module.prop`，目标 API >= 102 时会选择 `xp102`，所以本项目不需要降级到 API 100。
- FPA 的 API 102 接口声明 `API_102=102`、`LIB_API=102`，`getApiVersion()` 返回 102，框架名称返回 `FPA`。
- FPA API 102 支持项目使用的 Hook、原方法调用、反优化、日志和远程配置能力；仅 `hookClassInitializer` 未实现，而本项目没有调用它。

合并上游后必须保留：

- `libs.versions.toml` 中 `xposed-api=102.0.0`，`app` 继续 `compileOnly(libs.libxposed.api)`。不要恢复 `api-100.aar` 或 API 82 依赖，也不要把框架 API 打进 APK。
- `META-INF/xposed/module.prop` 的 `minApiVersion`、`targetApiVersion` 均为 102；入口只保留 `META-INF/xposed/java_init.list`。**不要添加 `assets/xposed_init`**，它是 API 100 以下的 legacy 入口。
- `HookEntry` 使用 API 102 的无参构造、`onModuleLoaded` 和 `onPackageReady`；`LibXposedRuntime` 与 `ApplicationHook` 直接使用同一套 API 102 接口，不增加 API 100 适配层。
- `ModuleStatus.isSupportedHookRuntime()` 仅精确放行 API >= 102 的 `LSPosed` 与 `FPA`。LSPosed 服务状态仍使用 `isSupportedLsposedFramework()`，不要把服务连接与 FPA Hook 身份混为一谈。
- 目标包、主进程、Android 主用户、UID、sourceDir 和 FPA 提供的 `moduleApplicationInfo` 校验必须保留。仅 FPA 补丁环境可以跳过 PackageManager 对“独立安装模块包”的二次回查，因为补丁 APK 不保证存在独立模块安装记录。
- `WorkflowRootGuard` 仅在 Hook 已成功安装且运行时通过统一准入判断后，把 FPA 注入视为执行权限；否则仍进行真实 Root 探测。
- FPA 3.8 声明远程配置能力，继续通过 libxposed API 102 的 `remotePreferences` 加载模块配置。
- `MyUtils.CHANGE_KT3` 是该定制的代码跳转标记，没有业务含义。所有 FPA 关键修改处应继续引用它，并保留说明性注释。

验证：

- [ ] APK 包含 `META-INF/xposed/java_init.list`，`module.prop` 的最低/目标 API 均为 102，且不包含 `assets/xposed_init`。
- [ ] 无 Root + FPA：进入 `Application.attach`、完成 Hook、加载配置并执行一次手动任务。
- [ ] FPA API 101、未知运行时、非目标包、非主进程和 Android 非主用户仍被拒绝。
- [ ] LSPosed API 102 的现有运行路径无回归。
- [ ] 有 Root、无受支持 Hook 时的 Root fallback 保持原样。
- [ ] 单账号和多账号继续受 `AccountSlotRegistry` 与会话 UID 一致性保护。
- [ ] 运行 `:app:testDebugUnitTest`、`:app:compileDebugKotlin` 和 `git diff --check`，再做 FPA 真机验证。

## 8. 必须保留的仓库换行约定

- 保留根目录 `.gitattributes`：普通文本统一使用 LF，确保 Windows、macOS 和 Linux 检出一致。
- Windows 专用的 `.bat`、`.cmd` 使用 CRLF；`gradlew`、`.sh` 必须保持 LF，否则 macOS/Linux 可能因 `\r` 无法执行。
- AAR、APK、JAR、SO、图片和签名文件按二进制处理，禁止 Git 做换行转换。
- 若上游合并修改了换行规则，应把纯换行归一化做成独立改动，避免与业务代码差异混在一起。

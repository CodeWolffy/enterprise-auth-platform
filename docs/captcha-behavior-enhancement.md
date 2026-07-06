# 行为验证码增强 —— 实施方案与落地记录

> 版本：2026-07-06　范围：登录验证码（tianai-captcha 1.5.5）
> 目标：把当前"滑块**位置**校验"升级为真正的"**行为**校验"，并接入文字点选做**失败风险升级**。

---

## 一、背景与问题诊断

改造前现状：

| 层 | 现状 | 问题 |
|---|---|---|
| 后端 | tianai `SLIDER` 单类型，默认 `SimpleImageCaptchaValidator` | **只校滑块落点，不校行为轨迹**（源码注释原文："只校验用户是否滑动到缺口处，不校验行为轨迹"） |
| 前端 | 自写滑块组件采集 (x,y,t) 轨迹 | `normalizeTrack` 最后一步把整条轨迹**端到端线性重采样成匀速直线**，人类行为特征被彻底抹平 |
| 结论 | — | 两端叠加 = 一个防不住脚本的"滑块位置校验"，非行为验证码 |

## 二、改造目标（推荐方案）

1. **前端**：提交真实人类轨迹（去掉直线重采样）。
2. **后端**：启用行为轨迹校验器。
3. **多类型**：接入 `WORD_IMAGE_CLICK` 文字点选。
4. **风险升级**：同一账号累计登录失败达阈值后，验证码由滑块**自动升级为文字点选**（打码成本更高）。

## 三、后端改动

### 3.1 启用行为校验器 + 注册点选资源
`CaptchaResourceConfiguration.java`
- 新增 `@Bean ImageCaptchaValidator → BasicCaptchaTrackValidator`，覆盖 starter 默认的 `SimpleImageCaptchaValidator`（默认 Bean 是 `@ConditionalOnMissingBean`，自定义即生效）。
  - 该校验器在"落点容差"之外增加 7 项行为检测：滑动耗时 <300ms、轨迹点数异常、起点偏移过大、Y 轴恒定（机器）、相邻跳变 >50px、匀速（非前快后慢）等。
  - 点选类型 tianai 暂未实现轨迹行为检测，自动回退为位置校验。
- 背景图（`1/2/3.png`）**同时注册给 `SLIDER` 与 `WORD_IMAGE_CLICK`**。文字点选所需字体 `SIMSUN.TTC` 由 tianai 默认资源自动加载，无需额外配置。

### 3.2 验证码支持多类型 + 风险升级
`CaptchaService.java`
- `create(String type)`：按类型生成（`generate()` 泛化，不再硬编码 SLIDER）。
- `createForLogin(tenantId, username)`：按账号近期失败数决定类型——`>= ESCALATE_AFTER_FAILURES(2)` 下发 `WORD_IMAGE_CLICK`，否则 `SLIDER`。
- `CaptchaChallenge` 记录新增 `type` 字段下发前端。
- 校验路径 `verify()` 无需改动：`matching(id, track)` 对滑块/点选通用，`trackList` 同时承载滑动轨迹与点击坐标。

`LoginAttemptService.java`
- 新增 `currentFailures(tenantId, username)`：读取失败窗口内计数（不消耗），供升级判定。

`AuthController.java`
- `GET /api/auth/captcha` 增加可选参数 `username`，租户取 `TenantContext`（由 `TenantFilter` 从请求头注入），调用 `createForLogin`。

## 四、前端改动

### 4.1 修复轨迹拉直（核心）
`components/slider-captcha/index.vue` → `normalizeTrack`
- **删除**端到端线性重采样（原会把轨迹变成匀速直线）。
- 保留"大间隔填充 + 点数不足二次细分 + 上限抽稀"，插值点 t 按真实采样线性推算，**完整保留真实速度/加速度曲线**——这正是后端行为判定的依据。

### 4.2 文字点选组件
`components/slider-captcha/point-captcha.vue`（新增）
- 展示文字底图（点击目标）+ 提示图（按序待点文字），支持序号标记、重置、确认、刷新。
- 提交：点击坐标缩放到**原图像素**，`trackList` 每点 `type:'CLICK'`，携带 `startTime/stopTime/t`。契合后端 `doValidClickCaptcha`（按 `type==='CLICK'` 过滤、按顺序匹配、点数需等于文字数）。

### 4.3 登录页按类型渲染
`views/_core/authentication/login.vue`
- 拉验证码时传 `username`；据返回 `type` 渲染 `SliderCaptcha` 或 `PointCaptcha`；`handleVerify` 两类共用（载荷 JSON 化后走同一 `verifyCaptchaApi`）。

`api/core/auth.ts` `getCaptchaApi(username?)`；`types.ts` 增补 `CaptchaType` 与点选契约。

## 五、验证情况

| 项 | 状态 |
|---|---|
| 后端 `mvn -o test-compile`（主+测试） | ✅ 通过 |
| 后端 `CaptchaServiceRedisTest` 单测 | ✅ 通过 |
| 前端 `vue-tsc` 类型检查（@vben/web-ele） | ✅ 通过 |
| 受影响单测同步修正（构造函数/DTO 字段/mock） | ✅ 完成 |

## 六、待运行时冒烟验证（本地需 Redis/MySQL + 前后端联调）

1. 正常登录：滑块生成/拖动/校验/二次令牌复用链路。
2. 连续失败 ≥2 次后再取验证码，应下发**文字点选**；点选校验通过后可登录。
3. 行为校验负例：脚本提交匀速直线/超快滑动应被拒（返回 `basic check fail`）。
4. 文字点选底图/字体渲染是否清晰（首次接入 `WORD_IMAGE_CLICK`）。

## 七、遗留与后续建议

- `SliderCaptchaResponse.java` 改造后已无引用（死代码），可后续清理。
- 升级判定按 (租户, 用户名) 维度；多租户同名冲突场景会退化为滑块（该场景登录本就抛 `USERNAME_CONFLICT`，可接受）。
- 可选增强：点选顺序错误的更友好提示、验证码资源图库扩充、注册/改密接入验证码。

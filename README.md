## 说明

Reimagined YSM 是一款基于 [Yes Steve Model](https://modrinth.com/mod/yes-steve-model) 的模组，它修改了原版玩家模型，其核心使用 [GeckoLib](https://github.com/bernie-g/geckolib) 库，并采用了 Minecraft 基岩版的模型和动画文件。这使得玩家可以根据自己的喜好自定义玩家模型和动画。

本项目使用可选的C++库实现更快速的渲染，项目位于[OpenYSMDev/openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp)

本项目基于O5penYSM，目标是提供一个在多人模式下优化了网络传输方式，并可以纯客户端模式运行的替代品。

本人不支持，不认可OpenYSM开发组反编译闭源项目并以MIT协议开源的行为。拿着反编译的产物作为把柄要挟别人，这并非开源精神，而是绝对可耻的，对开源精神的亵渎。

本人始终尊重YSM模组开发团队及其社区的模型作者的劳动成果。没有他们的劳动就没有YSM如今的辉煌。

此项目诞生的原因仅仅是我受不了YSM没有官方支持的纯客户端模式与模型下载传输优化。

Reimagined YSM 仅维护简体中文（`zh_cn`）和英语（`en_us`）两种语言。

## 与原版的区别

### 官方 YSM 服务端兼容

本模组客户端兼容官方YSM2.6.x版本的传输协议。客户端在YSM2.6.x服务器时本模组的网络包行为将与官方YSM一致。

因网络传输相关优化的实现方式限制，暂不支持官方版YSM加入安装本模组的服务器。

### 模型按需传输

联机时先同步模型名称、哈希、分类和搜索所需的轻量目录。只有当前页实际显示的模型卡片，以及场景中实际使用的玩家模型，才请求完整模型数据。仅新显示的卡片触发按需加载；加载完成后直接使用原生动态模型预览。

服务端 `config/yes_steve_model-common.toml` 的 `[server_scheduler] BandwidthLimit` 控制每位玩家的模型下载速率，默认 `36` Mbps（4.5 MB/s，相当于基本上没有限速），范围 `1–999`。可通过管理员服务端配置面板调整，即时生效。每位玩家独立计速，同一玩家的并发模型请求共享额度。

### 纯客户端模型

把模型文件夹（包含 `ysm.json`）、`.ysm` 或 `.zip` 放入 `config/yes_steve_model/client/`，例如 `client/my_model/ysm.json`。进入世界时独立扫描，在选择列表中以 `client/…` 名称显示。这些模型在多人模式下仅自己可见。

可在配置启用纯客户端模式，启用后本地的一切模型均将在客户端模式下运行。

## 物理组实现

### 组名与参数

```text
ysmPhys@hair
ysmPhys#r=1.0,g=9.8@hair
ysmPhys#r=2, g=0, s=30, a=60, c=0.75@ribbon
ysmPhys#bc=true@tail
```

大小写敏感，`@` 分隔参数与非空名称，不支持多个 `@`。参数之间允许空白。

### 显式身体碰撞与限制

只有名称为 `ysmPhysC@非空名称` 的组**直接包含的方块**才是碰撞体。普通组及普通子组中的方块完全不参与；想让多个子组参与，就分别添加该前缀。名称大小写敏感，不接受参数或多个 `@`。例如：

```text
Body
├─ 普通身体方块（不碰撞）
└─ ysmPhysC@chest
   └─ 胸部碰撞方块
```

### 参数表格

| 参数 | 含义 | 默认值 | 范围 |
|---|---|---|---|
| `r` | 速度衰减系数；每个子步乘以 `exp(-r × dt)`，越大阻尼越强 | 1.0 | ≥ 0 |
| `g` | 沿世界向下的加速度，方块/秒²；负数向上 | 9.8 | 有限数值 |
| `s` | 回到当前动画目标位置的回弹强度，秒⁻² | 20 | ≥ 0 |
| `a` | 相对动画姿态允许的最大偏转角，度 | 90 | 0–180 |
| `c` | 单个骨骼的碰撞胶囊体半径，模型像素 | 0.5 | ≥ 0 |
| `air` | 静止世界空气的二次阻力系数，方块⁻¹；0 关闭 | 0.5 | ≥ 0 |
| `bc` | 与世界方块的实际碰撞形状交互 | false | `true` / `false` |

### 链条、分叉与末端

```text
Head
└─ ysmPhys@hair
   └─ ysmPhys#r=2@tip
      └─ ysmPhysEnd@hair_end
```

## 新增模组联动

### 诡厄巫法 / 诡厄巫法：启示录 饰品叠加层（仅Forge）

支持 Goety 的 `unholy_hat`、`unholy_hat_halo`，以及 GR 的 `ascension_halo`、`broken_halo`、`halo_of_the_end`。

可添加特殊定位组 `HaloLocator`（不洁圣冠、破碎之环、晋升之环）和 `OdamaneHaloLocator`（终末之环）。饰品使用定位组的枢轴位置、旋转、缩放，并继承全部父级变换。缺少对应定位组时，前三者沿用头部定位，终末之环沿用身体主骨骼。

模型动画、控制器或 Molang 函数中通过 `ysm.has_any_curios` 检测如上某个饰品 ID 时，该饰品将交由模型自行显示；标签查询 `has_any_curios_with_all_tags` / `has_any_curios_with_any_tag` 同样参与避让。

同时安装 showmeurhalo 时关闭其反射式 YSM 头部适配，并跳过它对已由原生层接管玩家的这些饰品的重复渲染；其他功能保留。

### Youkai’s Homecoming 头饰兼容（仅Forge）

允许玩家模型叠加此模组的发饰何头饰。模型存在 `YoukaiHeadLocator` 时，优先使用其枢轴位置、旋转、缩放和父级变换；缺少时使用默认头部骨骼。


## 构建

构建工具使用 Java 21（游戏及编译产物仍以 Java 17 为目标）。

```bash
./gradlew build
```

构建产物位于 `<platform>/build/libs/` 目录下。

IDEA 的项目 SDK 与 Gradle JVM 使用 `zulu-21`，源码语言级别和编译目标仍为 Java 17。

回归检查：`./gradlew :common:checkModelIcons :common:checkModelTransfer`。

## 开源协议

### 源代码协议

本项目的源代码采用 MIT License 开放，您可以自由地使用、修改和分发代码，仅需要保留原始的版权声明。

详细的许可证条款请参见 [LICENSE](LICENSE) 文件。

### 模型资源协议

仓库中自带的模型文件采用不同的协议：

- 默认模型: 采用 CC0 (Creative Commons Zero) 协议，完全开放，无任何使用限制
- 酒狐 (Wine Fox) 模型: 采用 CC BY-NC-SA 4.0 协议，允许非商业使用，需要署名，并且衍生作品需要采用相同协议

请在使用相应模型时严格遵守对应的协议要求。

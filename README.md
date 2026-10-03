# Minecraft-place-mod

一个补全原版 `/place` 命令的 Fabric mod，让 `/place structure` 生成的结构**和自然生成的完全一样**。

## 它解决什么问题

原版的 `/place structure` 本质上只是个「建筑粘贴工具」。执行时它会：

1. 用 `Structure.generate(...)` 生成一个 `StructureStart`；
2. 调 `StructureStart.placeInChunk(...)` 把方块摆到世界里；
3. 生成结构内的实体（如村庄的村民）。

但它**跳过了自然生成时最关键的一步——结构注册**：

- 不调 `StructureManager.setStartForStructure(...)`，结构不进 `StructureManager`；
- 不写区块的 `structureReferences`，区块 NBT 里不记录这个结构。

后果：

- 游戏不认这块区域是「村庄 / 海底神殿」，**结构专属的刷怪规则永不激活**；
- 区块存档不记录该结构，**重启后结构在逻辑上「消失」**，只剩一堆方块。

## 这个 mod 干了什么

用一个 Mixin 挂在 `PlaceCommand.placeStructure` 的返回处，在命令执行完后补做自然生成的注册步骤：

1. **`setStartForStructure`** —— 把 `StructureStart` 存进它所在区块的 `StructureAccess`；
2. **`createReferences`** —— 调 `ChunkGenerator.createReferences`（自然生成用的同一个方法），扫描 17×17 邻居区块，把引用写入区块；
3. **`markUnsaved`** —— 标记涉及的区块未保存，强制持久化。

全程 `try-catch` 兜底：补注册失败不会影响命令本身的成功返回。

## 覆盖范围

- ✅ `/place structure`
- ❌ `/place jigsaw`、`/place template`、`/place feature`（语义不同，暂不处理）

## 构建

需要 JDK 25（Minecraft 26.1+ 要求）。

```bash
./gradlew build
```

产物在 `build/libs/`。

## 环境

- Minecraft 26.1.2
- Fabric Loader ≥ 0.19.5
- Fabric API
- Loom 1.18-SNAPSHOT / Gradle 9.7.1 / Java 25 / official 映射

## 已知未验证点

- Mixin 用 `LocalCapture.CAPTURE_FAILHARD` 捕获 `placeStructure` 的局部变量（`ServerLevel` / `Structure` / `ChunkGenerator` / `StructureStart`）。局部表顺序已按 26.1.2 字节码核对，但仍需实机编译验证。
- `createReferences` 对包围盒覆盖的每个区块各调一次（每次内部扫 17×17）。`addReferenceForStructure` 写的是 `LongSet`，天然幂等，不会重复写脏数据，但存在多余开销。

## 链接

- GitHub: https://github.com/Mr-dayhappy/Minecraft-place-mod

## License

MIT
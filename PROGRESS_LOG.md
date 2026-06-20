# Piano Companion — 自动开发进度日志

## 基本信息
- 项目路径: /home/agentuser/projects/PianoCompanion
- GitHub: https://github.com/zhang6236872/PianoCompanion
- 当前版本: **v2.9.0** (全部路线图 Phase 1-4 完成 + 后续增强: 离线同步引擎 + 真实 OMR 识谱引擎 + OMR 节奏分析 + OMR 连梁组切分 + OMR 谱号/调号/拍号识别 + OMR 中音/次中音谱号(C clef)识别 + OMR 附点音符识别 + OMR 符尾精细层数识别)
- 当前分支: main
- 最新 tag: v2.9.0

## 健康状态 (2026-06-20 核验)
- ✅ 编译通过: `gradle :app:compileDebugKotlin` BUILD SUCCESSFUL
- ✅ 单元测试通过: `gradle :app:testDebugUnitTest` — 160 个用例, 0 失败, 0 错误
- ✅ APK 构建成功: `gradle :app:assembleDebug` — app-debug.apk
- ✅ 全部 tag 已打: v1.1.0 → v1.2.0 → v1.3.0 → v1.4.0 → v2.0.0 → v2.1.0 → v2.2.0 → v2.3.0 → v2.4.0 → v2.5.0 → v2.6.0 → v2.7.0 → v2.8.0 → v2.9.0
- Kotlin 文件: 64 个 / 代码行数: 10000+ 行

## 开发历史

### 2026-06-18 (手动开发)
- v1.0.0: MVP 核心 (YIN算法+DTW+ScoreFollower+MusicXML+19测试)
- v1.1.0: 五线谱渲染+4首内置乐谱+会话持久化+节拍器+统计
- v1.2.0: UI打磨 (MD3配色+暗色主题+组件库+动画)

### 2026-06-19 (自主+手动开发)
- **Phase 1 (v1.3.0): 核心可用性 — ✅ 完成**
  - 任务2: MusicXML 文件导入 (SAF)
  - 任务3: DTW 参数可配置化 (DtwConfig + SettingsRepository + 3预设)
  - 任务4: 练习模式选择 (NORMAL/FOLLOW/EXAM)
  - 任务5: 错误音振动反馈 (HapticFeedback 5模式 + VIBRATE 权限)

- **Phase 2 (v1.4.0): 乐谱增强 — ✅ 完成**
  - 任务1: MIDI 文件导入 (MidiParser + 7测试)
  - 任务2: 五线谱增强 (升降号/休止符/小节线/音符类型)
  - 任务3: 自动翻页滚动 (AutoScrollScoreRenderer 平滑滚动)
  - 任务4/5: 乐谱搜索 + 来源标签

- **Phase 3 (v2.0.0): 高级功能 — ✅ 完成**
  - 任务1: 节拍器与练习联动
  - 任务2: 左右手分离跟踪 (HandTracker)
  - 任务3: 练习报告导出/分享 (ReportExporter)
  - 任务4: OMR 拍照识谱框架 (OmrEngine 占位)
  - 任务5: 云端同步占位 + 数据备份 (SyncManager)

- **Phase 4 (v2.1.0): 打磨发布 — ✅ 完成**
  - 任务1: App 图标 + 启动页 (SplashScreen)
  - 任务2: 多语言支持 (中/英/日)
  - 任务3: 桌面 Widget 快捷练习 (PracticeWidgetProvider)
  - 任务4: Wear OS 节拍器 (WearMetronomeActivity)
  - 任务5: Google Play 上架准备

### 2026-06-19 (自主开发)
- **后续增强 (v2.2.0): 离线优先同步引擎 — ✅ 完成**
  - 新增 `SyncEngine` (纯 Kotlin, 无 Android 依赖): 设备无关 fingerprint 身份、
    Last-Write-Wins 冲突解决、schema 迁移 (v1→v2)、SHA-1 完整性校验和、幂等合并
  - `SessionRecord` 新增 `updatedAt` 字段 (Gson 向后兼容)
  - `SyncManager` 重构为 解析→校验→迁移→LWW 合并→持久化 管线
  - `SyncResult.Success` 细化为 imported/updated/skipped; 设置页展示同步明细
  - 新增 `SyncEngineTest` 23 用例; 单元测试 36 → 59 全部通过

- **后续增强 (v2.3.0): 真实 OMR 拍照识谱引擎 — ✅ 完成**
  - 用真实计算机视觉管线替换原 `StubOmrEngine` 占位实现（不再返回硬编码音符）
  - 新增纯 Kotlin 图像处理内核 `omr/image/`（无 Android 依赖，完全可单元测试）：
    - `BinaryImage` — 二值图表示 + Otsu 一致的灰度二值化
    - `OtsuThresholder` — 自适应阈值（类间方差最大化），适应不同光照/对比度
    - `StaffLineDetector` — 水平投影 + 均匀间距分组检测五线谱系统
    - `StaffLineRemover` — 关键算法：长水平行程 + 薄垂直行程判定，
      只擦除谱线而保留与之重叠的音符头（避免把音符头切成两半）
    - `ConnectedComponents` — 8-连通迭代洪填充标记连通块（显式栈，防栈溢出）
    - `NoteheadDetector` — 按谱线间距缩放的尺寸/长宽比过滤选音符头
    - `PitchMapper` — 音符头 Y 坐标 → MIDI 音高（全局二进制音阶索引，
      正确处理 E-F / B-C 半音间距；高音/低音谱表）
  - `OmrPipeline`（纯 Kotlin 编排器）: 二值图 → 谱检测 → 去线 → 连通块 →
    音符头 → 音高映射 → 左右序列化（同列 = 和弦，共享起始时间）
  - `RealOmrEngine`（Android 层）: Bitmap → 灰度 → Otsu 二值化 → pipeline
  - `OmrViewModel` 改用 `RealOmrEngine`；识谱页说明文案更新
  - 保留 `StubOmrEngine` 作为离线流程演示回退
  - 新增 `PitchMapperTest`(6) + `OmrPipelineTest`(7) 共 13 用例，使用
    **手绘合成乐谱图**端到端验证（C 大调上行音阶 E4→E5、和弦同列、无谱表空结果等）
  - 单元测试 59 → **72** 全部通过；修复 Otsu↔二值化边界一致性（`<=` 约定）
  - 已知限制（已通过 warnings 告知用户）：节奏为估算（每个音符按四分音符），
    需人工校对；谱号按竖直位置推断（上谱表=高音，下谱表=低音）

- **后续增强 (v2.4.0): OMR 节奏分析 — ✅ 完成**
  - 用真实的节奏分析管线替换 v2.3.0 的"每个音符按四分音符"占位假设
  - 新增纯 Kotlin `omr/image/RhythmAnalyzer`（无 Android 依赖，完全可单元测试）：
    - **填充判定**：取符头中心 50% 区域黑像素占比 → 实心/空心（区分全/二分 vs 四/八分）
    - **符干检测**：在符头左右两侧向上/向下扫描垂直黑线（≥1.8 谱线间距，带 1px 间断容忍）→
      方向 + 远端坐标
    - **横梁检测**：成组同向符干末端间的水平黑色连线（≥60% 连通）→ 堆叠层数（1=八分/2=十六分）
    - **符尾检测**：符干末端侧方堆叠墨迹层数（单音符八分/十六分）
    - 综合分类：空心无干=全音符、空心有干=二分、实心有干+0/1/2/3尾=四/八/十六/三十二分
  - `NoteheadDetector` 新增**二次恢复扫描**：对"符头+符干"融合连通块（真实照片常见），
    定位最宽水平带恢复符头中心，使带干四分/二分音符也能被检测到
  - `OmrPipeline` 重构序列化：用各音符真实时值（而非统一四分音符）累加 startTime，
    节奏提示文案随检测结果动态变化
  - 全程只读二值图像素，不依赖连通块拓扑（即使符干与符头融合也能分析）
  - 新增 `RhythmAnalyzerTest`(14) + `OmrPipelineTest` 节奏集成(4)，共 18 用例，
    使用手绘合成符头/符干/横梁端到端验证（全/二/四/八/十六分音符、混合时值时序）
  - 单元测试 72 → **90** 全部通过
  - 已知限制：连梁组（符头+符干+横梁融合成一个宽连通块）的符头提取仍需未来的
    多字形连通块分割；符尾（非连梁单音符）检测为尽力而为

- **后续增强 (v2.5.0): OMR 连梁组多字形切分 — ✅ 完成**
  - 解决 v2.4.0 的首要已知限制：连梁组（多个符头+符干+横梁融合成一个宽连通块）
    此前因"过宽"被整体丢弃，导致连梁音符完全无法识别
  - `NoteheadDetector` 新增**三次扫描（连梁组切分）** `splitBeamedGroup`：
    - **横梁定位**：逐行统计黑像素宽度，行宽 ≥ 60% 连通块宽度的行即横梁带
      （横梁横跨几乎整个宽度），据此判定符头所在的一侧（横梁在上→符头在下）
    - **符头窗口**：在符头侧取行宽 ≥ 该侧最大行宽 40% 的密集行作为垂直窗口，
      可同时覆盖不同高度的多个符头
    - **列投影切分**：在窗口内对每列统计黑像素数，每个符头形成一段较宽的高值区间；
      符干虽也贡献但仅 1~2px 宽，被最小宽度（0.4 谱线间距）过滤掉
    - 提取连续峰值，每个峰值 → 一个符头（水平中心取峰中点，垂直中心取黑像素质心）
  - 与 v2.4.0 的 `RhythmAnalyzer` 横梁分组无缝衔接：切分出的符头经符干检测→
    横梁分组→层数统计，正确分类为八分/十六分音符（无需额外逻辑）
  - 全程只读取二值图像素，不修改连通块拓扑
  - 新增 `OmrPipelineTest` 5 个连梁组集成用例（手绘合成连梁图端到端验证）：
    双连梁八分对、双横梁十六分对、向下符干连梁对、三连梁组、连梁组+独立四分混合
  - 单元测试 90 → **95** 全部通过
  - 已知限制：符头与符干在不同 y 且间距过大（>1 个谱线间距）的连梁组，
    窗口可能无法同时覆盖；密集拥挤连梁组（符头水平间距 <0.4 谱线间距）仍难区分

- **后续增强 (v2.6.0): OMR 谱号/调号/拍号识别 — ✅ 完成**
  - 替代 v2.3.0 的"按竖直位置推断谱表"启发式，提升音高映射准确性
  - 新增 `KeySignature`（调号音乐理论模型：五度圈枚举 + `accidentalOffset(letter)`）
  - 新增 `TimeSignature`（拍号模型：`quartersPerMeasure` / `fromDigits` / `isValid`）
  - 新增 `SignatureDetector`（核心检测器，纯 Kotlin 无 Android 依赖）：
    - **谱号**：低音谱号双点（最可靠，高音谱号从不出现）+ 向上延伸量 + 高宽比等几何特征
    - **调号**：竖直笔画数区分升号(2 根竖笔)/降号(1 根竖笔)，按多数票；个数即调号
    - **拍号**：降采样到 5×7 布尔网格，与内置 0-9 点阵模板做汉明距离匹配
    - UNKNOWN 谱号时调用方回退旧启发式，保证向后兼容
  - `PitchMapper` 新增调号感知 `mapToMidi` 重载（先算白键音高，再用
    `KeySignature.accidentalOffset(letter)` 叠加升/降半音）
  - `OmrPipeline` 重构 `recognize()`：
    - 集成 SignatureDetector；`resolveStaff()` 在谱号 UNKNOWN 时回退竖直位置启发式
    - 拍号驱动 `measureMs` 计算（未识别默认 4/4）；`Score.timeSignature` 字段填充
    - **两阶段符头检测**：先用紧凑主扫描得到不含高大字形的干净符头确定签名区右界，
      再用完整扫描（含符头+符干恢复、连梁组切分）得到最终符头并排除签名区
      ——解决谱号曲线/拍号数字被符头恢复扫描误判为符头、进而污染签名区边界的问题
    - `SystemSignatures.signatureEndX` 暴露签名区最右 x 供符头过滤
  - 新增测试：`KeySignatureTest`(纯逻辑)、`TimeSignatureTest`(纯逻辑)、
    `SignatureDetectorTest`(合成图端到端：谱号/调号/拍号)、
    `OmrSignatureIntegrationTest`(全管线：谱号改音高、调号升半音、拍号写入 Score)
  - 单元测试 95 → **128** 全部通过；编译 + assembleDebug 通过
  - 已知限制：真实手写体照片的拍号数字/调号字形可能需人工校对（合成规整数字可靠）

- **后续增强 (v2.7.0): OMR 中音/次中音谱号(C clef)识别 — ✅ 完成**
  - 补齐 v2.6.0 列出的待完善项「中音谱号(C clef)支持」，使 OMR 能正确处理中提琴/
    大提琴等单声部乐器的乐谱（此前只支持高音/低音谱号）
  - `Staff` 数据模型枚举新增 `ALTO`(中音) / `TENOR`(次中音)
  - `PitchMapper` 新增 C 谱表底线 GDC：
    - 中音谱表底线 = F3 (GDC -4)，中央线(step 4) = C4 = MIDI 60
    - 次中音谱表底线 = D3 (GDC -6)，第 2 线(step 6) = C4 = MIDI 60
  - `SignatureDetector` 新增 `classifyCClef`：
    - **判定依据**：在排除低音谱号双点与高音谱号向上延伸后，计算谱号连通块的
      **竖直质心**；质心落在某谱线容差内且该线上下两侧均有墨迹(横跨)即判为 C 谱号
    - **中音 vs 次中音**：质心最近中央线(lines[2]) → ALTO；最近自上而下第 2 线(lines[1]) → TENOR
    - 辅以 `verticalCenterOfMass` / `straddlesLine` 两个纯函数（无 Android 依赖，可单测）
    - 已知限制：真实低音谱号若双点未被检测到，质心可能落在中央线附近而被误判为中音；
      低音双点是最可靠特征，正常情况下在 C 谱号判定之前已命中
  - `OmrPipeline.resolveStaff` 支持 ALTO/TENOR；提示文案新增「中音谱号/次中音谱号」
  - `HandTracker` 将 C 谱号单声部乐器(中提琴/大提琴)归入右手(主旋律)轨道
  - `ScoreRenderer` / `AutoScrollScoreRenderer` 在高音谱表以正确 MIDI 音高渲染 C 谱号音符
  - 新增 14 个单元测试：
    - `PitchMapperTest` +6（中音/次中音底线、中央线/第2线=C4、音阶上行）
    - `SignatureDetectorTest` +5（中音/次中音识别、低音谱号回归保护、中音+调号、中音/次中音互斥）
    - `OmrSignatureIntegrationTest` +3（全管线：中音底线=F3、中音中央线=C4、次中音底线=D3 + 提示文案）
  - 单元测试 128 → **142** 全部通过；编译 + assembleDebug 通过

### 2026-06-20 (自主开发)
- **后续增强 (v2.8.0): OMR 附点音符识别 — ✅ 完成**
  - 解决 v2.4.0 列出的节奏限制：此前 OMR 完全忽略附点（augmentation dot），
    导致附点二分/附点四分/附点八分等真实音乐中极常见的节奏全部按基础时值处理
    （例如附点四分音符被当成四分音符，时值偏短 1/3）
  - `RhythmAnalyzer` 新增 `countAugmentationDots`（纯 Kotlin，无 Android 依赖）：
    - **扫描区域**：符头右边缘 + 0.35~1.9 个谱线间距、竖直 ±0.75 个谱线间距
      （兼容间内音符附点居中、线上音符附点偏上/下半个间距两种记谱惯例）
    - **三态墨块分类**：对每个连续墨列构成的墨块判定
      ① 紧凑二维墨块（水平宽度与竖直跨度都 ≤0.8 谱线间距）→ 计为 1 个附点；
      ② 高而窄墨块（竖直跨度过大，即符干）→ 跳过不计数也不停止；
      ③ 宽墨块（宽度 >0.8 谱线间距，即下一个符头）→ 停止扫描。
      —— 据此正确区分附点 / 符干 / 相邻符头，避免误判
    - 支持单附点与双附点（标准记谱最多两个）
  - `NoteDuration.toMillis` 新增 `dotCount` 参数：按 `×(2 − 0.5^dotCount)` 叠加附点倍率
    （1 附点 ×1.5、2 附点 ×1.75），默认 0 保持向后兼容
  - `RhythmFeatures` 新增 `dotCount` / `dotted` / `effectiveMillis(quarterMs)`；
    `classify` 返回的仍是基础时值，附点倍率在 `effectiveMillis` 中叠加（关注点分离）
  - `OmrPipeline` 改用 `effectiveMillis`（含附点）累加 startTime；
    节奏提示文案新增「含 N 个附点音符」
  - 新增 10 个单元测试 `RhythmAnalyzerTest`：附点倍率纯逻辑（toMillis 单/双附点）、
    附点四分/附点二分/附点全音符、双附点、effectiveMillis、
    线上音符附点偏上检测、**回归保护**（相邻宽符头不误判、高窄符干不误判、无附点不误判）
  - 单元测试 142 → **152** 全部通过；编译 + assembleDebug 通过
  - 已知限制：附点窗口结束于符头右 1.9 谱线间距，极密集（间距 <1.9 间距）的相邻音符
    其右侧符头可能落入窗内；已用「宽墨块即停止」缓解，真实拥挤排版仍需人工校对

### 2026-06-20 (自主开发)
- **后续增强 (v2.9.0): OMR 符尾(flag)精细层数识别 — ✅ 完成**
  - 解决 v2.4.0 列出的节奏分析首要待完善项「符尾（非连梁单音符）精细层数识别」。
    此前 `detectFlags` 采用**单列纵向带计数**（仅在符干末端左/右偏移一列、用窄窗口
    数纵向墨带），对真实卷曲符尾非常脆弱：符尾在某列可能恰好无墨、或卷曲跨度不足一列
    而被漏检，导致八分/十六分/三十二分单音符常被误判为四分音符。
  - `RhythmAnalyzer.detectFlags` 重写为**符干方向感知 + 2D 水平墨迹投影**计数：
    - **符干方向感知**：up-stem 符尾在符干顶端之下、down-stem 在底端之上——据此确定
      竖直扫描方向，并在越过符头中心前截断，避免把符头误算为符尾层
    - **逐行水平墨迹**：对扫描区每一行计算「从符干向左/右延伸的最长水平墨迹长度」
      （`horizontalRun`），裸符干仅 ~1px 不会超过阈值，而符尾会在所在行形成一条较长
      的水平墨迹（≥0.5 谱线间距）；同时尝试左/右两侧取较大值，兼容符尾向左或向右卷曲
    - **层计数**：被白行隔开的连续「符尾行」段（允许 1 行间断）即符尾个数
      （八分=1、十六分=2、三十二分=3），封顶 3 层
    - 关键修复：`horizontalRun` 移除原先的纵向 ±1 容差——BinaryImage 已是 Otsu 二值图
      （无抗锯齿），纵向容差反而使每个符尾影响区上下各膨胀 1 行，把间距较近的堆叠符尾
      （十六分/三十二分）误合并成单个；移除后符尾影响区贴合真实墨迹行，正确分层
  - `OmrPipeline` 节奏提示文案新增「含 N 个带符尾音符」统计
  - 新增 8 个单元测试 `RhythmAnalyzerTest`（合成符头+符干+符尾端到端验证）：
    单符尾八分、双堆叠符尾十六分、三堆叠符尾三十二分、向下符干符尾、
    向左卷曲符尾、裸符干(四分)不误判、符尾与四分混合、符尾不污染符头区
  - 单元测试 152 → **160** 全部通过；编译 + assembleDebug 通过
  - 已知限制：符尾行间隔 <2 行（间距 <0.4 谱线间距的极密堆叠）可能被 1 行桥接合并；
    真实记谱符尾间距约 0.7 谱线间距，正常情况下可正确分层

## 当前状态
**🎉 全部路线图 (Phase 1-4) 已完成 + 后续增强 (离线同步引擎 v2.2.0、真实 OMR 识谱引擎 v2.3.0、OMR 节奏分析 v2.4.0、OMR 连梁组切分 v2.5.0、OMR 谱号/调号/拍号识别 v2.6.0、OMR 中音/次中音谱号识别 v2.7.0、OMR 附点音符识别 v2.8.0、OMR 符尾精细层数识别 v2.9.0) 已完成！** 代码已合并到 main，所有 tag 已打。

## 单元测试明细 (160 个, 全部通过)
- PitchDetectorTest: 5
- MidiParserTest: 7
- MusicXmlParserTest: 4
- DtwConfigTest: 6
- OnlineDTWTest: 5
- MusicUtilsTest: 9
- SyncEngineTest: 23
- PitchMapperTest: 12
- OmrPipelineTest: 16
- RhythmAnalyzerTest: 32
- KeySignatureTest: 11
- TimeSignatureTest: 5
- SignatureDetectorTest: 18
- OmrSignatureIntegrationTest: 7

## 阻塞
（无）

## 后续可选方向 (超出原路线图)
- 真机端到端测试 (需物理设备) — 含真实照片 OMR 效果验证
- OMR 节奏分析增强：符干/横梁/音符尾 → 真实时值 ✅ (v2.4.0 已完成核心：全/二/四/八/十六分)
  - ✅ 符尾（非连梁单音符）精细层数识别 (v2.9.0 已完成：符干方向感知 + 2D 水平墨迹投影计数八/十六/三十二分符尾)
- OMR 附点音符识别 ✅ (v2.8.0 已完成：符头右侧三态墨块分类检测单/双附点，时值 ×1.5/×1.75)
- OMR 连梁组切分 ✅ (v2.5.0 已完成双/三连梁组、上下符干、双横梁十六分)
  - 待完善：不同高度间距过大的连梁组、密集拥挤连梁组（符头水平间距 <0.4 谱线间距）
- OMR 谱号/调号/拍号识别 ✅ (v2.6.0 已完成：几何特征判谱号 + 竖直笔画判升降 + 5×7 网格匹配拍号)
  - ✅ 中音谱号(C clef)支持 (v2.7.0 已完成：竖直质心 + 谱线横跨判定，含次中音谱号)
  - 待完善：真实手写体照片鲁棒性（需真实样本调优模板）、女高音/女低音谱号(短谱表)支持
- 云端同步真实后端 (SyncEngine 合并语义已就绪, 仅需接入 Firebase/Drive 传输层)
- Play Store 实际上架

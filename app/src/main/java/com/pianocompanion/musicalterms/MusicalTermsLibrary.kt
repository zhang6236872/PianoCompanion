package com.pianocompanion.musicalterms

/**
 * 音乐表情术语库（Musical Terms Library）。
 *
 * 包含 80+ 条常见音乐术语，涵盖速度、力度、演奏法、表情和修饰词五大类别。
 * 每条术语按难度分为初级（BEGINNER）、中级（INTERMEDIATE）、高级（ADVANCED）。
 *
 * 术语来源：标准古典音乐乐谱中常见的意大利语/德语/法语表情标记，
 * 参考 ABRSM 乐理考级术语表和标准的音乐术语词典。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 */
object MusicalTermsLibrary {

    // ── 速度术语（TEMPO）──────────────────────────────────

    private val tempoTerms = listOf(
        // 初级：最常见的速度标记
        MusicalTerm("Largo", "广板（宽广缓慢）", TermCategory.TEMPO, "40-60", TermDifficulty.BEGINNER,
            example = "韩德尔《广板》"),
        MusicalTerm("Adagio", "柔板（缓慢）", TermCategory.TEMPO, "66-76", TermDifficulty.BEGINNER,
            example = "贝多芬《月光奏鸣曲》第二乐章"),
        MusicalTerm("Andante", "行板（步行速度）", TermCategory.TEMPO, "76-108", TermDifficulty.BEGINNER,
            example = "贝多芬《第五交响曲》第二乐章"),
        MusicalTerm("Moderato", "中板（中等速度）", TermCategory.TEMPO, "108-120", TermDifficulty.BEGINNER,
            example = "最常见的速度标记"),
        MusicalTerm("Allegro", "快板（快速活泼）", TermCategory.TEMPO, "120-168", TermDifficulty.BEGINNER,
            example = "莫扎特奏鸣曲第一乐章"),
        MusicalTerm("Presto", "急板（极快）", TermCategory.TEMPO, "168-200", TermDifficulty.BEGINNER,
            example = "帕格尼尼《随想曲》"),

        // 中级：次常见速度标记
        MusicalTerm("Larghetto", "小广板（比广板稍快）", TermCategory.TEMPO, "60-66", TermDifficulty.INTERMEDIATE),
        MusicalTerm("Adagietto", "小柔板（比柔板稍快）", TermCategory.TEMPO, "70-80", TermDifficulty.INTERMEDIATE),
        MusicalTerm("Andantino", "小行板（比行板稍快或稍慢）", TermCategory.TEMPO, "80-108", TermDifficulty.INTERMEDIATE),
        MusicalTerm("Allegretto", "小快板（稍快，轻巧）", TermCategory.TEMPO, "100-128", TermDifficulty.INTERMEDIATE,
            example = "贝多芬《第七交响曲》第二乐章"),
        MusicalTerm("Vivace", "活泼的（比快板更快）", TermCategory.TEMPO, "140-176", TermDifficulty.INTERMEDIATE),
        MusicalTerm("Prestissimo", "最急板（最快）", TermCategory.TEMPO, "200+", TermDifficulty.INTERMEDIATE),

        // 高级：冷门和变化速度
        MusicalTerm("Grave", "极慢板（庄严沉重）", TermCategory.TEMPO, "25-45", TermDifficulty.ADVANCED),
        MusicalTerm("Lento", "慢板（缓慢）", TermCategory.TEMPO, "52-68", TermDifficulty.ADVANCED),
        MusicalTerm("Maestoso", "庄严的（庄严宏伟）", TermCategory.TEMPO, null, TermDifficulty.ADVANCED,
            example = "也可作表情术语"),
        MusicalTerm("Vivo", "活跃的（充满活力）", TermCategory.TEMPO, "140-160", TermDifficulty.ADVANCED)
    )

    // ── 速度变化术语 ──────────────────────────────────────

    private val tempoChangeTerms = listOf(
        // 初级
        MusicalTerm("accelerando", "渐快（逐渐加速）", TermCategory.TEMPO, null, TermDifficulty.BEGINNER,
            abbreviation = "accel.", example = "激动、紧迫的音乐段落"),
        MusicalTerm("ritardando", "渐慢（逐渐减速）", TermCategory.TEMPO, null, TermDifficulty.BEGINNER,
            abbreviation = "ritard.", example = "乐段结尾减速"),

        // 中级
        MusicalTerm("rallentando", "渐慢渐弱（逐渐减慢减弱）", TermCategory.TEMPO, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "rall."),
        MusicalTerm("ritenuto", "突慢（立即变慢）", TermCategory.TEMPO, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "rit."),
        MusicalTerm("allargando", "渐慢渐强（变宽变慢）", TermCategory.TEMPO, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "allarg."),
        MusicalTerm("a tempo", "回原速", TermCategory.TEMPO, null, TermDifficulty.INTERMEDIATE,
            example = "渐快/渐慢后恢复原速"),
        MusicalTerm("tempo rubato", "自由速度（弹性速度）", TermCategory.TEMPO, null, TermDifficulty.INTERMEDIATE,
            example = "肖邦作品中常用"),
        MusicalTerm("stringendo", "加速渐强（紧迫）", TermCategory.TEMPO, null, TermDifficulty.INTERMEDIATE),

        // 高级
        MusicalTerm("doppio movimento", "加倍速度", TermCategory.TEMPO, null, TermDifficulty.ADVANCED),
        MusicalTerm("meno mosso", "稍慢（少快一点）", TermCategory.TEMPO, null, TermDifficulty.ADVANCED),
        MusicalTerm("più mosso", "稍快（更快一点）", TermCategory.TEMPO, null, TermDifficulty.ADVANCED),
        MusicalTerm("tempo primo", "回最初速度", TermCategory.TEMPO, null, TermDifficulty.ADVANCED,
            abbreviation = "Tempo I")
    )

    // ── 力度术语（DYNAMICS）──────────────────────────────

    private val dynamicsTerms = listOf(
        // 初级：基础力度
        MusicalTerm("pianissimo", "很弱", TermCategory.DYNAMICS, null, TermDifficulty.BEGINNER,
            abbreviation = "pp"),
        MusicalTerm("piano", "弱", TermCategory.DYNAMICS, null, TermDifficulty.BEGINNER,
            abbreviation = "p"),
        MusicalTerm("mezzo piano", "中弱", TermCategory.DYNAMICS, null, TermDifficulty.BEGINNER,
            abbreviation = "mp"),
        MusicalTerm("mezzo forte", "中强", TermCategory.DYNAMICS, null, TermDifficulty.BEGINNER,
            abbreviation = "mf"),
        MusicalTerm("forte", "强", TermCategory.DYNAMICS, null, TermDifficulty.BEGINNER,
            abbreviation = "f"),
        MusicalTerm("fortissimo", "很强", TermCategory.DYNAMICS, null, TermDifficulty.BEGINNER,
            abbreviation = "ff"),

        // 中级：力度变化
        MusicalTerm("crescendo", "渐强", TermCategory.DYNAMICS, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "cresc."),
        MusicalTerm("decrescendo", "渐弱（渐小）", TermCategory.DYNAMICS, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "decresc."),
        MusicalTerm("diminuendo", "渐弱", TermCategory.DYNAMICS, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "dim."),
        MusicalTerm("fortepiano", "强后即弱（突强后立即弱）", TermCategory.DYNAMICS, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "fp"),

        // 高级：极端力度
        MusicalTerm("pianississimo", "极弱（比 pp 更弱）", TermCategory.DYNAMICS, null, TermDifficulty.ADVANCED,
            abbreviation = "ppp"),
        MusicalTerm("pianopianissimo", "最弱", TermCategory.DYNAMICS, null, TermDifficulty.ADVANCED,
            abbreviation = "pppp"),
        MusicalTerm("fortissimo fortissimo", "最强", TermCategory.DYNAMICS, null, TermDifficulty.ADVANCED,
            abbreviation = "ffff"),
        MusicalTerm("sforzando", "突强（强调单个音）", TermCategory.DYNAMICS, null, TermDifficulty.ADVANCED,
            abbreviation = "sfz"),
        MusicalTerm("sforzato", "突强（同 sforzando）", TermCategory.DYNAMICS, null, TermDifficulty.ADVANCED,
            abbreviation = "sf"),
        MusicalTerm("rinforzando", "加强（突然加强力度）", TermCategory.DYNAMICS, null, TermDifficulty.ADVANCED,
            abbreviation = "rfz")
    )

    // ── 演奏法（ARTICULATION）────────────────────────────

    private val articulationTerms = listOf(
        // 初级：基础演奏法
        MusicalTerm("legato", "连奏（连贯圆滑）", TermCategory.ARTICULATION, null, TermDifficulty.BEGINNER,
            abbreviation = "︵", example = "最基础的演奏法之一"),
        MusicalTerm("staccato", "断奏（短促分离）", TermCategory.ARTICULATION, null, TermDifficulty.BEGINNER,
            abbreviation = "·", example = "短促、清晰的触键"),
        MusicalTerm("tenuto", "保持音（充分保持时值）", TermCategory.ARTICULATION, null, TermDifficulty.BEGINNER,
            abbreviation = "—"),
        MusicalTerm("accent", "重音（强调）", TermCategory.ARTICULATION, null, TermDifficulty.BEGINNER,
            abbreviation = ">"),

        // 中级：进阶演奏法
        MusicalTerm("marcato", "着重（强调突出）", TermCategory.ARTICULATION, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "^"),
        MusicalTerm("portato", "次断奏（介于连奏和断奏之间）", TermCategory.ARTICULATION, null, TermDifficulty.INTERMEDIATE,
            abbreviation = "·_"),
        MusicalTerm("legatissimo", "极连奏（非常连贯）", TermCategory.ARTICULATION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("spiccato", "跳弓（弦乐弓法）", TermCategory.ARTICULATION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("martelé", "槌弓（弦乐击弓）", TermCategory.ARTICULATION, null, TermDifficulty.INTERMEDIATE),

        // 高级
        MusicalTerm("staccatissimo", "顿音（极短促的断奏）", TermCategory.ARTICULATION, null, TermDifficulty.ADVANCED,
            abbreviation = "▼"),
        MusicalTerm("detache", "分弓（每个音分开演奏）", TermCategory.ARTICULATION, null, TermDifficulty.ADVANCED),
        MusicalTerm("pizzicato", "拨弦（弦乐用手指拨弦）", TermCategory.ARTICULATION, null, TermDifficulty.ADVANCED,
            abbreviation = "pizz."),
        MusicalTerm("col legno", "用弓杆击弦", TermCategory.ARTICULATION, null, TermDifficulty.ADVANCED),
        MusicalTerm("glissando", "滑奏（连续滑动）", TermCategory.ARTICULATION, null, TermDifficulty.ADVANCED,
            abbreviation = "gliss.")
    )

    // ── 表情术语（EXPRESSION）────────────────────────────

    private val expressionTerms = listOf(
        // 初级
        MusicalTerm("dolce", "柔和甜美地", TermCategory.EXPRESSION, null, TermDifficulty.BEGINNER,
            example = "温柔、甜美的音乐段落"),
        MusicalTerm("cantabile", "如歌地（歌唱般）", TermCategory.EXPRESSION, null, TermDifficulty.BEGINNER,
            example = "旋律优美、如歌唱般"),
        MusicalTerm("espressivo", "有表情地（富有表现力）", TermCategory.EXPRESSION, null, TermDifficulty.BEGINNER,
            abbreviation = "espr."),

        // 中级
        MusicalTerm("animato", "生气勃勃地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("appassionato", "热情洋溢地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("brillante", "辉煌灿烂地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("calando", "渐弱渐慢（渐消失）", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("con moto", "有动感地（有运动感）", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("con brio", "有活力地（精神焕发）", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("con anima", "有感情地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("energico", "有力地（精力充沛）", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("furioso", "狂暴地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("gioioso", "欢乐地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("grazioso", "优雅地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("leggero", "轻巧地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("misterioso", "神秘地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("religioso", "虔诚地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("scherzando", "诙谐地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("semplice", "单纯朴素地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),
        MusicalTerm("tranquillo", "安静地", TermCategory.EXPRESSION, null, TermDifficulty.INTERMEDIATE),

        // 高级
        MusicalTerm("affettuoso", "深情地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("agitato", "激动不安地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("amabile", "可爱地（亲切悦人）", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("brioso", "充满活力地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("capriccioso", "随想地（自由幻想）", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("con delicatezza", "精致细腻地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("con fuoco", "火热地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("con grazia", "优雅地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("con spirito", "精神抖擞地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("dolente", "哀怨悲伤地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("funebre", "葬礼般地（阴沉）", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("impetuoso", "急躁冲动地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("lacrimoso", "哭泣悲哀地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("lugubre", "阴郁凄凉地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("morendo", "渐弱至消失", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("nobilmente", "高贵庄严地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("perdendosi", "渐弱消失（渐逝）", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("pesante", "沉重有力地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("scherzoso", "戏谑玩笑地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("sostenuto", "绵延地（保持音）", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("teneramente", "温柔亲切地", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED),
        MusicalTerm("vigoroso", "精力充沛地（刚健）", TermCategory.EXPRESSION, null, TermDifficulty.ADVANCED)
    )

    // ── 修饰词（MODIFIER）────────────────────────────────

    private val modifierTerms = listOf(
        // 初级
        MusicalTerm("molto", "很、非常", TermCategory.MODIFIER, null, TermDifficulty.BEGINNER,
            example = "molto allegro = 很快"),
        MusicalTerm("poco", "稍、一点点", TermCategory.MODIFIER, null, TermDifficulty.BEGINNER,
            example = "poco a poco = 一点一点地"),
        MusicalTerm("più", "更、更多", TermCategory.MODIFIER, null, TermDifficulty.BEGINNER,
            example = "più forte = 更强"),
        MusicalTerm("meno", "较少、不那么", TermCategory.MODIFIER, null, TermDifficulty.BEGINNER,
            example = "meno mosso = 稍慢"),

        // 中级
        MusicalTerm("assai", "非常、很", TermCategory.MODIFIER, null, TermDifficulty.INTERMEDIATE,
            example = "allegro assai = 很快"),
        MusicalTerm("non tanto", "不要太", TermCategory.MODIFIER, null, TermDifficulty.INTERMEDIATE,
            example = "non tanto allegro = 不太快"),
        MusicalTerm("poco a poco", "逐渐地（一点一点地）", TermCategory.MODIFIER, null, TermDifficulty.INTERMEDIATE,
            example = "cresc. poco a poco = 逐渐渐强"),
        MusicalTerm("quasi", "几乎、宛如", TermCategory.MODIFIER, null, TermDifficulty.INTERMEDIATE,
            example = "quasi presto = 几乎是急板"),
        MusicalTerm("sempre", "一直、始终", TermCategory.MODIFIER, null, TermDifficulty.INTERMEDIATE,
            example = "sempre legato = 始终连奏"),
        MusicalTerm("subito", "突然、立即", TermCategory.MODIFIER, null, TermDifficulty.INTERMEDIATE,
            example = "subito piano = 突然变弱"),
        MusicalTerm("troppo", "太多、过于", TermCategory.MODIFIER, null, TermDifficulty.INTERMEDIATE,
            example = "non troppo = 不太…"),

        // 高级
        MusicalTerm("al", "到、至", TermCategory.MODIFIER, null, TermDifficulty.ADVANCED,
            example = "al fine = 到结束"),
        MusicalTerm("come", "如同、像", TermCategory.MODIFIER, null, TermDifficulty.ADVANCED),
        MusicalTerm("ma", "但是、然而", TermCategory.MODIFIER, null, TermDifficulty.ADVANCED,
            example = "allegro ma non troppo = 快板但不太快"),
        MusicalTerm("o", "或者", TermCategory.MODIFIER, null, TermDifficulty.ADVANCED),
        MusicalTerm("tanto", "如此、那么", TermCategory.MODIFIER, null, TermDifficulty.ADVANCED)
    )

    /** 全部术语（合并所有类别）。 */
    val ALL: List<MusicalTerm> = (
        tempoTerms + tempoChangeTerms + dynamicsTerms +
            articulationTerms + expressionTerms + modifierTerms
    )

    init {
        // 确保没有重复术语
        val duplicates = ALL.groupingBy { it.term.lowercase() }
            .eachCount().filter { it.value > 1 }
        require(duplicates.isEmpty()) { "重复的术语: $duplicates" }
    }

    /** 术语总数。 */
    val size: Int get() = ALL.size

    /**
     * 按类别筛选术语。
     */
    fun byCategory(category: TermCategory): List<MusicalTerm> =
        ALL.filter { it.category == category }

    /**
     * 获取指定难度及以下难度的所有术语。
     * - BEGINNER: 只含 BEGINNER
     * - INTERMEDIATE: 含 BEGINNER + INTERMEDIATE
     * - ADVANCED: 含全部
     */
    fun upToDifficulty(difficulty: TermDifficulty): List<MusicalTerm> {
        val levels = when (difficulty) {
            TermDifficulty.BEGINNER -> setOf(TermDifficulty.BEGINNER)
            TermDifficulty.INTERMEDIATE -> setOf(TermDifficulty.BEGINNER, TermDifficulty.INTERMEDIATE)
            TermDifficulty.ADVANCED -> TermDifficulty.ALL.toSet()
        }
        return ALL.filter { it.difficulty in levels }
    }

    /**
     * 按类别 + 难度筛选术语。
     */
    fun filter(category: TermCategory?, difficulty: TermDifficulty): List<MusicalTerm> {
        val pool = upToDifficulty(difficulty)
        return if (category != null) pool.filter { it.category == category } else pool
    }

    /**
     * 各类别 + 各难度的术语数量统计。
     */
    fun countByCategoryAndDifficulty(): Map<Pair<TermCategory, TermDifficulty>, Int> {
        val result = mutableMapOf<Pair<TermCategory, TermDifficulty>, Int>()
        for (category in TermCategory.ALL) {
            for (difficulty in TermDifficulty.ALL) {
                result[category to difficulty] = filter(category, difficulty).size
            }
        }
        return result
    }
}

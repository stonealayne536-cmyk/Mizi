package com.example.util

object PinyinHelper {

    private val famousAuthors = mapOf(
        "忘语" to "Wang Yu",
        "天蚕土豆" to "Tian Can Tu Dou",
        "爱潜水的乌贼" to "Ai Qian Shui De Wu Zei (Cuttlefish)",
        "墨香铜臭" to "Mo Xiang Tong Xiu",
        "猫腻" to "Mao Ni",
        "辰东" to "Chen Dong",
        "我吃西红柿" to "Wo Chi Xi Hong Shi (I Eat Tomatoes)",
        "耳根" to "Er Gen",
        "唐家三少" to "Tang Jia San Shao",
        "烽火戏诸侯" to "Feng Huo Xi Zhu Hou",
        "梦入神机" to "Meng Ru Shen Ji",
        "蚕茧里的牛" to "Can Jian Li De Niu",
        "骷髅精灵" to "Ku Lou Jing Ling",
        "净无痕" to "Jing Wu Hen",
        "蛊真人" to "Gu Zhen Ren",
        "会说话的肘子" to "Hui Shuo Hua De Zhou Zi",
        "卖报小郎君" to "Mai Bao Xiao Lang Jun",
        "齐佩甲" to "Qi Pei Jia",
        "风凌天下" to "Feng Ling Tian Xia",
        "跳舞" to "Tiao Wu",
        "月关" to "Yue Guan",
        "血红" to "Xue Hong",
        "愤怒的香蕉" to "Fen Nu De Xiang Jiao",
        "蝴蝶蓝" to "Hu Die Lan (Butterfly Blue)",
        "肉包不吃肉" to "Rou Bao Bu Chi Rou",
        "Priest" to "Priest",
        "淮上" to "Huai Shang",
        "西子绪" to "Xi Zi Xu",
        "木苏里" to "Mu Su Li"
    )

    private val famousNovelTitles = mapOf(
        "凡人修仙传" to "Record of a Mortal's Journey to Immortality",
        "凡人修仙之仙界篇" to "A Mortal's Journey to Immortality: Immortal Realm",
        "斗破苍穹" to "Battle Through the Heavens",
        "诡秘之主" to "Lord of the Mysteries",
        "宿命之环" to "Circle of Inevitability (Lord of the Mysteries 2)",
        "天官赐福" to "Heaven Official's Blessing",
        "魔道祖师" to "Grandmaster of Demonic Cultivation",
        "人渣反派自救系统" to "The Scum Villain's Self-Saving System",
        "遮天" to "Shrouding the Heavens",
        "完美世界" to "Perfect World",
        "圣墟" to "The Sacred Ruins",
        "吞噬星空" to "Swallowed Star",
        "全职高手" to "The King's Avatar",
        "仙逆" to "Renegade Immortal",
        "求魔" to "Beseech the Devil",
        "我欲封天" to "I Shall Seal the Heavens",
        "一念永恒" to "A Will Eternal",
        "三寸人间" to "A World Worth Protecting",
        "光阴之外" to "Beyond the Timescape",
        "大奉打更人" to "Nightwatchers of the Great Feng",
        "武动乾坤" to "Martial Universe",
        "大主宰" to "The Great Ruler",
        "元尊" to "Dragon Prince Yuan (Yuan Zun)",
        "万相之王" to "King of Gods (Wan Xiang Zhi Wang)",
        "盘龙" to "Coiling Dragon",
        "星辰变" to "Stellar Transformations",
        "九鼎记" to "The Nine Cauldrons",
        "寸芒" to "Inch of Radiance",
        "飞剑问道" to "Seeking the Flying Sword Path",
        "沧元图" to "Archean Eon Art",
        "神墓" to "Tomb of the Gods",
        "长生界" to "World of Immortals",
        "不死不灭" to "Immortal and Indestructible",
        "庆余年" to "Joy of Life",
        "将夜" to "Ever Night",
        "择天记" to "Way of Choices (Fighter of the Destiny)",
        "大道朝天" to "The Path Toward Heaven",
        "间客" to "The Outcast",
        "朱雀记" to "The Vermilion Bird",
        "雪中悍刀行" to "The Snowy Path of the Heroic Blade",
        "剑来" to "Sword Coming",
        "第一序列" to "The First Order",
        "夜的命名术" to "Naming of the Night",
        "超神机械师" to "The Legendary Mechanic",
        "牧神记" to "Tales of Herding Gods",
        "临渊行" to "Journey Near the Abyss",
        "蛊真人" to "Reverend Insanity",
        "修真聊天群" to "Cultivation Chat Group",
        "斗罗大陆" to "Soul Land (Douluo Dalu)",
        "绝世唐门" to "Peerless Tang Sect (Soul Land 2)",
        "龙王传说" to "Legend of the Dragon King (Soul Land 3)",
        "终极斗罗" to "Ultimate Douluo (Soul Land 4)",
        "神印王座" to "Throne of Seal",
        "酒神" to "Dionysus (Wine God)",
        "天火大道" to "Skyfire Avenue",
        "二哈和他的白猫师尊" to "The Husky and His White Cat Shizun (Erha)",
        "杀破狼" to "Sha Po Lang (Stars of Chaos)",
        "默读" to "Silent Reading",
        "残次品" to "The Defectives",
        "全球高考" to "Global Examination",
        "死亡万花筒" to "Kaleidoscope of Death",
        "破云" to "Breaking Through the Clouds",
        "吞海" to "Swallowing the Seas"
    )

    private val pinyinCharMap = mapOf(
        '忘' to "Wang", '语' to "Yu", '天' to "Tian", '蚕' to "Can", '土' to "Tu", '豆' to "Dou",
        '爱' to "Ai", '潜' to "Qian", '水' to "Shui", '的' to "De", '乌' to "Wu", '贼' to "Zei",
        '墨' to "Mo", '香' to "Xiang", '铜' to "Tong", '臭' to "Xiu", '猫' to "Mao", '腻' to "Ni",
        '辰' to "Chen", '东' to "Dong", '我' to "Wo", '吃' to "Chi", '西' to "Xi", '红' to "Hong",
        '柿' to "Shi", '耳' to "Er", '根' to "Gen", '唐' to "Tang", '家' to "Jia", '三' to "San",
        '少' to "Shao", '烽' to "Feng", '火' to "Huo", '戏' to "Xi", '诸' to "Zhu", '侯' to "Hou",
        '凡' to "Fan", '人' to "Ren", '修' to "Xiu", '仙' to "Xian", '传' to "Zhuan", '斗' to "Dou",
        '破' to "Po", '苍' to "Cang", '穹' to "Qiong", '诡' to "Gui", '秘' to "Mi", '主' to "Zhu",
        '官' to "Guan", '赐' to "Ci", '福' to "Fu", '神' to "Shen", '道' to "Dao", '剑' to "Jian",
        '武' to "Wu", '极' to "Ji", '尊' to "Zun", '龙' to "Long", '凤' to "Feng", '雪' to "Xue",
        '月' to "Yue", '光' to "Guang", '风' to "Feng", '云' to "Yun", '山' to "Shan", '海' to "Hai",
        '星' to "Xing", '魔' to "Mo", '圣' to "Sheng", '帝' to "Di", '王' to "Wang", '皇' to "Huang",
        '夜' to "Ye", '无' to "Wu", '痕' to "Hen", '青' to "Qing", '白' to "Bai", '金' to "Jin",
        '木' to "Mu", '飞' to "Fei", '生' to "Sheng", '世' to "Shi", '界' to "Jie", '灵' to "Ling"
    )

    fun cleanRawTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("[《》【】\\[\\]()（）\"“”'']"), "")
            .replace(Regex("(?i)(?:最新章节|TXT下载|全文阅读|全集下载|目录|章节列表|无弹窗|顶点小说|全本小说|笔趣阁|69书吧|69shu|起点中文网).*"), "")
            .replace(Regex("[-_|_].*"), "")
            .trim()
    }

    fun formatAuthorWithPinyin(authorRaw: String): String {
        val clean = authorRaw.trim()
            .removePrefix("作者：").removePrefix("作者:").removePrefix("文 / ").trim()
        if (clean.isBlank()) return "Unknown"

        famousAuthors[clean]?.let { return "$it ($clean)" }

        // If author already contains pinyin or English
        if (clean.matches(Regex(".*[a-zA-Z].*"))) return clean

        // Convert individual Chinese characters
        val pinyinParts = mutableListOf<String>()
        for (ch in clean) {
            val p = pinyinCharMap[ch]
            if (p != null) {
                pinyinParts.add(p)
            } else if (ch.isLetter()) {
                pinyinParts.add(ch.toString())
            }
        }

        return if (pinyinParts.isNotEmpty()) {
            val pinyin = pinyinParts.joinToString(" ")
            "$pinyin ($clean)"
        } else {
            clean
        }
    }

    fun translateNovelTitle(rawTitle: String): String {
        val clean = cleanRawTitle(rawTitle)
        if (clean.isBlank()) return "Chinese Web Novel"

        // Direct exact match
        famousNovelTitles[clean]?.let { return it }

        // Partial match
        for ((cn, en) in famousNovelTitles) {
            if (clean.contains(cn)) {
                return en
            }
        }

        // Common web novel title patterns
        if (clean.endsWith("传")) {
            val core = clean.removeSuffix("传")
            val translatedCore = famousNovelTitles[core] ?: core
            return "Legend of $translatedCore"
        }
        if (clean.startsWith("大") && clean.length in 3..6) {
            val core = clean.removePrefix("大")
            return "The Great $core"
        }
        if (clean.contains("修仙")) {
            return "Cultivating Immortality in $clean"
        }
        if (clean.contains("剑神")) {
            return "Peerless Sword God"
        }
        if (clean.contains("无敌")) {
            return "Invincible $clean"
        }

        return clean
    }
}

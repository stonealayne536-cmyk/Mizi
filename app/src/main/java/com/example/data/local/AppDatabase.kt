package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Chapter
import com.example.data.model.GlossaryTerm
import com.example.data.model.Novel
import com.example.data.model.Quote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Novel::class, Chapter::class, GlossaryTerm::class, Quote::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun glossaryDao(): GlossaryDao
    abstract fun quoteDao(): QuoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inkweave_novel_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            val novelDao = db.novelDao()
            val chapterDao = db.chapterDao()
            val glossaryDao = db.glossaryDao()
            val quoteDao = db.quoteDao()

            // Seed Global Cultivation Glossary
            val initialGlossary = listOf(
                GlossaryTerm(rawTerm = "练气期", translatedTerm = "Qi Condensation Stage", category = "Cultivation Realm", notes = "Initial stage of absorbing spiritual energy into the meridians"),
                GlossaryTerm(rawTerm = "筑基期", translatedTerm = "Foundation Establishment Stage", category = "Cultivation Realm", notes = "Forming the spiritual pillar in the dantian"),
                GlossaryTerm(rawTerm = "金丹期", translatedTerm = "Golden Core Stage", category = "Cultivation Realm", notes = "Condensing liquid spiritual essence into an indestructible core"),
                GlossaryTerm(rawTerm = "元婴期", translatedTerm = "Nascent Soul Stage", category = "Cultivation Realm", notes = "Birth of spiritual soul that can survive physical death"),
                GlossaryTerm(rawTerm = "化神期", translatedTerm = "Soul Formation Stage", category = "Cultivation Realm", notes = "Merging mind with the heavens and earth"),
                GlossaryTerm(rawTerm = "丹田", translatedTerm = "Dantian", category = "Technique", notes = "Energy sea located three inches below the navel"),
                GlossaryTerm(rawTerm = "储物袋", translatedTerm = "Storage Pouch", category = "Item", notes = "Dimensional pocket bag used by cultivators to store treasures"),
                GlossaryTerm(rawTerm = "灵石", translatedTerm = "Spirit Stones", category = "Item", notes = "Currency of the cultivation world rich in pure spiritual qi"),
                GlossaryTerm(rawTerm = "天劫", translatedTerm = "Heavenly Tribulation", category = "General", notes = "Divine lightning bolts sent by Heaven to test advancing cultivators"),
                GlossaryTerm(rawTerm = "法宝", translatedTerm = "Dharma Treasure", category = "Item", notes = "Magical artifact nurtured with a cultivator's blood essence"),
                GlossaryTerm(rawTerm = "师父", translatedTerm = "Master (Shifu)", category = "Character", notes = "Respected mentor and teacher"),
                GlossaryTerm(rawTerm = "师兄", translatedTerm = "Senior Martial Brother", category = "Character", notes = "Elder apprentice under the same sect or master"),
                GlossaryTerm(rawTerm = "青竹蜂云剑", translatedTerm = "Bamboo Cloudswarm Swords", category = "Item", notes = "Handcrafted flying swords forged from golden thunder bamboo"),
                GlossaryTerm(rawTerm = "御剑术", translatedTerm = "Sword Kinesis Art", category = "Technique", notes = "Skill allowing a cultivator to ride flying swords through the sky"),
                GlossaryTerm(rawTerm = "七玄门", translatedTerm = "Seven Mysteries Sect", category = "Faction", notes = "Mortal martial arts sect where Han Li began his path")
            )
            glossaryDao.insertTerms(initialGlossary)

            // Seed Sample Novel 1: Record of a Mortal's Journey to Immortality
            val novel1Id = novelDao.insertNovel(
                Novel(
                    title = "凡人修仙传",
                    titleTranslated = "Record of a Mortal's Journey to Immortality",
                    author = "忘语",
                    authorPinyin = "Wang Yu (忘语)",
                    coverGradientIndex = 0,
                    sourceUrl = "https://www.69shu.com/txt/1042.htm",
                    description = "An ordinary young mortal boy from a poor village enters a small martial arts sect by chance. With no extraordinary background or peerless talent, he relies on a mysterious small green vial and shrewd caution to navigate the brutal world of cultivation step by step towards immortality.",
                    genre = "Xianxia / Cultivation",
                    totalChapters = 4
                )
            )

            // Novel 1 Chapters
            chapterDao.insertChapters(
                listOf(
                    Chapter(
                        novelId = novel1Id,
                        chapterIndex = 1,
                        titleRaw = "第一章 山边小村",
                        titleTranslated = "Chapter 1: A Small Village by the Mountain",
                        contentRaw = """二叔从城里回来的时候，带回了一个令全家震惊的消息。

城里的大帮派“七玄门”要招收一批内门弟子。凡是十岁到十五岁之间的少年，无论出身贫富，都可以前去参加入门考核。如果侥幸通过，不仅管吃管住，每个月还有不菲的银子寄回家中。

韩立坐在低矮破旧的门槛上，手里把玩着一块光滑的鹅卵石，默默听着二叔和父亲的商议。

“三哥，小立这孩子向来懂事沉稳，身体也结实。咱们穷苦庄稼人家，一辈子面朝黄土背朝天，能有什么出头之日？要是小立真能进七玄门，那可是光宗耀祖的大好事啊！”二叔抽了一口旱烟，语气诚恳地劝道。

韩父抽动了一下嘴角，粗糙皲裂的大手紧紧按在膝盖上。他看着瘦小却目光澄澈的三儿子韩立，长叹了一口气：“娃儿，你愿意去试试么？”

韩立仰起黝黑清瘦的小脸，没有丝毫犹豫地点了点头：“爹，我想去。要是能挣到银两，小妹就有新衣服穿了，爹娘也不用整日吃糠咽菜。”

山风穿过破旧的窗棂，带来阵阵泥土的芬芳。此时年幼的韩立尚不知道，这一场决定，将彻底斩断凡俗尘缘，开启一段通往浩瀚仙途的万古传奇。""",
                        contentTranslated = """When Second Uncle returned from the city, he brought back news that shook the entire household.

The city's prominent martial faction, the "Seven Mysteries Sect," was recruiting a new batch of inner sect disciples. Any youth between the ages of ten and fifteen, regardless of wealth or background, could take the entrance examination. If one was fortunate enough to pass, the sect would provide full room and board, along with a handsome monthly silver stipend sent directly home.

Han Li sat upon the low, weathered wooden threshold, absently turning a smooth river pebble in his hands as he silently listened to the discussion between his Second Uncle and his father.

"Third Brother, Xiao Li has always been sensible and steady, and his constitution is tough. For us poor farming folk, doomed to face the yellow soil with our backs to the sky for generations, what future is there? If Xiao Li can truly enter the Seven Mysteries Sect, it would bring immense glory to our family lineage!" Second Uncle puffed on his dry tobacco pipe, offering earnest counsel.

Han Li's father gave a faint twitch of his lips, his rough, calloused hands gripping his worn knees tightly. Looking at his slender third son whose eyes shone with deep clarity, he let out a long sigh: "Child, are you willing to go and try?"

Han Li raised his sun-tanned, lean young face and nodded without the slightest hesitation: "Father, I want to go. If I can earn silver, Little Sister can have new clothes, and Mother and Father won't have to eat husk and wild greens every day."

A mountain breeze drifted through the worn lattice window, carrying the sweet scent of moist earth. At this tender moment, the young Han Li had no inkling that this single decision would sever his ties with the mundane realm forever, embarking upon an immortal path of boundless legend.""",
                        isTranslated = true,
                        chapterUrl = "https://www.69shu.com/txt/1042/1.htm",
                        nextChapterUrl = "https://www.69shu.com/txt/1042/2.htm"
                    ),
                    Chapter(
                        novelId = novel1Id,
                        chapterIndex = 2,
                        titleRaw = "第二章 七玄门试炼",
                        titleTranslated = "Chapter 2: The Seven Mysteries Trial",
                        contentRaw = """清晨的落日峰云雾缭绕，宛如人间仙境。

数百名来自各个城镇村落的少年汇聚在宏伟的山门之下，人人神情紧张。石阶宛如一条长龙，蜿蜒通向云雾深处的彩霞峰顶。

一名身穿黑袍的中年执事面无表情地站在高台之上，冷声道：“今日考核，唯有日落之前徒步登顶者方可过关！中途若有体力不支者，立即淘汰！”

钟声长鸣，群童狂奔。

韩立并没有盲目抢先，而是调整呼吸，脚步均匀地迈上石阶。日光渐盛，炽热的阳光炙烤着大地，许多起初冲刺飞快的锦衣少年渐渐气喘吁吁，瘫倒在路旁哭泣。

韩立的汗水浸透了粗布麻衣，双腿如灌了铅一般沉重。但他紧咬牙关，眼中闪烁着异乎寻常的坚毅。每当想要放弃时，脑海中便浮现出母亲期盼的目光和小妹瘦弱的笑脸。

“我一定要登上去！”

一步，又一步。在晚霞彻底染红天际的那一刻，一只满是磨痕的小手终于攀上了试炼崖的最后一级石阶。""",
                        contentTranslated = """The morning mist wreathed Sunset Peak in swirling vapors, resembling a celestial paradise fallen to earth.

Hundreds of youths gathered from surrounding towns and villages stood beneath the grand mountain gate, their faces tight with apprehension. A flight of stone steps wound like a slumbering dragon upwards into the rainbow-tinted clouds of Sunflare Peak.

A stone-faced deacon dressed in black robes stood upon the elevated stone terrace, speaking coldly: "For today's trial, only those who reach the summit on foot before sunset shall pass! Anyone who collapses midway will be disqualified immediately!"

As the bronze bell tolled, the youths surged forward in a frenzy.

Han Li did not rush ahead recklessly. Instead, he steadied his breathing and maintained an even, measured pace up the mountain stairs. As the midday sun climbed high, baking the rugged stone, many of the wealthy youths who had sprinted at the start began to gasp desperately for breath, collapsing in tears beside the trail.

Han Li's coarse hemp tunic was drenched in sweat, his legs heavy as poured lead. Yet he ground his molars together, a remarkable determination blazing in his eyes. Whenever fatigue threatened to overwhelm him, his mother's hopeful gaze and his little sister's fragile smile surfaced in his mind.

'I must reach the top!'

One step, then another. Just as the twilight glow painted the horizon in brilliant vermilion, a scraped and battered hand finally hauled itself onto the very last stone step of the Trial Cliff.""",
                        isTranslated = true,
                        chapterUrl = "https://www.69shu.com/txt/1042/2.htm"
                    ),
                    Chapter(
                        novelId = novel1Id,
                        chapterIndex = 3,
                        titleRaw = "第三章 神秘绿瓶",
                        titleTranslated = "Chapter 3: The Mysterious Green Vial",
                        contentRaw = """深夜，神手谷静谧无声，唯有药圃中偶传几声清脆的虫鸣。

韩立盘坐在简陋的木榻上，就着跳跃的油灯，小心翼翼地从怀中掏出了那只从山崖草丛中偶然捡到的小物件。

这是一只只有拇指大小的深绿色小瓶，表面刻满了奇异繁奥的墨绿花纹，触手温润如玉，非金非木。最为奇异的是，瓶口虽有封口，却无论如何也拔不开。

今夜正值月圆之夜，皎洁的月光透过窗隙倾泻在桌案上。

忽然，不可思议的一幕发生了！

四周空气中的月光仿佛受到了某种难以言喻的吸力，竟化作千万缕晶莹剔透的白色光丝，如飞蛾扑火般缓缓向深绿色小瓶汇聚而去。原本平平无奇的小瓶表面，那些奇异的花纹竟然亮起了柔和的微光，仿佛活了过来！

韩立屏住呼吸，心脏剧烈跳动，下意识地握紧了双拳。

他万万没有想到，正是这只看似不起眼的小小绿瓶，将在未来孕育出夺天地造化的参天造化露，彻底颠覆整个修仙界的法则！""",
                        contentTranslated = null,
                        isTranslated = false,
                        chapterUrl = "https://www.69shu.com/txt/1042/3.htm"
                    ),
                    Chapter(
                        novelId = novel1Id,
                        chapterIndex = 4,
                        titleRaw = "第四章 长春功初成",
                        titleTranslated = "Chapter 4: The Changchun Arts Awakens",
                        contentRaw = """自从墨大夫将那本泛黄的《长春功》秘籍交予韩立之后，已经过去了整整三个月。

在这三个月里，韩立每日除了打理药圃中的名贵草药，其余时间全部用来打坐冥想，试图感应秘籍中记载的那一缕玄之又玄的“天地灵气”。

然而，凡人修仙，谈何容易？同入门的张铁早已将凡俗横练硬功练得虎虎生风，而韩立丹田中却始终空空如也，毫无气感。

“难道我真的没有所谓的灵根天资？”韩立望着夜空中悬挂的明月，心中难免升起一丝惘然。

他深吸了一口气，再次运转长春功第一层心法。随着心神沉入虚无，桌案上的小绿瓶忽然散发出一阵极其微弱的清凉波动，悄然拂过他的四肢百骸。

刹那间，丹田处骤然升起一缕温热微弱的暖流！

这股暖流顺着经脉缓缓流转，宛如甘霖滋润干涸的土地。韩立浑身毛孔舒张，前所未有的舒泰感涌遍全身。

“气感！我终于练出第一缕灵力了！”韩立猛然睁开双眼，清澈的双眸中精芒闪烁。""",
                        contentTranslated = null,
                        isTranslated = false,
                        chapterUrl = "https://www.69shu.com/txt/1042/4.htm"
                    )
                )
            )

            // Seed Sample Novel 2: Battle Through the Heavens
            val novel2Id = novelDao.insertNovel(
                Novel(
                    title = "斗破苍穹",
                    titleTranslated = "Battle Through the Heavens",
                    author = "天蚕土豆",
                    authorPinyin = "Tian Can Tu Dou (天蚕土豆)",
                    coverGradientIndex = 1,
                    sourceUrl = "https://www.biquge.tv/0_1/",
                    description = "Here, there is no magic, no martial arts, only Dou Qi that has multiplied to its absolute pinnacle! Xiao Yan, who was once hailed as a rare genius, suddenly became the laughingstock of the clan after mysteriously losing all his cultivation. What kind of secret lies within his mother's antique ring?",
                    genre = "Eastern Fantasy / Xuanhuan",
                    totalChapters = 2
                )
            )

            chapterDao.insertChapters(
                listOf(
                    Chapter(
                        novelId = novel2Id,
                        chapterIndex = 1,
                        titleRaw = "第一章 陨落的天才",
                        titleTranslated = "Chapter 1: The Fallen Genius",
                        contentRaw = """“斗之力，三段！”

望着测验魔石碑上面闪亮得甚至有些刺眼的五个大字，少年面无表情，唇角噙着一抹自嘲，紧握的手掌因为用力，尖锐的指甲深深地刺进了掌心之中，带来一阵阵钻心的痛。

“萧炎，斗之力，三段！级别：低级！”测验魔石碑之旁，一位中年男子看了一眼碑上所显示出来的信息，语气漠然地将之公布了出来……

中年男子话音刚落，广场上便是不出意外地响起了一阵嘲讽的骚动。

“三段？嘿嘿，果然不出我所料，这个‘天才’这一年又在原地踏步呢！”
“哎，真是把我们萧家的脸都给丢尽了，当年那个四年便修炼至九段斗之力的妖孽天才，如今怎么沦落成这副模样了？”
“谁知道呢，或许当年只是昙花一现吧……”

周围传来的窃窃私语以及讥笑冷嘲，如同一根根尖锐的利刺，狠狠扎在萧炎的心头。

少年缓缓抬起头来，露出一张清秀稚嫩的面孔，漆黑的眸子扫过周围那些曾经对自己阿谀奉承、如今却满脸鄙夷的族人，嘴角挑起一抹冷冽的弧度。

三十年河东，三十年河西，莫欺少年穷！""",
                        contentTranslated = """“Dou Disciple, Third Stage!”

Staring at the five radiant, almost blinding characters gleaming upon the magical test stele, the youth’s expression remained indifferent. A touch of self-mockery lingered on his lips. His fists were clenched so hard that his fingernails dug deep into his palms, sending sharp jolts of pain straight into his heart.

“Xiao Yan, Dou Disciple, Third Stage! Grade: Low!” Beside the testing stele, a middle-aged man glanced at the displayed result and announced it in a completely impassive tone...

No sooner had the man’s words dropped than a wave of mocking clamor inevitably swept across the plaza.

“Third Stage? Heh, exactly as I expected. Our ‘genius’ has once again spent the entire year standing in place!”
“Sigh, he’s truly thrown away all the prestige of our Xiao Clan. Where did the monstrous genius who reached Ninth Stage Dou Disciple in just four years go? How did he fall into such a pathetic state?”
“Who knows? Perhaps his brilliance back then was merely a fleeting flash in the pan...”

The whispers and icy ridicule around him stabbed like venomous needles directly into Xiao Yan’s soul.

The youth slowly raised his head, revealing a delicate and youthful face. His pitch-black eyes swept across his clansmen—people who had once showered him with lavish flattery, but now wore sneering contempt. A cold smirk tugged at the corner of his lips.

Thirty years east of the river, thirty years west of the river—never bully a young man just because he is poor!""",
                        isTranslated = true,
                        chapterUrl = "https://www.biquge.tv/0_1/1.htm"
                    ),
                    Chapter(
                        novelId = novel2Id,
                        chapterIndex = 2,
                        titleRaw = "第二章 黑色古戒",
                        titleTranslated = null,
                        contentRaw = """后山幽静的山崖之巅，微风拂过茂密的青草。

萧炎双手抱头，仰躺在松软的草坪上，嘴里咬着一根细长的青草，呆呆地凝望着天际浮动的白云。

在这个以斗气为尊的大陆上，失去了实力，便等同于失去了一切尊严与未来。曾经的天才光环有多耀眼，如今跌入凡尘的落差便有多痛彻心扉。

萧炎抬起右手，目光落在手指上一枚看似古旧无华的黑色古戒上。

这枚戒指是他母亲临终前留给他的唯一遗物，从小到大他一直贴身佩戴。然而不知为何，自三年前开始，每当他修炼凝聚出斗气，体内的斗之气旋便会在夜间莫名其妙地消散一空。

“母亲，您若在天有灵，能告诉我这究竟是为什么吗？”萧炎喃喃自语。

就在这时，那枚沉寂多年的黑色古戒上，微弱的幽光忽然微微一闪，一道苍老而带着戏谑的声音毫无征兆地在萧炎耳畔炸响：

“嘿嘿，小娃娃，这三年多亏了你日夜不停地供应斗气，老夫这缕残魂才能苏醒啊……”""",
                        contentTranslated = null,
                        isTranslated = false,
                        chapterUrl = "https://www.biquge.tv/0_1/2.htm"
                    )
                )
            )

            // Seed a sample Quote
            quoteDao.insertQuote(
                Quote(
                    novelId = novel2Id,
                    novelTitle = "斗破苍穹 (Battle Through the Heavens)",
                    chapterId = 5,
                    chapterTitle = "Chapter 1: The Fallen Genius",
                    highlightedText = "Thirty years east of the river, thirty years west of the river—never bully a young man just because he is poor!",
                    rawContext = "三十年河东，三十年河西，莫欺少年穷！",
                    userNote = "Classic immortal quote of determination and resilience against mockery.",
                    colorHex = 0xFFFFD166
                )
            )
        }
    }
}

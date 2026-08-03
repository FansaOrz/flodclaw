package com.foldledger.data.seed

import com.foldledger.data.db.AccountDao
import com.foldledger.data.db.AccountEntity
import com.foldledger.data.db.CategoryDao
import com.foldledger.data.db.CategoryEntity
import com.foldledger.domain.model.AccountType
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.repo.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val categories: CategoryRepository,
) {
    suspend fun seedIfNeeded() {
        if (accountDao.count() == 0) {
            listOf(
                AccountEntity(name = "现金", type = AccountType.CASH.name, sortOrder = 0),
                AccountEntity(name = "银行卡", type = AccountType.BANK.name, sortOrder = 1),
                AccountEntity(name = "支付宝", type = AccountType.ALIPAY.name, sortOrder = 2),
                AccountEntity(name = "微信", type = AccountType.WECHAT.name, sortOrder = 3),
            ).forEach { accountDao.upsert(it) }
        }
        if (categoryDao.count() == 0) {
            defaultExpenseCategories().forEachIndexed { index, (name, keywords) ->
                categoryDao.upsert(
                    CategoryEntity(
                        name = name,
                        direction = MoneyDirection.EXPENSE.name,
                        keywordsCsv = keywords.joinToString(","),
                        sortOrder = index,
                    ),
                )
            }
            listOf("工资", "红包收入", "退款", "理财收益", "其他收入").forEachIndexed { index, name ->
                categoryDao.upsert(
                    CategoryEntity(
                        name = name,
                        direction = MoneyDirection.INCOME.name,
                        sortOrder = index,
                    ),
                )
            }
        }
        refreshCategoryKeywords()
        // 回填放到 Application 里延迟执行，避免挡住首屏加载
    }

    /** 仅在有未分类流水时做关键词回填。 */
    suspend fun reclassifyIfNeeded(): Int {
        return categories.reclassifyUncategorized()
    }

    private fun defaultExpenseCategories(): List<Pair<String, List<String>>> = listOf(
        "餐饮" to diningKeywords,
        "交通" to transportKeywords,
        "购物" to shoppingKeywords,
        "居住" to listOf("房租", "物业", "水电", "燃气", "电费", "水费"),
        "运动健身" to fitnessKeywords,
        "娱乐" to listOf("电影", "游戏", "会员", "视频", "爱奇艺", "腾讯视频", "网易云", "Spotify", "演出", "演唱会"),
        "医疗" to listOf("医院", "药店", "挂号", "诊所", "体检"),
        "通讯" to listOf("话费", "流量", "宽带", "中国移动", "中国联通", "中国电信"),
        "理财" to financeKeywords,
        "转账" to listOf("转账", "红包", "微信转账", "扫二维码", "二维码收款"),
        "其他" to emptyList(),
    )

    /**
     * 合并默认关键词：只补齐缺失的，不覆盖用户自行添加的词。
     */
    private suspend fun refreshCategoryKeywords() {
        val map = defaultExpenseCategories().toMap()
        val existing = categoryDao.listActive().associateBy { it.name }
        map.forEach { (name, defaults) ->
            val cat = existing[name]
            if (cat == null) {
                if (name != "其他" || defaults.isNotEmpty()) {
                    categoryDao.upsert(
                        CategoryEntity(
                            name = name,
                            direction = MoneyDirection.EXPENSE.name,
                            keywordsCsv = defaults.joinToString(","),
                            sortOrder = existing.size + 1,
                        ),
                    )
                }
            } else {
                val current = cat.keywordsCsv.split(',', '，')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val missing = defaults.filter { d ->
                    current.none { it.equals(d, ignoreCase = true) }
                }
                if (missing.isNotEmpty()) {
                    categoryDao.upsert(
                        cat.copy(keywordsCsv = (current + missing).joinToString(",")),
                    )
                }
            }
        }
    }

    companion object {
        private val diningKeywords = listOf(
            "美团外卖", "饿了么", "餐厅", "饭店", "食堂", "咖啡", "奶茶", "外卖",
            "必胜客", "Pizza Hut", "PizzaHut", "披萨", "肯德基", "KFC", "麦当劳", "McDonald",
            "星巴克", "Starbucks", "瑞幸", "喜茶", "奈雪", "蜜雪", "古茗", "茶百道",
            "海底捞", "西贝", "汉堡王", "德克士", "华莱士", "萨莉亚", "呷哺",
            "烧烤", "火锅", "小吃", "面馆", "粥", "烘焙", "面包", "蛋糕",
        )
        private val transportKeywords = listOf(
            "滴滴", "滴滴出行", "美团打车", "高德", "高德打车", "地铁", "公交", "打车",
            "高铁", "火车", "机票", "航空", "哈啰", "出行", "停车", "加油", "充电桩",
            "铁路", "12306", "曹操出行", "T3出行",
        )
        private val shoppingKeywords = listOf(
            "淘宝", "天猫", "京东", "拼多多", "超市", "便利店", "美团", "商城",
            "唯品会", "得物", "闲鱼", "苹果", "Apple", "华为", "小米",
        )
        private val fitnessKeywords = listOf(
            "健身", "健身房", "Gym", "GYM", "gym", "OneMore", "OneMoreGYM",
            "乐刻", "超级猩猩", "威尔士", "金吉姆", "游泳", "瑜伽", "普拉提",
            "Keep", "私教", "运动", "球场", "羽毛球", "网球", "高尔夫",
        )
        private val financeKeywords = listOf(
            "理财", "基金", "证券", "股票", "国债", "定投", "申购", "赎回",
            "余额宝", "余利宝", "零钱通", "理财通", "蚂蚁财富", "天天基金",
            "支付宝理财", "微信理财", "券商", "期货", "黄金", "贵金属",
        )
    }
}

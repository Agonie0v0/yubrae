package com.yubrae.app.capture.rules

import com.yubrae.app.data.db.TransactionType

/**
 * 将 AutoAccounting `category.json` 的 57 个标准分类映射为 Yubrae `DefaultSeedData` 分类候选列表。
 */
object JsCategoryMapper {

    private val EXPENSE_MAPPINGS: Map<String, List<String>> = mapOf(
        "快赎理财" to listOf("投资亏损", "金融保险", "其他支出"),
        "基金" to listOf("投资亏损", "金融保险", "其他支出"),
        "黄金" to listOf("配饰首饰", "投资亏损", "金融保险", "其他支出"),
        "债券" to listOf("投资亏损", "金融保险", "其他支出"),
        "外汇" to listOf("汇损手续费", "金融保险", "其他支出"),
        "理财产品" to listOf("投资亏损", "金融保险", "其他支出"),
        "三餐" to listOf("三餐外食", "餐饮美食"),
        "早餐" to listOf("三餐外食", "餐饮美食"),
        "早茶" to listOf("咖啡茶饮", "三餐外食", "餐饮美食"),
        "午餐" to listOf("三餐外食", "餐饮美食"),
        "下午茶" to listOf("咖啡茶饮", "零食甜品", "餐饮美食"),
        "晚餐" to listOf("三餐外食", "餐饮美食"),
        "夜宵" to listOf("三餐外食", "餐饮美食"),
        "电器数码" to listOf("数码配件", "家用电器", "手机电脑", "数码家电"),
        "运动" to listOf("运动健身", "休闲娱乐"),
        "房租" to listOf("房租房贷", "居家物业"),
        "零食" to listOf("零食甜品", "餐饮美食"),
        "饮料" to listOf("咖啡茶饮", "零食甜品", "餐饮美食"),
        "水果" to listOf("水果生鲜", "餐饮美食"),
        "快递" to listOf("快递邮寄", "日用购物"),
        "网购代付" to listOf("网购百货", "日用购物"),
        "烟酒" to listOf("烟酒茶", "餐饮美食"),
        "搬家" to listOf("家政服务", "物业维修", "居家物业", "其他支出"),
        "购物" to listOf("超市便利", "网购百货", "家居日用", "日用购物"),
        "党费会费" to listOf("其他支出"),
        "话费网费" to listOf("话费宽带", "居家物业"),
        "水电煤" to listOf("水电燃气", "居家物业"),
        "保险" to listOf("商业保险", "医疗保险", "金融保险"),
        "穿戴" to listOf("服饰鞋包", "配饰首饰", "服饰美容"),
        "理发" to listOf("理发美甲", "服饰美容"),
        "护肤美妆" to listOf("美妆护肤", "服饰美容"),
        "付费会员" to listOf("软件会员", "影音音乐", "数字订阅"),
        "购买App" to listOf("软件会员", "数字订阅"),
        "游戏" to listOf("游戏充值", "数字订阅", "休闲娱乐"),
        "小说漫画" to listOf("影音音乐", "软件会员", "数字订阅", "书籍资料", "学习成长"),
        "电影" to listOf("电影演出", "休闲娱乐"),
        "旅行" to listOf("酒店民宿", "旅游门票", "差旅住宿"),
        "打赏" to listOf("聚会玩乐", "软件会员", "休闲娱乐", "人情社交"),
        "聚会" to listOf("聚餐请客", "聚会玩乐", "餐饮美食", "休闲娱乐"),
        "收藏" to listOf("特产纪念", "聚会玩乐", "日用购物", "休闲娱乐", "其他支出"),
        "娱乐" to listOf("聚会玩乐", "休闲娱乐"),
        "医疗" to listOf("看病挂号", "买药保健", "医疗健康"),
        "学习" to listOf("书籍资料", "课程培训", "文具办公", "学习成长"),
        "罚款" to listOf("利息罚息", "意外丢失", "其他支出"),
        "捐赠" to listOf("公益捐赠", "其他支出"),
        "公交地铁" to listOf("地铁公交", "交通出行"),
        "打车" to listOf("网约打车", "交通出行"),
        "火车" to listOf("高铁火车", "交通出行"),
        "飞机" to listOf("机票船票", "差旅住宿", "交通出行"),
        "汽车加油" to listOf("加油充电", "养车维修", "交通出行"),
        "育儿" to listOf("奶粉辅食", "童装玩具", "早教培训", "亲子育儿"),
        "发红包" to listOf("红包礼金", "送礼请客", "人情社交"),
        "工资" to listOf("其他支出"),
        "收红包" to listOf("红包礼金", "人情社交"),
        "退款" to listOf("其他支出"),
        "公积金" to listOf("其他支出"),
        "外快" to listOf("其他支出"),
        "其他" to listOf("其他支出")
    )

    private val INCOME_MAPPINGS: Map<String, List<String>> = mapOf(
        "快赎理财" to listOf("基金股票", "银行利息", "投资理财", "其他收入"),
        "基金" to listOf("基金股票", "投资理财", "其他收入"),
        "黄金" to listOf("基金股票", "投资理财", "其他收入"),
        "债券" to listOf("银行利息", "分红收益", "投资理财", "其他收入"),
        "外汇" to listOf("外汇收益", "投资理财", "其他收入"),
        "理财产品" to listOf("基金股票", "分红收益", "投资理财", "其他收入"),
        "房租" to listOf("租金收入", "经营所得", "其他收入"),
        "保险" to listOf("保险理赔", "报销退款", "其他收入"),
        "打赏" to listOf("自媒体稿费", "兼职副业", "其他收入"),
        "发红包" to listOf("微信红包", "礼金往来", "人情礼金", "其他收入"),
        "工资" to listOf("基本工资", "工资薪金", "奖金补贴"),
        "收红包" to listOf("微信红包", "礼金往来", "人情礼金"),
        "退款" to listOf("购物退款", "报销退款"),
        "公积金" to listOf("公积金提取", "政策补贴", "工资薪金", "其他收入"),
        "外快" to listOf("外包私活", "二手闲置", "兼职副业"),
        "其他" to listOf("其他收入")
    )

    fun mapToYubraeCandidates(externalCategory: String, transactionType: String): List<String> {
        val cleaned = externalCategory.trim()
        val isIncome = transactionType.equals(TransactionType.INCOME, ignoreCase = true)
        val fallback = if (isIncome) listOf("其他收入") else listOf("其他支出")
        if (cleaned.isEmpty()) return fallback

        if (isIncome) {
            INCOME_MAPPINGS[cleaned]?.let { return it }
            // 若收入交易匹配到了共享词条（如餐饮/购物退款场景未触发退款词条），回退为包含原词与收入兜底
            return listOf(cleaned) + fallback
        }

        return EXPENSE_MAPPINGS[cleaned] ?: (listOf(cleaned) + fallback)
    }
}

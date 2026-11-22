package com.kairoledger.app.data.db

import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首次启动默认种子数据：
 * - 中文一/二级收支分类
 * - 默认账户（CNY 电子钱包/储蓄卡/信用卡 + USD / EUR / GBP 常用外币账户）
 * - 离线兜底汇率（CNY <-> USD / EUR / GBP 等）
 * - 默认账号指纹（微信、支付宝、常见银行）与常见动账正则规则
 */
object DefaultSeedData {

    suspend fun ensureSeeded(db: KairoDatabase) {
        if (db.ledgerDao().getAll().isEmpty()) {
            db.ledgerDao().insert(
                LedgerEntity(
                    id = 1L,
                    name = "默认账本",
                    currencyCode = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
                    icon = "menu_book",
                    colorHex = 0xFF2AA876L,
                    sortOrder = 0
                )
            )
        }
        if (db.categoryDao().getAll().isEmpty()) {
            seedCategories(db)
        }
        if (db.accountDao().getAll().isEmpty()) {
            seedAccounts(db)
        }
        if (db.exchangeRateDao().getAll().isEmpty()) {
            seedFallbackRates(db)
        }
        if (db.parseRuleDao().getAll().isEmpty()) {
            seedParseRules(db)
        }
    }

    private suspend fun seedCategories(db: KairoDatabase) {
        val dao = db.categoryDao()
        val expenseParents = listOf(
            CategoryEntity(name = "餐饮美食", type = TransactionType.EXPENSE, icon = "restaurant", colorHex = 0xFFEA580CL, sortOrder = 10),
            CategoryEntity(name = "交通出行", type = TransactionType.EXPENSE, icon = "directions_car", colorHex = 0xFF0284C7L, sortOrder = 20),
            CategoryEntity(name = "日用购物", type = TransactionType.EXPENSE, icon = "shopping_bag", colorHex = 0xFFDB2777L, sortOrder = 30),
            CategoryEntity(name = "居家物业", type = TransactionType.EXPENSE, icon = "home", colorHex = 0xFF0D9488L, sortOrder = 40),
            CategoryEntity(name = "数字订阅", type = TransactionType.EXPENSE, icon = "subscriptions", colorHex = 0xFF7C3AEDL, sortOrder = 50),
            CategoryEntity(name = "休闲娱乐", type = TransactionType.EXPENSE, icon = "sports_esports", colorHex = 0xFF9333EAL, sortOrder = 60),
            CategoryEntity(name = "医疗健康", type = TransactionType.EXPENSE, icon = "medical_services", colorHex = 0xFFDC2626L, sortOrder = 70),
            CategoryEntity(name = "差旅住宿", type = TransactionType.EXPENSE, icon = "flight", colorHex = 0xFF2563EBL, sortOrder = 80),
            CategoryEntity(name = "汇损手续费", type = TransactionType.EXPENSE, icon = "currency_exchange", colorHex = 0xFF64748BL, sortOrder = 90),
            CategoryEntity(name = "其他支出", type = TransactionType.EXPENSE, icon = "more_horiz", colorHex = 0xFF475569L, sortOrder = 100)
        )
        val parentIds = mutableMapOf<String, Long>()
        expenseParents.forEach { parent ->
            parentIds[parent.name] = dao.insert(parent)
        }

        val subExpenses = listOf(
            CategoryEntity(parentId = parentIds["餐饮美食"], name = "三餐外食", type = TransactionType.EXPENSE, icon = "lunch_dining", colorHex = 0xFFEA580CL, sortOrder = 11),
            CategoryEntity(parentId = parentIds["餐饮美食"], name = "咖啡茶饮", type = TransactionType.EXPENSE, icon = "local_cafe", colorHex = 0xFFC2410CL, sortOrder = 12),
            CategoryEntity(parentId = parentIds["餐饮美食"], name = "生鲜买菜", type = TransactionType.EXPENSE, icon = "shopping_basket", colorHex = 0xFF16A34AL, sortOrder = 13),
            CategoryEntity(parentId = parentIds["交通出行"], name = "地铁公交", type = TransactionType.EXPENSE, icon = "subway", colorHex = 0xFF0284C7L, sortOrder = 21),
            CategoryEntity(parentId = parentIds["交通出行"], name = "网约打车", type = TransactionType.EXPENSE, icon = "local_taxi", colorHex = 0xFF0369A1L, sortOrder = 22),
            CategoryEntity(parentId = parentIds["日用购物"], name = "网购百货", type = TransactionType.EXPENSE, icon = "local_mall", colorHex = 0xFFDB2777L, sortOrder = 31),
            CategoryEntity(parentId = parentIds["日用购物"], name = "数码硬件", type = TransactionType.EXPENSE, icon = "devices", colorHex = 0xFF4F46E5L, sortOrder = 32),
            CategoryEntity(parentId = parentIds["居家物业"], name = "房租水电", type = TransactionType.EXPENSE, icon = "bolt", colorHex = 0xFF0D9488L, sortOrder = 41),
            CategoryEntity(parentId = parentIds["居家物业"], name = "话费宽带", type = TransactionType.EXPENSE, icon = "wifi", colorHex = 0xFF0F766EL, sortOrder = 42),
            CategoryEntity(parentId = parentIds["数字订阅"], name = "软件会员", type = TransactionType.EXPENSE, icon = "cloud_done", colorHex = 0xFF7C3AEDL, sortOrder = 51),
            CategoryEntity(parentId = parentIds["数字订阅"], name = "AI与云服务", type = TransactionType.EXPENSE, icon = "smart_toy", colorHex = 0xFF6D28D9L, sortOrder = 52)
        )
        dao.insertAll(subExpenses)

        val incomes = listOf(
            CategoryEntity(name = "工资薪金", type = TransactionType.INCOME, icon = "work", colorHex = 0xFF16A34AL, sortOrder = 10),
            CategoryEntity(name = "奖金补贴", type = TransactionType.INCOME, icon = "card_giftcard", colorHex = 0xFF15803DL, sortOrder = 20),
            CategoryEntity(name = "投资理财", type = TransactionType.INCOME, icon = "trending_up", colorHex = 0xFF0D9488L, sortOrder = 30),
            CategoryEntity(name = "报销退款", type = TransactionType.INCOME, icon = "replay", colorHex = 0xFF0284C7L, sortOrder = 40),
            CategoryEntity(name = "兼职副业", type = TransactionType.INCOME, icon = "laptop_mac", colorHex = 0xFF2563EBL, sortOrder = 50),
            CategoryEntity(name = "其他收入", type = TransactionType.INCOME, icon = "attach_money", colorHex = 0xFF475569L, sortOrder = 60)
        )
        dao.insertAll(incomes)
    }

    private suspend fun seedAccounts(db: KairoDatabase) {
        val accountDao = db.accountDao()
        val aliasDao = db.accountAliasDao()

        val wechatId = accountDao.insert(
            AccountEntity(
                name = "微信零钱",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY",
                balanceMicros = 0L,
                icon = "chat",
                colorHex = 0xFF07C160L,
                sortOrder = 10
            )
        )
        val alipayId = accountDao.insert(
            AccountEntity(
                name = "支付宝",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY",
                balanceMicros = 0L,
                icon = "account_balance_wallet",
                colorHex = 0xFF1677FFL,
                sortOrder = 20
            )
        )
        val cashId = accountDao.insert(
            AccountEntity(
                name = "现金",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY",
                balanceMicros = 0L,
                icon = "payments",
                colorHex = 0xFF0F766EL,
                sortOrder = 30
            )
        )
        val debitId = accountDao.insert(
            AccountEntity(
                name = "银行储蓄卡",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY",
                balanceMicros = 0L,
                icon = "account_balance",
                colorHex = 0xFF0B57D0L,
                sortOrder = 40
            )
        )
        val creditId = accountDao.insert(
            AccountEntity(
                name = "全币种信用卡",
                groupType = AccountGroup.CREDIT,
                currencyCode = "CNY",
                balanceMicros = 0L,
                creditLimitMicros = 50_000L * MoneyAmount.AMOUNT_SCALE,
                billingDay = 5,
                repaymentDay = 23,
                icon = "credit_card",
                colorHex = 0xFFDC2626L,
                sortOrder = 50
            )
        )
        val usdId = accountDao.insert(
            AccountEntity(
                name = "美元账户 (USD)",
                groupType = AccountGroup.FOREIGN,
                currencyCode = "USD",
                balanceMicros = 0L,
                icon = "attach_money",
                colorHex = 0xFF1D4ED8L,
                sortOrder = 60
            )
        )
        val eurId = accountDao.insert(
            AccountEntity(
                name = "欧元账户 (EUR)",
                groupType = AccountGroup.FOREIGN,
                currencyCode = "EUR",
                balanceMicros = 0L,
                icon = "euro",
                colorHex = 0xFF0369A1L,
                sortOrder = 70
            )
        )
        val gbpId = accountDao.insert(
            AccountEntity(
                name = "英镑账户 (GBP)",
                groupType = AccountGroup.FOREIGN,
                currencyCode = "GBP",
                balanceMicros = 0L,
                icon = "currency_pound",
                colorHex = 0xFF6D28D9L,
                sortOrder = 80
            )
        )

        aliasDao.insertAll(
            listOf(
                AccountAliasEntity(accountId = wechatId, matchType = AliasMatchType.PACKAGE, pattern = "com.tencent.mm", priority = 100),
                AccountAliasEntity(accountId = wechatId, matchType = AliasMatchType.KEYWORD, pattern = "微信支付", priority = 80),
                AccountAliasEntity(accountId = wechatId, matchType = AliasMatchType.KEYWORD, pattern = "零钱", priority = 70),
                AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.PACKAGE, pattern = "com.eg.android.AlipayGphone", priority = 100),
                AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.KEYWORD, pattern = "支付宝", priority = 85),
                AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.KEYWORD, pattern = "余额宝", priority = 80),
                AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.KEYWORD, pattern = "花呗", priority = 75),
                AccountAliasEntity(accountId = debitId, matchType = AliasMatchType.KEYWORD, pattern = "储蓄卡", priority = 60),
                AccountAliasEntity(accountId = creditId, matchType = AliasMatchType.KEYWORD, pattern = "信用卡", priority = 65),
                AccountAliasEntity(accountId = creditId, matchType = AliasMatchType.KEYWORD, pattern = "Visa", priority = 60),
                AccountAliasEntity(accountId = creditId, matchType = AliasMatchType.KEYWORD, pattern = "Mastercard", priority = 60),
                AccountAliasEntity(accountId = usdId, matchType = AliasMatchType.KEYWORD, pattern = "美元账户", priority = 70),
                AccountAliasEntity(accountId = eurId, matchType = AliasMatchType.KEYWORD, pattern = "欧元账户", priority = 70),
                AccountAliasEntity(accountId = gbpId, matchType = AliasMatchType.KEYWORD, pattern = "英镑账户", priority = 70),
                AccountAliasEntity(accountId = cashId, matchType = AliasMatchType.KEYWORD, pattern = "现金", priority = 40)
            )
        )
    }

    private suspend fun seedFallbackRates(db: KairoDatabase) {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val now = System.currentTimeMillis()
        val rates = CurrencyCatalog.all
            .filter { it.code != CurrencyCatalog.DEFAULT_BASE_CURRENCY }
            .flatMap { info ->
                val toCny = info.fallbackRateToCnyMicros
                val fromCny = MoneyAmount.invertRate(toCny)
                listOf(
                    ExchangeRateEntity(
                        baseCurrency = info.code,
                        targetCurrency = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
                        rateMicros = toCny,
                        date = today,
                        source = "BUILTIN",
                        updatedAt = now
                    ),
                    ExchangeRateEntity(
                        baseCurrency = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
                        targetCurrency = info.code,
                        rateMicros = fromCny,
                        date = today,
                        source = "BUILTIN",
                        updatedAt = now
                    )
                )
            }
        db.exchangeRateDao().upsertAll(rates)
    }

    private suspend fun seedParseRules(db: KairoDatabase) {
        db.parseRuleDao().insertAll(
            listOf(
                ParseRuleEntity(
                    name = "微信支付付款通知",
                    sourcePackage = "com.tencent.mm",
                    channelType = "NOTIFICATION",
                    regexPattern = "(?:微信支付|向)(.*?)付款(?:人民币|RMB)?[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 2,
                    currencyGroup = 0,
                    defaultCurrency = "CNY",
                    merchantGroup = 1,
                    defaultCategoryName = "日用购物",
                    priority = 100,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "微信收款到账通知",
                    sourcePackage = "com.tencent.mm",
                    channelType = "NOTIFICATION",
                    regexPattern = "(?:收款|到账|微信转账)[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
                    transactionType = TransactionType.INCOME,
                    amountGroup = 1,
                    defaultCurrency = "CNY",
                    defaultCategoryName = "其他收入",
                    priority = 95,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "支付宝支付/扣款通知",
                    sourcePackage = "com.eg.android.AlipayGphone",
                    channelType = "NOTIFICATION",
                    regexPattern = "(?:向(.*?)付款|消费|支付|扣款)[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 2,
                    defaultCurrency = "CNY",
                    merchantGroup = 1,
                    defaultCategoryName = "日用购物",
                    priority = 100,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "支付宝收款通知",
                    sourcePackage = "com.eg.android.AlipayGphone",
                    channelType = "NOTIFICATION",
                    regexPattern = "(?:你已成功收款|到账|转入)[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
                    transactionType = TransactionType.INCOME,
                    amountGroup = 1,
                    defaultCurrency = "CNY",
                    defaultCategoryName = "其他收入",
                    priority = 95,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "银行外币消费通用规则(USD/EUR/GBP)",
                    sourcePackage = "*",
                    channelType = "ANY",
                    regexPattern = "(?:尾号(\\d{4})).*?(?:消费|支出|扣款|支付|spent|charged)\\s*(USD|EUR|GBP|HKD|JPY|SGD|CNY|RMB|\\$|€|£|¥)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 3,
                    currencyGroup = 2,
                    defaultCurrency = "USD",
                    accountHintGroup = 1,
                    defaultCategoryName = "数字订阅",
                    priority = 90,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "银行人民币支出通用短信/通知",
                    sourcePackage = "*",
                    channelType = "ANY",
                    regexPattern = "(?:尾号(\\d{4})).*?(?:支出|消费|扣款|付款|转出).*?(?:人民币|RMB|CNY|¥|￥)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 2,
                    defaultCurrency = "CNY",
                    accountHintGroup = 1,
                    defaultCategoryName = "其他支出",
                    priority = 80,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "银行人民币收入/入账短信",
                    sourcePackage = "*",
                    channelType = "ANY",
                    regexPattern = "(?:尾号(\\d{4})).*?(?:收入|入账|转入|到账|工资).*?(?:人民币|RMB|CNY|¥|￥)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?",
                    transactionType = TransactionType.INCOME,
                    amountGroup = 2,
                    defaultCurrency = "CNY",
                    accountHintGroup = 1,
                    defaultCategoryName = "其他收入",
                    priority = 80,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "英文境外卡/Wise/Revolut扣款通知",
                    sourcePackage = "*",
                    channelType = "NOTIFICATION",
                    regexPattern = "(?:You spent|Paid|Charged|Payment of)\\s*(\\$|€|£|USD|EUR|GBP|CNY)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(?:at|to)\\s+([^.\\n]+)",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 2,
                    currencyGroup = 1,
                    defaultCurrency = "USD",
                    merchantGroup = 3,
                    defaultCategoryName = "数字订阅",
                    priority = 88,
                    isBuiltIn = true
                )
            )
        )
    }
}

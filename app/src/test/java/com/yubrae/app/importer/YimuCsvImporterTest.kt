package com.yubrae.app.importer

import com.yubrae.app.core.contract.BudgetProgress
import com.yubrae.app.core.contract.CategoryStat
import com.yubrae.app.core.contract.ConversionPreview
import com.yubrae.app.core.contract.ExchangeRateProvider
import com.yubrae.app.core.contract.LedgerService
import com.yubrae.app.core.contract.MonthlyPoint
import com.yubrae.app.core.contract.NetWorthPoint
import com.yubrae.app.core.contract.NetWorthSummary
import com.yubrae.app.core.contract.RateQuote
import com.yubrae.app.core.contract.RecordTransactionRequest
import com.yubrae.app.core.contract.TrendBucketPoint
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountDao
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.BudgetEntity
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryDao
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DebtRecordEntity
import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.InstallmentPlanEntity
import com.yubrae.app.data.db.LedgerDao
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.RecurringRuleEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagDao
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TransactionDao
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import jxl.Workbook
import jxl.write.DateFormat
import jxl.write.DateTime
import jxl.write.Label
import jxl.write.Number as JxlNumber
import jxl.write.WritableCellFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YimuCsvImporterTest {

    private val testZone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun rfc4180Parser_handlesQuotedCommasNewlinesAndEscapedQuotes() {
        val csv = buildString {
            appendLine("col1,col2,col3,col4")
            append("a,\"1,250.50\",\"line1\nline2\",\"\"\"Pro\"\" Plan\"\r\n")
            append("b,plain,x,\n")
        }

        val rows = Rfc4180CsvParser.parse(csv)
        assertEquals(3, rows.size)
        assertEquals(listOf("col1", "col2", "col3", "col4"), rows[0])
        assertEquals(listOf("a", "1,250.50", "line1\nline2", "\"Pro\" Plan"), rows[1])
        assertEquals(listOf("b", "plain", "x", ""), rows[2])
    }

    @Test
    fun previewAndImportYimuMultiCurrencyCsv_createsMissingEntitiesAndImportsChronologically() = runTest {
        val env = TestEnv()
        env.accountDao.insert(
            AccountEntity(
                id = 1L,
                name = "微信零钱",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY"
            )
        )
        val foodParentId = env.categoryDao.insert(
            CategoryEntity(name = "餐饮美食", type = TransactionType.EXPENSE)
        )

        // Newer row placed before older row to verify chronological sorting oldest -> newest
        val csvContent = """
            日期,收支类型,金额,币种,一级分类,二级分类,账户,转入账户,商品,备注,标签
            2026-03-15 18:00:00,支出,"20.00",USD,数字订阅,AI服务,美元信用卡,,OpenAI,"ChatGPT ""Plus"", monthly",AI
            2026-03-10 09:30:00,支出,35.50,CNY,餐饮美食,咖啡茶饮,微信零钱,,星巴克,拿铁,日常
            2026-03-16 10:00:00,转账,100.00,USD,账户互转,,美元信用卡,欧元储蓄卡,,换汇划转,
        """.trimIndent()
        val utf8BomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            csvContent.toByteArray(Charsets.UTF_8)

        val preview = env.importer.previewCsv(ByteArrayInputStream(utf8BomBytes)).getOrThrow()
        assertEquals("通用记账 CSV", preview.formatName)
        assertEquals(3, preview.totalRows)
        assertEquals(3, preview.validRows)
        assertEquals(0, preview.skippedDuplicateRows)
        assertEquals(listOf("美元信用卡", "欧元储蓄卡"), preview.newAccountNames)
        assertEquals(listOf("数字订阅", "AI服务", "咖啡茶饮"), preview.newCategoryNames)
        assertTrue(preview.detectedCurrencies.containsAll(listOf("USD", "CNY")))

        val summary = env.importer.importCsv(ByteArrayInputStream(utf8BomBytes)).getOrThrow()
        assertEquals(3, summary.importedCount)
        assertEquals(0, summary.skippedDuplicateCount)
        assertEquals(2, summary.createdAccounts)
        assertEquals(3, summary.createdCategories)

        // Verify chronological order of saveTransaction calls
        val saved = env.ledgerService.savedRequests
        assertEquals(3, saved.size)
        val ts0 = LocalDateTime.of(2026, 3, 10, 9, 30, 0).atZone(testZone).toInstant().toEpochMilli()
        val ts1 = LocalDateTime.of(2026, 3, 15, 18, 0, 0).atZone(testZone).toInstant().toEpochMilli()
        val ts2 = LocalDateTime.of(2026, 3, 16, 10, 0, 0).atZone(testZone).toInstant().toEpochMilli()
        assertEquals(ts0, saved[0].timestamp)
        assertEquals(ts1, saved[1].timestamp)
        assertEquals(ts2, saved[2].timestamp)

        // Verify sub-category parent linkage
        val coffeeCat = env.categoryDao.findByNameAndType("咖啡茶饮", TransactionType.EXPENSE)
        assertNotNull(coffeeCat)
        assertEquals(foodParentId, coffeeCat!!.parentId)

        // Verify created USD account has USD currencyCode
        val usdAcc = env.accountDao.getByName("美元信用卡")
        assertNotNull(usdAcc)
        assertEquals("USD", usdAcc!!.currencyCode)
        assertEquals(AccountGroup.CREDIT, usdAcc.groupType)

        // Verify historical date was requested from exchangeRateProvider for cross-currency transfer
        assertTrue(env.rateProvider.queriedDates.contains("2026-03-16"))
        assertEquals(TransactionType.TRANSFER, saved[2].type)
        assertEquals(CaptureChannel.IMPORT, saved[1].sourceType)
        assertEquals("ChatGPT \"Plus\", monthly", saved[1].note)
    }

    @Test
    fun importWeChatCsv_skipsPreambleAndNeutralOrFailedRows() = runTest {
        val env = TestEnv()
        val wechatCsv = """
            微信支付账单明细,,,,,,,,
            昵称：[测试用户],,,,,,,,
            起始时间：[2026-03-01 00:00:00] 终止时间：[2026-03-31 23:59:59],,,,,,,,
            ----------------------微信支付账单明细列表--------------------,,,,,,,,
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2026-03-01 12:15:00,商户消费,瑞幸咖啡,生椰拿铁,支出,¥18.50,零钱,支付成功,1001,2001,/
            2026-03-02 09:00:00,微信红包,张三,/,收入,¥50.00,零钱,已存入零钱,1002,2002,新年红包
            2026-03-03 10:00:00,零钱提现,银行卡,提现,中性交易,¥100.00,零钱,已到账,1003,2003,/
            2026-03-04 11:00:00,商户消费,测试商户,订单,支出,¥9.90,零钱,支付失败,1004,2004,/
        """.trimIndent()
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            wechatCsv.toByteArray(Charsets.UTF_8)

        val preview = env.importer.previewCsv(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals("微信账单 CSV", preview.formatName)
        assertEquals(4, preview.totalRows)
        assertEquals(2, preview.validRows)
        assertEquals(2, preview.warnings.size)

        val summary = env.importer.importCsv(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(2, summary.importedCount)
        val txs = env.transactionDao.getAll()
        assertEquals(2, txs.size)
        val expenseTx = txs.first { it.type == TransactionType.EXPENSE }
        assertEquals(185_000L, expenseTx.origAmountMicros)
        assertEquals("CNY", expenseTx.origCurrency)
        assertEquals("瑞幸咖啡", expenseTx.merchant)
        assertEquals("生椰拿铁", expenseTx.note)
    }

    @Test
    fun importAlipayCsvInGb18030_decodesChineseWithoutBomAndSkipsClosedOrders() = runTest {
        val env = TestEnv()
        val alipayCsv = """
            支付宝交易记录明细查询,,,,,,,,,,,,
            账号:[demo@example.com],,,,,,,,,,,,
            ---------------------------------交易记录明细列表------------------------------------,,,,,,,,,,,,
            交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
            2026-04-05 08:20:10,餐饮美食,肯德基,kfc@pay,早餐套餐,支出,26.80,花呗,交易成功,A001,M001,/
            2026-04-06 14:10:00,退款,淘宝店铺,tb@pay,退货退款,收入,120.00,余额宝,退款成功,A002,M002,运费险
            2026-04-07 16:00:00,日用购物,某商户,m@pay,未付款订单,支出,88.00,支付宝,交易关闭,A003,M003,/
            ------------------------------------------------------------------------------------,,,,,,,,,,,,
            共3笔记录,,,,,,,,,,,,
        """.trimIndent()
        val gb18030Bytes = alipayCsv.toByteArray(Charset.forName("GB18030"))

        val preview = env.importer.previewCsv(ByteArrayInputStream(gb18030Bytes)).getOrThrow()
        assertEquals("支付宝账单 CSV", preview.formatName)
        assertEquals(3, preview.totalRows)
        assertEquals(2, preview.validRows)
        assertEquals(1, preview.warnings.size)

        val summary = env.importer.importCsv(ByteArrayInputStream(gb18030Bytes)).getOrThrow()
        assertEquals(2, summary.importedCount)
        val huabei = env.accountDao.getByName("花呗")
        assertNotNull(huabei)
        assertEquals(AccountGroup.CREDIT, huabei!!.groupType)
    }

    @Test
    fun reimportSameCsv_deduplicatesStrictlyByTuple() = runTest {
        val env = TestEnv()
        val csv = """
            日期,收支类型,金额,币种,类别,账户,备注
            2026-05-01 12:00:00,支出,45.00,CNY,餐饮美食,现金,午饭
            2026-05-01 12:00:00,支出,45.00,CNY,餐饮美食,现金,午饭
            2026-05-02 18:30:00,收入,300.00,CNY,兼职副业,现金,设计费
        """.trimIndent()
        val bytes = csv.toByteArray(Charsets.UTF_8)

        // First import: 1 intra-CSV duplicate skipped, 2 imported
        val firstSummary = env.importer.importCsv(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(2, firstSummary.importedCount)
        assertEquals(1, firstSummary.skippedDuplicateCount)

        // Preview after import: all rows are now duplicates, no new accounts/categories
        val secondPreview = env.importer.previewCsv(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(3, secondPreview.totalRows)
        assertEquals(0, secondPreview.validRows)
        assertEquals(3, secondPreview.skippedDuplicateRows)
        assertTrue(secondPreview.newAccountNames.isEmpty())
        assertTrue(secondPreview.newCategoryNames.isEmpty())

        // Append 1 new transaction with different timestamp
        val csvWithOneNew = csv + "\n2026-05-03 09:00:00,支出,12.00,CNY,交通出行,现金,地铁"
        val secondSummary = env.importer.importCsv(
            ByteArrayInputStream(csvWithOneNew.toByteArray(Charsets.UTF_8))
        ).getOrThrow()
        assertEquals(1, secondSummary.importedCount)
        assertEquals(3, secondSummary.skippedDuplicateCount)
        assertEquals(3, env.transactionDao.getAll().size)
    }

    @Test
    fun tsvAndSemicolonDelimiter_autoDetectedWithoutBreakingStandardCsv() = runTest {
        val tsv = "日期\t收支类型\t金额\t类别\t账户\t备注\n" +
            "2026-06-10 08:30:00\t支出\t\"1,250.50\"\t餐饮美食\t微信零钱\t聚餐,含服务费\n"
        val tsvDecoded = Rfc4180CsvParser.decodeAndParse(tsv.toByteArray(Charsets.UTF_8), "yimu.tsv")
        assertEquals('\t', tsvDecoded.delimiter)
        assertEquals(2, tsvDecoded.rows.size)
        assertEquals("1,250.50", tsvDecoded.rows[1][2])

        val semiCsv = "日期;收支类型;金额;类别;账户;备注\n" +
            "2026-06-11 09:00:00;支出;42.00;交通出行;公交卡;地铁\n"
        val semiDecoded = Rfc4180CsvParser.decodeAndParse(semiCsv.toByteArray(Charsets.UTF_8), "export.csv")
        assertEquals(';', semiDecoded.delimiter)
        assertEquals(2, semiDecoded.rows.size)

        val env = TestEnv()
        val preview = env.importer.previewFile(
            "yimu.tsv",
            ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        ).getOrThrow()
        assertEquals(1, preview.validRows)
        val summary = env.importer.importFile(
            "yimu.tsv",
            ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        ).getOrThrow()
        assertEquals(1, summary.importedCount)
        assertEquals(12_505_000L, env.transactionDao.getAll().first().origAmountMicros)
    }

    @Test
    fun importYimu24ColumnCsv_handlesPlanARefundLedgersTagsReimburseExcludeArrowAndMultiCurrency() = runTest {
        val env = TestEnv(enableExtendedDaos = true)
        env.ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本", currencyCode = "CNY"))
        env.tagDao.insert(
            TagEntity(
                id = 1L,
                name = "外卖",
                groupName = "渠道平台",
                colorHex = 0xFFEA580CL,
                sortOrder = 10,
                isSystem = true
            )
        )
        env.accountDao.insert(
            AccountEntity(
                id = 1L,
                name = "招行储蓄卡",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY"
            )
        )

        val csv24 = """
            日期,收支类型,金额,类别,二级分类,账户,账本,退款,退款明细,优惠,备注,标签,报销账户,报销金额,报销明细,多币种,地址,创建用户,其他,附件1,附件2,附件3,附件4,附件5
            2026-06-01 12:30:00,支出,120.00,餐饮美食,外卖,微信零钱,家庭账本,35.50,退部分餐费,5.00,公司加班点餐,外卖 聚餐;加班,,,,,软件园A座,张三,,,,,,
            2026-06-02 09:15:00,支出,72.50,差旅住宿,酒店民宿,招行储蓄卡,出差账本,0,,,机场打车,差旅,企业对公户,72.50,机场发票已报销,USD 10.00,T3航站楼,张三,不计入预算,,,,,
            2026-06-03 14:00:00,不计入收支,200.00,人情社交,红包礼金,微信零钱,默认账本,,,,代垫体检费,,,,,,医院,张三,待报销,,,,,
            2026-06-04 10:00:00,还款,500.00,,,招行储蓄卡 -> 招行信用卡,默认账本,,,,信用卡自动还款,,,,,,,,,,,,,
        """.trimIndent()
        val bytes = csv24.toByteArray(Charsets.UTF_8)

        val preview = env.importer.previewFile("yimu_24.csv", ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals("一木记账 CSV", preview.formatName)
        assertEquals(4, preview.totalRows)
        assertEquals(4, preview.validRows)
        assertEquals(0, preview.skippedDuplicateRows)
        assertEquals(listOf("家庭账本", "出差账本"), preview.newLedgerNames)
        assertEquals(listOf("聚餐", "加班", "差旅"), preview.newTagNames)
        assertEquals(2, preview.reimburseCount)
        assertEquals(1, preview.excludedStatsCount)
        assertEquals(1, preview.refundGeneratedCount)
        assertTrue(preview.detectedCurrencies.containsAll(listOf("CNY", "USD")))

        val summary = env.importer.importFile("yimu_24.csv", ByteArrayInputStream(bytes)).getOrThrow()
        // 4 rows + 1 generated refund transaction = 5 saved transactions
        assertEquals(5, summary.importedCount)
        assertEquals(0, summary.skippedDuplicateCount)
        assertEquals(2, summary.createdLedgers)
        assertEquals(3, summary.createdTags)
        assertEquals(1, summary.generatedRefundCount)

        // Verify existing tag "外卖" was NOT overwritten, while new tags got groupName = "导入"
        val existingTakeoutTag = env.tagDao.findByName("外卖")!!
        assertEquals(0xFFEA580CL, existingTakeoutTag.colorHex)
        assertTrue(existingTakeoutTag.isSystem)
        val newTag = env.tagDao.findByName("聚餐")!!
        assertEquals("导入", newTag.groupName)
        assertFalse(newTag.isSystem)

        // Verify Plan A expense + linked refund INCOME
        val allTxs = env.transactionDao.getAll()
        assertEquals(5, allTxs.size)
        val ts1 = LocalDateTime.of(2026, 6, 1, 12, 30, 0).atZone(testZone).toInstant().toEpochMilli()
        val origExpense = allTxs.first { it.timestamp == ts1 && it.type == TransactionType.EXPENSE }
        val linkedRefund = allTxs.firstOrNull { it.refundOfTxId == origExpense.id }
        assertNotNull(linkedRefund)
        assertEquals(TransactionType.INCOME, linkedRefund!!.type)
        assertEquals(ts1 + 1000L, linkedRefund.timestamp)
        assertEquals(355_000L, linkedRefund.origAmountMicros)
        assertEquals(origExpense.categoryId, linkedRefund.categoryId)
        assertEquals(origExpense.ledgerId, linkedRefund.ledgerId)
        assertEquals("退部分餐费", linkedRefund.note)
        assertEquals("外卖,聚餐,加班", origExpense.tags)
        assertEquals(MoneyAmount.parseToMicros("5.00")!!, origExpense.discountAmountMicros)
        assertEquals(0L, origExpense.feeAmountMicros)
        assertFalse(origExpense.note.contains("优惠 5.00"))
        assertTrue(origExpense.note.contains("地址:软件园A座"))
        assertTrue(origExpense.note.contains("创建用户:张三"))

        // Verify multi-currency row (USD 10.00 -> CNY 72.50) + REIMBURSED + excludeFromBudget
        val ts2 = LocalDateTime.of(2026, 6, 2, 9, 15, 0).atZone(testZone).toInstant().toEpochMilli()
        val fcyTx = allTxs.first { it.timestamp == ts2 }
        assertEquals("USD", fcyTx.origCurrency)
        assertEquals(100_000L, fcyTx.origAmountMicros)
        assertEquals(725_000L, fcyTx.accountAmountMicros)
        assertEquals(ReimburseStatus.REIMBURSED, fcyTx.reimburseStatus)
        assertTrue(fcyTx.excludeFromBudget)
        assertFalse(fcyTx.excludeFromStats)

        // Verify 不计入收支 + PENDING reimburse mapped to default ledger 1L
        val ts3 = LocalDateTime.of(2026, 6, 3, 14, 0, 0).atZone(testZone).toInstant().toEpochMilli()
        val neutralTx = allTxs.first { it.timestamp == ts3 }
        assertEquals(TransactionType.EXPENSE, neutralTx.type)
        assertTrue(neutralTx.excludeFromStats)
        assertEquals(ReimburseStatus.PENDING, neutralTx.reimburseStatus)
        assertEquals(1L, neutralTx.ledgerId)

        // Verify single-column A->B transfer split
        val ts4 = LocalDateTime.of(2026, 6, 4, 10, 0, 0).atZone(testZone).toInstant().toEpochMilli()
        val transferTx = allTxs.first { it.timestamp == ts4 }
        assertEquals(TransactionType.TRANSFER, transferTx.type)
        assertEquals(env.accountDao.getByName("招行储蓄卡")!!.id, transferTx.accountId)
        assertEquals(env.accountDao.getByName("招行信用卡")!!.id, transferTx.toAccountId)

        // Re-import must yield 0 duplicates (including generated refund!)
        val secondPreview = env.importer.previewFile("yimu_24.csv", ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(0, secondPreview.validRows)
        assertEquals(4, secondPreview.skippedDuplicateRows)
        assertEquals(0, secondPreview.refundGeneratedCount)
        assertTrue(secondPreview.newLedgerNames.isEmpty())
        assertTrue(secondPreview.newTagNames.isEmpty())

        val secondSummary = env.importer.importFile("yimu_24.csv", ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(0, secondSummary.importedCount)
        assertEquals(4, secondSummary.skippedDuplicateCount)
        assertEquals(0, secondSummary.generatedRefundCount)
        assertEquals(5, env.transactionDao.getAll().size)
    }

    @Test
    fun importXlsxSpreadsheet_handlesSparseColumnsSharedStringsRichTextAnd1900Dates() = runTest {
        val env = TestEnv(enableExtendedDaos = true)
        env.ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本"))

        // Excel 1900 serial 46096.75 = 2026-03-15 18:00:00
        val xlsxBytes = buildMinimalXlsx(
            sharedStringsXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="5" uniqueCount="5">
                  <si><t>日期</t></si>
                  <si><t>收支类型</t></si>
                  <si><t>金额</t></si>
                  <si><t>账户</t></si>
                  <si><r><t>星</t></r><r><t>巴克</t></r><rPh sb="0" eb="1"><t>IGNORED</t></rPh></si>
                </sst>
            """.trimIndent(),
            stylesXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <numFmts count="2">
                    <numFmt numFmtId="164" formatCode="[Red]#,##0.00"/>
                    <numFmt numFmtId="165" formatCode="yyyy&quot;年&quot;m&quot;月&quot;d&quot;日&quot; hh:mm:ss"/>
                  </numFmts>
                  <cellXfs count="3">
                    <xf numFmtId="0"/>
                    <xf numFmtId="164"/>
                    <xf numFmtId="22"/>
                  </cellXfs>
                </styleSheet>
            """.trimIndent(),
            sheet1Xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="inlineStr"><is><t>导出说明</t></is></c></row>
                  </sheetData>
                </worksheet>
            """.trimIndent(),
            sheet2Xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="inlineStr"><is><t>类别</t></is></c>
                      <c r="E1" t="s"><v>3</v></c>
                      <c r="F1" t="inlineStr"><is><t>备注</t></is></c>
                      <c r="G1" t="inlineStr"><is><t>其他</t></is></c>
                    </row>
                    <row r="2">
                      <c r="A2" s="2"><v>46096.75</v></c>
                      <c r="B2" t="inlineStr"><is><t>支出</t></is></c>
                      <c r="C2" s="1"><v>38.50</v></c>
                      <c r="E2" t="inlineStr"><is><t>微信零钱</t></is></c>
                      <c r="F2" t="s"><v>4</v></c>
                      <c r="G2" t="inlineStr"><is><t>不计入预算</t></is></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        val preview = env.importer.previewFile("yimu.xlsx", ByteArrayInputStream(xlsxBytes)).getOrThrow()
        assertEquals("通用记账 XLSX", preview.formatName)
        assertEquals(1, preview.validRows)

        val summary = env.importer.importFile("yimu.xlsx", ByteArrayInputStream(xlsxBytes)).getOrThrow()
        assertEquals(1, summary.importedCount)

        val tx = env.transactionDao.getAll().single()
        val expectedTs = LocalDateTime.of(2026, 3, 15, 18, 0, 0)
            .atZone(testZone).toInstant().toEpochMilli()
        assertEquals(expectedTs, tx.timestamp)
        assertEquals(385_000L, tx.origAmountMicros)
        assertEquals("微信零钱", env.accountDao.getById(tx.accountId)?.name)
        assertEquals("星巴克", tx.note)
        assertTrue(tx.excludeFromBudget)
    }

    @Test
    fun importXlsBinaryViaJxl_preservesUtcDateAndNumericPrecision() = runTest {
        val env = TestEnv(enableExtendedDaos = true)
        env.ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本"))

        val baos = ByteArrayOutputStream()
        val workbook = Workbook.createWorkbook(baos)
        val summarySheet = workbook.createSheet("目录", 0)
        summarySheet.addCell(Label(0, 0, "导出信息"))

        val billSheet = workbook.createSheet("账单", 1)
        val headers = listOf("日期", "收支类型", "金额", "类别", "账户", "备注", "标签")
        headers.forEachIndexed { col, title ->
            billSheet.addCell(Label(col, 0, title))
        }
        val dateFormat = WritableCellFormat(DateFormat("yyyy-MM-dd HH:mm:ss"))
        val utcDate = Date.from(LocalDateTime.of(2026, 3, 15, 18, 0, 0).toInstant(ZoneOffset.UTC))
        billSheet.addCell(DateTime(0, 1, utcDate, dateFormat, DateTime.GMT))
        billSheet.addCell(Label(1, 1, "支出"))
        billSheet.addCell(JxlNumber(2, 1, 123.45))
        billSheet.addCell(Label(3, 1, "日用购物"))
        billSheet.addCell(Label(4, 1, "支付宝"))
        billSheet.addCell(Label(5, 1, "JXL测试"))
        billSheet.addCell(Label(6, 1, "网购"))
        workbook.write()
        workbook.close()

        val xlsBytes = baos.toByteArray()
        val preview = env.importer.previewFile("legacy.xls", ByteArrayInputStream(xlsBytes)).getOrThrow()
        assertEquals("通用记账 XLS", preview.formatName)
        assertEquals(1, preview.validRows)

        val summary = env.importer.importFile("legacy.xls", ByteArrayInputStream(xlsBytes)).getOrThrow()
        assertEquals(1, summary.importedCount)

        val tx = env.transactionDao.getAll().single()
        val expectedTs = LocalDateTime.of(2026, 3, 15, 18, 0, 0)
            .atZone(testZone).toInstant().toEpochMilli()
        assertEquals(expectedTs, tx.timestamp)
        assertEquals(1_234_500L, tx.origAmountMicros)
        assertEquals("网购", tx.tags)
    }

    @Test
    fun importZipArchiveWithBillAndReceiptImages_persistsAttachmentsAndDeduplicates() = runTest {
        val tempDir = Files.createTempDirectory("kairo_imp_receipts").toFile()
        try {
            val env = TestEnv(enableExtendedDaos = true, receiptsDir = tempDir)
            env.ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本"))

            val csvContent = """
                日期,收支类型,金额,类别,二级分类,账户,账本,退款,退款明细,优惠,备注,标签,报销账户,报销金额,报销明细,多币种,地址,创建用户,其他,附件1,附件2,附件3,附件4,附件5
                2026-07-01 09:00:00,支出,55.00,餐饮美食,咖啡茶饮,微信零钱,默认账本,,,,拿铁与面包,线下,,,,,,,,attachments/coffee.jpg,receipt2.png,,,
            """.trimIndent()
            val jpgBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
            val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 4, 5, 6)

            val zipBaos = ByteArrayOutputStream()
            ZipOutputStream(zipBaos).use { zos ->
                zos.putNextEntry(ZipEntry("yimu_bill.csv"))
                zos.write(csvContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("attachments/coffee.jpg"))
                zos.write(jpgBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("nested/receipt2.png"))
                zos.write(pngBytes)
                zos.closeEntry()
            }
            val zipBytes = zipBaos.toByteArray()

            val preview = env.importer.previewFile("backup.zip", ByteArrayInputStream(zipBytes)).getOrThrow()
            assertEquals("一木记账 CSV (压缩包)", preview.formatName)
            assertEquals(1, preview.validRows)
            assertEquals(2, preview.attachedImageCount)

            val summary = env.importer.importFile("backup.zip", ByteArrayInputStream(zipBytes)).getOrThrow()
            assertEquals(1, summary.importedCount)
            assertEquals(2, summary.attachedImageCount)

            val tx = env.transactionDao.getAll().single()
            val savedPaths = tx.attachmentPaths.split(";").filter { it.isNotBlank() }
            assertEquals(2, savedPaths.size)
            val file0 = File(savedPaths[0])
            val file1 = File(savedPaths[1])
            assertTrue(file0.exists() && file0.name.startsWith("receipt_imp_") && file0.name.endsWith(".jpg"))
            assertTrue(file1.exists() && file1.name.startsWith("receipt_imp_") && file1.name.endsWith(".png"))
            assertArrayEquals(jpgBytes, file0.readBytes())
            assertArrayEquals(pngBytes, file1.readBytes())

            // Re-import zip should be fully deduplicated
            val reimport = env.importer.importFile("backup.zip", ByteArrayInputStream(zipBytes)).getOrThrow()
            assertEquals(0, reimport.importedCount)
            assertEquals(1, reimport.skippedDuplicateCount)
            assertEquals(0, reimport.attachedImageCount)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun importCsv_whenDefaultLedger1Deleted_mapsBlankAndDefaultLedgerToRemainingActiveLedger() = runTest {
        val env = TestEnv(enableExtendedDaos = true)
        val activeLedgerId = env.ledgerDao.insert(
            LedgerEntity(id = 2L, name = "个人账本", sortOrder = 0)
        )
        env.accountDao.insert(
            AccountEntity(id = 1L, name = "微信零钱", groupType = AccountGroup.WALLET, currencyCode = "CNY")
        )
        env.categoryDao.insert(
            CategoryEntity(id = 1L, name = "餐饮", type = TransactionType.EXPENSE)
        )

        val csv = """
            日期,收支类型,金额,币种,一级分类,二级分类,账户,转入账户,商品,备注,标签,账本
            2026-04-01 12:00:00,支出,25.00,CNY,餐饮,,微信零钱,,午饭,空账本行,,
            2026-04-02 12:00:00,支出,18.00,CNY,餐饮,,微信零钱,,早饭,默认账本别名行,,默认账本
        """.trimIndent()

        val summary = env.importer.importFile(
            "no_default_ledger.csv",
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        ).getOrThrow()

        assertEquals(2, summary.importedCount)
        assertEquals(listOf(activeLedgerId), env.ledgerDao.getAll().map { it.id })
        assertFalse(env.ledgerDao.getAll().any { it.name == "默认账本" })
        assertTrue(env.transactionDao.getAll().all { it.ledgerId == activeLedgerId })
    }

    @Test
    fun importYimuCsv_parsesStructuredDiscountAndFeeColumnsAndKeepsNonNumericFallbackInNote() = runTest {
        val env = TestEnv(enableExtendedDaos = true)
        env.ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本", currencyCode = "CNY"))

        val csv = """
            日期,收支类型,金额,币种,类别,二级分类,账户,转入账户,账本,优惠,手续费,备注
            2026-08-01 12:00:00,支出,48.00,CNY,餐饮美食,午餐,微信零钱,,默认账本,2.00,0,工作餐
            2026-08-02 09:00:00,转账,48.00,CNY,,,招行储蓄卡,交通卡,默认账本,2.00,,充值满50减2
            2026-08-03 10:00:00,转账,100.10,CNY,,,微信零钱,招行储蓄卡,默认账本,,0.10,零钱提现
            2026-08-04 15:00:00,收入,99.40,CNY,兼职副业,,招行储蓄卡,,默认账本,,0.60,收款扣费
            2026-08-05 18:00:00,支出,0.00,CNY,餐饮美食,咖啡,微信零钱,,默认账本,50.00,,满50减50免单
            2026-08-06 20:00:00,支出,30.00,CNY,日用购物,,微信零钱,,默认账本,满减券,免收,非数字备注回退
            2026-08-07 11:00:00,转账,98.00,USD,,,美元信用卡,欧元储蓄卡,默认账本,2.00,,跨币种优惠划转
        """.trimIndent()

        val preview = env.importer.previewFile(
            "yimu_discount_fee.csv",
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        ).getOrThrow()
        assertEquals(7, preview.totalRows)
        assertEquals(7, preview.validRows)

        val summary = env.importer.importFile(
            "yimu_discount_fee.csv",
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        ).getOrThrow()
        assertEquals(7, summary.importedCount)

        val requests = env.ledgerService.savedRequests
        assertEquals(7, requests.size)

        // 1. 支出含优惠：不重复扣减金额，结构化写入 discountAmountMicros，不重复拼入备注
        assertEquals(MoneyAmount.parseToMicros("48.00")!!, requests[0].origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("2.00")!!, requests[0].discountAmountMicros)
        assertEquals(0L, requests[0].feeAmountMicros)
        assertEquals("工作餐", requests[0].note)

        // 2. 同币种转账含优惠：manualToAmountMicros 为 null，交由仓库按 orig + discount - fee 推导 50.00
        assertEquals(TransactionType.TRANSFER, requests[1].type)
        assertEquals(MoneyAmount.parseToMicros("48.00")!!, requests[1].origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("2.00")!!, requests[1].discountAmountMicros)
        assertEquals(0L, requests[1].feeAmountMicros)
        assertNull(requests[1].manualToAmountMicros)
        assertEquals("充值满50减2", requests[1].note)

        // 3. 同币种提现含手续费：结构化写入 feeAmountMicros，manualToAmountMicros 为 null
        assertEquals(TransactionType.TRANSFER, requests[2].type)
        assertEquals(MoneyAmount.parseToMicros("100.10")!!, requests[2].origAmountMicros)
        assertEquals(0L, requests[2].discountAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("0.10")!!, requests[2].feeAmountMicros)
        assertNull(requests[2].manualToAmountMicros)
        assertEquals("零钱提现", requests[2].note)

        // 4. 收入含手续费
        assertEquals(TransactionType.INCOME, requests[3].type)
        assertEquals(MoneyAmount.parseToMicros("99.40")!!, requests[3].origAmountMicros)
        assertEquals(0L, requests[3].discountAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("0.60")!!, requests[3].feeAmountMicros)
        assertEquals("收款扣费", requests[3].note)

        // 5. 满 50 减 50 零元免单支出允许导入
        assertEquals(TransactionType.EXPENSE, requests[4].type)
        assertEquals(0L, requests[4].origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("50.00")!!, requests[4].discountAmountMicros)
        assertEquals("满50减50免单", requests[4].note)

        // 6. 无法解析为数字的优惠/手续费文本保留到备注
        assertEquals(MoneyAmount.parseToMicros("30.00")!!, requests[5].origAmountMicros)
        assertEquals(0L, requests[5].discountAmountMicros)
        assertEquals(0L, requests[5].feeAmountMicros)
        assertTrue(requests[5].note.contains("优惠 满减券"))
        assertTrue(requests[5].note.contains("手续费 免收"))

        // 7. 跨币种转账含优惠：按有效面值 (98 + 2 = 100 USD) * 0.92 = 92.00 EUR 折算转入额
        assertEquals(TransactionType.TRANSFER, requests[6].type)
        assertEquals(MoneyAmount.parseToMicros("98.00")!!, requests[6].origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("2.00")!!, requests[6].discountAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("92.00")!!, requests[6].manualToAmountMicros)
    }

    private fun buildMinimalXlsx(
        sharedStringsXml: String,
        stylesXml: String,
        sheet1Xml: String,
        sheet2Xml: String
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun put(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                </Types>
                """.trimIndent()
            )
            put(
                "_rels/.rels",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """.trimIndent()
            )
            put(
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Summary" sheetId="1" r:id="rId1"/>
                    <sheet name="Bills" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>
                """.trimIndent()
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                </Relationships>
                """.trimIndent()
            )
            put("xl/sharedStrings.xml", sharedStringsXml)
            put("xl/styles.xml", stylesXml)
            put("xl/worksheets/sheet1.xml", sheet1Xml)
            put("xl/worksheets/sheet2.xml", sheet2Xml)
        }
        return baos.toByteArray()
    }

    private inner class TestEnv(
        enableExtendedDaos: Boolean = false,
        receiptsDir: File? = null
    ) {
        val accountDao = FakeAccountDao()
        val categoryDao = FakeCategoryDao()
        val transactionDao = FakeTransactionDao()
        val ledgerDao = FakeLedgerDao()
        val tagDao = FakeTagDao()
        val rateProvider = FakeExchangeRateProvider()
        val ledgerService = FakeLedgerService(accountDao, transactionDao, rateProvider)
        val importer = YimuCsvImporter(
            accountDao = accountDao,
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            exchangeRateProvider = rateProvider,
            ledgerService = ledgerService,
            settingsProvider = { AppSettings(baseCurrency = "CNY") },
            zoneId = testZone,
            ledgerDao = if (enableExtendedDaos) ledgerDao else null,
            tagDao = if (enableExtendedDaos) tagDao else null,
            receiptsDir = receiptsDir
        )
    }

    private class FakeExchangeRateProvider : ExchangeRateProvider {
        val queriedDates = mutableListOf<String?>()

        override fun observeRates(): Flow<List<ExchangeRateEntity>> = MutableStateFlow(emptyList())

        override suspend fun getRate(fromCurrency: String, toCurrency: String, onDate: String?): RateQuote {
            queriedDates.add(onDate)
            val rate = when {
                fromCurrency == toCurrency -> 1_000_000L
                fromCurrency == "USD" && toCurrency == "CNY" -> 7_250_000L
                fromCurrency == "USD" && toCurrency == "EUR" -> 920_000L
                fromCurrency == "EUR" && toCurrency == "CNY" -> 7_800_000L
                else -> 1_000_000L
            }
            return RateQuote(fromCurrency, toCurrency, rate, onDate ?: "2026-03-16", "MOCK")
        }

        override suspend fun refreshFromNetwork(baseCurrency: String): Result<Int> = Result.success(0)

        override suspend fun saveCustomRate(fromCurrency: String, toCurrency: String, rateMicros: Long) {}
    }

    private class FakeLedgerService(
        private val accountDao: FakeAccountDao,
        private val transactionDao: FakeTransactionDao,
        private val rateProvider: FakeExchangeRateProvider
    ) : LedgerService {
        val savedRequests = mutableListOf<RecordTransactionRequest>()

        override fun observeActiveAccounts(): Flow<List<AccountEntity>> = accountDao.observeActive()
        override fun observeAllAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()
        override fun observeTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAll()
        override fun observePendingTransactions(): Flow<List<TransactionEntity>> = transactionDao.observePending()
        override fun observePendingCount(): Flow<Int> = transactionDao.observePendingCount()
        override fun observeCategories(): Flow<List<CategoryEntity>> = MutableStateFlow(emptyList())
        override fun observeBudgets(): Flow<List<BudgetEntity>> = MutableStateFlow(emptyList())
        override fun observeRecurringRules(): Flow<List<RecurringRuleEntity>> = MutableStateFlow(emptyList())

        override suspend fun previewConversion(
            origAmountMicros: Long,
            origCurrency: String,
            accountId: Long,
            manualAccountAmountMicros: Long?,
            manualRateToAccountMicros: Long?,
            baseCurrency: String?
        ): ConversionPreview {
            val acc = accountDao.getById(accountId)!!
            val rateToAcc = manualRateToAccountMicros ?: rateProvider.getRate(origCurrency, acc.currencyCode).rateMicros
            val accAmount = manualAccountAmountMicros ?: MoneyAmount.convert(origAmountMicros, rateToAcc)
            val base = baseCurrency ?: "CNY"
            val rateToBase = rateProvider.getRate(origCurrency, base).rateMicros
            return ConversionPreview(
                origAmountMicros = origAmountMicros,
                origCurrency = origCurrency,
                accountAmountMicros = accAmount,
                accountCurrency = acc.currencyCode,
                rateToAccountMicros = rateToAcc,
                baseAmountMicros = MoneyAmount.convert(origAmountMicros, rateToBase),
                baseCurrency = base,
                rateToBaseMicros = rateToBase,
                rateSource = "MOCK"
            )
        }

        override suspend fun saveTransaction(request: RecordTransactionRequest): Result<TransactionEntity> {
            savedRequests.add(request)
            val acc = accountDao.getById(request.accountId)!!
            val toAcc = request.toAccountId?.let { accountDao.getById(it) }
            val rateToAcc = request.manualRateToAccountMicros
                ?: if (request.manualAccountAmountMicros != null && request.origAmountMicros > 0L) {
                    MoneyAmount.computeRate(request.origAmountMicros, request.manualAccountAmountMicros)
                } else {
                    rateProvider.getRate(request.origCurrency, acc.currencyCode).rateMicros
                }
            val accAmount = request.manualAccountAmountMicros
                ?: MoneyAmount.convert(request.origAmountMicros, rateToAcc)
            val rateToBase = if (request.manualAccountAmountMicros != null && acc.currencyCode == "CNY") {
                rateToAcc
            } else {
                rateProvider.getRate(request.origCurrency, "CNY").rateMicros
            }
            val entity = TransactionEntity(
                type = request.type,
                timestamp = request.timestamp,
                origAmountMicros = request.origAmountMicros,
                origCurrency = request.origCurrency,
                accountId = request.accountId,
                accountAmountMicros = accAmount,
                accountCurrency = acc.currencyCode,
                toAccountId = toAcc?.id,
                toAmountMicros = request.manualToAmountMicros,
                toCurrency = toAcc?.currencyCode,
                rateToAccountMicros = rateToAcc,
                rateToBaseMicros = rateToBase,
                baseCurrency = "CNY",
                categoryId = request.categoryId,
                merchant = request.merchant,
                note = request.note,
                tags = request.tags,
                reimburseStatus = request.reimburseStatus,
                sourceType = request.sourceType,
                rawEventId = request.rawEventId,
                status = request.status,
                ledgerId = request.ledgerId.takeIf { it > 0L } ?: 0L,
                excludeFromStats = request.excludeFromStats,
                excludeFromBudget = request.excludeFromBudget,
                refundOfTxId = request.refundOfTxId,
                attachmentPaths = request.attachmentPaths,
                reimbursedTxId = request.reimbursedTxId,
                discountAmountMicros = if (request.type == TransactionType.INCOME) 0L else request.discountAmountMicros,
                feeAmountMicros = request.feeAmountMicros
            )
            val id = transactionDao.insert(entity)
            return Result.success(entity.copy(id = id))
        }

        override suspend fun deleteTransaction(transactionId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun confirmTransaction(transactionId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun confirmAllPending(): Result<Int> = Result.success(0)
        override suspend fun saveAccount(account: AccountEntity): Long = accountDao.insert(account)
        override suspend fun reorderAccounts(orderedAccountIds: List<Long>): Result<Unit> = Result.success(Unit)
        override suspend fun archiveAccount(accountId: Long, archived: Boolean) {}
        override suspend fun deleteAccount(accountId: Long, migrateToAccountId: Long?): Result<Unit> = Result.success(Unit)
        override suspend fun calibrateAccountBalance(
            accountId: Long,
            targetBalanceMicros: Long,
            note: String,
            createRecord: Boolean
        ): Result<TransactionEntity?> =
            Result.failure(UnsupportedOperationException())
        override suspend fun saveCategory(category: CategoryEntity): Long = 0L
        override suspend fun deleteCategory(categoryId: Long) {}
        override suspend fun saveBudget(budget: BudgetEntity): Long = 0L
        override suspend fun deleteBudget(budgetId: Long) {}
        override suspend fun saveRecurringRule(rule: RecurringRuleEntity): Long = 0L
        override suspend fun deleteRecurringRule(ruleId: Long) {}
        override suspend fun processDueRecurringRules(nowMillis: Long): Int = 0
        override suspend fun computeNetWorth(displayCurrency: String): NetWorthSummary =
            NetWorthSummary(displayCurrency, 0L, 0L, 0L, emptyMap())
        override suspend fun computeMonthlyTrend(monthsBack: Int, displayCurrency: String): List<MonthlyPoint> = emptyList()
        override suspend fun computeCategoryStats(startMillis: Long, endMillis: Long, type: String, displayCurrency: String): List<CategoryStat> = emptyList()
        override suspend fun computeBudgetProgress(year: Int, month: Int): List<BudgetProgress> = emptyList()
        override fun observeLedgers(): Flow<List<LedgerEntity>> = MutableStateFlow(emptyList())
        override suspend fun saveLedger(ledger: LedgerEntity): Long = 1L
        override suspend fun archiveLedger(ledgerId: Long, archived: Boolean) {}
        override suspend fun deleteLedger(ledgerId: Long, migrateToLedgerId: Long?): Result<Unit> = Result.success(Unit)
        override suspend fun batchUpdateCategory(txIds: List<Long>, categoryId: Long?): Result<Int> = Result.success(0)
        override suspend fun batchMoveTransactionsAccount(txIds: List<Long>, targetAccountId: Long): Result<Int> = Result.success(0)
        override suspend fun batchDeleteTransactions(txIds: List<Long>): Result<Int> = Result.success(0)
        override suspend fun settleReimbursements(txIds: List<Long>, targetAccountId: Long, timestamp: Long, note: String): Result<TransactionEntity> =
            Result.failure(UnsupportedOperationException())
        override fun observeDebts(): Flow<List<DebtRecordEntity>> = MutableStateFlow(emptyList())
        override suspend fun saveDebt(debt: DebtRecordEntity, syncAccountBalance: Boolean): Long = 0L
        override suspend fun settleDebt(debtId: Long, settleAmountMicros: Long, accountId: Long?, note: String): Result<DebtRecordEntity> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteDebt(debtId: Long) {}
        override fun observeInstallments(): Flow<List<InstallmentPlanEntity>> = MutableStateFlow(emptyList())
        override suspend fun saveInstallment(plan: InstallmentPlanEntity): Long = 0L
        override suspend fun recordInstallmentPeriod(planId: Long): Result<TransactionEntity> = Result.failure(UnsupportedOperationException())
        override suspend fun deleteInstallment(planId: Long) {}
        override suspend fun computeNetWorthHistory(monthsBack: Int, displayCurrency: String): List<NetWorthPoint> = emptyList()
        override suspend fun computeTrendBuckets(granularity: String, anchorDateMillis: Long, customStartMillis: Long?, customEndMillis: Long?, ledgerId: Long, displayCurrency: String): List<TrendBucketPoint> = emptyList()
        override suspend fun computeCategoryStatsForLedger(startMillis: Long, endMillis: Long, type: String, ledgerId: Long, displayCurrency: String): List<CategoryStat> = emptyList()
        override suspend fun computeBudgetProgressForLedger(year: Int, month: Int, ledgerId: Long): List<BudgetProgress> = emptyList()
        override fun observeTags(): Flow<List<TagEntity>> = MutableStateFlow(emptyList())
        override suspend fun saveTag(tag: TagEntity): Long = 0L
        override suspend fun deleteTag(tagId: Long) {}
        override suspend fun ensurePresetCatalogSeeded(): Pair<Int, Int> = 0 to 0
    }

    private class FakeAccountDao : AccountDao {
        private val items = LinkedHashMap<Long, AccountEntity>()
        private val flow = MutableStateFlow<List<AccountEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeActive(): Flow<List<AccountEntity>> = flow.map { list -> list.filter { !it.archived } }
        override fun observeAll(): Flow<List<AccountEntity>> = flow
        override suspend fun getAll(): List<AccountEntity> = items.values.toList()
        override suspend fun getById(id: Long): AccountEntity? = items[id]
        override suspend fun getByName(name: String): AccountEntity? = items.values.firstOrNull { it.name == name }

        override suspend fun insert(account: AccountEntity): Long {
            val id = if (account.id > 0L) account.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = account.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(accounts: List<AccountEntity>) {
            accounts.forEach { insert(it) }
        }

        override suspend fun update(account: AccountEntity) {
            items[account.id] = account
            publish()
        }

        override suspend fun adjustBalance(accountId: Long, deltaMicros: Long, updatedAt: Long) {
            val cur = items[accountId] ?: return
            items[accountId] = cur.copy(balanceMicros = cur.balanceMicros + deltaMicros, updatedAt = updatedAt)
            publish()
        }

        override suspend fun updateSortOrder(accountId: Long, sortOrder: Int, updatedAt: Long) {
            val cur = items[accountId] ?: return
            items[accountId] = cur.copy(sortOrder = sortOrder, updatedAt = updatedAt)
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }

    private class FakeCategoryDao : CategoryDao {
        private val items = LinkedHashMap<Long, CategoryEntity>()
        private val flow = MutableStateFlow<List<CategoryEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeAll(): Flow<List<CategoryEntity>> = flow
        override suspend fun getAll(): List<CategoryEntity> = items.values.toList()
        override suspend fun getById(id: Long): CategoryEntity? = items[id]
        override suspend fun findByNameAndType(name: String, type: String): CategoryEntity? =
            items.values.firstOrNull { it.name == name && it.type == type }

        override suspend fun insert(category: CategoryEntity): Long {
            val id = if (category.id > 0L) category.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = category.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(categories: List<CategoryEntity>) {
            categories.forEach { insert(it) }
        }

        override suspend fun update(category: CategoryEntity) {
            items[category.id] = category
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }

    private class FakeTransactionDao : TransactionDao {
        private val items = LinkedHashMap<Long, TransactionEntity>()
        private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.sortedByDescending { it.timestamp } }

        override fun observeAll(): Flow<List<TransactionEntity>> = flow
        override fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.timestamp in startMillis..endMillis } }
        override fun observePending(): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.status == "PENDING" } }
        override fun observePendingCount(): Flow<Int> =
            observePending().map { it.size }
        override suspend fun getById(id: Long): TransactionEntity? = items[id]
        override suspend fun getAll(): List<TransactionEntity> = items.values.toList()
        override suspend fun getBetween(startMillis: Long, endMillis: Long): List<TransactionEntity> =
            items.values.filter { it.timestamp in startMillis..endMillis }

        override suspend fun getByLedgerId(ledgerId: Long): List<TransactionEntity> =
            items.values.filter { it.ledgerId == ledgerId }

        override suspend fun insert(tx: TransactionEntity): Long {
            val id = if (tx.id > 0L) tx.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = tx.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(txs: List<TransactionEntity>) {
            txs.forEach { insert(it) }
        }

        override suspend fun update(tx: TransactionEntity) {
            items[tx.id] = tx
            publish()
        }

        override suspend fun confirmById(id: Long, updatedAt: Long) {
            val cur = items[id] ?: return
            items[id] = cur.copy(status = "CONFIRMED", updatedAt = updatedAt)
            publish()
        }

        override suspend fun confirmAllPending(updatedAt: Long) {
            items.replaceAll { _, tx ->
                if (tx.status == "PENDING") tx.copy(status = "CONFIRMED", updatedAt = updatedAt) else tx
            }
            publish()
        }

        override suspend fun updateCategoryForIds(ids: List<Long>, categoryId: Long?, updatedAt: Long) {
            val idSet = ids.toHashSet()
            items.replaceAll { _, tx ->
                if (tx.id in idSet) tx.copy(categoryId = categoryId, updatedAt = updatedAt) else tx
            }
            publish()
        }

        override suspend fun markReimbursed(ids: List<Long>, reimbursedTxId: Long, updatedAt: Long) {
            val idSet = ids.toHashSet()
            items.replaceAll { _, tx ->
                if (tx.id in idSet) tx.copy(reimburseStatus = "REIMBURSED", reimbursedTxId = reimbursedTxId, updatedAt = updatedAt) else tx
            }
            publish()
        }

        override suspend fun migrateLedger(fromLedgerId: Long, toLedgerId: Long, updatedAt: Long) {
            items.replaceAll { _, tx ->
                if (tx.ledgerId == fromLedgerId) tx.copy(ledgerId = toLedgerId, updatedAt = updatedAt) else tx
            }
            publish()
        }

        override suspend fun clearRefundRefsForLedger(deletedLedgerId: Long, updatedAt: Long) {
            val deletedIds = items.values.asSequence().filter { it.ledgerId == deletedLedgerId }.map { it.id }.toHashSet()
            if (deletedIds.isEmpty()) return
            items.replaceAll { _, tx ->
                if (tx.ledgerId != deletedLedgerId && tx.refundOfTxId in deletedIds) {
                    tx.copy(refundOfTxId = null, updatedAt = updatedAt)
                } else {
                    tx
                }
            }
            publish()
        }

        override suspend fun clearReimbursedRefsForLedger(deletedLedgerId: Long, updatedAt: Long) {
            val deletedIds = items.values.asSequence().filter { it.ledgerId == deletedLedgerId }.map { it.id }.toHashSet()
            if (deletedIds.isEmpty()) return
            items.replaceAll { _, tx ->
                if (tx.ledgerId != deletedLedgerId && tx.reimbursedTxId in deletedIds) {
                    tx.copy(reimbursedTxId = null, updatedAt = updatedAt)
                } else {
                    tx
                }
            }
            publish()
        }

        override suspend fun deleteByLedgerId(ledgerId: Long) {
            items.entries.removeAll { it.value.ledgerId == ledgerId }
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }

    private class FakeLedgerDao : LedgerDao {
        private val items = LinkedHashMap<Long, LedgerEntity>()
        private val flow = MutableStateFlow<List<LedgerEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.sortedWith(compareBy({ it.sortOrder }, { it.id })) }

        override fun observeActive(): Flow<List<LedgerEntity>> = flow.map { list -> list.filter { !it.archived } }
        override fun observeAll(): Flow<List<LedgerEntity>> = flow
        override suspend fun getAll(): List<LedgerEntity> = flow.value
        override suspend fun getById(id: Long): LedgerEntity? = items[id]

        override suspend fun insert(ledger: LedgerEntity): Long {
            val id = if (ledger.id > 0L) ledger.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = ledger.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(ledgers: List<LedgerEntity>) {
            ledgers.forEach { insert(it) }
        }

        override suspend fun update(ledger: LedgerEntity) {
            items[ledger.id] = ledger
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }

    private class FakeTagDao : TagDao {
        private val items = LinkedHashMap<Long, TagEntity>()
        private val flow = MutableStateFlow<List<TagEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.sortedWith(compareBy({ it.sortOrder }, { it.id })) }

        override fun observeAll(): Flow<List<TagEntity>> = flow
        override suspend fun getAll(): List<TagEntity> = flow.value
        override suspend fun getById(id: Long): TagEntity? = items[id]
        override suspend fun findByName(name: String): TagEntity? = items.values.firstOrNull { it.name == name }

        override suspend fun insert(tag: TagEntity): Long {
            val existing = items.values.firstOrNull { it.name == tag.name }
            val id = when {
                tag.id > 0L -> tag.id
                existing != null -> existing.id
                else -> nextId++
            }
            if (id >= nextId) nextId = id + 1
            if (existing != null && existing.id != id) {
                items.remove(existing.id)
            }
            items[id] = tag.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(tags: List<TagEntity>) {
            tags.forEach { insert(it) }
        }

        override suspend fun update(tag: TagEntity) {
            items[tag.id] = tag
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }
}

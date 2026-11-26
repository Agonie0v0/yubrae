import AppIntents
import Foundation
import UIKit
import XCTest
@testable import Yubrae

final class ExternalBillAndUpdateTests: XCTestCase {

    // MARK: - External URL Scheme Tests

    func testParseYubraeAndLegacyKairoledgerURLs() throws {
        let nowMillis: Int64 = 1_772_500_000_000

        let url1 = try XCTUnwrap(URL(string: "yubrae://addbill?money=15.50&currency=USD&type=EXPENSE&category=%E6%95%B0%E5%AD%97%E8%AE%A2%E9%98%85&account=%E7%BE%8E%E5%85%83%E8%B4%A6%E6%88%B7&remark=OpenAI%20Plus&time=1772000000&showPrompt=false"))
        let p1 = try XCTUnwrap(ExternalBillUrlParser.parse(url: url1, nowMillis: nowMillis))
        XCTAssertEqual(p1.origAmountMicros, 155_000)
        XCTAssertEqual(p1.currency, "USD")
        XCTAssertEqual(p1.type, TransactionType.EXPENSE)
        XCTAssertEqual(p1.categoryName, "数字订阅")
        XCTAssertEqual(p1.accountNameOrHint, "美元账户")
        XCTAssertEqual(p1.merchant, "OpenAI Plus")
        XCTAssertEqual(p1.note, "OpenAI Plus")
        XCTAssertEqual(p1.timestamp, 1_772_000_000_000) // 秒级自动转毫秒
        XCTAssertFalse(p1.showFloatingPrompt)

        let url2 = try XCTUnwrap(URL(string: "kairoledger://record?amount=8800&type=1&accountName=%E9%93%B6%E8%A1%8C%E5%82%A8%E8%93%84%E5%8D%A1&shopName=%E5%85%AC%E5%8F%B8%E5%8F%91%E8%96%AA"))
        let p2 = try XCTUnwrap(ExternalBillUrlParser.parse(url: url2, nowMillis: nowMillis))
        XCTAssertEqual(p2.origAmountMicros, 88_000_000)
        XCTAssertEqual(p2.type, TransactionType.INCOME)
        XCTAssertEqual(p2.accountNameOrHint, "银行储蓄卡")
        XCTAssertEqual(p2.merchant, "公司发薪")
        XCTAssertTrue(p2.showFloatingPrompt)

        // 不支持的 Scheme 与非法金额返回 nil
        XCTAssertNil(ExternalBillUrlParser.parse(urlString: "https://example.com/addbill?money=10"))
        XCTAssertNil(ExternalBillUrlParser.parse(urlString: "yubrae://addbill?money=0"))
        XCTAssertNil(ExternalBillUrlParser.parse(urlString: "yubrae://addbill?money=abc"))

        // buildURL 往返校验
        let built = try XCTUnwrap(ExternalBillUrlParser.buildURL(
            amount: "29.90",
            type: TransactionType.EXPENSE,
            currency: "CNY",
            category: "咖啡茶饮",
            account: "微信零钱",
            remark: "瑞幸生椰拿铁"
        ))
        let reparsed = try XCTUnwrap(ExternalBillUrlParser.parse(url: built))
        XCTAssertEqual(reparsed.origAmountMicros, 299_000)
        XCTAssertEqual(reparsed.categoryName, "咖啡茶饮")
        XCTAssertEqual(reparsed.accountNameOrHint, "微信零钱")
    }

    @MainActor
    func testHandleIncomingURLRecordsTransactionIntoStore() throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("yubrae-url-test-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: dir) }

        let store = LedgerStore(storageDirectory: dir, useKeychain: false)
        let url = try XCTUnwrap(ExternalBillUrlParser.buildURL(
            amount: "36.50",
            type: TransactionType.EXPENSE,
            currency: "CNY",
            category: "外卖",
            account: "支付宝",
            remark: "午饭外卖"
        ))
        let saved = try XCTUnwrap(try store.handleIncomingURL(url))
        XCTAssertEqual(saved.origAmountMicros, 365_000)
        XCTAssertEqual(saved.sourceType, CaptureChannel.AUTO_ACCOUNTING)
        let alipay = try XCTUnwrap(store.accounts.first(where: { $0.name == "支付宝" }))
        XCTAssertEqual(alipay.balanceMicros, -365_000)
    }

    // MARK: - Local Smart Natural Language Parser Tests

    func testSmartTextBillParserMultiClauseColloquialInput() throws {
        let (accounts, aliases) = DefaultSeedData.makeDefaultAccounts()
        let categories = DefaultSeedData.makeDefaultCategories()
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = tz
        let anchorDate = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 6, day: 15, hour: 14, minute: 0)))
        let anchorMillis = Int64((anchorDate.timeIntervalSince1970 * 1000.0).rounded())

        let input = "昨天晚上用微信买瑞幸咖啡35块5，然后滴滴打车42元，今天上午发工资8000，再从银行储蓄卡转到支付宝500元"
        let drafts = SmartTextBillParser.parseLocalBatch(
            text: input,
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: anchorMillis,
            timeZone: tz
        )

        XCTAssertEqual(drafts.count, 4)

        // 1. 昨天晚上 瑞幸咖啡 35.50 微信
        XCTAssertEqual(drafts[0].type, TransactionType.EXPENSE)
        XCTAssertEqual(drafts[0].origAmountMicros, 355_000)
        XCTAssertEqual(drafts[0].origCurrency, "CNY")
        XCTAssertEqual(drafts[0].categoryName, "咖啡茶饮")
        XCTAssertEqual(drafts[0].accountId, accounts.first(where: { $0.name == "微信零钱" })?.id)
        let day0 = cal.component(.day, from: Date(timeIntervalSince1970: Double(drafts[0].timestamp) / 1000.0))
        XCTAssertEqual(day0, 14) // 昨天

        // 2. 滴滴打车 42.00（继承昨天偏移）
        XCTAssertEqual(drafts[1].type, TransactionType.EXPENSE)
        XCTAssertEqual(drafts[1].origAmountMicros, 420_000)
        XCTAssertEqual(drafts[1].categoryName, "网约打车")
        let day1 = cal.component(.day, from: Date(timeIntervalSince1970: Double(drafts[1].timestamp) / 1000.0))
        XCTAssertEqual(day1, 14)

        // 3. 今天上午 发工资 8000
        XCTAssertEqual(drafts[2].type, TransactionType.INCOME)
        XCTAssertEqual(drafts[2].origAmountMicros, 80_000_000)
        XCTAssertEqual(drafts[2].categoryName, "工资薪金")
        let day2 = cal.component(.day, from: Date(timeIntervalSince1970: Double(drafts[2].timestamp) / 1000.0))
        XCTAssertEqual(day2, 15)

        // 4. 从银行储蓄卡转到支付宝 500
        XCTAssertEqual(drafts[3].type, TransactionType.TRANSFER)
        XCTAssertEqual(drafts[3].origAmountMicros, 5_000_000)
        XCTAssertEqual(drafts[3].accountId, accounts.first(where: { $0.name == "银行储蓄卡" })?.id)
        XCTAssertEqual(drafts[3].toAccountId, accounts.first(where: { $0.name == "支付宝" })?.id)
    }

    func testSmartTextBillParserForeignCurrency() {
        let (accounts, aliases) = DefaultSeedData.makeDefaultAccounts()
        let categories = DefaultSeedData.makeDefaultCategories()
        let drafts = SmartTextBillParser.parseLocalBatch(
            text: "OpenAI 订阅 $20.00",
            accounts: accounts,
            categories: categories,
            aliases: aliases
        )
        XCTAssertEqual(drafts.count, 1)
        XCTAssertEqual(drafts[0].origCurrency, "USD")
        XCTAssertEqual(drafts[0].origAmountMicros, 200_000)
        XCTAssertEqual(drafts[0].categoryName, "AI与云服务")
        XCTAssertEqual(drafts[0].accountId, accounts.first(where: { $0.currencyCode == "USD" })?.id)
    }

    // MARK: - WebDAV Multi-Status XML Parsing Tests

    func testWebDavMultiStatusXmlParsing() throws {
        let xml = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
          <D:response>
            <D:href>/dav/Yubrae/</D:href>
            <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
          </D:response>
          <d:response xmlns:d="DAV:">
            <d:href>/dav/Yubrae/yubrae_backup_20260501_100000_000.klbk</d:href>
            <d:propstat>
              <d:prop>
                <d:getcontentlength>1024</d:getcontentlength>
                <d:getlastmodified>Fri, 01 May 2026 10:00:00 GMT</d:getlastmodified>
                <d:resourcetype/>
              </d:prop>
            </d:propstat>
          </d:response>
          <response>
            <href>/dav/Yubrae/yubrae_backup_20260502_120000_000.klbk</href>
            <propstat>
              <prop>
                <getcontentlength>2048</getcontentlength>
                <getlastmodified>Sat, 02 May 2026 12:00:00 GMT</getlastmodified>
                <resourcetype/>
              </prop>
            </propstat>
          </response>
          <D:response>
            <D:href>/dav/Yubrae/readme.txt</D:href>
            <D:propstat><D:prop><D:getcontentlength>12</D:getcontentlength></D:prop></D:propstat>
          </D:response>
        </D:multistatus>
        """
        let dirURL = try XCTUnwrap(URL(string: "https://dav.jianguoyun.com/dav/Yubrae/"))
        let files = WebDavClient.parseMultiStatusXML(xml, dirURL: dirURL)
        XCTAssertEqual(files.count, 2)
        XCTAssertEqual(files[0].name, "yubrae_backup_20260502_120000_000.klbk")
        XCTAssertEqual(files[0].sizeBytes, 2048)
        XCTAssertEqual(files[1].name, "yubrae_backup_20260501_100000_000.klbk")
        XCTAssertEqual(files[1].sizeBytes, 1024)
    }

    // MARK: - AppUpdateChecker Semver, Mirror & IPA Matching Tests

    func testSemverComparisonAndMirrorValidation() {
        XCTAssertEqual(AppUpdateChecker.compareVersions("v1.10.0", "1.2.0"), 1)
        XCTAssertEqual(AppUpdateChecker.compareVersions("1.1.0", "v1.1.0-debug"), 0)
        XCTAssertEqual(AppUpdateChecker.compareVersions("v1.1.0-perf", "1.1.0"), 0)
        XCTAssertEqual(AppUpdateChecker.compareVersions("1.2.0", "1.2.0-beta.1"), 1)
        XCTAssertEqual(AppUpdateChecker.compareVersions("1.2.0-beta.1", "1.2.0"), -1)
        XCTAssertEqual(AppUpdateChecker.compareVersions("1.0.9", "1.1.0"), -1)

        XCTAssertEqual(AppUpdateChecker.validateMirrorPrefix(""), .valid(normalizedPrefix: ""))
        XCTAssertEqual(
            AppUpdateChecker.validateMirrorPrefix("https://gh-proxy.com"),
            .valid(normalizedPrefix: "https://gh-proxy.com/")
        )
        if case .invalid = AppUpdateChecker.validateMirrorPrefix("http://example.com/") {
            // expected
        } else {
            XCTFail("Non-HTTPS remote mirror must be rejected")
        }
        if case .invalid = AppUpdateChecker.validateMirrorPrefix("https://gh-proxy.com/?a=1") {
            // expected
        } else {
            XCTFail("Mirror with query string must be rejected")
        }
    }

    func testIpaAssetMatchingAndReleaseEvaluation() {
        let release = GitHubReleaseDto(
            tagName: "v1.2.0",
            name: "余白 v1.2.0 双端互通版",
            body: "- 新增 iOS 原生版\n- 支持 WebDAV 与 .klbk 双向同步",
            draft: false,
            prerelease: false,
            publishedAt: "2026-06-01T08:00:00Z",
            htmlUrl: "https://github.com/Agonie0v0/yubrae/releases/tag/v1.2.0",
            assets: [
                GitHubReleaseAssetDto(name: "yubrae-v1.2.0-release.apk", size: 12_000_000, browserDownloadUrl: "https://github.com/Agonie0v0/yubrae/releases/download/v1.2.0/yubrae-v1.2.0-release.apk"),
                GitHubReleaseAssetDto(name: "Yubrae-simulator.ipa", size: 8_000_000, browserDownloadUrl: "https://github.com/Agonie0v0/yubrae/releases/download/v1.2.0/Yubrae-simulator.ipa"),
                GitHubReleaseAssetDto(name: "yubrae-v1.2.0-ios-unsigned.ipa", size: 9_500_000, browserDownloadUrl: "https://github.com/Agonie0v0/yubrae/releases/download/v1.2.0/yubrae-v1.2.0-ios-unsigned.ipa"),
                GitHubReleaseAssetDto(name: "SHA256SUMS.txt", size: 256, browserDownloadUrl: "https://github.com/Agonie0v0/yubrae/releases/download/v1.2.0/SHA256SUMS.txt")
            ]
        )

        let result = AppUpdateChecker.evaluateRelease(
            release,
            currentVersion: "1.1.0",
            mirrorPrefix: "https://gh-proxy.com"
        )
        guard case .updateAvailable(let info) = result else {
            return XCTFail("Expected updateAvailable, got \(result)")
        }
        let expectedDirectUrl = "https://github.com/Agonie0v0/yubrae/releases/download/v1.2.0/yubrae-v1.2.0-ios-unsigned.ipa"
        XCTAssertEqual(info.versionName, "1.2.0")
        XCTAssertEqual(info.ipaAssetName, "yubrae-v1.2.0-ios-unsigned.ipa")
        XCTAssertEqual(info.ipaDownloadUrl, expectedDirectUrl)
        XCTAssertEqual(
            AppUpdateChecker.applyMirrorPrefix(rawUrl: expectedDirectUrl, mirrorPrefix: "https://gh-proxy.com"),
            "https://gh-proxy.com/\(expectedDirectUrl)"
        )
        XCTAssertEqual(
            AppUpdateChecker.applyMirrorPrefix(rawUrl: expectedDirectUrl, mirrorPrefix: "https://mirror-b.example.com/"),
            "https://mirror-b.example.com/\(expectedDirectUrl)"
        )
        XCTAssertEqual(info.htmlUrl, "https://github.com/Agonie0v0/yubrae/releases/tag/v1.2.0")
        XCTAssertNotNil(info.sha256AssetUrl)

        let upToDate = AppUpdateChecker.evaluateRelease(release, currentVersion: "1.2.0")
        XCTAssertEqual(upToDate, .upToDate(currentVersion: "1.2.0", latestTag: "v1.2.0"))
    }

    // MARK: - RootTab Navigation & YubraeTab Tests

    func testYubraeTabOrderingMetadataAndShiftedBounds() {
        XCTAssertEqual(YubraeTab.allCases, [.transactions, .assets, .stats, .mine])
        XCTAssertEqual(YubraeTab.allCases.map(\.rawValue), [0, 1, 2, 3])

        for tab in YubraeTab.allCases {
            XCTAssertFalse(tab.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            XCTAssertFalse(tab.systemImage.isEmpty)
            XCTAssertFalse(tab.selectedSystemImage.isEmpty)
        }

        XCTAssertEqual(YubraeTab.transactions.title, "明细")
        XCTAssertEqual(YubraeTab.assets.title, "资产")
        XCTAssertEqual(YubraeTab.stats.title, "统计")
        XCTAssertEqual(YubraeTab.mine.title, "我的")

        // 首尾越界钳制
        XCTAssertEqual(YubraeTab.transactions.shifted(by: -1), .transactions)
        XCTAssertEqual(YubraeTab.transactions.shifted(by: -99), .transactions)
        XCTAssertEqual(YubraeTab.transactions.shifted(by: Int.min), .transactions)
        XCTAssertEqual(YubraeTab.mine.shifted(by: 1), .mine)
        XCTAssertEqual(YubraeTab.mine.shifted(by: 99), .mine)
        XCTAssertEqual(YubraeTab.mine.shifted(by: Int.max), .mine)

        // 中间下标与跨步切换
        XCTAssertEqual(YubraeTab.transactions.shifted(by: 1), .assets)
        XCTAssertEqual(YubraeTab.transactions.shifted(by: 3), .mine)
        XCTAssertEqual(YubraeTab.transactions.shifted(by: 10), .mine)
        XCTAssertEqual(YubraeTab.assets.shifted(by: -1), .transactions)
        XCTAssertEqual(YubraeTab.assets.shifted(by: 0), .assets)
        XCTAssertEqual(YubraeTab.assets.shifted(by: 1), .stats)
        XCTAssertEqual(YubraeTab.stats.shifted(by: -1), .assets)
        XCTAssertEqual(YubraeTab.stats.shifted(by: 1), .mine)
        XCTAssertEqual(YubraeTab.mine.shifted(by: -1), .stats)
        XCTAssertEqual(YubraeTab.mine.shifted(by: -10), .transactions)
    }

    // MARK: - Smart Text Chinese Numerals (Issue #4)

    func testSmartTextParserHandlesChineseNumeralsAndProtectsNonMoneyTokens() {
        let (accounts, aliases) = DefaultSeedData.makeDefaultAccounts()
        let categories = DefaultSeedData.makeDefaultCategories()
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let nowMs: Int64 = 1_763_899_200_000

        let cnSubway = SmartTextBillParser.parseLocalBatch(
            text: "今天地铁花了九块钱",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertEqual(cnSubway.count, 1)
        XCTAssertEqual(cnSubway[0].type, TransactionType.EXPENSE)
        XCTAssertEqual(cnSubway[0].origCurrency, "CNY")
        XCTAssertEqual(cnSubway[0].origAmountMicros, MoneyAmount.parseToMicros("9.00"))
        XCTAssertEqual(cnSubway[0].merchant, "地铁")

        let arabicSubway = SmartTextBillParser.parseLocalBatch(
            text: "今天地铁花了9块钱",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertEqual(arabicSubway.count, 1)
        XCTAssertEqual(arabicSubway[0].origAmountMicros, MoneyAmount.parseToMicros("9.00"))
        XCTAssertEqual(arabicSubway[0].merchant, "地铁")

        let frac = SmartTextBillParser.parseLocalBatch(
            text: "瑞幸咖啡花了九块五",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertEqual(frac.first?.origAmountMicros, MoneyAmount.parseToMicros("9.50"))

        let jiao = SmartTextBillParser.parseLocalBatch(
            text: "便利店五毛钱",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertEqual(jiao.first?.origAmountMicros, MoneyAmount.parseToMicros("0.50"))

        let brand = SmartTextBillParser.parseLocalBatch(
            text: "在九毛九花了八十八",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertEqual(brand.count, 1)
        XCTAssertEqual(brand[0].origAmountMicros, MoneyAmount.parseToMicros("88.00"))
        XCTAssertTrue(brand[0].merchant.contains("九毛九"))

        let weekday = SmartTextBillParser.parseLocalBatch(
            text: "星期三麦当劳花了三十八块五",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertEqual(weekday.first?.origAmountMicros, MoneyAmount.parseToMicros("38.50"))

        let classifier = SmartTextBillParser.parseLocalBatch(
            text: "买了一块面包花了十二块钱",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertEqual(classifier.first?.origAmountMicros, MoneyAmount.parseToMicros("12.00"))

        let fx = SmartTextBillParser.parseLocalBatch(
            text: "亚马逊十美元",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertEqual(fx.first?.origCurrency, "USD")
        XCTAssertEqual(fx.first?.origAmountMicros, MoneyAmount.parseToMicros("10.00"))

        let nonMoney = SmartTextBillParser.parseLocalBatch(
            text: "今天加班花了三个小时",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs,
            timeZone: tz
        )
        XCTAssertTrue(nonMoney.isEmpty)
    }

    // MARK: - Release Notes Markdown Formatter Tests

    func testReleaseNotesFormatterCleansMarkdownAndParsesAttributedLines() {
        let raw = """
        ## What's Changed\r
        ### **核心体验优化**\r
        - **双端互通**：支持 `.klbk` 加密备份与 [WebDAV 指南](https://example.com/webdav)\r
          * 嵌套要点说明\r
        * 修复残留 **未闭合加粗\r
        - 说明 ** 孤立星号\r
        ---\r
        ****\r
        **Full Changelog**: https://github.com/Agonie0v0/yubrae/compare/v1.2.1...v1.2.2
        """

        let lines = YubraeReleaseNotesFormatter.attributedLines(raw)
        let plain = YubraeReleaseNotesFormatter.cleanedPlainText(raw)

        XCTAssertEqual(lines.count, 7)
        XCTAssertFalse(plain.contains("**"))
        XCTAssertFalse(plain.contains("##"))
        XCTAssertEqual(plain, lines.map { String($0.characters) }.joined(separator: "\n"))

        // 1. 标题行解析为加粗且剥离 ## / ###
        XCTAssertEqual(String(lines[0].characters), "What's Changed")
        XCTAssertTrue(lines[0].runs.contains { $0.inlinePresentationIntent?.contains(.stronglyEmphasized) == true })

        XCTAssertEqual(String(lines[1].characters), "核心体验优化")
        XCTAssertTrue(lines[1].runs.contains { $0.inlinePresentationIntent?.contains(.stronglyEmphasized) == true })

        // 2. 列表行转换圆点、加粗、行内代码与链接文本
        XCTAssertEqual(String(lines[2].characters), "• 双端互通：支持 .klbk 加密备份与 WebDAV 指南")
        XCTAssertTrue(lines[2].runs.contains {
            String(lines[2][$0.range].characters) == "双端互通"
                && $0.inlinePresentationIntent?.contains(.stronglyEmphasized) == true
        })
        XCTAssertTrue(lines[2].runs.contains {
            String(lines[2][$0.range].characters) == ".klbk"
                && $0.inlinePresentationIntent?.contains(.code) == true
        })

        // 3. 缩进子列表保留两格缩进
        XCTAssertEqual(String(lines[3].characters), "  • 嵌套要点说明")

        // 4. 残留未闭合 ** 与孤立 ** 均被剥离且折叠多余空格
        XCTAssertEqual(String(lines[4].characters), "• 修复残留 未闭合加粗")
        XCTAssertFalse(String(lines[4].characters).contains("**"))
        XCTAssertEqual(String(lines[5].characters), "• 说明 孤立星号")
        XCTAssertFalse(String(lines[5].characters).contains("**"))

        // 5. GitHub 自动生成的 Full Changelog 行无裸露 ** 且保留完整链接
        XCTAssertEqual(
            String(lines[6].characters),
            "Full Changelog: https://github.com/Agonie0v0/yubrae/compare/v1.2.1...v1.2.2"
        )
        XCTAssertTrue(lines[6].runs.contains {
            String(lines[6][$0.range].characters) == "Full Changelog"
                && $0.inlinePresentationIntent?.contains(.stronglyEmphasized) == true
        })

        // 空输入与纯空白返回空
        XCTAssertEqual(YubraeReleaseNotesFormatter.cleanedPlainText("  \n\r\n  "), "")
        XCTAssertTrue(YubraeReleaseNotesFormatter.attributedLines("   \n  ").isEmpty)
    }

    // MARK: - Smart Input Launch & Receipt Vision Intent Tests (Issue #5)

    func testSmartInputLaunchURLParsing() throws {
        let smartPhotoURL = try XCTUnwrap(URL(string: "yubrae://smartinput?mode=photo&autoPick=true&autoParse=true"))
        let smartPhotoReq = try XCTUnwrap(ExternalBillUrlParser.parseSmartInputLaunch(url: smartPhotoURL))
        XCTAssertEqual(smartPhotoReq.initialMode, .receiptImage)
        XCTAssertTrue(smartPhotoReq.autoOpenPhotoPicker)
        XCTAssertTrue(smartPhotoReq.autoParseOnPick)

        let receiptURL = try XCTUnwrap(URL(string: "yubrae://receipt"))
        let receiptReq = try XCTUnwrap(ExternalBillUrlParser.parseSmartInputLaunch(url: receiptURL))
        XCTAssertEqual(receiptReq.initialMode, .receiptImage)
        XCTAssertTrue(receiptReq.autoOpenPhotoPicker)
        XCTAssertTrue(receiptReq.autoParseOnPick)

        let legacyScanURL = try XCTUnwrap(URL(string: "kairoledger://scan?autoParse=false"))
        let legacyScanReq = try XCTUnwrap(ExternalBillUrlParser.parseSmartInputLaunch(url: legacyScanURL))
        XCTAssertEqual(legacyScanReq.initialMode, .receiptImage)
        XCTAssertTrue(legacyScanReq.autoOpenPhotoPicker)
        XCTAssertFalse(legacyScanReq.autoParseOnPick)

        let smartTextURL = try XCTUnwrap(URL(string: "yubrae://smartinput"))
        let smartTextReq = try XCTUnwrap(ExternalBillUrlParser.parseSmartInputLaunch(url: smartTextURL))
        XCTAssertEqual(smartTextReq.initialMode, .text)
        XCTAssertFalse(smartTextReq.autoOpenPhotoPicker)
        XCTAssertFalse(smartTextReq.autoParseOnPick)

        let addBillURL = try XCTUnwrap(URL(string: "yubrae://addbill?money=28.50&type=EXPENSE"))
        XCTAssertNil(ExternalBillUrlParser.parseSmartInputLaunch(url: addBillURL))

        let webURL = try XCTUnwrap(URL(string: "https://example.com/receipt"))
        XCTAssertNil(ExternalBillUrlParser.parseSmartInputLaunch(url: webURL))
    }

    @MainActor
    func testLedgerStoreSmartInputLaunchRequestFlow() throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("yubrae-smart-input-test-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: dir) }

        let store = LedgerStore(storageDirectory: dir, useKeychain: false)
        XCTAssertNil(store.pendingSmartInputLaunch)

        store.requestSmartInputLaunch(
            mode: .receiptImage,
            autoOpenPhotoPicker: true,
            autoParseOnPick: true
        )

        let pending = try XCTUnwrap(store.pendingSmartInputLaunch)
        XCTAssertEqual(pending.initialMode, .receiptImage)
        XCTAssertTrue(pending.autoOpenPhotoPicker)
        XCTAssertTrue(pending.autoParseOnPick)

        let consumed = try XCTUnwrap(store.consumePendingSmartInputLaunch())
        XCTAssertEqual(consumed, pending)
        XCTAssertNil(store.pendingSmartInputLaunch)
        XCTAssertNil(store.consumePendingSmartInputLaunch())
    }

    func testReceiptImageNormalizerResizesAndConvertsToJPEG() throws {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 2000, height: 1000))
        let pngData = renderer.pngData { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 2000, height: 1000))
        }
        XCTAssertFalse(pngData.isEmpty)

        let normalized = try XCTUnwrap(
            YubraeReceiptImageNormalizer.prepareForVision(
                data: pngData,
                fallbackMimeType: "image/png",
                maxDimension: 1600
            )
        )
        XCTAssertEqual(normalized.mimeType, "image/jpeg")
        XCTAssertFalse(normalized.data.isEmpty)

        let outputImage = try XCTUnwrap(UIImage(data: normalized.data))
        let maxSide = max(outputImage.size.width, outputImage.size.height)
        XCTAssertLessThanOrEqual(maxSide, 1600)
        XCTAssertEqual(outputImage.size.width, 1600, accuracy: 1)
        XCTAssertEqual(outputImage.size.height, 800, accuracy: 1)

        XCTAssertNil(YubraeReceiptImageNormalizer.prepareForVision(data: Data()))

        let unreadable = Data([0x01, 0x02, 0x03, 0x04])
        let fallbackHeic = try XCTUnwrap(
            YubraeReceiptImageNormalizer.prepareForVision(
                data: unreadable,
                fallbackMimeType: " image/heic "
            )
        )
        XCTAssertEqual(fallbackHeic.data, unreadable)
        XCTAssertEqual(fallbackHeic.mimeType, "image/heic")

        let fallbackBlank = try XCTUnwrap(
            YubraeReceiptImageNormalizer.prepareForVision(
                data: unreadable,
                fallbackMimeType: "  "
            )
        )
        XCTAssertEqual(fallbackBlank.data, unreadable)
        XCTAssertEqual(fallbackBlank.mimeType, "image/jpeg")
    }

    @MainActor
    func testPickPhotoAndReceiptImageIntents() async throws {
        _ = YubraeRuntime.sharedStore.consumePendingSmartInputLaunch()
        defer { _ = YubraeRuntime.sharedStore.consumePendingSmartInputLaunch() }

        XCTAssertFalse(YubraeReceiptImageIntent.openAppWhenRun)
        XCTAssertTrue(YubraePickPhotoReceiptIntent.openAppWhenRun)

        _ = try await YubraePickPhotoReceiptIntent().perform()
        let pending = try XCTUnwrap(YubraeRuntime.sharedStore.pendingSmartInputLaunch)
        XCTAssertEqual(pending.initialMode, .receiptImage)
        XCTAssertTrue(pending.autoOpenPhotoPicker)
        XCTAssertTrue(pending.autoParseOnPick)

        let consumed = YubraeRuntime.sharedStore.consumePendingSmartInputLaunch()
        XCTAssertEqual(consumed, pending)
        XCTAssertNil(YubraeRuntime.sharedStore.pendingSmartInputLaunch)

        XCTAssertTrue(YubraeReceiptImageIntent.missingImageGuidance.contains("相册"))
        XCTAssertTrue(YubraeReceiptImageIntent.missingImageGuidance.contains("选择照片"))

        let receiptIntent = YubraeReceiptImageIntent()
        _ = try await receiptIntent.perform()
    }

    // MARK: - QuickExprEvaluator Direct Evaluation & Period Pickers Tests (Issues #6, #9, #10, #11)

    func testQuickExprEvaluatorDirectOperatorEvaluation() {
        XCTAssertTrue(QuickExprEvaluator.containsOperator("120+35×2"))
        XCTAssertEqual(QuickExprEvaluator.evaluateToMicros("120+35×2"), 1_900_000)

        XCTAssertTrue(QuickExprEvaluator.containsOperator("50+"))
        XCTAssertEqual(QuickExprEvaluator.evaluateToMicros("50+"), 500_000)

        XCTAssertTrue(QuickExprEvaluator.containsOperator("10÷0"))
        XCTAssertNil(QuickExprEvaluator.evaluateToMicros("10÷0"))

        XCTAssertTrue(QuickExprEvaluator.containsOperator("10-20"))
        XCTAssertNil(QuickExprEvaluator.evaluateToMicros("10-20"))

        XCTAssertNil(QuickExprEvaluator.evaluateToMicros("10-10"))
        XCTAssertFalse(QuickExprEvaluator.containsOperator("-10"))
        XCTAssertFalse(QuickExprEvaluator.containsOperator("88.50"))
    }

    func testYubraePeriodHelperWeekYearAndSummary() throws {
        let tz = try XCTUnwrap(TimeZone(secondsFromGMT: 8 * 3600))
        let cal = YubraePeriodHelper.mondayCalendar(timeZone: tz)

        // 1. 普通周（2026-11-25 周三 -> 周一 11/23 ~ 周日 11/29）
        let wed = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 11, day: 25, hour: 14, minute: 30)))
        let normalBounds = YubraePeriodHelper.weekBounds(for: wed, calendar: cal)
        let normalStart = cal.dateComponents([.year, .month, .day, .weekday], from: normalBounds.start)
        let normalEnd = cal.dateComponents([.year, .month, .day, .weekday], from: normalBounds.end)
        XCTAssertEqual(normalStart.year, 2026)
        XCTAssertEqual(normalStart.month, 11)
        XCTAssertEqual(normalStart.day, 23)
        XCTAssertEqual(normalStart.weekday, 2) // Monday
        XCTAssertEqual(normalEnd.year, 2026)
        XCTAssertEqual(normalEnd.month, 11)
        XCTAssertEqual(normalEnd.day, 29)
        XCTAssertEqual(normalEnd.weekday, 1) // Sunday
        XCTAssertTrue(normalBounds.start <= wed && wed <= normalBounds.end)
        XCTAssertEqual(YubraePeriodHelper.formatWeekRangeText(for: wed, calendar: cal), "2026/11/23 - 11/29")

        // 2. 闰年跨月（2024-02-29 周四 -> 周一 02/26 ~ 周日 03/03）
        let leapDay = try XCTUnwrap(cal.date(from: DateComponents(year: 2024, month: 2, day: 29, hour: 9, minute: 0)))
        let leapBounds = YubraePeriodHelper.weekBounds(for: leapDay, calendar: cal)
        let leapStart = cal.dateComponents([.year, .month, .day], from: leapBounds.start)
        let leapEnd = cal.dateComponents([.year, .month, .day], from: leapBounds.end)
        XCTAssertEqual(leapStart.year, 2024)
        XCTAssertEqual(leapStart.month, 2)
        XCTAssertEqual(leapStart.day, 26)
        XCTAssertEqual(leapEnd.year, 2024)
        XCTAssertEqual(leapEnd.month, 3)
        XCTAssertEqual(leapEnd.day, 3)
        XCTAssertEqual(YubraePeriodHelper.formatWeekRangeText(for: leapDay, calendar: cal), "2024/02/26 - 03/03")

        // 3. 跨年周（2025-12-31 周三 -> 周一 2025/12/29 ~ 周日 2026/01/04，校验非 week-year）
        let yearEnd = try XCTUnwrap(cal.date(from: DateComponents(year: 2025, month: 12, day: 31, hour: 23, minute: 0)))
        let crossBounds = YubraePeriodHelper.weekBounds(for: yearEnd, calendar: cal)
        let crossStart = cal.dateComponents([.year, .month, .day], from: crossBounds.start)
        let crossEnd = cal.dateComponents([.year, .month, .day], from: crossBounds.end)
        XCTAssertEqual(crossStart.year, 2025)
        XCTAssertEqual(crossStart.month, 12)
        XCTAssertEqual(crossStart.day, 29)
        XCTAssertEqual(crossEnd.year, 2026)
        XCTAssertEqual(crossEnd.month, 1)
        XCTAssertEqual(crossEnd.day, 4)
        XCTAssertEqual(YubraePeriodHelper.formatWeekRangeText(for: yearEnd, calendar: cal), "2025/12/29 - 01/04")

        // 4. 12 年分页网格与负数保护
        XCTAssertEqual(YubraePeriodHelper.yearGridRange(for: 2026), Array(2016...2027))
        XCTAssertEqual(YubraePeriodHelper.yearGridRange(for: 2016), Array(2016...2027))
        XCTAssertEqual(YubraePeriodHelper.yearGridRange(for: 2027), Array(2016...2027))
        XCTAssertEqual(YubraePeriodHelper.yearGridRange(for: 2028), Array(2028...2039))
        let negGrid = YubraePeriodHelper.yearGridRange(for: -15)
        XCTAssertEqual(negGrid.count, 12)
        XCTAssertTrue(negGrid.allSatisfy { $0 >= 1 })
        XCTAssertEqual(YubraePeriodHelper.yearGridRange(for: 0), Array(1...12))

        // 5. 月度收支汇总复制文案
        let copyText = YubraePeriodHelper.formatMonthSummaryCopyText(
            title: "2026年11月",
            currency: "CNY",
            expenseText: "¥12,345,678.90",
            incomeText: "¥9,876,543.21",
            balanceText: "-¥2,469,135.69"
        )
        XCTAssertTrue(copyText.contains("2026年11月"))
        XCTAssertTrue(copyText.contains("CNY"))
        XCTAssertTrue(copyText.contains("本月支出：¥12,345,678.90"))
        XCTAssertTrue(copyText.contains("本月收入：¥9,876,543.21"))
        XCTAssertTrue(copyText.contains("月度结余：-¥2,469,135.69"))
    }
}

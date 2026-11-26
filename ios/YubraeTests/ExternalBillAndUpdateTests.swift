import Foundation
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
        let store = makeTempStore()
        let accounts = store.accounts
        let categories = store.categories
        let aliases = store.accountAliases
        let nowMs: Int64 = 1_763_899_200_000

        let cnSubway = SmartTextBillParser.parseBatch(
            text: "今天地铁花了九块钱",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertEqual(cnSubway.count, 1)
        XCTAssertEqual(cnSubway[0].type, TransactionType.EXPENSE)
        XCTAssertEqual(cnSubway[0].origCurrency, "CNY")
        XCTAssertEqual(cnSubway[0].origAmountMicros, MoneyAmount.parseToMicros("9.00"))
        XCTAssertEqual(cnSubway[0].merchant, "地铁")

        let arabicSubway = SmartTextBillParser.parseBatch(
            text: "今天地铁花了9块钱",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertEqual(arabicSubway.count, 1)
        XCTAssertEqual(arabicSubway[0].origAmountMicros, MoneyAmount.parseToMicros("9.00"))
        XCTAssertEqual(arabicSubway[0].merchant, "地铁")

        let frac = SmartTextBillParser.parseBatch(
            text: "瑞幸咖啡花了九块五",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertEqual(frac.first?.origAmountMicros, MoneyAmount.parseToMicros("9.50"))

        let jiao = SmartTextBillParser.parseBatch(
            text: "便利店五毛钱",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertEqual(jiao.first?.origAmountMicros, MoneyAmount.parseToMicros("0.50"))

        let brand = SmartTextBillParser.parseBatch(
            text: "在九毛九花了八十八",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertEqual(brand.count, 1)
        XCTAssertEqual(brand[0].origAmountMicros, MoneyAmount.parseToMicros("88.00"))
        XCTAssertTrue(brand[0].merchant.contains("九毛九"))

        let weekday = SmartTextBillParser.parseBatch(
            text: "星期三麦当劳花了三十八块五",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertEqual(weekday.first?.origAmountMicros, MoneyAmount.parseToMicros("38.50"))

        let classifier = SmartTextBillParser.parseBatch(
            text: "买了一块面包花了十二块钱",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertEqual(classifier.first?.origAmountMicros, MoneyAmount.parseToMicros("12.00"))

        let fx = SmartTextBillParser.parseBatch(
            text: "亚马逊十美元",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertEqual(fx.first?.origCurrency, "USD")
        XCTAssertEqual(fx.first?.origAmountMicros, MoneyAmount.parseToMicros("10.00"))

        let nonMoney = SmartTextBillParser.parseBatch(
            text: "今天加班花了三个小时",
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMs
        )
        XCTAssertTrue(nonMoney.isEmpty)
    }
}

package com.yubrae.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        ExchangeRateEntity::class,
        AccountAliasEntity::class,
        ParseRuleEntity::class,
        RawCaptureLogEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        LedgerEntity::class,
        DebtRecordEntity::class,
        InstallmentPlanEntity::class,
        TagEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class KairoDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun accountAliasDao(): AccountAliasDao
    abstract fun parseRuleDao(): ParseRuleDao
    abstract fun rawCaptureLogDao(): RawCaptureLogDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun debtRecordDao(): DebtRecordDao
    abstract fun installmentPlanDao(): InstallmentPlanDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DB_NAME = "yubrae.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ledgers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `icon` TEXT NOT NULL,
                        `color_hex` INTEGER NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        `archived` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `ledgers` (`id`, `name`, `currency_code`, `icon`, `color_hex`, `sort_order`, `archived`, `created_at`) " +
                        "VALUES (1, '默认账本', 'CNY', 'menu_book', 4280985718, 0, 0, strftime('%s','now') * 1000)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `debt_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `person_name` TEXT NOT NULL,
                        `amount_micros` INTEGER NOT NULL,
                        `settled_amount_micros` INTEGER NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `account_id` INTEGER,
                        `due_date_millis` INTEGER,
                        `note` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_records_direction` ON `debt_records` (`direction`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_records_status` ON `debt_records` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_records_person_name` ON `debt_records` (`person_name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `installment_plans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `account_id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `total_amount_micros` INTEGER NOT NULL,
                        `fee_per_period_micros` INTEGER NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `total_periods` INTEGER NOT NULL,
                        `completed_periods` INTEGER NOT NULL,
                        `billing_day` INTEGER NOT NULL,
                        `next_due_millis` INTEGER NOT NULL,
                        `category_id` INTEGER,
                        `enabled` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_plans_account_id` ON `installment_plans` (`account_id`)")

                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `ledger_id` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `exclude_from_stats` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `exclude_from_budget` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `refund_of_tx_id` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `attachment_paths` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `reimbursed_tx_id` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ledger_id` ON `transactions` (`ledger_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_refund_of_tx_id` ON `transactions` (`refund_of_tx_id`)")

                db.execSQL("ALTER TABLE `budgets` ADD COLUMN `ledger_id` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `budgets` ADD COLUMN `rollover_enabled` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `group_name` TEXT NOT NULL,
                        `color_hex` INTEGER NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        `is_system` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `icon` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `debt_records` ADD COLUMN `include_in_net_worth` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun build(context: Context): KairoDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KairoDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}

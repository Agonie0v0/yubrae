package com.kairoledger.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        RecurringRuleEntity::class
    ],
    version = 1,
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

    companion object {
        const val DB_NAME = "kairo_ledger.db"

        fun build(context: Context): KairoDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KairoDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}

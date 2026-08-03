package com.foldledger.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE categories ADD COLUMN colorArgb INTEGER DEFAULT NULL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_deletedAt_happenedAt` " +
                "ON `transactions` (`deletedAt`, `happenedAt`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` " +
                "ON `transactions` (`categoryId`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_direction_happenedAt` " +
                "ON `transactions` (`direction`, `happenedAt`)",
        )
    }
}

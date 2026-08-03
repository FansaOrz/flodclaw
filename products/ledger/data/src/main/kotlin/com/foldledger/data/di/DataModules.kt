package com.foldledger.data.di

import android.content.Context
import androidx.room.Room
import com.foldledger.data.backup.BackupRepositoryImpl
import com.foldledger.data.db.AccountDao
import com.foldledger.data.db.BudgetDao
import com.foldledger.data.db.CategoryDao
import com.foldledger.data.db.LedgerDatabase
import com.foldledger.data.db.MIGRATION_1_2
import com.foldledger.data.db.MIGRATION_2_3
import com.foldledger.data.db.PendingCaptureDao
import com.foldledger.data.db.TransactionDao
import com.foldledger.data.repo.AccountRepositoryImpl
import com.foldledger.data.repo.BudgetRepositoryImpl
import com.foldledger.data.repo.CategoryRepositoryImpl
import com.foldledger.data.repo.PendingCaptureRepositoryImpl
import com.foldledger.data.repo.TransactionRepositoryImpl
import com.foldledger.data.settings.SettingsRepositoryImpl
import com.foldledger.domain.repo.AccountRepository
import com.foldledger.domain.repo.BackupRepository
import com.foldledger.domain.repo.BudgetRepository
import com.foldledger.domain.repo.CategoryRepository
import com.foldledger.domain.repo.PendingCaptureRepository
import com.foldledger.domain.repo.SettingsRepository
import com.foldledger.domain.repo.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LedgerDatabase =
        Room.databaseBuilder(context, LedgerDatabase::class.java, "foldledger.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides fun provideAccountDao(db: LedgerDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: LedgerDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: LedgerDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideBudgetDao(db: LedgerDatabase): BudgetDao = db.budgetDao()
    @Provides fun providePendingDao(db: LedgerDatabase): PendingCaptureDao = db.pendingCaptureDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindAccountRepo(impl: AccountRepositoryImpl): AccountRepository
    @Binds @Singleton abstract fun bindCategoryRepo(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds @Singleton abstract fun bindTransactionRepo(impl: TransactionRepositoryImpl): TransactionRepository
    @Binds @Singleton abstract fun bindBudgetRepo(impl: BudgetRepositoryImpl): BudgetRepository
    @Binds @Singleton abstract fun bindPendingRepo(impl: PendingCaptureRepositoryImpl): PendingCaptureRepository
    @Binds @Singleton abstract fun bindSettingsRepo(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds @Singleton abstract fun bindBackupRepo(impl: BackupRepositoryImpl): BackupRepository
}

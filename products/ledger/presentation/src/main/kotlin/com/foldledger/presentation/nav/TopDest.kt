package com.foldledger.presentation.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Stats("stats", "统计", Icons.Default.BarChart),
    Accounts("accounts", "账户", Icons.Default.AccountBalanceWallet),
    Ledger("ledger", "流水", Icons.AutoMirrored.Filled.List),
    Budgets("budgets", "预算", Icons.Default.Savings),
    Settings("settings", "设置", Icons.Default.Settings),
}

package com.foldledger.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foldledger.domain.model.Account
import com.foldledger.domain.model.AccountType
import com.foldledger.domain.model.Category
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.ReclassifyCandidate
import com.foldledger.domain.repo.AccountRepository
import com.foldledger.domain.repo.CategoryRepository
import com.foldledger.domain.repo.TransactionRepository
import com.foldledger.domain.util.MoneyFormat
import com.foldledger.presentation.common.CategoryColorDot
import com.foldledger.presentation.common.CategoryColorPicker
import com.foldledger.presentation.common.LedgerCard
import com.foldledger.presentation.common.LedgerPageHeader
import com.foldledger.presentation.common.ReclassifyConfirmDialog
import com.foldledger.presentation.theme.CategoryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsUi(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {
    val ui: StateFlow<AccountsUi> = combine(
        accounts.observeAccounts(),
        categories.observeCategories(),
    ) { a, c -> AccountsUi(a, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUi())

    fun addAccount(name: String, type: AccountType) {
        viewModelScope.launch {
            accounts.upsert(Account(name = name, type = type, sortOrder = 99))
        }
    }

    suspend fun deleteAccount(id: Long): String? = accounts.delete(id)

    suspend fun addCategory(
        name: String,
        direction: MoneyDirection,
        keywords: List<String>,
        colorArgb: Int? = null,
    ): List<ReclassifyCandidate> {
        val cleaned = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val id = categories.upsert(
            Category(
                name = name.trim(),
                direction = direction,
                keywords = cleaned,
                sortOrder = 99,
                colorArgb = colorArgb,
            ),
        )
        if (cleaned.isEmpty()) return emptyList()
        return categories.findReclassifyCandidates(id, cleaned)
    }

    suspend fun updateCategory(
        cat: Category,
        name: String,
        keywords: List<String>,
        colorArgb: Int? = cat.colorArgb,
    ): List<ReclassifyCandidate> {
        val cleaned = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        categories.upsert(
            cat.copy(
                name = name.trim(),
                keywords = cleaned,
                colorArgb = colorArgb,
            ),
        )
        // 保存时用当前全部关键词扫描，便于「词已加过但当时没弹窗」时再扫一遍
        if (cleaned.isEmpty()) return emptyList()
        return categories.findReclassifyCandidates(cat.id, cleaned)
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { categories.delete(id) }
    }

    fun applyReclassify(candidate: ReclassifyCandidate) {
        viewModelScope.launch {
            transactions.upsert(
                candidate.item.transaction.copy(
                    categoryId = candidate.targetCategoryId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun applyReclassifyAll(candidates: List<ReclassifyCandidate>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            candidates.forEach { c ->
                transactions.upsert(
                    c.item.transaction.copy(
                        categoryId = c.targetCategoryId,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsRoute(viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var showAddAccount by remember { mutableStateOf(false) }
    var showAddCategoryDirection by remember { mutableStateOf(false) }
    var addCategoryDirection by remember { mutableStateOf<MoneyDirection?>(null) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var confirmDeleteCategory by remember { mutableStateOf<Category?>(null) }
    var confirmDeleteAccount by remember { mutableStateOf<Account?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reclassifyQueue by remember { mutableStateOf<List<ReclassifyCandidate>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val mutedIcon = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    fun keywordOwnersExcluding(excludeId: Long?): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for (c in state.categories) {
            if (excludeId != null && c.id == excludeId) continue
            for (kw in c.keywords) {
                val key = kw.trim().lowercase()
                if (key.isNotEmpty() && key !in map) map[key] = c.name
            }
        }
        return map
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LedgerPageHeader(
                title = "账户与分类",
                eyebrow = "${state.accounts.size} 个账户 · ${state.categories.size} 个分类",
                actions = {
                    TextButton(onClick = { showAddAccount = true }) { Text("添加账户") }
                    TextButton(onClick = { showAddCategoryDirection = true }) { Text("添加分类") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "资金账户",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            items(state.accounts, key = { "a-${it.id}" }) { acc ->
                LedgerCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(acc.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                acc.type.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "¥${MoneyFormat.fenToYuan(acc.balanceFen)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        SoftOverflowMenu(
                            tint = mutedIcon,
                            items = listOf("删除" to { confirmDeleteAccount = acc }),
                        )
                    }
                }
            }
            item {
                Text(
                    "智能分类",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, end = 4.dp, bottom = 4.dp),
                )
            }
            items(state.categories, key = { "c-${it.id}" }) { cat ->
                LedgerCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryColorDot(
                            name = cat.name,
                            colorArgb = cat.colorArgb,
                            size = 14.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${when (cat.direction) {
                                    MoneyDirection.INCOME -> "收入"
                                    MoneyDirection.TRANSFER -> "转账"
                                    else -> "支出"
                                }} · ${cat.keywords.size} 个关键词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editCategory = cat }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = mutedIcon,
                            )
                        }
                        SoftOverflowMenu(
                            tint = mutedIcon,
                            items = listOf("删除" to { confirmDeleteCategory = cat }),
                        )
                    }
                }
            }
        }
    }
    if (showAddAccount) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAccount = false },
            title = { Text("添加账户") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addAccount(name, AccountType.CUSTOM)
                        showAddAccount = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddAccount = false }) { Text("取消") } },
        )
    }
    if (showAddCategoryDirection) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDirection = false },
            title = { Text("添加分类") },
            text = { Text("支出分类会出现在记支出时；收入分类（如理财收益）在记收入时可选。") },
            confirmButton = {
                TextButton(onClick = {
                    showAddCategoryDirection = false
                    addCategoryDirection = MoneyDirection.EXPENSE
                }) { Text("支出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showAddCategoryDirection = false
                        addCategoryDirection = MoneyDirection.INCOME
                    }) { Text("收入") }
                    TextButton(onClick = { showAddCategoryDirection = false }) { Text("取消") }
                }
            },
        )
    }
    addCategoryDirection?.let { direction ->
        CategoryKeywordEditorDialog(
            title = if (direction == MoneyDirection.INCOME) "添加收入分类" else "添加支出分类",
            initialName = "",
            initialKeywords = emptyList(),
            initialColorArgb = null,
            keywordOwners = keywordOwnersExcluding(null),
            onDismiss = { addCategoryDirection = null },
            onSave = { name, keywords, colorArgb ->
                addCategoryDirection = null
                scope.launch {
                    val found = viewModel.addCategory(name, direction, keywords, colorArgb)
                    if (found.isNotEmpty()) {
                        reclassifyQueue = found
                    } else if (keywords.isNotEmpty()) {
                        message = "分类已保存，没有发现其他需确认的账单。"
                    }
                }
            },
        )
    }
    editCategory?.let { cat ->
        CategoryKeywordEditorDialog(
            title = "编辑分类",
            initialName = cat.name,
            initialKeywords = cat.keywords,
            initialColorArgb = cat.colorArgb,
            keywordOwners = keywordOwnersExcluding(cat.id),
            onDismiss = { editCategory = null },
            onSave = { name, keywords, colorArgb ->
                editCategory = null
                scope.launch {
                    val found = viewModel.updateCategory(cat, name, keywords, colorArgb)
                    if (found.isNotEmpty()) {
                        reclassifyQueue = found
                    } else if (keywords.any { it.isNotBlank() }) {
                        message = "分类已保存，没有发现其他需确认的账单。"
                    }
                }
            },
        )
    }
    if (reclassifyQueue.isNotEmpty()) {
        ReclassifyConfirmDialog(
            candidates = reclassifyQueue,
            onConfirmOne = { viewModel.applyReclassify(it) },
            onSkipOne = { },
            onConfirmAll = { viewModel.applyReclassifyAll(reclassifyQueue) },
            onDismissAll = { reclassifyQueue = emptyList() },
        )
    }
    confirmDeleteCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { confirmDeleteCategory = null },
            title = { Text("删除分类") },
            text = { Text("确定删除「${cat.name}」？已用该分类的流水会变为未分类。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat.id)
                    confirmDeleteCategory = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteCategory = null }) { Text("取消") } },
        )
    }
    confirmDeleteAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { confirmDeleteAccount = null },
            title = { Text("删除账户") },
            text = { Text("确定删除「${acc.name}」？若仍有流水则无法删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val err = viewModel.deleteAccount(acc.id)
                        confirmDeleteAccount = null
                        if (err != null) message = err
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAccount = null }) { Text("取消") } },
        )
    }
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun SoftOverflowMenu(
    tint: Color,
    items: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CategoryKeywordEditorDialog(
    title: String,
    initialName: String,
    initialKeywords: List<String>,
    initialColorArgb: Int?,
    keywordOwners: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (name: String, keywords: List<String>, colorArgb: Int?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val tags = remember {
        mutableStateListOf<String>().also { it.addAll(initialKeywords.filter { k -> k.isNotBlank() }) }
    }
    var colorArgb by remember { mutableStateOf(initialColorArgb) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun conflictMessage(tag: String, owner: String): String =
        "「$tag」已在分类「$owner」中，不能重复"

    fun tryAddTag() {
        val value = draft.trim()
        if (value.isEmpty()) return
        val parts = value.split(',', '，', ' ', '、', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (parts.isEmpty()) return

        val conflicts = mutableListOf<String>()
        val selfDupes = mutableListOf<String>()
        var added = 0
        parts.forEach { p ->
            val key = p.lowercase()
            val owner = keywordOwners[key]
            when {
                owner != null -> conflicts += conflictMessage(p, owner)
                tags.any { it.equals(p, ignoreCase = true) } -> selfDupes += "「$p」已在本分类中"
                else -> {
                    tags.add(p)
                    added++
                }
            }
        }
        draft = ""
        error = when {
            conflicts.isNotEmpty() -> conflicts.first() + if (conflicts.size > 1) " 等 ${conflicts.size} 项" else ""
            selfDupes.isNotEmpty() && added == 0 -> selfDupes.first()
            added == 0 -> "该关键词已存在"
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryColorPicker(
                    categoryName = name.ifBlank { "未命名" },
                    selectedArgb = colorArgb,
                    onSelect = { colorArgb = it },
                )
                Text(
                    "关键词标签",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Text(
                    "匹配商户名/账单原文时自动归到此类。同一关键词不能出现在多个分类。点标签可删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                if (tags.isEmpty()) {
                    Text(
                        "暂无关键词，在下方输入后点添加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                } else {
                    val conflictTags = tags.filter { keywordOwners.containsKey(it.lowercase()) }
                    if (conflictTags.isNotEmpty()) {
                        Text(
                            conflictTags.joinToString("；") {
                                conflictMessage(it, keywordOwners[it.lowercase()]!!)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            val conflicted = keywordOwners.containsKey(tag.lowercase())
                            InputChip(
                                selected = conflicted,
                                onClick = { tags.remove(tag) },
                                label = {
                                    Text(
                                        tag,
                                        color = if (conflicted) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            Color.Unspecified
                                        },
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除 $tag",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            error = null
                        },
                        label = { Text("新关键词") },
                        placeholder = { Text("例如：必胜客") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                    )
                    TextButton(
                        onClick = { tryAddTag() },
                        enabled = draft.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("添加")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = "请填写分类名称"
                        return@TextButton
                    }
                    val conflict = tags.firstOrNull { keywordOwners.containsKey(it.lowercase()) }
                    if (conflict != null) {
                        error = conflictMessage(conflict, keywordOwners[conflict.lowercase()]!!)
                        return@TextButton
                    }
                    val deduped = tags.distinctBy { it.lowercase() }
                    onSave(name.trim(), deduped, colorArgb)
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

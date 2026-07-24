package com.foldclaw.presentation.approval

import com.foldclaw.policy.ApprovalGate
import com.foldclaw.policy.ApprovalRequest
import com.foldclaw.policy.ApprovalResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把审批请求推到 Compose UI，挂起等待用户点击允许/拒绝。
 */
@Singleton
class UiApprovalGate @Inject constructor() : ApprovalGate {

    private val _pending = MutableStateFlow<ApprovalRequest?>(null)
    val pending: StateFlow<ApprovalRequest?> = _pending.asStateFlow()

    @Volatile
    private var deferred: CompletableDeferred<ApprovalResponse>? = null

    override suspend fun request(request: ApprovalRequest): ApprovalResponse {
        val d = CompletableDeferred<ApprovalResponse>()
        synchronized(this) {
            deferred?.cancel()
            deferred = d
            _pending.value = request
        }
        return try {
            d.await()
        } finally {
            synchronized(this) {
                if (deferred === d) {
                    deferred = null
                    _pending.value = null
                }
            }
        }
    }

    fun respond(approved: Boolean, remember: Boolean = false) {
        synchronized(this) {
            deferred?.complete(ApprovalResponse(approved = approved, remember = remember))
        }
    }

    fun cancelPending() {
        respond(approved = false, remember = false)
    }
}

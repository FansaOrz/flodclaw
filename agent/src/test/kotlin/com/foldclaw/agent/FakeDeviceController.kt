package com.foldclaw.agent

import com.foldclaw.domain.device.DeviceController
import com.foldclaw.domain.device.GlobalAction
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.ObservationSnapshot
import com.foldclaw.domain.model.Rect
import com.foldclaw.domain.model.Result

/**
 * 测试用 DeviceController。返回可配置的观察快照,记录所有动作。
 */
class FakeDeviceController(
    private val snapshot: ObservationSnapshot? = null,
) : DeviceController {
    val taps = mutableListOf<Rect>()
    var launches = mutableListOf<String>()
    var available = true

    override fun isAvailable(): Boolean = available
    override suspend fun observe(): Result<ObservationSnapshot> =
        snapshot?.let { Result.Success(it) }
            ?: Result.Failure(DomainError(ErrorKind.DeviceCapabilityMissing, "无快照"))

    val clicks = mutableListOf<String>()

    override suspend fun tap(rect: Rect): Result<Unit> { taps.add(rect); return Result.Success(Unit) }
    override suspend fun longPress(rect: Rect): Result<Unit> = Result.Success(Unit)
    override suspend fun swipe(start: Rect, end: Rect, durationMs: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun clickNode(nodeId: String): Result<Unit> {
        clicks.add(nodeId)
        return Result.Success(Unit)
    }
    override suspend fun setText(nodeId: String, text: String): Result<Unit> = Result.Success(Unit)
    override suspend fun globalAction(action: GlobalAction): Result<Unit> = Result.Success(Unit)
    override suspend fun launchApp(packageName: String): Result<Unit> { launches.add(packageName); return Result.Success(Unit) }
    override fun isLocked(): Boolean = false
}

/**
 * 测试用 LedgerWriter,记录所有事件,便于断言。
 */
class FakeLedgerWriter : com.foldclaw.domain.agent.LedgerWriter {
    val states = mutableListOf<Pair<String, com.foldclaw.domain.model.TaskState>>()
    val errors = mutableListOf<com.foldclaw.domain.model.DomainError>()
    val verifications = mutableListOf<Boolean>()
    val starts = mutableListOf<String>()

    override suspend fun writeStarted(taskId: String, instruction: String) {
        starts.add(instruction)
        states.add(taskId to com.foldclaw.domain.model.TaskState.RUNNING)
    }

    override suspend fun writeState(taskId: String, step: Int, state: com.foldclaw.domain.model.TaskState) {
        states.add(taskId to state)
    }

    override suspend fun writeCancel(taskId: String, step: Int) {
        states.add(taskId to com.foldclaw.domain.model.TaskState.CANCELLED)
    }

    override suspend fun writeError(taskId: String, step: Int, error: com.foldclaw.domain.model.DomainError) {
        errors.add(error)
    }

    override suspend fun writeFailure(taskId: String, step: Int, reason: String) {
        states.add(taskId to com.foldclaw.domain.model.TaskState.FAILED)
    }

    override suspend fun writeVerification(taskId: String, step: Int, ok: Boolean) {
        verifications.add(ok)
    }

    override suspend fun writeTool(taskId: String, step: Int, toolName: String, outcome: String) = Unit
}

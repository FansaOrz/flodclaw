package com.foldclaw.policy

/**
 * PolicyEngine 工厂。包装成具名类,避免 Dagger 对 Kotlin 函数类型(KFunction1)的 binding 问题。
 */
class PolicyFactory {
    fun create(envelope: CapabilityEnvelope): PolicyEngine = PolicyEngine(envelope)
}

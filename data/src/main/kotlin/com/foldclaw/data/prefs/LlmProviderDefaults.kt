package com.foldclaw.data.prefs

/**
 * 默认走阿里云百炼（通义千问）OpenAI 兼容模式。
 * 仍可手动切到 OpenAI 或自定义网关。
 */
object LlmProviderDefaults {
    const val PROVIDER_ID = "openai_compatible"

    const val PRESET_BAILIAN = "bailian"
    const val PRESET_OPENAI = "openai"
    const val PRESET_CUSTOM = "custom"

    /** 北京地域存量 DashScope 域名，无需 WorkspaceId 即可调用。 */
    const val BAILIAN_DASH_SCOPE_BASE_URL =
        "https://dashscope.aliyuncs.com/compatible-mode/v1"

    /** 文档示例与推荐 Plus 型号。 */
    const val BAILIAN_MODEL = "qwen-plus"

    const val OPENAI_BASE_URL = "https://api.openai.com/v1"
    const val OPENAI_MODEL = "gpt-4o-mini"

    const val DEFAULT_PRESET = PRESET_BAILIAN
    const val DEFAULT_BASE_URL = BAILIAN_DASH_SCOPE_BASE_URL
    const val DEFAULT_MODEL = BAILIAN_MODEL

    fun bailianWorkspaceBaseUrl(
        workspaceId: String,
        regionHost: String = "cn-beijing.maas.aliyuncs.com",
    ): String {
        val id = workspaceId.trim().trimEnd('.')
        require(id.isNotEmpty()) { "WorkspaceId 不能为空" }
        return "https://$id.$regionHost/compatible-mode/v1"
    }

    fun inferPreset(baseUrl: String): String {
        val u = baseUrl.lowercase()
        return when {
            u.contains("dashscope") || u.contains("maas.aliyuncs.com") -> PRESET_BAILIAN
            u.contains("api.openai.com") -> PRESET_OPENAI
            else -> PRESET_CUSTOM
        }
    }

    fun extractWorkspaceId(baseUrl: String): String {
        // https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
        val host = baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
        val marker = ".cn-beijing.maas.aliyuncs.com"
        if (host.endsWith(marker)) {
            return host.removeSuffix(marker)
        }
        val intlMarkers = listOf(
            ".ap-southeast-1.maas.aliyuncs.com",
            ".eu-central-1.maas.aliyuncs.com",
            ".ap-northeast-1.maas.aliyuncs.com",
        )
        for (m in intlMarkers) {
            if (host.endsWith(m)) return host.removeSuffix(m)
        }
        return ""
    }
}

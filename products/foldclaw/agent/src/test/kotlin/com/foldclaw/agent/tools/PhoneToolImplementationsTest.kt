package com.foldclaw.agent.tools

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.AppLaunchBackend
import com.foldclaw.domain.tool.ResolvedApp
import com.foldclaw.domain.tool.ToolContext
import com.foldclaw.domain.tool.ToolOutcome
import com.foldclaw.domain.tool.WeatherBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAppLaunchBackend : AppLaunchBackend {
    var lastLaunch: String? = null
    var resolveResult: Result<ResolvedApp> =
        Result.Success(ResolvedApp("com.taobao.taobao", "淘宝"))
    var launchResult: Result<Unit> = Result.Success(Unit)

    override fun resolve(appName: String?, packageName: String?): Result<ResolvedApp> = resolveResult
    override fun launch(packageName: String): Result<Unit> {
        lastLaunch = packageName
        return launchResult
    }

    override fun openSettingsPage(page: String): Result<ResolvedApp> =
        Result.Success(ResolvedApp("com.android.settings", "设置/$page"))
}

class FakeWeatherBackend(
    private val text: String = "北京 2026-07-25：晴，气温 20~30℃，降水 0.0 mm",
) : WeatherBackend {
    override suspend fun forecast(city: String, dayOffset: Int): Result<String> =
        Result.Success("$city#$dayOffset#$text")
}

class OpenAppToolImplTest {
    private val backend = FakeAppLaunchBackend()
    private val tool = OpenAppToolImpl(backend)
    private val ctx = ToolContext("t1", 0, null, emptySet(), emptySet())

    @Test
    fun `打开淘宝成功`() = runTest {
        val res = tool.execute(ctx, """{"appName":"淘宝"}""")
        val eff = res.getOrNull() as ToolOutcome.SideEffect
        assertEquals("com.taobao.taobao", backend.lastLaunch)
        assertTrue(eff.launchedByIntent)
        assertTrue(eff.summary.contains("淘宝"))
    }

    @Test
    fun `缺参数失败`() = runTest {
        val res = tool.execute(ctx, """{}""")
        assertEquals(ErrorKind.PolicyDenied, res.errorOrNull()?.kind)
    }
}

class GetWeatherToolImplTest {
    private val tool = GetWeatherToolImpl(FakeWeatherBackend())
    private val ctx = ToolContext("t1", 0, null, emptySet(), emptySet())

    @Test
    fun `查询明天北京天气`() = runTest {
        val res = tool.execute(ctx, """{"city":"北京","dayOffset":1}""")
        val text = res.getOrNull() as ToolOutcome.Text
        assertTrue(text.text.contains("北京#1#"))
    }

    @Test
    fun `空城市拒绝`() = runTest {
        val res = tool.execute(ctx, """{"city":"  "}""")
        assertEquals(ErrorKind.PolicyDenied, res.errorOrNull()?.kind)
    }
}

package com.foldclaw.di

import com.foldclaw.agent.ActiveRunLock
import com.foldclaw.agent.AgentOrchestrator
import com.foldclaw.agent.ToolRegistry
import com.foldclaw.agent.tools.AlarmSetToolImpl
import com.foldclaw.agent.tools.CalendarInsertToolImpl
import com.foldclaw.agent.tools.GetUiTreeToolImpl
import com.foldclaw.agent.tools.GetWeatherToolImpl
import com.foldclaw.agent.tools.GoBackToolImpl
import com.foldclaw.agent.tools.GoHomeToolImpl
import com.foldclaw.agent.tools.IntentBackend
import com.foldclaw.agent.tools.OpenAppToolImpl
import com.foldclaw.agent.tools.OpenSettingsPageToolImpl
import com.foldclaw.agent.tools.SetRingerModeToolImpl
import com.foldclaw.agent.tools.SwipeToolImpl
import com.foldclaw.agent.tools.TapNodeToolImpl
import com.foldclaw.agent.tools.TypeTextToolImpl
import com.foldclaw.data.db.RoomLedgerWriter
import com.foldclaw.data.db.TaskHistoryReaderImpl
import com.foldclaw.data.keystore.KeyVault
import com.foldclaw.data.prefs.TrustedToolsStoreImpl
import com.foldclaw.data.weather.OpenMeteoWeatherClient
import com.foldclaw.device.audio.RingerModeBackendImpl
import com.foldclaw.device.controller.A11yDeviceController
import com.foldclaw.device.intent.AppLaunchBackendImpl
import com.foldclaw.device.intent.IntentBackendImpl
import com.foldclaw.domain.agent.LedgerWriter
import com.foldclaw.domain.agent.TaskHistoryReader
import com.foldclaw.domain.agent.TrustedToolsStore
import com.foldclaw.domain.device.DeviceController
import com.foldclaw.domain.llm.ProviderGateway
import com.foldclaw.domain.tool.AppLaunchBackend
import com.foldclaw.domain.tool.RingerModeBackend
import com.foldclaw.domain.tool.WeatherBackend
import com.foldclaw.policy.ApprovalGate
import com.foldclaw.policy.ApprovalManager
import com.foldclaw.policy.PolicyFactory
import com.foldclaw.presentation.approval.UiApprovalGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApprovalGate(impl: UiApprovalGate): ApprovalGate = impl

    @Provides
    @Singleton
    fun provideTrustedToolsStore(impl: TrustedToolsStoreImpl): TrustedToolsStore = impl

    @Provides
    @Singleton
    fun provideTaskHistoryReader(impl: TaskHistoryReaderImpl): TaskHistoryReader = impl

    @Provides
    @Singleton
    fun provideDeviceController(impl: A11yDeviceController): DeviceController = impl

    @Provides
    @Singleton
    fun provideIntentBackend(impl: IntentBackendImpl): IntentBackend = impl

    @Provides
    @Singleton
    fun provideAppLaunchBackend(impl: AppLaunchBackendImpl): AppLaunchBackend = impl

    @Provides
    @Singleton
    fun provideWeatherBackend(impl: OpenMeteoWeatherClient): WeatherBackend = impl

    @Provides
    @Singleton
    fun provideRingerModeBackend(impl: RingerModeBackendImpl): RingerModeBackend = impl

    @Provides
    @Singleton
    fun provideLedgerWriter(impl: RoomLedgerWriter): LedgerWriter = impl

    @Provides
    @Singleton
    fun provideApprovalManager(): ApprovalManager = ApprovalManager()

    @Provides
    @Singleton
    fun provideActiveRunLock(): ActiveRunLock = ActiveRunLock()

    @Provides
    @Singleton
    fun providePolicyFactory(): PolicyFactory = PolicyFactory()

    @Provides
    @Singleton
    fun provideToolRegistry(
        backend: IntentBackend,
        appLaunch: AppLaunchBackend,
        weather: WeatherBackend,
        ringer: RingerModeBackend,
        device: DeviceController,
    ): ToolRegistry = ToolRegistry().apply {
        register(CalendarInsertToolImpl(backend))
        register(AlarmSetToolImpl(backend))
        register(OpenAppToolImpl(appLaunch))
        register(OpenSettingsPageToolImpl(appLaunch))
        register(SetRingerModeToolImpl(ringer))
        register(GetWeatherToolImpl(weather))
        register(GetUiTreeToolImpl(device))
        register(TapNodeToolImpl(device))
        register(TypeTextToolImpl(device))
        register(SwipeToolImpl(device))
        register(GoBackToolImpl(device))
        register(GoHomeToolImpl(device))
    }

    @Provides
    @Singleton
    fun provideOrchestrator(
        provider: ProviderGateway,
        tools: ToolRegistry,
        device: DeviceController,
        policyFactory: PolicyFactory,
        approvalManager: ApprovalManager,
        ledger: LedgerWriter,
        approvalGate: ApprovalGate,
        trustedTools: TrustedToolsStore,
        lock: ActiveRunLock,
    ): AgentOrchestrator = AgentOrchestrator(
        provider = provider,
        tools = tools,
        device = device,
        policyFactory = policyFactory,
        approvalManager = approvalManager,
        ledger = ledger,
        approvalGate = approvalGate,
        trustedTools = trustedTools,
        lock = lock,
    )
}

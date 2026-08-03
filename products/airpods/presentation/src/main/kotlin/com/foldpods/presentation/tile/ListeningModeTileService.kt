package com.foldpods.presentation.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.foldpods.domain.AirPodsRepository
import com.foldpods.domain.FoldPodsPrefsStore
import com.foldpods.domain.ListeningMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ListeningModeTileService : TileService() {

    @Inject lateinit var repository: AirPodsRepository
    @Inject lateinit var prefsStore: FoldPodsPrefsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        scope.launch {
            val mode = repository.observeListeningMode().first()
            qsTile?.apply {
                label = "降噪"
                subtitle = mode?.name ?: "点按切换"
                state = if (repository.observeConnection().first().aacpConnected) {
                    Tile.STATE_ACTIVE
                } else {
                    Tile.STATE_INACTIVE
                }
                updateTile()
            }
        }
    }

    override fun onClick() {
        scope.launch {
            val prefs = prefsStore.observe().first()
            val modes = prefs.preferredListeningModes.ifEmpty {
                listOf(
                    ListeningMode.NOISE_CANCELLATION,
                    ListeningMode.TRANSPARENCY,
                    ListeningMode.OFF,
                )
            }
            val current = repository.observeListeningMode().first()
            val next = modes[(modes.indexOf(current).let { if (it < 0) 0 else it } + 1) % modes.size]
            repository.setListeningMode(next)
            qsTile?.subtitle = next.name
            qsTile?.updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

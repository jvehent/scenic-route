package com.scenicroute.ui.rfe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenicroute.data.db.dao.LocationBufferDao
import com.scenicroute.data.db.entities.LocationBufferEntity
import com.scenicroute.data.db.entities.TrackPointEntity
import com.scenicroute.recording.RecordingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordFromEarlierViewModel @Inject constructor(
    private val bufferDao: LocationBufferDao,
    private val controller: RecordingController,
) : ViewModel() {

    private val _buffer = MutableStateFlow<List<LocationBufferEntity>>(emptyList())
    val buffer: StateFlow<List<LocationBufferEntity>> = _buffer.asStateFlow()

    private val _rangeMinutes = MutableStateFlow(30)
    val rangeMinutes: StateFlow<Int> = _rangeMinutes.asStateFlow()

    init {
        load()
    }

    fun setRangeMinutes(m: Int) {
        _rangeMinutes.value = m
    }

    fun reload() = load()

    private fun load() {
        viewModelScope.launch {
            val oldest = bufferDao.oldestRecordedAt()
            val defaultMinutes = if (oldest != null) {
                val age = ((System.currentTimeMillis() - oldest) / 60_000L).toInt().coerceAtLeast(1)
                minOf(age, 30)
            } else 30
            _rangeMinutes.value = defaultMinutes
            _buffer.value = bufferDao.since(cutoffFor(120))
        }
    }

    fun selectedTrack(): List<TrackPointEntity> {
        val cutoff = cutoffFor(_rangeMinutes.value)
        return _buffer.value
            .filter { it.recordedAt >= cutoff }
            .map {
                TrackPointEntity(
                    driveId = "preview",
                    seq = 0,
                    lat = it.lat,
                    lng = it.lng,
                    alt = it.alt,
                    speed = it.speed,
                    accuracy = it.accuracy,
                    recordedAt = it.recordedAt,
                )
            }
    }

    fun save(onSaved: (driveId: String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val cutoff = cutoffFor(_rangeMinutes.value)
            controller.startFromBuffer(cutoff)
                .onSuccess(onSaved)
                .onFailure { onError(it.message ?: "Failed to save") }
        }
    }

    private fun cutoffFor(minutes: Int): Long =
        System.currentTimeMillis() - minutes * 60_000L
}

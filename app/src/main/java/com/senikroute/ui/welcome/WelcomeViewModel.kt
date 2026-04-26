package com.senikroute.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senikroute.data.discovery.DiscoveryDrive
import com.senikroute.data.discovery.DiscoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val discovery: DiscoveryRepository,
) : ViewModel() {

    private val _featured = MutableStateFlow<List<DiscoveryDrive>>(emptyList())
    val featured: StateFlow<List<DiscoveryDrive>> = _featured.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _loading.value = true
        viewModelScope.launch {
            _featured.value = discovery.fetchFeatured(limit = 8)
            _loading.value = false
        }
    }

    fun reload() = load()
}

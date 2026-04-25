package com.scenicroute.ui.photo

import androidx.lifecycle.ViewModel
import com.scenicroute.data.storage.PhotoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    val storage: PhotoStorage,
) : ViewModel()

package com.example.nexora.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexora.data.AppRepository
import com.example.nexora.graph.CircuitEngine
import com.example.nexora.graph.CircuitTrace
import com.example.nexora.graph.GridSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _isLoading = MutableStateFlow(value = true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isDefaultLauncher = MutableStateFlow(value = false)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    private val _slots = MutableStateFlow<List<GridSlot>>(emptyList())
    val slots: StateFlow<List<GridSlot>> = _slots.asStateFlow()

    private val _traces = MutableStateFlow<List<CircuitTrace>>(emptyList())
    val traces: StateFlow<List<CircuitTrace>> = _traces.asStateFlow()

    private val _time = MutableStateFlow(0f)
    val time: StateFlow<Float> = _time.asStateFlow()

    private val _draggedPackage = MutableStateFlow<String?>(null)

    private var canvasWidth = 1080f
    private var canvasHeight = 1920f

    fun setIsDefaultLauncher(isDefault: Boolean) {
        _isDefaultLauncher.value = isDefault
    }

    fun loadApps(width: Float, height: Float) {
        if ((width <= 0f) || (height <= 0f)) return
        canvasWidth = width
        canvasHeight = height

        viewModelScope.launch {
            _isLoading.value = true
            val rawNodes = repository.getInstalledApps()
            val (slots, traces) = CircuitEngine.layoutGrid(rawNodes, canvasWidth, canvasHeight)
            _slots.value = slots
            _traces.value = traces
            _isLoading.value = false
        }
    }

    fun onNodeDragStart(packageName: String) {
        _draggedPackage.value = packageName
    }

    fun onNodeDrag(packageName: String, dragAmount: Offset) {
        val currentSlots = _slots.value
        val targetSlot = currentSlots.firstOrNull { it.appNode?.packageName == packageName } ?: return
        val node = targetSlot.appNode ?: return

        val newX = (node.position.x + dragAmount.x).coerceIn(80f, canvasWidth - 80f)
        val newY = (node.position.y + dragAmount.y).coerceIn(80f, canvasHeight - 80f)
        node.position = Offset(newX, newY)

        _slots.value = currentSlots.toList()
    }

    fun onNodeDragEnd() {
        _draggedPackage.value = null
    }

    fun tickAnimation(deltaSeconds: Float) {
        _time.value += deltaSeconds
    }

    fun launchApp(packageName: String) {
        repository.launchApp(packageName)
    }
}

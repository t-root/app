package com.example.nexora.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexora.data.AppRepository
import com.example.nexora.data.GroupConfig
import com.example.nexora.data.GroupRepository
import com.example.nexora.data.listenForPackageChanges
import com.example.nexora.data.SettingsRepository
import com.example.nexora.model.AppGroup
import com.example.nexora.model.AppNode
import com.example.nexora.model.CoverSettings
import com.example.nexora.model.GroupColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val groupRepository = GroupRepository(application)
    private val settings = SettingsRepository(application)

    private val _isLoading = MutableStateFlow(value = true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _apps = MutableStateFlow<List<AppNode>>(emptyList())
    val apps: StateFlow<List<AppNode>> = _apps.asStateFlow()

    private val _groups = MutableStateFlow(groupRepository.load())
    val groups: StateFlow<List<AppGroup>> = _groups.asStateFlow()

    private val _showAppNames = MutableStateFlow(settings.showAppNames)
    val showAppNames: StateFlow<Boolean> = _showAppNames.asStateFlow()

    private val _centerPackage = MutableStateFlow(settings.centerPackage)
    val centerPackage: StateFlow<String?> = _centerPackage.asStateFlow()

    private val _cover = MutableStateFlow(settings.cover)
    val cover: StateFlow<CoverSettings> = _cover.asStateFlow()

    private var loadJob: Job? = null

    // An app installed or uninstalled while this screen is up shows at once.
    private val stopListening = listenForPackageChanges(application, ::refreshApps)

    override fun onCleared() {
        stopListening()
    }

    init {
        // Groups coloured from the old, too-similar palette get distinct colours
        // from the new one, once (custom colours are left alone).
        if (settings.paletteVersion < 2) {
            val recoloured = _groups.value.mapIndexed { i, group ->
                if (group.color in GroupColors.OLD_PALETTE) {
                    group.copy(color = GroupColors.PALETTE[i % GroupColors.PALETTE.size])
                } else {
                    group
                }
            }
            _groups.value = recoloured
            groupRepository.save(recoloured)
            settings.paletteVersion = 2
        }
        // The centre app is never in a group (it may have been put in one earlier).
        _centerPackage.value?.let { center ->
            if (_groups.value.any { center in it.packages }) commit(_groups.value)
        }
    }

    /** Makes [packageName] the centre app (out of every group), or clears it with null. */
    fun setCenter(packageName: String?) {
        settings.centerPackage = packageName
        _centerPackage.value = packageName
        commit(_groups.value)
    }

    /** Saved at once; the wallpaper picks it up next time it shows. */
    fun setCover(cover: CoverSettings) {
        settings.cover = cover
        _cover.value = cover
    }

    fun setShowAppNames(show: Boolean) {
        settings.showAppNames = show
        _showAppNames.value = show
    }

    /** Reloads installed apps; only the first load shows the spinner. */
    fun refreshApps() {
        // A load already running may have read the list before the change.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = _apps.value.isEmpty()
            _apps.value = repository.getInstalledApps()
            _isLoading.value = false
        }
    }

    fun launchApp(packageName: String) {
        repository.launchApp(packageName)
    }

    /** A new, unsaved group, optionally starting with some apps. */
    fun newGroupDraft(initialPackages: List<String> = emptyList()): AppGroup {
        val count = _groups.value.size
        return AppGroup(
            id = UUID.randomUUID().toString(),
            name = "Nhóm ${count + 1}",
            color = GroupColors.PALETTE[count % GroupColors.PALETTE.size],
            packages = initialPackages,
        )
    }

    /** Inserts or updates [group]; its apps are taken out of every other group. */
    fun saveGroup(group: AppGroup) {
        val current = _groups.value
        val taken = group.packages.toSet()
        val others = current
            .filter { it.id != group.id }
            .map { it.copy(packages = it.packages.filterNot(taken::contains)) }
        val index = current.indexOfFirst { it.id == group.id }
        val updated = if (index >= 0) {
            others.toMutableList().apply { add(index, group) }
        } else {
            others + group
        }
        commit(updated)
    }

    fun deleteGroup(groupId: String) {
        commit(_groups.value.filterNot { it.id == groupId })
    }

    /** Moves an app into [groupId], or out of every group (free floating) when null. */
    fun moveApp(packageName: String, groupId: String?) {
        commit(
            _groups.value.map { group ->
                val without = group.packages - packageName
                if (group.id == groupId) group.copy(packages = without + packageName) else group.copy(packages = without)
            }
        )
    }

    /** Writes every group to [uri] as JSON; reports a message for the user. */
    fun exportGroups(uri: Uri, onDone: (String) -> Unit) {
        val json = GroupRepository.exportJson(_groups.value, _centerPackage.value)
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                "Đã xuất ${_groups.value.size} nhóm"
            } catch (e: Exception) {
                "Không xuất được: ${e.message}"
            }
            onDone(message)
        }
    }

    /** Reads groups from a JSON file at [uri], without applying them yet. */
    fun readGroupsFile(uri: Uri, onDone: (Result<GroupConfig>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    GroupRepository.importJson(text)
                }
            }
            onDone(result)
        }
    }

    fun replaceGroups(imported: GroupConfig) {
        imported.center?.let(::setCenter)
        commit(imported.groups)
    }

    /** Adds imported groups; their apps leave whichever group had them before. */
    fun mergeGroups(imported: GroupConfig) {
        imported.center?.let(::setCenter)
        val taken = imported.groups.flatMap { it.packages }.toSet()
        val kept = _groups.value.map { it.copy(packages = it.packages.filterNot(taken::contains)) }
        commit(kept + imported.groups)
    }

    private fun commit(groups: List<AppGroup>) {
        val center = _centerPackage.value
        val clean = if (center == null) groups else groups.map { it.copy(packages = it.packages - center) }
        _groups.value = clean
        groupRepository.save(clean)
    }
}

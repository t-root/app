package com.example.nexora.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.nexora.R
import com.example.nexora.data.GroupConfig
import com.example.nexora.model.AppGroup
import com.example.nexora.model.AppNode
import com.example.nexora.space.SpaceCamera
import com.example.nexora.space.SpaceHub
import com.example.nexora.space.SpaceNode
import com.example.nexora.space.SpaceRenderer
import com.example.nexora.space.SpaceScene

@Composable
fun SpaceScreen(
    viewModel: MainViewModel,
    onApplyLiveWallpaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val showAppNames by viewModel.showAppNames.collectAsState()
    val centerPackage by viewModel.centerPackage.collectAsState()
    val cover by viewModel.cover.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current.density
    val topInset = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val bottomInset = WindowInsets.navigationBars.getBottom(LocalDensity.current).toFloat()
    val camera = remember { SpaceCamera() }
    val renderer = remember(density) {
        val typeface = ResourcesCompat.getFont(context, R.font.square_vn) ?: android.graphics.Typeface.DEFAULT
        SpaceRenderer(density, typeface)
    }

    var time by remember { mutableFloatStateOf(0f) }
    var scene by remember { mutableStateOf<SpaceScene?>(null) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(apps, groups, centerPackage) {
        if (apps.isEmpty()) return@LaunchedEffect
        // Built from the previous scene, so changes animate from where things are.
        val built = SpaceScene.build(apps, groups, centerPackage, scene)
        // The focused group was deleted: back to idle.
        if (focusedId != null && built.focused == null) camera.frameIdle()
        focusedId = built.focused?.group?.id
        scene = built
    }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                time += dt
                camera.tick(dt)
                scene?.update(time, dt, camera)
            }
        }
    }

    BackHandler(enabled = focusedId != null) {
        scene?.unfocus(camera)
        focusedId = null
    }

    var showManager by remember { mutableStateOf(false) }
    var pickingCenter by remember { mutableStateOf(false) }
    var editingCover by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<GroupConfig?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportGroups(it) { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            viewModel.readGroupsFile(it) { result ->
                result
                    .onSuccess { imported -> pendingImport = imported }
                    .onFailure { e -> Toast.makeText(context, "File không hợp lệ: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
    var editorDraft by remember { mutableStateOf<AppGroup?>(null) }
    var assigningApp by remember { mutableStateOf<AppNode?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val current = scene
        if (isLoading || current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TerminalGreen)
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (zoom != 1f) camera.zoomBy(zoom) else camera.dragBy(pan.x / density, pan.y / density)
                        }
                    }
                    .pointerInput(current) {
                        val minHit = 20.dp.toPx()
                        detectTapGestures(
                            onTap = { p ->
                                when (val hit = current.hitTest(p.x, p.y, minHit)) {
                                    is SpaceNode -> viewModel.launchApp(hit.app.packageName)
                                    // Tap a group: it becomes the focus and grows its branches.
                                    is SpaceHub -> {
                                        current.focus(hit, camera)
                                        focusedId = hit.group.id
                                    }
                                    else -> {
                                        current.unfocus(camera)
                                        focusedId = null
                                    }
                                }
                            },
                            onLongPress = { p ->
                                when (val hit = current.hitTest(p.x, p.y, minHit)) {
                                    is SpaceNode -> assigningApp = hit.app
                                    is SpaceHub -> editorDraft = hit.group
                                    else -> Unit
                                }
                            },
                        )
                    }
            ) {
                val t = time // read in the draw phase: redraws every frame
                renderer.showAppNames = showAppNames
                // Keep clear of the status bar + buttons on top and the gesture bar below.
                renderer.insetTop = topInset + 64.dp.toPx()
                renderer.insetBottom = bottomInset + 12.dp.toPx()
                drawIntoCanvas { canvas ->
                    renderer.draw(canvas.nativeCanvas, current, camera, size.width, size.height, t)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                TerminalButton("> SETUP") { showManager = true }
                TerminalButton("> HÌNH NỀN", onApplyLiveWallpaper)
            }

            if (groups.isEmpty()) {
                Text(
                    text = "NHẤN GIỮ MỘT APP ĐỂ ĐƯA VÀO NHÓM · CHẠM TÊN NHÓM ĐỂ MỞ · KÉO NGANG ĐỂ XOAY · VUỐT DỌC / CHỤM ĐỂ THU PHÓNG",
                    color = TerminalDim,
                    fontSize = 11.sp,
                    fontFamily = SquareFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }

        if (showManager) {
            GroupManagerDialog(
                groups = groups,
                centerApp = apps.firstOrNull { it.packageName == centerPackage },
                onPickCenter = { pickingCenter = true },
                showAppNames = showAppNames,
                onShowAppNames = viewModel::setShowAppNames,
                coverEnabled = cover.enabled,
                onEditCover = { editingCover = true },
                onEdit = { group -> editorDraft = group },
                onCreate = { editorDraft = viewModel.newGroupDraft() },
                onExport = { exportLauncher.launch("nexora-nhom.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                onDismiss = { showManager = false },
            )
        }

        if (editingCover) {
            CoverDialog(
                cover = cover,
                onChange = viewModel::setCover,
                onDismiss = { editingCover = false },
            )
        }

        if (pickingCenter) {
            CenterPickerDialog(
                apps = apps,
                groups = groups,
                current = centerPackage,
                onPick = { pkg ->
                    viewModel.setCenter(pkg)
                    pickingCenter = false
                },
                onDismiss = { pickingCenter = false },
            )
        }

        pendingImport?.let { imported ->
            ImportGroupsDialog(
                config = imported,
                centerName = imported.center?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.appName },
                hasGroups = groups.isNotEmpty(),
                onReplace = {
                    viewModel.replaceGroups(imported)
                    pendingImport = null
                },
                onMerge = {
                    viewModel.mergeGroups(imported)
                    pendingImport = null
                },
                onDismiss = { pendingImport = null },
            )
        }

        assigningApp?.let { app ->
            AssignGroupDialog(
                app = app,
                groups = groups,
                isCenter = app.packageName == centerPackage,
                onPick = { groupId ->
                    viewModel.moveApp(app.packageName, groupId)
                    assigningApp = null
                },
                onNewGroup = {
                    editorDraft = viewModel.newGroupDraft(listOf(app.packageName))
                    assigningApp = null
                },
                onSetCenter = { makeCenter ->
                    viewModel.setCenter(if (makeCenter) app.packageName else null)
                    assigningApp = null
                },
                onDismiss = { assigningApp = null },
            )
        }

        editorDraft?.let { draft ->
            val exists = groups.any { it.id == draft.id }
            GroupEditorDialog(
                draft = draft,
                apps = apps,
                groups = groups,
                centerPackage = centerPackage,
                onSave = { group ->
                    viewModel.saveGroup(group)
                    editorDraft = null
                },
                onDelete = if (exists) {
                    {
                        viewModel.deleteGroup(draft.id)
                        editorDraft = null
                    }
                } else {
                    null
                },
                onDismiss = { editorDraft = null },
            )
        }
    }
}

/** Bordered green-on-black button, like the web's search bar. */
@Composable
private fun TerminalButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = TerminalGreen,
        fontSize = 12.sp,
        fontFamily = SquareFont,
        maxLines = 1,
        modifier = Modifier
            .background(Color.Black)
            .border(1.dp, TerminalDim)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

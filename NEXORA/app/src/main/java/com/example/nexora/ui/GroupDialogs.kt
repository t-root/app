package com.example.nexora.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.nexora.data.GroupConfig
import com.example.nexora.model.AppGroup
import com.example.nexora.model.AppNode
import com.example.nexora.model.GroupColors

private val PanelColor = Color.Black
private val Accent = TerminalGreen

/** List of all groups (pick one to edit or create one), JSON export / import and display options. */
@Composable
fun GroupManagerDialog(
    groups: List<AppGroup>,
    centerApp: AppNode?,
    onPickCenter: () -> Unit,
    showAppNames: Boolean,
    onShowAppNames: (Boolean) -> Unit,
    coverEnabled: Boolean,
    onEditCover: () -> Unit,
    onEdit: (AppGroup) -> Unit,
    onCreate: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelColor,
        title = { Text("Nhóm ứng dụng", color = Color.White) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (groups.isEmpty()) {
                    Text(
                        "Chưa có nhóm nào. Ứng dụng trong một nhóm sẽ kết thành khối quanh tâm, " +
                            "còn lại trôi tự do trong không gian.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                }
                for (group in groups) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEdit(group) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(Color(group.color), 14.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(group.name, color = Color.White, modifier = Modifier.weight(1f))
                        Text("${group.packages.size} ứng dụng", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onPickCenter)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ứng dụng trung tâm", color = Color.White, modifier = Modifier.weight(1f))
                    if (centerApp != null) {
                        Image(bitmap = centerApp.iconBitmap, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        centerApp?.appName ?: "Chưa chọn",
                        color = if (centerApp != null) Accent else Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onShowAppNames(!showAppNames) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Hiện tên ứng dụng", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = showAppNames,
                        onCheckedChange = onShowAppNames,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Accent,
                        ),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onEditCover)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Khối che icon hệ thống", color = Color.White, modifier = Modifier.weight(1f))
                    Text(
                        if (coverEnabled) "Bật" else "Tắt",
                        color = if (coverEnabled) Accent else Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalOutlineButton("Xuất JSON", onExport, Modifier.weight(1f), enabled = groups.isNotEmpty())
                    TerminalOutlineButton("Nhập JSON", onImport, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            ) { Text("+ Tạo nhóm mới") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", color = Color.White) }
        },
    )
}

/** Confirms a JSON import: replace every group, or add these to the ones there are. */
@Composable
fun ImportGroupsDialog(
    config: GroupConfig,
    centerName: String?,
    hasGroups: Boolean,
    onReplace: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelColor,
        title = { Text("Nhập cấu hình nhóm", color = Color.White) },
        text = {
            val imported = config.groups
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (config.center != null) {
                    Text(
                        "Ứng dụng trung tâm: ${centerName ?: config.center}",
                        color = Accent,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    "File có ${imported.size} nhóm, ${imported.sumOf { it.packages.size }} ứng dụng. " +
                        "Ứng dụng chưa cài sẽ tự vào nhóm khi được cài.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                for (group in imported) {
                    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(Color(group.color), 12.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(group.name, color = Color.White, modifier = Modifier.weight(1f))
                        Text("${group.packages.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
                if (hasGroups) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onMerge) { Text("Gộp thêm vào nhóm hiện có", color = Accent) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onReplace,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            ) { Text(if (hasGroups) "Thay thế tất cả" else "Nhập") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ", color = Color.White) }
        },
    )
}

@Composable
private fun TerminalOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    val color = if (enabled) Accent else Accent.copy(alpha = 0.3f)
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .border(1.dp, color)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

/** Full-screen list to pick the centre app (or none). */
@Composable
fun CenterPickerDialog(
    apps: List<AppNode>,
    groups: List<AppGroup>,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groupOf = remember(groups) {
        buildMap { for (group in groups) for (pkg in group.packages) put(pkg, group.name) }
    }
    val sortedApps = remember(apps) { apps.sortedBy { it.appName.lowercase() } }
    val visibleApps = if (query.isBlank()) sortedApps else sortedApps.filter { it.appName.contains(query.trim(), ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = PanelColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Huỷ", color = Color.White) }
                    Text(
                        "Ứng dụng trung tâm",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
                Text(
                    "Nằm giữa không gian và nối dây tới tất cả các nhóm. Ứng dụng được chọn sẽ rời khỏi nhóm của nó.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Tìm ứng dụng…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OptionRow(
                    label = "Không dùng ứng dụng trung tâm",
                    color = Color.White.copy(alpha = 0.35f),
                    selected = current == null,
                    onClick = { onPick(null) },
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val selected = app.packageName == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(app.packageName) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(bitmap = app.iconBitmap, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    app.appName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                                groupOf[app.packageName]?.let { group ->
                                    Text("Đang ở nhóm \"$group\"", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                                }
                            }
                            if (selected) Text("✓", color = Accent, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Long-press menu for one app: move it into a group, out of all groups, or into a new group. */
@Composable
fun AssignGroupDialog(
    app: AppNode,
    groups: List<AppGroup>,
    isCenter: Boolean,
    onPick: (groupId: String?) -> Unit,
    onNewGroup: () -> Unit,
    onSetCenter: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentId = groups.firstOrNull { app.packageName in it.packages }?.id
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelColor,
        icon = { Image(bitmap = app.iconBitmap, contentDescription = null, modifier = Modifier.size(44.dp)) },
        title = { Text(app.appName, color = Color.White) },
        text = {
            if (isCenter) {
                // The centre app wires every group together; it can't join one.
                Column {
                    Text(
                        "Ứng dụng trung tâm: nằm giữa không gian và nối tới tất cả các nhóm, không thuộc nhóm nào.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onSetCenter(false) }) { Text("Bỏ làm ứng dụng trung tâm", color = Accent) }
                }
                return@AlertDialog
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (group in groups) {
                    OptionRow(
                        label = group.name,
                        color = Color(group.color),
                        selected = group.id == currentId,
                        onClick = { onPick(group.id) },
                    )
                }
                OptionRow(
                    label = "Không nhóm (trôi tự do)",
                    color = Color.White.copy(alpha = 0.35f),
                    selected = currentId == null,
                    onClick = { onPick(null) },
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onNewGroup) { Text("+ Nhóm mới với ứng dụng này", color = Accent) }
                TextButton(onClick = { onSetCenter(true) }) { Text("◎ Đặt làm ứng dụng trung tâm", color = Accent) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", color = Color.White) }
        },
    )
}

/** Full-screen editor: name, colour and which apps belong to the group. */
@Composable
fun GroupEditorDialog(
    draft: AppGroup,
    apps: List<AppNode>,
    groups: List<AppGroup>,
    centerPackage: String?,
    onSave: (AppGroup) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var color by remember(draft.id) { mutableIntStateOf(draft.color) }
    val selected = remember(draft.id) { mutableStateListOf<String>().apply { addAll(draft.packages) } }
    var query by remember { mutableStateOf("") }

    val otherGroupOf = remember(groups, draft.id) {
        buildMap {
            for (group in groups) {
                if (group.id == draft.id) continue
                for (pkg in group.packages) put(pkg, group.name)
            }
        }
    }
    // Apps already in another group aren't offered (one starting in this draft,
    // e.g. "new group with this app", still shows so it can be moved here).
    val sortedApps = remember(apps, otherGroupOf, draft.id, centerPackage) {
        apps.filter { it.packageName != centerPackage }
            .filter { it.packageName !in otherGroupOf || it.packageName in draft.packages }
            .sortedBy { it.appName.lowercase() }
    }
    val visibleApps = if (query.isBlank()) sortedApps else sortedApps.filter { it.appName.contains(query.trim(), ignoreCase = true) }
    val save = {
        onSave(
            draft.copy(
                name = name.trim().ifBlank { draft.name },
                color = color,
                packages = selected.toList(),
            )
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = PanelColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                // Actions at the top, clear of the gesture bar at the bottom.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Huỷ", color = Color.White) }
                    Text(
                        if (onDelete == null) "Tạo nhóm" else "Sửa nhóm",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Button(
                        onClick = save,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(color), contentColor = Color.Black),
                    ) { Text("Lưu") }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên nhóm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in GroupColors.PALETTE.chunked(10)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (option in row) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(Color(option))
                                        .border(
                                            width = if (option == color) 3.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape,
                                        )
                                        .clickable { color = option },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Tìm ứng dụng…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Đã chọn ${selected.size} ứng dụng",
                        color = Color(color),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                    )
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) { Text("Xoá nhóm", color = Color(0xFFFF5370)) }
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (checked) selected.remove(app.packageName) else selected.add(app.packageName)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(bitmap = app.iconBitmap, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, color = Color.White, fontSize = 15.sp)
                                otherGroupOf[app.packageName]?.let { other ->
                                    Text(
                                        "Sẽ chuyển từ nhóm \"$other\" sang nhóm này",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selected.add(app.packageName) else selected.remove(app.packageName)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Color(color), checkmarkColor = Color.Black),
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun OptionRow(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(color, 14.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("✓", color = color, fontSize = 16.sp)
    }
}

@Composable
private fun ColorDot(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

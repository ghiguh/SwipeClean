package com.aditya.swipeclean

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(item: MediaItem, vm: MainViewModel, onDismiss: () -> Unit) {
    val meta by produceState<Meta?>(null, item.id, vm.metaVersion) { value = vm.readMeta(item) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val m = meta
        if (m == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            EditForm(item, m) { vm.requestEdit(item, it); onDismiss() }
        }
    }
}

@Composable
private fun EditForm(item: MediaItem, m: Meta, onSave: (Meta) -> Unit) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false } }
    var date by remember {
        mutableStateOf((m.dateTaken ?: item.dateTakenMs.takeIf { it > 0 })?.let { fmt.format(Date(it)) } ?: "")
    }
    var desc by remember { mutableStateOf(m.description) }
    var make by remember { mutableStateOf(m.make) }
    var model by remember { mutableStateOf(m.model) }
    var lat by remember { mutableStateOf(m.lat?.toString() ?: "") }
    var lng by remember { mutableStateOf(m.lng?.toString() ?: "") }

    val dateMs = if (date.isBlank()) null else runCatching { fmt.parse(date.trim())?.time }.getOrNull()
    val dateOk = date.isBlank() || dateMs != null
    val latD = lat.trim().toDoubleOrNull()
    val lngD = lng.trim().toDoubleOrNull()
    val locBlank = lat.isBlank() && lng.isBlank()
    val locOk = locBlank || (latD != null && lngD != null && latD in -90.0..90.0 && lngD in -180.0..180.0)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)
            .navigationBarsPadding().imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Edit info", style = MaterialTheme.typography.titleLarge)
        Text(item.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        OutlinedTextField(
            date, { date = it }, Modifier.fillMaxWidth(), singleLine = true, isError = !dateOk,
            label = { Text("Date taken (yyyy-MM-dd HH:mm)") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { date = fmt.format(Date(item.dateAddedMs)) }, label = { Text("Use file date") })
            AssistChip(onClick = { date = fmt.format(Date()) }, label = { Text("Now") })
        }
        if (!item.isVideo) {
            OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(make, { make = it }, Modifier.weight(1f), singleLine = true, label = { Text("Camera make") })
                OutlinedTextField(model, { model = it }, Modifier.weight(1f), singleLine = true, label = { Text("Model") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    lat, { lat = it }, Modifier.weight(1f), singleLine = true, isError = !locOk, label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    lng, { lng = it }, Modifier.weight(1f), singleLine = true, isError = !locOk, label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        } else {
            Text("For videos only the date can be edited.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = {
                onSave(Meta(dateMs, desc.trim(), make.trim(), model.trim(), if (locBlank) null else latD, if (locBlank) null else lngD))
            },
            enabled = dateOk && locOk, modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
        Text("Android will ask you to approve the change.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

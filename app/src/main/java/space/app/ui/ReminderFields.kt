package space.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import space.app.remind.Freq
import space.app.remind.describe
import space.app.remind.rule

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatWhen(time: LocalDateTime): String = "${time.format(dayFormat)}, ${time.format(timeFormat)}"

/** A new reminder starts at the next full hour. */
fun defaultReminderTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenPicker(value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickingDate = true }) { Text(value.format(dayFormat)) }
        OutlinedButton(onClick = { pickingTime = true }) { Text(value.format(timeFormat)) }
    }
    if (pickingDate) {
        // The date picker works in UTC midnights.
        val state = rememberDatePickerState(initialSelectedDateMillis = value.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atTime(value.toLocalTime()))
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickingTime) {
        val state = rememberTimePickerState(value.hour, value.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = { onChange(value.withHour(state.hour).withMinute(state.minute)); pickingTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

/** None / a preset / every N days, weeks, months or years, with weekdays for weekly. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatPicker(value: String?, start: LocalDateTime, onChange: (String?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val parts = value?.split(';')?.associate { it.substringBefore('=') to it.substringAfter('=') }.orEmpty()
    val freq = parts["FREQ"]?.let(Freq::valueOf)
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    val days = parts["BYDAY"]?.split(',')?.mapNotNull { code -> DayOfWeek.entries.find { it.name.take(2) == code } }?.toSet()
        ?: setOf(start.dayOfWeek)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Repeat")
            TextButton(onClick = { menu = true }) { Text(value?.let(::describe) ?: "Never") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val presets = listOf(
                    "Never" to null,
                    "Every day" to "FREQ=DAILY",
                    "Weekdays" to "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
                    "Every week" to rule(Freq.WEEKLY, 1, setOf(start.dayOfWeek)),
                    "Every month" to "FREQ=MONTHLY",
                    "Every year" to "FREQ=YEARLY",
                )
                presets.forEach { (label, r) -> DropdownMenuItem(text = { Text(label) }, onClick = { onChange(r); menu = false }) }
            }
        }
        if (freq != null && freq != Freq.HOURLY) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Every")
                OutlinedTextField(
                    value = interval.toString(),
                    onValueChange = { t -> t.toIntOrNull()?.takeIf { it in 1..99 }?.let { onChange(rule(freq, it, days)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                )
                Text(freq.name.lowercase().removeSuffix("ly").replace("dai", "day") + if (interval > 1) "s" else "")
            }
        }
        if (freq == Freq.WEEKLY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(
                        selected = d in days,
                        onClick = { val next = if (d in days) days - d else days + d; if (next.isNotEmpty()) onChange(rule(freq, interval, next)) },
                        label = { Text(d.name.take(2).lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
        }
    }
}

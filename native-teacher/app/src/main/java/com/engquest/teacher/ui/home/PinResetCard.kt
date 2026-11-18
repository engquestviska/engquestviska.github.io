package com.engquest.teacher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.ui.Bad
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.Line
import com.engquest.teacher.ui.Muted
import kotlinx.coroutines.launch

private val Good = Color(0xFF059669)

/**
 * Home card: reset one student's EngQuest Chronicle PIN (they forgot it, or someone else set it).
 * [onReset] returns null when it worked, else a message to show.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PinResetCard(onReset: suspend (className: String, studentNo: String) -> String?) {
    var open by remember { mutableStateOf(false) }
    var cls by remember { mutableStateOf<String?>(null) }
    var number by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "🔑 Reset a student's Chronicle PIN",
            fontWeight = FontWeight.Bold, color = Ink,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open; result = null }.testTag("pinResetOpen"),
        )
        if (!open) return@Column
        Text("Pick the class, type the student's number. They make a new PIN next time they open their Chronicle.", color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Classes.ALL.forEach { c ->
                val on = cls == c.id
                Text(
                    c.label,
                    color = if (on) Color.White else Ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (on) Ink else Color.White).border(1.dp, if (on) Ink else Line, RoundedCornerShape(999.dp))
                        .clickable { cls = c.id; result = null }
                        .padding(horizontal = 12.dp, vertical = 6.dp).testTag("pinClass-${c.id}"),
                )
            }
        }
        OutlinedTextField(
            value = number,
            onValueChange = { v -> number = v.filter(Char::isDigit).take(3); result = null },
            label = { Text("Student number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("pinNumber"),
        )
        Button(
            onClick = {
                val c = cls ?: run { result = false to "Pick a class first."; return@Button }
                busy = true
                scope.launch {
                    val problem = onReset(c, number)
                    val label = Classes.ALL.firstOrNull { it.id == c }?.label ?: c
                    result = if (problem == null) true to "PIN reset for $label no. $number. They can make a new one now." else false to problem
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("pinResetGo"),
        ) { Text(if (busy) "Resetting…" else "Reset PIN") }
        result?.let { (ok, text) -> Text(text, color = if (ok) Good else Bad, fontWeight = FontWeight.SemiBold) }
    }
}

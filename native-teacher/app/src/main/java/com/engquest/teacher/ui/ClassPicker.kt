package com.engquest.teacher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.ui.theme.Brand

/** "Choose a class" sheet: Grade 10 and Grade 11, three to a row; the current class is marked. */
@Composable
fun ClassPicker(selectedId: String, onPick: (ClassInfo) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Choose a class", onDismiss, Modifier.testTag("classSheet")) {
        for ((grade, title) in listOf("10" to "Grade 10", "11" to "Grade 11")) {
            Text(
                title.uppercase(),
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 8.dp),
            )
            Classes.ALL.filter { it.grade == grade }.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { ClassOption(it, it.id == selectedId, Modifier.weight(1f)) { onPick(it) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ClassOption(info: ClassInfo, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .border(BorderStroke(1.5.dp, if (on) Brand else Line), shape)
            .background(if (on) BrandSoft else Color.White, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = on }
            .padding(vertical = 14.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(info.label, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (on) Brand else Ink)
    }
}

/** The top-bar pill showing the class; opens [ClassPicker]. */
@Composable
fun ClassChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .testTag("classChip")
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClickLabel = "Choose a class", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Icon(AppIcons.ChevronDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

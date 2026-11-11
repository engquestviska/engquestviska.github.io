package com.engquest.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.engquest.app.AppGraph
import com.engquest.app.domain.Profile
import com.engquest.app.push.NOTIFY_KINDS
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.theme.LocalEq
import com.engquest.app.ui.theme.Tint

/**
 * Notification switches. [permissionGranted] / [askPermission] come from the activity (Android 13+
 * asks the student once; after a "no", only the phone's own settings can turn it back on).
 */
@Composable
fun SettingsScreen(
    graph: AppGraph,
    profile: Profile,
    onBack: () -> Unit,
    permissionGranted: () -> Boolean,
    askPermission: () -> Unit,
) {
    val eq = LocalEq.current
    val n = graph.notify
    var tick by remember { mutableIntStateOf(0) } // re-read prefs after each change
    @Suppress("UNUSED_EXPRESSION") tick

    fun changed() {
        tick++
        graph.syncNotifyPrefs(profile)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = "Settings", onBack = onBack)
        SectionTitle("Notifications")
        EqCard(Modifier.padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Phone notifications", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (n.on) "A buzz when something happens" else "Notifications are off",
                        style = MaterialTheme.typography.bodySmall, color = eq.muted,
                    )
                }
                Switch(
                    checked = n.on,
                    onCheckedChange = { v ->
                        n.on = v
                        if (v && !permissionGranted()) askPermission()
                        changed()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = eq.accent, uncheckedThumbColor = eq.muted, uncheckedTrackColor = eq.line, uncheckedBorderColor = eq.muted),
                    modifier = Modifier.testTag("switch-on"),
                )
            }
            if (n.on && !permissionGranted()) {
                Text("Your phone is blocking EngQuest's notifications.", style = MaterialTheme.typography.bodySmall, color = Tint.amberInk)
                TextButton(onClick = askPermission) { Text("Turn on in phone settings") }
            }
        }
        if (n.on) {
            SectionTitle("Buzz me for")
            EqCard(Modifier.padding(horizontal = 14.dp)) {
                NOTIFY_KINDS.forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = n.wants(k), onCheckedChange = { n.set(k, it); changed() },
                            colors = SwitchDefaults.colors(checkedTrackColor = eq.accent, uncheckedThumbColor = eq.muted, uncheckedTrackColor = eq.line, uncheckedBorderColor = eq.muted),
                            modifier = Modifier.testTag("switch-$k"),
                        )
                    }
                }
            }
            Text(
                "Everything still shows in What's new (the bell), even when its buzz is off.",
                style = MaterialTheme.typography.bodySmall, color = eq.muted, modifier = Modifier.padding(18.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

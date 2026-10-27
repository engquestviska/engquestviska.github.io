package com.engquest.teacher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.data.ApiError
import com.engquest.teacher.data.Session
import com.engquest.teacher.ui.theme.Brand
import kotlinx.coroutines.launch

/**
 * Login card on the brand gradient. [note] is the line under the title (e.g. the
 * "log in again" message after the password stopped working); [initialUsername] pre-fills.
 */
@Composable
fun LoginScreen(session: Session, note: String, initialUsername: String) {
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    // Not saveable: the password never goes into saved instance state.
    var password by remember { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // Not saveable: a recreated screen has lost the check in progress, so it must not stay "Checking…".
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val u = username.trim()
        val p = password.trim()
        if (busy) return
        if (u.isEmpty() || p.isEmpty()) {
            error = "Please type your username and password."
            return
        }
        busy = true
        error = null
        scope.launch {
            error = try {
                if (session.login(u, p)) null else "That username or password didn't work. Please check and try again."
            } catch (e: ApiError) {
                loginErrorText(e)
            }
            busy = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6366F1), Brand, Color(0xFF312E81))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(22.dp))
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 22.dp),
        ) {
            Box(Modifier.size(56.dp).background(Brand, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Logo, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("EngQuest Teacher", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text(note, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("loginUser"),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("loginPass"),
            )
            Text(
                error.orEmpty(),
                color = Bad,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Button(
                onClick = { submit() },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (busy) "Checking…" else "Log in", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

/** Why the login could not be checked, in plain words. */
fun loginErrorText(e: ApiError): String = when (e) {
    is ApiError.Offline -> "No internet connection. Check your Wi-Fi or mobile data, then try again."
    is ApiError.RetryLater -> "The server is busy right now. Please try again in a moment."
    is ApiError.Unauthorized -> "That username or password didn't work. Please check and try again."
    is ApiError.Server -> {
        val code = Regex("""\b(\d{3})\b""").find(e.userMessage)?.value
        if (code != null) "The server had a problem ($code). Please try again later."
        else "The server had a problem. Please try again later."
    }
}

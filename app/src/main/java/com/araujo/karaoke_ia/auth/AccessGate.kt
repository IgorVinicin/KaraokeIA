package com.araujo.karaoke_ia.auth

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.araujo.karaoke_ia.ui.components.tvFocusFeedback
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccessGate(repository: AccessRepository, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {
        AccessGateContent(repository, content)
    }
}

@Composable
private fun AccessGateContent(repository: AccessRepository, content: @Composable () -> Unit) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val triggers = remember { Channel<Unit>(Channel.CONFLATED) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) triggers.trySend(Unit) }
        lifecycle.addObserver(observer)
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object: ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { triggers.trySend(Unit) }
            override fun onLost(network: Network) { triggers.trySend(Unit) }
        }
        manager.registerNetworkCallback(NetworkRequest.Builder().addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        onDispose { lifecycle.removeObserver(observer); manager.unregisterNetworkCallback(callback) }
    }
    LaunchedEffect(repository) {
        launch { repository.validate(); for (ignored in triggers) { delay(300); repository.validate() } }
        while (true) { delay(15_000); if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) triggers.trySend(Unit) }
    }
    if (state.allowed) {
        var confirmLogout by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.username + if (state.offline) " · offline" else " · conectado", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmLogout = true }, modifier = Modifier.tvFocusFeedback()) { Text("Sair da conta") }
            }
            Box(Modifier.weight(1f)) { content() }
        }
        if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Sair da conta?") },
            text = { Text("Será necessário entrar com usuário e senha novamente.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; scope.launch { repository.logout() } }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } })
    } else {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        val requester = remember { FocusRequester() }
        val focus = LocalFocusManager.current
        val keys = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionDown, Key.DirectionUp)) {
                focus.moveFocus(if (event.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up); true
            } else false
        }
        LaunchedEffect(state.checking) { if (!state.checking) requester.requestFocus() }
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("SoltaVoz", style = MaterialTheme.typography.headlineLarge)
                Text("Entre para começar a cantar", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(40) },
                    label = { Text("Usuário") },
                    singleLine = true,
                    modifier = keys.fillMaxWidth().focusRequester(requester),
                    enabled = !state.checking
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(256) },
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = keys.fillMaxWidth(),
                    enabled = !state.checking
                )
                Button(onClick = { val secret = password; password = ""; scope.launch { repository.login(username, secret) } },
                    enabled = !state.checking && username.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().tvFocusFeedback()) { Text("Entrar") }
                if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                if (!state.checking) TextButton(onClick = { scope.launch { repository.validate() } }, modifier = Modifier.tvFocusFeedback()) { Text("Já tenho acesso · tentar validar novamente") }
            }
        }
    }
}

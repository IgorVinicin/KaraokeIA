package com.araujo.karaoke_ia.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import com.araujo.karaoke_ia.online.OnlineRepository
import com.araujo.karaoke_ia.ui.theme.TextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchConfiguration(repository: OnlineRepository) {
    val configured by repository.searchConfigured.collectAsState()
    var open by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    Text(if (configured) "YouTube disponível quando a música não estiver no aparelho."
        else "A pesquisa no YouTube ainda precisa ser configurada.", color = TextMuted)
    if (!repository.usesSearchService) OutlinedButton(modifier = Modifier.tvFocusFeedback(), onClick = { open = true; error = null }) {
        Text("Configurar pesquisa do YouTube")
    }
    if (open) {
        fun save(remove: Boolean) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (remove) repository.clearSearchKey() else repository.configureSearchKey(input)
                    }
                    input = ""; error = null; open = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "Não foi possível salvar. Confira a chave e tente novamente." }
                finally { busy = false }
            }
        }
        AlertDialog(onDismissRequest = { if (!busy) { open = false; input = "" } },
            title = { Text("Pesquisa do YouTube") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Para distribuir o app, configure o servidor de pesquisa. Para uso pessoal, você pode salvar uma chave apenas neste aparelho, fora do APK e dos backups.")
                    OutlinedTextField(input, { input = it.take(256) }, Modifier.fillMaxWidth().tvFocusFeedback().onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                            Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                            Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                            else -> false
                        }
                    },
                        singleLine = true, enabled = !busy, label = { Text("Chave da YouTube Data API") },
                        visualTransformation = PasswordVisualTransformation())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (configured) OutlinedButton(modifier = Modifier.tvFocusFeedback(), enabled = !busy,
                        onClick = { save(true) }) { Text("Remover chave do aparelho") }
                }
            },
            confirmButton = { TextButton(modifier = Modifier.tvFocusFeedback(), enabled = !busy && input.isNotBlank(), onClick = { save(false) }) { Text("Salvar") } },
            dismissButton = { TextButton(modifier = Modifier.tvFocusFeedback(), enabled = !busy, onClick = { open = false; input = "" }) { Text("Cancelar") } })
    }
}

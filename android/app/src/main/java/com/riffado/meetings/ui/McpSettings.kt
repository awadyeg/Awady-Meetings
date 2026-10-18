package com.riffado.meetings.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riffado.meetings.R
import com.riffado.meetings.mcp.McpClientConfig
import com.riffado.meetings.mcp.McpNetwork
import com.riffado.meetings.mcp.McpServerState
import com.riffado.meetings.mcp.McpSettings
import com.riffado.meetings.mcp.McpSettingsStore
import com.riffado.meetings.mcp.McpToken

data class McpUiState(
    val settings: McpSettings,
    val server: McpServerState,
    val addresses: List<McpNetwork.LanAddress>,
)

class McpActions(
    val onToggle: (Boolean) -> Unit,
    val onKeepAwake: (Boolean) -> Unit,
    val onPort: (Int) -> Unit,
    val onRegenerate: () -> Unit,
)

/** Settings → "Connect AI agents (MCP)". */
@Composable
fun McpSettingsSection(state: McpUiState, actions: McpActions, initiallyShowToken: Boolean = false, initialStyle: Int = 0) {
    val c = Apple.colors
    val context = LocalContext.current
    val s = state.settings
    val port = (state.server as? McpServerState.Running)?.port ?: s.port
    val primary = state.addresses.firstOrNull()
    val url = McpNetwork.url(primary?.ip ?: "<phone-ip>", port)
    var showToken by rememberSaveable { mutableStateOf(initiallyShowToken) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var style by rememberSaveable { mutableIntStateOf(initialStyle) }
    var portText by rememberSaveable(s.port) { mutableStateOf(s.port.toString()) }
    val portValue = portText.toIntOrNull()
    val portValid = portValue != null && McpSettingsStore.validPort(portValue)

    InsetGroup(header = stringResource(R.string.mcp_section), footer = stringResource(R.string.mcp_footer_security)) {
        row {
            InsetRow(
                stringResource(R.string.mcp_toggle),
                subtitle = statusText(state.server, s.enabled),
                icon = Icons.Filled.Hub, iconColor = c.indigo,
                trailing = { IosSwitch(s.enabled, actions.onToggle) },
            )
        }
        if (s.enabled) {
            row { StatusCard(state, port) { text -> copy(context, context.getString(R.string.mcp_url), text, sensitive = false) } }
        }
        row {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    InsetTextFieldRow(stringResource(R.string.mcp_port), portText, { v -> portText = v.filter(Char::isDigit).take(5) }, placeholder = "8765", keyboardType = KeyboardType.Number)
                }
                if (portValid && portValue != s.port) {
                    CapsuleButton(stringResource(R.string.mcp_port_apply), { actions.onPort(portValue!!) }, style = CapsuleStyle.Tinted)
                }
            }
        }
        if (!portValid) {
            row { InsetContent { Text(stringResource(R.string.mcp_port_invalid), style = AppleType.footnote, color = c.red) } }
        }
        row {
            InsetToggleRow(
                stringResource(R.string.mcp_keep_awake), s.keepAwake, actions.onKeepAwake,
                subtitle = stringResource(R.string.mcp_keep_awake_sub), icon = Icons.Filled.Bolt, iconColor = c.orange,
            )
        }
    }

    InsetGroup(header = stringResource(R.string.mcp_token), footer = stringResource(R.string.mcp_token_help)) {
        row {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RowIcon(Icons.Filled.Key, c.green)
                Spacer(Modifier.width(14.dp))
                Text(
                    if (showToken) s.token else McpToken.masked(s.token),
                    style = AppleType.footnote.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                    color = c.label, maxLines = if (showToken) 3 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                    if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    stringResource(if (showToken) R.string.mcp_token_hide else R.string.mcp_token_show), { showToken = !showToken }, size = 36.dp, iconSize = 18.dp,
                )
                Spacer(Modifier.width(6.dp))
                GlassIconButton(
                    Icons.Filled.ContentCopy, stringResource(R.string.mcp_copy_token),
                    { copy(context, context.getString(R.string.mcp_token), s.token, sensitive = true) }, size = 36.dp, iconSize = 18.dp, tint = c.tint,
                )
            }
        }
        row { InsetButtonRow(stringResource(R.string.mcp_regenerate), { confirmRegenerate = true }, color = c.orange, icon = Icons.Filled.Refresh) }
    }

    InsetGroup(header = stringResource(R.string.mcp_client_config), footer = stringResource(R.string.mcp_client_help)) {
        row {
            InsetContent {
                SegmentedControl(listOf(stringResource(R.string.mcp_style_remote), stringResource(R.string.mcp_style_http)), style, { style = it })
                val kind = if (style == 0) McpClientConfig.Style.MCP_REMOTE else McpClientConfig.Style.HTTP
                val shown = McpClientConfig.snippet(kind, url, if (showToken) s.token else McpToken.masked(s.token))
                // JSON is always left-to-right, also in the Arabic UI (and scrolls from the left).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        shown,
                        style = AppleType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 16.sp, textDirection = TextDirection.Ltr),
                        color = c.label,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), elevation = 1.dp)
                            .horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
                CapsuleButton(
                    stringResource(R.string.mcp_copy_config),
                    { copy(context, context.getString(R.string.mcp_client_config), McpClientConfig.snippet(kind, url, s.token), sensitive = true) },
                    icon = Icons.Filled.ContentCopy, style = CapsuleStyle.Filled,
                )
            }
        }
    }

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text(stringResource(R.string.mcp_regenerate)) },
            text = { Text(stringResource(R.string.mcp_regenerate_confirm)) },
            confirmButton = { TextButton(onClick = { confirmRegenerate = false; actions.onRegenerate() }) { Text(stringResource(R.string.mcp_regenerate_ok)) } },
            dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun statusText(server: McpServerState, enabled: Boolean): String = when (server) {
    is McpServerState.Running -> stringResource(R.string.mcp_status_running, server.port)
    McpServerState.Starting -> stringResource(R.string.mcp_status_starting)
    is McpServerState.Failed -> stringResource(R.string.mcp_status_failed, server.message)
    McpServerState.Stopped -> stringResource(if (enabled) R.string.mcp_status_starting else R.string.mcp_status_off)
}

/** Glass card with the live status and every address the server can be reached on. */
@Composable
private fun StatusCard(state: McpUiState, port: Int, onCopy: (String) -> Unit) {
    val c = Apple.colors
    val server = state.server
    val (label, color) = when (server) {
        is McpServerState.Running -> stringResource(R.string.mcp_pill_running) to c.green
        is McpServerState.Failed -> stringResource(R.string.mcp_pill_failed) to c.red
        else -> stringResource(R.string.mcp_pill_starting) to c.orange
    }
    Column(
        Modifier.fillMaxWidth().padding(12.dp).glass(RoundedCornerShape(18.dp), elevation = 2.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            StatusPill(label, color)
            Spacer(Modifier.weight(1f))
            if (server is McpServerState.Running) {
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.mcp_requests, server.requests.toInt(), server.requests.toInt()), style = AppleType.footnote, color = c.secondaryLabel)
            }
        }
        if (server is McpServerState.Failed) {
            Text(server.message, style = AppleType.footnote.auto(server.message), color = c.red)
        }
        if (state.addresses.isEmpty()) {
            Text(stringResource(R.string.mcp_no_network), style = AppleType.footnote, color = c.secondaryLabel)
        }
        state.addresses.forEach { a ->
            val url = McpNetwork.url(a.ip, port)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onCopy(url) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lan, null, tint = if (a.kind == McpNetwork.Kind.TAILSCALE) c.indigo else c.tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            when (a.kind) {
                                McpNetwork.Kind.WIFI -> R.string.mcp_addr_wifi
                                McpNetwork.Kind.TAILSCALE -> R.string.mcp_addr_tailscale
                                McpNetwork.Kind.OTHER -> R.string.mcp_addr_other
                            },
                        ) + " · " + a.interfaceName,
                        style = AppleType.caption, color = c.secondaryLabel,
                    )
                    Text(url, style = AppleType.subheadline.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, textDirection = TextDirection.Ltr), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.ContentCopy, stringResource(R.string.mcp_copy_url), tint = c.tint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun copy(context: Context, label: String, text: String, sensitive: Boolean) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            else putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, R.string.mcp_copied, Toast.LENGTH_SHORT).show()
}


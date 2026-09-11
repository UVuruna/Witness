package com.pebblesoft.toolbox.ui.numbers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.data.DefaultRule
import com.pebblesoft.toolbox.data.NumberRule
import com.pebblesoft.toolbox.data.RuleMode
import com.pebblesoft.toolbox.data.UnknownRule
import com.pebblesoft.toolbox.ui.AppState
import com.pebblesoft.toolbox.ui.components.AdaptiveBody
import com.pebblesoft.toolbox.ui.components.Section
import com.pebblesoft.toolbox.ui.components.fullWidthItem
import com.pebblesoft.toolbox.ui.components.SoftCard

/**
 * Who gets recorded.
 *
 * Two lists and two defaults, and nothing else — because this screen is edited
 * in a hurry, sometimes by someone who has never changed a phone setting. The
 * two lists are the same kind of thing (see NumberRule), so they are drawn by
 * the same composable with a different mode.
 */
@Composable
fun NumbersScreen(
    state: AppState,
    onAdd: (number: String, label: String?, mode: RuleMode) -> Unit,
    onRemove: (NumberRule) -> Unit,
    onDefaultRule: (DefaultRule) -> Unit,
    onUnknownRule: (UnknownRule) -> Unit,
) {
    var adding by remember { mutableStateOf<RuleMode?>(null) }

    AdaptiveBody(spacing = 18.dp) {
        item {
            RuleList(
                title = stringResource(R.string.numbers_whitelist),
                emptyText = stringResource(R.string.numbers_whitelist_empty),
                icon = Icons.Filled.FiberManualRecord,
                tint = MaterialTheme.colorScheme.primary,
                rules = state.whitelist,
                onRemove = onRemove,
                onAdd = { adding = RuleMode.ALWAYS_RECORD },
            )
        }

        item {
            RuleList(
                title = stringResource(R.string.numbers_blacklist),
                emptyText = stringResource(R.string.numbers_blacklist_empty),
                icon = Icons.Filled.Block,
                tint = MaterialTheme.colorScheme.error,
                rules = state.blacklist,
                onRemove = onRemove,
                onAdd = { adding = RuleMode.NEVER_RECORD },
            )
        }

        item {
            Section(stringResource(R.string.numbers_rules_title)) {
                SoftCard {
                    ChoiceRow(
                        selected = state.defaultRule == DefaultRule.RECORD_EVERYTHING,
                        title = stringResource(R.string.numbers_default_all),
                        detail = stringResource(R.string.numbers_default_all_detail),
                    ) { onDefaultRule(DefaultRule.RECORD_EVERYTHING) }

                    Spacer(Modifier.height(10.dp))

                    ChoiceRow(
                        selected = state.defaultRule == DefaultRule.ONLY_WHITELIST,
                        title = stringResource(R.string.numbers_default_whitelist),
                        detail = stringResource(R.string.numbers_default_whitelist_detail),
                    ) { onDefaultRule(DefaultRule.ONLY_WHITELIST) }
                }
            }
        }

        item {
            Section(stringResource(R.string.numbers_unknown_title)) {
                SoftCard {
                    ChoiceRow(
                        selected = state.unknownRule == UnknownRule.RECORD,
                        title = stringResource(R.string.numbers_unknown_record),
                        detail = null,
                    ) { onUnknownRule(UnknownRule.RECORD) }

                    Spacer(Modifier.height(10.dp))

                    ChoiceRow(
                        selected = state.unknownRule == UnknownRule.SKIP,
                        title = stringResource(R.string.numbers_unknown_skip),
                        detail = null,
                    ) { onUnknownRule(UnknownRule.SKIP) }
                }
            }
        }

        fullWidthItem { Spacer(Modifier.height(24.dp)) }
    }

    adding?.let { mode ->
        AddNumberDialog(
            onDismiss = { adding = null },
            onConfirm = { number, label ->
                onAdd(number, label, mode)
                adding = null
            },
        )
    }
}

@Composable
private fun RuleList(
    title: String,
    emptyText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    rules: List<NumberRule>,
    onRemove: (NumberRule) -> Unit,
    onAdd: () -> Unit,
) {
    Section(title) {
        SoftCard {
            if (rules.isEmpty()) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rules.forEach { rule ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(icon, contentDescription = null, tint = tint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rule.label ?: rule.number, style = MaterialTheme.typography.bodyLarge)
                        if (rule.label != null) {
                            Text(
                                rule.number,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(rule) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.numbers_remove),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.numbers_add))
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    detail: String?,
    onSelect: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddNumberDialog(
    onDismiss: () -> Unit,
    onConfirm: (number: String, label: String?) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.numbers_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.numbers_type_it)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.numbers_from_contacts)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(number, label.takeIf { it.isNotBlank() }) },
                enabled = number.any { it.isDigit() },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

package com.x8rgb.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x8rgb.R

// Localised effect name. Order matches EffectEngine ids; source is the effect_names array in res values and values-en.
@Composable
fun effectName(id: Int): String =
    stringArrayResource(R.array.effect_names).getOrElse(id) { stringResource(R.string.effect_fallback) }

/** One picker for every effect menu: large tappable tiles, selected one is highlighted with a check. */
@Composable
fun EffectPickerDialog(
    title: String,
    ids: List<Int>,
    selectedId: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    labelOf: @Composable (Int) -> String = { effectName(it) },
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(ids) { id ->
                    val selected = id == selectedId
                    Surface(
                        onClick = { onPick(id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = labelOf(id),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                }
            }
        },
    )
}

package com.georgeapp.bulksmsreply.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeapp.bulksmsreply.AttributionExtractor

/**
 * Round 7 (2026-09-21): the small tag icon + Automatic/Political/
 * Commercial/No label menu (originally added in Round 6), factored out
 * into one shared composable so EVERY screen that shows a sender label -
 * Messages, S.R.B., Log, and Reports - offers the same correction control.
 *
 * Why this exists: Round 6 only put this control on the Messages tab. That
 * meant the moment a conversation was bulk-processed (which moves it to
 * S.R.B.) or simply reviewed later in Log or Reports, there was no way
 * left to fix a wrong automatic guess for it - which is exactly what Mr.
 * George ran into ("I cannot manually change the label," 2026-09-21),
 * since the numbers he most wants correctly labeled are usually the ones
 * he's already taken action on. Putting the control here once, and calling
 * it from all four screens, also avoids the kind of drift bug from the
 * Round 6 sync (two copies of similar logic quietly falling out of step).
 */
@Composable
fun LabelTagButton(
    labelOverride: String?,
    onSetLabelOverride: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Filled.Sell,
                contentDescription = "Set Political/Commercial/Personal/Other/No label",
                modifier = Modifier.size(16.dp),
                // A filled-in color hints at a glance that this number has
                // a manual override set, rather than the automatic guess.
                tint = if (labelOverride != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            AttributionExtractor.OVERRIDE_OPTIONS.forEach { (optionLabel, optionValue) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSetLabelOverride(optionValue)
                        showMenu = false
                    }
                )
            }
        }
    }
}

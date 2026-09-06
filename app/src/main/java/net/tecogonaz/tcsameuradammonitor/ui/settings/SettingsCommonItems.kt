// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppTheme

@Composable
fun SettingsCategoryTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
fun ThemeSettingItem(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { expanded = true },
                    role = Role.DropdownList,
                    onClickLabel = stringResource(R.string.desc_select_theme)
                )
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_system_theme),
                style = MaterialTheme.typography.titleMedium,
            )
            val themeDisplayName = when (currentTheme) {
                AppTheme.SYSTEM -> stringResource(id = R.string.theme_system)
                AppTheme.LIGHT -> stringResource(id = R.string.theme_light)
                AppTheme.DARK -> stringResource(id = R.string.theme_dark)
            }
            Text(
                text = themeDisplayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AppTheme.entries.forEach { theme ->
                val themeItemName = when (theme) {
                    AppTheme.SYSTEM -> stringResource(id = R.string.theme_system)
                    AppTheme.LIGHT -> stringResource(id = R.string.theme_light)
                    AppTheme.DARK -> stringResource(id = R.string.theme_dark)
                }
                DropdownMenuItem(
                    text = { Text(themeItemName) },
                    onClick = {
                        onThemeSelected(theme)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingItemSwitch(title: String, description: String? = null, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@Composable
fun SettingItemReadOnly(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingItemClickable(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                role = Role.Button
            )
            .padding(vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 設定項目の直下に表示する説明文（settings-help 相当）の共通Composable。
 *
 * @param text 説明文
 */
@Composable
fun SettingsHelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

package io.github.sapog3090.opprice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sapog3090.opprice.ui.theme.LocalExtraColors

/** Переключатель из нескольких вариантов, нарисованный вручную — плоский, без «пилюль». */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        options.forEachIndexed { i, option ->
            if (i > 0) Box(Modifier.width(1.dp).height(34.dp).background(MaterialTheme.colorScheme.outline))
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Фильтр-«чип» со счётчиком: «13 · 24». */
@Composable
fun FacetChip(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) colors.onSurface else colors.surface)
            .border(1.dp, if (selected) colors.onSurface else colors.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.surface else colors.onSurface)
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.surface.copy(alpha = 0.7f) else colors.onSurfaceVariant,
            )
        }
    }
}

/** Двухбуквенная метка площадки моноширинным шрифтом: WB, AV, AE, EB. */
@Composable
fun SourceTag(tag: String, modifier: Modifier = Modifier) {
    Text(
        tag,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.fillMaxWidth(), thickness = 1.dp, color = LocalExtraColors.current.hairline)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

package com.recipefordisaster.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.scene.Outfit
import com.recipefordisaster.app.ui.scene.Pen
import com.recipefordisaster.app.ui.scene.SceneLayout.Point
import com.recipefordisaster.app.ui.scene.drawPerson

/** Choose how your waiter looks: a big picture of them, and a row of choices for each part. */
@Composable
fun PlayerLookDialog(onClose: () -> Unit) {
    val looks = PlayerLooks.get(LocalContext.current)
    val look = looks.look
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.look_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFC9A27A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(110.dp)) {
                        // The figure is about 16 scene units tall; fit it in the box.
                        val unit = size.height / 18f
                        Pen(this, unit, Offset(size.width / 2 - 0f * unit, size.height * 0.62f)).drawPerson(
                            at = Point(0f, 0f),
                            outfit = Outfit.SERVER,
                            mood = 80,
                            apron = look.apronColor,
                            look = look,
                        )
                    }
                }
                LookRow(stringResource(R.string.look_skin), PlayerLook.SKINS, look.skin) { looks.update(look.copy(skin = it)) }
                LookRow(stringResource(R.string.look_hair), PlayerLook.HAIRS, look.hair) { looks.update(look.copy(hair = it)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.look_style), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { looks.update(look.copy(hairStyle = (look.style + 1) % PlayerLook.STYLES)) },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(stringResource(STYLE_NAMES[look.style]))
                    }
                }
                LookRow(stringResource(R.string.look_apron), PlayerLook.APRONS, look.apron) { looks.update(look.copy(apron = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.look_done)) } },
    )
}

private val STYLE_NAMES = listOf(R.string.look_style_parting, R.string.look_style_curls, R.string.look_style_long, R.string.look_style_bun, R.string.look_style_crop)

/** A label and a row of colour dots; the chosen one has a ring round it. */
@Composable
private fun LookRow(label: String, colours: List<Color>, chosen: Int, onChoose: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            colours.forEachIndexed { i, colour ->
                val picked = i == chosen.mod(colours.size)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(if (picked) 3.dp else 1.dp, if (picked) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(colour)
                        .semantics {
                            contentDescription = "$label ${i + 1}"
                            selected = picked
                        }
                        .clickable { onChoose(i) },
                )
            }
        }
    }
}

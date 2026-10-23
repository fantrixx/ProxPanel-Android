package com.proxpanel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.proxpanel.app.data.GuestIconMode
import com.proxpanel.app.data.GuestIconRecord
import com.proxpanel.app.data.resolveServiceIcon
import com.proxpanel.app.data.serviceIconUrl
import com.proxpanel.app.ui.theme.ProxCt
import com.proxpanel.app.ui.theme.ProxCtContainer
import com.proxpanel.app.ui.theme.ProxVm
import com.proxpanel.app.ui.theme.ProxVmContainer
import java.io.File

@Composable
fun GuestLogo(
    name: String,
    tags: String?,
    record: GuestIconRecord?,
    type: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(size * 0.22f)
    val mode = record?.mode?.let { GuestIconMode.fromId(it) } ?: GuestIconMode.Auto

    if (mode == GuestIconMode.None && onClick == null) return

    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    when (mode) {
        GuestIconMode.None -> {
            Box(
                modifier = modifier
                    .size(size * 0.55f)
                    .clip(shape)
                    .then(clickMod)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        GuestIconMode.Cdn -> {
            val slug = record?.slug
            if (!slug.isNullOrBlank()) {
                AsyncImage(
                    model = serviceIconUrl(slug),
                    contentDescription = name,
                    modifier = modifier
                        .size(size)
                        .clip(shape)
                        .then(clickMod),
                    contentScale = ContentScale.Fit,
                )
            } else {
                KindLetterAvatar(name, type, size, modifier.then(clickMod), shape)
            }
        }
        GuestIconMode.Upload -> {
            val path = record?.uri
            val model = when {
                path.isNullOrBlank() -> null
                path.startsWith("file://") || path.startsWith("content://") -> path
                else -> File(path)
            }
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = name,
                    modifier = modifier
                        .size(size)
                        .clip(shape)
                        .then(clickMod),
                    contentScale = ContentScale.Crop,
                )
            } else {
                KindLetterAvatar(name, type, size, modifier.then(clickMod), shape)
            }
        }
        GuestIconMode.Auto -> {
            val match = resolveServiceIcon(name, tags)
            if (match != null) {
                AsyncImage(
                    model = serviceIconUrl(match.slug),
                    contentDescription = match.label,
                    modifier = modifier
                        .size(size)
                        .clip(shape)
                        .then(clickMod),
                    contentScale = ContentScale.Fit,
                )
            } else {
                KindLetterAvatar(name, type, size, modifier.then(clickMod), shape)
            }
        }
    }
}

@Composable
private fun KindLetterAvatar(
    name: String,
    type: String,
    size: Dp,
    modifier: Modifier,
    shape: RoundedCornerShape,
) {
    val isCt = type == "lxc"
    val bg = if (isCt) ProxCtContainer else ProxVmContainer
    val fg = if (isCt) ProxCt else ProxVm
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = fg,
        )
    }
}

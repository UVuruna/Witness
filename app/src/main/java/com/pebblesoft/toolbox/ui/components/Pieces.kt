package com.pebblesoft.toolbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The small shared pieces every screen is built from.
 *
 * They exist so that spacing, corner radius and the way a heading sits above
 * its content are decided ONCE. A screen that invents its own card is a screen
 * that will drift away from the others.
 */

/**
 * A titled block of content. The title is optional — some blocks speak for
 * themselves.
 *
 * Padding only — the column count and the overall width cap are decided by
 * [AdaptiveBody], so a section never has to know how wide the screen is.


 */
@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        content()
    }
}

/** Below this the phone is narrow: one column, bottom bar. Above it, two and a rail. */
const val WIDE_DP = 600

/** As wide as the whole content may get before it stops being a page and becomes a field. */
val MAX_CONTENT = 1200.dp

@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_DP

/**
 * The scrolling body every screen is built on.
 *
 * One column on a phone held upright, TWO on anything wider — landscape, a
 * foldable, a tablet. This is the reflow step of SPACE & LEGIBILITY: before a
 * screen is allowed to hide content below the fold, it must first spend the
 * empty space beside it. The whole grid is capped at [MAX_CONTENT] and centred
 * so that on a very wide screen it stays a page rather than a stretched field.
 */
@Composable
fun AdaptiveBody(
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(if (isWide()) 2 else 1),
            modifier = Modifier.widthIn(max = MAX_CONTENT).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalItemSpacing = spacing,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** A grid cell that must take the whole row — a search box, a heading, a footer. */
fun LazyStaggeredGridScope.fullWidthItem(
    key: Any? = null,
    content: @Composable () -> Unit,
) = item(key = key, span = StaggeredGridItemSpan.FullLine) { content() }

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tone),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/**
 * The one big card at the top of the home screen: what state the app is in,
 * said in one short sentence, with the one action that changes it.
 */
@Composable
fun StatusCard(
    icon: ImageVector,
    headline: String,
    detail: String,
    accent: Color,
    onAccent: Color,
    action: (@Composable () -> Unit)? = null,
) {
    SoftCard(tone = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(onAccent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = onAccent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleLarge, color = onAccent)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = onAccent.copy(alpha = 0.86f),
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** What a screen shows before it has anything to show. Never a bare blank page. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A small coloured word — used for a recording's quality and its transcript state. */
@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

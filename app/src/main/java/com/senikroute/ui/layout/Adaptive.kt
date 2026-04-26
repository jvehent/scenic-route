package com.senikroute.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WidthClass { Compact, Medium, Expanded }

@Composable
@ReadOnlyComposable
fun widthClass(): WidthClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w < 600 -> WidthClass.Compact
        w < 840 -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
}

@Composable
@ReadOnlyComposable
fun isCompact(): Boolean = widthClass() == WidthClass.Compact

/** Max content width for text-heavy forms — keeps line length readable on wide screens. */
val FormMaxWidth: Dp = 600.dp

/** Map height scales with screen width class. */
@Composable
@ReadOnlyComposable
fun mapHeight(compact: Dp = 260.dp, medium: Dp = 360.dp, expanded: Dp = 480.dp): Dp =
    when (widthClass()) {
        WidthClass.Compact -> compact
        WidthClass.Medium -> medium
        WidthClass.Expanded -> expanded
    }

/** Minimum card width for adaptive grid columns — yields 1 col on phones, 2 on 7" tablets, 2-3 on 10". */
val CardGridMinWidth: Dp = 320.dp

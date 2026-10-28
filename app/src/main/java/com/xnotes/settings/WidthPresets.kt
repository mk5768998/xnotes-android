package com.xnotes.settings

import com.xnotes.core.tools.Tool

/** The width favourites a stroke tool's quick ink bar starts with, thin to thick. */
object WidthPresets {
    const val COUNT = 3

    fun defaults(tool: Tool): List<Double> = when (tool) {
        // The marker covers a line of handwriting at its middle width.
        Tool.HIGHLIGHTER -> listOf(10.0, 20.0, 36.0)
        else -> listOf(1.5, 3.0, 6.0)
    }
}

package com.danzucker.stitchpad.core.sharing

private val POSITIONAL_ARG = Regex("""%(\d+)\$[sd]""")

/**
 * Fills a raw positional template ("%1$s each", "Commande n° %1$s") the same way
 * Compose's `getString(res, args)` does. The share formatters and native renderers
 * are resource-free (pure / platform code), so callers resolve the raw template
 * once with `getString(res)` and the renderer fills it per row.
 */
fun fillTemplate(template: String, vararg args: Any): String =
    POSITIONAL_ARG.replace(template) { match ->
        val index = match.groupValues[1].toInt() - 1
        args.getOrNull(index)?.toString() ?: match.value
    }

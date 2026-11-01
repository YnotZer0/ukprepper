package uk.prepper.app.reference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Reference pages live in app/src/main/assets/reference/ (one .md file per topic) as plain text with
 * a tiny markdown subset:  "# Title", "## Heading", "- bullet", "1. step",
 * "! warning", "@image file.png | caption" (file in assets/reference/img),
 * blank line = new paragraph. Edit those files to change content.
 */
object ReferenceLibrary {
    data class Topic(val file: String, val title: String, val body: String)
    enum class Kind { H1, H2, BULLET, NUMBERED, WARNING, PARA, IMAGE }
    data class Block(val kind: Kind, val text: String, val image: String? = null)

    private var cache: List<Topic>? = null

    fun topics(ctx: Context): List<Topic> {
        cache?.let { return it }
        val am = ctx.assets
        val files = (am.list("reference") ?: emptyArray()).filter { it.endsWith(".md") }.sorted()
        val list = files.map { f ->
            val body = am.open("reference/$f").use { it.readBytes().toString(Charsets.UTF_8) }
            val title = body.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
                ?: f.removeSuffix(".md")
            Topic(f, title, body)
        }
        cache = list
        return list
    }

    /** Diagram from assets/reference/img. sample = 1 for full size, 2 for half, etc. */
    fun image(ctx: Context, file: String, sample: Int = 1): Bitmap? = runCatching {
        ctx.assets.open("reference/img/$file").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

    private val numbered = Regex("^\\d+\\.\\s+.*")

    fun blocks(body: String): List<Block> {
        val out = mutableListOf<Block>()
        val para = StringBuilder()
        fun flush() { if (para.isNotBlank()) out += Block(Kind.PARA, para.toString().trim()); para.clear() }
        for (raw in body.lines()) {
            val line = raw.trim()
            when {
                line.isBlank() -> flush()
                line.startsWith("## ") -> { flush(); out += Block(Kind.H2, line.removePrefix("## ").trim()) }
                line.startsWith("# ") -> { flush(); out += Block(Kind.H1, line.removePrefix("# ").trim()) }
                line.startsWith("- ") -> { flush(); out += Block(Kind.BULLET, line.removePrefix("- ").trim()) }
                line.startsWith("@image ") -> {
                    flush()
                    val parts = line.removePrefix("@image ").split("|", limit = 2)
                    out += Block(Kind.IMAGE, parts.getOrElse(1) { "" }.trim(), parts[0].trim())
                }
                line.startsWith("! ") -> { flush(); out += Block(Kind.WARNING, line.removePrefix("! ").trim()) }
                numbered.matches(line) -> { flush(); out += Block(Kind.NUMBERED, line.trim()) }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(line.trim()) }
            }
        }
        flush()
        return out
    }
}

package com.alex.aceshare.data.update

sealed interface ReleaseNoteBlock {
    val text: String

    data class Heading(override val text: String) : ReleaseNoteBlock
    data class Bullet(override val text: String) : ReleaseNoteBlock
    data class Paragraph(override val text: String) : ReleaseNoteBlock
}

fun parseReleaseNotes(
    markdown: String,
    displayedVersion: String? = null,
): List<ReleaseNoteBlock> {
    val blocks = mutableListOf<ReleaseNoteBlock>()
    val paragraph = StringBuilder()
    var inFence = false
    var lastWasBlank = true

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        paragraph.clear()
        if (text.isNotEmpty()) {
            blocks += ReleaseNoteBlock.Paragraph(text)
        }
    }

    val source = htmlComment.replace(markdown, "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    for (rawLine in source.lines()) {
        var line = rawLine.trim()
        if (line.startsWith(">")) {
            line = line.removePrefix(">").trim()
        }
        if (line.startsWith("```")) {
            flushParagraph()
            inFence = !inFence
            lastWasBlank = true
            continue
        }
        if (inFence) {
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(line)
            lastWasBlank = false
            continue
        }
        if (line.isEmpty() || hrLine.matches(line)) {
            flushParagraph()
            lastWasBlank = true
            continue
        }

        val heading = headingText(line)
        if (heading != null) {
            flushParagraph()
            val text = stripInlineMarkdown(heading)
            if (text.isNotEmpty() && !sameVersion(text, displayedVersion)) {
                blocks += ReleaseNoteBlock.Heading(text)
            }
            lastWasBlank = false
            continue
        }

        val bullet = bulletLine.matchEntire(line)?.groupValues?.get(1)
            ?: numberedLine.matchEntire(line)?.groupValues?.get(1)
        if (bullet != null) {
            flushParagraph()
            val text = stripInlineMarkdown(bullet)
            if (text.isNotEmpty()) {
                blocks += ReleaseNoteBlock.Bullet(text)
            }
            lastWasBlank = false
            continue
        }

        val previousBullet = blocks.lastOrNull() as? ReleaseNoteBlock.Bullet
        if (
            !lastWasBlank &&
            paragraph.isEmpty() &&
            rawLine.firstOrNull()?.isWhitespace() == true &&
            previousBullet != null
        ) {
            val text = stripInlineMarkdown(line)
            if (text.isNotEmpty()) {
                blocks[blocks.lastIndex] = ReleaseNoteBlock.Bullet(joinWrap(previousBullet.text, text))
            }
            lastWasBlank = false
            continue
        }

        val plain = stripInlineMarkdown(line)
        if (plain.isEmpty()) {
            lastWasBlank = false
            continue
        }
        if (paragraph.isNotEmpty()) paragraph.append('\n')
        paragraph.append(plain)
        lastWasBlank = false
    }
    flushParagraph()
    return blocks
}

private fun headingText(line: String): String? {
    headingMarkdown.matchEntire(line)?.let { return it.groupValues[1] }
    boldLine.matchEntire(line)?.let { return it.groupValues[1] }
    underscoreBoldLine.matchEntire(line)?.let { return it.groupValues[1] }
    return null
}

private fun stripInlineMarkdown(text: String): String {
    var value = text.trim()
    value = image.replace(value, "$1")
    value = link.replace(value, "$1")
    value = inlineBold.replace(value, "$1")
    value = inlineUnderscoreBold.replace(value, "$1")
    value = inlineCode.replace(value, "$1")
    return value.trim()
}

private fun sameVersion(heading: String, displayedVersion: String?): Boolean {
    if (displayedVersion.isNullOrBlank()) return false
    return normalizeVersion(heading) == normalizeVersion(displayedVersion)
}

private fun normalizeVersion(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.startsWith("v") -> trimmed.substring(1)
        trimmed.startsWith("V") -> trimmed.substring(1)
        else -> trimmed
    }
}

private fun joinWrap(left: String, right: String): String {
    val leftAscii = left.lastOrNull()?.isAsciiWord() == true
    val rightAscii = right.firstOrNull()?.isAsciiWord() == true
    return if (leftAscii || rightAscii) "$left $right" else left + right
}

private fun Char.isAsciiWord(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

private val htmlComment = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
private val headingMarkdown = Regex("""^#{1,6}\s+(.+?)\s*#*$""")
private val boldLine = Regex("""^\*\*(.+?)\*\*$""")
private val underscoreBoldLine = Regex("""^__(.+?)__$""")
private val bulletLine = Regex("""^(?:[-*+]\s+|•\s*|·\s*)(.+)$""")
private val numberedLine = Regex("""^\d+[.)]\s+(.+)$""")
private val hrLine = Regex("""^([-*_])\1{2,}$""")
private val image = Regex("""!\[([^]]*)]\([^)]*\)""")
private val link = Regex("""\[([^]]*)]\([^)]*\)""")
private val inlineBold = Regex("""\*\*(.+?)\*\*""")
private val inlineUnderscoreBold = Regex("""__(.+?)__""")
private val inlineCode = Regex("""`([^`]*)`""")

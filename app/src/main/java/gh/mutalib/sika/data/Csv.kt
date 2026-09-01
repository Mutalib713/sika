package gh.mutalib.sika.data

/**
 * The smallest correct CSV reader and writer, written by hand.
 *
 * ⚠ **`split(",")` cannot read this file, and reaching for it would corrupt the backup
 * silently.** Sacred Rule 6 keeps the whole SMS body on every row, and a MoMo message is full
 * of commas — *"Fee charged: GHS0.50,Tax Charged 0."* — so a naive split turns one row into
 * several, shifts every column right, and produces a file that still looks like a CSV. The
 * failure would only surface on the day it was needed, which is the worst possible day.
 *
 * So this follows RFC 4180 properly, and only that:
 *   * a field containing a comma, a quote or a newline is wrapped in double quotes;
 *   * a quote inside a quoted field is doubled (`""`);
 *   * everything else is written as-is.
 *
 * ⚠ **Empty and absent are different things and stay different.** A missing label is `null`;
 * a label that is the empty string is not a thing Sika can produce, but a *note* could be, and
 * conflating them would turn "no note" into "a note that says nothing". Null is written as an
 * unquoted empty field, an empty string as `""` — two characters that survive a round trip.
 */
object Csv {

    private const val QUOTE = '"'

    /** One field, escaped only if it has to be. Null becomes a genuinely empty field. */
    fun field(value: String?): String {
        if (value == null) return ""
        if (value.isEmpty()) return "\"\""
        val needsQuotes = value.any { it == ',' || it == QUOTE || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return QUOTE + value.replace("\"", "\"\"") + QUOTE
    }

    fun row(values: List<String?>): String = values.joinToString(",") { field(it) }

    /**
     * Splits a whole file into rows of fields.
     *
     * Done in one pass over the characters rather than line by line, because **a quoted field
     * may contain a newline** — an SMS body can wrap — and a line-by-line reader would end the
     * record in the middle of one. Row boundaries are decided by the quoting state, not by the
     * line breaks.
     *
     * Accepts `\n` and `\r\n`. A trailing newline does not produce a final empty row.
     */
    fun parse(text: String): List<List<String?>> {
        val rows = mutableListOf<List<String?>>()
        var row = mutableListOf<String?>()
        val field = StringBuilder()
        var quoted = false      // inside a "..." field
        var wasQuoted = false   // this field was quoted, so "" means empty string not null
        var i = 0

        fun endField() {
            val raw = field.toString()
            row.add(if (raw.isEmpty() && !wasQuoted) null else raw)
            field.setLength(0)
            wasQuoted = false
        }

        fun endRow() {
            endField()
            // A line that is entirely empty is not a record - it is the gap after the last one.
            if (row.size > 1 || row.firstOrNull() != null) rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == QUOTE && i + 1 < text.length && text[i + 1] == QUOTE -> {
                    field.append(QUOTE); i++
                }
                quoted && c == QUOTE -> quoted = false
                quoted -> field.append(c)
                c == QUOTE -> { quoted = true; wasQuoted = true }
                c == ',' -> endField()
                c == '\r' -> { /* swallowed; the \n that follows ends the row */ }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        // Whatever is still in hand when the text runs out is the last record, unless the file
        // ended cleanly on a newline and there is nothing pending.
        if (field.isNotEmpty() || wasQuoted || row.isNotEmpty()) endRow()
        return rows
    }
}

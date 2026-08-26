package com.example.keyboard.input

/**
 * Bộ gõ tiếng Việt Telex + VNI
 * - Telex: aa->â, aw->ă, ee->ê, oo->ô, ow->ơ, uw/w->ư, dd->đ, s/f/r/x/j dấu
 * - VNI: 1 sắc, 2 huyền, 3 hỏi, 4 ngã, 5 nặng, 6 â/ê/ô, 7 ă/ơ/ư, 8 ă, 9 đ
 * Hỗ trợ lùi dấu (gõ lại cùng phím dấu để bỏ / đổi dấu)
 */
class VietnameseEngine {

    enum class Mode { OFF, TELEX, VNI }

    var mode: Mode = Mode.TELEX

    private val buffer = StringBuilder()

    fun reset() { buffer.clear() }

    fun current(): String = buffer.toString()

    /**
     * Nhận 1 ký tự vừa gõ. Trả về chuỗi thay thế cho buffer hiện tại
     * (caller xóa buffer cũ trên editor rồi commit chuỗi mới).
     */
    fun process(ch: Char): String {
        if (mode == Mode.OFF) {
            buffer.append(ch)
            val out = buffer.toString()
            if (ch == ' ' || ch == '\n') buffer.clear()
            return out
        }
        // Ký tự kết thúc từ (dấu câu, khoảng trắng…)
        if (!ch.isLetter() && ch !in '1'..'9') {
            buffer.append(ch)
            val out = buffer.toString()
            buffer.clear()
            return out
        }
        buffer.append(ch)
        val composed = when (mode) {
            Mode.TELEX -> composeTelex(buffer.toString())
            Mode.VNI -> composeVni(buffer.toString())
            Mode.OFF -> buffer.toString()
        }
        buffer.clear()
        buffer.append(composed)
        return composed
    }

    fun backspace(): String {
        if (buffer.isNotEmpty()) {
            buffer.deleteCharAt(buffer.length - 1)
        }
        return buffer.toString()
    }

    // ---- Telex ----
    private fun composeTelex(raw: String): String {
        if (raw.isEmpty()) return raw
        var s = raw
        // Đ
        s = replacePair(s, "dd", "đ")
        s = replacePair(s, "DD", "Đ")
        s = replacePair(s, "dD", "Đ")
        s = replacePair(s, "Dd", "Đ")
        // Nguyên âm đôi
        s = replaceVowels(s, listOf(
            "uw" to "ư", "Ưw" to "Ư", "Uw" to "Ư", "uW" to "ư",
            "ow" to "ơ", "Ow" to "Ơ",
            "aw" to "ă", "Aw" to "Ă",
            "aa" to "â", "Aa" to "Â",
            "ee" to "ê", "Ee" to "Ê",
            "oo" to "ô", "Oo" to "Ô",
            "uo" to "ươ" // gõ tắt
        ))
        // w đứng một mình sau nguyên âm u/ơ đã xử lý; w -> ư nếu còn
        if (s.endsWith("w", true) && s.length >= 1) {
            val base = s.dropLast(1)
            val isUpper = s.last().isUpperCase()
            s = base + if (isUpper) "Ư" else "ư"
        }
        // Dấu thanh cuối
        val toneMap = mapOf(
            's' to Tone.SAC, 'f' to Tone.HUYEN, 'r' to Tone.HOI,
            'x' to Tone.NGA, 'j' to Tone.NANG
        )
        val last = s.lastOrNull()?.lowercaseChar()
        if (last != null && last in toneMap && s.length >= 2) {
            val body = s.dropLast(1)
            val toned = applyTone(body, toneMap[last]!!)
            s = if (toned != null) toned else s
        }
        return s
    }

    // ---- VNI ----
    private fun composeVni(raw: String): String {
        if (raw.isEmpty()) return raw
        var s = raw
        val last = s.lastOrNull() ?: return s
        if (last !in '1'..'9') return s
        val body = s.dropLast(1)
        if (body.isEmpty()) return s
        s = when (last) {
            '1' -> applyTone(body, Tone.SAC) ?: s
            '2' -> applyTone(body, Tone.HUYEN) ?: s
            '3' -> applyTone(body, Tone.HOI) ?: s
            '4' -> applyTone(body, Tone.NGA) ?: s
            '5' -> applyTone(body, Tone.NANG) ?: s
            '6' -> applyMark(body, Mark.CIRCUMFLEX) ?: s  // â ê ô
            '7' -> applyMark(body, Mark.HORN_OR_BREVE) ?: s // ă ơ ư
            '8' -> applyMark(body, Mark.BREVE) ?: s // ă
            '9' -> {
                val i = body.indexOfLast { it.equals('d', true) }
                if (i >= 0) {
                    val c = if (body[i].isUpperCase()) 'Đ' else 'đ'
                    body.substring(0, i) + c + body.substring(i + 1)
                } else s
            }
            else -> s
        }
        return s
    }

    private fun replacePair(s: String, from: String, to: String): String {
        val idx = s.indexOf(from, ignoreCase = false)
        if (idx >= 0) return s.substring(0, idx) + to + s.substring(idx + from.length)
        // try case variants handled by callers
        return s
    }

    private fun replaceVowels(s: String, pairs: List<Pair<String, String>>): String {
        var out = s
        for ((a, b) in pairs) {
            val i = out.indexOf(a)
            if (i >= 0) {
                out = out.substring(0, i) + b + out.substring(i + a.length)
            }
        }
        return out
    }

    private enum class Tone { NONE, SAC, HUYEN, HOI, NGA, NANG }
    private enum class Mark { CIRCUMFLEX, HORN_OR_BREVE, BREVE }

    private val toneTable = mapOf(
        // a ă â
        'a' to listOf("a", "á", "à", "ả", "ã", "ạ"),
        'ă' to listOf("ă", "ắ", "ằ", "ẳ", "ẵ", "ặ"),
        'â' to listOf("â", "ấ", "ầ", "ẩ", "ẫ", "ậ"),
        'e' to listOf("e", "é", "è", "ẻ", "ẽ", "ẹ"),
        'ê' to listOf("ê", "ế", "ề", "ể", "ễ", "ệ"),
        'i' to listOf("i", "í", "ì", "ỉ", "ĩ", "ị"),
        'o' to listOf("o", "ó", "ò", "ỏ", "õ", "ọ"),
        'ô' to listOf("ô", "ố", "ồ", "ổ", "ỗ", "ộ"),
        'ơ' to listOf("ơ", "ớ", "ờ", "ở", "ỡ", "ợ"),
        'u' to listOf("u", "ú", "ù", "ủ", "ũ", "ụ"),
        'ư' to listOf("ư", "ứ", "ừ", "ử", "ữ", "ự"),
        'y' to listOf("y", "ý", "ỳ", "ỷ", "ỹ", "ỵ")
    )

    private fun applyTone(word: String, tone: Tone): String? {
        // Chọn nguyên âm đặt dấu theo quy tắc đơn giản: ưu tiên nguyên âm cuối / ê ô ơ ư
        val chars = word.toCharArray()
        var target = -1
        for (i in chars.indices.reversed()) {
            val base = stripTone(chars[i].lowercaseChar())
            if (base in toneTable) {
                target = i
                // ưu tiên ê ô ơ ư â ă
                if (base in setOf('ê', 'ô', 'ơ', 'ư', 'â', 'ă')) break
            }
        }
        if (target < 0) return null
        val orig = chars[target]
        val lower = stripTone(orig.lowercaseChar())
        val list = toneTable[lower] ?: return null
        val idx = tone.ordinal
        var result = list.getOrElse(idx) { list[0] }
        if (orig.isUpperCase()) result = result.replaceFirstChar { it.uppercaseChar() }
        // Lùi dấu: nếu đã cùng tone thì về không dấu
        val currentTone = detectTone(orig.lowercaseChar())
        if (currentTone == tone) {
            result = list[0]
            if (orig.isUpperCase()) result = result.replaceFirstChar { it.uppercaseChar() }
        }
        chars[target] = result[0]
        return String(chars)
    }

    private fun applyMark(word: String, mark: Mark): String? {
        val chars = word.toCharArray()
        for (i in chars.indices.reversed()) {
            val c = stripTone(chars[i].lowercaseChar())
            val upper = chars[i].isUpperCase()
            val repl = when (mark) {
                Mark.CIRCUMFLEX -> when (c) {
                    'a' -> "â"; 'e' -> "ê"; 'o' -> "ô"; else -> null
                }
                Mark.BREVE -> if (c == 'a') "ă" else null
                Mark.HORN_OR_BREVE -> when (c) {
                    'a' -> "ă"; 'o' -> "ơ"; 'u' -> "ư"; else -> null
                }
            } ?: continue
            chars[i] = if (upper) repl[0].uppercaseChar() else repl[0]
            return String(chars)
        }
        return null
    }

    private fun stripTone(c: Char): Char {
        for ((base, list) in toneTable) {
            if (list.any { it[0] == c }) return base
        }
        return c
    }

    private fun detectTone(c: Char): Tone {
        for ((_, list) in toneTable) {
            val i = list.indexOfFirst { it[0] == c }
            if (i >= 0) return Tone.entries[i]
        }
        return Tone.NONE
    }
}

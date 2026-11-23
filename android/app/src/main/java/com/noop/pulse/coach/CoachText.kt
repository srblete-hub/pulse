package com.noop.pulse.coach

import java.text.Normalizer

/**
 * Text normalisation for the rule-based coach. Lower-cases, strips accents, expands common Spanish chat
 * abbreviations, squeezes repeated letters ("holaaa" → "hola") and tokenises. Matching is stem-based and
 * tolerant of one typo on longer words, so "recuperasion" still reaches the recovery rules.
 */
object CoachText {

    private val abbreviations = mapOf(
        "q" to "que", "k" to "que", "ke" to "que", "xq" to "porque", "pq" to "porque", "porq" to "porque",
        "xk" to "porque", "tb" to "tambien", "tmb" to "tambien", "tbn" to "tambien", "x" to "por",
        "d" to "de", "dnd" to "donde", "cdo" to "cuando", "qtal" to "que tal", "ntp" to "no te preocupes",
        "grax" to "gracias", "graciass" to "gracias", "thx" to "gracias", "ok" to "vale", "okey" to "vale",
        "oki" to "vale", "vle" to "vale", "bn" to "bien", "mñn" to "manana", "mn" to "manana",
        "finde" to "fin de semana", "hrs" to "horas", "hr" to "horas", "h" to "horas", "min" to "minutos",
        "mins" to "minutos", "ejer" to "ejercicio", "entreno" to "entrenar", "gym" to "gimnasio",
        "gim" to "gimnasio", "hrv" to "vfc", "rhr" to "fcr", "spo2" to "oxigeno", "zzz" to "dormir",
        "hola!" to "hola", "buenas" to "hola", "ey" to "hola", "hey" to "hola", "hi" to "hola", "hello" to "hola",
        "si!" to "si", "sii" to "si", "sip" to "si", "sep" to "si", "yes" to "si", "nop" to "no", "nope" to "no",
    )

    fun normalize(input: String): String {
        val lower = input.lowercase()
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val cleaned = noAccents
            .replace(Regex("(\\d)[,.](\\d)"), "$1.$2")
            .replace(Regex("[^a-z0-9.:\\s]"), " ")
            .replace(Regex("([a-z])\\1{2,}"), "$1")
        return cleaned.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trim('.', ':') }
            .filter { it.isNotBlank() }
            .flatMap { (abbreviations[it] ?: it).split(' ') }
            .joinToString(" ")
    }

    fun tokens(normalized: String): List<String> = normalized.split(' ').filter { it.isNotBlank() }

    /** Levenshtein distance, early-exit above [limit]. */
    fun distance(a: String, b: String, limit: Int = 2): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                rowMin = minOf(rowMin, cur[j])
            }
            if (rowMin > limit) return limit + 1
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * A stem matches a token when the token starts with it, or — for stems of 5+ letters — when the
     * token's prefix of the same length is within one edit (typos like "recuperasion", "dormdo").
     * A stem ending in '$' must match the whole token ("si$" does not match "sientes").
     */
    fun stemMatches(stem: String, token: String): Boolean {
        if (stem.endsWith('$')) {
            val exact = stem.dropLast(1)
            return token == exact || (exact.length >= 5 && distance(exact, token, 1) <= 1)
        }
        if (token.startsWith(stem)) return true
        if (stem.length >= 5 && token.length >= stem.length - 1) {
            val prefix = token.take(stem.length)
            if (distance(stem, prefix, 1) <= 1) return true
            if (distance(stem, token.take(stem.length + 1), 1) <= 1) return true
        }
        return false
    }

    /** All stems of a space-separated phrase must appear somewhere in the tokens. */
    fun phraseMatches(phrase: String, tokens: List<String>): Boolean =
        phrase.split(' ').all { stem -> tokens.any { stemMatches(stem, it) } }

    /** True when any of [phrases] matches (each phrase: all its stems present). */
    fun containsAny(tokens: List<String>, vararg phrases: String): Boolean =
        phrases.any { phraseMatches(it, tokens) }

    // ---- entity extraction ----------------------------------------------------------------------

    private val numberWords = mapOf(
        "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0, "cinco" to 5.0,
        "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0, "once" to 11.0,
        "doce" to 12.0, "media" to 0.5, "medio" to 0.5, "ninguno" to 0.0, "ninguna" to 0.0, "cero" to 0.0,
    )

    /** First number in the message, accepting digits ("6.5", "6:30") and Spanish number words. */
    fun firstNumber(normalized: String): Double? {
        val tokens = tokens(normalized)
        for ((i, t) in tokens.withIndex()) {
            Regex("^(\\d{1,2}):(\\d{2})$").find(t)?.let { m ->
                return m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / 60.0
            }
            Regex("^(\\d{1,2}(?:\\.\\d)?)(h|horas|kg|km|min)$").find(t)?.let { m -> return m.groupValues[1].toDouble() }
            t.toDoubleOrNull()?.let { v ->
                // "6 y media"
                if (tokens.getOrNull(i + 1) == "y" && tokens.getOrNull(i + 2) in setOf("media", "medio")) return v + 0.5
                return v
            }
            numberWords[t]?.let { v ->
                if (t == "media" || t == "medio") return@let
                if (tokens.getOrNull(i + 1) == "y" && tokens.getOrNull(i + 2) in setOf("media", "medio")) return v + 0.5
                return v
            }
        }
        return null
    }

    /** "me llamo Ana" / "soy Ana" / "mi nombre es Ana" → "Ana" (original casing). */
    fun extractName(original: String): String? {
        val m = Regex("(?i)(?:me llamo|mi nombre es|llamame|puedes llamarme|soy)\\s+([A-Za-zÁÉÍÓÚÑáéíóúñ]{2,20})").find(original)
            ?: return null
        val name = m.groupValues[1]
        val notNames = setOf(
            "muy", "un", "una", "de", "del", "bastante", "nuevo", "nueva", "principiante", "deportista", "corredor",
            "corredora", "ciclista", "mujer", "hombre", "chico", "chica", "mayor", "joven", "vegano", "vegana",
            "vegetariano", "vegetariana", "nocturno", "nocturna", "madrugador", "madrugadora", "el", "la", "tu",
        )
        if (name.lowercase() in notNames) return null
        return name.replaceFirstChar { it.uppercase() }
    }
}

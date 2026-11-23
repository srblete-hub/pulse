package com.noop.pulse.coach

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Everything the coach knows about the user's day, read from noop's stored rows. All optional: the
 * coach words its answers around what is missing instead of inventing numbers.
 */
data class CoachSnapshot(
    val recovery: Double? = null,
    val sleepPct: Double? = null,
    val sleepMin: Double? = null,
    val deepMin: Double? = null,
    val remMin: Double? = null,
    val efficiency: Double? = null,
    val disturbances: Int? = null,
    val hrv: Double? = null,
    val hrvAvg: Double? = null,
    val rhr: Double? = null,
    val rhrAvg: Double? = null,
    val resp: Double? = null,
    val respAvg: Double? = null,
    val skinTempDev: Double? = null,
    /** Effort on the 0–21 scale. */
    val effort: Double? = null,
    val effortAvg: Double? = null,
    val yesterdayEffort: Double? = null,
    val steps: Int? = null,
    val stepsAvg: Double? = null,
    val kcal: Double? = null,
    val stress: Double? = null,
    val nights: Int = 0,
    val streak: Int = 0,
    val age: Int? = null,
    val sex: String = "",
    val pulseAge: Double? = null,
    val weekRecovery: List<Double?> = emptyList(),
    val weekSleepMin: List<Double?> = emptyList(),
    val weekEffort: List<Double?> = emptyList(),
    val hour: Int = 12,
    val batteryPct: Double? = null,
    val connected: Boolean = false,
)

/** Small key-value store so the coach remembers name, goal, sport and habits between sessions. */
interface CoachMemory {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

class MapCoachMemory : CoachMemory {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun set(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** Screens a reply can offer to open. */
enum class CoachAction(val label: String) {
    Sleep("Ver mi sueño"), Recovery("Ver recuperación"), Effort("Ver esfuerzo"), Stress("Ver estrés"),
    Age("Ver edad fisiológica"), Alarms("Horario de sueño"), Live("Iniciar actividad"), Strap("Ver pulsera"),
    Import("Importar datos"), Backup("Copia de seguridad"), Journal("Abrir diario"), Calibration("Ver calibración"),
    Trends("Ver tendencias"), Profile("Ver perfil"),
}

data class CoachReply(
    val text: String,
    val quickReplies: List<String> = emptyList(),
    val action: CoachAction? = null,
    /** Which rule answered; used by tests and to chain "¿por qué?" / "más". */
    val intent: String = "",
)

/**
 * Offline, rule-based coach. No model, no network: a keyword/intent matcher with typo tolerance, a
 * conversation state machine for follow-up questions, a small memory, and answer templates that read the
 * user's own numbers. Safety rules run before anything else.
 */
class CoachEngine(
    private val memory: CoachMemory,
    private val random: Random = Random.Default,
) {
    private class Ctx(
        val original: String,
        val norm: String,
        val tokens: List<String>,
        val s: CoachSnapshot,
    ) {
        fun has(vararg stems: String) = CoachText.containsAny(tokens, *stems)
        val number: Double? by lazy { CoachText.firstNumber(norm) }
    }

    private class Rule(
        val id: String,
        val phrases: List<Pair<String, Double>>,
        val answer: (Ctx) -> CoachReply,
        val why: ((Ctx) -> String)? = null,
        val more: ((Ctx) -> CoachReply)? = null,
    )

    private fun pick(vararg options: String): String = options[random.nextInt(options.size)]

    private fun p(vararg phrases: String, w: Double = 1.0) = phrases.map { it to w }

    // ---- memory helpers ------------------------------------------------------------------------

    private val name get() = memory.get("name")
    private val goal get() = memory.get("goal")
    private val sport get() = memory.get("sport")
    private val sleepNeedH: Double get() = memory.get("sleep_need")?.toDoubleOrNull() ?: 8.0
    private fun hi() = name?.let { ", $it" } ?: ""

    private fun goalLabel(g: String?) = when (g) {
        "dormir" -> "dormir mejor"
        "rendimiento" -> "rendir más en tus entrenamientos"
        "peso" -> "perder grasa"
        "musculo" -> "ganar músculo"
        "estres" -> "bajar el estrés"
        "longevidad" -> "envejecer mejor"
        "salud" -> "estar más sano en general"
        else -> null
    }

    // ---- data phrasing ---------------------------------------------------------------------------

    private fun f0(v: Double) = v.roundToInt().toString()
    private fun f1(v: Double) = String.format(Locale("es", "ES"), "%.1f", v)
    private fun hm(min: Double): String {
        val m = min.roundToInt()
        return if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
    }

    private fun band(r: Double) = when {
        r >= 67 -> "verde"
        r >= 34 -> "amarilla"
        else -> "roja"
    }

    private fun pctDiff(v: Double?, avg: Double?): Double? =
        if (v == null || avg == null || avg == 0.0) null else (v - avg) / avg * 100.0

    private fun hrvPhrase(s: CoachSnapshot): String? {
        val hrv = s.hrv ?: return null
        val d = pctDiff(hrv, s.hrvAvg)
        return when {
            d == null -> "Tu VFC de anoche fue de ${f0(hrv)} ms."
            d >= 8 -> "Tu VFC (${f0(hrv)} ms) está un ${f0(d)} % por encima de tu media: buena señal."
            d <= -8 -> "Tu VFC (${f0(hrv)} ms) está un ${f0(abs(d))} % por debajo de tu media: tu cuerpo está gestionando algo."
            else -> "Tu VFC (${f0(hrv)} ms) está en tu rango normal."
        }
    }

    private fun rhrPhrase(s: CoachSnapshot): String? {
        val rhr = s.rhr ?: return null
        val avg = s.rhrAvg
        return when {
            avg == null -> "Tu pulso en reposo fue de ${f0(rhr)} lpm."
            rhr - avg >= 3 -> "Tu pulso en reposo (${f0(rhr)} lpm) está ${f0(rhr - avg)} lpm por encima de lo habitual."
            avg - rhr >= 3 -> "Tu pulso en reposo (${f0(rhr)} lpm) está ${f0(avg - rhr)} lpm por debajo de lo habitual: estás descansado."
            else -> "Tu pulso en reposo (${f0(rhr)} lpm) está en tu línea normal."
        }
    }

    private fun sleepPhrase(s: CoachSnapshot): String? {
        val min = s.sleepMin ?: return null
        val need = sleepNeedH * 60
        val gap = need - min
        val base = "Anoche dormiste ${hm(min)}"
        return when {
            gap > 60 -> "$base, ${hm(gap)} menos de lo que necesitas."
            gap > 15 -> "$base, algo por debajo de tus ${f1(sleepNeedH)} h."
            gap < -45 -> "$base, más de lo habitual: tu cuerpo lo pedía."
            else -> "$base, justo en lo que necesitas."
        }
    }

    private fun missingNights(s: CoachSnapshot, need: Int) = (need - s.nights).coerceAtLeast(1)

    private fun noRecoveryYet(s: CoachSnapshot) =
        if (s.nights < 4) "Aún estoy calibrando tu línea base: me faltan ${missingNights(s, 4)} noches con la pulsera para darte una recuperación fiable."
        else "Todavía no tengo la recuperación de hoy. Se calcula cuando la pulsera sincroniza la noche; abre la app con la pulsera cerca."

    private fun redFlags(s: CoachSnapshot): List<String> = buildList {
        val hd = pctDiff(s.hrv, s.hrvAvg)
        if (hd != null && hd <= -15) add("VFC baja")
        if (s.rhr != null && s.rhrAvg != null && s.rhr - s.rhrAvg >= 5) add("pulso en reposo alto")
        if (s.resp != null && s.respAvg != null && s.resp - s.respAvg >= 1.0) add("respiración más rápida")
        if (s.skinTempDev != null && abs(s.skinTempDev) < 5 && s.skinTempDev >= 0.5) add("temperatura de la piel más alta")
    }

    // ---- training recommendation --------------------------------------------------------------

    private fun effortTarget(r: Double): ClosedFloatingPointRange<Double> = when {
        r >= 67 -> 14.0..18.0
        r >= 34 -> 10.0..14.0
        else -> 0.0..10.0
    }

    private fun trainAdvice(s: CoachSnapshot): String {
        val r = s.recovery ?: return noRecoveryYet(s) + " Mientras tanto, guíate por sensaciones: si dormiste bien y no tienes agujetas fuertes, adelante con una sesión moderada."
        val t = effortTarget(r)
        val flags = redFlags(s)
        val sportTxt = sport?.let { " de $it" } ?: ""
        return when {
            flags.size >= 2 -> "Tu recuperación es del ${f0(r)} %, pero veo ${flags.joinToString(" y ")}. Eso a veces anticipa un resfriado o mucha fatiga. Hoy mejor algo suave (paseo, movilidad) y acuéstate pronto."
            r >= 67 -> pick(
                "Recuperación ${band(r)} (${f0(r)} %). Hoy es buen día para apretar: una sesión$sportTxt intensa o un entreno de calidad. Apunta a un esfuerzo de ${f0(t.start)}–${f0(t.endInclusive)}.",
                "Estás en verde con un ${f0(r)} %. Tu cuerpo está listo para cargar: series, fuerza pesada o una tirada larga. Objetivo de esfuerzo: ${f0(t.start)}–${f0(t.endInclusive)}.",
            )
            r >= 34 -> pick(
                "Recuperación ${band(r)} (${f0(r)} %). Puedes entrenar, pero sin ir al límite: técnica, zona 2 o fuerza moderada. Esfuerzo recomendado: ${f0(t.start)}–${f0(t.endInclusive)}.",
                "Un ${f0(r)} %: día de mantenimiento. Entrena con cabeza, deja 2–3 repeticiones en la recámara y prioriza dormir bien esta noche. Esfuerzo: ${f0(t.start)}–${f0(t.endInclusive)}.",
            )
            else -> pick(
                "Recuperación ${band(r)} (${f0(r)} %). Hoy toca recuperar: paseo, estiramientos o yoga suave. Un entreno duro hoy cuesta mucho y rinde poco. Mantén el esfuerzo por debajo de 10.",
                "Estás en rojo (${f0(r)} %). No es un fallo, es información: descansa activo, hidrátate y acuéstate antes. Mañana lo agradecerás.",
            )
        }
    }

    // ---- rules ---------------------------------------------------------------------------------

    private val rules: List<Rule> = listOf(
        Rule(
            "saludo",
            p("hola$", "buenos dias", "buenas tardes", "buenas noches", "que tal$", "saludos", "holi"),
            { c -> greeting(c.s) },
        ),
        Rule(
            "despedida",
            p("adios", "hasta luego", "hasta manana", "nos vemos", "chao", "bye", "me voy"),
            { _ -> CoachReply(pick("¡Hasta luego${hi()}! Aquí estaré cuando quieras repasar tus datos.", "Nos vemos${hi()}. Recuerda: lo que haces hoy se ve mañana en tu recuperación.", "¡Chao! Que descanses bien esta noche."), listOf("¿Cómo dormí anoche?")) },
        ),
        Rule(
            "gracias",
            p("gracias", "genial", "perfecto", "eres un crack", "muy util", "me sirve", "buenisimo"),
            { _ -> CoachReply(pick("¡A ti${hi()}! ¿Te ayudo con algo más?", "Para eso estoy. ¿Seguimos con algo?", "Me alegra que te sirva. ¿Quieres que miremos tu semana?"), listOf("Mi semana", "¿Entreno fuerte hoy?")) },
        ),
        Rule(
            "quien_eres",
            p("quien eres", "que eres", "que puedes hacer", "que sabes hacer", "como funcionas", "eres una ia", "eres un robot", "ayuda$", "que haces"),
            { _ ->
                CoachReply(
                    "Soy el coach de Pulse. No uso inteligencia artificial ni internet: funciono con reglas y con tus propios datos de la pulsera, todo dentro del teléfono.\n\n" +
                        "Puedo ayudarte con:\n• Cómo has dormido y cuánto necesitas\n• Si te conviene entrenar fuerte o suave hoy\n• Qué significa tu VFC, tu pulso en reposo o tu estrés\n• Consejos de sueño, estrés, nutrición e hidratación\n• Tu semana y tu edad fisiológica\n\nTambién me acuerdo de tu nombre y tu objetivo si me los dices.",
                    listOf("¿Cómo dormí anoche?", "¿Entreno fuerte hoy?", "Mi objetivo es dormir mejor"),
                )
            },
        ),
        Rule(
            "resumen_hoy",
            p("como estoy", "que tal estoy", "resumen", "como voy", "mi dia", "como me ves", "estado de hoy", "como ando", "dame un resumen", "informe") +
                p("como esta mi cuerpo", "estoy preparado", w = 1.2),
            { c -> todaySummary(c.s) },
        ),
        Rule(
            "recuperacion",
            p("recuper", "bateria corporal", "cuanto me he recuperado", "estoy recuperado", "como me recupere") + p("mi recuperacion", w = 1.5),
            { c -> recoveryAnswer(c.s) },
            why = { c -> recoveryWhy(c.s) },
            more = { _ -> recoveryTips() },
        ),
        Rule(
            "recuperacion_mejorar",
            p("mejorar recuper", "recuperarme mejor", "subir recuper", "recuperar mas rapido", "acelerar recuper", "consejos recuper", w = 2.0),
            { _ -> recoveryTips() },
        ),
        Rule(
            "sueno_anoche",
            p("dormi", "como he dormido", "mi sueno", "anoche", "rendimiento del sueno", "descanse", "he descansado", "como dormi", "dormido") +
                p("como dormi anoche", "mi sueno", w = 2.0),
            { c -> sleepAnswer(c.s) },
            why = { c -> sleepWhy(c.s) },
            more = { c -> sleepTips(c.s) },
        ),
        Rule(
            "sueno_consejos",
            p("consejo dorm", "dormir mejor", "mejorar sueno", "mejorar el sueno", "higiene del sueno", "tips dormir", "como duermo mejor", "dormir bien", w = 2.0),
            { c -> sleepTips(c.s) },
            more = { _ -> sleepTipsExtra() },
        ),
        Rule(
            "insomnio",
            p("insomnio", "no puedo dormir", "no consigo dormir", "me cuesta dormir", "no me duermo", "desvelado", "dando vueltas", "no duermo", w = 2.0),
            { c -> insomnia(c.s) },
        ),
        Rule(
            "despertares",
            p("me despierto", "despertares", "me desvelo", "despierto de madrugada", "despierto por la noche", "me levanto a mear", w = 2.0),
            { c -> wakeUps(c.s) },
        ),
        Rule(
            "necesidad_sueno",
            p("cuanto dormir", "cuantas horas dormir", "cuanto necesito dormir", "horas necesito", "deuda de sueno", "cuantas horas debo", w = 2.0),
            { c -> sleepNeed(c.s) },
        ),
        Rule(
            "hora_acostarse",
            p("a que hora me acuesto", "hora de dormir", "cuando acostarme", "a que hora dormir", "cuando me acuesto", "a que hora me voy a la cama", w = 2.0),
            { c -> bedtime(c.s) },
        ),
        Rule(
            "siesta",
            p("siesta", "echarme un rato", "dormir por la tarde", "power nap", w = 2.0),
            { c -> nap(c.s) },
        ),
        Rule(
            "regularidad",
            p("regularidad", "horario regular", "mismo horario", "fines de semana duermo", "jet lag social", "consistencia", w = 1.5),
            { _ -> CoachReply("La regularidad pesa casi tanto como las horas. Acostarte y levantarte a la misma hora (±30 min), también el fin de semana, estabiliza tu reloj interno: te duermes antes, tienes más sueño profundo y la VFC mejora.\n\nTruco: fija primero la hora de levantarte y deja que la de acostarte se ajuste sola en una o dos semanas.", listOf("¿A qué hora me acuesto?", "Configurar horario"), CoachAction.Alarms) },
        ),
        Rule(
            "cafe",
            p("cafe", "cafeina", "infusion", "energetica", "monster", "red bull", "coca cola", w = 1.5),
            { c -> caffeine(c) },
        ),
        Rule(
            "alcohol",
            p("alcohol", "cerveza", "vino", "copas", "beber", "resaca", "borracho", "fiesta", "salir de fiesta", w = 1.5),
            { c -> alcohol(c.s) },
        ),
        Rule(
            "pantallas",
            p("movil antes de dormir", "pantallas", "luz azul", "netflix", "series por la noche", "redes sociales noche", w = 1.5),
            { _ -> CoachReply("Lo que más daña no es tanto la luz azul como la activación: notificaciones, contenido que engancha y quedarte \"solo un capítulo más\". Prueba esto 45 minutos antes de dormir: móvil fuera del dormitorio o en modo no molestar, luz cálida y tenue, y algo aburrido (leer en papel, estirar, ducharte).", listOf("Consejos para dormir mejor")) },
        ),
        Rule(
            "entrenar_hoy",
            p("entreno hoy", "entrenar hoy", "puedo entrenar", "deberia entrenar", "entreno fuerte", "apretar hoy", "dar cana", "dia de descanso", "descanso hoy", "salgo a correr", "voy al gimnasio", "hago deporte hoy", "que intensidad") +
                p("entreno fuerte hoy", w = 2.5),
            { c -> CoachReply(trainAdvice(c.s), listOf("¿Qué entreno me recomiendas?", "¿Por qué?"), CoachAction.Live, "entrenar_hoy") },
            why = { c -> recoveryWhy(c.s) },
        ),
        Rule(
            "que_entreno",
            p("que entreno", "que entrenamiento", "rutina", "plan de entreno", "que hago hoy", "ejercicios", "sesion", "workout", w = 1.3),
            { c -> workoutSuggestion(c.s) },
            more = { c -> workoutSuggestion(c.s) },
        ),
        Rule(
            "zona2",
            p("zona 2", "zona dos", "cardio suave", "cardio", "aerobico", "rodaje", "base aerobica", w = 1.5),
            { c -> zone2(c.s) },
        ),
        Rule(
            "hiit",
            p("hiit", "series", "intervalos", "tabata", "sprint", "alta intensidad", "zona 4", "zona 5", w = 1.5),
            { c -> hiit(c.s) },
        ),
        Rule(
            "fuerza",
            p("fuerza", "pesas", "musculacion", "sentadilla", "press", "peso muerto", "hipertrofia", "gimnasio", w = 1.2),
            { c -> strength(c.s) },
        ),
        Rule(
            "correr",
            p("correr", "running", "carrera", "maraton", "media maraton", "10k", "5k", "trail", "ritmo", w = 1.2),
            { c -> running(c) },
        ),
        Rule(
            "agujetas",
            p("agujetas", "dolor muscular", "cargado", "piernas cansadas", "me duelen las piernas", "doms", w = 2.0),
            { _ -> CoachReply("Las agujetas son normales 24–72 h después de algo nuevo o intenso. Lo que mejor funciona: movimiento suave (caminar, bici fácil), dormir bien, proteína suficiente y paciencia. Estirar fuerte o entrenar duro el mismo grupo muscular no acelera nada.\n\nSi el dolor es en una articulación, punzante o no mejora en unos días, eso ya no son agujetas: consúltalo con un fisioterapeuta.", listOf("¿Entreno fuerte hoy?")) },
        ),
        Rule(
            "lesion",
            p("lesion", "me he lesionado", "esguince", "me duele la rodilla", "me duele el tobillo", "me duele la espalda", "tendinitis", "dolor articular", "me he torcido", "rotura", w = 2.5),
            { _ -> CoachReply("Lo siento. Con una lesión no te voy a dar pautas de entrenamiento: lo correcto es que la valore un médico o fisioterapeuta.\n\nMientras tanto: no fuerces la zona, y si hay hinchazón, dolor intenso, no puedes apoyar o se deforma, acude a urgencias. Puedes seguir moviendo lo que no duela y cuidar el sueño, que es cuando más se repara el tejido.", listOf("Consejos para dormir mejor")) },
        ),
        Rule(
            "sobreentrenamiento",
            p("sobreentren", "estoy reventado", "agotado", "fatiga acumulada", "no rindo", "estancado", "cansado siempre", "sin fuerzas", w = 1.5),
            { c -> fatigue(c.s) },
        ),
        Rule(
            "esfuerzo",
            p("esfuerzo", "strain", "carga", "cuanto me he movido", "cuanto he entrenado", "mi esfuerzo") + p("esfuerzo de hoy", w = 1.5),
            { c -> effortAnswer(c.s) },
            why = { _ -> "El esfuerzo mide la carga cardiovascular del día en una escala de 0 a 21: cuanto más tiempo pasas con el pulso alto respecto a tu máximo, más sube. Por encima de 14 es un día exigente; por encima de 18, muy exigente." },
        ),
        Rule(
            "vfc",
            p("vfc", "variabilidad", "hrv", "variabilidad cardiaca", "mi vfc") + p("vfc baja", "por que baja mi vfc", w = 1.5),
            { c -> hrvAnswer(c.s) },
            why = { _ -> "La VFC refleja el equilibrio de tu sistema nervioso: cuando estás descansado domina el modo \"reposo\" y el tiempo entre latidos varía más. Estrés, alcohol, poco sueño, enfermedad o entreno duro la bajan." },
        ),
        Rule(
            "fcr",
            p("pulso en reposo", "fcr", "frecuencia cardiaca en reposo", "pulsaciones en reposo", "pulso alto", "pulsaciones altas", "frecuencia cardiaca", w = 1.5),
            { c -> rhrAnswer(c.s) },
        ),
        Rule(
            "respiracion_dato",
            p("frecuencia respiratoria", "respiraciones por minuto", "respiracion nocturna", w = 2.0),
            { c ->
                val r = c.s.resp
                CoachReply(
                    if (r == null) "Aún no tengo tu frecuencia respiratoria nocturna. Aparece tras unas noches con la pulsera."
                    else "Anoche respiraste a ${f1(r)} respiraciones por minuto${c.s.respAvg?.let { " (tu media: ${f1(it)})" } ?: ""}. Es una de las métricas más estables: si sube más de 1 respiración/min sobre tu media sin motivo, suele anticipar un resfriado o mucha fatiga.",
                    listOf("¿Cómo estoy hoy?"),
                )
            },
        ),
        Rule(
            "oxigeno",
            p("oxigeno", "saturacion", "spo2", w = 2.0),
            { c ->
                CoachReply(
                    "La pulsera estima la saturación de oxígeno durante el sueño. Es orientativa, no un pulsioxímetro médico.\n\n" +
                        "Si alguna vez notas falta de aire, labios azulados o confusión, no mires la app: llama al 112.",
                    listOf("Monitor de salud"),
                )
            },
        ),
        Rule(
            "temperatura",
            p("temperatura", "fiebre la pulsera", "temperatura piel", w = 1.5),
            { c ->
                val t = c.s.skinTempDev?.takeIf { abs(it) < 5 }
                CoachReply(
                    if (t == null) "Todavía no tengo tu desviación de temperatura de la piel. Necesito unas noches para conocer tu valor normal."
                    else "Anoche tu piel estuvo ${f1(abs(t))} °C ${if (t >= 0) "por encima" else "por debajo"} de tu normal. Variaciones de ±0,5 °C son habituales; subidas mayores junto a VFC baja pueden indicar que estás incubando algo (o, en mujeres, la fase del ciclo).",
                    listOf("¿Cómo estoy hoy?"),
                )
            },
        ),
        Rule(
            "estres",
            p("estres", "estresado", "agobiado", "nervioso", "tension", "presion", "nivel de estres", "sobrecargado", "burnout", w = 1.5),
            { c -> stressAnswer(c.s) },
            more = { _ -> breathing() },
        ),
        Rule(
            "respirar",
            p("respiracion", "respirar", "ejercicio de respiracion", "calmarme", "relajarme", "tranquilizarme", "respiracion cuadrada", w = 1.8),
            { _ -> breathing() },
            more = { _ -> breathing() },
        ),
        Rule(
            "ansiedad",
            p("ansiedad", "ataque de panico", "panico", "angustia", "me agobio", w = 2.0),
            { _ ->
                CoachReply(
                    "Siento que estés así. Vamos a bajar revoluciones juntos:\n\n1. Apoya los pies en el suelo y nota el contacto.\n2. Inspira por la nariz 4 segundos.\n3. Exhala despacio por la boca 6–8 segundos, como soplando una vela sin apagarla.\n4. Repite 6 veces. La exhalación larga activa el freno de tu corazón.\n\nSi la ansiedad es frecuente o te limita, hablar con un profesional de salud mental ayuda de verdad. Y si en algún momento sientes que no puedes más, llama al 024 (gratuito, 24 h).",
                    listOf("Otro ejercicio de respiración", "¿Cómo está mi estrés?"),
                    CoachAction.Stress,
                )
            },
        ),
        Rule(
            "meditacion",
            p("meditar", "meditacion", "mindfulness", "atencion plena", w = 1.8),
            { _ -> CoachReply("Para empezar no necesitas nada especial: 5 minutos al día, sentado, atento a la respiración. Cuando la mente se vaya (se irá), vuelve sin juzgarte. Eso es la práctica.\n\nEl mejor momento suele ser después de despertar o antes de dormir. Si lo mantienes 2–3 semanas, mira si tu VFC nocturna sube.", listOf("Ejercicio de respiración")) },
        ),
        Rule(
            "nutricion",
            p("comer", "comida", "dieta", "nutricion", "alimentacion", "que como", "cenar", "cena", "desayuno", "desayunar", "carbohidratos", "azucar", w = 1.2),
            { c -> nutrition(c) },
        ),
        Rule(
            "proteina",
            p("proteina", "proteinas", "batido", "whey", "cuanta proteina", w = 2.0),
            { _ -> CoachReply("Una referencia útil para gente activa: 1,6–2,2 g de proteína por kilo de peso al día, repartida en 3–4 tomas de 25–40 g. Huevos, lácteos, legumbres, pescado, carne o tofu. El batido es comodidad, no magia.\n\nSi tienes alguna enfermedad renal, consúltalo antes con tu médico.", listOf("Quiero ganar músculo")) },
        ),
        Rule(
            "hidratacion",
            p("agua", "hidrat", "beber agua", "cuanta agua", "deshidrat", "sed", w = 1.5),
            { c ->
                val extra = c.s.effort?.takeIf { it >= 12 }?.let { " Hoy has hecho un esfuerzo de ${f1(it)}, así que suma 0,5–1 litro más y algo de sal si has sudado mucho." } ?: ""
                CoachReply("Como regla sencilla: bebe lo suficiente para que tu orina sea de color amarillo claro. Para la mayoría son 1,5–2,5 litros al día entre agua y alimentos.$extra\n\nEvita beber mucho en la última hora antes de dormir para no despertarte.", listOf("¿Cómo dormí anoche?"))
            },
        ),
        Rule(
            "perder_peso",
            p("perder peso", "adelgazar", "bajar peso", "perder grasa", "quemar grasa", "definir", "kilos de mas", "deficit", w = 2.0),
            { c -> loseWeight(c.s) },
        ),
        Rule(
            "ganar_musculo",
            p("ganar musculo", "ganar masa", "volumen", "crecer", "musculo", "masa muscular", "hipertrofia", w = 1.8),
            { c -> gainMuscle(c.s) },
        ),
        Rule(
            "pasos",
            p("pasos", "caminar", "andar", "paseo", "10000 pasos", "diez mil pasos", w = 1.3),
            { c -> stepsAnswer(c.s) },
        ),
        Rule(
            "calorias",
            p("calorias", "kcal", "gasto calorico", "cuanto he quemado", "gastado", w = 1.5),
            { c ->
                val k = c.s.kcal
                CoachReply(
                    if (k == null) "Todavía no tengo tu gasto de hoy. Se estima a partir de tu pulso a lo largo del día."
                    else "Hoy llevas unas ${f0(k)} kcal estimadas a partir de tu pulso. Tómalo como orientación (±15 %): sirve más para comparar días entre sí que como cifra exacta.",
                    listOf("Quiero perder peso", "Mi semana"), CoachAction.Effort,
                )
            },
        ),
        Rule(
            "vo2",
            p("vo2", "vo2max", "capacidad aerobica", "forma fisica", "fondo fisico", w = 1.8),
            { c -> CoachReply("El VO₂ máx. es la cantidad de oxígeno que tu cuerpo puede usar a tope, y es el mejor predictor de longevidad que existe. Se mejora con dos cosas: mucho volumen suave (zona 2, 2–4 h/semana) y un poco de intensidad alta (1–2 sesiones de intervalos de 3–4 minutos).\n\nEn tu edad fisiológica es el factor que más años puede restar (hasta 6).", listOf("¿Cómo hago zona 2?", "Mi edad fisiológica"), CoachAction.Age) },
        ),
        Rule(
            "edad_fisiologica",
            p("edad fisiologica", "edad biologica", "envejecimiento", "envejecer", "cuantos anos tengo", "longevidad", "vivir mas", "ritmo de envejecimiento", w = 2.0),
            { c -> ageAnswer(c.s) },
        ),
        Rule(
            "enfermo",
            p("enfermo", "resfriado", "constipado", "gripe", "fiebre", "estoy malo", "estoy mala", "mocos", "dolor de garganta", "covid", "me encuentro mal", "virus", "tos", w = 2.0),
            { c -> illness(c.s) },
        ),
        Rule(
            "viaje",
            p("viaje", "jet lag", "jetlag", "cambio de hora", "vuelo", "zona horaria", "viajar", w = 1.8),
            { _ -> CoachReply("Para el jet lag lo que manda es la luz:\n• Viajando hacia el este: busca luz por la mañana en destino y evítala por la noche.\n• Hacia el oeste: luz por la tarde, y aguanta despierto hasta una hora razonable.\n• Ajusta comidas al horario local desde el primer día.\n• Siestas cortas (máx. 20 min) y nada de cafeína después de las 14 h locales.\n\nCuenta con 1 día de ajuste por cada zona horaria; tu VFC lo reflejará.", listOf("¿Puedo echarme una siesta?")) },
        ),
        Rule(
            "ciclo",
            p("regla", "menstruacion", "periodo", "ciclo menstrual", "ovulacion", "sindrome premenstrual", "fase lutea", w = 2.0),
            { _ -> CoachReply("Durante la fase lútea (la semana o dos antes de la regla) es normal que suba algo el pulso en reposo y la temperatura y baje un poco la VFC. No es que estés peor recuperada: es fisiología.\n\nEn esos días ajusta expectativas en entrenos muy intensos, cuida el sueño y la hidratación. Si registras tu ciclo en Ajustes, lo verás reflejado en tus tendencias.", listOf("¿Entreno fuerte hoy?")) },
        ),
        Rule(
            "motivacion",
            p("motivacion", "no tengo ganas", "pereza", "desmotivado", "me cuesta empezar", "no me apetece", "animame", "dame animos", w = 2.0),
            { c -> motivation(c.s) },
        ),
        Rule(
            "semana",
            p("mi semana", "esta semana", "semana", "ultimos dias", "resumen semanal", "como ha ido la semana", w = 1.5),
            { c -> weekSummary(c.s) },
        ),
        Rule(
            "tendencias",
            p("tendencia", "evolucion", "progreso", "estoy mejorando", "voy a mejor", "ultimo mes$", w = 1.2),
            { c -> CoachReply(weekSummary(c.s).text + "\n\nPara ver la evolución a 90 días abre Tendencias en la pestaña Salud.", listOf("Mi edad fisiológica"), CoachAction.Trends) },
        ),
        Rule(
            "objetivo_consulta",
            p("mi objetivo", "cual es mi objetivo", "objetivos", "que objetivo", "meta", w = 1.2),
            { _ ->
                val g = goalLabel(goal)
                if (g == null) {
                    memory.set("pending", "goal")
                    CoachReply("Aún no me has dicho tu objetivo. ¿Qué quieres mejorar principalmente?", listOf("Dormir mejor", "Rendir más", "Perder grasa", "Ganar músculo", "Bajar el estrés", "Envejecer mejor"))
                } else CoachReply("Tu objetivo es $g. Si quieres cambiarlo, dime por ejemplo \"mi objetivo es bajar el estrés\".", listOf("Dame un plan para mi objetivo"))
            },
        ),
        Rule(
            "plan",
            p("plan", "dame un plan", "plan para mi objetivo", "plan semanal", "que hago esta semana", "como mejoro", w = 1.5),
            { c -> plan(c.s) },
        ),
        Rule(
            "calibracion",
            p("calibr", "cuando tendre datos", "por que no tengo datos", "no veo datos", "sin datos", "linea base", "cuantas noches", w = 1.5),
            { c -> CoachReply("Llevas ${c.s.nights} ${if (c.s.nights == 1) "noche" else "noches"} con datos. Así funciona la calibración:\n• 1 noche: sueño y esfuerzo.\n• 4 noches: primera recuperación.\n• 7 noches: línea base completa y monitor de salud.\n• 21 días: edad fisiológica.\n\nDuerme con la pulsera puesta y abre la app por la mañana para que sincronice.", listOf("¿Cómo dormí anoche?"), CoachAction.Calibration) },
        ),
        Rule(
            "pulsera",
            p("pulsera", "conectar", "emparejar", "bluetooth", "no conecta", "sincroniz", "no sincroniza", "bateria de la pulsera", "cargar la pulsera", "se ha desconectado", w = 1.5),
            { c ->
                val b = c.s.batteryPct?.let { "La batería está al ${f0(it)} %. " } ?: ""
                val conn = if (c.s.connected) "Ahora mismo está conectada. " else "Ahora mismo no está conectada. "
                CoachReply("$conn$b\nSi no sincroniza: activa el Bluetooth, acerca la pulsera al teléfono, abre Pulse y espera un minuto. Si sigue igual, en Ajustes → Pulsera puedes volver a emparejarla. Cárgala cuando baje del 20 % para no perder la noche.", listOf("¿Cómo dormí anoche?"), CoachAction.Strap)
            },
        ),
        Rule(
            "datos_privacidad",
            p("privacidad", "mis datos", "donde se guardan", "nube", "servidor", "quien ve mis datos", "seguridad de datos", w = 1.5),
            { _ -> CoachReply("Tus datos no salen del teléfono: Pulse no tiene cuenta ni servidor, y yo no uso internet. Para no perderlos, haz una copia de seguridad (.noopbak) y guárdala donde quieras.", listOf("Copia de seguridad"), CoachAction.Backup) },
        ),
        Rule(
            "importar",
            p("importar", "exportar", "copia de seguridad", "backup", "health connect", "pasar datos", "datos antiguos", w = 1.5),
            { _ -> CoachReply("En Ajustes → Importar datos puedes traer tu historial de otra app, Health Connect o archivos CSV. Y en Copia y restauración guardas todo Pulse en un archivo .noopbak para cambiar de móvil.", listOf("Mis datos están seguros?"), CoachAction.Import) },
        ),
        Rule(
            "alarma",
            p("alarma", "despertador", "despertarme", "vibracion", "horario de sueno", "despertar suave", w = 1.5),
            { _ -> CoachReply("La pulsera puede despertarte con vibración, y Pulse puede avisarte para desconectar antes de dormir. Se configura en Horario de sueño: elige la hora de levantarte y los días.", listOf("¿A qué hora me acuesto?"), CoachAction.Alarms) },
        ),
        Rule(
            "diario",
            p("diario", "habitos", "apuntar", "registrar habitos", "que me afecta", "que influye", w = 1.5),
            { _ -> CoachReply("El diario sirve para descubrir qué te sienta bien y qué no. Marca cada mañana lo que hiciste el día anterior (alcohol, cena tarde, estrés, meditación…). Tras unas semanas Pulse compara tu recuperación con y sin cada hábito.", listOf("¿Cómo estoy hoy?"), CoachAction.Journal) },
        ),
        Rule(
            "animo_bajo",
            p("estoy triste", "triste", "deprimido", "deprimida", "bajon", "me siento mal", "me siento solo", "me siento sola", "sin ganas de nada", "lloro", w = 2.0),
            { _ -> CoachReply("Siento que estés pasando por esto. No soy un profesional, pero sí te digo que no tienes que llevarlo solo/a: hablar con alguien de confianza o con tu médico de cabecera ayuda.\n\nCosas pequeñas que suelen sumar: salir a la luz del día por la mañana, caminar 20 minutos y mantener horarios de sueño regulares.\n\nSi en algún momento piensas en hacerte daño, llama al 024 (gratuito, 24 h) o al 112.", listOf("Ejercicio de respiración")) },
        ),
        Rule(
            "chiste",
            p("chiste", "cuentame algo", "aburrido", "me aburro", "di algo gracioso", w = 1.5),
            { _ -> CoachReply(pick("¿Sabes por qué el corazón nunca se aburre? Porque siempre tiene algo que latir. 🫀 Vale, mejor te ayudo con tus datos.", "Mi deporte favorito es el levantamiento de datos. ¿Miramos los tuyos?", "Dormir es el único entreno que puedes hacer con los ojos cerrados y aun así mejorar. ¿Te doy consejos para dormir?"), listOf("Consejos para dormir mejor", "¿Cómo estoy hoy?")) },
        ),
    )

    // ---- public API ------------------------------------------------------------------------------

    fun welcome(s: CoachSnapshot): CoachReply {
        val intro = "Hola${hi()}. Leo tu sueño, tu recuperación y tus entrenos para explicarte qué significan y ayudarte a mejorar día a día. Todo funciona sin internet y tus datos no salen del teléfono."
        val ask = if (goal == null) "\n\nPara empezar, ¿qué te gustaría mejorar?" else ""
        if (goal == null) memory.set("pending", "goal")
        return CoachReply(
            intro + ask,
            if (goal == null) listOf("Dormir mejor", "Rendir más", "Bajar el estrés", "Perder grasa") else listOf("¿Cómo dormí anoche?", "¿Entreno fuerte hoy?", "Mi semana"),
            intent = "bienvenida",
        )
    }

    fun reply(message: String, s: CoachSnapshot): CoachReply {
        val norm = CoachText.normalize(message)
        val c = Ctx(message, norm, CoachText.tokens(norm), s)
        if (c.tokens.isEmpty()) return CoachReply("¿Me cuentas algo? Puedes preguntarme por tu sueño, tu recuperación o si te conviene entrenar.", listOf("¿Cómo estoy hoy?"), intent = "vacio")

        safety(c)?.let { return remember(it) }
        pending(c)?.let { return remember(it) }
        statements(c)?.let { return remember(it) }
        followUp(c)?.let { return remember(it) }

        val best = rules
            .map { r -> r to r.phrases.filter { (ph, _) -> CoachText.phraseMatches(ph, c.tokens) }.sumOf { (ph, w) -> w * (1 + 0.5 * (ph.count { it == ' ' })) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
        if (best != null) {
            val r = best.first.answer(c)
            return remember(r.copy(intent = r.intent.ifEmpty { best.first.id }))
        }
        return remember(fallback(c))
    }

    private fun remember(r: CoachReply): CoachReply {
        if (r.intent.isNotEmpty() && r.intent !in setOf("porque", "mas", "vacio")) memory.set("last_intent", r.intent)
        return r
    }

    // ---- safety ----------------------------------------------------------------------------------

    private fun safety(c: Ctx): CoachReply? {
        val selfHarm = listOf("suicid", "quitarme la vida", "no quiero vivir", "matarme", "hacerme dano", "autoles", "acabar con todo", "no quiero seguir viviendo", "mejor muerto", "mejor muerta")
        if (selfHarm.any { CoachText.phraseMatches(it, c.tokens) }) {
            return CoachReply(
                "Gracias por contármelo. Lo que sientes importa y no tienes que pasarlo solo/a.\n\nPor favor, habla ahora con alguien que pueda ayudarte:\n• Línea 024 de atención a la conducta suicida: gratuita, 24 h, en España.\n• Si estás en peligro inmediato: 112.\n• Fuera de España, llama al número de emergencias local.\n\nSi puedes, avisa también a una persona de confianza para que esté contigo.",
                emptyList(), intent = "seguridad_crisis",
            )
        }
        val emergency = listOf(
            "dolor en el pecho", "dolor de pecho", "me duele el pecho", "opresion en el pecho", "no puedo respirar", "me ahogo", "me desmayo", "me he desmayado",
            "perdida de conocimiento", "palpitaciones fuertes", "corazon muy rapido", "brazo dormido", "cara torcida", "no puedo hablar", "infarto", "ictus",
        )
        if (emergency.any { CoachText.phraseMatches(it, c.tokens) }) {
            return CoachReply(
                "Esto puede ser una urgencia médica. Deja la app y llama al 112 ahora (o al número de emergencias de tu país). No conduzcas tú; si estás con alguien, avísale.\n\nPulse no puede diagnosticar ni descartar problemas del corazón.",
                emptyList(), intent = "seguridad_urgencia",
            )
        }
        return null
    }

    // ---- conversation state ----------------------------------------------------------------------

    private fun parseGoal(c: Ctx): String? = when {
        c.has("dorm", "sueno", "descans") -> "dormir"
        c.has("rend", "rendimiento", "competi", "marca", "mas rapido", "entrenar mejor") -> "rendimiento"
        c.has("perder", "adelgaz", "grasa", "peso", "definir") -> "peso"
        c.has("musculo", "masa", "fuerte", "fuerza", "hipertrof", "volumen") -> "musculo"
        c.has("estres", "ansiedad", "relaj", "calma", "tranquil") -> "estres"
        c.has("envejec", "longev", "edad", "vivir mas") -> "longevidad"
        c.has("salud", "sano", "general", "todo") -> "salud"
        else -> null
    }

    private fun pending(c: Ctx): CoachReply? {
        val p = memory.get("pending") ?: return null
        when (p) {
            "goal" -> {
                val isQuestion = c.has("como", "cuanto", "que tal", "por que", "deberia", "puedo")
                val g = (if (isQuestion) null else parseGoal(c)) ?: return null.also { memory.set("pending", null) }
                memory.set("goal", g)
                memory.set("pending", "sport")
                return CoachReply(
                    "Apuntado: tu objetivo es ${goalLabel(g)}. Adaptaré mis consejos a eso.\n\n¿Practicas algún deporte en concreto? (por ejemplo: correr, gimnasio, ciclismo, fútbol, natación… o \"ninguno\")",
                    listOf("Correr", "Gimnasio", "Ciclismo", "Ninguno"), intent = "objetivo_guardado",
                )
            }
            "sport" -> {
                memory.set("pending", null)
                val sp = detectSport(c)
                if (sp == null && !c.has("ninguno", "nada", "no$")) return null
                memory.set("sport", sp)
                memory.set("pending", "sleep_need")
                val txt = if (sp == null) "Sin problema, también te ayudo a moverte más en el día a día." else "Perfecto, tendré en cuenta que haces $sp."
                return CoachReply("$txt\n\nÚltima pregunta: ¿cuántas horas sueles dormir entre semana?", listOf("6 horas", "7 horas", "8 horas"), intent = "deporte_guardado")
            }
            "sleep_need" -> {
                memory.set("pending", null)
                val h = c.number?.takeIf { it in 3.0..12.0 } ?: return null
                memory.set("usual_sleep", h.toString())
                val msg = when {
                    h < 6.5 -> "Duermes unas ${f1(h)} h. Es poco para casi todo el mundo: por debajo de 7 h la recuperación, el apetito y el ánimo se resienten. Intentar sumar 30 minutos sería el cambio con más retorno para ti."
                    h < 7.5 -> "Unas ${f1(h)} h: estás cerca. Muchas personas rinden mejor con 7,5–8,5 h, sobre todo si entrenan. Probaremos a ver qué dice tu recuperación."
                    else -> "${f1(h)} h: buena base. Ahora la clave es la regularidad y la calidad."
                }
                return CoachReply("$msg\n\nYa estamos. Pregúntame lo que quieras cuando quieras.", listOf("¿Cómo dormí anoche?", "¿Entreno fuerte hoy?", "Dame un plan"), intent = "sueno_habitual")
            }
            "coffee_count" -> {
                memory.set("pending", null)
                val n = c.number ?: return null
                memory.set("coffees", n.toString())
                return CoachReply(
                    when {
                        n == 0.0 -> "Sin café, perfecto para el sueño. Si alguna vez lo necesitas, tómalo antes del mediodía."
                        n <= 2 -> "${f0(n)} al día está bien para la mayoría. La clave es la hora: el último, antes de las 14 h. La cafeína tarda 5–6 horas en reducirse a la mitad."
                        else -> "${f0(n)} al día es bastante. Prueba a bajar uno cada semana y a no tomar ninguno después de las 14 h; muchas personas notan que se duermen antes y la VFC sube."
                    },
                    listOf("Consejos para dormir mejor"), intent = "cafe_guardado",
                )
            }
            "drinks" -> {
                memory.set("pending", null)
                val n = c.number ?: return null
                memory.set("drinks_week", n.toString())
                return CoachReply(
                    if (n <= 1) "Genial, el alcohol apenas afectará a tus noches." else "${f0(n)} a la semana. Prueba a fijarte en tu recuperación las mañanas siguientes: ese dato convence más que cualquier consejo mío.",
                    listOf("Mi semana"), intent = "alcohol_guardado",
                )
            }
        }
        memory.set("pending", null)
        return null
    }

    private fun detectSport(c: Ctx): String? = when {
        c.has("corr", "running", "trail", "maraton") -> "running"
        c.has("gimnas", "pesas", "fuerza", "crossfit", "musculac") -> "gimnasio"
        c.has("bici", "ciclis", "spinning", "mtb") -> "ciclismo"
        c.has("futbol", "futbito", "padel", "tenis", "basket", "balonc") -> c.tokens.firstOrNull { it.startsWith("fut") || it.startsWith("pad") || it.startsWith("ten") || it.startsWith("bas") || it.startsWith("bal") }?.let { if (it.startsWith("pad")) "pádel" else if (it.startsWith("fut")) "fútbol" else it } ?: "deporte de equipo"
        c.has("nada", "nadar", "natacion", "piscina") && !c.has("nada$") -> "natación"
        c.has("yoga", "pilates") -> "yoga/pilates"
        c.has("camin", "andar", "senderis", "montana") -> "senderismo"
        c.has("artes marciales", "boxeo", "judo", "karate", "bjj") -> "deportes de contacto"
        else -> null
    }

    /** Declarative statements the user makes about themselves. */
    private fun statements(c: Ctx): CoachReply? {
        CoachText.extractName(c.original)?.let { n ->
            if (c.has("llam", "nombre")) {
                memory.set("name", n)
                return CoachReply("Encantado, $n. Me acordaré de tu nombre. ¿En qué te ayudo?", listOf("¿Cómo estoy hoy?", "¿Entreno fuerte hoy?"), intent = "nombre")
            }
        }
        val declaresGoal = c.has("objetivo", "mi meta") ||
            (c.has("quiero", "me gustaria") && c.has("mejor", "mejorar", "perder", "ganar", "bajar", "envejecer", "reducir"))
        if (declaresGoal && !c.has("cual", "que es")) {
            val g = parseGoal(c)
            if (g != null) {
                memory.set("goal", g)
                return CoachReply("Hecho: tu objetivo es ${goalLabel(g)}. A partir de ahora mis consejos irán enfocados a eso.", listOf("Dame un plan", "¿Cómo estoy hoy?"), intent = "objetivo_guardado")
            }
        }
        if (c.has("hago", "practico", "juego", "entreno") && detectSport(c) != null && c.tokens.size <= 8 && !c.has("hoy", "que")) {
            val sp = detectSport(c)!!
            memory.set("sport", sp)
            return CoachReply("Apuntado: haces $sp. Lo tendré en cuenta al recomendarte entrenos.", listOf("¿Qué entreno me recomiendas?"), intent = "deporte_guardado")
        }
        if (c.has("necesito") && c.has("horas") && c.has("dorm")) {
            c.number?.takeIf { it in 5.0..11.0 }?.let {
                memory.set("sleep_need", it.toString())
                return CoachReply("Vale, tomaré ${f1(it)} h como tu necesidad de sueño para calcular la deuda.", listOf("¿Cómo dormí anoche?"), intent = "necesidad_guardada")
            }
        }
        if (c.has("levanto", "despierto", "despertarme", "levantarme") && c.tokens.contains("las") && c.number != null) {
            val n = c.number!!
            val h = n.toInt().coerceIn(0, 23)
            val m = ((n - h) * 60).roundToInt().coerceIn(0, 59)
            memory.set("wake_time", "%02d:%02d".format(h, m))
            return bedtime(c.s)
        }
        if (c.has("olvida", "borra") && c.has("todo", "datos", "memoria", "lo que sabes")) {
            listOf("name", "goal", "sport", "sleep_need", "usual_sleep", "coffees", "drinks_week", "pending", "last_intent", "wake_time").forEach { memory.set(it, null) }
            return CoachReply("Hecho, he olvidado lo que me habías contado. Tus datos de la pulsera no se tocan.", listOf("Empezar de nuevo"), intent = "olvidar")
        }
        if (c.has("que sabes de mi", "que recuerdas")) return memorySummary()
        return null
    }

    private fun memorySummary(): CoachReply {
        val items = buildList {
            name?.let { add("Te llamas $it.") }
            goalLabel(goal)?.let { add("Tu objetivo es $it.") }
            sport?.let { add("Haces $it.") }
            memory.get("usual_sleep")?.toDoubleOrNull()?.let { add("Sueles dormir unas ${f1(it)} h.") }
            memory.get("sleep_need")?.toDoubleOrNull()?.let { add("Necesitas unas ${f1(it)} h de sueño.") }
            memory.get("coffees")?.toDoubleOrNull()?.let { add("Tomas unos ${f0(it)} cafés al día.") }
        }
        return CoachReply(
            if (items.isEmpty()) "Todavía no me has contado nada sobre ti. Dime tu nombre, tu objetivo o tu deporte y lo recordaré (solo en este teléfono)."
            else "Esto es lo que recuerdo (guardado solo en tu teléfono):\n• " + items.joinToString("\n• ") + "\n\nSi quieres que lo olvide, dime \"olvida todo\".",
            listOf("Olvida todo"), intent = "memoria",
        )
    }

    /** "¿Por qué?", "más", "sí", "no" after a previous answer. */
    private fun followUp(c: Ctx): CoachReply? {
        val last = memory.get("last_intent")?.let { id -> rules.firstOrNull { it.id == id } }
        val short = c.tokens.size <= 3
        if (short && c.has("porque", "por que", "y eso", "motivo", "razon", "explicame") && !c.has("pulso", "vfc", "recuper", "dorm", "estres")) {
            val why = last?.why?.invoke(c)
                ?: return CoachReply("¿Por qué qué? Pregúntame algo concreto, por ejemplo \"¿por qué tengo la recuperación baja?\"", listOf("¿Cómo estoy hoy?"), intent = "porque")
            return CoachReply(why, listOf("Dame consejos", "¿Entreno fuerte hoy?"), intent = "porque")
        }
        if (c.tokens.size <= 2 && c.has("mas$", "otro", "otra", "dame mas", "algo mas", "sigue", "dame consejos", "consejos$")) {
            last?.more?.let { return it(c).copy(intent = "mas") }
            if (last != null) return CoachReply("Sobre eso no tengo más que añadir. ¿Te cuento algo de tu sueño o de tu entreno de hoy?", listOf("¿Cómo dormí anoche?", "¿Entreno fuerte hoy?"), intent = "mas")
        }
        if (c.tokens.size <= 4 && c.has("empezar$", "empezar de nuevo")) return welcome(c.s)
        val yes = setOf("si", "vale", "claro", "venga", "dale", "bueno", "perfecto", "va")
        if (c.tokens.size <= 2 && c.tokens.all { it in yes }) {
            return CoachReply(pick("¡Genial! ¿Qué quieres que miremos?", "Perfecto. Dime qué te interesa."), listOf("¿Cómo estoy hoy?", "Mi semana", "Dame un plan"), intent = "afirmacion")
        }
        if (c.tokens.size <= 3 && (c.tokens == listOf("no") || c.tokens == listOf("nada") || c.norm == "da igual" || c.norm == "no gracias")) {
            return CoachReply("Sin problema. Aquí estaré cuando lo necesites.", listOf("¿Cómo dormí anoche?"), intent = "negacion")
        }
        return null
    }

    // ---- answers --------------------------------------------------------------------------------

    private fun greeting(s: CoachSnapshot): CoachReply {
        val time = when (s.hour) {
            in 5..11 -> "Buenos días"
            in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        val hook = when {
            s.recovery != null && s.hour in 5..13 -> " Hoy tu recuperación es del ${f0(s.recovery)} % (${band(s.recovery)})."
            s.effort != null && s.hour >= 18 -> " Llevas un esfuerzo de ${f1(s.effort)} hoy."
            else -> ""
        }
        return CoachReply("$time${hi()}.$hook ¿En qué te ayudo?", listOf("¿Cómo estoy hoy?", "¿Cómo dormí anoche?", "¿Entreno fuerte hoy?"), intent = "saludo")
    }

    private fun todaySummary(s: CoachSnapshot): CoachReply {
        val parts = buildList {
            s.recovery?.let { add("Recuperación: ${f0(it)} % (${band(it)}).") } ?: add(noRecoveryYet(s))
            sleepPhrase(s)?.let { add(it) }
            hrvPhrase(s)?.let { add(it) }
            rhrPhrase(s)?.let { add(it) }
            s.effort?.let { add("Esfuerzo hasta ahora: ${f1(it)} de 21.") }
            s.stress?.let { add("Estrés actual: ${stressWord(it)} (${f1(it)} de 3).") }
        }
        val flags = redFlags(s)
        val close = when {
            flags.size >= 2 -> "\n\nOjo: veo ${flags.joinToString(", ")}. Hoy prioriza descansar."
            s.recovery != null -> "\n\n" + trainAdvice(s).substringBefore(". ") + "."
            else -> ""
        }
        val goalTip = goalLabel(goal)?.let { "\n\nPensando en tu objetivo ($it): " + goalTip(s) } ?: ""
        return CoachReply(parts.joinToString("\n") + close + goalTip, listOf("¿Entreno fuerte hoy?", "¿Por qué?", "Mi semana"), CoachAction.Recovery, "resumen_hoy")
    }

    private fun goalTip(s: CoachSnapshot): String = when (goal) {
        "dormir" -> if ((s.sleepMin ?: 0.0) < sleepNeedH * 60 - 30) "esta noche intenta acostarte 30 minutos antes." else "mantén la misma hora de acostarte que anoche."
        "rendimiento" -> if ((s.recovery ?: 50.0) >= 67) "aprovecha hoy para tu sesión de calidad." else "guarda la calidad para un día en verde."
        "peso" -> "suma pasos (objetivo: ${f0(maxOf(8000.0, (s.stepsAvg ?: 6000.0) + 1500))}) y prioriza proteína en cada comida."
        "musculo" -> "llega a tu proteína diaria y duerme bien: el músculo crece descansando."
        "estres" -> "regálate 5 minutos de respiración lenta a media tarde."
        "longevidad" -> "lo que más suma es dormir 7–9 h, moverte cada día y sudar un poco 2–3 veces por semana."
        else -> "un hábito pequeño hoy vale más que uno perfecto mañana."
    }

    private fun recoveryAnswer(s: CoachSnapshot): CoachReply {
        val r = s.recovery ?: return CoachReply(noRecoveryYet(s), listOf("¿Cómo dormí anoche?", "¿Cuándo tendré datos?"), CoachAction.Calibration, "recuperacion")
        val opener = when {
            r >= 67 -> pick("¡Buena noticia${hi()}! Recuperación del ${f0(r)} %, en verde.", "Estás en verde: ${f0(r)} % de recuperación.")
            r >= 34 -> pick("Recuperación del ${f0(r)} %, en amarillo.", "Hoy estás en zona amarilla: ${f0(r)} %.")
            else -> pick("Recuperación del ${f0(r)} %, en rojo.", "Hoy tu cuerpo pide calma: ${f0(r)} % de recuperación.")
        }
        val detail = listOfNotNull(hrvPhrase(s), rhrPhrase(s), sleepPhrase(s)).joinToString(" ")
        val advice = when {
            r >= 67 -> "Es un buen día para entrenar con intensidad."
            r >= 34 -> "Puedes entrenar con moderación."
            else -> "Hoy prioriza descanso activo y acostarte pronto."
        }
        return CoachReply("$opener $detail\n\n$advice", listOf("¿Por qué?", "¿Entreno fuerte hoy?", "Consejos para recuperarme"), CoachAction.Recovery, "recuperacion")
    }

    private fun recoveryWhy(s: CoachSnapshot): String {
        if (s.recovery == null) return "La recuperación combina tu VFC, tu pulso en reposo, tu respiración y tu sueño, comparados con tu propia línea base. Por eso necesita unas noches para empezar."
        val reasons = buildList {
            pctDiff(s.hrv, s.hrvAvg)?.let {
                when {
                    it <= -8 -> add("tu VFC está un ${f0(abs(it))} % por debajo de tu media")
                    it >= 8 -> add("tu VFC está un ${f0(it)} % por encima de tu media")
                    else -> Unit
                }
            }
            if (s.rhr != null && s.rhrAvg != null) {
                val d = s.rhr - s.rhrAvg
                if (d >= 3) add("tu pulso en reposo está ${f0(d)} lpm alto") else if (d <= -3) add("tu pulso en reposo está ${f0(-d)} lpm más bajo de lo normal")
            }
            s.sleepMin?.let { if (it < sleepNeedH * 60 - 60) add("dormiste ${hm(sleepNeedH * 60 - it)} menos de lo que necesitas") }
            s.yesterdayEffort?.let { if (it >= 15) add("ayer hiciste un esfuerzo alto (${f1(it)})") }
            if (s.resp != null && s.respAvg != null && s.resp - s.respAvg >= 1) add("respiraste más rápido que de costumbre")
        }
        return if (reasons.isEmpty()) "Tus métricas están cerca de tu línea base, por eso la recuperación sale en ${band(s.recovery)}. No hay un factor que destaque."
        else "Lo que más ha influido: " + reasons.joinToString("; ") + ".\n\nOtras causas habituales que yo no veo: alcohol, cena tardía, estrés del trabajo o empezar a ponerte malo. Apuntarlas en el diario ayuda a encontrar el patrón."
    }

    private fun recoveryTips(): CoachReply = CoachReply(
        pick(
            "Lo que más sube la recuperación, en orden:\n1. Dormir más y con horario regular.\n2. Evitar alcohol (incluso 1–2 copas bajan la VFC esa noche).\n3. Cenar 2–3 h antes de acostarte.\n4. Alternar días duros con días suaves.\n5. Hidratarte bien y gestionar el estrés (respiración lenta, paseos).",
            "Tres palancas rápidas para mañana:\n• Acuéstate 30–45 minutos antes.\n• Nada de alcohol ni cafeína tarde.\n• Dormitorio fresco (18–19 °C), oscuro y silencioso.\n\nY a medio plazo: más volumen de cardio suave, que es lo que más sube tu VFC basal.",
        ),
        listOf("Consejos para dormir mejor", "¿Qué es la VFC?"), CoachAction.Journal, "recuperacion_mejorar",
    )

    private fun sleepAnswer(s: CoachSnapshot): CoachReply {
        val min = s.sleepMin ?: return CoachReply(
            if (s.nights == 0) "Aún no tengo ninguna noche registrada. Duerme esta noche con la pulsera y mañana te cuento qué tal has descansado." else "No tengo datos de la última noche. ¿Llevabas la pulsera puesta? Abre la app con la pulsera cerca para que sincronice.",
            listOf("Consejos para dormir mejor"), CoachAction.Sleep, "sueno_anoche",
        )
        val perf = s.sleepPct?.let { "Rendimiento del sueño: ${f0(it)} %. " } ?: ""
        val stages = buildList {
            s.deepMin?.let { add("${hm(it)} de profundo") }
            s.remMin?.let { add("${hm(it)} de REM") }
        }.takeIf { it.isNotEmpty() }?.joinToString(" y ")?.let { "Tuviste $it. " } ?: ""
        val eff = s.efficiency?.let { "Eficiencia del ${f0(it)} %${if (it < 85) " (algo baja: pasaste bastante tiempo despierto en la cama)" else ""}. " } ?: ""
        val wake = s.disturbances?.takeIf { it > 0 }?.let { "Te despertaste $it ${if (it == 1) "vez" else "veces"}. " } ?: ""
        val verdict = when {
            (s.sleepPct ?: 0.0) >= 85 -> pick("¡Noche excelente!", "Muy buena noche.")
            (s.sleepPct ?: 0.0) >= 70 -> "Una noche correcta."
            s.sleepPct != null -> "Noche floja: tu cuerpo no ha descansado lo que necesita."
            else -> ""
        }
        return CoachReply(
            "$verdict ${sleepPhrase(s) ?: ""} $perf$stages$eff$wake".trim().replace("  ", " "),
            listOf("¿Por qué?", "Consejos para dormir mejor", "¿A qué hora me acuesto?"), CoachAction.Sleep, "sueno_anoche",
        )
    }

    private fun sleepWhy(s: CoachSnapshot): String {
        val r = buildList {
            s.efficiency?.let { if (it < 85) add("mucho tiempo despierto en la cama (eficiencia ${f0(it)} %)") }
            s.disturbances?.let { if (it >= 4) add("$it despertares") }
            s.sleepMin?.let { if (it < sleepNeedH * 60 - 45) add("pocas horas respecto a tus ${f1(sleepNeedH)} h") }
            s.yesterdayEffort?.let { if (it >= 16) add("un día muy exigente, que aumenta lo que necesitas dormir") }
        }
        return if (r.isEmpty()) "El rendimiento del sueño compara cuánto dormiste con lo que necesitas, y suma eficiencia y regularidad. Anoche no veo nada fuera de lo normal."
        else "Lo que más ha restado: ${r.joinToString(", ")}. Causas típicas: cafeína por la tarde, alcohol, cena copiosa, estrés o un dormitorio con calor o luz."
    }

    private fun sleepTips(s: CoachSnapshot): CoachReply {
        val personal = buildList {
            if ((s.sleepMin ?: 999.0) < sleepNeedH * 60 - 45) add("Tu prioridad es la cantidad: adelanta la hora de acostarte 30 min durante una semana.")
            if ((s.efficiency ?: 100.0) < 85) add("Tu eficiencia es baja: si no te duermes en 20 minutos, levántate, haz algo aburrido con luz tenue y vuelve cuando tengas sueño.")
            if ((s.disturbances ?: 0) >= 4) add("Te despiertas a menudo: revisa calor, ruido y beber mucho antes de dormir.")
            memory.get("coffees")?.toDoubleOrNull()?.let { if (it >= 3) add("Tomas ${f0(it)} cafés: deja el último antes de las 14 h.") }
        }
        val base = "Lo básico que funciona:\n• Mismo horario cada día, también el finde.\n• Luz natural por la mañana, luz tenue por la noche.\n• Dormitorio fresco (18–19 °C), oscuro y silencioso.\n• Nada de cafeína después de las 14 h ni alcohol cerca de dormir.\n• Cena ligera 2–3 h antes de acostarte."
        val text = if (personal.isEmpty()) base else "Mirando tus datos:\n• " + personal.joinToString("\n• ") + "\n\n" + base
        if (memory.get("coffees") == null) memory.set("pending", "coffee_count")
        return CoachReply(
            text + if (memory.get("coffees") == null) "\n\nPor cierto, ¿cuántos cafés tomas al día?" else "",
            if (memory.get("coffees") == null) listOf("Ninguno", "1", "2", "3 o más") else listOf("Más consejos", "¿A qué hora me acuesto?"),
            CoachAction.Alarms, "sueno_consejos",
        )
    }

    private fun sleepTipsExtra() = CoachReply(
        pick(
            "Más ideas:\n• Ducha templada 60–90 min antes de dormir: al enfriarte después, te entra sueño.\n• Escribe en un papel lo que tienes pendiente para sacarlo de la cabeza.\n• Si roncas o te despiertas cansado aunque duermas mucho, coméntalo con tu médico (apnea del sueño).",
            "Otras cosas que ayudan:\n• Ejercicio regular, mejor no intenso en las 2 h previas a dormir.\n• Exponerte a luz brillante 10–20 min al despertar.\n• Usar la cama solo para dormir: nada de trabajar o ver series en ella.",
        ),
        listOf("¿A qué hora me acuesto?"), intent = "sueno_consejos",
    )

    private fun insomnia(s: CoachSnapshot) = CoachReply(
        "Cuando no consigues dormir:\n1. No mires la hora ni el móvil.\n2. Si llevas más de 20 minutos despierto, levántate, ve a otra habitación con luz tenue y haz algo aburrido hasta que tengas sueño.\n3. Prueba respiración 4-7-8: inspira 4 s, retén 7 s, exhala 8 s. Cuatro veces.\n4. Al día siguiente, levántate a la hora de siempre aunque hayas dormido poco: así esta noche tendrás más presión de sueño.\n\n" +
            (if (s.stress != null && s.stress >= 2) "Hoy tu estrés está alto (${f1(s.stress)}), y eso suele estar detrás. " else "") +
            "Si te pasa más de 3 noches por semana durante semanas, habla con tu médico: la terapia cognitivo-conductual para el insomnio funciona muy bien.",
        listOf("Ejercicio de respiración", "Consejos para dormir mejor"), intent = "insomnio",
    )

    private fun wakeUps(s: CoachSnapshot) = CoachReply(
        (s.disturbances?.let { "Anoche registré $it despertares. " } ?: "") +
            "Despertarse 2–4 veces por noche es normal, casi siempre sin recordarlo. Si son más o te cuesta volver a dormir, revisa:\n• Temperatura: el calor fragmenta mucho el sueño.\n• Alcohol: al metabolizarse a mitad de noche provoca despertares.\n• Líquidos: deja de beber en abundancia 1–2 h antes.\n• Estrés: el cortisol de madrugada (3–5 h) despierta a mucha gente.\n\nSi roncas fuerte o te despiertas con sensación de ahogo, consúltalo con tu médico.",
        listOf("Consejos para dormir mejor"), CoachAction.Sleep, "despertares",
    )

    private fun sleepNeed(s: CoachSnapshot): CoachReply {
        val base = sleepNeedH
        val extra = (s.yesterdayEffort ?: s.effort)?.let { if (it >= 14) (it - 12) * 4 else 0.0 } ?: 0.0
        val debt = s.weekSleepMin.filterNotNull().takeIf { it.isNotEmpty() }?.sumOf { (base * 60 - it).coerceAtLeast(0.0) }
        val tonight = base * 60 + extra + ((debt ?: 0.0) / 7).coerceAtMost(60.0)
        return CoachReply(
            "Tomo como base ${f1(base)} h${if (memory.get("sleep_need") == null) " (puedes cambiarlo diciéndome \"necesito 7 horas de sueño\")" else ""}." +
                (if (extra > 0) " Por el esfuerzo de hoy, suma unos ${f0(extra)} min." else "") +
                (debt?.let { " Esta semana acumulas ${hm(it)} de deuda de sueño." } ?: "") +
                "\n\nEsta noche te convendría dormir unas ${hm(tonight)}.",
            listOf("¿A qué hora me acuesto?"), CoachAction.Sleep, "necesidad_sueno",
        )
    }

    private fun bedtime(s: CoachSnapshot): CoachReply {
        val wake = memory.get("wake_time")
        val need = sleepNeedH * 60 + 15
        val txt = if (wake != null) {
            val (h, m) = wake.split(":").map { it.toInt() }
            val total = (h * 60 + m - need.roundToInt() + 24 * 60) % (24 * 60)
            "Si te levantas a las $wake y necesitas ${f1(sleepNeedH)} h (más 15 min para dormirte), deberías estar en la cama a las ${"%02d:%02d".format(total / 60, total % 60)}."
        } else {
            memory.set("pending", null)
            "Cuenta hacia atrás desde tu hora de levantarte: ${f1(sleepNeedH)} h de sueño + 15 min para dormirte. Por ejemplo, si te levantas a las 7:00, a la cama a las 22:45.\n\nDime \"me levanto a las 7:30\" y te lo calculo."
        }
        return CoachReply(txt, listOf("Configurar horario"), CoachAction.Alarms, "hora_acostarse")
    }

    private fun nap(s: CoachSnapshot): CoachReply {
        val shortSleep = s.sleepMin?.let { it < sleepNeedH * 60 - 60 } ?: false
        return CoachReply(
            (if (shortSleep) "Anoche dormiste poco, así que una siesta hoy te vendrá bien. " else "") +
                "La siesta ideal: 10–20 minutos, antes de las 16 h. Más larga te mete en sueño profundo y te levantas aturdido, y por la tarde te quita sueño para la noche.\n\nTruco: tómate un café justo antes de echarte; la cafeína hace efecto a los 20 min, justo al despertar.",
            listOf("¿A qué hora me acuesto?"), intent = "siesta",
        )
    }

    private fun caffeine(c: Ctx): CoachReply {
        val h = c.s.hour
        val n = memory.get("coffees")
        val timing = when {
            h >= 16 -> "Son más de las 16 h: un café ahora probablemente te robe sueño profundo esta noche, aunque te duermas igual. Mejor un descafeinado o una infusión."
            h >= 14 -> "Ya es tarde para cafeína si quieres dormir bien. Si lo necesitas, que sea pequeño."
            else -> "A esta hora no hay problema."
        }
        if (n == null) memory.set("pending", "coffee_count")
        return CoachReply(
            "$timing La cafeína tarda 5–6 horas en reducirse a la mitad, así que el café de las 17 h sigue activo a medianoche." + if (n == null) "\n\n¿Cuántos cafés sueles tomar al día?" else "",
            if (n == null) listOf("Ninguno", "1", "2", "3 o más") else listOf("Consejos para dormir mejor"),
            intent = "cafe",
        )
    }

    private fun alcohol(s: CoachSnapshot): CoachReply {
        val drank = s.recovery != null && redFlags(s).isNotEmpty()
        if (memory.get("drinks_week") == null) memory.set("pending", "drinks")
        return CoachReply(
            (if (drank) "Si bebiste ayer, se nota: ${redFlags(s).joinToString(" y ")}. " else "") +
                "El alcohol te ayuda a dormirte pero destroza la segunda mitad de la noche: menos REM, más despertares, pulso más alto y VFC más baja. Incluso 1–2 copas se ven en la recuperación.\n\nSi bebes: hazlo pronto, alterna con agua, cena bien y deja 3 horas antes de acostarte." +
                if (memory.get("drinks_week") == null) "\n\n¿Cuántas copas sueles tomar a la semana?" else "",
            if (memory.get("drinks_week") == null) listOf("0", "1-2", "3-5", "Más de 5") else listOf("¿Cómo estoy hoy?"),
            intent = "alcohol",
        )
    }

    private fun workoutSuggestion(s: CoachSnapshot): CoachReply {
        val r = s.recovery
        val sp = sport
        val text = when {
            r == null -> "Sin recuperación todavía, te propongo algo seguro: 30–45 minutos de cardio suave (puedes hablar mientras lo haces) + 10 minutos de movilidad."
            r >= 67 -> when (sp) {
                "running" -> pick("Día verde: series de 5 × 4 min a ritmo fuerte con 3 min suaves entre medias.", "Día verde: tempo de 20–30 min a ritmo de media maratón, con calentamiento y vuelta a la calma.")
                "gimnasio" -> pick("Día verde: fuerza pesada. 4–5 series de 3–6 repeticiones en sentadilla, peso muerto o press. Descansa 2–3 min entre series.", "Día verde: sesión de piernas exigente o un full-body con básicos pesados.")
                "ciclismo" -> "Día verde: 4 × 8 min a umbral con 4 min suaves de recuperación."
                else -> pick("Día verde: intervalos 6 × 3 min intensos (sin llegar al límite) con 2 min suaves, o un partido/sesión exigente de tu deporte.", "Día verde: entreno de calidad. Lo que más te cueste de tu semana, hazlo hoy.")
            }
            r >= 34 -> when (sp) {
                "running" -> "Día amarillo: rodaje de 40–60 min en zona 2 (ritmo en el que puedes hablar) + 4–6 rectas cortas."
                "gimnasio" -> "Día amarillo: fuerza moderada, 3 series de 8–12 repeticiones dejando 2–3 en la recámara. Nada al fallo."
                "ciclismo" -> "Día amarillo: 60–90 min en zona 2, cadencia alta."
                else -> "Día amarillo: 45 min de cardio moderado o una sesión técnica de tu deporte, sin apretar."
            }
            else -> "Día rojo: 20–40 min de paseo, yoga suave o movilidad. Si haces algo, que sea muy fácil. Hoy entrenas descansando."
        }
        return CoachReply(text, listOf("¿Por qué?", "Otro entreno"), CoachAction.Live, "que_entreno")
    }

    private fun zone2(s: CoachSnapshot): CoachReply {
        val maxHr = s.age?.let { 208 - 0.7 * it }
        val range = maxHr?.let { " Para ti, aproximadamente entre ${f0(it * 0.6)} y ${f0(it * 0.7)} lpm." } ?: ""
        return CoachReply(
            "La zona 2 es cardio suave: puedes mantener una conversación pero no cantar.$range\n\nEs la base de todo: mejora tus mitocondrias, tu VO₂ máx., tu pulso en reposo y tu recuperación. Recomendación: 150–300 minutos a la semana (por ejemplo, 3–5 sesiones de 45 min). Caminar rápido cuesta arriba, bici o trote suave sirven.",
            listOf("¿Y los intervalos?", "Mi edad fisiológica"), CoachAction.Live, "zona2",
        )
    }

    private fun hiit(s: CoachSnapshot): CoachReply {
        val ok = s.recovery?.let { it >= 67 }
        return CoachReply(
            (when (ok) {
                true -> "Hoy estás en verde, buen día para intervalos. "
                false -> "Hoy no es el mejor día para intervalos: tu recuperación es del ${f0(s.recovery!!)} %. Guárdalos para un día en verde. "
                null -> ""
            }) + "Los intervalos (zonas 4–5) son la forma más eficiente de subir tu VO₂ máx. Con 1–2 sesiones por semana basta: por ejemplo 4 × 4 min fuertes con 3 min suaves. Más no es mejor: necesitan 48 h de recuperación.",
            listOf("¿Entreno fuerte hoy?"), CoachAction.Live, "hiit",
        )
    }

    private fun strength(s: CoachSnapshot): CoachReply = CoachReply(
        "La fuerza es clave a cualquier edad: protege músculo, huesos y metabolismo. Recomendación: 2–3 sesiones por semana trabajando todo el cuerpo (empuje, tirón, piernas, bisagra de cadera y core). Unos 40–60 minutos a la semana ya dan beneficios grandes.\n\n" +
            (s.recovery?.let { if (it >= 67) "Hoy estás en verde: puedes ir pesado." else if (it >= 34) "Hoy, mejor cargas moderadas." else "Hoy, mejor técnica ligera o descanso." } ?: ""),
        listOf("¿Qué entreno me recomiendas?", "¿Cuánta proteína necesito?"), intent = "fuerza",
    )

    private fun running(c: Ctx): CoachReply {
        if (sport == null) memory.set("sport", "running")
        return CoachReply(
            "Para correr mejor, la regla 80/20: el 80 % de tus kilómetros a ritmo suave (zona 2) y el 20 % rápido. No subas más de un 10 % de volumen por semana, y haz algo de fuerza de piernas 2 veces por semana para prevenir lesiones.\n\n" + trainAdvice(c.s),
            listOf("¿Qué entreno me recomiendas?", "¿Cómo hago zona 2?"), CoachAction.Live, "correr",
        )
    }

    private fun fatigue(s: CoachSnapshot): CoachReply {
        val reds = s.weekRecovery.filterNotNull().count { it < 34 }
        val hiStrain = s.weekEffort.filterNotNull().count { it >= 15 }
        return CoachReply(
            (if (reds >= 3) "Esta semana llevas $reds días en rojo. " else "") + (if (hiStrain >= 4) "Y $hiStrain días de esfuerzo alto. " else "") +
                "Cuando te sientes así, el cuerpo pide una semana de descarga: baja el volumen un 40–50 %, mantén algo de intensidad corta, duerme más y come suficiente. Si tras 1–2 semanas sigues igual de cansado, consulta a tu médico (anemia, tiroides, déficit de sueño…).",
            listOf("¿Cómo estoy hoy?", "Mi semana"), CoachAction.Recovery, "sobreentrenamiento",
        )
    }

    private fun effortAnswer(s: CoachSnapshot): CoachReply {
        val e = s.effort ?: return CoachReply("Aún no tengo tu esfuerzo de hoy. Empieza a contar en cuanto la pulsera registra tu pulso.", listOf("¿Entreno fuerte hoy?"), CoachAction.Effort, "esfuerzo")
        val target = s.recovery?.let { effortTarget(it) }
        val vsTarget = target?.let {
            when {
                e < it.start -> " Tu objetivo según la recuperación es ${f0(it.start)}–${f0(it.endInclusive)}: aún tienes margen."
                e > it.endInclusive -> " Ya has superado tu objetivo (${f0(it.start)}–${f0(it.endInclusive)}). Lo que sumes ahora cuesta más recuperarlo."
                else -> " Estás dentro de tu objetivo (${f0(it.start)}–${f0(it.endInclusive)}). ¡Bien medido!"
            }
        } ?: ""
        val avg = s.effortAvg?.let { " Tu media es ${f1(it)}." } ?: ""
        return CoachReply("Llevas un esfuerzo de ${f1(e)} de 21.$avg$vsTarget", listOf("¿Por qué?", "¿Qué entreno me recomiendas?"), CoachAction.Effort, "esfuerzo")
    }

    private fun hrvAnswer(s: CoachSnapshot): CoachReply {
        val base = "La VFC (variabilidad de la frecuencia cardiaca) mide cuánto varía el tiempo entre latidos. Más variación = sistema nervioso más relajado y recuperado. Se mide durante el sueño y lo importante es compararla con tu propia media, no con la de otros."
        val mine = hrvPhrase(s)?.let { "\n\n$it" } ?: "\n\nAún no tengo tu VFC; aparece tras la primera noche."
        val tips = if ((pctDiff(s.hrv, s.hrvAvg) ?: 0.0) <= -8) "\n\nPara subirla: dormir más, nada de alcohol, día suave y respiración lenta antes de dormir." else ""
        return CoachReply(base + mine + tips, listOf("¿Por qué?", "¿Cómo la subo?"), CoachAction.Recovery, "vfc")
    }

    private fun rhrAnswer(s: CoachSnapshot): CoachReply {
        val mine = rhrPhrase(s) ?: "Aún no tengo tu pulso en reposo."
        val high = s.rhr != null && s.rhrAvg != null && s.rhr - s.rhrAvg >= 5
        return CoachReply(
            "$mine\n\nEl pulso en reposo se mide durmiendo. Baja con la forma física (más cardio suave) y sube con estrés, alcohol, calor, deshidratación, falta de sueño o una infección." +
                if (high) "\n\nHoy lo tienes alto: si además te notas raro, puede que estés incubando algo. Día tranquilo." else "",
            listOf("¿Cómo estoy hoy?"), CoachAction.Recovery, "fcr",
        )
    }

    private fun stressWord(v: Double) = when {
        v < 1 -> "bajo"
        v < 2 -> "medio"
        else -> "alto"
    }

    private fun stressAnswer(s: CoachSnapshot): CoachReply {
        val now = s.stress?.let { "Ahora mismo tu nivel de estrés es ${stressWord(it)} (${f1(it)} de 3). " } ?: "No tengo lecturas de estrés recientes. "
        val advice = when {
            s.stress == null -> "El estrés se calcula comparando tu pulso y tu VFC del día con tu línea base."
            s.stress >= 2 -> "Vamos a bajarlo: 2 minutos de respiración lenta (exhalación más larga que la inhalación) ya cambian el pulso. Un paseo corto al aire libre también funciona."
            s.stress >= 1 -> "Es un nivel normal en un día activo. Si notas tensión, una pausa de respiración te vendrá bien."
            else -> "Estás tranquilo. Buen momento para concentrarte o descansar de verdad."
        }
        return CoachReply("$now$advice", listOf("Ejercicio de respiración", "Ver estrés"), CoachAction.Stress, "estres")
    }

    private fun breathing() = CoachReply(
        pick(
            "Respiración cuadrada (4 minutos):\n1. Inspira por la nariz contando 4.\n2. Mantén el aire contando 4.\n3. Exhala por la boca contando 4.\n4. Mantén sin aire contando 4.\nRepite. Si te mareas, acorta a 3.",
            "Respiración 4-6 (coherencia cardiaca, 5 minutos):\nInspira 4 segundos por la nariz y exhala 6 segundos. Unas 6 respiraciones por minuto. Es el ritmo que más sube la VFC en el momento.",
            "Suspiro fisiológico (el más rápido, 1 minuto):\nDos inspiraciones seguidas por la nariz (una larga y otra corta para llenar del todo) y una exhalación muy lenta por la boca. Repite 3–5 veces.",
        ),
        listOf("Otro ejercicio", "¿Cómo está mi estrés?"), CoachAction.Stress, "respirar",
    )

    private fun nutrition(c: Ctx): CoachReply {
        val evening = c.s.hour >= 19 || c.has("cena", "cenar")
        val g = goal
        val focus = when (g) {
            "peso" -> "Para perder grasa: proteína en cada comida, mucha verdura, y un déficit moderado (300–500 kcal). Sin prohibir nada, pero con menos ultraprocesados."
            "musculo" -> "Para ganar músculo: un ligero superávit (200–300 kcal), 1,6–2,2 g/kg de proteína y carbohidratos alrededor del entreno."
            "rendimiento" -> "Para rendir: carbohidratos suficientes los días de entreno duro, proteína repartida y buena hidratación."
            else -> "Lo básico: plato con verdura, proteína y carbohidrato de calidad; fruta; legumbres varias veces por semana; poco ultraprocesado y poco azúcar añadido."
        }
        val timing = if (evening) "\n\nPara la cena: ligera, 2–3 h antes de dormir. Las cenas copiosas o tardías suben el pulso nocturno y bajan la recuperación." else ""
        return CoachReply(focus + timing + "\n\nNo soy nutricionista: si tienes alguna patología o dieta especial, consulta a un profesional.", listOf("¿Cuánta proteína necesito?", "¿Cuánta agua bebo?"), intent = "nutricion")
    }

    private fun loseWeight(s: CoachSnapshot): CoachReply {
        if (goal == null) memory.set("goal", "peso")
        val steps = s.stepsAvg?.let { "Tu media es de ${f0(it)} pasos; intenta subir a ${f0(maxOf(8000.0, it + 2000))}. " } ?: "Apunta a 8.000–10.000 pasos al día. "
        return CoachReply(
            "Lo que funciona de verdad para perder grasa:\n1. Déficit moderado (300–500 kcal/día), no dietas extremas.\n2. Proteína alta para no perder músculo.\n3. Fuerza 2–3 veces por semana.\n4. Moverte mucho en el día a día: $steps\n5. Dormir 7–9 h: con poco sueño aumenta el hambre y se pierde más músculo.\n\nObjetivo realista: 0,5–1 % de tu peso por semana.",
            listOf("¿Cuánta proteína necesito?", "Dame un plan"), intent = "perder_peso",
        )
    }

    private fun gainMuscle(s: CoachSnapshot): CoachReply {
        if (goal == null) memory.set("goal", "musculo")
        return CoachReply(
            "Para ganar músculo:\n1. Fuerza 3–4 días por semana, 10–20 series por grupo muscular a la semana, cerca del fallo (1–3 repeticiones en la recámara).\n2. Progresión: sube peso o repeticiones poco a poco.\n3. Proteína: 1,6–2,2 g/kg al día.\n4. Ligero superávit calórico.\n5. Dormir bien: la hormona de crecimiento se libera sobre todo en el sueño profundo." +
                (s.deepMin?.let { "\n\nAnoche tuviste ${hm(it)} de sueño profundo." } ?: ""),
            listOf("¿Qué entreno me recomiendas?", "¿Cuánta proteína necesito?"), intent = "ganar_musculo",
        )
    }

    private fun stepsAnswer(s: CoachSnapshot): CoachReply {
        val st = s.steps
        val age = s.age ?: 40
        val target = if (age >= 60) 7000 else 9000
        val txt = when {
            st == null -> "Aún no tengo tus pasos de hoy."
            st >= target -> "Llevas $st pasos. ¡Objetivo cumplido! Por encima de $target pasos el beneficio sigue, pero más despacio."
            else -> "Llevas $st pasos. Te faltan ${target - st} para llegar a $target, que es donde se concentra la mayor parte del beneficio para tu edad. Un paseo de ${((target - st) / 110).coerceAtLeast(5)} minutos lo resuelve."
        }
        return CoachReply("$txt\n\nUn truco: camina 10 minutos después de cada comida; ayuda también a la glucosa.", listOf("¿Cuántas calorías he gastado?"), CoachAction.Effort, "pasos")
    }

    private fun ageAnswer(s: CoachSnapshot): CoachReply {
        val pa = s.pulseAge
        val txt = if (pa == null || s.age == null) {
            "La edad fisiológica estima cómo de \"joven\" es tu cuerpo a partir de 9 hábitos: horas y regularidad del sueño, pasos, minutos en zonas de pulso, fuerza, VO₂ máx., pulso en reposo y masa magra. Necesito 21 días de datos para calcularla."
        } else {
            val d = s.age - pa
            "Tu edad fisiológica es ${f1(pa)} años, ${if (d >= 0) "${f1(d)} menos" else "${f1(-d)} más"} que tu edad real.\n\nLo que más años puede restar: VO₂ máx. (cardio), dormir 7–9 h con regularidad y moverte a diario."
        }
        return CoachReply(txt, listOf("¿Cómo mejoro mi VO₂ máx.?", "Dame un plan"), CoachAction.Age, "edad_fisiologica")
    }

    private fun illness(s: CoachSnapshot): CoachReply {
        val flags = redFlags(s)
        return CoachReply(
            (if (flags.isNotEmpty()) "Tus datos lo confirman: ${flags.joinToString(", ")}. " else "") +
                "Si estás enfermo, nada de entrenar fuerte. La regla del cuello: si los síntomas son solo de cuello para arriba (mocos, garganta leve) puedes caminar suave; si hay fiebre, dolor muscular o síntomas en el pecho, descanso total.\n\nBebe, duerme mucho y vuelve poco a poco cuando tu VFC y tu pulso en reposo regresen a tu normal. Si la fiebre es alta, dura más de 3 días o te cuesta respirar, ve al médico.",
            listOf("¿Cómo estoy hoy?"), CoachAction.Recovery, "enfermo",
        )
    }

    private fun motivation(s: CoachSnapshot): CoachReply {
        val streak = if (s.streak >= 2) "Llevas una racha de ${s.streak} días. No la rompas hoy por cinco minutos. " else ""
        return CoachReply(
            streak + pick(
                "Truco de los 5 minutos: comprométete solo a empezar 5 minutos. Si después quieres parar, paras. Casi nunca paras.",
                "No hace falta motivación, hace falta un plan pequeño. Ponte la ropa de deporte. Ese es todo el objetivo de ahora mismo.",
                "Piensa en cómo te sentirás después, no en cómo te sientes ahora. Nadie se arrepiente de un paseo.",
            ) + (goalLabel(goal)?.let { "\n\nRecuerda por qué empezaste: quieres $it." } ?: ""),
            listOf("¿Qué entreno me recomiendas?"), intent = "motivacion",
        )
    }

    private fun weekSummary(s: CoachSnapshot): CoachReply {
        val rec = s.weekRecovery.filterNotNull()
        val sl = s.weekSleepMin.filterNotNull()
        val ef = s.weekEffort.filterNotNull()
        if (rec.isEmpty() && sl.isEmpty() && ef.isEmpty()) {
            return CoachReply("Todavía no tengo datos suficientes de esta semana. Lleva la pulsera día y noche y en unos días te hago el resumen.", listOf("¿Cuándo tendré datos?"), intent = "semana")
        }
        val lines = buildList {
            if (rec.isNotEmpty()) add("Recuperación media: ${f0(rec.average())} % (${rec.count { it >= 67 }} días en verde, ${rec.count { it < 34 }} en rojo).")
            if (sl.isNotEmpty()) add("Sueño medio: ${hm(sl.average())} por noche.")
            if (ef.isNotEmpty()) add("Esfuerzo medio: ${f1(ef.average())}; día más exigente: ${f1(ef.max())}.")
        }
        val insight = when {
            rec.isNotEmpty() && rec.count { it < 34 } >= 3 -> "Has tenido varios días en rojo. Toca bajar carga y dormir más."
            sl.isNotEmpty() && sl.average() < sleepNeedH * 60 - 45 -> "Lo que más te limita es el sueño: duermes menos de lo que necesitas."
            rec.isNotEmpty() && rec.average() >= 67 -> "¡Semana muy buena! Puedes permitirte un poco más de carga."
            else -> "Semana equilibrada. Sigue así."
        }
        return CoachReply("Tu semana:\n• " + lines.joinToString("\n• ") + "\n\n$insight", listOf("Dame un plan", "¿Cómo estoy hoy?"), CoachAction.Trends, "semana")
    }

    private fun plan(s: CoachSnapshot): CoachReply {
        val g = goal
        if (g == null) {
            memory.set("pending", "goal")
            return CoachReply("Para darte un plan necesito saber tu objetivo. ¿Qué quieres mejorar?", listOf("Dormir mejor", "Rendir más", "Perder grasa", "Ganar músculo", "Bajar el estrés", "Envejecer mejor"), intent = "plan")
        }
        val body = when (g) {
            "dormir" -> "Plan de 7 días para dormir mejor:\n• Día 1–7: misma hora de levantarte, incluido el finde.\n• Día 1: fija una hora de \"apagar pantallas\" 45 min antes de dormir.\n• Día 2: último café antes de las 14 h.\n• Día 3: 10 min de luz natural al despertar.\n• Día 4: dormitorio a 18–19 °C y totalmente a oscuras.\n• Día 5: cena 3 h antes de acostarte.\n• Día 6–7: revisa conmigo tu rendimiento del sueño."
            "rendimiento" -> "Plan semanal para rendir más:\n• 2 sesiones de calidad (intervalos o fuerza pesada) en días verdes.\n• 2–3 sesiones de zona 2 (45–60 min).\n• 1 día de descanso total.\n• Si amaneces en rojo, cambia la calidad por zona 2 o descanso.\n• Duerme 8 h las noches previas a la calidad."
            "peso" -> "Plan semanal para perder grasa:\n• Pasos: +2.000 sobre tu media cada día.\n• Fuerza: 3 días, cuerpo completo.\n• Proteína en cada comida.\n• Cenas ligeras y tempranas.\n• Duerme 7–9 h: con poco sueño comes más.\n• Pésate solo 1 vez por semana, a la misma hora."
            "musculo" -> "Plan semanal para ganar músculo:\n• 4 días de fuerza (torso/pierna/torso/pierna).\n• 10–20 series por grupo a la semana.\n• Proteína 1,6–2,2 g/kg.\n• Cardio suave 1–2 días para la salud del corazón.\n• Días en rojo: técnica ligera o descanso."
            "estres" -> "Plan de 7 días para bajar el estrés:\n• Cada mañana: 5 min de respiración 4-6.\n• Cada día: 20–30 min caminando al aire libre.\n• Pausas de 2 min cada 90 min de trabajo.\n• Sin móvil la primera y la última media hora del día.\n• Apunta en el diario los días más estresantes y mira tu recuperación."
            "longevidad" -> "Plan semanal para envejecer mejor:\n• 150–300 min de zona 2.\n• 1–2 sesiones de intervalos.\n• 2 sesiones de fuerza.\n• 7–9 h de sueño con horario regular.\n• 8.000+ pasos diarios.\nCada uno de estos puntos resta años en tu edad fisiológica."
            else -> "Plan semanal básico:\n• 7–9 h de sueño con horario fijo.\n• 8.000 pasos al día.\n• 2 días de fuerza y 2–3 de cardio suave.\n• Ajusta la intensidad a tu recuperación de cada mañana."
        }
        val today = s.recovery?.let { "\n\nHoy (${f0(it)} %, ${band(it)}): " + trainAdvice(s).substringBefore(". ") + "." } ?: ""
        return CoachReply(body + today, listOf("¿Cómo estoy hoy?", "Mi semana"), intent = "plan")
    }

    @Suppress("UNUSED_PARAMETER")
    private fun fallback(c: Ctx): CoachReply = fallbackGeneric()

    private fun fallbackGeneric() = CoachReply(
        pick(
            "No estoy seguro de haberte entendido. Puedo hablarte de tu sueño, tu recuperación, tu esfuerzo, el estrés, la nutrición o tu entreno de hoy.",
            "Esa no me la sé. Prueba a preguntarme de otra forma, por ejemplo: \"¿cómo dormí?\", \"¿entreno hoy?\" o \"consejos para el estrés\".",
            "Mmm, no lo pillo. Como no uso inteligencia artificial, entiendo mejor las frases cortas y concretas. ¿Te refieres a alguna de estas?",
        ),
        listOf("¿Cómo estoy hoy?", "¿Cómo dormí anoche?", "¿Entreno fuerte hoy?", "¿Qué puedes hacer?"),
        intent = "no_entendido",
    )
}

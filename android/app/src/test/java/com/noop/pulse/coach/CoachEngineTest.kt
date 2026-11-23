package com.noop.pulse.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CoachEngineTest {

    private val data = CoachSnapshot(
        recovery = 42.0, sleepPct = 71.0, sleepMin = 380.0, deepMin = 70.0, remMin = 85.0, efficiency = 82.0,
        disturbances = 5, hrv = 48.0, hrvAvg = 60.0, rhr = 58.0, rhrAvg = 54.0, resp = 15.0, respAvg = 14.8,
        effort = 9.5, effortAvg = 11.0, yesterdayEffort = 16.0, steps = 6200, stepsAvg = 7000.0, kcal = 2100.0,
        stress = 2.2, nights = 12, streak = 5, age = 35, sex = "male",
        weekRecovery = listOf(70.0, 55.0, 30.0, null, 80.0, 42.0, 42.0),
        weekSleepMin = listOf(420.0, 400.0, 350.0, 460.0, 380.0, 390.0, 380.0),
        weekEffort = listOf(12.0, 16.0, 8.0, 10.0, 15.0, 16.0, 9.5), hour = 10,
    )

    private fun engine(mem: CoachMemory = MapCoachMemory()) = CoachEngine(mem, Random(1))

    private fun intentOf(msg: String, s: CoachSnapshot = data, mem: CoachMemory = MapCoachMemory()) =
        engine(mem).reply(msg, s).intent

    @Test fun `normalises accents, repeats and abbreviations`() {
        assertEquals("hola que tal", CoachText.normalize("¡¡Holaaa!! ¿Q tal?"))
        assertEquals("porque dormi tan mal", CoachText.normalize("xq dormí tan mal"))
    }

    @Test fun `numbers in words and digits`() {
        assertEquals(6.5, CoachText.firstNumber(CoachText.normalize("duermo 6 y media"))!!, 1e-9)
        assertEquals(7.5, CoachText.firstNumber(CoachText.normalize("me levanto a las 7:30"))!!, 1e-9)
        assertEquals(3.0, CoachText.firstNumber(CoachText.normalize("tres cafés"))!!, 1e-9)
        assertEquals(8.0, CoachText.firstNumber(CoachText.normalize("8h"))!!, 1e-9)
    }

    @Test fun `routes common questions`() {
        val cases = mapOf(
            "hola" to "saludo",
            "Buenos días!" to "saludo",
            "como dormi anoche?" to "sueno_anoche",
            "¿Cómo he dormido?" to "sueno_anoche",
            "q tal mi sueño" to "sueno_anoche",
            "consejos para dormir mejor" to "sueno_consejos",
            "no puedo dormir" to "insomnio",
            "me despierto mucho por la noche" to "despertares",
            "cuantas horas necesito dormir" to "necesidad_sueno",
            "a que hora me acuesto" to "hora_acostarse",
            "puedo echarme una siesta" to "siesta",
            "¿Entreno fuerte hoy?" to "entrenar_hoy",
            "deberia entrenar hoy" to "entrenar_hoy",
            "que entreno me recomiendas" to "que_entreno",
            "como esta mi recuperacion" to "recuperacion",
            "mi recuperasion" to "recuperacion",
            "como estoy hoy" to "resumen_hoy",
            "que es la vfc" to "vfc",
            "por que tengo el pulso en reposo alto" to "fcr",
            "estoy muy estresado" to "estres",
            "tengo ansiedad" to "ansiedad",
            "ejercicio de respiracion" to "respirar",
            "cuanta proteina necesito" to "proteina",
            "cuanta agua tengo que beber" to "hidratacion",
            "quiero adelgazar" to "perder_peso",
            "como gano masa muscular" to "ganar_musculo",
            "cuantos pasos llevo" to "pasos",
            "mi edad fisiologica" to "edad_fisiologica",
            "creo que estoy resfriado" to "enfermo",
            "tengo jet lag" to "viaje",
            "anoche bebi mucha cerveza" to "alcohol",
            "puedo tomar cafe ahora" to "cafe",
            "no tengo ganas de entrenar" to "motivacion",
            "como ha ido mi semana" to "semana",
            "la pulsera no sincroniza" to "pulsera",
            "donde se guardan mis datos" to "datos_privacidad",
            "tengo agujetas" to "agujetas",
            "me he lesionado la rodilla" to "lesion",
            "que puedes hacer" to "quien_eres",
            "cuentame un chiste" to "chiste",
            "dame un plan" to "plan",
            "zona 2" to "zona2",
            "hacer series de intervalos" to "hiit",
            "gracias!!" to "gracias",
            "cuando tendre datos" to "calibracion",
            "estoy triste" to "animo_bajo",
            "tengo la regla" to "ciclo",
            "cuanto esfuerzo llevo" to "esfuerzo",
            "asdkjh qwe" to "no_entendido",
        )
        val wrong = cases.mapNotNull { (msg, want) ->
            val got = intentOf(msg)
            if (got != want) "'$msg' → $got (esperado $want)" else null
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun `safety rules win over everything`() {
        assertEquals("seguridad_urgencia", intentOf("tengo dolor en el pecho al correr"))
        assertEquals("seguridad_crisis", intentOf("no quiero vivir"))
        assertTrue(engine().reply("pienso en el suicidio", data).text.contains("024"))
    }

    @Test fun `answers use the user's numbers`() {
        val r = engine().reply("como esta mi recuperacion", data).text
        assertTrue(r, r.contains("42 %"))
        val why = engine().let { e -> e.reply("mi recuperacion", data); e.reply("¿por qué?", data) }
        assertTrue(why.text, why.text.contains("VFC") && why.intent == "porque")
    }

    @Test fun `missing data is said, not invented`() {
        val r = engine().reply("como esta mi recuperacion", CoachSnapshot(nights = 1)).text
        assertTrue(r, r.contains("calibrando"))
    }

    @Test fun `onboarding conversation remembers goal sport and sleep`() {
        val mem = MapCoachMemory()
        val e = engine(mem)
        e.welcome(CoachSnapshot())
        assertEquals("objetivo_guardado", e.reply("Dormir mejor", data).intent)
        assertEquals("dormir", mem.get("goal"))
        assertEquals("deporte_guardado", e.reply("Correr", data).intent)
        assertEquals("running", mem.get("sport"))
        assertEquals("sueno_habitual", e.reply("6 horas", data).intent)
        assertEquals("6.0", mem.get("usual_sleep"))
        assertTrue(e.reply("que sabes de mi", data).text.contains("dormir mejor"))
    }

    @Test fun `name and goal statements`() {
        val mem = MapCoachMemory()
        val e = engine(mem)
        e.reply("me llamo Lucía", data)
        assertEquals("Lucía", mem.get("name"))
        assertTrue(e.reply("hola", data).text.contains("Lucía"))
        e.reply("mi objetivo es bajar el estrés", data)
        assertEquals("estres", mem.get("goal"))
        e.reply("olvida todo", data)
        assertEquals(null, mem.get("name"))
    }

    @Test fun `bedtime from wake time`() {
        val mem = MapCoachMemory()
        val r = engine(mem).reply("me levanto a las 7:00", data)
        assertTrue(r.text, r.text.contains("22:45"))
    }

    @Test fun `follow up more on last topic`() {
        val e = engine()
        e.reply("ejercicio de respiracion", data)
        assertEquals("mas", e.reply("otro", data).intent)
    }

    @Test fun `accepting an offer from a coach bar answers that topic`() {
        val e = engine()
        e.offer("como esta mi estres")
        assertEquals("estres", e.reply("Sí, vamos", data).intent)
        e.offer("como esta mi recuperacion")
        assertEquals("negacion", e.reply("No, gracias", data).intent)
    }

    @Test fun `bar suggestion queries route to their topic`() {
        assertEquals("esfuerzo", intentOf("como va mi esfuerzo de hoy"))
        assertEquals("recuperacion", intentOf("como esta mi recuperacion"))
        assertEquals("sueno_consejos", intentOf("consejos para dormir mejor"))
        assertEquals("respirar", intentOf("ejercicio de respiracion"))
    }
}

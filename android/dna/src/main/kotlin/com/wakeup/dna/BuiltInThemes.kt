package com.wakeup.dna

/** Small declarative defaults that ship with the app. Everything else lives in the content library. */
object BuiltInThemes {
    val IDS = listOf("linen", "fjordlys", "salt-flat")

    fun load(id: String): ThemeDna {
        val stream = BuiltInThemes::class.java.getResourceAsStream("/themes/$id.json")
            ?: throw IllegalStateException("missing built-in theme $id")
        return stream.use { ThemeDna.parse(it.readBytes().decodeToString()) }
    }

    fun all(): List<ThemeDna> = IDS.map(::load)
}

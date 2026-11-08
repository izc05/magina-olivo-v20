package com.isivoltpro.maginaolivo.core.common

/** «1 pesada», «2 pesadas». */
fun countOf(count: Number, singular: String, plural: String): String =
    "$count ${if (count.toLong() == 1L) singular else plural}"

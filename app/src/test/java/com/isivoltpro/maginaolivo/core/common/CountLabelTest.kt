package com.isivoltpro.maginaolivo.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CountLabelTest {
    @Test fun oneIsSingular() = assertEquals("1 pesada", countOf(1, "pesada", "pesadas"))
    @Test fun zeroAndManyArePlural() {
        assertEquals("0 pesadas", countOf(0, "pesada", "pesadas"))
        assertEquals("3 pesadas", countOf(3L, "pesada", "pesadas"))
    }
}
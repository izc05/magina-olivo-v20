package com.isivoltpro.maginaolivo.feature.maps

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapUnifiedSearchTest {
    @Test fun emptyTextAsksNothing() = assertEquals(MapSearchIntent.Empty, classifyMapSearch("   "))

    @Test fun readsCoordinatesInTheFormatsAlreadySupported() {
        val decimal = classifyMapSearch("37.636, -3.480") as MapSearchIntent.Coordinates
        assertEquals(37.636, decimal.point.latitude, 1e-9)
        assertTrue(classifyMapSearch("37°38'09.6\"N 3°28'48.4\"W") is MapSearchIntent.Coordinates)
    }

    @Test fun twoWholeNumbersAreNeverSentToTheMapAsAPoint() {
        assertTrue(classifyMapSearch("5 12") is MapSearchIntent.NeedsClarification)
        assertTrue(classifyMapSearch("12") is MapSearchIntent.NeedsClarification)
    }

    @Test fun readsReferencesOfFourteenAndTwentyCharacters() {
        assertEquals(MapSearchIntent.CadastralReference("23044A00400120"), classifyMapSearch("23044a00400120"))
        assertEquals(MapSearchIntent.CadastralReference("23044A00400120"), classifyMapSearch("23044A00400120 0000AB"))
    }

    @Test fun aLongNameWithoutDigitsIsAPlaceNotAReference() {
        assertEquals(MapSearchIntent.Place("Sierramagina"), classifyMapSearch("Sierramagina"))
        assertEquals(MapSearchIntent.Place("Torredelcampoo"), classifyMapSearch("Torredelcampoo"))
    }

    @Test fun readsPolygonAndParcelWithOrWithoutMunicipality() {
        assertEquals(MapSearchIntent.PolygonParcel("5", "12", null), classifyMapSearch("polígono 5 parcela 12"))
        assertEquals(MapSearchIntent.PolygonParcel("5", "12", null), classifyMapSearch("Pol. 5, parc. 12"))
        assertEquals(MapSearchIntent.PolygonParcel("5", "12", "Bedmar"), classifyMapSearch("pol 5 par 12 Bedmar"))
    }

    @Test fun anyOtherTextIsAPlace() = assertEquals(MapSearchIntent.Place("Bedmar"), classifyMapSearch(" Bedmar "))

    @Test fun ownMatchesIgnoreAccentsAndNeedTwoLetters() {
        val farm = UUID.randomUUID()
        val parcel = UUID.randomUUID()
        val found = ownMapMatches("solana", listOf(farm to "La Solana"), listOf(parcel to "Solana alta"))
        assertEquals(listOf(MapOwnMatch(farm, "Finca · La Solana", true), MapOwnMatch(parcel, "Parcela · Solana alta", false)), found)
        assertEquals(1, ownMapMatches("JODAR", listOf(farm to "Jódar"), emptyList()).size)
        assertTrue(ownMapMatches("s", listOf(farm to "La Solana"), emptyList()).isEmpty())
    }
}

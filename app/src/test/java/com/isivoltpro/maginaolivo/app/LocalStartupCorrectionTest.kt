package com.isivoltpro.maginaolivo.app

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocalStartupCorrectionTest {
    @Test fun recreatedOwnerWaitsForInFlightCorrection()=runBlocking {
        val correction=LocalStartupCorrection();val release=CompletableDeferred<Unit>()
        var calls=0
        val first=async(start=CoroutineStart.UNDISPATCHED) { correction.ensure { calls++;release.await();true } }
        val recreated=async(start=CoroutineStart.UNDISPATCHED) { correction.ensure { calls++;true } }
        assertFalse(recreated.isCompleted);assertEquals(1,calls)
        release.complete(Unit);assertTrue(first.await());assertTrue(recreated.await());assertEquals(1,calls)
    }
    @Test fun cancelledOwnerDoesNotMarkUnfinishedCorrectionComplete()=runBlocking {
        val correction=LocalStartupCorrection();var calls=0
        val first=async(start=CoroutineStart.UNDISPATCHED) { correction.ensure { calls++;awaitCancellation() } }
        val recreated=async(start=CoroutineStart.UNDISPATCHED) { correction.ensure { calls++;true } }
        assertFalse(recreated.isCompleted)
        first.cancelAndJoin();assertTrue(recreated.await());assertEquals(2,calls)
    }
    @Test fun failedCorrectionIsRetriedRatherThanMemoized()=runBlocking {
        val correction=LocalStartupCorrection()
        assertFalse(correction.ensure { false });assertTrue(correction.ensure { true })
        assertTrue(correction.ensure { fail("Successful correction must not repeat");false })
    }
}

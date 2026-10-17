package com.isivoltpro.maginaolivo.feature.accountlink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class FarmLinkAccessStoreTest {
    private val accountA = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val accountB = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test fun recreatedActivityRetainsTheSameNavigationStoreAndEntryViewModels() {
        val activityStore = ViewModelStore()
        val first = holder(activityStore)
        val owner = first.ownerFor(accountA, true)
        val navigation = probe(owner)
        // ComponentActivity supplies this same retained parent store after configuration change.
        val recreated = holder(activityStore)
        assertSame(first, recreated)
        assertSame(owner, recreated.ownerFor(accountA, true))
        assertSame(navigation, probe(recreated.ownerFor(accountA, true)))
        assertEquals(0, navigation.clearCount)
        activityStore.clear()
        assertEquals(1, navigation.clearCount)
    }

    @Test fun logoutAndAnotherAccountClearNavigationWithoutReusingAnOldStore() {
        val activityStore = ViewModelStore()
        val holder = holder(activityStore)
        val firstA = holder.ownerFor(accountA, true)
        val aNavigation = probe(firstA)
        val loggedOut = holder.ownerFor(null, false)
        assertNotSame(firstA, loggedOut)
        assertEquals(1, aNavigation.clearCount)
        val logoutNavigation = probe(loggedOut)
        val b = holder.ownerFor(accountB, false)
        assertNotSame(loggedOut, b)
        assertNotSame(firstA, b)
        assertEquals(1, logoutNavigation.clearCount)
        val bNavigation = probe(b)
        val returningA = holder.ownerFor(accountA, true)
        assertNotSame(b, returningA)
        assertNotSame(loggedOut, returningA)
        assertEquals(1, bNavigation.clearCount)
        assertNotSame(firstA, returningA)
        assertNotSame(aNavigation, probe(returningA))
        activityStore.clear()
        assertEquals(1, aNavigation.clearCount)
        assertEquals(1, bNavigation.clearCount)
    }

    @Test fun identityChangeClearsNavigationEvenWhenAccessRemainsAllowed() {
        val activityStore = ViewModelStore()
        val holder = holder(activityStore)
        val owner = holder.ownerFor(accountA, true)
        val navigation = probe(owner)
        assertNotSame(owner, holder.ownerFor(accountB, true))
        assertEquals(1, navigation.clearCount)
        activityStore.clear()
    }

    @Test fun accessChangeClearsNavigationEvenWhenTheAccountDoesNotChange() {
        val activityStore = ViewModelStore()
        val holder = holder(activityStore)
        val allowed = holder.ownerFor(accountA, true)
        val navigation = probe(allowed)
        val locked = holder.ownerFor(accountA, false)
        assertNotSame(allowed, locked)
        assertEquals(1, navigation.clearCount)
        val lockedNavigation = probe(locked)
        assertSame(locked, holder.ownerFor(accountA, false))
        val allowedAgain = holder.ownerFor(accountA, true)
        assertNotSame(allowed, allowedAgain)
        assertEquals(1, lockedNavigation.clearCount)
        val currentNavigation = probe(allowedAgain)
        activityStore.clear()
        assertEquals(1, currentNavigation.clearCount)
        assertEquals(1, navigation.clearCount)
    }

    private fun holder(store: ViewModelStore): FarmLinkAccessStore =
        ViewModelProvider.create(owner(store), factory { FarmLinkAccessStore() })[FarmLinkAccessStore::class]

    private fun probe(owner: ViewModelStoreOwner): Probe =
        ViewModelProvider.create(owner, factory { Probe() })[Probe::class]

    private fun owner(store: ViewModelStore) = object : ViewModelStoreOwner {
        override val viewModelStore = store
    }

    private inline fun <reified T : ViewModel> factory(crossinline create: () -> T) =
        object : ViewModelProvider.Factory {
            override fun <V : ViewModel> create(modelClass: Class<V>): V {
                require(modelClass == T::class.java)
                @Suppress("UNCHECKED_CAST")
                return create() as V
            }
        }

    private class Probe : ViewModel() {
        var clearCount = 0
        override fun onCleared() { clearCount++ }
    }
}

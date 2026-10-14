package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.feature.account.*
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

/** Real Android Keystore, synthetic owned file/alias only, no Auth network. */
class AccountAuthStorageTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val fixture=UUID.randomUUID().toString()
    private val file=File(context.noBackupFilesDir,"qa-account-$fixture.enc")
    private val alias="magina-qa-account-$fixture"
    private val scope="synthetic-project"
    private fun store(binding:String=scope)=AndroidAccountSessionStore(file,alias,binding)
    private val session=AuthSession(UUID.randomUUID(),"qa@example.com","qa-private-access","qa-private-refresh",Instant.now().plusSeconds(3600))
    @Before fun requireOwnEmulator() {
        assertEquals("com.isivoltpro.maginaolivo.dev",context.packageName)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
            .bufferedReader().use { assertEquals("1",it.readText().trim()) }
        assertEquals(context.noBackupFilesDir.canonicalFile,file.canonicalFile.parentFile)
    }
    @After fun removeOnlyOwnCredentials() {
        check(file.name=="qa-account-$fixture.enc");store().clear()
        val keys=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(alias=="magina-qa-account-$fixture");if(keys.containsAlias(alias))keys.deleteEntry(alias)
    }
    @Test fun encryptedSessionAndPkceSurviveReopeningWithoutPlaintext() {
        val pending=PendingAuthLink(session.email,LinkPurpose.RECOVERY,"qa-private-verifier"+"x".repeat(30),Instant.now())
        store().write(AccountRecord(session=session,pending=pending))
        assertTrue("Encrypted credential file must exist",file.exists())
        val raw=file.readBytes().toString(Charsets.UTF_8)
        assertFalse(raw.contains("qa-private"));assertFalse(raw.contains("qa@example.com"))
        val restored=store().read()
        assertEquals(session.userId,restored.session!!.userId);assertEquals(session.email,restored.session!!.email)
        assertEquals(LinkPurpose.RECOVERY,restored.pending!!.purpose)
        store().clear();assertFalse(file.exists());assertNull(store().read().session)
    }
    @Test fun tamperingFailsClosedWithoutReturningCachedIdentity() {
        store().write(AccountRecord(session=session))
        val bytes=file.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
        try { store().read();fail("Tampering must not restore a session") } catch(e:AuthFailure) { assertEquals(AuthFailure.Kind.STORAGE,e.kind) }
    }
    @Test fun ciphertextCannotBeImportedUnderAnotherProject() {
        store().write(AccountRecord(session=session))
        try { store("other-project").read();fail("Project binding must be authenticated") } catch(e:AuthFailure) { assertEquals(AuthFailure.Kind.STORAGE,e.kind) }
    }
}

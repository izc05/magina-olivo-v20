package com.isivoltpro.maginaolivo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.feature.account.*
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Manual DEV phases across real email callbacks/process restarts; never enabled in CI. */
class AccountAuthDevJourneyTest {
    private enum class Phase { REGISTER, CONFIRMED, LOGIN, RESTORE_OFFLINE_LOGOUT, RECOVER, RESET_LOGIN_LOGOUT }
    private class Input(val phase:Phase,val email:String,val password:String,val replacement:String) {
        override fun toString()="PrivateDevInput(redacted)"
    }

    @Test fun realDevAccountJourneyPhase() = runBlocking {
        val project="zubpicdibqbmewxeurhm"
        assumeTrue("Email journey requires explicit DEV opt-in and private local input",
            InstrumentationRegistry.getArguments().getString("realDevAuthProject")==project)
        val context=ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.isivoltpro.maginaolivo.dev",context.packageName)
        assertTrue(BuildConfig.DEBUG && BuildConfig.ENVIRONMENT=="DEV")
        assertEquals("https://$project.supabase.co",BuildConfig.AUTH_PROJECT_URL)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("getprop ro.kernel.qemu")).bufferedReader().use {
                assertEquals("1",it.readText().trim())
            }
        val file=File(context.noBackupFilesDir,"qa-account-e2e-input.json")
        check(file.isFile) { "Private local QA input required" }
        val text=try {
            check(file.length()<=8192) { "Private QA input too large" }
            file.readText()
        } finally { check(file.delete()) { "Private QA input could not be removed" } }
        // No parser exception may echo the private JSON/password in instrumentation output.
        val input=runCatching {
            val json=Json.parseToJsonElement(text).jsonObject
            Input(Phase.valueOf(json.getValue("phase").jsonPrimitive.content),
                json.getValue("email").jsonPrimitive.content,
                json["password"]?.jsonPrimitive?.content.orEmpty(),
                json["replacement"]?.jsonPrimitive?.content.orEmpty())
        }.getOrNull() ?: error("Invalid private QA input")
        check(AccountValidation.email(input.email)) { "Invalid QA email" }
        if(input.phase in setOf(Phase.REGISTER,Phase.LOGIN,Phase.RESET_LOGIN_LOGOUT)) {
            check(input.password.length in 16..128) { "Strong synthetic QA password required" }
        }
        if(input.phase==Phase.RESET_LOGIN_LOGOUT) {
            check(input.replacement.length in 16..128 && input.replacement!=input.password) { "New synthetic QA password required" }
        }
        val before=snapshot(context)
        val baseline=File(context.noBackupFilesDir,"qa-account-e2e-agriculture-baseline.json")
        if(baseline.exists()) assertEquals("Agriculture changed between DEV phases",baseline.readText(),before)
        else {
            check(input.phase==Phase.REGISTER) { "Start with REGISTER to record the agriculture baseline" }
            baseline.writeText(before)
        }
        val config=checkNotNull(AuthConfiguration.forEnvironment("DEV",BuildConfig.AUTH_PROJECT_URL,
            BuildConfig.AUTH_PUBLIC_KEY,context.packageName))
        val api=SupabaseAuthApi(config,HttpsAuthTransport(config))
        val store=AndroidAccountSessionStore(File(context.noBackupFilesDir,"account-auth-$project.enc"),
            "magina-account-$project-v1","${context.packageName}|${config.projectUrl}")
        val repo=AccountRuntime.get(context)
        fun healthy() { assertNull("Sanitized DEV Auth error",repo.state.value.error);assertFalse(repo.state.value.simulated) }
        fun identity() { healthy();assertTrue("Unexpected DEV identity",input.email.equals(repo.state.value.email,true)) }
        suspend fun rejectedPassword() {
            val result=runCatching { api.signIn(input.email,input.password) }
            result.getOrNull()?.let { api.signOut(it);fail("Old password was accepted") }
            assertEquals(AuthFailure.Kind.CREDENTIALS,(result.exceptionOrNull() as? AuthFailure)?.kind)
        }
        suspend fun logoutAndCheck() {
            val old=checkNotNull(store.read().session)
            repo.logout(true);healthy();assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status)
            assertNull(store.read().session)
            assertFalse("Remote revocation unconfirmed",repo.state.value.message.orEmpty().contains("No se pudo confirmar",true))
            val refresh=runCatching { api.refresh(old) }
            refresh.getOrNull()?.let { api.signOut(it);fail("Revoked refresh token remained usable") }
            assertEquals(AuthFailure.Kind.EXPIRED,(refresh.exceptionOrNull() as? AuthFailure)?.kind)
        }
        try {
            when(input.phase) {
                Phase.REGISTER -> {
                    repo.resume(false);assertNull("Existing local session must not be replaced",repo.state.value.email)
                    repo.register(input.email,input.password,true);healthy()
                    assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status)
                    assertEquals(LinkPurpose.SIGNUP,store.read().pending?.purpose)
                    val login=runCatching { api.signIn(input.email,input.password) }
                    login.getOrNull()?.let { api.signOut(it);fail("Registration did not require email confirmation") }
                    assertEquals(AuthFailure.Kind.CONFIRM_EMAIL,(login.exceptionOrNull() as? AuthFailure)?.kind)
                }
                Phase.CONFIRMED -> {
                    repo.resume(true);identity();assertEquals(AccountStatus.SIGNED_IN,repo.state.value.status)
                    assertNotNull(store.read().session);assertNull(store.read().pending)
                }
                Phase.LOGIN -> {
                    repo.logout(true);healthy()
                    repo.login(input.email,input.password,true);identity()
                    assertEquals(AccountStatus.SIGNED_IN,repo.state.value.status);assertNotNull(store.read().session)
                }
                Phase.RESTORE_OFFLINE_LOGOUT -> {
                    repo.resume(false);identity();assertEquals(AccountStatus.OFFLINE,repo.state.value.status)
                    repo.resume(true);identity();assertEquals(AccountStatus.SIGNED_IN,repo.state.value.status)
                    logoutAndCheck()
                }
                Phase.RECOVER -> {
                    repo.resume(false);assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status)
                    repo.requestRecovery(input.email,true);healthy()
                    assertEquals(LinkPurpose.RECOVERY,store.read().pending?.purpose);assertNull(store.read().session)
                }
                Phase.RESET_LOGIN_LOGOUT -> {
                    repo.resume(true);identity();assertEquals(AccountStatus.RECOVERY,repo.state.value.status)
                    repo.updatePassword(input.replacement,true);healthy()
                    assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status)
                    rejectedPassword()
                    repo.login(input.email,input.replacement,true);identity()
                    assertEquals(AccountStatus.SIGNED_IN,repo.state.value.status)
                    logoutAndCheck()
                }
            }
            println("DEV Auth phase=${input.phase.name}: verified; agriculture unchanged")
        } finally { assertEquals("Agricultural tables/attachments changed",before,snapshot(context)) }
    }

    /** Read-only logical hashes, no Room creation/migration or data restoration. No row values logged. */
    private fun snapshot(context:Context):String {
        val path=context.getDatabasePath("magina-olivo.db")
        check(path.isFile) { "Open the DEV app before recording its existing agriculture baseline" }
        val tables=sortedMapOf<String,String>()
        SQLiteDatabase.openDatabase(path.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            val names=mutableListOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",null).use {
                while(it.moveToNext()) names+=it.getString(0)
            }
            names.forEach { name ->
                db.rawQuery("SELECT * FROM \"${name.replace("\"","\"\"")}\"",null).use { cursor ->
                    val rows=mutableListOf<String>()
                    while(cursor.moveToNext()) rows+=(0 until cursor.columnCount).joinToString("|") { i ->
                        val type=cursor.getType(i)
                        val value=when(type) { 0 -> "";4 -> Base64.getEncoder().encodeToString(cursor.getBlob(i));else -> cursor.getString(i) }
                        "$type:${value.length}:$value"
                    }
                    tables[name]="${rows.size}:${digest(rows.sorted().joinToString("\n").toByteArray())}"
                }
            }
        }
        val attachmentRoot=File(context.filesDir,"attachments")
        val files=attachmentRoot.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.map {
            "${it.name}:${it.length()}:${digest(it.readBytes())}"
        }
        return buildJsonObject {
            put("tables",buildJsonObject { tables.forEach { (table,hash) -> put(table,hash) } })
            put("attachmentCount",files.size);put("attachmentDigest",digest(files.joinToString("\n").toByteArray()))
        }.toString()
    }
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

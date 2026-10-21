package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.data.repository.*
import com.isivoltpro.maginaolivo.feature.account.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FarmLinkRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val fixture=UUID.randomUUID()
    private val databaseName="qa-g1b-$fixture.db"
    private val database=MaginaOlivoDatabase.create(context,databaseName)
    private val file=File(context.noBackupFilesDir,"qa-g1b-$fixture.json")
    private val photo=File(context.filesDir,"qa-g1b-$fixture.bin")
    private val store=AndroidFarmLinkLedger(file)
    private val authStore=MemoryAccountSessionStore()
    private val api=object:AccountAuthApi by SimulatedAccountAuthApi() { override val simulated=false }
    private val account=AccountRepository(api,authStore)
    private fun repo(auth:AccountRepository=account)=LocalFarmLinkRepository(context,database,auth,"fixture-project",store)
    @After fun cleanup() {
        runCatching { store.read()?.consents?.forEach { File(context.noBackupFilesDir,"account-link-backups/${it.backupId}").deleteRecursively() } }
        database.close();context.deleteDatabase(databaseName);photo.delete()
        listOf(file,File(file.path+".bak"),File(file.path+".new")).forEach { it.delete() }
    }
    private suspend fun seed() {
        account.login("a@example.test","password42",true)
        val db=database.openHelper.writableDatabase
        val workspace=UUID.randomUUID().toString()
        db.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES (?,'QA','local-owner','ES','Europe/Madrid','es-ES','EUR',1,1,1,'LOCAL_ONLY')",arrayOf(workspace))
        repeat(3) { i -> db.execSQL("INSERT INTO farms (id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES (?,? ,?,'ACTIVE',1,1,3,'LOCAL_ONLY')",arrayOf(UUID.randomUUID().toString(),workspace,"Finca $i")) }
        photo.writeBytes(byteArrayOf(2,0,8,3))
        db.execSQL("INSERT INTO documents (id,workspace_id,owner_type,owner_id,type,mime_type,display_name,local_uri,upload_status,created_at,updated_at,version,sync_status) VALUES (?,?, 'WORKSPACE',?,'PHOTO','application/octet-stream','QA',?,'LOCAL_ONLY',1,1,1,'LOCAL_ONLY')",arrayOf(UUID.randomUUID().toString(),workspace,workspace,photo.toURI().toString()))
    }
    @Test fun selectsTwoOfThreeWithRealBackupAndKeepsHistoricalOwnerAndOfflineAccess()=runBlocking {
        seed();val repository=repo();val id=account.state.value.userId!!
        val before=fingerprint()
        val all=repository.choices();val selected=all.take(2)
        try { repository.consent(id,selected,false);fail("Consent required") } catch (_:IOException) { }
        assertNull(store.read())
        val result=repository.consent(id,selected,true)
        assertEquals(selected.map { it.id }.toSet(),result.farmIds)
        assertEquals(3,all.size)
        val copied=File(context.noBackupFilesDir,"account-link-backups/${result.consents.single().backupId}/files/0")
        assertArrayEquals(photo.readBytes(),copied.readBytes())
        assertEquals(1,repository.consent(id,selected,true).consents.size)
        database.openHelper.readableDatabase.query("SELECT owner_user_id FROM workspaces").use { assertTrue(it.moveToFirst());assertEquals("local-owner",it.getString(0)) }
        account.resume(false);assertTrue(repository.accessAllowed())
        val restored=AccountRepository(api,authStore);restored.resume(false)
        assertTrue(repo(restored).accessAllowed())
        assertEquals(result,store.read())
        assertEquals(before,fingerprint())
    }
    @Test fun logoutAndOtherAccountCannotReadPreviewOrReassignAnyFarm()=runBlocking {
        seed();val repository=repo();val selected=repository.choices().take(1)
        val id=account.state.value.userId!!;repository.consent(id,selected,true)
        account.logout(false);assertFalse(repository.accessAllowed())
        account.login("b@example.test","password42",true);assertFalse(repository.accessAllowed())
        try { repository.choices();fail("Other account must not see farm names") } catch (_:IOException) { }
        try { repository.consent(id,selected,true);fail("Stale A selection must not bind B") } catch (_:IOException) { }
        assertEquals(1,store.read()!!.consents.size)
        account.logout(false);account.login("a@example.test","password42",true);account.resume(false)
        assertTrue(repository.accessAllowed());assertEquals(3,repository.choices().size)
    }
    @Test fun changedFarmAndMissingPhotoDoNotCommitConsent()=runBlocking {
        seed();val repository=repo();val all=repository.choices();val id=account.state.value.userId!!
        database.openHelper.writableDatabase.execSQL("UPDATE farms SET version=4")
        try { repository.consent(id,all,true);fail("Stale preview") } catch (_:IOException) { }
        assertNull(store.read())
        val fresh=repository.choices();photo.delete()
        try { repository.consent(id,fresh,true);fail("Missing bytes") } catch (_:IOException) { }
        assertNull(store.read());assertEquals(3,repository.choices().size)
    }
    @Test fun firstConsentAndLogoutCannotRaceAnUnboundBackgroundPublication()=runBlocking {
        seed();val repository=repo();val all=repository.choices();val id=account.state.value.userId!!
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val events=mutableListOf<String>()
        val background=async(start=CoroutineStart.UNDISPATCHED) {
            FarmLinkDeviceAccess.runIfAllowed(store,account,"fixture-project",false) {
                entered.complete(Unit);release.await();events.add("published");true
            }
        }
        entered.await()
        val consent=async(start=CoroutineStart.UNDISPATCHED) { repository.consent(id,all.take(1),true);events.add("consent") }
        val logout=async(start=CoroutineStart.UNDISPATCHED) { account.logout(false);events.add("logout") }
        assertFalse(consent.isCompleted);assertFalse(logout.isCompleted)
        release.complete(Unit);assertTrue(background.await());consent.await();logout.await()
        assertEquals(listOf("published","consent","logout"),events)
        assertFalse(FarmLinkDeviceAccess.runIfAllowed(store,account,"fixture-project",false) { fail("Private notification after logout");true })
    }
    @Test fun brokenCachedCredentialsDenyBackgroundAccessWithoutCrashing()=runBlocking {
        val broken=object:AccountSessionStore {
            override fun read():AccountRecord=throw AuthFailure(AuthFailure.Kind.STORAGE)
            override fun write(record:AccountRecord)=Unit
            override fun clear()=Unit
        }
        val auth=AccountRepository(api,broken)
        assertFalse(FarmLinkDeviceAccess.runIfAllowed(store,auth,"fixture-project",false) { fail("No private access with broken identity");true })
    }

    @Test fun g23OutboxRespectsG1bConsentAndBlocksOnRevocation()=runBlocking {
        seed()
        val repository = repo()
        val id = account.state.value.userId!!
        val choices = repository.choices()
        val consentedFarm = choices[0]
        val unconsentedFarm = choices[1]

        // Only consentedFarm is consented
        repository.consent(id, listOf(consentedFarm), true)
        assertTrue(repository.isFarmConsented(consentedFarm.id))
        assertFalse(repository.isFarmConsented(unconsentedFarm.id))

        // Enqueue operation for consented farm
        val opConsentedId = UUID.randomUUID()
        database.syncOutboxDao().insert(
            com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity(
                id = opConsentedId,
                entityType = com.isivoltpro.maginaolivo.data.local.model.SyncEntityType.DELIVERY,
                entityId = UUID.randomUUID(),
                operation = com.isivoltpro.maginaolivo.data.local.model.OutboxOperation.CREATE,
                payloadVersion = 1,
                status = com.isivoltpro.maginaolivo.data.local.model.OutboxStatus.PENDING,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                accountId = id.toString(),
                farmId = consentedFarm.id,
                payloadJson = "{\"test\":true}"
            )
        )

        // Enqueue operation for unconsented farm
        val opUnconsentedId = UUID.randomUUID()
        database.syncOutboxDao().insert(
            com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity(
                id = opUnconsentedId,
                entityType = com.isivoltpro.maginaolivo.data.local.model.SyncEntityType.DELIVERY,
                entityId = UUID.randomUUID(),
                operation = com.isivoltpro.maginaolivo.data.local.model.OutboxOperation.CREATE,
                payloadVersion = 1,
                status = com.isivoltpro.maginaolivo.data.local.model.OutboxStatus.PENDING,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                accountId = id.toString(),
                farmId = unconsentedFarm.id,
                payloadJson = "{\"test\":false}"
            )
        )

        val pendingConsented = database.syncOutboxDao().listPendingForFarm(consentedFarm.id)
        assertEquals(1, pendingConsented.size)
        assertEquals(opConsentedId, pendingConsented[0].id)

        // Revoke consent for consented farm -> pending operations become BLOCKED
        repository.revokeConsentForFarm(consentedFarm.id)
        val afterRevocation = database.syncOutboxDao().findById(opConsentedId)
        assertNotNull(afterRevocation)
        assertEquals(com.isivoltpro.maginaolivo.data.local.model.OutboxStatus.BLOCKED, afterRevocation!!.status)
    }

    private fun fingerprint(): String {
        val db=database.openHelper.readableDatabase
        val tables=mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { c -> while(c.moveToNext()) tables.add(c.getString(0)) }
        return tables.joinToString("\n") { table ->
            db.query("SELECT * FROM \"$table\"").use { c ->
                val rows=mutableListOf<String>()
                while(c.moveToNext()) rows.add((0 until c.columnCount).joinToString { i ->
                    val value=when(c.getType(i)) { android.database.Cursor.FIELD_TYPE_NULL -> "NULL"; android.database.Cursor.FIELD_TYPE_BLOB -> java.util.Base64.getEncoder().encodeToString(c.getBlob(i)); else -> c.getString(i) }
                    "${c.getType(i)}:${value.length}:$value"
                })
                "$table:${rows.sorted()}"
            }
        }
    }
}

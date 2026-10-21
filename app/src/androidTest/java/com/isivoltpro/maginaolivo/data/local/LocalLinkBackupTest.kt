package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.data.repository.LocalLinkBackup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.system.ErrnoException
import android.system.OsConstants
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/** Synthetic database and private bytes only; never opens the application's database. */
class LocalLinkBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = UUID.randomUUID().toString()
    private val databaseName = "qa-link-$fixture.db"
    private val database = MaginaOlivoDatabase.create(context, databaseName)
    private val photo = File(context.filesDir, "qa-link-$fixture.jpg")
    private val operations = mutableListOf<UUID>()
    private fun operation() = UUID.randomUUID().also { operations.add(it) }
    private fun service() = LocalLinkBackup(context, database)

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(databaseName)
        photo.delete()
        operations.forEach { File(context.noBackupFilesDir, "account-link-backups/$it").deleteRecursively() }
    }

    private fun seed(uri: String = photo.toURI().toString()) {
        photo.writeBytes(byteArrayOf(1, 8, 0, 42, -1))
        val db = database.openHelper.writableDatabase
        db.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('w','Olivar','local-owner','ES','Europe/Madrid','es-ES','EUR',1,1,1,'LOCAL_ONLY')")
        db.execSQL("INSERT INTO farms (id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('f','w','Finca','ACTIVE',1,1,7,'PENDING')")
        db.execSQL("INSERT INTO campaigns (id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status) VALUES ('c','w','f','2025/2026','2025-10-01','PREPARATION',1,1,1,'LOCAL_ONLY')")
        db.execSQL("INSERT INTO deliveries (id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,ticket_number,source,created_at,updated_at,version,sync_status) VALUES ('d','w','f','c','2025-11-20','Cooperativa',3500000,'T-101','MANUAL',1,1,1,'LOCAL_ONLY')")
        db.execSQL("INSERT INTO harvests (id,workspace_id,campaign_id,farm_id,harvest_date,weight_grams,created_at,updated_at,version,sync_status) VALUES ('h','w','c','f','2025-11-20',3500000,1,1,1,'LOCAL_ONLY')")
        db.execSQL("INSERT INTO harvest_labour (id,workspace_id,harvest_id,quantity,unit,created_at,updated_at,version,sync_status) VALUES ('l','w','h',4,'DAY',1,1,1,'LOCAL_ONLY')")
        db.execSQL("INSERT INTO expenses (id,workspace_id,campaign_id,farm_id,expense_date,concept,category,amount_minor,currency,status,origin,created_at,updated_at,version,sync_status) VALUES ('e','w','c','f','2025-11-20','Gasóleo','FUEL',8500,'EUR','POSTED','MANUAL',1,1,1,'LOCAL_ONLY')")
        db.execSQL("INSERT INTO documents (id,workspace_id,owner_type,owner_id,type,mime_type,display_name,local_uri,upload_status,created_at,updated_at,version,sync_status) VALUES ('d','w','FARM','f','PHOTO','image/jpeg','Foto',?,'LOCAL_ONLY',1,1,1,'LOCAL_ONLY')", arrayOf(uri))
    }

    @Test fun snapshotReopensWithOriginalIdsVersionsAndNonEmptyPhoto() = runBlocking {
        seed()
        val receipt = service().capture(operation())
        assertTrue(receipt.directory.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
        SQLiteDatabase.openDatabase(File(receipt.directory, "room.db").path, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
            copy.rawQuery("SELECT id,owner_user_id FROM workspaces", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("w", c.getString(0)); assertEquals("local-owner", c.getString(1))
            }
            copy.rawQuery("SELECT id,version,sync_status FROM farms", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("f", c.getString(0)); assertEquals(7, c.getInt(1)); assertEquals("PENDING", c.getString(2))
            }
            assertEquals(MaginaOlivoDatabase.VERSION, copy.version)
            copy.rawQuery("SELECT net_grams,ticket_number FROM deliveries WHERE id='d'", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(3500000L,c.getLong(0)); assertEquals("T-101",c.getString(1))
            }
            copy.rawQuery("SELECT amount_minor FROM expenses WHERE id='e'", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(8500L,c.getLong(0))
            }
            copy.rawQuery("SELECT quantity FROM harvest_labour WHERE id='l'", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(4.0,c.getDouble(0),0.0)
            }
        }
        assertArrayEquals(photo.readBytes(), File(receipt.directory, "files/0").readBytes())
        assertEquals(1, receipt.documentCount)
        assertTrue(receipt.tableCounts.containsKey("harvest_labour"))
        assertTrue(receipt.tableCounts.containsKey("sync_outbox"))
        assertEquals(1L, receipt.tableCounts["farms"])
        assertEquals(1, database.openHelper.readableDatabase.query("SELECT * FROM farms").use { it.count })
    }

    @Test fun retryAfterServiceRecreationKeepsImmutableCheckpoint() = runBlocking {
        seed()
        val id = operation()
        val first = service().capture(id)
        val hash = File(first.directory, "manifest.json").readBytes()
        database.openHelper.writableDatabase.execSQL("UPDATE farms SET version=8")
        val retry = service().capture(id)
        assertEquals(first.directory, retry.directory)
        assertArrayEquals(hash, File(retry.directory, "manifest.json").readBytes())
        assertEquals(1, first.directory.parentFile!!.listFiles()!!.count { it.name == id.toString() })
    }

    @Test fun missingAttachmentDoesNotPublishBackupOrMutateSource() = runBlocking {
        seed(); photo.delete()
        val id = operation()
        try { service().capture(id); fail("Missing bytes must stop linking") } catch (_: java.io.IOException) { }
        assertFalse(File(context.noBackupFilesDir, "account-link-backups/$id").exists())
        assertEquals(1, database.openHelper.readableDatabase.query("SELECT * FROM documents").use { it.count })
    }

    @Test fun corruptCompletedCopyIsRejectedOnRetry() = runBlocking {
        seed()
        val id = operation()
        val copy = service().capture(id)
        File(copy.directory, "files/0").writeBytes(byteArrayOf(5))
        try { service().capture(id); fail("Corrupt checkpoint must not be trusted") } catch (_: java.io.IOException) { }
        assertArrayEquals(byteArrayOf(1, 8, 0, 42, -1), photo.readBytes())
    }

    @Test fun omittedManifestDocumentCannotHideMissingBytes() = runBlocking {
        seed()
        val id = operation()
        val copy = service().capture(id)
        val manifest = File(copy.directory, "manifest.json")
        manifest.writeText(JSONObject(manifest.readText()).put("documents", JSONArray()).toString())
        File(copy.directory, "files/0").delete()
        try { service().capture(id); fail("Inventory must match copied Room") } catch (_: java.io.IOException) { }
    }

    @Test fun changedOperationIdentityIsRejected() = runBlocking {
        seed()
        val id = operation()
        val copy = service().capture(id)
        val manifest = File(copy.directory, "manifest.json")
        manifest.writeText(JSONObject(manifest.readText()).put("operationId", UUID.randomUUID().toString()).toString())
        try { service().capture(id); fail("Operation identity must match") } catch (_: java.io.IOException) { }
    }

    @Test fun concurrentRetryPublishesOnlyOneVerifiedCheckpoint() = runBlocking {
        seed()
        val id = operation()
        val copies = List(2) { async(Dispatchers.IO) { service().capture(id) } }.awaitAll()
        assertEquals(copies[0].directory, copies[1].directory)
        assertEquals(1, copies[0].directory.parentFile!!.listFiles()!!.count { it.name == id.toString() })
        assertFalse(copies[0].directory.parentFile!!.listFiles()!!.any { it.name.startsWith(".$id-") })
    }

    @Test fun wrongStoredSizeStopsBeforePublishing() = runBlocking {
        seed()
        database.openHelper.writableDatabase.execSQL("UPDATE documents SET file_size_bytes=999")
        val id = operation()
        try { service().capture(id); fail("Metadata mismatch must stop linking") } catch (_: java.io.IOException) { }
        assertFalse(File(context.noBackupFilesDir, "account-link-backups/$id").exists())
        assertArrayEquals(byteArrayOf(1, 8, 0, 42, -1), photo.readBytes())
    }

    @Test fun diskFullMidFileAndManifestPreservesSourceAndExistingCheckpoint() = runBlocking {
        seed()
        val kept = service().capture(operation())
        val keptManifest = File(kept.directory, "manifest.json").readBytes()
        for (failAt in listOf("0", "manifest.json")) {
            val id = operation()
            val fullDisk = LocalLinkBackup(context, database) { file ->
                object : FileOutputStream(file) {
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        if (file.name == failAt) {
                            super.write(bytes, offset, minOf(2, length))
                            // Inject the exact errno: SELinux denies /dev/full in the CI app domain.
                            throw IOException("injected_full_volume", ErrnoException("write", OsConstants.ENOSPC))
                        }
                        super.write(bytes, offset, length)
                    }
                    override fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)
                }
            }
            try { fullDisk.capture(id); fail("ENOSPC must not publish a checkpoint") }
            catch (error: IOException) { assertEquals(OsConstants.ENOSPC, (error.cause as ErrnoException).errno) }
            assertFalse(File(context.noBackupFilesDir, "account-link-backups/$id").exists())
            assertFalse(kept.directory.parentFile!!.listFiles()!!.any { it.name.startsWith(".$id-") })
            assertArrayEquals(keptManifest, File(kept.directory, "manifest.json").readBytes())
            assertArrayEquals(byteArrayOf(1, 8, 0, 42, -1), photo.readBytes())
            assertEquals(1, service().capture(id).documentCount)
        }
    }
}

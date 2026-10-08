package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.repository.AndroidAttachmentFileStore
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Contract fixtures must never remove another database's photos or camera inputs. */
@RunWith(AndroidJUnit4::class)
class AttachmentFixtureIsolationTest {
    @Test fun expenseFixturePreservesUnrelatedFiles() {
        val fixture = ExpenseLedgerContractTest()
        preservesUnrelatedFiles({ fixture.before() }, { fixture.after() })
    }

    @Test fun deliveryFixturePreservesUnrelatedFiles() {
        val fixture = DeliveryContractTest()
        preservesUnrelatedFiles({ fixture.before() }, { fixture.after() })
    }

    @Test fun attachmentFixturePreservesUnrelatedFiles() {
        val fixture = AttachmentContractTest()
        preservesUnrelatedFiles({ fixture.before() }, { fixture.after() })
    }

    @Test fun farmCoverFixturePreservesUnrelatedFiles() {
        val fixture = OfflineFirstFarmCoverRepositoryTest()
        preservesUnrelatedFiles({ fixture.clearDatabase() }, { fixture.cleanUp() })
    }

    private fun preservesUnrelatedFiles(setUp: () -> Unit, tearDown: () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "fixture-isolation-${UUID.randomUUID()}.jpg"
        val files = listOf(
            File(File(context.filesDir, AndroidAttachmentFileStore.ROOT_DIRECTORY), name),
            File(File(context.cacheDir, "camera"), name),
        )
        files.forEach { it.parentFile!!.mkdirs(); it.writeText("unrelated-photo") }
        try {
            try {
                setUp()
                files.forEach { assertEquals("Unrelated file removed by fixture setup", "unrelated-photo", it.takeIf(File::exists)?.readText()) }
            } finally {
                tearDown()
            }
            files.forEach { assertEquals("Unrelated file removed by fixture teardown", "unrelated-photo", it.takeIf(File::exists)?.readText()) }
        } finally {
            files.forEach(File::delete)
        }
    }
}

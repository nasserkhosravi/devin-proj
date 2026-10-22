package com.khosravi.devin.write

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.write.testing.DevinTestFixture
import com.khosravi.devin.write.testing.DevinTestFixture.WRITE_TIMEOUT_MS
import com.khosravi.devin.write.testing.DevinTestFixture.onWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** How [DevinTool.init] and [LogCore] use [TrustedPresenter]s and [PresenterVerifier]. */
@RunWith(RobolectricTestRunner::class)
class TrustedPresenterInitTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val digest = "9be88a48039809b66845d6fd375fedb45f51960331eb182018ca1b39593c00b8"

    @Before
    fun setUp() {
        DevinTestFixture.resetSingletons()
        DevinTestFixture.registerProvider()
    }

    @After
    fun tearDown() {
        DevinTestFixture.resetSingletons()
    }

    private fun installPresenter(packageName: String) {
        shadowOf(context.packageManager).addOrUpdateProvider(ProviderInfo().apply {
            authority = DevinTestFixture.AUTHORITY
            this.packageName = packageName
            name = DevinContentProvider::class.java.name
        })
    }

    @Test
    fun `init with an untrusted presenter sends nothing, not even the client registration`() {
        installPresenter("com.impostor.presenter")

        onWorker {
            DevinTool.init(context, true, null, listOf(TrustedPresenter("com.example.presenter", digest)))
        }

        val tool = DevinTool.get()!!
        assertEquals(false, tool.isProviderInstalled())
        assertNull(tool.logger)
        assertNull(tool.imageLogger)
        assertTrue(DevinTestFixture.clients(context).isEmpty())
    }

    @Test
    fun `init with no trusted presenters keeps today's behavior`() {
        installPresenter("com.any.presenter")

        onWorker { DevinTool.init(context, true, null, emptyList()) }

        val tool = DevinTool.get()!!
        assertEquals(true, tool.isProviderInstalled())
        assertNotNull(tool.logger)
        assertEquals(context.packageName, DevinTestFixture.clients(context).single().first)
    }

    @Test
    fun `LogCore drops logs when the presenter isn't trusted`() {
        installPresenter("com.example.presenter")
        val core = LogCore(context, true, PresenterVerifier(context, listOf(TrustedPresenter("com.example.presenter", digest))) { _, _, _ -> false })

        onWorker { core.insertLogAsync("t", "not sent", typeId = null) }
        core.awaitPendingWrites(WRITE_TIMEOUT_MS)

        assertTrue(DevinTestFixture.logs(context).isEmpty())
    }

    @Test
    fun `LogCore sends logs when the presenter is trusted`() {
        installPresenter("com.example.presenter")
        val core = LogCore(context, true, PresenterVerifier(context, listOf(TrustedPresenter("com.example.presenter", digest))) { _, _, _ -> true })

        onWorker { core.insertLogAsync("t", "sent", typeId = null) }
        core.awaitPendingWrites(WRITE_TIMEOUT_MS)

        assertEquals(listOf("sent"), DevinTestFixture.logs(context).map { it.value })
    }
}

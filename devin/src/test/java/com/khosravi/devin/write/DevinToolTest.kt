package com.khosravi.devin.write

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.write.testing.DevinTestFixture
import com.khosravi.devin.write.testing.DevinTestFixture.onWorker
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevinToolTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        DevinTestFixture.resetSingletons()
    }

    @After
    fun tearDown() {
        DevinTestFixture.resetSingletons()
    }

    /** Stands in for a device without a presenter: the real resolver throws "Unknown URL" there. */
    class MissingPresenterProvider : ContentProvider() {
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri = throw IllegalArgumentException("Unknown URL $uri")
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    private fun providerComponentState() = context.packageManager.getComponentEnabledSetting(
        ComponentName(context.packageName, DevinContentProvider::class.java.name)
    )

    @Test
    fun `enabled init with a presenter registers the client`() {
        DevinTestFixture.registerProvider()
        val config = DevinTool.PresenterConfigBuilder().logPassword("secret").build()

        onWorker { DevinTool.init(context, isEnable = true, presenterConfig = config) }

        val tool = DevinTool.get()!!
        assertEquals(true, tool.isProviderInstalled())
        assertNotNull(tool.logger)
        assertNotNull(tool.imageLogger)
        val (packageId, storedConfig) = DevinTestFixture.clients(context).single()
        assertEquals(context.packageName, packageId)
        assertEquals("secret", JSONObject(storedConfig!!).getString("logPassword"))
    }

    @Test
    fun `enabled init's logger writes to the presenter`() {
        DevinTestFixture.registerProvider()
        onWorker { DevinTool.init(context, isEnable = true) }

        onWorker { DevinTool.get()!!.logger!!.info("tag", "hello") }

        assertEquals(listOf("hello"), DevinTestFixture.logs(context).map { it.value })
    }

    @Test
    fun `enabled init without a presenter reports it missing`() {
        Robolectric.setupContentProvider(MissingPresenterProvider::class.java, DevinTestFixture.AUTHORITY)

        onWorker { DevinTool.init(context, isEnable = true) }

        val tool = DevinTool.get()!!
        assertEquals(false, tool.isProviderInstalled())
        assertNull(tool.logger)
        assertNull(tool.imageLogger)
    }

    @Test
    fun `disabled init has no loggers, unknown state and disables the provider component`() {
        DevinTool.init(context, isEnable = false)

        val tool = DevinTool.get()!!
        assertNull(tool.isProviderInstalled())
        assertNull(tool.logger)
        assertNull(tool.imageLogger)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, providerComponentState())
    }

    @Test
    fun `a second init is ignored`() {
        DevinTool.init(context, isEnable = false)
        val first = DevinTool.get()

        DevinTestFixture.registerProvider()
        onWorker { DevinTool.init(context, isEnable = true) }

        assertSame(first, DevinTool.get())
        assertNull(DevinTool.get()!!.isProviderInstalled())
    }

    @Test
    fun `connectPlugin hands out the enabled log core`() {
        DevinTestFixture.registerProvider()
        onWorker { DevinTool.init(context, isEnable = true) }
        var core: DevinLogCore? = null

        DevinTool.get()!!.connectPlugin { core = it }

        assertTrue(core!!.isEnable())
    }

    @Test
    fun `connectPlugin does nothing when disabled`() {
        DevinTool.init(context, isEnable = false)
        var called = false

        DevinTool.get()!!.connectPlugin { called = true }

        assertEquals(false, called)
    }

    @Test
    fun `init without isEnable follows the debuggable flag`() {
        DevinTestFixture.registerProvider()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        onWorker { DevinTool.init(context) }

        assertEquals(true, DevinTool.get()!!.isProviderInstalled())
    }

    @Test
    fun `init without isEnable is disabled for a non-debuggable app`() {
        context.applicationInfo.flags = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()

        DevinTool.init(context)

        assertNull(DevinTool.get()!!.isProviderInstalled())
    }
}

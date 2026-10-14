package com.khosravi.devin.present.retention

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.present.testing.DevinDbFixture
import com.khosravi.devin.present.testing.DevinDbFixture.insertLog
import com.khosravi.devin.present.testing.DevinDbFixture.logCount
import com.khosravi.devin.read.DevinUriHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RetentionObserverTest {

    private lateinit var context: Context
    private lateinit var appPref: AppPref
    private lateinit var db: SupportSQLiteDatabase
    private lateinit var observer: RetentionObserver

    private val oldDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
    private val logChangeUri = DevinUriHelper.getLogChangeUri(1, "a", "tag")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appPref = AppPref(context)
        db = DevinDbFixture.db(context)
        observer = RetentionObserver(context, appPref)
    }

    @After
    fun tearDown() {
        awaitTrims()
        DevinDbFixture.resetSingleton()
    }

    /** Waits for every trim already handed to the observer's single-thread executor. */
    private fun awaitTrims() {
        val field = RetentionObserver::class.java.getDeclaredField("executor").apply { isAccessible = true }
        (field.get(observer) as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
    }

    private fun notifyAndWait(uri: android.net.Uri = logChangeUri) {
        observer.onChange(false, uri)
        awaitTrims()
    }

    @Test
    fun `log change notification trims with the stored policy`() {
        insertLog(db, "a", oldDate)
        insertLog(db, "a", System.currentTimeMillis())

        notifyAndWait()

        assertEquals(1, logCount(db))
    }

    @Test
    fun `ignores notifications that are not a single log change`() {
        insertLog(db, "a", oldDate)

        notifyAndWait(DevinUriHelper.getLogListUri())
        observer.onChange(false, null)
        awaitTrims()

        assertEquals(1, logCount(db))
    }

    @Test
    fun `does nothing when retention is disabled`() {
        appPref.retentionEnabled = false
        insertLog(db, "a", oldDate)

        notifyAndWait()

        assertEquals(1, logCount(db))
    }

    @Test
    fun `second notification within the throttle window does not trim`() {
        notifyAndWait()
        insertLog(db, "a", oldDate)

        notifyAndWait()

        assertEquals(1, logCount(db))
    }

    @Test
    fun `resetThrottle lets the next notification trim with new settings`() {
        notifyAndWait()
        repeat(5) { insertLog(db, "a", System.currentTimeMillis() - it) }
        appPref.retentionMaxCount = 2

        observer.resetThrottle()
        notifyAndWait()

        assertEquals(2, logCount(db))
    }

    @Test
    fun `disabled check does not consume the throttle`() {
        appPref.retentionEnabled = false
        notifyAndWait()
        appPref.retentionEnabled = true
        insertLog(db, "a", oldDate)

        notifyAndWait()

        assertEquals(0, logCount(db))
    }
}

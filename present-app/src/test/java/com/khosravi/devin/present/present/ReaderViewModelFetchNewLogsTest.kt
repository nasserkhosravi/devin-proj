package com.khosravi.devin.present.present

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.present.data.CacheRepository
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.data.FilterUsageRepository
import com.khosravi.devin.present.data.UserSettings
import com.khosravi.devin.present.data.model.PageInfo
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.CalendarType
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.testing.DevinDbFixture
import com.khosravi.devin.present.testing.ProviderFixture
import com.khosravi.devin.present.tool.NotEmptyString
import com.khosravi.devin.read.DevinLogFlagsApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.Calendar

/** [ReaderViewModel.fetchNewLogs] over the real provider: what the "new logs" pull adds on top of the shown list. */
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelFetchNewLogsTest {

    private lateinit var app: Application
    private lateinit var calendar: CalendarProxy
    private lateinit var vm: ReaderViewModel

    private val day1 = noon(2026, Calendar.NOVEMBER, 10)
    private val day2 = noon(2026, Calendar.NOVEMBER, 11)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        ProviderFixture.register()
        ProviderFixture.putClient(app, CLIENT)
        CacheRepository(app).setSelectedClientId(ClientData(CLIENT, null))
        calendar = CalendarProxy(CalendarType.GREGORIAN)
        vm = ReaderViewModel(
            app, calendar, FilterRepository(app), FilterUsageRepository(app), CacheRepository(app), UserSettings(), AppPref(app),
        )
    }

    @After
    fun tearDown() {
        DevinDbFixture.resetSingleton()
    }

    private fun put(value: String, date: Long, tag: String = "app") = ProviderFixture.putLog(app, CLIENT, tag, value, date)

    private fun fetch(filter: FilterItem, search: String?, newerThanId: Long) =
        runBlocking { vm.fetchNewLogs(filter, search, newerThanId).first() }

    private fun header(date: Long) = "date:" + calendar.initIfNeed(DatePresent(date)).getFormatted()

    private fun List<LogItemData>.shape(): List<String> = map {
        when (it) {
            is DateLogItemData -> "date:" + it.presentDate.getFormatted()
            is TextLogItemData -> it.text
            is SessionStartLogItemData -> "session"
            else -> it.javaClass.simpleName
        }
    }

    @Test
    fun `returns only logs newer than the id, newest first, under a date header`() {
        put("old1", day1)
        val lastShown = put("old2", day1 + 1)
        put("new1", day2)
        put("new2", day2 + 1)

        assertEquals(listOf(header(day2), "new2", "new1"), fetch(IndexFilterItem.instance, null, lastShown).shape())
    }

    @Test
    fun `new logs spanning two days get a header per day`() {
        val lastShown = put("old", day1)
        put("late day1", day1 + 60_000)
        put("day2", day2)

        assertEquals(
            listOf(header(day2), "day2", header(day1), "late day1"),
            fetch(IndexFilterItem.instance, null, lastShown).shape(),
        )
    }

    @Test
    fun `nothing newer gives an empty list`() {
        put("a", day1)
        val lastShown = put("b", day1 + 1)

        assertEquals(emptyList<String>(), fetch(IndexFilterItem.instance, null, lastShown).shape())
    }

    @Test
    fun `a tag filter keeps its tag and session starts only`() {
        val lastShown = put("old", day1, tag = "net")
        put("GET /a", day2, tag = "net")
        put("click", day2 + 1, tag = "ui")
        put("Session Started: 1.0 (1)", day2 + 2, tag = DevinLogFlagsApi.TAG_SESSION_START)

        val result = fetch(TagFilterItem("net", isPinned = false), null, lastShown)

        assertEquals(listOf(header(day2), "session", "GET /a"), result.shape())
    }

    @Test
    fun `search text narrows a tag filter and drops session starts`() {
        val lastShown = put("old POST", day1, tag = "net")
        put("GET /a", day2, tag = "net")
        put("POST /b", day2 + 1, tag = "net")
        put("POST elsewhere", day2 + 2, tag = "ui")
        put("Session Started: 1.0 (1)", day2 + 3, tag = DevinLogFlagsApi.TAG_SESSION_START)

        val result = fetch(TagFilterItem("net", isPinned = false), "POST", lastShown)

        assertEquals(listOf(header(day2), "POST /b"), result.shape())
    }

    @Test
    fun `a custom filter keeps its tags and its own search text`() {
        val lastShown = put("old", day1, tag = "net")
        put("net hit", day2, tag = "net")
        put("net miss", day2 + 1, tag = "net")
        put("db hit", day2 + 2, tag = "db")
        put("ui hit", day2 + 3, tag = "ui")
        val filter = CustomFilterItem(FilterUiData("mine", NotEmptyString("mine"), false), CustomFilterCriteria(listOf("net", "db"), "hit"))

        assertEquals(listOf(header(day2), "db hit", "net hit"), fetch(filter, null, lastShown).shape())
    }

    @Test
    fun `returns every new log, not just one page`() {
        val lastShown = put("old", day1)
        repeat(PAGE_SIZE + 5) { put("n$it", day2 + it) }

        val result = fetch(IndexFilterItem.instance, null, lastShown)

        assertEquals(PAGE_SIZE + 5, result.count { it is TextLogItemData })
        assertEquals(1, result.count { it is DateLogItemData })
    }

    @Test
    fun `fetching new logs leaves the shown list and the next page's headers alone`() {
        // Page 1 and page 2 are both day1: page 2 continues the day, so it must not repeat its header.
        var lastShown = 0L
        repeat(PAGE_SIZE + 5) { lastShown = put("old$it", day1 + it) }
        val refresh = vm.refreshLogsAndFilters(IndexFilterItem.instance)
        idleUntil { refresh.isCompleted }
        val shown = vm.uiState.value
        assertEquals(header(day1), shown.logList!!.shape().first())

        put("new1", day2)
        put("new2", day2 + 1)
        val newLogs = fetch(IndexFilterItem.instance, null, lastShown)
        assertEquals(listOf(header(day2), "new2", "new1"), newLogs.shape())
        assertSame(shown, vm.uiState.value)

        vm.nextPage(0, IndexFilterItem.instance, null)
        idleUntil { vm.nextPageFlow.replayCache.isNotEmpty() }
        val page2 = vm.nextPageFlow.replayCache.single().logs
        assertTrue(page2.isNotEmpty())
        assertTrue("page 2 continues day1, so no header: ${page2.shape()}", page2.none { it is DateLogItemData })
    }

    private fun idleUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > end) fail("timed out")
            Thread.sleep(5)
        }
    }

    private companion object {
        const val CLIENT = "app.a"
        val PAGE_SIZE = PageInfo().count

        fun noon(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis
    }
}

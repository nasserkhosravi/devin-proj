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
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.CalendarType
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.filter.setIsPinned
import com.khosravi.devin.present.testing.DevinDbFixture
import com.khosravi.devin.present.testing.ProviderFixture
import com.khosravi.devin.present.tool.NotEmptyString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Editing a custom filter ([ReaderViewModel.updateFilter]) and the title check the edit dialog uses. */
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelFilterEditTest {

    private lateinit var app: Application
    private lateinit var filterRepository: FilterRepository
    private lateinit var usageRepository: FilterUsageRepository
    private lateinit var vm: ReaderViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        ProviderFixture.register()
        ProviderFixture.putClient(app, CLIENT)
        CacheRepository(app).setSelectedClientId(ClientData(CLIENT, null))
        filterRepository = FilterRepository(app)
        usageRepository = FilterUsageRepository(app)
        listOf(filter("alpha"), filter("beta"), filter("gamma")).forEach {
            assertTrue(filterRepository.saveFilter(it, CLIENT))
            Thread.sleep(2) // filters are ordered by their save time
        }
        filterRepository.saveAsPinned(filter("beta"))
        vm = ReaderViewModel(
            app, CalendarProxy(CalendarType.GREGORIAN), filterRepository, usageRepository, CacheRepository(app), UserSettings(), AppPref(app),
        )
    }

    @After
    fun tearDown() {
        DevinDbFixture.resetSingleton()
    }

    private fun filter(title: String, tags: List<String> = listOf("net"), search: String? = null, pinned: Boolean = false) =
        CustomFilterItem(FilterUiData(title, NotEmptyString(title), isPinned = pinned), CustomFilterCriteria(tags, search))

    private fun loadFilters() {
        val job = vm.refreshLogsAndFilters(IndexFilterItem.instance)
        idleUntil { job.isCompleted }
    }

    private fun filterIds() = vm.uiState.value.filterList!!.map { it.id }

    private fun savedIds() = filterRepository.getCustomFilterItemList(CLIENT).map { it.id }

    @Test
    fun `renaming replaces the chip in place, keeps its pin and selects it`() {
        loadFilters()
        val before = filterIds()
        val index = before.indexOf("beta")

        vm.updateFilter("beta", filter("beta2", tags = listOf("db"), search = "SELECT"), callbackId = "cb")
        idleUntil { vm.uiState.value.updateInfo.filterIdSelection == "beta2" }

        val state = vm.uiState.value
        assertEquals(before.toMutableList().apply { this[index] = "beta2" }, filterIds())
        val edited = state.filterList!![index] as CustomFilterItem
        assertEquals(CustomFilterCriteria(listOf("db"), "SELECT"), edited.criteria)
        assertTrue(edited.ui.isPinned)
        assertEquals("cb", state.updateInfo.callbackId)

        // Saved too: the old title is gone and the pin moved with the rename.
        assertEquals(listOf("alpha", "beta2", "gamma"), savedIds())
        assertTrue(filterRepository.getCustomFilterItemList(CLIENT).single { it.id == "beta2" }.ui.isPinned)
    }

    @Test
    fun `an unpinned filter stays unpinned whatever the edited data says`() {
        loadFilters()
        val index = filterIds().indexOf("alpha")

        vm.updateFilter("alpha", filter("alpha", tags = listOf("db"), pinned = true))
        idleUntil { (vm.uiState.value.filterList!![index] as CustomFilterItem).criteria.tags == listOf("db") }

        assertFalse(vm.uiState.value.filterList!![index].ui.isPinned)
        assertEquals("alpha", vm.uiState.value.updateInfo.filterIdSelection)
    }

    @Test
    fun `the log list is kept across an edit`() {
        ProviderFixture.putLog(app, CLIENT, "net", "GET /", 1_000)
        loadFilters()
        val logs = vm.uiState.value.logList

        vm.updateFilter("gamma", filter("gamma2"))
        idleUntil { vm.uiState.value.updateInfo.filterIdSelection == "gamma2" }

        assertSame(logs, vm.uiState.value.logList)
    }

    @Test
    fun `renaming drops the old title's chip usage, a same-title edit keeps it`() {
        usageRepository.recordPick(CLIENT, "alpha")
        usageRepository.recordPick(CLIENT, "beta")
        loadFilters()

        vm.updateFilter("alpha", filter("alpha", tags = listOf("db")))
        idleUntil { vm.uiState.value.updateInfo.filterIdSelection == "alpha" }
        vm.updateFilter("beta", filter("beta2"))
        idleUntil { vm.uiState.value.updateInfo.filterIdSelection == "beta2" }

        val scores = usageRepository.getScores(CLIENT)
        assertTrue(scores.containsKey("alpha"))
        assertFalse(scores.containsKey("beta"))
    }

    @Test
    fun `an unknown original id changes nothing`() {
        loadFilters()
        val before = vm.uiState.value

        vm.updateFilter("missing", filter("new"))
        idleFor(300)

        assertSame(before, vm.uiState.value)
        assertFalse(vm.isFilterTitleTaken("new"))
        assertEquals(listOf("alpha", "beta", "gamma"), savedIds())
    }

    @Test
    fun `a tag chip with that id is not edited as a custom filter`() {
        ProviderFixture.putLog(app, CLIENT, "net", "GET /", 1_000)
        loadFilters()
        val before = vm.uiState.value
        assertTrue(before.filterList!!.any { it is TagFilterItem && it.id == "net" })

        vm.updateFilter("net", filter("net2"))
        idleFor(300)

        assertSame(before, vm.uiState.value)
        assertFalse(vm.isFilterTitleTaken("net2"))
    }

    /** The screen reorders its chips on Pin/Unpin without going through uiState; the edit must build on that list. */
    @Test
    fun `an edit builds on the screen's list, keeping a pin made since the load`() {
        loadFilters()
        val loaded = vm.uiState.value.filterList!!
        val alpha = loaded.single { it.id == "alpha" }
        val screenList = listOf(alpha.setIsPinned(true)) + loaded.filter { it !== alpha }

        vm.updateFilter("alpha", filter("uno"), currentList = screenList)
        idleUntil { vm.uiState.value.updateInfo.filterIdSelection == "uno" }

        val list = vm.uiState.value.filterList!!
        assertEquals(screenList.map { it.id }.map { if (it == "alpha") "uno" else it }, list.map { it.id })
        assertTrue(list.first().ui.isPinned)
    }

    @Test
    fun `a new filter is appended to the screen's list`() {
        loadFilters()
        val loaded = vm.uiState.value.filterList!!
        val alpha = loaded.single { it.id == "alpha" }
        val screenList = listOf(alpha.setIsPinned(true)) + loaded.filter { it !== alpha }

        vm.addFilter(filter("delta"), currentList = screenList)
        idleUntil { vm.uiState.value.updateInfo.filterIdSelection == "delta" }

        assertEquals(screenList.map { it.id } + "delta", vm.uiState.value.filterList!!.map { it.id })
        assertTrue(vm.uiState.value.filterList!!.first().ui.isPinned)
    }

    @Test
    fun `an edit before the filters load changes nothing`() {
        vm.updateFilter("alpha", filter("alpha2"))
        idleFor(300)

        assertEquals(null, vm.uiState.value.filterList)
        assertEquals(listOf("alpha", "beta", "gamma"), savedIds())
    }

    @Test
    fun `a title is taken once any client saved a filter with it`() {
        assertTrue(vm.isFilterTitleTaken("alpha"))
        assertFalse(vm.isFilterTitleTaken("delta"))

        filterRepository.saveFilter(filter("delta"), "other.client")
        assertTrue(vm.isFilterTitleTaken("delta"))
    }

    @Test
    fun `a renamed filter's old title is free again`() {
        loadFilters()

        vm.updateFilter("beta", filter("beta2"))
        idleUntil { vm.uiState.value.updateInfo.filterIdSelection == "beta2" }

        assertFalse(vm.isFilterTitleTaken("beta"))
        assertTrue(vm.isFilterTitleTaken("beta2"))
    }

    private fun idleFor(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
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
    }
}

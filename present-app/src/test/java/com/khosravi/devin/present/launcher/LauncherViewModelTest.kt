package com.khosravi.devin.present.launcher

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.CacheRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LauncherViewModelTest {

    private lateinit var app: Application
    private lateinit var repository: LaunchRepository

    /** The screen passes its Activity (Compose's LocalContext) to launch from. */
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        repository = LaunchRepository(app)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun selectClient() = CacheRepository(app).setSelectedClientId(ClientData(CLIENT, null))

    private fun newViewModel() = LauncherViewModel(app, CacheRepository(app), repository, DeeplinkScanner(app))

    /** A ViewModel for the selected client whose saved launches have loaded. */
    private fun loadedViewModel(): LauncherViewModel {
        selectClient()
        val expected = repository.list(CLIENT)
        return newViewModel().also { vm -> idleUntil { vm.state.value.entries == expected }; idleFor(50) }
    }

    private fun save(link: String, key: String = "", value: String = "", at: Long) =
        repository.recordLaunch(CLIENT, LaunchInput.of(link, key, value), editingId = null, now = at)

    private fun LauncherViewModel.type(link: String, key: String = "", value: String = "") {
        onDeeplinkChange(link)
        onKeyChange(key)
        onValueChange(value)
    }

    private fun startedActivity(): Intent? = shadowOf(activity).nextStartedActivity

    private fun LauncherViewModel.nextEvent(): LauncherEvent = runBlocking { withTimeout(5_000) { events.first() } }

    /** A context whose startActivity fails the way [launchClient] sees a missing or unexported activity. */
    private fun failingContext(error: RuntimeException) = object : ContextWrapper(activity) {
        override fun startActivity(intent: Intent?) = throw error
    }

    @Test
    fun `without a selected client there is nothing to launch`() {
        val vm = newViewModel()
        vm.type("app://home")

        vm.launch(activity)
        idleFor(100)

        assertFalse(vm.state.value.hasClient)
        assertNull(startedActivity())
        assertEquals(emptyList<LaunchEntry>(), vm.state.value.entries)
    }

    @Test
    fun `opening lists the client's saved launches and pre-fills the last extra key`() {
        save("app://a", "payload", "{}", at = 1)
        save("app://b", at = 2)

        val vm = loadedViewModel()

        assertEquals(listOf("app://b", "app://a"), vm.state.value.entries.map { it.input.deeplink })
        assertEquals("payload", vm.state.value.extraKey)
        assertTrue(vm.state.value.hasClient)
    }

    @Test
    fun `a key typed before the saved launches load is not overwritten`() {
        save("app://a", "payload", "{}", at = 1)
        selectClient()

        val vm = newViewModel()
        vm.onKeyChange("mine")
        idleUntil { vm.state.value.entries.isNotEmpty() }

        assertEquals("mine", vm.state.value.extraKey)
    }

    @Test
    fun `invalid input reports its error and does not launch`() {
        val vm = loadedViewModel()

        assertEquals(LaunchInputError.MissingDeeplink, vm.state.value.inputError)
        vm.type("no scheme")
        assertEquals(LaunchInputError.InvalidDeeplink, vm.state.value.inputError)
        vm.type("app://home", key = "", value = "{}")
        assertEquals(LaunchInputError.MissingKey, vm.state.value.inputError)
        vm.type("app://home", key = "k", value = "[1]")
        assertEquals(LaunchInputError.InvalidJson, vm.state.value.inputError)

        vm.launch(activity)
        idleFor(100)

        assertNull(startedActivity())
        assertEquals(emptyList<LaunchEntry>(), repository.list(CLIENT))
    }

    @Test
    fun `a launch opens the link in the client with the extra and saves it on top`() {
        save("app://old", at = 1)
        val vm = loadedViewModel()
        vm.type(" app://home/1 ", key = " payload ", value = """{"x":1}""")

        vm.launch(activity)
        idleUntil { vm.state.value.entries.size == 2 }

        val intent = startedActivity()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("app://home/1", intent.dataString)
        assertEquals(CLIENT, intent.`package`)
        assertEquals("""{"x":1}""", intent.getStringExtra("payload"))
        val top = vm.state.value.entries.first().input
        assertEquals(LaunchInput("app://home/1", "payload", """{"x":1}"""), top)
        assertEquals(vm.state.value.entries, repository.list(CLIENT))
    }

    @Test
    fun `a launch with no value sends no extra, even with the key pre-filled`() {
        save("app://a", "payload", "{}", at = 1)
        val vm = loadedViewModel()
        vm.onDeeplinkChange("app://b")
        assertNull(vm.state.value.inputError)

        vm.launch(activity)
        idleUntil { vm.state.value.entries.size == 2 }

        assertNull(startedActivity()!!.extras?.getString("payload"))
        assertEquals(LaunchInput("app://b", null, null), vm.state.value.entries.first().input)
    }

    @Test
    fun `no handling activity reports the failure and saves nothing`() {
        val vm = loadedViewModel()
        vm.type("app://nowhere")

        vm.launch(failingContext(ActivityNotFoundException()))

        assertEquals(LauncherEvent.LaunchFailed(LaunchResult.Reason.NoHandler), vm.nextEvent())
        idleFor(100)
        assertEquals(emptyList<LaunchEntry>(), vm.state.value.entries)
        assertEquals(emptyList<LaunchEntry>(), repository.list(CLIENT))
    }

    @Test
    fun `an unexported activity reports the failure`() {
        val vm = loadedViewModel()
        vm.type("app://private")

        vm.launch(failingContext(SecurityException()))

        assertEquals(LauncherEvent.LaunchFailed(LaunchResult.Reason.NotExported), vm.nextEvent())
    }

    @Test
    fun `editing an entry fills the inputs and the launch updates that entry`() {
        val a = save("app://a", "k", "{}", at = 1)
        save("app://b", at = 2)
        val vm = loadedViewModel()

        vm.edit(a)
        assertEquals(LauncherUiState(deeplink = "app://a", extraKey = "k", extraValue = "{}", editingId = a.id), vm.state.value.copy(entries = emptyList(), suggestions = emptyList()))
        vm.onDeeplinkChange("app://a2")
        vm.launch(activity)
        idleUntil { vm.state.value.entries.first().input.deeplink == "app://a2" }

        assertEquals(listOf("app://a2", "app://b"), vm.state.value.entries.map { it.input.deeplink })
        assertEquals(a.id, vm.state.value.entries.first().id)
        assertNull(vm.state.value.editingId)
    }

    @Test
    fun `cancelling an edit makes the next launch a new entry`() {
        val a = save("app://a", at = 1)
        val vm = loadedViewModel()

        vm.edit(a)
        vm.cancelEdit()
        vm.onDeeplinkChange("app://c")
        vm.launch(activity)
        idleUntil { vm.state.value.entries.size == 2 }

        assertEquals(listOf("app://c", "app://a"), vm.state.value.entries.map { it.input.deeplink })
        assertNotEquals(a.id, vm.state.value.entries.first().id)
    }

    @Test
    fun `relaunching an entry opens it again and moves it to the top`() {
        val a = save("app://a", "k", """{"y":2}""", at = 1)
        save("app://b", at = 2)
        val vm = loadedViewModel()

        vm.relaunch(activity, a)
        idleUntil { vm.state.value.entries.first().id == a.id }

        val intent = startedActivity()!!
        assertEquals("app://a", intent.dataString)
        assertEquals("""{"y":2}""", intent.getStringExtra("k"))
        assertEquals(listOf("app://a", "app://b"), vm.state.value.entries.map { it.input.deeplink })
    }

    @Test
    fun `deleting removes the entry, reports it, and undo puts it back`() {
        val a = save("app://a", at = 1)
        save("app://b", at = 2)
        val vm = loadedViewModel()

        vm.delete(a)
        idleUntil { vm.state.value.entries.size == 1 }

        assertEquals(LauncherEvent.Deleted(a), vm.nextEvent())
        assertEquals(listOf("app://b"), repository.list(CLIENT).map { it.input.deeplink })

        vm.undoDelete(a)
        idleUntil { vm.state.value.entries.size == 2 }
        assertEquals(listOf("app://b", "app://a"), vm.state.value.entries.map { it.input.deeplink })
    }

    @Test
    fun `deleting the entry being edited ends the edit`() {
        val a = save("app://a", at = 1)
        val vm = loadedViewModel()
        vm.edit(a)

        vm.delete(a)
        idleUntil { vm.state.value.entries.isEmpty() }

        assertNull(vm.state.value.editingId)
        assertEquals("app://a", vm.state.value.deeplink) // the inputs stay, so the user can launch them as new
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
        const val CLIENT = "app.client"
    }
}

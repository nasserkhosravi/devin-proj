package com.khosravi.devin.present.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LaunchRepositoryTest {

    private lateinit var repository: LaunchRepository

    @Before
    fun setUp() {
        repository = LaunchRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    private fun input(link: String, key: String = "", value: String = "") = LaunchInput.of(link, key, value)

    @Test
    fun `new launches are listed newest first`() {
        repository.recordLaunch("c", input("a://1"), editingId = null, now = 1)
        repository.recordLaunch("c", input("a://2"), editingId = null, now = 2)

        assertEquals(listOf("a://2", "a://1"), repository.list("c").map { it.input.deeplink })
    }

    @Test
    fun `an identical launch moves to the top without a duplicate`() {
        val a = repository.recordLaunch("c", input("a://1", "k", "{\"x\":1}"), editingId = null, now = 1)
        repository.recordLaunch("c", input("a://2"), editingId = null, now = 2)
        repository.recordLaunch("c", input("a://1", "k", "{ \"x\" : 1 }"), editingId = null, now = 3)

        val list = repository.list("c")
        assertEquals(listOf("a://1", "a://2"), list.map { it.input.deeplink })
        assertEquals(a.id, list[0].id)
        assertEquals(3L, list[0].lastLaunchedAt)
    }

    @Test
    fun `editing updates the entry in place`() {
        val a = repository.recordLaunch("c", input("a://1"), editingId = null, now = 1)
        repository.recordLaunch("c", input("a://2"), editingId = null, now = 2)

        repository.recordLaunch("c", input("a://3", "k", "{}"), editingId = a.id, now = 3)

        val list = repository.list("c")
        assertEquals(listOf("a://3", "a://2"), list.map { it.input.deeplink })
        assertEquals(a.id, list[0].id)
        assertEquals("k", list[0].input.extraKey)
    }

    @Test
    fun `editing into an existing entry's values merges them`() {
        val a = repository.recordLaunch("c", input("a://1"), editingId = null, now = 1)
        repository.recordLaunch("c", input("a://2"), editingId = null, now = 2)

        repository.recordLaunch("c", input("a://2"), editingId = a.id, now = 3)

        assertEquals(listOf("a://2"), repository.list("c").map { it.input.deeplink })
    }

    @Test
    fun `the list is capped at 50`() {
        repeat(51) { repository.recordLaunch("c", input("a://$it"), editingId = null, now = it.toLong()) }

        val list = repository.list("c")
        assertEquals(50, list.size)
        assertEquals("a://50", list.first().input.deeplink)
        assertEquals("a://1", list.last().input.deeplink)
    }

    @Test
    fun `delete and restore`() {
        repository.recordLaunch("c", input("a://1"), editingId = null, now = 1)
        val b = repository.recordLaunch("c", input("a://2"), editingId = null, now = 2)
        repository.recordLaunch("c", input("a://3"), editingId = null, now = 3)

        assertEquals(b, repository.delete("c", b.id))
        assertEquals(listOf("a://3", "a://1"), repository.list("c").map { it.input.deeplink })

        repository.restore("c", b)
        assertEquals(listOf("a://3", "a://2", "a://1"), repository.list("c").map { it.input.deeplink })
    }

    @Test
    fun `lastExtraKey is the newest key`() {
        assertNull(repository.lastExtraKey("c"))
        repository.recordLaunch("c", input("a://1", "old", "{}"), editingId = null, now = 1)
        repository.recordLaunch("c", input("a://2", "new", "{}"), editingId = null, now = 2)
        repository.recordLaunch("c", input("a://3"), editingId = null, now = 3)

        assertEquals("new", repository.lastExtraKey("c"))
    }

    @Test
    fun `clients are kept apart`() {
        repository.recordLaunch("c1", input("a://1"), editingId = null, now = 1)
        repository.recordLaunch("c2", input("a://2"), editingId = null, now = 2)

        assertEquals(listOf("a://1"), repository.list("c1").map { it.input.deeplink })
        assertEquals(listOf("a://2"), repository.list("c2").map { it.input.deeplink })
    }
}

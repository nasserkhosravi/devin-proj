package com.khosravi.devin.write.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.write.testing.DevinTestFixture.toLogRows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevinDBTest {

    private lateinit var db: DevinDB

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DevinDB::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun log(clientId: String, date: Long, tag: String = "tag", value: String = "v$date") =
        db.logDao().insert(LogTable(0, tag, value, date, null, clientId, null))

    private fun metaIndexCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ${MetaIndexTable.TABLE_NAME}")
            .use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun `getAllAsCursor returns one client's logs newest first`() {
        log("a", date = 1)
        log("a", date = 3)
        log("b", date = 2)
        log("a", date = 2)

        val rows = db.logDao().getAllAsCursor("a").use { it.toLogRows() }

        assertEquals(listOf(3L, 2L, 1L), rows.map { it.date })
        assertEquals(setOf("a"), rows.map { it.clientId }.toSet())
    }

    @Test
    fun `getAllLogsByTagAsCursor filters by client and tag`() {
        log("a", date = 1, tag = "x")
        log("a", date = 2, tag = "y")
        log("b", date = 3, tag = "x")

        val rows = db.logDao().getAllLogsByTagAsCursor("a", "x").use { it.toLogRows() }

        assertEquals(listOf("v1"), rows.map { it.value })
    }

    @Test
    fun `getLog returns the row with that id`() {
        log("a", date = 1)
        val id = log("a", date = 2)

        val rows = db.logDao().getLog(id).use { it.toLogRows() }

        assertEquals(listOf(id), rows.map { it.id })
    }

    @Test
    fun `update rewrites the row with the same id`() {
        val id = log("a", date = 1)

        db.logDao().update(LogTable(id, "new-tag", "new-value", 9, "{}", "a", "type"))

        val row = db.logDao().getLog(id).use { it.toLogRows() }.single()
        assertEquals("new-tag", row.tag)
        assertEquals("new-value", row.value)
        assertEquals(9L, row.date)
        assertEquals("{}", row.meta)
        assertEquals("type", row.typeId)
    }

    @Test
    fun `removeLogs only removes the given client`() {
        log("a", date = 1)
        log("b", date = 2)

        db.logDao().removeLogs("a")

        assertEquals(0, db.logDao().getAllAsCursor("a").use { it.count })
        assertEquals(1, db.logDao().getAllAsCursor("b").use { it.count })
    }

    @Test
    fun `removing a log cascades to its meta index`() {
        val id = log("a", date = 1)
        db.metaIndexDao().put(MetaIndexTable(id, "url", "https://x"))
        assertEquals(1, metaIndexCount())

        db.logDao().removeLogs("a")

        assertEquals(0, metaIndexCount())
    }

    @Test
    fun `putting a client with the same package replaces it`() {
        db.clientDao().put(ClientTable("pkg", "{\"v\":1}"))
        db.clientDao().put(ClientTable("pkg", "{\"v\":2}"))

        db.clientDao().getAllAsCursor().use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("{\"v\":2}", it.getString(it.getColumnIndexOrThrow(ClientTable.COLUMN_PRESENTER_CONFIG)))
        }
    }

    @Test
    fun `nukeTable removes every client`() {
        db.clientDao().put(ClientTable("a", null))
        db.clientDao().put(ClientTable("b", null))

        db.clientDao().nukeTable()

        assertEquals(0, db.clientDao().getAllAsCursor().use { it.count })
    }
}

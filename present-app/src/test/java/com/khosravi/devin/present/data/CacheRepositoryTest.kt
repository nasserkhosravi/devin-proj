package com.khosravi.devin.present.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.client.ClientData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `selected client id is null until set, then persists`() {
        assertNull(CacheRepository(context).getSelectedClientId())

        CacheRepository(context).setSelectedClientId(ClientData("app.a", null))
        assertEquals("app.a", CacheRepository(context).getSelectedClientId())

        CacheRepository(context).setSelectedClientId(ClientData("app.b", null))
        assertEquals("app.b", CacheRepository(context).getSelectedClientId())
    }
}

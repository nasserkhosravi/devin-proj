package com.khosravi.devin.present.domain

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.AppPref
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Covers the decisions made without UI. The path that needs a password shows a bottom sheet and
 * is not covered here.
 */
@RunWith(RobolectricTestRunner::class)
class ClientLoginInteractorTest {

    private lateinit var appPref: AppPref
    private lateinit var interactor: ClientLoginInteractor
    private val activity: AppCompatActivity = Robolectric.buildActivity(AppCompatActivity::class.java).get()

    @Before
    fun setUp() {
        appPref = AppPref(ApplicationProvider.getApplicationContext<Context>())
        interactor = ClientLoginInteractor(appPref)
    }

    private fun client(password: String?) = ClientData(
        "app.a",
        password?.let { JSONObject().put("logPassword", it) },
    )

    private fun select(client: ClientData): List<Boolean> {
        val results = mutableListOf<Boolean>()
        interactor.onClientSelect(activity, client) { results.add(it) }
        return results
    }

    @Test
    fun `client without a password opens directly`() {
        assertEquals(listOf(true), select(client(null)))
    }

    @Test
    fun `empty password counts as no password`() {
        assertEquals(listOf(true), select(client("")))
    }

    @Test
    fun `previously confirmed password opens directly`() {
        appPref.saveConfirmedPassword("app.a", "1234")
        assertEquals(listOf(true), select(client("1234")))
    }

    @Test
    fun `too many wrong tries blocks without asking`() {
        repeat(4) { appPref.increaseLastWrongPasswordCount("app.a") }
        assertEquals(listOf(false), select(client("1234")))
    }

    @Test
    fun `confirmed password for an old value does not bypass a changed password`() {
        appPref.saveConfirmedPassword("app.a", "old")
        repeat(4) { appPref.increaseLastWrongPasswordCount("app.a") }
        assertEquals(listOf(false), select(client("new")))
    }
}

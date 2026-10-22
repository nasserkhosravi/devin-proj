package com.khosravi.devin.present.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.Ui.textValue
import com.khosravi.devin.present.present.LogActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginE2eTest : E2eTest() {

    private fun logListOpen() = AppState.activities().any { it is LogActivity }

    private fun passwordField() = Ui.field("Password")

    private fun submit(password: String) {
        Ui.setText(passwordField(), password)
        Ui.clickText("Confirm")
    }

    /** LOGIN-01, LOGIN-04, LOGIN-06: sheet with title/field/Confirm; wrong → inline error; right → log list; remembered next time. */
    @Test
    fun wrongThenCorrectPassword_opensLogs_andIsRemembered() {
        TestData.client(presenterConfig = TestData.passwordConfig("12346"))
        TestData.log("A", "secret log")
        launchStarter()

        Ui.waitForText("Enter Numeric Password")
        Ui.waitForText("Password")
        Ui.waitForText("Confirm")

        submit("0000")
        Ui.waitForText("Incorrect password. Please try again.")
        Waits.holds("no log list after a wrong password", 1_000) { !logListOpen() }

        Ui.setText(passwordField(), "1")
        Ui.waitGone("error cleared on edit", predicate = Ui.text("Incorrect password. Please try again."))

        submit("12346")
        awaitLogList()
        Ui.waitForText("secret log")

        Ui.back()
        Ui.waitForText("One client found")
        Ui.clickText(TestData.CLIENT)
        awaitLogList()
        Ui.assertNotShown("no sheet once confirmed", Ui.text("Enter Numeric Password"))
    }

    /** LOGIN-02: an alphanumeric password is accepted (full keyboard, masked). LOGIN-03: SHOW reveals the text. */
    @Test
    fun alphanumericPassword_andShowToggle() {
        TestData.client(presenterConfig = TestData.passwordConfig("ab12"))
        launchStarter()

        Ui.setText(passwordField(), "ab12")
        val field = passwordField()
        assertEquals("masked by default", true, field.isPassword)
        Ui.clickText("SHOW")
        Ui.waitForText("HIDE")
        Waits.require("text visible after SHOW") { passwordField().textValue() == "ab12" && !passwordField().isPassword }

        Ui.clickText("Confirm")
        awaitLogList()
    }

    /** LOGIN-05: the keyboard's Done action submits like Confirm. */
    @Test
    fun imeDone_submits() {
        TestData.client(presenterConfig = TestData.passwordConfig("12346"))
        launchStarter()

        Ui.setText(passwordField(), "12346")
        Ui.imeAction(passwordField())
        awaitLogList()
    }

    /** LOGIN-07, LOGIN-08: the 4th wrong password closes the sheet with a toast; selecting again is refused at once. */
    @Test
    fun fourWrongPasswords_lockOut() {
        TestData.client(presenterConfig = TestData.passwordConfig("12346"))
        launchStarter()

        repeat(3) {
            submit("999$it")
            Ui.waitForText("Incorrect password. Please try again.")
        }
        submit("9993")
        Toasts.waitFor("Too many wrong password try")
        Ui.waitGone("sheet closed", predicate = Ui.text("Enter Numeric Password"))
        Waits.holds("no log list", 1_000) { !logListOpen() }

        Toasts.clear()
        Ui.clickText(TestData.CLIENT)
        Toasts.waitFor("Too many wrong password try")
        Waits.holds("no sheet and no log list while locked", 1_500) {
            !Ui.exists(Ui.text("Enter Numeric Password")) && !logListOpen()
        }
    }

    /** LOGIN-09: back dismisses the sheet and stays on Starter. */
    @Test
    fun back_dismissesSheet() {
        TestData.client(presenterConfig = TestData.passwordConfig("12346"))
        launchStarter()
        Ui.waitForText("Enter Numeric Password")

        Ui.back()

        Ui.waitGone("sheet", predicate = Ui.text("Enter Numeric Password"))
        Ui.waitForText("One client found")
        Waits.holds("stays on Starter", 1_000) { !logListOpen() }
    }
}

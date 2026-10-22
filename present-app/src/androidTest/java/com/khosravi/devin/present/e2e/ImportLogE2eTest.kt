package com.khosravi.devin.present.e2e

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.present.ImportLogActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The import viewer has no UI entry point (IMP-01); it is opened with its own intent and a fixture file. */
@RunWith(AndroidJUnit4::class)
class ImportLogE2eTest : E2eTest() {

    private fun importFile(content: String): Uri {
        val file = File(AppState.e2eDir(), "import.json").apply { writeText(content) }
        return Uri.fromFile(file)
    }

    /**
     * Opens the import screen on top of Starter. Error paths finish it at once; with Starter left in front the
     * toast is a foreground toast (a toast from an app with no visible activity counts against the system's
     * background toast quota and gets dropped when tests run back to back).
     */
    private fun launchOverStarter(intent: android.content.Intent) {
        launchStarter()
        Ui.waitForText("Client selection")
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun fixture(): String {
        val t1 = TestData.ts(60)
        val t2 = TestData.ts(30)
        return """{"logs":[
            {"_tag":"Net","_value":"Imported Alpha","_date":$t1,"_clientId":"app.x"},
            {"_tag":"Ui","_value":"imported beta","_date":$t2,"_clientId":"app.x"}
        ]}"""
    }

    /** IMP-02: rows render with tag chips; "Contain value" filters text rows case-insensitively. */
    @Test
    fun rendersRows_andSearchFilters() {
        launch(ImportLogActivity.intent(context, importFile(fixture())))

        Ui.waitForText("Imported Alpha")
        Ui.waitForText("imported beta")
        Ui.waitForText("Net")
        Ui.waitForText("Ui")

        Ui.typeInto("Contain value", "ALPHA")
        Ui.waitGone("non-matching row", predicate = Ui.text("imported beta"))
        Ui.waitForText("Imported Alpha")
    }

    /** IMP-03: tapping a text row opens the log detail page; back returns with the query kept. */
    @Test
    fun tapRow_opensDetail() {
        launch(ImportLogActivity.intent(context, importFile(fixture())))
        Ui.clickText("Imported Alpha")
        Ui.waitForText("Log detail")
        Ui.waitForText("Net")
        Ui.back()
        Ui.waitForText("Contain value")
    }

    /** IMP-04: an empty file → "Empty file text!" and the screen closes. */
    @Test
    fun emptyFile_toastsAndCloses() {
        launchOverStarter(ImportLogActivity.intent(context, importFile("")))
        Toasts.waitFor("Empty file text!")
        Waits.require("import screen closed") { AppState.activities().none { it is ImportLogActivity } }
    }

    /** IMP-04: a missing file → "Something went wrong!" and the screen closes. */
    @Test
    fun missingFile_toastsAndCloses() {
        launchOverStarter(ImportLogActivity.intent(context, Uri.fromFile(File(AppState.e2eDir(), "does-not-exist.json"))))
        Toasts.waitFor("Something went wrong!")
        Waits.require("import screen closed") { AppState.activities().none { it is ImportLogActivity } }
    }

    /** IMP-05: malformed JSON → "Empty file text!" and the screen closes. */
    @Test
    fun malformedFile_toastsAndCloses() {
        launchOverStarter(ImportLogActivity.intent(context, importFile("not json at all")))
        Toasts.waitFor("Empty file text!")
        Waits.require("import screen closed") { AppState.activities().none { it is ImportLogActivity } }
    }
}

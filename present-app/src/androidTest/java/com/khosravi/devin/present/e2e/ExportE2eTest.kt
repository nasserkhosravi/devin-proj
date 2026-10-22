package com.khosravi.devin.present.e2e

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.IntentStubs.chooserTarget
import com.khosravi.devin.present.e2e.Ui.textValue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ExportE2eTest : E2eTest() {

    private fun seedAndOpenExport() {
        openLogList {
            TestData.log("A", "export a1", date = TestData.ts(40))
            TestData.log("A", "export a2", date = TestData.ts(30))
            TestData.log("B", "export b1", date = TestData.ts(20))
        }
        Ui.waitForText("export b1")
        overflow("Share/Save logs")
        Ui.waitForText("Save Logs")
    }

    private fun stubSavePicker(out: File) {
        IntentStubs.stub(
            { it.action == Intent.ACTION_CHOOSER && it.chooserTarget()?.action == Intent.ACTION_CREATE_DOCUMENT },
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(out)),
        )
    }

    /** EXP-01: Default is selected and explained. EXP-02: Builder shows the whitelist, day filter ("1") and checkbox. */
    @Test
    fun modes_defaultAndBuilderFields() {
        seedAndOpenExport()
        Ui.waitForText("Default")
        Ui.waitFor("default explanation", predicate = Ui.textContains("It export all logs in a zip file"))

        Ui.clickText("Builder")
        Ui.waitForText("Whitelist tag filter,(example: a,b,c), Default is all")
        Ui.waitForText("Day filter, default is no filter")
        Ui.waitForText("1")
        val checkbox = with(Ui) { waitForText("Separate tag files").selfOrAncestor { it.isCheckable } }!!
        assertFalse("Separate tag files unchecked", checkbox.isChecked)
    }

    /** EXP-09: non-digits typed into the days field are dropped. */
    @Test
    fun daysField_keepsDigitsOnly() {
        seedAndOpenExport()
        Ui.clickText("Builder")
        Ui.typeInto("Day filter, default is no filter", "-12a")
        Waits.require("field shows 12") { Ui.exists(Ui.text("12")) }
    }

    /** EXP-04, EXP-07: Default → Save opens a .zip create-document picker; the saved zip has index.json + one file per tag. */
    @Test
    fun saveDefault_writesZipWithIndexAndTagFiles() {
        val out = File(AppState.e2eDir(), "export.zip")
        stubSavePicker(out)
        seedAndOpenExport()

        Ui.clickText("Save Logs")

        val chooser = IntentStubs.waitForStarted("save picker") { it.action == Intent.ACTION_CHOOSER }
        assertEquals("Share/Save logs", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        val create = chooser.chooserTarget()!!
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, create.action)
        assertEquals("application/zip", create.type)
        val name = create.getStringExtra(Intent.EXTRA_TITLE)!!
        assertTrue(name, name.matches(Regex("""Devin_.+\.zip""")))

        Toasts.waitFor("Save done")
        Ui.waitGone("export dialog", predicate = Ui.text("Save Logs"))
        Waits.require("zip written") { out.length() > 0 }
        val entries = ZipFile(out).use { zip -> zip.entries().toList().associate { it.name to zip.getInputStream(it).readBytes().decodeToString() } }
        assertEquals(setOf("index.json", "A.json", "B.json"), entries.keys)
        assertTrue(entries["index.json"]!!.let { it.contains("export a1") && it.contains("export b1") })
        assertTrue(entries["A.json"]!!.let { it.contains("export a2") && !it.contains("export b1") })
    }

    /** EXP-04, EXP-07: Builder with whitelist "A" and separate off → a single JSON file with only A's logs. */
    @Test
    fun saveBuilder_whitelistSingleJson() {
        val out = File(AppState.e2eDir(), "export.json")
        stubSavePicker(out)
        seedAndOpenExport()

        Ui.clickText("Builder")
        Ui.typeInto("Whitelist tag filter,(example: a,b,c), Default is all", "A")
        // Clear the day filter: its semantics are a documented known bug (EXP-08), not under test here.
        Ui.typeInto("Day filter, default is no filter", "")
        Ui.clickText("Save Logs")

        val create = IntentStubs.waitForStarted("save picker") { it.action == Intent.ACTION_CHOOSER }.chooserTarget()!!
        assertEquals("application/json", create.type)
        assertTrue(create.getStringExtra(Intent.EXTRA_TITLE)!!.endsWith(".json"))
        Toasts.waitFor("Save done")
        Waits.require("json written") { out.length() > 0 }
        val text = out.readText()
        JSONObject(text) // valid JSON
        assertTrue(text, text.contains("export a1") && text.contains("export a2"))
        assertFalse(text, text.contains("export b1"))
    }

    /** EXP-05: cancelling the save picker keeps the dialog open, with no toast. */
    @Test
    fun savePickerCancelled_dialogStaysOpen() {
        IntentStubs.stubSystemUi() // chooser → RESULT_CANCELED
        seedAndOpenExport()

        Ui.clickText("Save Logs")
        IntentStubs.waitForStarted("save picker") { it.action == Intent.ACTION_CHOOSER }

        Waits.require("buttons enabled again") { Ui.find(predicate = Ui.text("Save Logs"))?.let { n -> with(Ui) { n.selfOrAncestor { it.isClickable } }?.isEnabled } == true }
        Toasts.assertNone("Save done")
        Ui.waitForText("Share Logs")
    }

    /** EXP-06: Share (Default) opens "Share file through" with an application/zip ACTION_SEND and closes the dialog. */
    @Test
    fun shareDefault_sendsZip() {
        IntentStubs.stubSystemUi()
        seedAndOpenExport()

        Ui.clickText("Share Logs")

        val chooser = IntentStubs.waitForStarted("share chooser") { it.action == Intent.ACTION_CHOOSER }
        assertEquals("Share file through", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        val send = chooser.chooserTarget()!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/zip", send.type)
        @Suppress("DEPRECATION")
        assertTrue(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM).toString().startsWith("content://com.khosravi.devin.present.fileprovider/"))
        Ui.waitGone("export dialog", predicate = Ui.text("Share Logs"))
    }

    /** EXP-13: back closes the dialog. */
    @Test
    fun back_closesDialog() {
        seedAndOpenExport()
        Ui.back()
        Ui.waitGone("export dialog", predicate = Ui.text("Save Logs"))
        Ui.waitForText("export b1")
        assertTrue(Ui.find(predicate = Ui.text("export b1"))?.textValue() != null)
    }
}

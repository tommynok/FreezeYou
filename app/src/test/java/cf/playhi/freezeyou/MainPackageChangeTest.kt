package cf.playhi.freezeyou

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main screen now follows package changes made outside the app, and its selection actions are
 * derived from the unfiltered rows rather than only the rows the search currently shows. These
 * tests pin down which broadcasts count as package changes, when a removal drops the selection,
 * and how selected packages are matched against rows that may be hidden by the query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MainPackageChangeTest {

    @Test
    fun `install, update and removal are the package changes that rebuild the list`() {
        assertTrue(Main.isPackageChangeAction(Intent.ACTION_PACKAGE_ADDED))
        assertTrue(Main.isPackageChangeAction(Intent.ACTION_PACKAGE_REMOVED))
        assertTrue(Main.isPackageChangeAction(Intent.ACTION_PACKAGE_REPLACED))
    }

    @Test
    fun `other broadcasts do not touch the list`() {
        assertFalse(Main.isPackageChangeAction(Intent.ACTION_PACKAGE_CHANGED))
        assertFalse(Main.isPackageChangeAction("cf.playhi.freezeyou.action.packageStatusChanged"))
        assertFalse(Main.isPackageChangeAction(null))
    }

    @Test
    fun `a removed package is dropped from the selection`() {
        assertTrue(Main.shouldDropPackageFromSelectionOnRemoval(Intent.ACTION_PACKAGE_REMOVED, false))
    }

    @Test
    fun `a package replaced in place keeps its place in the selection`() {
        assertFalse(Main.shouldDropPackageFromSelectionOnRemoval(Intent.ACTION_PACKAGE_REMOVED, true))
        assertFalse(Main.shouldDropPackageFromSelectionOnRemoval(Intent.ACTION_PACKAGE_ADDED, false))
        assertFalse(Main.shouldDropPackageFromSelectionOnRemoval(Intent.ACTION_PACKAGE_REPLACED, false))
        assertFalse(Main.shouldDropPackageFromSelectionOnRemoval(null, false))
    }

    @Test
    fun `force-stop needs an installed action and an unfrozen selection`() {
        assertTrue(Main.canForceStop(true, true))
        assertFalse(Main.canForceStop(true, false))
        assertFalse(Main.canForceStop(false, true))
        assertFalse(Main.canForceStop(false, false))
    }

    @Test
    fun `a selected package hidden by the search is still counted as unfrozen`() {
        val rows = listOf(
            appRow("cf.playhi.hidden", frozen = false),
            appRow("cf.playhi.visible", frozen = true)
        )
        // The adapter would only hold the matching row; the unfiltered list keeps both, so the
        // unfrozen selected package still makes Force-stop available while the query hides it.
        assertTrue(Main.hasUnfrozenPackage(setOf("cf.playhi.hidden"), rows))
        assertFalse(Main.hasUnfrozenPackage(setOf("cf.playhi.visible"), rows))
        assertFalse(Main.hasUnfrozenPackage(setOf("cf.playhi.missing"), rows))
        assertFalse(Main.hasUnfrozenPackage(null, rows))
        assertFalse(Main.hasUnfrozenPackage(setOf("cf.playhi.hidden"), null))
    }

    @Test
    fun `an uninstalled system selection survives the search too`() {
        val rows = listOf(
            appRow("cf.playhi.removedSystem", frozen = true, restorableSystemApp = true),
            appRow("cf.playhi.installed", frozen = false)
        )
        assertTrue(Main.hasUninstalledSystemPackage(setOf("cf.playhi.removedSystem"), rows))
        assertFalse(Main.hasUninstalledSystemPackage(setOf("cf.playhi.installed"), rows))
        assertFalse(Main.hasUninstalledSystemPackage(null, rows))
        assertFalse(Main.hasUninstalledSystemPackage(setOf("cf.playhi.removedSystem"), null))
    }

    @Test
    fun `force-stop targets exclude every frozen selected package`() {
        val targets = Main.filterForceStopTargets(
            listOf("cf.playhi.a", "cf.playhi.b", "cf.playhi.c"),
            setOf("cf.playhi.b")
        )
        assertEquals(listOf("cf.playhi.a", "cf.playhi.c"), targets)
    }

    @Test
    fun `force-stop targets keep the selection order and tolerate missing data`() {
        assertEquals(
            listOf("cf.playhi.a", "cf.playhi.b"),
            Main.filterForceStopTargets(listOf("cf.playhi.a", "cf.playhi.b"), emptySet())
        )
        assertEquals(
            listOf("cf.playhi.a"),
            Main.filterForceStopTargets(listOf("cf.playhi.a"), null)
        )
        assertTrue(Main.filterForceStopTargets(null, setOf("cf.playhi.a")).isEmpty())
    }

    private fun appRow(
        packageName: String,
        frozen: Boolean? = null,
        restorableSystemApp: Boolean? = null
    ): Map<String, Any> {
        val row = HashMap<String, Any>()
        row["PackageName"] = packageName
        if (frozen != null) {
            row["Frozen"] = frozen
        }
        if (restorableSystemApp != null) {
            row["RestorableSystemApp"] = restorableSystemApp
        }
        return row
    }
}

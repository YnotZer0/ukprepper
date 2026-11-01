package uk.prepper.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import uk.prepper.app.data.Repo
import uk.prepper.app.logic.PrepMode
import uk.prepper.app.ui.ActivitiesScreen
import uk.prepper.app.ui.BackupScreen
import uk.prepper.app.ui.ChecklistRunScreen
import uk.prepper.app.ui.DashboardScreen
import uk.prepper.app.ui.DrillHistoryScreen
import uk.prepper.app.ui.EditItemScreen
import uk.prepper.app.ui.EnergyScreen
import uk.prepper.app.ui.InventoryScreen
import uk.prepper.app.ui.MealsScreen
import uk.prepper.app.ui.MoreScreen
import uk.prepper.app.ui.PlanSettingsScreen
import uk.prepper.app.ui.PrepareScreen
import uk.prepper.app.ui.PrepperTheme
import uk.prepper.app.ui.ReferenceListScreen
import uk.prepper.app.ui.ReferencePageScreen
import uk.prepper.app.ui.ScanScreen
import uk.prepper.app.work.ExpiryNotifier
import uk.prepper.app.work.ExpiryScheduler

sealed interface Screen {
    data object Dashboard : Screen
    data object Inventory : Screen
    data object Meals : Screen
    data object Prepare : Screen
    data object More : Screen
    data class EditItem(val id: Long = 0, val barcode: String? = null, val ration: Boolean = false) : Screen
    data object Scan : Screen
    data class RunChecklist(val mode: PrepMode, val drill: Boolean) : Screen
    data object DrillHistory : Screen
    data object Activities : Screen
    data object Energy : Screen
    data object PlanSettings : Screen
    data object Reference : Screen
    data class ReferencePage(val file: String) : Screen
    data object Backup : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Dashboard)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun replace(s: Screen) { stack.removeAt(stack.lastIndex); stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(s: Screen) { stack.clear(); stack.add(s) }
}

private val tabs = listOf(
    Screen.Dashboard to ("🛡" to "Ready"),
    Screen.Inventory to ("📦" to "Stock"),
    Screen.Meals to ("🍲" to "Meals"),
    Screen.Prepare to ("⚠" to "Prepare"),
    Screen.More to ("☰" to "More"),
)

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        ExpiryNotifier.createChannel(this)
        ExpiryScheduler.schedule(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { PrepperTheme { App() } }
    }
}

@Composable
fun App() {
    val nav = remember { Nav() }
    val current = nav.current
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    val isTab = tabs.any { it.first == current }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { (screen, labels) ->
                    NavigationBarItem(
                        selected = current == screen,
                        onClick = { nav.tab(screen) },
                        icon = { Text(labels.first) },
                        label = { Text(labels.second) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = current) {
                Screen.Dashboard -> DashboardScreen(nav)
                Screen.Inventory -> InventoryScreen(nav)
                Screen.Meals -> MealsScreen(nav)
                Screen.Prepare -> PrepareScreen(nav)
                Screen.More -> MoreScreen(nav)
                is Screen.EditItem -> EditItemScreen(nav, s.id, s.barcode, s.ration)
                Screen.Scan -> ScanScreen(nav)
                is Screen.RunChecklist -> ChecklistRunScreen(nav, s.mode, s.drill)
                Screen.DrillHistory -> DrillHistoryScreen(nav)
                Screen.Activities -> ActivitiesScreen(nav)
                Screen.Energy -> EnergyScreen(nav)
                Screen.PlanSettings -> PlanSettingsScreen(nav)
                Screen.Reference -> ReferenceListScreen(nav)
                is Screen.ReferencePage -> ReferencePageScreen(nav, s.file)
                Screen.Backup -> BackupScreen(nav)
            }
        }
    }
}

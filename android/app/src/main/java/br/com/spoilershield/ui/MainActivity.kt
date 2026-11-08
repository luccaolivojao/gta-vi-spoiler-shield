package br.com.spoilershield.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.spoilershield.ui.screens.BlockScreen
import br.com.spoilershield.ui.screens.HomeScreen
import br.com.spoilershield.ui.screens.SettingsScreen
import br.com.spoilershield.ui.theme.Neon
import br.com.spoilershield.ui.theme.ShieldTheme

class MainActivity : ComponentActivity() {
    private val shield: ShieldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShieldTheme { MainScaffold(shield) } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(shield: ShieldViewModel) {
    val tabs = remember {
        listOf(
            Tab("INÍCIO", Icons.Default.Home),
            Tab("BLOQUEIO", Icons.Default.Lock),
            Tab("CONFIG.", Icons.Default.Settings),
        )
    }
    var selected by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { shield.refreshPermissions() }

    Scaffold(
        containerColor = Neon.Black,
        bottomBar = {
            NavigationBar(containerColor = Neon.Surface) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = selected == i, onClick = { selected = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Neon.Red, selectedTextColor = Neon.Red,
                            indicatorColor = Neon.RedDeep.copy(alpha = 0.35f),
                            unselectedIconColor = Neon.Muted, unselectedTextColor = Neon.Muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (selected) {
                0 -> HomeScreen(shield, goToBlock = { selected = 1 })
                1 -> BlockScreen(shield)
                else -> SettingsScreen(shield)
            }
        }
    }
}

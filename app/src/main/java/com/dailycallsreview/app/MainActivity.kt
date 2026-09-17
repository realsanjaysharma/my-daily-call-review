package com.dailycallsreview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.dailycallsreview.app.ui.nav.AppNavGraph
import com.dailycallsreview.app.ui.permissions.PermissionsGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DailyCallsReviewApplication
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    PermissionsGate {
                        AppNavGraph(app = app, navController = navController)
                    }
                }
            }
        }
    }
}

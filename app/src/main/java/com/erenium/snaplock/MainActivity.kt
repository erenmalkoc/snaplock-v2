package com.erenium.snaplock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import com.erenium.snaplock.ui.navigation.AppNavigation
import com.erenium.snaplock.ui.theme.SnaplockV2Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var lockDatabaseUseCase: LockDatabaseUseCase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnaplockV2Theme {
             AppNavigation()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            lockDatabaseUseCase.invoke()
        }
    }
}




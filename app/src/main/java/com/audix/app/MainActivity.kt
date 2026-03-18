package com.audix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.audix.app.audio.EqEngine
import com.audix.app.ui.theme.AudixTheme

class MainActivity : ComponentActivity() {
    private val eqEngine = EqEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eqEngine.initialize()
        
        enableEdgeToEdge()
        setContent {
            AudixTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EqControls(
                        eqEngine = eqEngine,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        eqEngine.release()
    }
}

@Composable
fun EqControls(eqEngine: EqEngine, modifier: Modifier = Modifier) {
    var isBassBoostEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Audix Basic EQ Engine")
        Button(
            onClick = {
                val newState = !isBassBoostEnabled
                isBassBoostEnabled = newState
                eqEngine.toggleBassBoost(newState)
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(if (isBassBoostEnabled) "Disable Bass Boost" else "Enable Bass Boost")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EqControlsPreview() {
    AudixTheme {
        EqControls(EqEngine())
    }
}
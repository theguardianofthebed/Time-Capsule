package com.example.timecapsule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(onExplorePreview: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TimeCapsule", style = MaterialTheme.typography.headlineLarge)
        Text("Messages for the right moment", modifier = Modifier.padding(top = 8.dp))
        Text(
            "Authentication has not been integrated. " +
                "The next screen is a navigation preview, not a signed-in account.",
            modifier = Modifier.padding(vertical = 24.dp),
        )
        Button(onClick = onExplorePreview) { Text("Explore UI preview") }
    }
}

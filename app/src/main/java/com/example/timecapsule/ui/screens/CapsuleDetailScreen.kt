package com.example.timecapsule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CapsuleDetailScreen(capsuleId: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text("Capsule detail", style = MaterialTheme.typography.headlineSmall)
        Text("ID: $capsuleId")
        Text("Protected content is NOT loaded in this skeleton.")
        Text("Real content reads must pass server time/access checks and the client's location checks.")
    }
}

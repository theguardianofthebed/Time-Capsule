package com.example.timecapsule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateCapsuleScreen(onBack: () -> Unit) {
    var message by rememberSaveable { mutableStateOf("") }
    var recipientCode by rememberSaveable { mutableStateOf("") }
    var selectedLock by rememberSaveable { mutableStateOf("Time") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text("Create capsule", style = MaterialTheme.typography.headlineSmall)
        Text("UI scaffold only — backend-confirmed send is not implemented.")
        OutlinedTextField(
            value = recipientCode,
            onValueChange = { recipientCode = it },
            label = { Text("Recipient UID code (future lookup)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Unlock condition (selection preview)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Time", "Location", "Both").forEach { mode ->
                FilterChip(
                    selected = selectedLock == mode,
                    onClick = { selectedLock = mode },
                    label = { Text(mode) },
                )
            }
        }
        Text("Unlock time/location input and server eligibility are not integrated.")
        Text("Photo Picker pending: one JPEG/PNG photo, up to 5 MiB.")
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Send unavailable until integration")
        }
    }
}

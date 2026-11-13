package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.JourneyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if there was an active journey before device reboot
            CoroutineScope(Dispatchers.IO).launch {
                val repository = JourneyApplication.instance.repository
                val activeJourney = repository.getActiveJourney()
                if (activeJourney != null) {
                    // Mark as interrupted to ensure accurate timestamps and avoid fabricating data
                    repository.markActiveJourneysInterrupted()
                }
            }
        }
    }
}

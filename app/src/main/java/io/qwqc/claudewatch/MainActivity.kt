package io.qwqc.claudewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.qwqc.claudewatch.fcm.PushTokenRegistrar
import io.qwqc.claudewatch.presentation.ClaudeApp
import io.qwqc.claudewatch.presentation.theme.ClaudeWatchTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClaudeWatchTheme { ClaudeApp() }
        }
        registerFcmTokenIfAvailable()
    }

    /**
     * Grab the current FCM token and push it to the server over SSH. Guarded so
     * the app still runs before Firebase (google-services.json) is wired up.
     */
    private fun registerFcmTokenIfAvailable() {
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                lifecycleScope.launch {
                    Graph.settingsStore.saveFcmToken(token)
                    PushTokenRegistrar(Graph.sshManager, Graph.settingsStore).registerIfPossible(token)
                }
            }
        }
    }
}

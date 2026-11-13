package com.openjarvis.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import com.openjarvis.agent.ActionApprovalCoordinator

/**
 * Not exported. No approval can be initiated using an external Activity intent.
 * Completion is deferred until this screen has closed so Accessibility acts on
 * the intended foreground application rather than the approval dialog.
 */
class ActionApprovalActivity : ComponentActivity() {
    private var requestId: String? = null
    private var approvalScheduled = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val id = intent.getStringExtra(ActionApprovalCoordinator.EXTRA_REQUEST_ID)
        if (id.isNullOrBlank()) {
            finish()
            return
        }
        requestId = id
        val request = ActionApprovalCoordinator.getRequest(id)
        if (request == null) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = { deny() },
                    title = { Text("Allow Jarvis to perform this action?") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(request.details)
                            Text("\nWithout approval this action is blocked.")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { allow() }) { Text("Allow") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deny() }) { Text("Deny") }
                    }
                )
            }
        }
        handler.postDelayed({
            if (ActionApprovalCoordinator.getRequest(id) == null) deny()
        }, 31_000L)
    }

    private fun allow() {
        val id = requestId ?: return
        if (approvalScheduled || ActionApprovalCoordinator.getRequest(id) == null) {
            finish()
            return
        }
        approvalScheduled = true
        finish()
        // Give Android time to restore the app underneath the approval screen.
        Handler(Looper.getMainLooper()).postDelayed({
            ActionApprovalCoordinator.respond(id, true)
        }, 500L)
    }

    private fun deny() {
        requestId?.let { ActionApprovalCoordinator.respond(it, false) }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (isFinishing && !approvalScheduled) {
            requestId?.let { ActionApprovalCoordinator.respond(it, false) }
        }
        super.onDestroy()
    }
}

/** Internal broadcast only: dismissal of an approval notification denies. */
class ActionApprovalCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(ActionApprovalCoordinator.EXTRA_REQUEST_ID)?.let {
            ActionApprovalCoordinator.respond(it, false)
        }
    }
}

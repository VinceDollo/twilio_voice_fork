package com.twilio.twilio_voice.fcm

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.*
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.twilio.twilio_voice.receivers.TVBroadcastReceiver
import com.twilio.twilio_voice.service.TVConnectionService
import com.twilio.twilio_voice.storage.StorageImpl
import com.twilio.twilio_voice.types.TelecomManagerExtension.canReadPhoneNumbers
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.MessageListener
import com.twilio.voice.Voice
import com.twilio.twilio_voice.types.TelecomManagerExtension.canReadPhoneState
import com.twilio.twilio_voice.types.TelecomManagerExtension.hasCallCapableAccount

class VoiceFirebaseMessagingService : FirebaseMessagingService(), MessageListener {

    companion object {
        private const val TAG = "VoiceFirebaseMessagingService"

        /**
         * Action used with [EXTRA_TOKEN] to send the FCM token to the TwilioVoicePlugin
         */
        const val ACTION_NEW_TOKEN = "ACTION_NEW_TOKEN"

        /**
         * Extra used with [ACTION_NEW_TOKEN] to send the FCM token to the TwilioVoicePlugin
         */
        const val EXTRA_FCM_TOKEN = "token"

        /**
         * Extra used with [ACTION_NEW_TOKEN] to send the FCM token to the TwilioVoicePlugin
         */
        const val EXTRA_TOKEN = "token"
    }


    override fun onNewToken(token: String) {
        val intent = Intent(ACTION_NEW_TOKEN).also {
            it.putExtra(EXTRA_FCM_TOKEN, token)
        }
        sendBroadcast(intent)
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Received onMessageReceived()")
        Log.d(TAG, "Bundle data: " + remoteMessage.data)
        Log.d(TAG, "From: " + remoteMessage.from)
        // If application is running in the foreground use local broadcast to handle message.
        // Otherwise use the background isolate to handle message.
        if (remoteMessage.data.isNotEmpty()) {
            val valid = Voice.handleMessage(this, remoteMessage.data, this)
            if (!valid) {
                Log.d(TAG, "onMessageReceived: The message was not a valid Twilio Voice SDK payload, continuing...")
            }
        }
    }

    //region MessageListener
    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS])
    @SuppressLint("MissingPermission")
    override fun onCallInvite(callInvite: CallInvite) {
        Log.d(
            TAG,
            "onCallInvite: {\n\t" +
                    "CallSid: ${callInvite.callSid}, \n\t" +
                    "From: ${callInvite.from}, \n\t" +
                    "To: ${callInvite.to}, \n\t" +
                    "Parameters: ${callInvite.customParameters.entries.joinToString { "${it.key}:${it.value}" }},\n\t" +
                    "}"
        )
    }

    override fun onCancelledCallInvite(cancelledCallInvite: CancelledCallInvite, callException: CallException?) {
        Log.d(TAG, "onCancelledCallInvite: ", callException)
        Intent(applicationContext, TVConnectionService::class.java).apply {
            action = TVConnectionService.ACTION_CANCEL_CALL_INVITE
            putExtra(TVConnectionService.EXTRA_CANCEL_CALL_INVITE, cancelledCallInvite)
            Log.d(TAG, "check android version")
//            applicationContext.startService(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(this) // Ensure it's started as a foreground service
            } else {
                applicationContext.startService(this)
            }
        }
    }
    //endregion
}

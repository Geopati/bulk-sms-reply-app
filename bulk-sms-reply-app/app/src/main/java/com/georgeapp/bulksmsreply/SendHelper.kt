package com.georgeapp.bulksmsreply

import android.telephony.SmsManager

/**
 * Wraps SmsManager so the rest of the app doesn't need to think about
 * multi-part messages. Requires the SEND_SMS runtime permission to
 * already be granted - callers should check that before invoking this.
 */
object SendHelper {

    /** Result of trying to send to every requested address. */
    data class SendResult(
        val succeeded: List<String>,
        val failed: List<Pair<String, Exception>>
    )

    fun sendToAll(addresses: List<String>, message: String): SendResult {
        val smsManager = SmsManager.getDefault()
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, Exception>>()

        for (address in addresses) {
            try {
                if (message.length > 160) {
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(address, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(address, null, message, null, null)
                }
                succeeded.add(address)
            } catch (e: Exception) {
                failed.add(address to e)
            }
        }

        return SendResult(succeeded, failed)
    }

    /**
     * Forwards a message's original text to the carrier spam-reporting
     * short code (7726 / "SPAM"). This mirrors what most US carriers ask
     * you to do manually: forward the unwanted text to 7726. Carriers'
     * exact expectations for the forwarded body vary, so this sends the
     * original message body as-is, which matches the most common guidance.
     */
    fun reportAsSpam(originalMessageBody: String): SendResult {
        return sendToAll(listOf(SPAM_REPORT_SHORT_CODE), originalMessageBody)
    }

    const val SPAM_REPORT_SHORT_CODE = "7726"
}

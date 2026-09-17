package com.example.triage

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lit les notifications postees par WhatsApp et les stocke localement.
 *
 * Utilise NotificationListenerService, une API Android publique et documentee.
 * L'utilisateur doit accorder l'acces aux notifications dans les reglages systeme :
 * l'app ne peut rien lire tant que ce n'est pas fait.
 *
 * Rien n'est envoye sur le reseau. Tout reste dans les SharedPreferences de l'app.
 */
class WhatsAppListenerService : NotificationListenerService() {

    companion object {
        const val PREFS = "triage_store"
        const val KEY_MESSAGES = "messages"
        const val MAX_MESSAGES = 500

        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        /** Le plugin Capacitor s'abonne ici pour recevoir les messages en direct. */
        var onMessage: ((JSONObject) -> Unit)? = null

        fun storedMessages(context: Context): JSONArray {
            val raw = context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MESSAGES, "[]") ?: "[]"
            return try {
                JSONArray(raw)
            } catch (e: Exception) {
                JSONArray()
            }
        }

        fun clearMessages(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MESSAGES, "[]")
                .apply()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return

        val notification = sbn.notification ?: return

        // La notification de regroupement ("5 messages de 3 discussions")
        // n'apporte rien : on ne garde que les notifications de conversation.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val conversation = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

        if (conversation.isNullOrEmpty() || body.isNullOrEmpty()) return

        // WhatsApp poste aussi des notifications de service ("Sauvegarde en cours",
        // "Verification des nouveaux messages") sans conversation reelle.
        if (conversation.equals("WhatsApp", ignoreCase = true)) return

        val isGroup = extras.getBoolean("android.isGroupConversation", false)

        // Dans un groupe, WhatsApp prefixe le corps par l'expediteur : "Marie: salut".
        var sender = conversation
        var text = body
        if (isGroup) {
            val split = body.indexOf(": ")
            if (split in 1..40) {
                sender = body.substring(0, split)
                text = body.substring(split + 2)
            }
        }

        // Tentative (non garantie) de recuperer le numero : WhatsApp met parfois
        // un identifiant de conversation du type "336...@s.whatsapp.net".
        var phone = ""
        try {
            val candidates = mutableListOf<String?>()
            if (Build.VERSION.SDK_INT >= 26) candidates.add(notification.shortcutId)
            if (Build.VERSION.SDK_INT >= 29) candidates.add(notification.locusId?.id)
            for (cand in candidates) {
                if (cand == null) continue
                val match = Regex("(\\d{6,15})@s\\.whatsapp\\.net").find(cand)
                if (match != null) { phone = match.groupValues[1]; break }
            }
        } catch (e: Exception) {}

        val message = JSONObject().apply {
            put("id", "${sbn.postTime}-${conversation.hashCode()}")
            put("conversation", conversation)
            put("sender", sender)
            put("text", text)
            put("isGroup", isGroup)
            put("postTime", sbn.postTime)
            put("phone", phone)
            put("business", sbn.packageName == "com.whatsapp.w4b")
        }

        persist(message)
        onMessage?.invoke(message)
    }

    private fun persist(message: JSONObject) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = storedMessages(this)
        val id = message.optString("id")

        // WhatsApp reposte la meme notification a chaque nouveau message
        // de la conversation : on evite les doublons stricts.
        for (i in 0 until stored.length()) {
            if (stored.optJSONObject(i)?.optString("id") == id) return
        }

        stored.put(message)

        // On plafonne l'historique pour ne pas laisser grossir les preferences.
        val trimmed = if (stored.length() > MAX_MESSAGES) {
            JSONArray().also { out ->
                for (i in stored.length() - MAX_MESSAGES until stored.length()) {
                    out.put(stored.get(i))
                }
            }
        } else stored

        prefs.edit().putString(KEY_MESSAGES, trimmed.toString()).apply()
    }
}

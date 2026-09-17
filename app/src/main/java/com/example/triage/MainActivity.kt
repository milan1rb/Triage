package com.example.triage

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.app.NotificationManagerCompat

/**
 * Une WebView plein ecran qui charge l'interface, plus un pont expose
 * au JavaScript sous le nom "AndroidBridge".
 *
 * Les methodes annotees @JavascriptInterface sont appelees depuis un thread
 * secondaire : tout ce qui touche a l'interface passe par runOnUiThread.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true   // necessaire pour localStorage
            addJavascriptInterface(Bridge(this@MainActivity), "AndroidBridge")
            // Sans WebChromeClient, les dialogues JS (prompt/alert) sont ignores
            // par la WebView : on active le comportement par defaut.
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(web)
    }

    override fun onResume() {
        super.onResume()
        // De retour dans Triage : on retire la bulle...
        Bubble.hide(this)
        // ...et on redemande la liste a jour.
        if (::web.isInitialized) {
            web.evaluateJavascript("window.onAppResume && window.onAppResume()", null)
        }
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    class Bridge(private val activity: Activity) {

        @JavascriptInterface
        fun isEnabled(): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(activity)
                .contains(activity.packageName)

        @JavascriptInterface
        fun openSettings() {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }

        /** Renvoie tout l'historique capte, en JSON. */
        @JavascriptInterface
        fun getMessages(): String =
            WhatsAppListenerService.storedMessages(activity).toString()

        @JavascriptInterface
        fun clearMessages() {
            WhatsAppListenerService.clearMessages(activity)
        }

        /** La bulle flottante est-elle autorisee (permission overlay) ? */
        @JavascriptInterface
        fun overlayAllowed(): Boolean = Settings.canDrawOverlays(activity)

        /** Ouvre les reglages systeme pour accorder l'autorisation overlay. */
        @JavascriptInterface
        fun requestOverlay() {
            activity.runOnUiThread {
                try {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + activity.packageName)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {}
            }
        }

        @JavascriptInterface
        fun showBubble() { activity.runOnUiThread { Bubble.show(activity) } }

        @JavascriptInterface
        fun hideBubble() { activity.runOnUiThread { Bubble.hide(activity) } }

        /** Le repertoire est-il autorise ? */
        @JavascriptInterface
        fun contactsAllowed(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                activity.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

        /** Demande l'acces au repertoire. */
        @JavascriptInterface
        fun requestContacts() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.runOnUiThread {
                    activity.requestPermissions(
                        arrayOf(android.Manifest.permission.READ_CONTACTS), 1001
                    )
                }
            }
        }

        /**
         * Cherche dans le repertoire un contact dont le nom correspond, et renvoie
         * son numero au format international sans + (chaine vide si rien trouve).
         */
        @JavascriptInterface
        fun lookupNumber(name: String): String {
            if (!contactsAllowed() || name.isBlank()) return ""
            return try {
                val cursor = activity.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?",
                    arrayOf(name),
                    null
                )
                var raw = ""
                cursor?.use { if (it.moveToFirst()) raw = it.getString(0) ?: "" }
                normalizeNumber(raw)
            } catch (e: Exception) {
                ""
            }
        }

        /** Nettoie un numero du repertoire vers un format international sans +. */
        private fun normalizeNumber(input: String): String {
            var s = input.filter { it.isDigit() || it == '+' }
            if (s.startsWith("+")) s = s.drop(1).filter { it.isDigit() }
            else s = s.filter { it.isDigit() }
            if (s.startsWith("00")) s = s.drop(2)
            // Numero national francais (0X sur 10 chiffres) -> prefixe 33.
            if (s.length == 10 && s.startsWith("0")) s = "33" + s.drop(1)
            return s
        }

        /**
         * Ouvre une conversation dans le vrai WhatsApp.
         *
         * Lance depuis l'activite et sans FLAG_ACTIVITY_NEW_TASK : WhatsApp
         * s'empile dans la meme tache, donc le bouton retour ramene ici.
         *
         * @param phone numero international sans + ni espaces, ou chaine vide.
         */
        @JavascriptInterface
        fun openChat(phone: String) {
            val digits = phone.filter { it.isDigit() }
            activity.runOnUiThread {
                try {
                    val intent = if (digits.isNotEmpty()) {
                        // Deep link qui ouvre directement la conversation
                        Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$digits"))
                            .setPackage("com.whatsapp")
                    } else {
                        activity.packageManager.getLaunchIntentForPackage("com.whatsapp")
                            ?: error("WhatsApp introuvable")
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        activity.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
                        )
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }
}

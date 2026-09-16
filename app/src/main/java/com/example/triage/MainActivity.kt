package com.example.triage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        // Au retour depuis WhatsApp, on redemande la liste a jour.
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
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
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

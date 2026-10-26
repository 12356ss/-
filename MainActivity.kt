package cn.schoolwifi.autologin

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.JavascriptInterface
import android.widget.*
import android.text.InputType
import android.content.ClipData
import android.content.ClipboardManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var userBox: EditText
    private lateinit var passBox: EditText
    private lateinit var autoSubmit: CheckBox
    private lateinit var status: TextView
    private lateinit var web: WebView
    private var webMode = false

    companion object {
        private const val PREFS = "local_secure_settings"
        private const val KEY_ALIAS = "school_wifi_local_key"
        private const val URL = "http://10.254.253.250/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSettings()
    }

    private fun showSettings() {
        webMode = false
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "School-Wifi 自动认证"
            textSize = 24f
            setTextColor(Color.BLACK)
        }, match(ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "认证地址：$URL\\n账号密码只保存在本机。首次建议关闭自动提交，确认页面字段正确后再开启。"
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }, params(top = 10))

        userBox = EditText(this).apply {
            hint = "学号"
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
        }
        passBox = EditText(this).apply {
            hint = "校园网密码"
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val saved = loadSecrets()
        userBox.setText(saved.first)
        passBox.setText(saved.second)
        root.addView(userBox, params(top = 18))
        root.addView(passBox, params(top = 8))

        autoSubmit = CheckBox(this).apply {
            text = "尝试自动提交登录表单（首次建议不要勾选）"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            isChecked = getPreferences(MODE_PRIVATE).getBoolean("auto_submit", false)
        }
        root.addView(autoSubmit, params(top = 8))

        val save = Button(this).apply {
            text = "加密保存并打开认证页面"
            setOnClickListener {
                val u = userBox.text.toString()
                val p = passBox.text.toString()
                if (u.isBlank() || p.isBlank()) {
                    status.text = "请先在本机填写学号和密码。"
                } else {
                    try {
                        saveSecrets(u, p)
                        getPreferences(MODE_PRIVATE).edit().putBoolean("auto_submit", autoSubmit.isChecked).apply()
                        hideKeyboard()
                        showWeb()
                    } catch (e: Exception) {
                        status.text = "本机加密保存失败：${e.javaClass.simpleName}"
                    }
                }
            }
        }
        root.addView(save, params(top = 16, height = 52))

        val open = Button(this).apply {
            text = "仅打开认证页面（不保存）"
            setOnClickListener { hideKeyboard(); showWeb() }
        }
        root.addView(open, params(top = 8, height = 48))

        status = TextView(this).apply {
            text = "提示：本工具不会把账号密码发送给 ChatGPT。登录表单由学校页面处理。"
            textSize = 13f
            setTextColor(Color.GRAY)
        }
        root.addView(status, params(top = 12))
        setContentView(root)
    }

    private fun showWeb() {
        webMode = true
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        bar.addView(Button(this).apply {
            text = "返回设置"
            setOnClickListener { web.stopLoading(); showSettings() }
        })
        bar.addView(Button(this).apply {
            text = "重新打开"
            setOnClickListener { web.loadUrl(URL) }
        })
        layout.addView(bar, match(ViewGroup.LayoutParams.WRAP_CONTENT))
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.addJavascriptInterface(FormBridge(), "SchoolWifiApp")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!url.startsWith("http://10.254.253.250")) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "页面跳转到其他地址；请确认是学校认证页面。", Toast.LENGTH_LONG).show() }
                    return
                }
                injectAutofill()
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                return !(u.startsWith("http://10.254.253.250") || u.startsWith("https://10.254.253.250"))
            }
        }
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        web.loadUrl(URL)
    }

    private inner class FormBridge {
        @JavascriptInterface fun credentials(): String {
            val pair = loadSecrets()
            // JSON string literals are escaped by JSONObject.
            return org.json.JSONObject().put("username", pair.first).put("password", pair.second)
                .put("autoSubmit", getPreferences(MODE_PRIVATE).getBoolean("auto_submit", false)).toString()
        }
    }

    private fun injectAutofill() {
        val script = """
            (function() {
              try {
                var cfg = JSON.parse(SchoolWifiApp.credentials());
                var fields = Array.from(document.querySelectorAll('input'));
                if (!fields.length) return;
                var user = fields.find(e => /user|account|student|login|name|学号|账号/i.test((e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')))
                         || fields.find(e => !/password|hidden|submit|button|checkbox/i.test(e.type||'') && (e.type||'text') !== 'password');
                var pass = fields.find(e => (e.type||'').toLowerCase() === 'password')
                        || fields.find(e => /pass|pwd|密码/i.test((e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')));
                if (user && cfg.username) {
                  var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                  setter.call(user, cfg.username);
                  user.dispatchEvent(new Event('input', {bubbles:true}));
                  user.dispatchEvent(new Event('change', {bubbles:true}));
                }
                if (pass && cfg.password) {
                  var setter2 = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                  setter2.call(pass, cfg.password);
                  pass.dispatchEvent(new Event('input', {bubbles:true}));
                  pass.dispatchEvent(new Event('change', {bubbles:true}));
                }
                if (cfg.autoSubmit && user && pass) {
                  var form = pass.form || user.form;
                  if (form) {
                    var btn = form.querySelector('button[type=submit],input[type=submit],button:not([type])');
                    if (btn) btn.click(); else form.requestSubmit ? form.requestSubmit() : form.submit();
                  }
                }
              } catch(e) { console.log('SchoolWifi autofill error', e); }
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    private fun saveSecrets(username: String, password: String) {
        val json = org.json.JSONObject().put("u", username).put("p", password).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json)
        val packed = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size).put(cipher.iv).put(encrypted).array()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("credentials", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    private fun loadSecrets(): Pair<String, String> {
        return try {
            val encoded = getSharedPreferences(PREFS, MODE_PRIVATE).getString("credentials", null) ?: return "" to ""
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val b = ByteBuffer.wrap(packed)
            val ivSize = b.int
            if (ivSize !in 12..16 || packed.size <= 4 + ivSize) return "" to ""
            val iv = ByteArray(ivSize); b.get(iv)
            val data = ByteArray(b.remaining()); b.get(data)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val obj = org.json.JSONObject(String(cipher.doFinal(data), Charsets.UTF_8))
            obj.optString("u") to obj.optString("p")
        } catch (_: Exception) { "" to "" }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return gen.generateKey()
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun match(h: Int) = LinearLayout.LayoutParams(-1, h)
    private fun params(top: Int = 0, height: Int = -2) =
        LinearLayout.LayoutParams(-1, height).apply { topMargin = dp(top) }

    override fun onBackPressed() {
        if (webMode) showSettings() else super.onBackPressed()
    }
}

package com.zenpanel.app

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zenpanel.app.databinding.ActivityExtensionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native port of the PWA extension simulator window.
 */
class ExtensionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExtensionBinding
    private lateinit var store: PanelStore
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExtensionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = PanelStore(this)

        binding.buttonExtSearch.setOnClickListener { runSearch() }
        binding.buttonClose.setOnClickListener { finish() }

        showPlaceholder()
    }

    private fun runSearch() {
        val query = binding.inputExtSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return

        store.logTraffic("${timeFormat.format(Date())}  ⇄  $query")
        binding.containerResults.removeAllViews()
        for (i in 1..3) {
            addResult("$i. Результат для «$query»")
        }
        binding.inputExtSearch.setText("")
    }

    private fun addResult(text: String) {
        val view = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@ExtensionActivity, R.color.text_primary))
            setPadding(24, 28, 24, 28)
            isClickable = true
            setOnClickListener {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(text)))
                )
            }
        }
        binding.containerResults.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun showPlaceholder() {
        val view = TextView(this).apply {
            text = getString(R.string.extension_hint)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@ExtensionActivity, R.color.text_muted))
            typeface = Typeface.DEFAULT
            setPadding(24, 56, 24, 56)
        }
        binding.containerResults.addView(view)
    }
}

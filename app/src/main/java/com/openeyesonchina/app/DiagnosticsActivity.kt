package com.openeyesonchina.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.openeyesonchina.app.databinding.ActivityDiagnosticsBinding
import java.text.DateFormat

class DiagnosticsActivity : ComponentActivity() {
    private lateinit var binding: ActivityDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        refreshStats()
    }

    private fun refreshStats() {
        val stats = OfflineWebViewClient.getCacheStats(this)
        val prefs = getSharedPreferences("prefetch_meta", MODE_PRIVATE)
        val version = prefs.getString("version", "-")
        val checksum = prefs.getString("checksum", "-")
        val lastTs = prefs.getLong("last_ts", 0L)
        val lastStr = if (lastTs > 0) DateFormat.getDateTimeInstance().format(lastTs) else "-"
        binding.info.text = buildString {
            appendLine("Disk bytes: ${stats.diskBytes}")
            appendLine("Manifest entries: ${stats.manifestEntries}")
            appendLine("HTML in-memory: ${stats.htmlInMemory}")
            appendLine("Assets in-memory: ${stats.assetInMemory}")
            appendLine("Image LRU approx bytes: ${stats.imageLruBytes}")
            appendLine("Dynamic version: $version")
            appendLine("Dynamic checksum: $checksum")
            appendLine("Last dynamic refresh: $lastStr")
        }
        binding.refresh.setOnClickListener { refreshStats() }
    }
}

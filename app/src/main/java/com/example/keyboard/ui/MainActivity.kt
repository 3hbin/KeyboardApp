package com.example.keyboard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.keyboard.databinding.ActivityMainBinding
import com.example.keyboard.settings.Prefs
import com.example.keyboard.settings.SettingsActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            prefs.bgImageUri = uri.toString()
            Toast.makeText(this, "Đã đặt nền bàn phím. Mở lại bàn phím để xem.", Toast.LENGTH_LONG).show()
        }
    }

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Toast.makeText(this, if (ok) "Đã cấp quyền Micro" else "Cần quyền Micro để gõ bằng giọng nói", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        binding.btnPick.setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Extra buttons if present in layout - add programmatically below test field
        binding.root.post {
            val parent = binding.etTest.parent?.parent as? android.view.ViewGroup
            // Use existing buttons only; gallery via settings
        }

        binding.btnBg.setOnClickListener { pickBackground() }
        binding.btnClearBg.setOnClickListener {
            prefs.bgImageUri = ""
            Toast.makeText(this, "Đã xóa nền", Toast.LENGTH_SHORT).show()
        }
        ensureMicPermission()
    }

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun pickBackground() {
        pickImage.launch("image/*")
    }
}

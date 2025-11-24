package com.example.driverfatiguedetection

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val input = findViewById<TextInputEditText>(R.id.inputNumber)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnStart = findViewById<MaterialButton>(R.id.btnStart)

        // preload saved number if any
        Prefs.getEmergencyNumber(this)?.let { input.setText(it) }

        btnSave.setOnClickListener {
            val number = input.text?.toString()?.trim().orEmpty()
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a phone number", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setEmergencyNumber(this, number)
                Toast.makeText(this, "Saved: $number", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener {
            val number = input.text?.toString()?.trim().orEmpty()
            if (number.isNotEmpty()) Prefs.setEmergencyNumber(this, number)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

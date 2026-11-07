package com.example.timecapsule

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.timecapsule.databinding.ActivityMainBinding

/**
 * Main activity for the TimeCapsule application using native Views and ViewBinding.
 * Serves as the base container for S-01 through S-08 screen placeholders.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the layout using generated ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Title text is accessible via ViewBinding
        binding.tvMainTitle.text = getString(R.string.app_name)
    }
}

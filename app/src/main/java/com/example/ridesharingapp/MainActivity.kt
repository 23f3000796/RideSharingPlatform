package com.example.ridesharingapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ridesharingapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.cardOfferRide.setOnClickListener {
            startActivity(Intent(this, OfferRideActivity::class.java))
        }

        binding.cardFindRide.setOnClickListener {
            startActivity(Intent(this, SearchRideActivity::class.java))
        }
    }
}
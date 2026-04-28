package com.example.ridesharingapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.example.ridesharingapp.databinding.ActivityMapPickerBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val RESULT_LATITUDE = "latitude"
        const val RESULT_LONGITUDE = "longitude"
        const val RESULT_ADDRESS = "address"
        private const val LOCATION_PERMISSION_CODE = 1001
        private const val DEFAULT_ZOOM = 15f
    }

    private lateinit var binding: ActivityMapPickerBinding
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var selectedAddress: String = ""

    // Places Autocomplete launcher
    private lateinit var autocompleteLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Places SDK (safe to call multiple times — skips if already init)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getApiKey())
        }

        // Register autocomplete result handler (must be before lifecycle STARTED)
        setupAutocompleteLauncher()

        // Set title from intent extra
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (!title.isNullOrEmpty()) {
            binding.tvTitle.text = title
        }

        // Init map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Back button
        binding.btnBack.setOnClickListener { finish() }

        // Search bar → opens Places Autocomplete fullscreen
        binding.searchBar.setOnClickListener { launchPlacesAutocomplete() }

        // My Location FAB
        binding.fabMyLocation.setOnClickListener { goToMyLocation() }

        // Confirm button
        binding.btnConfirm.setOnClickListener { confirmLocation() }
    }

    // ───────────────────────── Map Ready ─────────────────────────

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Map UI settings
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isMapToolbarEnabled = false

        // On map tap → place marker + reverse geocode
        map.setOnMapClickListener { latLng ->
            placeMarker(latLng)
            reverseGeocode(latLng)
        }

        // Try to move camera to user's current location
        if (hasLocationPermission()) {
            goToMyLocation()
        } else {
            requestLocationPermission()
            // Default to India center if no permission yet
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 5f))
        }
    }

    // ───────────────────────── Marker ─────────────────────────

    private fun placeMarker(latLng: LatLng) {
        map.clear()
        map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(getString(R.string.map_picker_title_default))
        )
        selectedLat = latLng.latitude
        selectedLng = latLng.longitude
    }

    // ───────────────────────── Reverse Geocode (background thread) ─────────────────────────

    private fun reverseGeocode(latLng: LatLng) {
        // Show loading state + coordinates immediately (main thread, instant)
        binding.tvAddress.text = getString(R.string.map_picker_searching)
        binding.tvCoordinates.text = String.format(Locale.US, "%.6f, %.6f", latLng.latitude, latLng.longitude)
        binding.tvCoordinates.visibility = View.VISIBLE

        // Run Geocoder on IO thread, update UI on Main thread
        lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) {
                fetchAddressFromGeocoder(latLng.latitude, latLng.longitude)
            }

            // Back on Main thread — safe to touch views
            selectedAddress = address
            binding.tvAddress.text = address
        }
    }

    /**
     * Blocking call — must run on background thread only.
     * Returns a readable address string, or fallback on failure.
     */
    private fun fetchAddressFromGeocoder(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lng, 1)

            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                (0..addr.maxAddressLineIndex)
                    .map { addr.getAddressLine(it) }
                    .joinToString(", ")
            } else {
                getString(R.string.map_picker_no_address)
            }
        } catch (e: IOException) {
            getString(R.string.map_picker_no_address)
        }
    }

    // ───────────────────────── Places Autocomplete ─────────────────────────

    /**
     * Reads the API key from AndroidManifest meta-data.
     * Same key used for Maps — no extra key needed.
     */
    private fun getApiKey(): String {
        val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
    }

    /**
     * Registers the ActivityResultLauncher that handles the autocomplete result.
     * Must be called in onCreate BEFORE the activity reaches STARTED state.
     */
    private fun setupAutocompleteLauncher() {
        autocompleteLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(result.data!!)
                    handlePlaceSelected(place)
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status = Autocomplete.getStatusFromIntent(result.data!!)
                    Log.e("MapPicker", "Autocomplete error: ${status.statusMessage}")
                    Toast.makeText(this, R.string.toast_places_fallback, Toast.LENGTH_LONG).show()
                }
                // RESULT_CANCELED → user pressed back, do nothing
            }
        }
    }

    /**
     * Opens Google Places Autocomplete in fullscreen mode.
     * Wrapped in try-catch so the app never crashes if Places SDK isn't ready.
     */
    private fun launchPlacesAutocomplete() {
        try {
            val fields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS
            )

            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .build(this)

            autocompleteLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("MapPicker", "Places launch failed: ${e.message}")
            Toast.makeText(this, R.string.toast_places_fallback, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Called when user selects a place from autocomplete.
     * Moves camera, places marker, updates all UI state.
     */
    private fun handlePlaceSelected(place: Place) {
        val latLng = place.latLng ?: return
        val address = place.address ?: place.name ?: getString(R.string.map_picker_no_address)

        // Move camera to selected place
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))

        // Place marker
        placeMarker(latLng)

        // Store state
        selectedLat = latLng.latitude
        selectedLng = latLng.longitude
        selectedAddress = address

        // Update UI
        binding.tvAddress.text = address
        binding.tvCoordinates.text = String.format(Locale.US, "%.6f, %.6f", latLng.latitude, latLng.longitude)
        binding.tvCoordinates.visibility = View.VISIBLE
    }

    // ───────────────────────── My Location ─────────────────────────

    private fun goToMyLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
                    placeMarker(latLng)
                    reverseGeocode(latLng)
                }
            }
        } catch (e: SecurityException) {
            // Permission denied at runtime
        }
    }

    // ───────────────────────── Permissions ─────────────────────────

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            goToMyLocation()
        }
    }

    // ───────────────────────── Confirm ─────────────────────────

    private fun confirmLocation() {
        if (selectedLat == null || selectedLng == null) {
            Toast.makeText(this, R.string.toast_select_location, Toast.LENGTH_SHORT).show()
            return
        }

        val resultIntent = Intent().apply {
            putExtra(RESULT_LATITUDE, selectedLat!!)
            putExtra(RESULT_LONGITUDE, selectedLng!!)
            putExtra(RESULT_ADDRESS, selectedAddress)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
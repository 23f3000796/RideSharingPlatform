package com.example.ridesharingapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ridesharingapp.databinding.ActivityMapPickerBinding
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Professional Map Picker Activity with Center-Pin Selection logic.
 * Updated to use AutocompleteSupportFragment for robust search integration.
 */
class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AUTO_FETCH = "extra_auto_fetch"
        /** Optional camera start position (e.g. the previously chosen point). */
        const val EXTRA_START_LAT = "extra_start_lat"
        const val EXTRA_START_LNG = "extra_start_lng"
        /** Fallback when there is no GPS fix: campus area used by the seed data (Indore). */
        private val DEFAULT_CENTER = LatLng(22.7196, 75.8577)
        const val RESULT_LATITUDE = "latitude"
        const val RESULT_LONGITUDE = "longitude"
        const val RESULT_ADDRESS = "address"
        private const val LOCATION_PERMISSION_CODE = 1001
        private const val DEFAULT_ZOOM = 17f
        private const val TAG = "MapPicker"
    }

    private lateinit var binding: ActivityMapPickerBinding
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var cancellationTokenSource = CancellationTokenSource()

    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var selectedAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 🛡️ Play Services Check
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            availability.getErrorDialog(this, resultCode, 9000)?.show()
        }

        // Initialize Places SDK
        initPlacesSDK()
        
        // Setup the Autocomplete Widget (Fragment)
        setupAutocompleteFragment()

        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.map_picker_title_default)
        binding.tvTitle.text = title

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_view) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.fabMyLocation.setOnClickListener { goToMyLocation(true) }
        binding.btnConfirm.setOnClickListener { confirmLocation() }
    }

    private fun initPlacesSDK() {
        try {
            if (!Places.isInitialized()) {
                val apiKey = getString(R.string.google_maps_key)
                if (apiKey.isEmpty() || apiKey.startsWith("YOUR_")) {
                    Log.e(TAG, "API Key is missing in strings.xml!")
                }
                Places.initialize(applicationContext, apiKey)
                Log.d(TAG, "Places initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Places init failed", e)
        }
    }

    private fun setupAutocompleteFragment() {
        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                as? AutocompleteSupportFragment

        autocompleteFragment?.apply {
            // Specify the types of place data to return.
            setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS))
            
            // Limit results to India
            setCountries("IN")
            
            // Optional: Customize hint
            setHint(getString(R.string.search_place_hint))

            // Set up a PlaceSelectionListener to handle the response.
            setOnPlaceSelectedListener(object : PlaceSelectionListener {
                override fun onPlaceSelected(place: Place) {
                    Log.i(TAG, "Place selected: ${place.name}, ${place.id}")
                    place.latLng?.let { latLng ->
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
                    }
                }

                override fun onError(status: Status) {
                    Log.e(TAG, "Places Error: $status")
                    if (status.isCanceled) return
                    
                    val msg = status.statusMessage ?: getString(R.string.toast_places_fallback)
                    Toast.makeText(this@MapPickerActivity, msg, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    override fun onMapReady(mMap: GoogleMap) {
        Log.d(TAG, "onMapReady: Map is now ready")
        this.googleMap = mMap
        
        mMap.uiSettings.apply {
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = false
            isCompassEnabled = true
        }

        if (hasLocationPermission()) {
            try { mMap.isMyLocationEnabled = true } catch (_: SecurityException) {}
        }

        mMap.setOnCameraIdleListener {
            val center = mMap.cameraPosition.target
            selectedLat = center.latitude
            selectedLng = center.longitude
            reverseGeocode(center)
        }

        val shouldAutoFetch = intent.getBooleanExtra(EXTRA_AUTO_FETCH, false)
        val hasStart = intent.hasExtra(EXTRA_START_LAT) && intent.hasExtra(EXTRA_START_LNG)
        when {
            hasStart -> {
                val start = LatLng(intent.getDoubleExtra(EXTRA_START_LAT, 0.0), intent.getDoubleExtra(EXTRA_START_LNG, 0.0))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 15f))
            }
            shouldAutoFetch -> {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 13f))
                goToMyLocation(false)
            }
            else -> {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 13f))
                centerOnLastKnownLocation()
            }
        }
    }

    private var geocodeJob: kotlinx.coroutines.Job? = null

    private fun reverseGeocode(latLng: LatLng) {
        binding.tvAddress.text = getString(R.string.map_picker_searching)
        binding.tvCoordinates.text = getString(R.string.map_picker_coordinates, latLng.latitude, latLng.longitude)
        // The address must always describe the current pin: drop stale lookups and block confirm meanwhile.
        selectedAddress = ""
        binding.btnConfirm.isEnabled = false
        geocodeJob?.cancel()
        geocodeJob = lifecycleScope.launch {
            val address = fetchAddress(latLng.latitude, latLng.longitude)
            selectedAddress = address
            binding.tvAddress.text = address
            binding.btnConfirm.isEnabled = true
        }
    }

    private suspend fun fetchAddress(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(this@MapPickerActivity, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lng, 1) { results -> continuation.resume(results) }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
            }
            addresses?.firstOrNull()?.let {
                val thoroughfare = it.thoroughfare ?: it.featureName ?: ""
                val locality = it.locality ?: ""
                if (thoroughfare.isNotEmpty()) "$thoroughfare, $locality" else it.getAddressLine(0) ?: getString(R.string.map_picker_title_default)
            } ?: getString(R.string.map_picker_title_default)
        } catch (_: Exception) {
            getString(R.string.map_picker_title_default)
        }
    }

    private fun goToMyLocation(isManual: Boolean) {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (isManual) {
                Toast.makeText(this, "Please enable GPS", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            return
        }

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
                    } else {
                        // Fallback to last known if fresh fix fails
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            lastLoc?.let {
                                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), DEFAULT_ZOOM))
                            } ?: run { if (isManual) Toast.makeText(this, "Location fix failed", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission issue", e)
        }
    }

    /** Quietly move to the last known fix (no prompts) so the map starts near the user. */
    private fun centerOnLastKnownLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)) }
            }
        } catch (_: SecurityException) {
        }
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            goToMyLocation(false)
            try { googleMap?.isMyLocationEnabled = true } catch (_: SecurityException) {}
        }
    }

    private fun confirmLocation() {
        if (selectedLat == null || selectedLng == null) {
            Toast.makeText(this, R.string.toast_select_location, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedAddress.isBlank() || selectedAddress == getString(R.string.map_picker_title_default)) {
            // Geocoder failed (offline / no service): fall back to coordinates so the trip is still usable.
            selectedAddress = String.format(Locale.US, "Pinned location (%.5f, %.5f)", selectedLat, selectedLng)
        }
        val resultIntent = Intent().apply {
            putExtra(RESULT_LATITUDE, selectedLat!!)
            putExtra(RESULT_LONGITUDE, selectedLng!!)
            putExtra(RESULT_ADDRESS, selectedAddress)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancellationTokenSource.cancel()
    }
}

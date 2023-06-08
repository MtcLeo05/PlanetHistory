package com.mtcleo05.planethistory

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonObject
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import org.json.JSONArray
import java.io.IOException
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var mapView: MapView? = null
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var currentLocationMarker: PointAnnotation? = null
    private val activeAnnotations: MutableMap<String, PointAnnotation> = mutableMapOf()

    private lateinit var panelLayout: LinearLayout
    private lateinit var colorChange: LinearLayout

    private var isFirstLocationUpdate = true
    private val PCT = 0.00002 // POSITION CHANGE THRESHOLD
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var searchBarEditText: EditText
    private val LOCATION_PERMISSION_REQUEST_CODE = 177013

    private val ColorTagMap: HashMap<String, String> = HashMap()


    private lateinit var NameText: TextView
    private lateinit var TagsText: TextView

    companion object {
        private const val LOCATION_UPDATE_INTERVAL: Long = 10000 // 10 seconds
        private const val LOCATION_UPDATE_FASTEST_INTERVAL: Long = 5000 // 5 seconds
    }

    data class Marker(
        val description: String,
        val tags: List<String>,
        val mainTag: String,
        val markerName: String,
        val lat: Double,
        val lng: Double,
        val id: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {


        ColorTagMap["Monumenti"] = "#0000FF"
        ColorTagMap["CTM"] = "#FF0000"
        ColorTagMap["Curiosita"] = "#FFFF00"
        ColorTagMap["Parchi"] = "#00FF00"
        ColorTagMap["Epoche"] = "#FF00FF"

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        NameText = findViewById(R.id.NameText)
        TagsText = findViewById(R.id.TagsText)

        colorChange = findViewById(R.id.colorChange)

        panelLayout = findViewById(R.id.panelLayout)
        panelLayout.visibility = View.GONE


        mapView = findViewById(R.id.mapView)
        mapView?.getMapboxMap()?.loadStyleUri("mapbox://styles/ssulf/clhgo1b4901d001qy8wqrgo52")
        pointAnnotationManager = mapView?.annotations?.createPointAnnotationManager()

        mapView?.scalebar?.enabled = false
        mapView?.attribution?.enabled = false
        mapView?.logo?.enabled = false

        locationRequest = LocationRequest.create().apply {
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_UPDATE_FASTEST_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { handleLocationResult(it) }
            }
        }

        val jsonString = loadJSONResource(this.applicationContext, R.raw.markers)

        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val tagsArray = jsonObject.getJSONArray("tags")
                    val tagsList = mutableListOf<String>()
                    for (j in 0 until tagsArray.length()) {
                        tagsList.add(tagsArray.getString(j))
                    }

                    val marker = Marker(
                        description = jsonObject.getString("description"),
                        tags = tagsList,
                        mainTag = jsonObject.getString("main_tag"),
                        markerName = jsonObject.getString("marker_name"),
                        lat = jsonObject.getDouble("lat"),
                        lng = jsonObject.getDouble("lng"),
                        id = jsonObject.getString("id")
                    )

                    addMarkerToMap(marker)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val btnCenterMap: ImageButton = findViewById(R.id.btnCenterMap)
        btnCenterMap.setOnClickListener {
            centerMapOnUserPosition()
        }

        searchBarEditText = findViewById(R.id.searchBarEditText)
        val searchButton: ImageButton = findViewById(R.id.searchButton)
        searchButton.setOnClickListener {
            val searchText = searchBarEditText.text.toString()
            Log.d("SearchBar", "Search Text: $searchText")
        }


    }

    private fun centerMapOnUserPosition() {
        mapView?.getMapboxMap()?.setCamera(
            com.mapbox.maps.CameraOptions.Builder()
                .center(Point.fromLngLat(longitude, latitude))
                .zoom(15.0)
                .build()
        )
    }

    private fun loadJSONResource(context: Context, resourceId: Int): String? {
        return try {
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            jsonString
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override fun onStart() {
        super.onStart()
        if (!hasLocationPermission()) {
            requestLocationPermission()
        } else if (!isLocationServiceEnabled()) {
            showLocationServiceDisabledDialog()
        } else {
            startLocationUpdates()
        }
    }


    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        if (isLocationServiceEnabled()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            showLocationServiceDisabledDialog()
        }
    }

    private fun showLocationServiceDisabledDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Service Disabled")
            .setMessage("This app requires the location service to be enabled. Please enable the location service to proceed.")
            .setPositiveButton("Enable") { _, _ ->
                openLocationSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun showLocationPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app requires access to your device's location. Please grant the location permission in the app settings.")
            .setPositiveButton("App Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Close App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun startLocationUpdates() {
        if (hasLocationPermission()) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopLocationUpdates() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun handleLocationResult(location: Location) {
        val zoomLevel = 15.0

        val newLatitude = location.latitude
        val newLongitude = location.longitude

        val latitudeChange = abs(newLatitude - latitude)
        val longitudeChange = abs(newLongitude - longitude)

        // Remainder: PCT POSITION CHANGE THRESHOLD
        if (latitudeChange > PCT || longitudeChange > PCT) {
            latitude = newLatitude
            longitude = newLongitude

            currentLocationMarker?.let {
                deletePositionMarker(it)
                currentLocationMarker = null
            }

            currentLocationMarker = addPositionToMap(latitude, longitude)
        }

        if (isFirstLocationUpdate) {
            mapView?.getMapboxMap()?.setCamera(
                com.mapbox.maps.CameraOptions.Builder()
                    .center(Point.fromLngLat(longitude, latitude))
                    .zoom(zoomLevel)
                    .build()
            )
            isFirstLocationUpdate = false
        }
    }

    private fun deletePositionMarker(marker: PointAnnotation) {
        pointAnnotationManager?.delete(marker)
        activeAnnotations.remove(marker.id.toString())
    }

    private fun addPositionToMap(lat: Double, lng: Double): PointAnnotation? {
        bitmapFromDrawableRes(
            this@MainActivity,
            R.drawable.red_marker
        )?.let { markerIcon ->
            // Find and delete the previous marker if it exists
            val existingMarker = currentLocationMarker
            if (existingMarker != null && activeAnnotations.containsKey(existingMarker.id.toString())) {
                pointAnnotationManager?.delete(existingMarker)
                activeAnnotations.remove(existingMarker.id.toString())
                currentLocationMarker = null
            }

            // Create a new marker
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(lng, lat))
                .withIconImage(markerIcon)
            currentLocationMarker = pointAnnotationManager?.create(pointAnnotationOptions)

            return currentLocationMarker
        }

        return null
    }

    private fun addMarkerToMap(marker: Marker) {


        pointAnnotationManager?.addClickListener(OnPointAnnotationClickListener {
            annotation:PointAnnotation ->
            onMarkerItemClick(annotation)
            true
        })

        bitmapFromDrawableRes(
            this@MainActivity,
            R.drawable.green_marker
        )?.let { markerIcon ->
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(marker.lng, marker.lat))
                .withIconImage(markerIcon)
                .withData(createMarkerData(marker))

            val newMarker = pointAnnotationManager?.create(pointAnnotationOptions)
            newMarker?.let { activeAnnotations[marker.id] = it }
        }
    }

    private fun createMarkerData(marker: Marker): JsonObject {
        val jsonData = JsonObject()
        jsonData.addProperty("description", marker.description)
        jsonData.addProperty("tags", marker.tags.joinToString(", "))
        jsonData.addProperty("mainTag", marker.mainTag)
        jsonData.addProperty("markerName", marker.markerName)
        return jsonData
    }

    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resId: Int): Bitmap? {
        val drawable: Drawable? = AppCompatResources.getDrawable(context, resId)
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap: Bitmap? = if ((drawable?.intrinsicWidth ?: 0) > 0 && (drawable?.intrinsicHeight
                ?: 0) > 0
        ) {
            Bitmap.createBitmap(
                drawable?.intrinsicWidth ?: 0,
                drawable?.intrinsicHeight ?: 0,
                Bitmap.Config.ARGB_8888
            )
        } else {
            null
        }

        if (bitmap != null) {
            val canvas = Canvas(bitmap)
            drawable?.setBounds(0, 0, canvas.width, canvas.height)
            drawable?.draw(canvas)
        }

        return bitmap
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                showLocationPermissionDeniedDialog()
            }
        }
    }

    fun onMarkerItemClick(marker: PointAnnotation){

        if(panelLayout.visibility != View.VISIBLE){
            panelLayout.visibility = View.VISIBLE
        }

        val markerData = marker.getData()
        val name = markerData?.asJsonObject?.get("markerName").toString().removeSurrounding("\"")
        val mainTag = markerData?.asJsonObject?.get("mainTag").toString().removeSurrounding("\"")
        val tags = markerData?.asJsonObject?.get("tags").toString().removeSurrounding("\"")

        if(ColorTagMap.containsKey(mainTag)){
            val color = ColorTagMap[mainTag]
            colorChange.setBackgroundColor(Color.parseColor(color))
        }else{
            colorChange.setBackgroundColor(Color.parseColor("#6e6e6e"))
        }


        NameText.setText(name)
        TagsText.setText("$mainTag, $tags")
    }
}
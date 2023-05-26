package com.mtcleo05.planethistory

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import com.google.android.gms.location.*
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.google.android.gms.location.LocationResult
import android.util.Log
import android.widget.ImageButton
import androidx.core.app.ActivityCompat
import com.google.gson.JsonObject
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import org.json.JSONArray
import com.mtcleo05.planethistory.R
import java.io.IOException
import kotlin.math.abs


var mapView: MapView? = null

class MainActivity : AppCompatActivity() {

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var currentLocationMarker: PointAnnotation? = null
    private val activeAnnotations: MutableMap<String, PointAnnotation> = mutableMapOf()
    private var isFirstLocationUpdate = true
    private var pointAnnotationManager: PointAnnotationManager? = null
    private val PCT = 0.00002   //POSITION CHANGE THRESHOLD
    private var btnCenterMap: ImageButton? = null


    data class Marker(
        val description: String,
        val tags: List<String>,
        val mainTag: String,
        val markerName: String,
        val lat: Double,
        val lng: Double,
        val id: String
    )

    companion object {
        private const val LOCATION_UPDATE_INTERVAL: Long = 10000 // 10 seconds
        private const val LOCATION_UPDATE_FASTEST_INTERVAL: Long = 5000 // 5 seconds
        private const val LOCATION_PERMISSION_REQUEST_CODE = 177013
    }

    private val locationRequest: LocationRequest by lazy {
        LocationRequest.create().apply {
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_UPDATE_FASTEST_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            handleLocationResult(locationResult.lastLocation)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        mapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS)
        pointAnnotationManager = mapView?.annotations?.createPointAnnotationManager()
        startLocationUpdates()
        btnCenterMap = findViewById(R.id.btnCenterMap)
        btnCenterMap?.setOnClickListener { centerMapOnUserPosition() }

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
                Log.e("JSONUtils", "Error parsing JSON", e)
            }
        }
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

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun centerMapOnUserPosition() {
        mapView?.getMapboxMap()?.setCamera(
            com.mapbox.maps.CameraOptions.Builder()
                .center(Point.fromLngLat(longitude, latitude))
                .zoom(12.0)
                .build()
        )
    }

    private fun handleLocationResult(location: Location) {
        val zoomLevel = 12.0

        val newLatitude = location.latitude
        val newLongitude = location.longitude

        val latitudeChange = abs(newLatitude - latitude)
        val longitudeChange = abs(newLongitude - longitude)

        //Remainder: PCT POSITION CHANGE THRESHOLD
        if (latitudeChange > PCT || longitudeChange > PCT) {
            latitude = newLatitude
            longitude = newLongitude

            if (currentLocationMarker != null) {
                deletePositionMarker(currentLocationMarker!!)
                currentLocationMarker = null
            }

            currentLocationMarker = addPositionToMap(latitude, longitude)
        }


        if(isFirstLocationUpdate){
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
            }
        }
    }

}

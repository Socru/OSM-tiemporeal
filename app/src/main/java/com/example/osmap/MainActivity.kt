package com.example.osmap

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.http.GET
import retrofit2.http.Query
import android.util.Log
import org.osmdroid.views.overlay.Polyline
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.osmap.network.MapService
import com.example.osmap.network.RouteRequest
import com.example.osmap.network.RouteResponse
import android.location.Location



private lateinit var userLocationMarker: Marker




class MainActivity : AppCompatActivity() {


    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var trailOverlay: Polyline
    private val trailPoints = mutableListOf<GeoPoint>()




    companion object {
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", 0))
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.DEFAULT_TILE_SOURCE)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 2.0
        mapView.maxZoomLevel = 19.0
        mapController = mapView.controller

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            setupMap()
        }
        trailOverlay = Polyline().apply {
            width = 50f // Ancho de la polilínea
            color = Color.BLUE // Color de la polilínea
        }
        mapView.overlayManager.add(trailOverlay)

        mapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                val projection = mapView?.projection
                val geoPoint = projection?.fromPixels(e!!.x.toInt(), e.y.toInt())
                if (geoPoint != null) {
                    if (startPoint == null) {
                        startPoint = geoPoint as GeoPoint?
                        addMarker(startPoint!!)
                    } else if (endPoint == null) {
                        endPoint = geoPoint as GeoPoint?
                        addMarker(endPoint!!)
                        getRouteFromAPI("busqueda_profundidad", startPoint!!, endPoint!!)
                    }
                    return true
                }
                return false
            }
        })

        val btnLocate: Button = findViewById(R.id.myLocationButton)
        btnLocate.setOnClickListener {
            if (locationOverlay.myLocation != null) {
                val userLocation = GeoPoint(locationOverlay.myLocation)
                mapController.setCenter(userLocation)
                mapController.setZoom(16.0)
            } else {
                Toast.makeText(this, "No se puede obtener la ubicación del usuario", Toast.LENGTH_SHORT).show()
            }
        }

        val btnReset: Button = findViewById(R.id.resetButton)
        btnReset.setOnClickListener {
            resetPoints()
        }
        trailOverlay = Polyline().apply {
            width = 50f
            color = Color.BLUE
        }
        mapView.overlayManager.add(trailOverlay)
    }


    private fun resetPoints() {
        // Limpiar los puntos
        trailPoints.clear()
        mapView.invalidate()
        Toast.makeText(this, "Rastro reiniciado", Toast.LENGTH_SHORT).show()
        startPoint = null
        endPoint = null
        mapView.overlays.clear()
        mapView.overlays.add(locationOverlay)
        mapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                val projection = mapView?.projection
                val geoPoint = projection?.fromPixels(e!!.x.toInt(), e.y.toInt())
                if (geoPoint != null) {
                    if (startPoint == null) {
                        startPoint = geoPoint as GeoPoint?
                        addMarker(startPoint!!)
                    } else if (endPoint == null) {
                        endPoint = geoPoint as GeoPoint?
                        addMarker(endPoint!!)
                        ///calculateAndShowRoute(startPoint!!, endPoint!!)
                        getRouteFromAPI(algorithm = "busqueda_profundidad", startPoint!!, endPoint!!)
                    }
                    return true
                }
                return false
            }
        })
        mapView.invalidate()
        Toast.makeText(this, "Selección de puntos reiniciada", Toast.LENGTH_SHORT).show()
    }

    private fun onLocationChanged(newLocation: GeoPoint) {
        trailPoints.add(newLocation)
        trailOverlay.setPoints(trailPoints)
        trailOverlay.color = Color.BLUE
        mapView.invalidate()
    }



    private fun addMarker(geoPoint: GeoPoint) {
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(marker)
        mapView.invalidate()
        Toast.makeText(this, "Punto: ${marker.position}", Toast.LENGTH_SHORT).show()
    }

    private fun getRouteFromAPI(algorithm: String, startPoint: GeoPoint, endPoint: GeoPoint) {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000")  // Use 10.0.2.2 for the emulator
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val sPointX = startPoint.latitude
        val sPointY = startPoint.longitude
        val ePointX = endPoint.latitude
        val ePointY = endPoint.longitude
        val service = retrofit.create(MapService::class.java)
        val request = RouteRequest(
            start = listOf(sPointX, sPointY),
            end = listOf(ePointX, ePointY)
        )
        println("request: $request")
        service.getRoute(algorithm, request).enqueue(object : Callback<RouteResponse> {
            override fun onResponse(call: Call<RouteResponse>, response: Response<RouteResponse>) {
                if (response.isSuccessful) {
                    println("Respuesta: ${response.body()}")
                    val ruta = response.body()?.ruta
                    if (ruta != null) {
                        println("Rutita: ${ruta}")
                        displayRouteOnMap(ruta, startPoint)
                        Toast.makeText(this@MainActivity, "Ruta cargada exitosamente", Toast.LENGTH_SHORT).show()
                    }else{
                        Toast.makeText(this@MainActivity, "No se encontró la ruta", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<RouteResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error al conectarse con la API: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayRouteOnMap(route: List<List<Double>>, startPoint: GeoPoint) {
        mapView.overlays.clear()

        val geoPoints = route.map { GeoPoint(it[1], it[0]) }
        val geoPointsWithStart = geoPoints.plus(startPoint)
        val polyline = Polyline().apply {
            width = 50f // Ancho de la polilínea
            color = Color.BLUE // Establecer el color del trazo como azul
            setPoints(geoPointsWithStart)
            outlinePaint.color = Color.BLUE
        }
        mapView.overlayManager.add(polyline)
        mapView.invalidate()
    }


    object PolylineDecoder {
        private const val PRECISION = 1E5 // Utilizado para decodificar la polilínea

        fun decode(polyline: String): List<GeoPoint> {
            val points = mutableListOf<GeoPoint>()
            var index = 0
            var lat = 0
            var lng = 0

            while (index < polyline.length) {
                var result = 1
                var shift = 0
                var b: Int
                do {
                    b = polyline[index++].toInt() - 63 - 1
                    result += b shl shift
                    shift += 5
                } while (b >= 0x1f)
                lat += if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
                result = 1
                shift = 0
                do {
                    b = polyline[index++].toInt() - 63 - 1
                    result += b shl shift
                    shift += 5
                } while (b >= 0x1f)
                lng += if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
                points.add(GeoPoint(lat / PRECISION, lng / PRECISION))
            }

            return points
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }


    private fun checkPermissions(): Boolean {
        val permissionState = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_PERMISSIONS_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMap()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun setupMap() {
        // Crear un nuevo overlay para la ubicación del usuario
        val locationProvider = GpsMyLocationProvider(this)
        locationOverlay = MyLocationNewOverlay(locationProvider, mapView)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation() // Para centrar automáticamente el mapa

        // Crear un marcador para la ubicación del usuario
        userLocationMarker = Marker(mapView)
        userLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        userLocationMarker.icon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_mylocation) // Usar el símbolo predeterminado de Android Studio

        // Configurar un listener para detectar cuando se obtiene la ubicación del usuario por primera vez
        locationOverlay.runOnFirstFix {
            runOnUiThread {
                // Obtener la ubicación del usuario después de que se ha fijado por primera vez
                val myLocation = locationOverlay.myLocation
                if (myLocation != null) {
                    val startPoint = GeoPoint(myLocation.latitude, myLocation.longitude)
                    mapController.setCenter(startPoint)
                    mapController.setZoom(16.0)
                    // Mostrar la ubicación del usuario en el mapa
                    userLocationMarker.position = startPoint
                    mapView.invalidate()
                }
            }
        }

        // Listener para actualizaciones de la ubicación
        locationProvider.locationUpdateMinDistance = 10f
        locationProvider.locationUpdateMinTime = 1000
        locationProvider.startLocationProvider { location, _ ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                onLocationChanged(geoPoint)
                userLocationMarker.position = geoPoint
                mapView.invalidate()
            }
        }

        // Agregar los overlays al mapa
        mapView.overlays.add(locationOverlay)
        mapView.overlays.add(trailOverlay)
        mapView.overlays.add(userLocationMarker)
    }
    }



    interface OSRMRoutingService {
    @GET("/route/v1/driving/{coordinates}")
    fun getRoute(
        @retrofit2.http.Path("coordinates") coordinates: String,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("geometries") geometries: String = "polyline",
        @Query("overview") overview: String = "full",
        @Query("steps") steps: Boolean = true,
        @Query("annotations") annotations: String = "true"
    ): Call<RouteResponse>
}

data class RouteResponse(
    val routes: List<Route>
)

data class Route(
    val geometry: String,
    val legs: List<Leg>
)

data class Leg(
    val steps: List<Step>
)

data class Step(
    val geometry: String
)

package com.example.stormly

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.view.InputDeviceCompat
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Turn on your location", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "Please turn on location Permission",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?
                    ) {
                        permissionDialog()
                    }

                }).onSameThread().check()
        }

        iv_refresh_location.setOnClickListener {
            requestLocationData()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()!!
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude
            getWeatherDetails(latitude, longitude)
        }
    }

    private fun getWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit
                .create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        val weatherList: WeatherResponse? = response.body()
                        Log.i("Perfect My boy", "$weatherList")
                        setupUI(weatherList!!)

                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.i("Error", "Bad Connection")
                            }
                            404 -> {
                                Log.i("Error", "Not Found")
                            }
                            else -> {
                                Log.i("Error", "Something Wrong")

                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrr", t.message.toString())
                }

            })

        } else {
            Toast.makeText(this, "Turn on internet connection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun permissionDialog() {
        AlertDialog.Builder(this).setMessage("Please turn on the settings")
            .setPositiveButton("GO TO SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {

        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE)
                as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun setupUI(weatherResponse: WeatherResponse) {
        tv_area_name.text = weatherResponse.name

        for (i in weatherResponse.weather.indices) {
            tv_area_name.text = weatherResponse.name
            tv_weather_details.text = weatherResponse.weather[i].description
            tv_temp.text = weatherResponse.main.temp.toInt().toString()
            tv_wind_speed.text = weatherResponse.wind.speed.toString()
            tv_pressure.text = weatherResponse.main.pressure.toString()
            tv_humidity.text = weatherResponse.main.humidity.toString()
            tv_sunrise_time.text = gettime(weatherResponse.sys.sunrise)
            tv_sunset_time.text = gettime(weatherResponse.sys.sunset)

            when (weatherResponse.weather[i].icon) {
                "01d" -> weather_background.setImageResource(R.drawable.cleansky)
                "02d" -> weather_background.setImageResource(R.drawable.fewcloudday)
                "04d" -> weather_background.setImageResource(R.drawable.brokenclouds)
                "09d" -> weather_background.setImageResource(R.drawable.showerrain)
                "10d" -> weather_background.setImageResource(R.drawable.rain)
                "11d" -> weather_background.setImageResource(R.drawable.thunderstorm)
                "13d" -> weather_background.setImageResource(R.drawable.snow)
                "50d" -> weather_background.setImageResource(R.drawable.mist)
            }
        }


    }

    private fun gettime(time: Int): String {
        val date = Date(time * 1000L)
        val sdf = SimpleDateFormat("hh:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)

    }
}

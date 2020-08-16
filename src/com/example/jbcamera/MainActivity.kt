package com.example.jbcamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.impl.ImageOutputConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.instantapps.InstantApps
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private var cameraSelector: CameraSelector? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val isInstantApp = InstantApps.getPackageManagerCompat(this).isInstantApp
        Log.d(LOG_TAG, "are we instant? $isInstantApp")
        preview_surface.setOnClickListener(clickListener)
        capture_button.setOnClickListener(clickListener)
        switch_button.setOnClickListener(clickListener)
    }

    private val clickListener = View.OnClickListener { v ->
        when {
            cameraSelector == null -> return@OnClickListener
            v == preview_surface || v == capture_button -> try {
                    takePhoto()
                } catch (e: RuntimeException) {
                    Log.e(LOG_TAG, if (v == preview_surface) "preview_surface" else "capture_button", e)
                }
            v == switch_button -> switchCamera()
        }
    }

    @SuppressLint("RestrictedApi")
    fun switchCamera() {
        if (cameraSelector?.lensFacing == CameraSelector.LENS_FACING_BACK)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        else
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission()
                return
            }
        }
        startCamera()
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            val confirmationDialog = AlertDialog.Builder(this)
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                        requestPermissions(arrayOf(Manifest.permission.CAMERA),
                                REQUEST_CAMERA_PERMISSION)
                    }
                    .setNegativeButton(android.R.string.cancel
                    ) { dialog, which -> finish() }
                    .create()
            confirmationDialog.show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                val errorDialog = AlertDialog.Builder(this)
                        .setMessage(R.string.request_permission).create()
                errorDialog.show()
                finish()
            } else {
                startCamera()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun parseExif(inputStream: File) {
        val exif = ExifInterface(inputStream)
        Log.i(LOG_TAG, "Exif date ${exif.dateTime}")
        Log.i(LOG_TAG, "Exif gpsDate ${exif.gpsDateTime}")
        Log.i(LOG_TAG, "Exif width ${exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1)}×${exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1)}")
        Log.i(LOG_TAG, "Exif rotation ${exif.rotationDegrees}")
        Log.i(LOG_TAG, "Exif orientation ${exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)} ${exif.hasAttribute(ExifInterface.TAG_ORIENTATION)}")
        Log.i(LOG_TAG, "Exif model ${exif.getAttribute(ExifInterface.TAG_MODEL)} ${exif.hasAttribute(ExifInterface.TAG_MODEL)}")
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                Log.i(LOG_TAG, "rotation ${image.imageInfo.rotationDegrees}")
                val jpgPath = "$cacheDir/JBCameraCapture.jpg"
                val jpg = FileOutputStream(jpgPath).channel
                jpg.write(image.planes[0].buffer)
                jpg.close()

                parseExif(File(jpgPath))
//            val ihp = ImageHeaderParser(ByteArrayInputStream(data))
//            Log.i(LOG_TAG, "ImageHeaderParser orientation ${ihp.orientation}")
                Log.i(LOG_TAG, "written " + image.planes[0].buffer.capacity() + " bytes to " + jpgPath)

                image.close()
            }
        })
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        Log.e(LOG_TAG, "start Camera now!")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(preview_surface.createSurfaceProvider())
                    }

            // Select back camera as a default
            if (cameraSelector == null)
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatioCustom(Rational(preview_surface.width, preview_surface.height))
                    .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector!!, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(LOG_TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun setCameraParameters() {
//        var supportedSizes: List<Camera.Size>
//        val params = camera!!.parameters
//        supportedSizes = params.supportedPreviewSizes
//        for (sz in supportedSizes) {
//            Log.d(LOG_TAG, "supportedPreviewSizes " + sz.width + "x" + sz.height)
//        }
//        params.setPreviewSize(supportedSizes[0].width, supportedSizes[0].height)
//        supportedSizes = params.supportedVideoSizes
//        for (sz in supportedSizes) {
//            Log.d(LOG_TAG, "supportedVideoSizes " + sz.width + "x" + sz.height)
//        }
//        supportedSizes = params.supportedPictureSizes
//        for (sz in supportedSizes) {
//            Log.d(LOG_TAG, "supportedPictureSizes " + sz.width + "x" + sz.height)
//        }
//        params.setPictureSize(supportedSizes[0].width, supportedSizes[0].height)
//        Log.d(LOG_TAG, "current preview size " + params.previewSize.width + "x" + params.previewSize.height)
//        Log.d(LOG_TAG, "current picture size " + params.pictureSize.width + "x" + params.pictureSize.height)
//        // TODO: choose the best preview & picture size, and also fit the surface aspect ratio to preview aspect ratio
//        camera!!.parameters = params
    }

    @SuppressLint("RestrictedApi")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (imageCapture == null)
            return

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            imageCapture!!.setTargetRotation(Surface.ROTATION_90)
            imageCapture!!.setCropAspectRatio(Rational(preview_surface.width, preview_surface.height))
            Log.d(LOG_TAG, "setTargetRotation(Surface.ROTATION_0) ${preview_surface.width}×${preview_surface.height}")
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            imageCapture!!.setTargetRotation(Surface.ROTATION_0)
            imageCapture!!.setCropAspectRatio(Rational(preview_surface.width, preview_surface.height))
            Log.d(LOG_TAG, "setTargetRotation(Surface.ROTATION_90) ${preview_surface.width}×${preview_surface.height}")
        }
    }

    companion object {
        private const val LOG_TAG = "Scanner"
        private const val REQUEST_CAMERA_PERMISSION = 21
    }
}
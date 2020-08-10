package com.example.jbcamera

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import java.lang.RuntimeException
import android.os.Build
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi
import android.app.AlertDialog
import android.hardware.Camera
import android.hardware.Camera.PictureCallback
import java.io.FileOutputStream
import java.lang.Exception
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.os.HandlerThread
import android.view.Surface
import android.hardware.Camera.CameraInfo
import android.os.Handler
import com.google.android.gms.instantapps.InstantApps
import java.io.IOException

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity() {
    private var cameraId = 0
    private var camera: Camera? = null
    private var waitForPermission = false
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
            camera == null -> return@OnClickListener
            v == preview_surface || v == capture_button -> try {
                    camera!!.takePicture(null, null, pictureCallback)
                } catch (e: RuntimeException) {
                    Log.e(LOG_TAG, if (v == preview_surface) "preview_surface" else "capture_button", e)
                }
            v == switch_button -> switchCamera()
        }
    }

    fun switchCamera() {
        startCamera(1 - cameraId)
    }

    public override fun onResume() {
        super.onResume()
        setSurface()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                waitForPermission = true
                requestCameraPermission()
            }
        }
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
                waitForPermission = false
                startCamera(cameraId)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private val pictureCallback = PictureCallback { data, cam ->
        try {
            val jpgPath = "$cacheDir/JBCameraCapture.jpg"
            val jpg = FileOutputStream(jpgPath)
            jpg.write(data)
            jpg.close()
            Log.i(LOG_TAG, "written " + data.size + " bytes to " + jpgPath)
            camera?.startPreview()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private val shCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.i(LOG_TAG, "surfaceDestroyed callback")
            camera?.stopPreview()
            camera?.release()
            camera = null
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.i(LOG_TAG, "surfaceCreated callback")
            startCamera(cameraId)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int,
                                    height: Int) {
            Log.i(LOG_TAG, "surfaceChanged callback " + width + "x" + height)
            restartPreview()
        }
    }

    private fun setSurface() {
        val previewSurfaceView = findViewById<View>(R.id.preview_surface) as SurfaceView
        previewSurfaceView.holder.addCallback(shCallback)
    }

    var cameraHandler: Handler? = null
    protected fun startCamera(id: Int) {
        if (cameraHandler == null) {
            val handlerThread = HandlerThread("CameraHandlerThread")
            handlerThread.start()
            cameraHandler = Handler(handlerThread.looper)
        }
        Log.d(LOG_TAG, "startCamera(" + id + "): " + if (waitForPermission) "waiting" else "proceeding")
        if (waitForPermission) {
            return
        }
        releaseCamera()
        cameraHandler!!.post {
            val camera = openCamera(id)
            runOnUiThread { startPreview(id, camera) }
        }
    }

    private fun releaseCamera() {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    private fun restartPreview() {
        if (camera == null) {
            return
        }
        var degrees = 0
        when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        val ci = CameraInfo()
        Camera.getCameraInfo(cameraId, ci)
        if (ci.facing == CameraInfo.CAMERA_FACING_FRONT) {
            degrees += ci.orientation
            degrees %= 360
            degrees = 360 - degrees
        } else {
            degrees = 360 - degrees
            degrees += ci.orientation
        }
        camera!!.setDisplayOrientation(degrees % 360)
        setCameraParameters()
        camera!!.startPreview()
    }

    private fun setCameraParameters() {
        var supportedSizes: List<Camera.Size>
        val params = camera!!.parameters
        supportedSizes = params.supportedPreviewSizes
        for (sz in supportedSizes) {
            Log.d(LOG_TAG, "supportedPreviewSizes " + sz.width + "x" + sz.height)
        }
        params.setPreviewSize(supportedSizes[0].width, supportedSizes[0].height)
        supportedSizes = params.supportedVideoSizes
        for (sz in supportedSizes) {
            Log.d(LOG_TAG, "supportedVideoSizes " + sz.width + "x" + sz.height)
        }
        supportedSizes = params.supportedPictureSizes
        for (sz in supportedSizes) {
            Log.d(LOG_TAG, "supportedPictureSizes " + sz.width + "x" + sz.height)
        }
        params.setPictureSize(supportedSizes[0].width, supportedSizes[0].height)
        Log.d(LOG_TAG, "current preview size " + params.previewSize.width + "x" + params.previewSize.height)
        Log.d(LOG_TAG, "current picture size " + params.pictureSize.width + "x" + params.pictureSize.height)
        // TODO: choose the best preview & picture size, and also fit the surface aspect ratio to preview aspect ratio
        camera!!.parameters = params
    }

    private fun setPictureSize(width: Int, height: Int) {
        val params = camera!!.parameters
        params.setPictureSize(width, height)
        camera!!.parameters = params
    }

    private fun startPreview(id: Int, c: Camera?) {
        if (c != null) {
            try {
                val previewSurfaceView = findViewById<View>(R.id.preview_surface) as SurfaceView
                val holder = previewSurfaceView.holder
                c.setPreviewDisplay(holder)
                camera = c
                cameraId = id
                restartPreview()
            } catch (e: IOException) {
                e.printStackTrace()
                c.release()
            }
        }
    }

    companion object {
        private const val LOG_TAG = "Scanner"
        private const val REQUEST_CAMERA_PERMISSION = 21
        private fun openCamera(id: Int): Camera? {
            Log.d(LOG_TAG, "opening camera $id")
            var camera: Camera? = null
            try {
                camera = Camera.open(id)
                Log.d(LOG_TAG, "opened camera $id")
                return camera
            } catch (e: Exception) {
                Log.e(LOG_TAG, "failed to open camera $id", e)
                camera?.release()
            }
            return null
        }
    }
}
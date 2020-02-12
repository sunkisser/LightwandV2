//code from https://codelabs.developers.google.com/codelabs/camerax-getting-started/index.html

package com.sunkisselectric.lightwand

// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate.
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.Bitmap.Config.ARGB_8888
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraX.bindToLifecycle
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.camera.core.ImageCaptureConfig
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors.newSingleThreadExecutor



// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)


class CameraActivity : AppCompatActivity() {

    // Add this after onCreate
    private lateinit var viewFinder: TextureView
    private lateinit var lightwandviewer: ImageView
    private lateinit var lightwandcheckbox: CheckBox
    private lateinit var lightwandtextview: TextView
    private val executor = newSingleThreadExecutor()
    private var counter=0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera2)

        viewFinder = findViewById(R.id.view_finder)
        lightwandviewer = findViewById(R.id.lightwandimgdisplay)
        lightwandcheckbox = findViewById(R.id.lightwand_checkbox)
        lightwandtextview = findViewById(R.id.lightwand_textview)

        // Request camera permissions
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        }
        else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    private fun startCamera() {
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetResolution(Size(320, 240))
        }.build()


        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {
            Log.e("asdfsdfsdfsdfsdf","ONPREVIEWOUTPUTUPDATELISTENER TRIGGERED")
            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)
            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // Add this before CameraX.bindToLifecycle

        // Create configuration object for the image capture use case
        val imageCaptureConfig = ImageCaptureConfig.Builder()
                .apply {
                    // We don't set a resolution for image capture; instead, we
                    // select a capture mode which will infer the appropriate
                    // resolution based on aspect ration and requested mode
                    setCaptureMode(CaptureMode.MIN_LATENCY)
                }.build()

        // Build the image capture use case and attach button click listener
        val imageCapture = ImageCapture(imageCaptureConfig)
        findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
            val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")

            imageCapture.takePicture(file, executor,
                    object : ImageCapture.OnImageSavedListener {
                        override fun onError(
                                imageCaptureError: ImageCapture.ImageCaptureError,
                                message: String,
                                exc: Throwable?
                        ) {
                            val msg = "Photo capture failed: $message"
                            Log.e("CameraXApp", msg, exc)
                            viewFinder.post {
                                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onImageSaved(file: File) {
                            val msg = "Photo capture succeeded: ${file.absolutePath}"
                            Log.d("CameraXApp", msg)
                            viewFinder.post {
                                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
        }
        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.
        bindToLifecycle(this, preview, imageCapture)

        // now ready for actions --- this is how we handle an old phone without a manual shutter speed
        // we can do something much better on a newer phone with CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        // for that see the android example HdrViewfinder
        object : Thread() {
            lateinit var showme: Bitmap
            lateinit var addme: Bitmap
            lateinit var oldme: Bitmap
            var engage: Boolean = false
            var totalexposure: Int = 20000 //milliseconds
            val multa = Paint()

            override fun run() {

                runOnUiThread{
                    //TODO showme should start as black just like real film
                    showme = viewFinder.bitmap.copy(ARGB_8888, true)
                    addme = viewFinder.bitmap.copy(ARGB_8888, true)
                    lightwandviewer.setImageBitmap(showme)
                }

                while(true){
                    sleep(33)

                    runOnUiThread {
                        engage = lightwandcheckbox.isChecked
                        if (engage) {
                            lightwandtextview.setText(counter++.toString()) //just a counter for fun
                            if (addme != null && viewFinder != null) {
                                addme = viewFinder.bitmap.copy(ARGB_8888, true)
                            }
                        }
                    }
                    if (engage) {
                        //oldme = showme.copy(ARGB_8888, true)
                        val showmecanvas = Canvas(showme)
                        val addmecanvas = Canvas(addme)
                        addmecanvas.drawColor(0x05 shl 24, PorterDuff.Mode.LIGHTEN)
                        multa.xfermode =  PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                        showmecanvas.drawBitmap(addme, 0.0f, 0.0f, multa)
                        //showme = oldme.copy(ARGB_8888, true)
                        runOnUiThread {
                            lightwandviewer.setImageBitmap(showme)
                        }
                    }
                }
            }
        }.start()
    }

    private fun updateTransform(){
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}

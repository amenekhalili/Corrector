package com.example.corrector

import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.jar.Manifest

typealias LumaListener  = (luma : Double) -> Unit

class MainActivity : AppCompatActivity() {

    private  var imageCapture: ImageCapture ?  = null

    private lateinit var outputDirectory : File
    private lateinit var cameraExecutor : ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(allPermissionGranted()){
            startCamera()
        }else{
            ActivityCompat.requestPermissions(
                this , REQUIRED_PERMISSIONS , REQUEST_CODE_PERMISSION)
        }

        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()


    }

    private fun takePhoto(){
          val imageCapture = imageCapture ?: return

        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT , Locale.US)
                        .format(System.currentTimeMillis()) + ".jpg")


        val outputOption = ImageCapture.OutputFileOptions.Builder(photoFile).build()

      imageCapture.takePicture(outputOption,ContextCompat.getMainExecutor(this) ,
              object : ImageCapture.OnImageSavedCallback{

                  override fun onError(exception: ImageCaptureException) {
                      Log.e(TAG , "photo capture failed : ${exception.message}" , exception)
                  }


                  override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                      val savedUri = Uri.fromFile(photoFile)
                      val msg = "photo capture succeed :$savedUri"
                      Toast.makeText(baseContext , msg , Toast.LENGTH_SHORT).show()
                      Log.d(TAG , msg)

                  }
              }
      )

    }

    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }

            imageCapture = ImageCapture.Builder()
                    .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor , LuminosityAnalyzer {luma ->
                            Log.d(TAG , "Average luminosity :$luma")
                        })
                    }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview ,imageCapture  , imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))


    }

    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all{
        ContextCompat.checkSelfPermission(
            baseContext , it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory():File{
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it , resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object{
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SS"
        private const val REQUEST_CODE_PERMISSION = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == REQUEST_CODE_PERMISSION){
            if(allPermissionGranted()){
                startCamera()
            }else{
                Toast.makeText(this , "permission not granted by the user. " ,
                Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    private class LuminosityAnalyzer(private val Listener : LumaListener):ImageAnalysis.Analyzer{


        private fun ByteBuffer.toByteArray():ByteArray{
            rewind()
            val data = ByteArray(remaining())
            get(data)
            return data
        }
        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            Listener(luma)
            image.close()

        }

    }
}
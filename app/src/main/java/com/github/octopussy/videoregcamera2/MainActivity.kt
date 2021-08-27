package com.github.octopussy.videoregcamera2

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import com.github.octopussy.videoregcamera2.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

private const val TAG = "MainActivity"

data class CameraInfo(val id: String, val isBack: Boolean, val availableSizes: List<Size>)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var previewTextureView: TextureView

    private lateinit var cameraInfos: List<CameraInfo>

    private var surfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var surfaceTextureWidth: Int = 0
    private var surfaceTextureHeight: Int = 0

    private var imageReader: ImageReader? = null

    private var cameraInfo: CameraInfo? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null

    private lateinit var requestBuilder: CaptureRequest.Builder

    private val cameraManager
        get() = getSystemService(Context.CAMERA_SERVICE) as CameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPhoto.setOnClickListener { takePhoto() }

        previewTextureView = binding.textureView
        previewTextureView.surfaceTextureListener = surfaceTextureListener

        prepareCameras()
        startCameraThread()
    }

    private fun prepareCameras() {
        cameraInfos = cameraManager.cameraIdList.map {
            val cars = cameraManager.getCameraCharacteristics(it)
            val map = cars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val facing = cars.get(CameraCharacteristics.LENS_FACING)
            val sizes = map!!.getOutputSizes(ImageFormat.JPEG)
            CameraInfo(it, facing == CameraCharacteristics.LENS_FACING_BACK, sizes.toList())
        }
    }

    override fun onResume() {
        super.onResume()
        startCameraThread()

        if (previewSurface != null) {
            startOpenCamera()
        }
    }

    override fun onPause() {
        closeCamera()
        stopCameraThread()
        super.onPause()
    }

    private fun takePhoto() {
        val cam = cameraDevice ?: return
        val imageReader = imageReader ?: return
        val session = captureSession ?: return

        val captureBuilder = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureBuilder.addTarget(imageReader.surface)

        /*session.stopRepeating()
        session.abortCaptures()
*/
        val callback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                Log.d(TAG, "onCaptureCompleted")
            }
        }
        session.capture(captureBuilder.build(), callback, cameraHandler)
    }

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraBackground")
        cameraThread?.start()
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
            cameraThread = null
            cameraHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun startOpenCamera() {
        val cameraId = cameraManager.cameraIdList.first()
        if (cameraDevice != null) {
            //pendingOpenCameraInfo = camera
            closeCamera()
        } else {
            openCamera(cameraId)
        }
    }

    private val surfaceTextureListener: TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Log.v(TAG, "onSurfaceTextureAvailable: $width x $height")
                surfaceTextureWidth = width
                surfaceTextureHeight = height
                this@MainActivity.surfaceTexture = surfaceTexture
                this@MainActivity.previewSurface = Surface(surfaceTexture)
                startOpenCamera()
                //streamController?.setDisplaySurfaceTexture(surfaceTexture, width, height)
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Log.v(TAG, "onSurfaceTextureSizeChanged: $width x $height")
                surfaceTextureWidth = width
                surfaceTextureHeight = height
                //streamController?.setDisplaySurfaceTexture(surfaceTexture, width, height)
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                Log.v(TAG, "onSurfaceTextureDestroyed")
                surfaceTextureWidth = 0
                surfaceTextureHeight = 0
                this@MainActivity.surfaceTexture = null
                previewSurface = null
                //streamController?.setDisplaySurfaceTexture(surfaceTexture, 0, 0)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }
        }

    private fun openCamera(cameraInfo: CameraInfo) {
        val cameraStateCallback = object : CameraDevice.StateCallback() {

            override fun onOpened(camera: CameraDevice) {
                check(this@MainActivity.cameraDevice == null)

                this@MainActivity.cameraInfo = cameraInfo
                this@MainActivity.cameraDevice = camera

                val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onReady(session: CameraCaptureSession) {
                        Log.v(TAG, "onReady")
                        /* if (currentCaptureSession === session*//* && !isReleasing*//*) {
                            startPreview()
                        }*/
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        Log.v(TAG, "startPreview ${this@MainActivity.cameraInfo} ${this@MainActivity.cameraDevice}")
                        try {
                            // val range = getCurrentCameraFpsRange() ?: FpsRange(30, 30)
                            requestBuilder =
                                this@MainActivity.cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            /*requestBuilder.set(
                                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                Range.create(range.fpsMin, range.fpsMax)
                            )*/
                            requestBuilder.addTarget(previewSurface!!)
                            val previewRequest = requestBuilder.build()
                            captureSession!!.setRepeatingRequest(
                                previewRequest,
                                null,
                                cameraHandler
                            )
                            // now wait for onFrameAvailable callback to get video frames
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, Log.getStackTraceString(e))
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.v(TAG, "onConfigureFailed")
                    }
                }

                imageReader = ImageReader.newInstance(
                    1920,
                    1080,
                    ImageFormat.JPEG,
                    1
                ).apply {
                    setOnImageAvailableListener({
                        val path = getExternalFilesDir(Environment.DIRECTORY_DCIM)
                        val file = File(path, UUID.randomUUID().toString() + ".jpg")
                        cameraHandler?.post(ImageSaver(it.acquireNextImage(), file))
                    }, null)
                }

                // Открываем новую сессию с камерой
                try {
                    val surfaceList: MutableList<Surface> = ArrayList()
                    surfaceList.add(previewSurface!!)
                    surfaceList.add(imageReader!!.surface)
                    this@MainActivity.cameraDevice!!.createCaptureSession(
                        surfaceList,
                        sessionStateCallback,
                        cameraHandler
                    )
                    // now wait for mSessionStateCallback / onConfigured, then onReady
                } catch (e: java.lang.Exception) {
                    Log.e(TAG, Log.getStackTraceString(e))
                }

                //notifyBaseStateUpdate()

                // setStatus(com.wmspanel.backgroundcamera.BgCameraService.BgCameraNotification.NOTIFICATION_STATUS.CAMERA_OPENED)
            }

            override fun onClosed(camera: CameraDevice) {
                /*Log.v(
                    TAG,
                    "camera closed current='${currentCameraInfo?.cameraId}', pendingCamera='${pendingOpenCameraInfo?.cameraId}'"
                )
                if (pendingOpenCameraInfo != null) {
                    openCamera(pendingOpenCameraInfo!!)
                    pendingOpenCameraInfo = null
                }*/
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.v(TAG, "onDisconnected")
                captureSession = null
                //stopSafe()

            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.v(TAG, "onError, error=$error")
            }
        }

        cameraHandler?.post {
            try {
                cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)
            } catch (e: SecurityException) {
                Log.e(TAG, Log.getStackTraceString(e))
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    private fun closeCamera() {
        Log.v(TAG, "closeCameraCapture")
        if (captureSession == null) {
            Log.v(TAG, "currentCaptureSession == null")
            return
        }

        if (cameraDevice == null) {
            Log.v(TAG, "currentCamera2 == null")
            return
        }

        captureSession?.abortCaptures()
        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null
    }

    private fun refreshGallery(
        ctx: Context,
        file: File?,
        callback: MediaScannerConnection.OnScanCompletedListener
    ) {
        // refresh gallery
        if (file != null && file.exists()) {
            MediaScannerConnection.scanFile(
                ctx, arrayOf(file.absolutePath),
                null,
                callback
            )
        }
    }

///sdcard/Android/data/com.github.octopussy.videoregcamera2/files/DCIM/39626525-784c-4db1-9ad3-456ff7d1f239.jpg
    private inner class ImageSaver(val image: Image, val file: File) : Runnable {

        override fun run() {
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            var output: FileOutputStream? = null
            try {
                output = FileOutputStream(file)
                output.write(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                image.close()
                if (null != output) {
                    try {
                        output.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                refreshGallery(this@MainActivity, file) { path, uri ->
                    Log.d(TAG, "Scan completed '$path' '$uri'")
                }
            }
        }

    }

    /**
     * A native method that is implemented by the 'videoregcamera2' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'videoregcamera2' library on application startup.
        init {
            System.loadLibrary("videoregcamera2")
        }
    }
}

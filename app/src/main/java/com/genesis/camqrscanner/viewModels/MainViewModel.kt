package com.genesis.camqrscanner.viewModels

import android.R
import android.content.Context
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val _contentValue = MutableStateFlow<String?>(null)
    val contentValue: StateFlow<String?> = _contentValue

    private val _hasCompleted = MutableStateFlow<Boolean>(false)
    val hasCompleted : StateFlow<Boolean> = _hasCompleted

    private val camPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.value = newSurfaceRequest
        }
    }

    @OptIn(ExperimentalGetImage::class)
    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val processCamProvider = ProcessCameraProvider.awaitInstance(appContext)

        //Create image analysis use case for QR code scanning
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            .build()

        //set up the barcode scanner
        val scanner = BarcodeScanning.getClient()

        //set up image analysis to process frames
        imageAnalysis.setAnalyzer(Dispatchers.IO.asExecutor()) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                //process image
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                //process the image for barcodes and qet the content
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            if (barcode.format == Barcode.FORMAT_QR_CODE) {
                                barcode.rawValue?.let { qrContent ->
                                    _contentValue.value = qrContent
                                }
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                        _hasCompleted.value = true
                    }
            } else {
                imageProxy.close()
                _hasCompleted.value = true
            }
        }

        processCamProvider.bindToLifecycle(
            lifecycleOwner, DEFAULT_BACK_CAMERA, camPreviewUseCase, imageAnalysis
        )


        // Cancellation signals we're done with the camera
        try {
            awaitCancellation()
        } finally {
            processCamProvider.unbindAll()
        }
    }
}
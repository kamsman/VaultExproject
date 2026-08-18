package com.vaultex.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import java.util.concurrent.Executors

/** Clé du résultat déposé dans le SavedStateHandle de l'écran appelant. */
const val SCANNED_ADDRESS_KEY = "scanned_address"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(navController: NavHostController) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    // Aucun capteur utilisable : ni arriere, ni avant.
    var cameraUnavailable by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.scan_qr), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                // AVANT `hasPermission` : sinon l'aperçu noir masquerait le
                // message, et l'utilisateur resterait devant un écran vide.
                cameraUnavailable -> {
                    Text(
                        stringResource(R.string.scanner_camera_unavailable),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                hasPermission -> {
                    CameraQrPreview(
                        onQrDetected = { raw ->
                            // Nettoie les schémas d'URI courants (bitcoin:, ethereum:…)
                            val address = if (raw.contains(":")) {
                                raw.substringAfter(":").substringBefore("?")
                            } else raw
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(SCANNED_ADDRESS_KEY, address)
                            navController.popBackStack()
                        },
                        onCameraUnavailable = { cameraUnavailable = true }
                    )
                    // Cadre de visée
                    Box(
                        Modifier
                            .size(240.dp)
                            .border(3.dp, AccentBlue, RoundedCornerShape(20.dp))
                    )
                    Text(
                        stringResource(R.string.scanner_hint),
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                permissionDenied -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            stringResource(R.string.scanner_permission_required),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) { Text(stringResource(R.string.scanner_grant_permission)) }
                    }
                }
                else -> CircularProgressIndicator(color = AccentBlue)
            }
        }
    }
}

@Composable
private fun CameraQrPreview(
    onQrDetected: (String) -> Unit,
    onCameraUnavailable: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val detected = remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = Executors.newSingleThreadExecutor()
            val reader = MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
            }

            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = CameraPreview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    if (!detected.value) {
                        decodeQr(reader, imageProxy)?.let { result ->
                            detected.value = true
                            previewView.post { onQrDetected(result) }
                        }
                    }
                    imageProxy.close()
                }
                /*
                 * REPLI SUR LA CAMERA FRONTALE, puis signalement.
                 *
                 * `DEFAULT_BACK_CAMERA` etait impose : un appareil depourvu de
                 * camera arriere — certaines tablettes, quelques modeles
                 * d'entree de gamme — echouait a lier la camera. L'exception
                 * etait capturee sans rien afficher : l'utilisateur restait
                 * devant un ecran noir, sans savoir si l'application chargeait,
                 * si son telephone etait incompatible, ou s'il devait attendre.
                 *
                 * On tente donc l'arriere, puis l'avant, et l'on ne renonce
                 * qu'apres les deux — en le DISANT.
                 */
                val selectors = listOf(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    CameraSelector.DEFAULT_FRONT_CAMERA
                )
                var bound = false
                for (selector in selectors) {
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                        bound = true
                        break
                    } catch (_: Exception) {
                        // Ce capteur n'existe pas ou est deja pris : on essaie le suivant.
                    }
                }
                if (!bound) onCameraUnavailable()
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

private fun decodeQr(reader: MultiFormatReader, imageProxy: ImageProxy): String? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val source = PlanarYUVLuminanceSource(
            bytes, imageProxy.width, imageProxy.height,
            0, 0, imageProxy.width, imageProxy.height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        reader.decodeWithState(bitmap).text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

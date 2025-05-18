package com.genesis.camqrscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genesis.camqrscanner.ui.theme.CamQrScannerTheme
import com.genesis.camqrscanner.viewModels.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CamQrScannerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {

    val viewmodel = MainViewModel()
    val bottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    val permissionSheetState = rememberModalBottomSheetState()
    var canShowPermissionSheet by remember { mutableStateOf(false) }
    val camPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    var qrContentValue by remember { mutableStateOf("") }
    var canShowDialog by remember { mutableStateOf(false) }

    if (canShowDialog) {
        AlertDialog(
            onDismissRequest = { canShowDialog = false },
            onConfirmation = { canShowDialog = false },
            dialogTitle = "QR Code Scanned",
            dialogText = qrContentValue ?: "No QR code found!",
            icon = Icons.Default.MailOutline
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            showBottomSheet = true
        }, colors = ButtonDefaults.buttonColors()) {
            Text("Scan QR Code")
        }
    }


    if (showBottomSheet) {

        if (camPermission.status.isGranted) {

            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = bottomSheetState,
            ) {
                BottomSheet(viewmodel, modifier = modifier, onDismiss = {
                    showBottomSheet = false
                }, onComplete = { dialog,qrValue ->
                    canShowDialog = dialog
                    qrContentValue = qrValue
                })
            }
        } else {
            canShowPermissionSheet = !canShowPermissionSheet//open the permission modal
            ModalBottomSheet(
                onDismissRequest = { canShowPermissionSheet = false },
                sheetState = permissionSheetState
            ) {
                PermissionDialog(
                    camState = camPermission,
                    onUnleash = { canShowPermissionSheet = false })
            }
        }
    }
}

@Composable
fun BottomSheet(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    lifeCycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onDismiss: () -> Unit,
    onComplete: (canShowDialog: Boolean,qr: String) -> Unit = { _ , _ -> }
) {
    val qrContentValue by viewModel.contentValue.collectAsStateWithLifecycle()
    val hasCompleted by viewModel.hasCompleted.collectAsStateWithLifecycle()
    var canShowDialog by remember { mutableStateOf(false) }



    if (canShowDialog) {
        AlertDialog(
            onDismissRequest = { canShowDialog = false },
            onConfirmation = { canShowDialog = false },
            dialogTitle = "QR Code Scanned",
            dialogText = qrContentValue ?: "No QR code found!",
            icon = Icons.Default.MailOutline
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasCompleted && !qrContentValue.isNullOrEmpty()) {
//            Text(
//                text = qrContentValue ?: "No QR code found!",
//                textAlign = TextAlign.Center,
//                modifier = Modifier.padding(16.dp)
//            )
            onComplete(true,qrContentValue.toString())
//            canShowDialog = true
            onDismiss()
        } else if (hasCompleted && qrContentValue.isNullOrEmpty()) {
            Text(
                text = "No QR code detected",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Text(
                text = "Scanning...",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }

        Button(onClick = onDismiss) {
            Text("Close")
        }
        val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LaunchedEffect(lifeCycleOwner) {
            viewModel.bindToCamera(context.applicationContext, lifeCycleOwner)
        }
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request, modifier
                    .height(180.dp)
                    .width(200.dp)
            )
        }

    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionDialog(
    modifier: Modifier = Modifier,
    camState: PermissionState,
    onUnleash: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val textToShow = if (camState.status.shouldShowRationale) {
            // If the user has denied the permission but the rationale can be shown,
            // then gently explain why the app requires this permission
            "Whoops! Looks like we need your camera to work our magic!" +
                    "Don't worry, we just wanna see your pretty face (and maybe some cats).  " +
                    "Grant us permission and let's get this party started!"
        } else {
            // If it's the first time the user lands on this feature, or the user
            // doesn't want to be asked again for this permission, explain that the
            // permission is required
            "Hi there! We need your camera to work our magic! ✨\n" +
                    "Grant us permission and let's get this party started! \uD83C\uDF89"
        }
        Text(textToShow, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            camState.launchPermissionRequest()
            onUnleash()
        }) {
            Text("Unleash the Camera!")
        }
    }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = "Example Icon")
        },
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Dismiss")
            }
        }
    )
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CamQrScannerTheme {
        HomeScreen()
    }
}
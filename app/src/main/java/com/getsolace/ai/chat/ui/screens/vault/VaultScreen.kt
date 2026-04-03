package com.getsolace.ai.chat.ui.screens.vault

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getsolace.ai.chat.data.EncryptedImage
import com.getsolace.ai.chat.data.VaultPinManager
import com.getsolace.ai.chat.data.VaultStore
import com.getsolace.ai.chat.ui.screens.ai.RunMateToast
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.VaultViewModel

// ─── Local design tokens ──────────────────────────────────────────────────────

private val DeepBg       = Color(0xFF0A0B14)
private val CardSurface  = Color(0xFF131722)
private val VioletBright = Color(0xFF9B7AFF)
private val VioletFaint  = Color(0x1E9B7AFF)
private val VioletMid    = Color(0x409B7AFF)
private val TextPri      = Color(0xFFFFFFFF)
private val TextSec      = Color(0x73FFFFFF)

// ─── Dialog State ─────────────────────────────────────────────────────────────

private sealed interface VaultDialog {
    object Disclaimer : VaultDialog
    object SetupPin : VaultDialog
    object VerifyForAdd : VaultDialog
    data class VerifyForView(val image: EncryptedImage) : VaultDialog
}

// ─── Vault Screen ─────────────────────────────────────────────────────────────

@Composable
fun VaultScreen(vm: VaultViewModel = viewModel()) {
    val context         = LocalContext.current
    val images          by vm.images.collectAsStateWithLifecycle()
    val isLoading       by vm.isLoading.collectAsStateWithLifecycle()
    val toastMessage    by vm.toastMessage.collectAsStateWithLifecycle()
    val decryptedBitmap by vm.decryptedBitmap.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { VaultStore.init(context) }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2000)
            vm.clearToast()
        }
    }

    var currentDialog by remember { mutableStateOf<VaultDialog?>(null) }

    LaunchedEffect(Unit) {
        if (!VaultPinManager.hasShownDisclaimer(context)) {
            currentDialog = VaultDialog.Disclaimer
        }
    }

    // Android 11+ 删除原图授权
    val deleteOriginalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* 不需要处理结果 */ }

    // 图片选择器 — PIN 验证通过后再 launch
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bmp = loadBitmapFromUri(context, it)
            bmp?.let { bitmap ->
                vm.encryptAndSave(bitmap)
                // 删除相册原图
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(it))
                        deleteOriginalLauncher.launch(
                            IntentSenderRequest.Builder(pi.intentSender).build()
                        )
                    } else {
                        context.contentResolver.delete(it, null, null)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun onAddClick() {
        if (VaultPinManager.hasPin(context)) {
            currentDialog = VaultDialog.VerifyForAdd
        } else {
            currentDialog = VaultDialog.SetupPin
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = DeepBg)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x207850FF), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.05f),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(size.width * 0.1f, size.height * 0.05f),
                    radius = size.width * 0.55f
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "私密保险箱",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = TextPri, letterSpacing = (-0.5).sp
                    )
                    Text(
                        "AES-256 加密保护",
                        fontSize = 13.sp, color = VioletBright.copy(alpha = 0.8f),
                        letterSpacing = 0.3.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(VioletBright.copy(alpha = 0.3f), VioletBright.copy(alpha = 0.15f))
                            )
                        )
                        .border(1.dp, VioletBright.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { onAddClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加", tint = VioletBright, modifier = Modifier.size(20.dp))
                }
            }

            VaultInfoCard(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))

            if (images.isEmpty()) {
                VaultEmptyState(modifier = Modifier.weight(1f), onAddClick = ::onAddClick)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(images, key = { it.id }) { image ->
                        EncryptedImageCard(
                            image    = image,
                            onView   = { currentDialog = VaultDialog.VerifyForView(image) },
                            onDelete = { vm.deleteImage(image.id) }
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VioletBright, strokeWidth = 2.5.dp)
            }
        }

        toastMessage?.let { msg ->
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)) {
                RunMateToast(
                    message   = msg,
                    isSuccess = msg.contains("保存") || msg.contains("解密") || msg.contains("删除")
                )
            }
        }

        decryptedBitmap?.let { bmp ->
            DecryptedImageViewer(bitmap = bmp, onDismiss = { vm.clearDecryptedBitmap() })
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    when (val dialog = currentDialog) {
        VaultDialog.Disclaimer -> DisclaimerDialog(
            onAccept = {
                VaultPinManager.markDisclaimerShown(context)
                currentDialog = VaultDialog.SetupPin
            },
            onDismiss = {
                VaultPinManager.markDisclaimerShown(context)
                currentDialog = null
            }
        )
        VaultDialog.SetupPin -> PinSetupDialog(
            onPinSet  = { pin -> VaultPinManager.savePin(context, pin); currentDialog = null },
            onDismiss = { currentDialog = null }
        )
        VaultDialog.VerifyForAdd -> PinVerifyDialog(
            title     = "请输入密码",
            onVerify  = { pin ->
                if (VaultPinManager.verifyPin(context, pin)) {
                    currentDialog = null
                    pickImage.launch("image/*")
                    true
                } else false
            },
            onDismiss = { currentDialog = null }
        )
        is VaultDialog.VerifyForView -> PinVerifyDialog(
            title     = "请输入密码",
            onVerify  = { pin ->
                if (VaultPinManager.verifyPin(context, pin)) {
                    currentDialog = null
                    vm.decryptImage(dialog.image)
                    true
                } else false
            },
            onDismiss = { currentDialog = null }
        )
        null -> {}
    }
}

// ─── Disclaimer Dialog ────────────────────────────────────────────────────────

@Composable
private fun DisclaimerDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1C2135),
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Shield, null, tint = VioletBright, modifier = Modifier.size(22.dp))
                Text("隐私保险箱声明", color = TextPri, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DisclaimerItem("照片将使用 AES-256-GCM 加密存储，仅本设备可解密")
                DisclaimerItem("加密后原图将从系统相册中删除，无法在相册中查看")
                DisclaimerItem("加密图片不显示缩略图，必须输入密码后方可查看")
                DisclaimerItem("请牢记您的密码，遗忘密码将无法恢复已加密的照片")
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors  = ButtonDefaults.buttonColors(containerColor = VioletBright)
            ) {
                Text("我已了解，设置密码", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("暂不使用", color = TextSec) }
        }
    )
}

@Composable
private fun DisclaimerItem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = VioletBright, fontSize = 13.sp)
        Text(text, color = TextSec, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

// ─── PIN Setup Dialog ─────────────────────────────────────────────────────────

@Composable
private fun PinSetupDialog(onPinSet: (String) -> Unit, onDismiss: () -> Unit) {
    var step      by remember { mutableIntStateOf(1) }
    var firstPin  by remember { mutableStateOf("") }
    var pin       by remember { mutableStateOf("") }
    var error     by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1C2135),
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (step == 1) "设置密码" else "确认密码",
                color = TextPri, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (step == 1) "请设置 4–6 位数字密码" else "请再次输入密码确认",
                    color = TextSec, fontSize = 13.sp
                )
                if (error) {
                    Text("两次密码不一致，请重新设置", color = Color(0xFFFF3B30), fontSize = 12.sp)
                }
                OutlinedTextField(
                    value       = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            pin = it; error = false
                        }
                    },
                    placeholder           = { Text("请输入数字密码", color = TextSec) },
                    visualTransformation  = PasswordVisualTransformation(),
                    keyboardOptions       = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError               = error,
                    singleLine            = true,
                    colors                = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = VioletBright,
                        unfocusedBorderColor = VioletMid,
                        focusedTextColor     = TextPri,
                        unfocusedTextColor   = TextPri,
                        cursorColor          = VioletBright
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length < 4) return@Button
                    if (step == 1) {
                        firstPin = pin; pin = ""; step = 2
                    } else {
                        if (pin == firstPin) {
                            onPinSet(pin)
                        } else {
                            error = true; pin = ""; step = 1; firstPin = ""
                        }
                    }
                },
                enabled = pin.length >= 4,
                colors  = ButtonDefaults.buttonColors(containerColor = VioletBright)
            ) {
                Text(if (step == 1) "下一步" else "确认", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSec) }
        }
    )
}

// ─── PIN Verify Dialog ────────────────────────────────────────────────────────

@Composable
private fun PinVerifyDialog(
    title: String,
    onVerify: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin   by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1C2135),
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, null, tint = VioletBright, modifier = Modifier.size(20.dp))
                Text(title, color = TextPri, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error) {
                    Text("密码错误，请重试", color = Color(0xFFFF3B30), fontSize = 12.sp)
                }
                OutlinedTextField(
                    value       = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            pin = it; error = false
                        }
                    },
                    placeholder           = { Text("请输入密码", color = TextSec) },
                    visualTransformation  = PasswordVisualTransformation(),
                    keyboardOptions       = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError               = error,
                    singleLine            = true,
                    colors                = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = VioletBright,
                        unfocusedBorderColor = VioletMid,
                        focusedTextColor     = TextPri,
                        unfocusedTextColor   = TextPri,
                        cursorColor          = VioletBright
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!onVerify(pin)) { error = true; pin = "" }
                },
                enabled = pin.isNotEmpty(),
                colors  = ButtonDefaults.buttonColors(containerColor = VioletBright)
            ) {
                Text("确认", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSec) }
        }
    )
}

// ─── Info Card ────────────────────────────────────────────────────────────────

@Composable
fun VaultInfoCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1530), Color(0xFF111827))))
            .border(1.dp, VioletBright.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(VioletBright.copy(alpha = 0.25f), Color.Transparent)))
            )
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(VioletFaint)
                    .border(1.dp, VioletMid, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, null, tint = VioletBright, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("银行级加密保护", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPri)
            Spacer(Modifier.height(3.dp))
            Text("AES-256-GCM · 密码保护 · 仅本设备可解密", fontSize = 12.sp, color = TextSec)
        }
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape)
                .background(Color(0xFF34C759).copy(alpha = 0.15f))
                .border(1.dp, Color(0xFF34C759).copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = Color(0xFF34C759), modifier = Modifier.size(13.dp))
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
fun VaultEmptyState(modifier: Modifier = Modifier, onAddClick: () -> Unit) {
    Column(
        modifier            = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(110.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(VioletBright.copy(alpha = 0.18f), Color.Transparent)))
            )
            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF1A1530), Color(0xFF111827))))
                    .border(1.dp, VioletBright.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = VioletBright.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("保险箱是空的", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPri, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("添加隐私照片，AES-256 加密，原图将从相册删除", fontSize = 13.sp, color = TextSec, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(VioletBright.copy(alpha = 0.8f), Color(0xFF6A4FCC).copy(alpha = 0.8f))))
                .clickable { onAddClick() }
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("选择照片加密", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── Encrypted Image Card ─────────────────────────────────────────────────────

@Composable
fun EncryptedImageCard(
    image: EncryptedImage,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1530), Color(0xFF111827))))
            .border(1.dp, VioletMid, RoundedCornerShape(16.dp))
    ) {
        // 锁图标占位 — 不显示任何缩略图
        Column(
            modifier            = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(VioletFaint)
                    .border(1.dp, VioletMid, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = VioletBright, modifier = Modifier.size(28.dp))
            }
            Text("已加密", fontSize = 11.sp, color = TextSec, letterSpacing = 0.5.sp)
        }

        // 底部操作栏
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VioletFaint)
                    .border(1.dp, VioletMid, RoundedCornerShape(8.dp))
                    .clickable { onView() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.LockOpen, null, tint = VioletBright, modifier = Modifier.size(13.dp))
                    Text("解锁查看", fontSize = 11.sp, color = VioletBright)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF3B30).copy(alpha = 0.15f))
                    .clickable { showDeleteConfirm = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(13.dp))
                    Text("删除", fontSize = 11.sp, color = Color(0xFFFF3B30))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = Color(0xFF1C2135),
            shape            = RoundedCornerShape(20.dp),
            title            = { Text("删除图片", color = TextPri) },
            text             = { Text("确定要永久删除这张加密照片吗？此操作不可恢复。", color = TextSec) },
            confirmButton    = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("删除", color = Color(0xFFFF3B30))
                }
            },
            dismissButton    = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = TextSec) }
            }
        )
    }
}

// ─── Decrypted Image Viewer ───────────────────────────────────────────────────

@Composable
fun DecryptedImageViewer(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
                .clickable { onDismiss() }
        ) {
            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = "解密图片",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxWidth().align(Alignment.Center)
            )
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Default.Close, "关闭", tint = Color.White)
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? = try {
    val stream = context.contentResolver.openInputStream(uri)
    android.graphics.BitmapFactory.decodeStream(stream)
} catch (_: Exception) { null }

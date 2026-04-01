package com.getsolace.ai.chat.ui.screens.vault

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getsolace.ai.chat.data.EncryptedImage
import com.getsolace.ai.chat.data.VaultStore
import com.getsolace.ai.chat.ui.screens.ai.RunMateToast
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.VaultViewModel

// ─── Vault Screen ─────────────────────────────────────────────────────────────

@Composable
fun VaultScreen(vm: VaultViewModel = viewModel()) {
    val context         = LocalContext.current
    val images          by vm.images.collectAsStateWithLifecycle()
    val isLoading       by vm.isLoading.collectAsStateWithLifecycle()
    val toastMessage    by vm.toastMessage.collectAsStateWithLifecycle()
    val decryptedBitmap by vm.decryptedBitmap.collectAsStateWithLifecycle()

    // Init store
    LaunchedEffect(Unit) { VaultStore.init(context) }

    // Toast auto-dismiss
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2000)
            vm.clearToast()
        }
    }

    // Image picker
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bmp = loadBitmapFromUri(context, it)
            bmp?.let { bitmap -> vm.encryptAndSave(bitmap) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BgGradientStart, BgGradientEnd)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "私密保险箱",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize   = AppFontSize.largeTitle
                        )
                    )
                    Text(
                        "AES-256 加密保护",
                        style = MaterialTheme.typography.bodySmall.copy(color = AccentPrimary)
                    )
                }
                IconButton(
                    onClick  = { pickImage.launch("image/*") },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Add, "添加", tint = AccentPrimary)
                }
            }

            // ── Info card ─────────────────────────────────────────────────────
            VaultInfoCard(modifier = Modifier.padding(horizontal = AppSpacing.lg))
            Spacer(Modifier.height(AppSpacing.lg))

            // ── Content ───────────────────────────────────────────────────────
            if (images.isEmpty()) {
                VaultEmptyState(
                    modifier   = Modifier.weight(1f),
                    onAddClick = { pickImage.launch("image/*") }
                )
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(2),
                    contentPadding        = PaddingValues(AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                    verticalArrangement   = Arrangement.spacedBy(AppSpacing.md),
                    modifier              = Modifier.weight(1f)
                ) {
                    items(images, key = { it.id }) { image ->
                        EncryptedImageCard(
                            image    = image,
                            onView   = { vm.decryptImage(image) },
                            onDelete = { vm.deleteImage(image.id) }
                        )
                    }
                }
            }
        }

        // ── Loading overlay ───────────────────────────────────────────────────
        if (isLoading) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentPrimary)
            }
        }

        // ── Toast ─────────────────────────────────────────────────────────────
        toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                RunMateToast(
                    message   = msg,
                    isSuccess = msg.contains("保存") || msg.contains("解密")
                )
            }
        }

        // ── Decrypted image viewer ────────────────────────────────────────────
        decryptedBitmap?.let { bmp ->
            DecryptedImageViewer(
                bitmap    = bmp,
                onDismiss = { vm.clearDecryptedBitmap() }
            )
        }
    }
}

// ─── Info Card ────────────────────────────────────────────────────────────────

@Composable
fun VaultInfoCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(AppRadius.md),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier          = Modifier.padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = AccentPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column {
                Text(
                    "银行级加密保护",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    "图片使用 AES-256-GCM 加密存储，仅在本设备解密",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
fun VaultEmptyState(modifier: Modifier = Modifier, onAddClick: () -> Unit) {
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .padding(AppSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(AccentPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                null,
                tint     = AccentPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(AppSpacing.xl))
        Text(
            "保险箱是空的",
            style     = MaterialTheme.typography.titleMedium.copy(
                color      = TextPrimary,
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            "添加需要保护的隐私照片，使用 AES-256 加密存储",
            style     = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpacing.xxl))
        Button(
            onClick = onAddClick,
            shape   = RoundedCornerShape(AppRadius.md),
            colors  = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(AppSpacing.sm))
            Text("选择照片加密")
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
    val thumbnailBitmap = remember(image.id) { VaultStore.thumbnailBitmap(image) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape  = RoundedCornerShape(AppRadius.md),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap             = thumbnailBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxSize()
                        .blur(8.dp)
                )
            } else {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(CardBgAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Photo, null, tint = TextTertiary, modifier = Modifier.size(32.dp))
                }
            }

            // Dark overlay
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

            // Lock icon
            Icon(
                Icons.Default.Lock,
                null,
                tint     = TextPrimary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(AppSpacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onView, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Visibility, "查看", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "删除", tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title            = { Text("删除图片", color = TextPrimary) },
            text             = { Text("确定要永久删除这张加密照片吗？", color = TextSecondary) },
            confirmButton    = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("删除", color = ErrorRed)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor   = CardBgAlt,
            shape            = RoundedCornerShape(AppRadius.lg)
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
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() }
        ) {
            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = "解密图片",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppSpacing.lg)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
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
} catch (e: Exception) { null }

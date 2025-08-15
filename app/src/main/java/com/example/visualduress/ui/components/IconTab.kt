package com.example.visualduress.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visualduress.R

@Composable
fun IconTab(
    labelKey: String,
    activeTab: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val isActive = labelKey == activeTab
    val label = when (labelKey) {
        "devices" -> stringResource(R.string.tab_devices)
        "floorplan" -> stringResource(R.string.tab_floorplan)
        "ip" -> stringResource(R.string.tab_ip)
        "password" -> stringResource(R.string.tab_password)
        else -> labelKey
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp)) // ✅ Add space at top
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF2196F3) else Color.Black
        )
        Text(
            text = label,
            color = if (isActive) Color(0xFF2196F3) else Color.Black,
            fontSize = 12.sp
        )
    }
}

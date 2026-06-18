package io.qwqc.claudewatch.presentation.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Typography

/**
 * Slightly tightened typography tuned for the small round Watch 5 Pro display.
 * Uses the system default font family (clean, legible) with custom sizes.
 */
val ClaudeTypography: Typography = Typography().run {
    copy(
        title1 = title1.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        title2 = title2.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        title3 = title3.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        body1 = body1.copy(fontSize = 14.sp),
        body2 = body2.copy(fontSize = 12.sp),
        button = button.copy(fontWeight = FontWeight.SemiBold),
        caption1 = caption1.copy(fontSize = 12.sp),
        caption2 = caption2.copy(fontSize = 10.sp),
    )
}

package ir.hawraa.quran.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.Vazirmatn

/**
 * The primary action button: a solid accent pill (the single accent rule — a filled
 * accent shape means "this is the current thing"). Shows a spinner in place of the
 * label while [loading].
 *
 * [BubbleButton] stays in its own file: it predates this port and carries the
 * filled/outlined/busy capabilities Hawraa's dock and player already use.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val tokens = LocalHawraaTokens.current
    val geo = LocalHawraaGeometry.current
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(geo.radius.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = tokens.accent,
            contentColor = tokens.onAccent,
            disabledContainerColor = tokens.glassFill,
            disabledContentColor = tokens.textTertiary,
        ),
        modifier = modifier.fillMaxWidth().height(geo.controls.xl),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = tokens.onAccent,
                strokeWidth = geo.controls.spinnerStroke,
                modifier = Modifier.size(geo.controls.spinner),
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = Vazirmatn),
            )
        }
    }
}

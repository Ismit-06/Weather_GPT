package com.example.weathergpt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weathergpt.ui.theme.AIViolet
import com.example.weathergpt.ui.theme.BackgroundDeep
import com.example.weathergpt.ui.theme.NeonBlue
import com.example.weathergpt.ui.theme.NeonCyan
import com.example.weathergpt.ui.theme.RiskRed
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.SurfaceDark
import com.example.weathergpt.ui.theme.SurfaceGlass
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary

@Composable
fun AiOrb(
    modifier: Modifier = Modifier
) {

    Box(
        modifier =
            modifier.size(76.dp),
        contentAlignment =
            Alignment.Center
    ) {

        Box(
            modifier =
                Modifier
                    .size(70.dp)
                    .blur(18.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                NeonCyan.copy(0.55f),
                                AIViolet.copy(0.35f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
        )

        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                NeonCyan,
                                NeonBlue,
                                AIViolet
                            )
                        ),
                        CircleShape
                    ),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "✦",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {

    Box(
        modifier =
            modifier
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(
                                SurfaceGlass,
                                SurfaceDark.copy(
                                    alpha = 0.94f
                                )
                            )
                        ),
                    shape =
                        RoundedCornerShape(22.dp)
                )
                .border(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            listOf(
                                NeonBlue.copy(0.30f),
                                Color.Transparent,
                                AIViolet.copy(0.25f)
                            )
                        ),
                    shape =
                        RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
    ) {
        content()
    }
}

@Composable
fun IntelligenceBadge(
    text: String,
    positive: Boolean = true
) {

    Row(
        modifier =
            Modifier
                .background(
                    if (positive) {
                        SuccessGreen.copy(
                            alpha = 0.10f
                        )
                    } else {
                        RiskRed.copy(
                            alpha = 0.10f
                        )
                    },
                    RoundedCornerShape(50)
                )
                .border(
                    1.dp,
                    if (positive) {
                        SuccessGreen.copy(
                            alpha = 0.25f
                        )
                    } else {
                        RiskRed.copy(
                            alpha = 0.25f
                        )
                    },
                    RoundedCornerShape(50)
                )
                .padding(
                    horizontal = 10.dp,
                    vertical = 5.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .background(
                        if (positive) {
                            SuccessGreen
                        } else {
                            RiskRed
                        },
                        CircleShape
                    )
        )

        Spacer(
            modifier =
                Modifier.size(6.dp)
        )

        Text(
            text = text,

            color =
                if (positive) {
                    SuccessGreen
                } else {
                    RiskRed
                },

            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    icon: String,
    modifier: Modifier = Modifier
) {

    GlassCard(
        modifier = modifier
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.Top
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = icon,
                    fontSize = androidx.compose.ui.unit
                        .TextUnit.Unspecified
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Text(
                    text = title,
                    color = TextMuted
                )

                Spacer(
                    modifier =
                        Modifier.height(3.dp)
                )

                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize =
                        22.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text = subtitle,
                    color = TextSecondary
                )
            }
        }
    }
}


@Composable
fun AiSectionTitle(
    eyebrow: String,
    title: String,
    subtitle: String? = null
) {

    Column {

        Text(
            text = eyebrow.uppercase(),
            color = NeonCyan,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp
        )

        subtitle?.let {

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text = it,
                color = TextMuted
            )
        }
    }
}

package ai.bewsoa.flow.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space

@Composable
fun ChatScreen() {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Chat",
            style = MaterialTheme.typography.headlineMedium,
            color = palette.textBright
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = "Ask about your week, reshape your program,\nor export your data.\nComing in the next build.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textDim,
            textAlign = TextAlign.Center
        )
    }
}

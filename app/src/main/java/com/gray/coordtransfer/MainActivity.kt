package com.gray.coordtransfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoordTransferTheme {
                CoordinateConverterScreen()
            }
        }
    }
}

private val Background = Color(0xFF0B1020)
private val CardColor = Color(0xFF141B2D)
private val InputColor = Color(0xFF1B2439)
private val TextColor = Color(0xFFEEF2FF)
private val MutedColor = Color(0xFF9AA6BF)
private val AccentColor = Color(0xFF7DD3FC)
private val BorderColor = Color(0xFF2A3550)
private val ErrorColor = Color(0xFFFB7185)

private val AppColors = darkColorScheme(
    primary = AccentColor,
    onPrimary = Background,
    secondary = Color(0xFFB8D7E8),
    background = Background,
    surface = CardColor,
    surfaceVariant = InputColor,
    onSurface = TextColor,
    onSurfaceVariant = MutedColor,
    outline = BorderColor,
    outlineVariant = BorderColor,
    error = ErrorColor
)

@Composable
private fun CoordTransferTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        content = content
    )
}

@Composable
private fun CoordinateConverterScreen() {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val inputText = input.text

    val conversionResult = remember(inputText) {
        if (inputText.isBlank()) null
        else runCatching { CoordinateConverter.convert(inputText) }
    }

    fun insertSymbol(symbol: String) {
        val start = input.selection.start.coerceIn(0, input.text.length)
        val end = input.selection.end.coerceIn(start, input.text.length)
        val updatedText = input.text.replaceRange(start, end, symbol)
        input = TextFieldValue(
            text = updatedText,
            selection = TextRange(start + symbol.length)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "COORD TRANSFER",
                    color = AccentColor,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = "Мультик",
                    color = TextColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "(Мультикоординатный конвертер)",
                    color = MutedColor.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Вхідні координати",
                        color = TextColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(10.dp),
                        placeholder = {
                            Text(
                                "49.8397, 24.0297\n" +
                                    "49° 50.382' N 24° 1.782' E\n" +
                                    "35U 426000 5520000\n" +
                                    "35U LV 26000 20000",
                                color = MutedColor
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = AccentColor,
                            focusedContainerColor = InputColor,
                            unfocusedContainerColor = InputColor
                        ),
                        trailingIcon = {
                            if (input.text.isNotEmpty()) {
                                IconButton(onClick = { input = TextFieldValue("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Очистити поле",
                                        tint = MutedColor
                                    )
                                }
                            }
                        }
                    )

                    Text(
                        text = "Символи координат",
                        color = MutedColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("°", "'", "\"").forEach { symbol ->
                            Button(
                                onClick = { insertSymbol(symbol) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InputColor,
                                    contentColor = AccentColor
                                ),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Text(
                                    text = symbol,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { input = TextFieldValue("") }) {
                            Text("Очистити", color = AccentColor)
                        }
                    }
                }
            }

            when {
                inputText.isBlank() -> {
                    InfoCard(
                        title = "Як користуватися",
                        body = "Встав або введи координати в підтримуваному форматі. " +
                            "Додаток визначить формат і автоматично перерахує координати."
                    )
                    Text(
                        text = "Формати: DD · DDM · DMS · UTM · MGRS",
                        color = MutedColor,
                        fontSize = 12.sp
                    )
                }

                conversionResult?.isFailure == true -> {
                    InfoCard(
                        title = "Не вдалося розпізнати координати",
                        body = conversionResult.exceptionOrNull()?.message
                            ?: "Перевір формат координат і спробуй ще раз.",
                        isError = true
                    )
                    Text(
                        text = "Приклади: 49.8397, 24.0297 · 35U 426000 5520000 · 35U LV 26000 20000",
                        color = MutedColor,
                        fontSize = 12.sp
                    )
                }

                conversionResult?.isSuccess == true -> {
                    val conversion = conversionResult.getOrThrow()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = InputColor),
                        border = BorderStroke(1.dp, BorderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "ВИЯВЛЕНО ФОРМАТ",
                                color = AccentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = conversion.detectedFormat.displayName,
                                color = TextColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Lat " + String.format(
                                    java.util.Locale.US,
                                    "%.6f",
                                    conversion.point.latitude
                                ) + "   Lon " + String.format(
                                    java.util.Locale.US,
                                    "%.6f",
                                    conversion.point.longitude
                                ),
                                color = MutedColor,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Результати перерахунку",
                            color = TextColor,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = conversion.outputs.size.toString() + " форматів",
                            color = MutedColor,
                            fontSize = 12.sp
                        )
                    }

                    conversion.outputs.forEach { output ->
                        OutputCard(
                            output = output,
                            onCopy = {
                                clipboard.setText(AnnotatedString(output.value))
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        output.title + " — скопійовано"
                                    )
                                }
                            }
                        )
                    }

                    Text(
                        text = "UTM/MGRS доступні у широтах від 80° пд. ш. до 84° пн. ш.",
                        color = MutedColor,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(color = BorderColor)
            Text(
                text = "For НКа \"Нова Каховка\" by Gray",
                color = Color(0xFF66718A),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun OutputCard(
    output: CoordinateOutput,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = output.title,
                    color = MutedColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = output.value,
                    color = TextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Копіювати: " + output.title,
                    tint = AccentColor
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    isError: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) Color(0xFF321B2A) else InputColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isError) Color(0xFF5C2732) else BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = if (isError) ErrorColor else TextColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = MutedColor,
                fontSize = 14.sp
            )
        }
    }
}

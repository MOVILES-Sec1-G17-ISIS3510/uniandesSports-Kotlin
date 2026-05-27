package com.uniandes.sport.ui.screens.sport_tools

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.models.nutrition.NutritionPlan
import com.uniandes.sport.ui.components.OfflineConnectivityBanner
import com.uniandes.sport.ui.components.rememberIsOnline
import com.uniandes.sport.ui.theme.ArchivoFamily
import com.uniandes.sport.viewmodels.nutrition.NutritionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// asistente de nutricion: form para perfil + plan renderizado en markdown.
// si el usuario ya genero un plan, al entrar a la pantalla aparece inmediatamente
// desde filesystem (vista protegida offline) sin tener que pegarle a la red.
// el generar nuevo plan requiere internet (call a openai)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionAssistantScreen(
    onNavigateBack: () -> Unit,
    viewModel: NutritionViewModel = viewModel()
) {
    val context = LocalContext.current
    val isOnline = rememberIsOnline()
    val currentPlan by viewModel.currentPlan.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI Nutrition Assistant",
                        fontWeight = FontWeight.Black,
                        fontFamily = ArchivoFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentPlan != null) {
                        IconButton(onClick = { viewModel.deletePlan() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete plan")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OfflineConnectivityBanner(
                offlineMessage = if (currentPlan != null)
                    "Showing your last saved plan. Generating a new one requires internet."
                else
                    "Internet required to generate a new plan."
            )

            if (currentPlan != null) {
                PlanView(
                    plan = currentPlan!!,
                    onGenerateNew = { viewModel.deletePlan() }
                )
            } else {
                GenerateForm(
                    isLoading = isLoading,
                    isOnline = isOnline,
                    onSubmit = { age, w, h, goal, restr ->
                        viewModel.generateNewPlan(age, w, h, goal, restr)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateForm(
    isLoading: Boolean,
    isOnline: Boolean,
    onSubmit: (Int, Double, Double, String, String) -> Unit
) {
    val context = LocalContext.current
    var age by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("Maintain weight") }
    var restrictions by remember { mutableStateOf("") }

    val goals = listOf("Lose weight", "Maintain weight", "Build muscle", "Improve endurance", "Improve recovery")
    var goalsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                "Tell us about you",
                fontWeight = FontWeight.Black,
                fontFamily = ArchivoFamily,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            "We will craft a daily nutrition plan tailored to your profile and goals.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = age,
            onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Age (years)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = sanitizeDecimal(it) },
                label = { Text("Weight (kg)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = height,
                onValueChange = { height = sanitizeDecimal(it) },
                label = { Text("Height (cm)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }

        ExposedDropdownMenuBox(
            expanded = goalsExpanded,
            onExpandedChange = { goalsExpanded = it }
        ) {
            OutlinedTextField(
                value = goal,
                onValueChange = {},
                readOnly = true,
                label = { Text("Goal") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = goalsExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = goalsExpanded, onDismissRequest = { goalsExpanded = false }) {
                goals.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            goal = option
                            goalsExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = restrictions,
            onValueChange = { restrictions = it.take(160) },
            label = { Text("Dietary restrictions/preferences (optional)") },
            placeholder = { Text("e.g. vegetarian, lactose intolerant") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val ageInt = age.toIntOrNull() ?: 0
                val weightDouble = weight.toDoubleOrNull() ?: 0.0
                val heightDouble = height.toDoubleOrNull() ?: 0.0
                if (ageInt <= 0 || weightDouble <= 0.0 || heightDouble <= 0.0) {
                    Toast.makeText(context, "Please enter valid age, weight and height.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (!isOnline) {
                    Toast.makeText(context, "Internet is required to generate a new plan.", Toast.LENGTH_LONG).show()
                    return@Button
                }
                onSubmit(ageInt, weightDouble, heightDouble, goal, restrictions)
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate plan", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PlanView(plan: NutritionPlan, onGenerateNew: () -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.US) }
    val createdLabel = if (plan.createdAtMillis > 0)
        dateFormatter.format(Date(plan.createdAtMillis))
    else "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Your nutrition plan",
                    fontWeight = FontWeight.Black,
                    fontFamily = ArchivoFamily,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Generated $createdLabel · ${plan.goal}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }

        MarkdownText(markdown = plan.planContent)

        OutlinedButton(
            onClick = onGenerateNew,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Generate a new plan", fontWeight = FontWeight.Bold)
        }
    }
}

// parser ligero de markdown para no depender de una libreria externa.
// soporta headings (#, ##, ###), bullets (- y *), parrafos y bold inline (**texto**)
@Composable
private fun MarkdownText(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        markdown.split("\n").forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") -> Text(
                    parseInlineBold(line.removePrefix("### ")),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                line.startsWith("## ") -> Text(
                    parseInlineBold(line.removePrefix("## ")),
                    fontWeight = FontWeight.Black,
                    fontFamily = ArchivoFamily,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                line.startsWith("# ") -> Text(
                    parseInlineBold(line.removePrefix("# ")),
                    fontWeight = FontWeight.Black,
                    fontFamily = ArchivoFamily,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        parseInlineBold(line.drop(2)),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> Text(
                    parseInlineBold(line),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// convierte segmentos **texto** en spans bold dentro de un annotatedstring.
// el resto del texto se conserva con el estilo por defecto
private fun parseInlineBold(text: String) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) {
            append(text.substring(i))
            return@buildAnnotatedString
        }
        if (start > i) append(text.substring(i, start))
        val end = text.indexOf("**", start + 2)
        if (end < 0) {
            append(text.substring(start))
            return@buildAnnotatedString
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(start + 2, end))
        }
        i = end + 2
    }
}

private fun sanitizeDecimal(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val parts = filtered.split('.')
    return if (parts.size <= 2) filtered.take(6) else (parts[0] + "." + parts.drop(1).joinToString("")).take(6)
}

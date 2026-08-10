package com.shonlabs.payday

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shonlabs.payday.ui.theme.PaydayTheme
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

// --- CONFIGURACIÓN DE ESTÉTICA ---
val PrimaryBlue = Color(0xFF0056BD)
val BackgroundGray = Color(0xFFF8F9FA)
val StatusGreen = Color(0xFFB2F2EF)
val TextDark = Color(0xFF2D3436)
val NotificationRed = Color(0xFFFF3B30)

data class UserProfile(
    val name: String,
    val rut: String,
    val nextPaymentDate: LocalDate?
) {
    // Verificación en tiempo real
    fun isExpired(): Boolean {
        val today = LocalDate.now()
        return nextPaymentDate == null || nextPaymentDate.isBefore(today) || nextPaymentDate.isEqual(today)
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("rut", rut)
            put("nextPaymentDate", nextPaymentDate?.toString() ?: "null")
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): UserProfile {
            val dateStr = json.getString("nextPaymentDate")
            return UserProfile(
                name = json.getString("name"),
                rut = json.getString("rut"),
                nextPaymentDate = if (dateStr == "null") null else LocalDate.parse(dateStr)
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaydayTheme {
                TesoreriaApp()
            }
        }
    }
}

// --- PERSISTENCIA ---
fun saveProfiles(context: Context, profiles: List<UserProfile>) {
    val sharedPref = context.getSharedPreferences("payday_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    profiles.forEach { jsonArray.put(it.toJsonObject()) }
    sharedPref.edit().putString("profiles_json", jsonArray.toString()).apply()
}

fun loadProfiles(context: Context): List<UserProfile> {
    val sharedPref = context.getSharedPreferences("payday_prefs", Context.MODE_PRIVATE)
    val jsonString = sharedPref.getString("profiles_json", null) ?: return emptyList()
    val profiles = mutableListOf<UserProfile>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            profiles.add(UserProfile.fromJsonObject(jsonArray.getJSONObject(i)))
        }
    } catch (e: Exception) { e.printStackTrace() }
    return profiles
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesoreriaApp() {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(loadProfiles(context)) }
    var selectedProfileId by remember { mutableStateOf(profiles.firstOrNull()?.rut) }

    // El perfil seleccionado se recalcula si la lista cambia
    val selectedProfile by remember(profiles, selectedProfileId) {
        derivedStateOf { profiles.find { it.rut == selectedProfileId } }
    }

    // Efecto para limpiar fechas que ya pasaron
    LaunchedEffect(profiles) {
        val today = LocalDate.now()
        val hasChanges = profiles.any {
            it.nextPaymentDate != null && (it.nextPaymentDate.isBefore(today) || it.nextPaymentDate.isEqual(today))
        }

        if (hasChanges) {
            val updatedList = profiles.map {
                if (it.nextPaymentDate != null && (it.nextPaymentDate.isBefore(today) || it.nextPaymentDate.isEqual(today))) {
                    it.copy(nextPaymentDate = null)
                } else it
            }
            profiles = updatedList
            saveProfiles(context, updatedList)
        }
    }

    var currentScreen by remember { mutableStateOf("profiles") }
    var showAddProfileScreen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundGray)) {
        if (profiles.isEmpty() || showAddProfileScreen) {
            CreateProfileScreen(
                isFirstProfile = profiles.isEmpty(),
                onProfileCreated = { newProfile ->
                    profiles = profiles + newProfile
                    saveProfiles(context, profiles)
                    selectedProfileId = newProfile.rut
                    showAddProfileScreen = false
                    currentScreen = "payments"
                },
                onCancel = { showAddProfileScreen = false }
            )
        } else {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AccountBalance, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Tesorería", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextDark)
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 0.dp,
                        modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    ) {
                        val items = listOf(
                            Triple("payments", "PAYMENTS", Icons.Default.AccountBalanceWallet),
                            Triple("history", "HISTORY", Icons.Default.History),
                            Triple("profiles", "PROFILES", Icons.Default.Group)
                        )
                        items.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = currentScreen == route,
                                onClick = { currentScreen = route },
                                label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 9.sp) },
                                icon = {
                                    BadgedBox(badge = {
                                        if (route == "profiles" && profiles.any { it.isExpired() }) {
                                            Badge(containerColor = NotificationRed)
                                        }
                                    }) { Icon(icon, null) }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryBlue,
                                    selectedTextColor = PrimaryBlue,
                                    indicatorColor = PrimaryBlue.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                },
                containerColor = BackgroundGray
            ) { padding ->
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    when (currentScreen) {
                        "profiles" -> ProfilesScreen(
                            profiles = profiles,
                            selectedProfileId = selectedProfileId,
                            onAddNewProfile = { showAddProfileScreen = true },
                            onProfileSelected = { profile ->
                                selectedProfileId = profile.rut
                                currentScreen = "payments"
                            },
                            onUpdatePaymentDate = { profile ->
                                showDatePicker(context) { newDate ->
                                    val newList = profiles.map {
                                        if (it.rut == profile.rut) it.copy(nextPaymentDate = newDate) else it
                                    }
                                    profiles = newList
                                    saveProfiles(context, newList)
                                }
                            }
                        )
                        "payments" -> PaymentsDetailScreen(
                            profile = selectedProfile,
                            onUpdateDate = {
                                selectedProfile?.let { profile ->
                                    if (profile.isExpired()) {
                                        showDatePicker(context) { newDate ->
                                            val newList = profiles.map {
                                                if (it.rut == profile.rut) it.copy(nextPaymentDate = newDate) else it
                                            }
                                            profiles = newList
                                            saveProfiles(context, newList)
                                        }
                                    }
                                }
                            }
                        )
                        "history" -> PlaceholderScreen("History", Icons.Default.History)
                    }
                }
            }
        }
    }
}

fun showDatePicker(context: Context, onDateSelected: (LocalDate) -> Unit) {
    val calendar = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onDateSelected(LocalDate.of(year, month + 1, day))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).apply {
        datePicker.minDate = System.currentTimeMillis() + 86400000
    }.show()
}

@Composable
fun ProfilesScreen(
    profiles: List<UserProfile>,
    selectedProfileId: String?,
    onAddNewProfile: () -> Unit,
    onProfileSelected: (UserProfile) -> Unit,
    onUpdatePaymentDate: (UserProfile) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Profiles", fontSize = 36.sp, fontWeight = FontWeight.Black, color = TextDark)
        Text("Manage your linked accounts", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            profiles.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    isSelected = profile.rut == selectedProfileId,
                    onCardClick = {
                        if (profile.isExpired()) onUpdatePaymentDate(profile)
                        else onProfileSelected(profile)
                    }
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth().height(80.dp).clickable { onAddNewProfile() },
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.5f),
                border = BorderStroke(2.dp, Color.LightGray.copy(alpha = 0.3f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Add, null, tint = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text("ADD NEW PROFILE", fontWeight = FontWeight.ExtraBold, color = Color.Gray, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProfileCard(profile: UserProfile, isSelected: Boolean, onCardClick: () -> Unit) {
    // Re-evaluación dinámica del estado de expiración
    val isExpired by remember(profile.nextPaymentDate) {
        derivedStateOf { profile.isExpired() }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp),
        shape = RoundedCornerShape(20.dp),
        border = if (isSelected) BorderStroke(2.dp, PrimaryBlue) else null
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(if (isSelected) PrimaryBlue else Color(0xFFF1F3F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = if (isSelected) Color.White else Color.Gray)
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(profile.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextDark)
                    Text(profile.rut, color = Color.Gray, fontSize = 14.sp)
                }

                if (isSelected && !isExpired) {
                    Icon(Icons.Default.CheckCircle, null, tint = PrimaryBlue)
                }
            }

            // El bullet ahora escucha directamente al estado derivado isExpired
            if (isExpired) {
                Box(
                    modifier = Modifier
                        .padding(14.dp)
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .background(NotificationRed, CircleShape)
                )
            }
        }
    }
}

@Composable
fun PaymentsDetailScreen(profile: UserProfile?, onUpdateDate: () -> Unit) {
    val isExpired by remember(profile?.nextPaymentDate) {
        derivedStateOf { profile?.isExpired() ?: true }
    }
    val today = LocalDate.now()

    val daysUntil = if (profile?.nextPaymentDate != null) {
        ChronoUnit.DAYS.between(today, profile.nextPaymentDate)
    } else 0

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp).clickable(enabled = isExpired) { onUpdateDate() },
            colors = CardDefaults.cardColors(containerColor = if (isExpired) Color(0xFF546E7A) else PrimaryBlue),
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (isExpired) "ACTION REQUIRED" else "NEXT PAYMENT IN", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (isExpired) "SET DATE" else "$daysUntil", fontSize = if (isExpired) 40.sp else 74.sp, fontWeight = FontWeight.Black, color = Color.White)
                if (!isExpired) Text("days left", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoSquareCardCompact("STATUS", if (isExpired) "EXPIRED" else "ACTIVE", Icons.Default.Verified, Modifier.weight(1f), isStatus = true)
            InfoSquareCardCompact("AMOUNT", "$420.00", Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable(enabled = isExpired) { onUpdateDate() },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, null, tint = if (isExpired) NotificationRed else PrimaryBlue)
                Column(Modifier.padding(horizontal = 16.dp).weight(1f)) {
                    Text("Payment Date", fontWeight = FontWeight.Bold)
                    Text(profile?.nextPaymentDate?.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")) ?: "Tap to set date", color = if (isExpired) NotificationRed else Color.Gray)
                }
                if (isExpired) Icon(Icons.Default.Edit, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun InfoSquareCardCompact(label: String, value: String, icon: ImageVector, modifier: Modifier, isStatus: Boolean = false) {
    Card(modifier = modifier.height(110.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            if (isStatus) {
                val color = if (value == "ACTIVE") StatusGreen else Color(0xFFFFEBEE)
                Surface(color = color, shape = RoundedCornerShape(8.dp)) {
                    Text(value, modifier = Modifier.padding(horizontal = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun CreateProfileScreen(
    isFirstProfile: Boolean,
    onProfileCreated: (UserProfile) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var rut by remember { mutableStateOf("") }

    // Estado para la fecha seleccionada (por defecto mañana)
    var selectedDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    val dateString = selectedDate.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .padding(24.dp)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        // Encabezado
        Text(
            text = if (isFirstProfile) "Welcome" else "New Profile",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = TextDark
        )
        Text(
            text = "Enter the details for the new linked account.",
            color = Color.Gray,
            fontSize = 16.sp
        )

        Spacer(Modifier.height(8.dp))

        // Campo Nickname
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nickname") },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryBlue) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        // Campo RUT
        OutlinedTextField(
            value = rut,
            onValueChange = { rut = it },
            label = { Text("RUT / ID Number") },
            leadingIcon = { Icon(Icons.Default.Badge, null, tint = PrimaryBlue) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        // Selector de Fecha (Nuevo)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showDatePicker(context) { newDate -> selectedDate = newDate }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Event, null, tint = PrimaryBlue)
                Column(Modifier.padding(horizontal = 16.dp).weight(1f)) {
                    Text("First Payment Date", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(dateString, fontWeight = FontWeight.Bold, color = TextDark)
                }
                Icon(Icons.Default.Edit, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        // Botón Guardar
        Button(
            onClick = {
                if (name.isNotBlank() && rut.isNotBlank()) {
                    onProfileCreated(UserProfile(name, rut, selectedDate))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            enabled = name.isNotBlank() && rut.isNotBlank()
        ) {
            Text("SAVE PROFILE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }

        // Botón Cancelar
        if (!isFirstProfile) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
            Text(name, color = Color.Gray)
        }
    }
}
package com.uniandes.sport.ui.screens.tabs.communities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.uniandes.sport.models.Community
import com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel
import com.uniandes.sport.viewmodels.communities.CommunitiesViewModelInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.activity.compose.BackHandler

@Composable
fun CommunitiesMainScreen(
    viewModel: CommunitiesViewModelInterface,
    authViewModel: FirebaseAuthViewModel = viewModel(),
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val communities by viewModel.communities.collectAsState()
    val myCommunityIds by viewModel.myCommunityIds.collectAsState()

    var selectedFilter by remember { mutableStateOf("My Communities") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCommunityId by remember { mutableStateOf<String?>(null) }
    var showCreateCommunityDialog by remember { mutableStateOf(false) }

    var currentUserId by remember { mutableStateOf<String?>(null) }
    var currentUserDisplayName by remember { mutableStateOf("Usuario") }

    // Init load
    LaunchedEffect(Unit) {
        viewModel.loadCommunities()
        authViewModel.getUser(
            onSuccess = { user ->
                currentUserId = user.uid
                currentUserDisplayName = user.fullName.ifBlank { user.email }
                viewModel.loadUserMemberships(user.uid)
            },
            onFailure = {
                currentUserId = null
            }
        )
    }

    val filters = listOf("My Communities", "Other Communities")
    val filteredCommunities = communities.filter { community ->
        val filterMatches = when (selectedFilter) {
            "My Communities" -> currentUserId != null && (community.ownerId == currentUserId || myCommunityIds.contains(community.id))
            "Other Communities" -> currentUserId == null || (community.ownerId != currentUserId && !myCommunityIds.contains(community.id))
            else -> true
        }
        val query = searchQuery.trim().lowercase()
        val queryMatches = query.isBlank() ||
            community.name.lowercase().contains(query) ||
            community.sport.lowercase().contains(query) ||
            community.description.lowercase().contains(query)
        filterMatches && queryMatches
    }

    BackHandler(enabled = selectedCommunityId != null) {
        selectedCommunityId = null
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
                // Filters
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                        placeholder = { Text("Search communities, sports, or keywords") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filters) { filter ->
                            FilterPill(
                                text = filter,
                                isSelected = selectedFilter == filter,
                                onClick = { selectedFilter = filter }
                            )
                        }
                    }
                }

                // Standard List
                item {
                    Text(
                        text = when (selectedFilter) {
                            "My Communities" -> "MY COMMUNITIES"
                            else -> "OTHER COMMUNITIES"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                    )
                }

                val listToRender = filteredCommunities
                if (listToRender.isEmpty()) {
                    item {
                        Text(
                            text = "No communities found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }

                items(listToRender) { community ->
                    StandardCommunityCard(community, currentUserId, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        selectedCommunityId = it.id
                    }
                }
            }

        FloatingActionButton(
            onClick = {
                if (currentUserId == null) {
                    Toast.makeText(context, "Please log in to create a community", Toast.LENGTH_SHORT).show()
                    return@FloatingActionButton
                }
                showCreateCommunityDialog = true
            },
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create community")
        }
        
        // Render Detail Modal if a community is selected
        if (selectedCommunityId != null) {
            val selectedCom = communities.find { it.id == selectedCommunityId }
            if (selectedCom != null) {
                CommunityDetailModal(
                    community = selectedCom,
                    viewModel = viewModel,
                    onDismiss = { selectedCommunityId = null }
                )
            }
        }

        if (showCreateCommunityDialog) {
            CreateCommunityDialog(
                onDismiss = { showCreateCommunityDialog = false },
                onCreate = { name, type, sport, description ->
                    val uid = currentUserId
                    if (uid == null) {
                        Toast.makeText(context, "Please log in to create a community", Toast.LENGTH_SHORT).show()
                        return@CreateCommunityDialog
                    }

                    viewModel.createCommunity(
                        name = name,
                        type = type,
                        sport = sport,
                        description = description,
                        ownerId = uid,
                        ownerDisplayName = currentUserDisplayName,
                        onSuccess = {
                            showCreateCommunityDialog = false
                            Toast.makeText(context, "Community created", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = {
                            Toast.makeText(context, "Could not create community", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun CreateCommunityDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: String, sport: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Community") }
    var sport by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create community") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = sport, onValueChange = { sport = it }, label = { Text("Sport") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, maxLines = 3)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(text = "Community", isSelected = type == "Community") { type = "Community" }
                    FilterPill(text = "Clan", isSelected = type == "Clan") { type = "Clan" }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, type, sport, description) },
                enabled = name.isNotBlank() && sport.isNotBlank() && description.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FilterPill(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun TrendingCommunityCard(community: Community, currentUserId: String?, onClick: (Community) -> Unit) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                )
            )
            .clickable { onClick(community) }
    ) {
        // Background Watermark Letter
        Text(
            text = community.name.take(1),
            color = Color.White.copy(alpha = 0.05f),
            fontSize = 100.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 16.dp, y = 16.dp)
        )

        Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = community.type.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.ChevronRight, 
                        contentDescription = "Go", 
                        tint = Color.White, 
                        modifier = Modifier.padding(4.dp).size(14.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = if (currentUserId != null && community.ownerId == currentUserId) "${community.name} \uD83D\uDC51" else community.name, 
                color = Color.White, 
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = community.sport, 
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f), 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    background = Color.Black.copy(alpha = 0.2f),
                    padding = 4.dp,
                    shape = RoundedCornerShape(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Group, contentDescription = "Members", tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${community.memberCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    background = Color.Black.copy(alpha = 0.2f),
                    padding = 4.dp,
                    shape = RoundedCornerShape(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ChatBubble, contentDescription = "Channels", tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${community.channelCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Row(background: Color, padding: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape, verticalAlignment: Alignment.Vertical, content: @Composable RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.background(background, shape).padding(padding),
        verticalAlignment = verticalAlignment,
        content = content
    )
}

@Composable
fun StandardCommunityCard(community: Community, currentUserId: String?, modifier: Modifier = Modifier, onClick: (Community) -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        onClick = { onClick(community) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = community.name.take(1),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentUserId != null && community.ownerId == currentUserId) "${community.name} \uD83D\uDC51" else community.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = community.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = community.type.uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.Gray))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = community.sport, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, contentDescription = "Members", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${community.memberCount}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChatBubble, contentDescription = "Channels", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${community.channelCount}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

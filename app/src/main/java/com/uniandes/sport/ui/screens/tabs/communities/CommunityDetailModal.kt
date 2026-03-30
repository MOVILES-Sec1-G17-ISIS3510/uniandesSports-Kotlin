package com.uniandes.sport.ui.screens.tabs.communities

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.models.Channel
import com.uniandes.sport.models.ChannelMessage
import com.uniandes.sport.models.Community
import com.uniandes.sport.models.CommunityMember
import com.uniandes.sport.models.Post
import com.uniandes.sport.models.PostComment
import com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel
import com.uniandes.sport.viewmodels.communities.CommunitiesViewModelInterface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailModal(
    community: Community,
    viewModel: CommunitiesViewModelInterface,
    authViewModel: FirebaseAuthViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    BackHandler {
        onDismiss()
    }

    var currentUserId by remember { mutableStateOf<String?>(null) }
    var currentUserDisplayName by remember { mutableStateOf("Usuario") }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Feed", "Channels", "Members")

    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var selectedPostForComments by remember { mutableStateOf<Post?>(null) }
    var isJoining by remember { mutableStateOf(false) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authViewModel.getUser(
            onSuccess = { user ->
                currentUserId = user.uid
                currentUserDisplayName = user.fullName.ifBlank { user.email }
            },
            onFailure = {
                currentUserId = null
            }
        )
    }

    LaunchedEffect(community.id) {
        viewModel.loadCommunityDetails(community.id)
    }

    val posts by viewModel.posts.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val members by viewModel.members.collectAsState()
    val channelMessages by viewModel.channelMessages.collectAsState()
    val hasMoreOldChannelMessages by viewModel.hasMoreOldChannelMessages.collectAsState()
    val isLoadingOlderChannelMessages by viewModel.isLoadingOlderChannelMessages.collectAsState()
    val postComments by viewModel.postComments.collectAsState()

    val userMembership = currentUserId?.let { uid -> members.find { it.userId == uid } }
    val userAlreadyMember = userMembership != null
    val isCurrentUserAdmin = userMembership?.role.equals("admin", ignoreCase = true)

    if (selectedChannel != null) {
        val channel = selectedChannel!!
        Dialog(
            onDismissRequest = { selectedChannel = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
        ChannelRoomScreen(
            community = community,
            channel = channel,
            messages = channelMessages,
            currentUserId = currentUserId,
            currentUserDisplayName = currentUserDisplayName,
            onBack = { selectedChannel = null },
            onLoadMessages = { viewModel.loadChannelMessages(community.id, channel.id) },
            onLoadOlder = { viewModel.loadOlderChannelMessages() },
            onSend = { content ->
                val uid = currentUserId
                if (uid == null) {
                    Toast.makeText(context, "Please log in to post", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.sendChannelMessage(
                        communityId = community.id,
                        channelId = channel.id,
                        authorId = uid,
                        authorName = currentUserDisplayName,
                        content = content,
                        onFailure = {
                            Toast.makeText(context, "Could not send message", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            onReact = { message, emoji ->
                val uid = currentUserId
                if (uid == null) {
                    Toast.makeText(context, "Please log in to react", Toast.LENGTH_SHORT).show()
                    return@ChannelRoomScreen
                }
                viewModel.reactToChannelMessage(
                    communityId = community.id,
                    channelId = channel.id,
                    messageId = message.id,
                    userId = uid,
                    emoji = emoji,
                    onFailure = {
                        Toast.makeText(context, "Could not react", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            hasMoreOldMessages = hasMoreOldChannelMessages,
            isLoadingOlderMessages = isLoadingOlderChannelMessages
        )
        }
        return
    }


    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val windowInsets = WindowInsets.systemBars
        Scaffold(
            contentWindowInsets = windowInsets,
            modifier = Modifier.imePadding(),
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = {
                        Column {
                            Text(community.name, fontWeight = FontWeight.Bold)
                            Text("Social", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back to communities")
                        }
                    }
                )
            },
        bottomBar = {
            if (!userAlreadyMember) {
                Button(
                    onClick = {
                        val uid = currentUserId
                        if (uid == null) {
                            Toast.makeText(context, "Please log in to join", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isJoining = true
                        viewModel.joinCommunity(
                            communityId = community.id,
                            userId = uid,
                            displayName = currentUserDisplayName,
                            onSuccess = {
                                isJoining = false
                                Toast.makeText(context, "Joined community", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = {
                                isJoining = false
                                Toast.makeText(context, "Could not join community", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isJoining,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text(if (isJoining) "Joining..." else "Join Community", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = community.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Sport", community.sport, Modifier.weight(1f))
                StatBox("Members", community.memberCount.toString(), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, tab ->
                    Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(tab) })
                }
            }

            Spacer(modifier = Modifier.height(if (selectedTabIndex == 1) 2.dp else 8.dp))

            when (selectedTabIndex) {
                0 -> FeedTab(
                    posts = posts,
                    canPost = userAlreadyMember,
                    isAdmin = isCurrentUserAdmin,
                    currentUserDisplayName = currentUserDisplayName.ifBlank { "Usuario" },
                    communityOwnerId = community.ownerId,
                    selectedPostForComments = selectedPostForComments,
                    postComments = postComments,
                    onPublishPost = { content, pinned ->
                        viewModel.createAnnouncement(
                            communityId = community.id,
                            author = currentUserDisplayName.ifBlank { "Usuario" },
                            role = if (isCurrentUserAdmin) "Admin" else "Member",
                            content = content,
                            pinned = pinned,
                            onSuccess = { Toast.makeText(context, "Post published", Toast.LENGTH_SHORT).show() },
                            onFailure = { Toast.makeText(context, "Could not publish post", Toast.LENGTH_SHORT).show() }
                        )
                    },
                    onOpenComments = { post ->
                        selectedPostForComments = post
                        viewModel.loadPostComments(community.id, post.id)
                    },
                    onCloseComments = { selectedPostForComments = null },
                    onSendComment = { text ->
                        if (selectedPostForComments != null) {
                            val uid = currentUserId
                            if (uid == null) {
                                Toast.makeText(context, "Please log in to comment", Toast.LENGTH_SHORT).show()
                                return@FeedTab
                            }
                            viewModel.addPostComment(
                                communityId = community.id,
                                postId = selectedPostForComments!!.id,
                                authorId = uid,
                                content = text,
                                authorName = currentUserDisplayName.ifBlank { "Usuario" },
                                onSuccess = {
                                    Toast.makeText(context, "Comment sent", Toast.LENGTH_SHORT).show()
                                    viewModel.loadPostComments(community.id, selectedPostForComments!!.id)
                                },
                                onFailure = { Toast.makeText(context, "Could not send comment", Toast.LENGTH_SHORT).show() }
                            )
                        }
                    }
                )

                1 -> ChannelsTab(
                    channels = channels,
                    isMember = userAlreadyMember,
                    isAdmin = isCurrentUserAdmin,
                    onCreateChannel = { showCreateChannelDialog = true },
                    onOpenChannel = { channel ->
                        selectedChannel = channel
                        viewModel.loadChannelMessages(community.id, channel.id)
                    }
                )

                2 -> MembersTab(
                    members = members,
                    isAdmin = isCurrentUserAdmin,
                    currentUserId = currentUserId,
                    onRemove = { member ->
                        viewModel.removeMember(
                            communityId = community.id,
                            memberId = member.userId.ifBlank { member.id },
                            onSuccess = { Toast.makeText(context, "Member removed", Toast.LENGTH_SHORT).show() },
                            onFailure = { Toast.makeText(context, "Could not remove member", Toast.LENGTH_SHORT).show() }
                        )
                    }
                )
            }
        }
    }
    }



    if (showCreateChannelDialog) {
        CreateChannelDialog(
            onDismiss = { showCreateChannelDialog = false },
            onCreate = { name, type ->
                viewModel.createChannel(
                    communityId = community.id,
                    name = name,
                    type = type,
                    onSuccess = {
                        showCreateChannelDialog = false
                        Toast.makeText(context, "Channel created", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(context, "Could not create channel", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

@Composable
private fun FeedTab(
    posts: List<Post>,
    canPost: Boolean,
    isAdmin: Boolean,
    currentUserDisplayName: String,
    communityOwnerId: String,
    selectedPostForComments: Post?,
    postComments: List<PostComment>,
    onPublishPost: (String, Boolean) -> Unit,
    onOpenComments: (Post) -> Unit,
    onCloseComments: () -> Unit,
    onSendComment: (String) -> Unit
) {
    var isComposing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isComposing && canPost) {
                item {
                    InlinePostComposer(
                        currentUserDisplayName = currentUserDisplayName,
                        isAdmin = isAdmin,
                        onPublish = { content, pinned ->
                            onPublishPost(content, pinned)
                            isComposing = false
                        },
                        onCancel = { isComposing = false }
                    )
                }
            }

            if (posts.isEmpty()) {
                item {
                    Text("No posts yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }

            items(posts) { post ->
                val authorIsAdmin = post.role.equals("Admin", ignoreCase = true)
                val isExpanded = post.id == selectedPostForComments?.id
                FeedPostItem(
                    post = post, 
                    isAdminPost = authorIsAdmin,
                    isExpanded = isExpanded,
                    comments = if (isExpanded) postComments else emptyList(),
                    canComment = canPost,
                    communityOwnerId = communityOwnerId,
                    onToggleComments = {
                        if (isExpanded) onCloseComments() else onOpenComments(post)
                    },
                    onSendComment = onSendComment
                )
            }
        }

        if (canPost && !isComposing) {
            Button(
                onClick = { isComposing = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Post", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChannelsTab(
    channels: List<Channel>,
    isMember: Boolean,
    isAdmin: Boolean,
    onCreateChannel: () -> Unit,
    onOpenChannel: (Channel) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            if (!isMember) {
                item {
                    Text("Join this community to access channels", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (channels.isEmpty()) {
                item {
                    Text("No channels available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(channels) { channel ->
                    androidx.compose.material3.Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenChannel(channel) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (channel.type == "privado") androidx.compose.material.icons.Icons.Default.Lock else androidx.compose.material.icons.Icons.Default.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("#${channel.name}", fontWeight = FontWeight.SemiBold)
                            }
                            Text("${channel.mensajes} msgs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (isAdmin) {
            androidx.compose.material3.Button(
                onClick = onCreateChannel,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Channel", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MembersTab(
    members: List<CommunityMember>,
    isAdmin: Boolean,
    currentUserId: String?,
    onRemove: (CommunityMember) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (members.isEmpty()) {
            item {
                Text("No members yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(members) { member ->
                val canRemove = isAdmin && member.userId != currentUserId && !member.role.equals("admin", ignoreCase = true)
                MemberRow(member = member, canRemove = canRemove, onRemove = { onRemove(member) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelRoomScreen(
    community: Community,
    channel: Channel,
    messages: List<ChannelMessage>,
    currentUserId: String?,
    currentUserDisplayName: String,
    onBack: () -> Unit,
    onLoadMessages: () -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onReact: (ChannelMessage, String) -> Unit,
    hasMoreOldMessages: Boolean,
    isLoadingOlderMessages: Boolean
) {
    var messageInput by remember { mutableStateOf("") }
    var reactionTarget by remember { mutableStateOf<ChannelMessage?>(null) }
    val listState = rememberLazyListState()

    BackHandler {
        onBack()
    }

    LaunchedEffect(channel.id) {
        onLoadMessages()
    }

    LaunchedEffect(listState, hasMoreOldMessages, isLoadingOlderMessages) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index <= 2 && hasMoreOldMessages && !isLoadingOlderMessages) {
                    onLoadOlder()
                }
            }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("#${channel.name}", fontWeight = FontWeight.Bold)
                        Text(community.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Write a message...") },
                        maxLines = 5,
                        enabled = currentUserId != null,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (messageInput.isNotBlank()) {
                                        onSend(messageInput.trim())
                                        messageInput = ""
                                    }
                                },
                                enabled = currentUserId != null && messageInput.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = if (messageInput.isNotBlank()) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text("No messages yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(messages) { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(msg.id) {
                                detectTapGestures(
                                    onLongPress = {
                                        reactionTarget = msg
                                    }
                                )
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = msg.authorName.ifBlank { currentUserDisplayName }.take(1).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = msg.authorName.ifBlank { currentUserDisplayName },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val ts = formatSmartTimestamp(msg.createdAt)
                                if (ts.isNotBlank()) {
                                    Text(
                                        text = "  •  $ts",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = msg.content,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            if (msg.reactions.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    msg.reactions.entries.sortedBy { it.key }.forEach { reaction ->
                                        val count = reaction.value
                                        if (count > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "${reaction.key} ${count}",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (reactionTarget != null) {
        ReactionPickerDialog(
            onDismiss = { reactionTarget = null },
            onPick = { emoji ->
                val target = reactionTarget
                if (target != null) {
                    onReact(target, emoji)
                }
                reactionTarget = null
            }
        )
    }
}

@Composable
private fun MemberRow(member: CommunityMember, canRemove: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.displayName.take(1).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(member.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(member.role, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove member", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FeedPostItem(
    post: Post, 
    isAdminPost: Boolean, 
    isExpanded: Boolean,
    comments: List<PostComment>,
    canComment: Boolean,
    communityOwnerId: String,
    onToggleComments: () -> Unit,
    onSendComment: (String) -> Unit
) {
    val bgColor = if (post.pinned) MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
    var input by remember { mutableStateOf("") }
    
    val focusRequester = remember { FocusRequester() }
    var shouldFocus by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded, shouldFocus) {
        if (isExpanded && shouldFocus) {
            focusRequester.requestFocus()
            shouldFocus = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.author, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (isAdminPost) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(com.uniandes.sport.ui.theme.CrownIcon, contentDescription = "Admin", tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(post.role, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val postTime = formatSmartTimestamp(post.createdAt, post.time)
                if (postTime.isNotBlank()) {
                    Text(
                        text = "  •  $postTime",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (post.pinned) {
                    Icon(androidx.compose.material.icons.Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(post.content, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.clickable(onClick = onToggleComments).padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    val commentText = if (isExpanded) "Hide comments" else "View comments"
                    Text(commentText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (canComment) {
                    Row(
                        modifier = Modifier.clickable {
                            if (!isExpanded) onToggleComments()
                            shouldFocus = true
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reply", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (comments.isEmpty()) {
                        Text("No comments yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    } else {
                        comments.forEach { c ->
                            val isCommentAdmin = c.authorId == communityOwnerId
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), shape = RoundedCornerShape(8.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.authorName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                        if (isCommentAdmin) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(com.uniandes.sport.ui.theme.CrownIcon, contentDescription = "Admin", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                    Text(c.content, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Write a comment...") },
                        enabled = canComment,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).padding(top = 6.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (input.isNotBlank()) {
                                        onSendComment(input.trim())
                                        input = ""
                                    }
                                },
                                enabled = canComment && input.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InlinePostComposer(
    currentUserDisplayName: String,
    isAdmin: Boolean,
    onPublish: (String, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentUserDisplayName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (isAdmin) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(com.uniandes.sport.ui.theme.CrownIcon, contentDescription = "Admin", tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isAdmin) "Admin" else "Member", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("What's on your mind?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                trailingIcon = {
                    if (content.isNotBlank()) {
                        IconButton(
                            onClick = { onPublish(content, pinned) },
                            enabled = content.isNotBlank()
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Send,
                                contentDescription = "Publish post",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (isAdmin) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pinned = !pinned }) {
                        Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                        Text("Pin post", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CreateChannelDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("publico") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Channel name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChipLike(label = "Public", selected = type == "publico") { type = "publico" }
                    AssistChipLike(label = "Private", selected = type == "privado") { type = "privado" }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, type) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AssistChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatSmartTimestamp(timestamp: Long, fallback: String = ""): String {
    if (timestamp <= 0L) return fallback

    val tz = TimeZone.getTimeZone("GMT-05:00")
    val now = Calendar.getInstance(tz)
    val msg = Calendar.getInstance(tz).apply { timeInMillis = timestamp }

    val diffMillis = (now.timeInMillis - timestamp).coerceAtLeast(0L)
    val diffMinutes = diffMillis / 60000L

    val nowYear = now.get(Calendar.YEAR)
    val nowDay = now.get(Calendar.DAY_OF_YEAR)
    val msgYear = msg.get(Calendar.YEAR)
    val msgDay = msg.get(Calendar.DAY_OF_YEAR)

    if (nowYear == msgYear && nowDay == msgDay) {
        return when {
            diffMinutes < 1L -> "Ahora"
            diffMinutes < 60L -> "$diffMinutes min"
            else -> "${diffMinutes / 60L} h"
        }
    }

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (
        msg.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        msg.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    ) {
        val hour = SimpleDateFormat("h:mm a", Locale("es", "CO")).apply { timeZone = tz }.format(msg.time)
        return "Ayer $hour"
    }

    return SimpleDateFormat("dd MMM h:mm a", Locale("es", "CO")).apply { timeZone = tz }.format(msg.time)
}

@Composable
private fun ReactionPickerDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val emojis = listOf("👍", "🔥", "😂", "👏", "💯", "❤️")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("React to message") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                emojis.forEach { emoji ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onPick(emoji) }
                    ) {
                        Text(
                            text = emoji,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            fontSize = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

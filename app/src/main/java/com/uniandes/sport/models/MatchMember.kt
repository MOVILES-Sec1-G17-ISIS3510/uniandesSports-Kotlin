package com.uniandes.sport.models

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.DocumentId

data class MatchMember(
    @get:PropertyName("userId") @set:PropertyName("userId") var userId: String = "",
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String = "",
    @get:PropertyName("joinedAt") @set:PropertyName("joinedAt") var joinedAt: Long = 0,
    @get:PropertyName("role") @set:PropertyName("role") var role: String = "member"
)

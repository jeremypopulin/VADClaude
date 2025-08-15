package com.example.visualduress.model

import kotlinx.serialization.Serializable

@Serializable
data class EventLogEntry(
    val timestamp: Long,
    val message: String
)

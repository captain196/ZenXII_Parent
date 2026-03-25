package com.schoolsync.parent.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("userId")
    val userId: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("deviceId")
    val deviceId: String
)

data class RefreshRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

data class LogoutRequest(
    @SerializedName("refreshToken")
    val refreshToken: String,

    @SerializedName("deviceId")
    val deviceId: String? = null
)

data class ChangePasswordRequest(
    @SerializedName("currentPassword")
    val currentPassword: String,

    @SerializedName("newPassword")
    val newPassword: String
)

data class RegisterFcmRequest(
    @SerializedName("fcmToken")
    val fcmToken: String,

    @SerializedName("deviceId")
    val deviceId: String
)

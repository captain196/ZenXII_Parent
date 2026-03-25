package com.schoolsync.parent.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("accessToken")
    val accessToken: String,

    @SerializedName("refreshToken")
    val refreshToken: String,

    @SerializedName("firebaseToken")
    val firebaseToken: String,

    @SerializedName("user")
    val user: UserDto
)

data class RefreshResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("accessToken")
    val accessToken: String,

    @SerializedName("refreshToken")
    val refreshToken: String,

    @SerializedName("firebaseToken")
    val firebaseToken: String
)

data class LogoutResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null
)

data class ChangePasswordResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null
)

data class RegisterFcmResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null
)

data class UserDto(
    @SerializedName("userId")
    val userId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("phone")
    val phone: String? = null,

    @SerializedName("role")
    val role: String,

    @SerializedName("schoolId")
    val schoolId: String,

    @SerializedName("schoolDisplayName")
    val schoolDisplayName: String? = null,

    @SerializedName("profilePic")
    val profilePic: String? = null,

    @SerializedName("className")
    val className: String? = null,

    @SerializedName("section")
    val section: String? = null,

    @SerializedName("rollNo")
    val rollNo: String? = null,

    @SerializedName("fatherName")
    val fatherName: String? = null,

    @SerializedName("motherName")
    val motherName: String? = null,

    @SerializedName("dob")
    val dob: String? = null,

    @SerializedName("gender")
    val gender: String? = null,

    @SerializedName("admissionDate")
    val admissionDate: String? = null,

    @SerializedName("parentDbKey")
    val parentDbKey: String? = null,

    @SerializedName("session")
    val session: String? = null
)

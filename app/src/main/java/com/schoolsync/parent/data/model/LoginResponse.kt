package com.schoolsync.parent.data.model

import com.google.gson.annotations.SerializedName

/**
 * User data transfer object — used by TokenManager.saveUser() for backward
 * compatibility. New login flow constructs User objects directly from Firebase RTDB data.
 */
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

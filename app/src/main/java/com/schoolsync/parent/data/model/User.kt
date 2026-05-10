package com.schoolsync.parent.data.model

/**
 * Local user profile model used throughout the app.
 * Constructed from [UserDto] after login or from DataStore on app restart.
 */
data class User(
    val userId: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val schoolId: String,
    val schoolDisplayName: String,
    val profilePic: String,
    val className: String,
    val section: String,
    val rollNo: String,
    val fatherName: String,
    val motherName: String,
    val dob: String,
    val gender: String,
    val admissionDate: String,
    val parentDbKey: String,
    val session: String,
    val schoolCode: String = "",  // Firebase school key resolved from Indexes/School_codes
    /** Phase A — set during login from `students/{id}.mustChangePassword`.
     *  Drives the force-change-password gate after a successful login. */
    val mustChangePassword: Boolean = false,
    /** B2 — set during login from `students/{id}.status`. Default "Active"
     *  for back-compat with any cached User from before this field existed.
     *  AuthRepository.login() blocks completion when the value is anything
     *  other than "Active" (TC, Inactive, Deleted, etc.) so a withdrawn
     *  student's parent can't keep using the app on stale credentials. */
    val status: String = "Active",
) {
    companion object {
        fun fromDto(dto: UserDto, schoolCode: String = ""): User {
            return User(
                userId = dto.userId,
                name = dto.name,
                email = dto.email ?: "",
                phone = dto.phone ?: "",
                role = dto.role,
                schoolId = dto.schoolId,
                schoolDisplayName = dto.schoolDisplayName ?: "",
                profilePic = dto.profilePic ?: "",
                className = dto.className ?: "",
                section = dto.section ?: "",
                rollNo = dto.rollNo ?: "",
                fatherName = dto.fatherName ?: "",
                motherName = dto.motherName ?: "",
                dob = dto.dob ?: "",
                gender = dto.gender ?: "",
                admissionDate = dto.admissionDate ?: "",
                parentDbKey = dto.parentDbKey ?: "",
                session = dto.session ?: "",
                schoolCode = schoolCode,
                mustChangePassword = false,
            )
        }

        fun empty(): User = User(
            userId = "",
            name = "",
            email = "",
            phone = "",
            role = "",
            schoolId = "",
            schoolDisplayName = "",
            profilePic = "",
            className = "",
            section = "",
            rollNo = "",
            fatherName = "",
            motherName = "",
            dob = "",
            gender = "",
            admissionDate = "",
            parentDbKey = "",
            session = "",
            schoolCode = "",
            mustChangePassword = false,
        )
    }

    val isLoggedIn: Boolean get() = userId.isNotBlank()
}

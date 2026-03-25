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
    val schoolCode: String = ""  // Firebase school key resolved from Indexes/School_codes
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
                schoolCode = schoolCode
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
            schoolCode = ""
        )
    }

    val isLoggedIn: Boolean get() = userId.isNotBlank()
}

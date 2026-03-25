package com.schoolsync.parent.data.remote

import com.schoolsync.parent.data.model.ChangePasswordRequest
import com.schoolsync.parent.data.model.ChangePasswordResponse
import com.schoolsync.parent.data.model.LoginRequest
import com.schoolsync.parent.data.model.LoginResponse
import com.schoolsync.parent.data.model.LogoutRequest
import com.schoolsync.parent.data.model.LogoutResponse
import com.schoolsync.parent.data.model.RefreshRequest
import com.schoolsync.parent.data.model.RefreshResponse
import com.schoolsync.parent.data.model.RegisterFcmRequest
import com.schoolsync.parent.data.model.RegisterFcmResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<RefreshResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<LogoutResponse>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ChangePasswordResponse>

    @POST("api/auth/register-fcm")
    suspend fun registerFcm(@Body request: RegisterFcmRequest): Response<RegisterFcmResponse>
}

package com.schoolsync.parent.data.remote

import com.google.gson.Gson
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.RefreshRequest
import com.schoolsync.parent.data.model.RefreshResponse
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that:
 * 1. Adds Bearer token to all authenticated requests
 * 2. On 401, attempts token refresh and retries the original request once
 * 3. If refresh fails, clears tokens (forces re-login)
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    private val gson = Gson()

    // Paths that don't require auth
    private val publicPaths = setOf(
        "/api/auth/login",
        "/api/auth/refresh"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for public endpoints
        if (isPublicPath(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Get current access token
        val accessToken = runBlocking {
            tokenManager.accessToken.firstOrNull()
        }

        // If no token, proceed without auth (will likely get 401)
        if (accessToken.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        // Add Bearer token
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

        val response = chain.proceed(authenticatedRequest)

        // If not 401, return response as-is
        if (response.code != 401) {
            return response
        }

        // 401 received — attempt token refresh
        response.close()

        synchronized(this) {
            // Double-check: another thread might have already refreshed
            val currentToken = runBlocking { tokenManager.accessToken.firstOrNull() }

            if (currentToken != accessToken) {
                // Token was already refreshed by another thread — retry with new token
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
                return chain.proceed(retryRequest)
            }

            // Actually refresh the token
            val newAccessToken = runBlocking { attemptTokenRefresh() }

            if (newAccessToken != null) {
                // Retry original request with new token
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                return chain.proceed(retryRequest)
            } else {
                // Refresh failed — clear all tokens (force re-login)
                runBlocking { tokenManager.clearAll() }
                // Return a 401 so the UI layer can handle navigation to login
                return chain.proceed(authenticatedRequest)
            }
        }
    }

    /**
     * Attempt to refresh the access token using the stored refresh token.
     * Uses a separate OkHttpClient to avoid interceptor recursion.
     * Returns the new access token on success, null on failure.
     */
    private suspend fun attemptTokenRefresh(): String? {
        val refreshToken = tokenManager.refreshToken.firstOrNull() ?: return null
        val baseUrl = tokenManager.baseUrl

        return try {
            val refreshBody = gson.toJson(RefreshRequest(refreshToken))
            val requestBody = refreshBody.toRequestBody("application/json".toMediaType())

            val refreshRequest = Request.Builder()
                .url("${baseUrl}api/auth/refresh")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()

            // Use a plain OkHttpClient without interceptors to avoid recursion
            val plainClient = OkHttpClient.Builder().build()
            val response = plainClient.newCall(refreshRequest).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val refreshResponse = gson.fromJson(responseBody, RefreshResponse::class.java)

                if (refreshResponse.success) {
                    // Store new tokens
                    tokenManager.saveTokens(
                        accessToken = refreshResponse.accessToken,
                        refreshToken = refreshResponse.refreshToken,
                        firebaseToken = refreshResponse.firebaseToken
                    )
                    refreshResponse.accessToken
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isPublicPath(request: Request): Boolean {
        val path = request.url.encodedPath
        return publicPaths.any { path.contains(it) }
    }
}

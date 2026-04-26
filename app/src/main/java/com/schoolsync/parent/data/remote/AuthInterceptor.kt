package com.schoolsync.parent.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor — simplified after removing Node.js API auth endpoints.
 * All auth is now handled directly by Firebase Auth SDK.
 * This interceptor is kept as a pass-through for any future REST endpoints.
 */
@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}

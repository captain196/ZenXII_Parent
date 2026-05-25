package com.schoolsync.parent.di

import android.content.Context
import com.schoolsync.parent.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.schoolsync.parent.data.firebase.FirebaseAuthManager
import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.remote.ApiService
import com.schoolsync.parent.data.remote.AuthInterceptor
import com.schoolsync.parent.data.remote.FeesApi
import com.schoolsync.parent.data.repository.AttendanceRepository
import com.schoolsync.parent.data.repository.AuthRepository
import com.schoolsync.parent.data.repository.DataRepository
import com.schoolsync.parent.data.repository.EventRepository
import com.schoolsync.parent.data.repository.HomeworkRepository
import com.schoolsync.parent.data.repository.MessageRepository
import com.schoolsync.parent.data.repository.NoticeRepository
import com.schoolsync.parent.data.repository.RedFlagRepository
import com.schoolsync.parent.data.repository.StudentRepository
import com.schoolsync.parent.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.ChatRtdbRepository
import com.schoolsync.parent.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.SchoolFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.SectionFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.TransportFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.CampusLifeFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.HRFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.InventoryFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.LeaveFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.MyTeachersFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.LibraryFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.AnalyticsFirestoreRepository
import com.schoolsync.parent.util.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Core Infrastructure ──────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context
    ): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(): AuthInterceptor {
        return AuthInterceptor()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: com.schoolsync.parent.data.remote.BaseUrlInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // BaseUrlInterceptor must run BEFORE AuthInterceptor so the
            // bearer header is attached to the rewritten host (and so
            // logging shows the actual host hit, not the BuildConfig one).
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            // Payment verify can take 30–60s because the server does a
            // round-trip to Razorpay's API for signature/amount checks
            // before writing the full receipt + allocation.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(
        retrofit: Retrofit
    ): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideFeesApi(
        retrofit: Retrofit
    ): FeesApi {
        return retrofit.create(FeesApi::class.java)
    }

    // ── Firebase ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideFirebaseService(): FirebaseService {
        return FirebaseService()
    }

    @Provides
    @Singleton
    fun provideFirebaseAuthManager(): FirebaseAuthManager {
        return FirebaseAuthManager()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirestoreService(
        firestore: FirebaseFirestore
    ): FirestoreService {
        return FirestoreService(firestore)
    }

    // ── Repositories ─────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAuthRepository(
        tokenManager: TokenManager,
        firebaseAuthManager: FirebaseAuthManager,
        firebaseService: FirebaseService,
        firestoreService: FirestoreService
    ): AuthRepository {
        return AuthRepository(tokenManager, firebaseAuthManager, firebaseService, firestoreService)
    }

    @Provides
    @Singleton
    fun provideDataRepository(
        firestoreService: FirestoreService,
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): DataRepository = DataRepository(firestoreService, firebaseService, tokenManager)

    @Provides
    @Singleton
    fun provideStudentRepository(
        firebaseService: FirebaseService,
        firestoreService: FirestoreService,
        feeFirestoreRepository: FeeFirestoreRepository,
        tokenManager: TokenManager
    ): StudentRepository {
        return StudentRepository(firebaseService, firestoreService, feeFirestoreRepository, tokenManager)
    }

    @Provides
    @Singleton
    fun provideMessageRepository(
        firebaseService: FirebaseService,
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): MessageRepository {
        return MessageRepository(firebaseService, firestoreService, tokenManager)
    }

    // RedFlagRepository: resolved via its @Inject constructor (FirestoreService,
    // TokenManager, FirebaseAuth) — no manual @Provides needed.

    // Legacy RTDB-backed StoryRepository removed —
    // see StoryFirestoreRepository for the canonical, real-time
    // single-store replacement.

    // GalleryRepository (RTDB) removed in Phase C-2 (2026-04-26).
    // GalleryFirestoreRepository is now the canonical reader; Hilt resolves
    // it via its @Inject constructor — no explicit @Provides needed.

    @Provides
    @Singleton
    fun provideNoticeRepository(
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): NoticeRepository {
        return NoticeRepository(firebaseService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideEventRepository(
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): EventRepository {
        return EventRepository(firebaseService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideHomeworkRepository(
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): HomeworkRepository {
        return HomeworkRepository(firebaseService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideAttendanceRepository(
        firestoreRepo: AttendanceFirestoreRepository,
        tokenManager: TokenManager
    ): AttendanceRepository {
        return AttendanceRepository(firestoreRepo, tokenManager)
    }

    // ── Firestore Repositories ────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideStudentFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): StudentFirestoreRepository {
        return StudentFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideSectionFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): SectionFirestoreRepository {
        return SectionFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideAttendanceFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): AttendanceFirestoreRepository {
        return AttendanceFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideHomeworkFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): HomeworkFirestoreRepository {
        return HomeworkFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideExamFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): ExamFirestoreRepository {
        return ExamFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideFeeFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): FeeFirestoreRepository {
        return FeeFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideCommunicationFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): CommunicationFirestoreRepository {
        return CommunicationFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideChatRtdbRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): ChatRtdbRepository {
        // Phase 5 — name kept for DI back-compat, backing store is now
        // Firestore (notifBadges / presence collections).
        return ChatRtdbRepository(firestoreService, tokenManager)
    }

    // ── Phase 7–12 Firestore Repositories ────────────────────────────────

    @Provides
    @Singleton
    fun provideTransportFirestoreRepository(
        firestoreService: FirestoreService,
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): TransportFirestoreRepository {
        return TransportFirestoreRepository(firestoreService, firebaseService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideCampusLifeFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): CampusLifeFirestoreRepository {
        return CampusLifeFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideHRFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): HRFirestoreRepository {
        return HRFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideInventoryFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): InventoryFirestoreRepository {
        return InventoryFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideLibraryFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): LibraryFirestoreRepository {
        return LibraryFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideLeaveFirestoreRepository(
        firestoreService: FirestoreService,
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): LeaveFirestoreRepository {
        return LeaveFirestoreRepository(firestoreService, firebaseService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideAnalyticsFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): AnalyticsFirestoreRepository {
        return AnalyticsFirestoreRepository(firestoreService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideMyTeachersFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): MyTeachersFirestoreRepository =
        MyTeachersFirestoreRepository(firestoreService, tokenManager)

    // ── Utility ───────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context
    ): NetworkMonitor {
        return NetworkMonitor(context)
    }
}

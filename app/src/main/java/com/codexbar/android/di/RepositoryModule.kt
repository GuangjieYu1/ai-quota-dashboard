package com.codexbar.android.di

import com.codexbar.android.core.data.ChatGPTPlusRepositoryImpl
import com.codexbar.android.core.data.ClaudeRepositoryImpl
import com.codexbar.android.core.data.CodexFeelolRepositoryImpl
import com.codexbar.android.core.data.CodexRepositoryImpl
import com.codexbar.android.core.data.DeepSeekRepositoryImpl
import com.codexbar.android.core.data.GeminiRepositoryImpl
import com.codexbar.android.core.data.MiMoRepositoryImpl
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.claude.ClaudeApiService
import com.codexbar.android.core.network.claude.ClaudeTokenRefreshService
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.codexfeelol.CodexFeelolApiService
import com.codexbar.android.core.network.deepseek.DeepSeekApiService
import com.codexbar.android.core.network.gemini.GeminiApiService
import com.codexbar.android.core.network.gemini.GeminiTokenRefreshService
import com.codexbar.android.core.network.mimo.MiMoApiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexFeelolRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeepSeekRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatGPTPlusRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MiMoRepository

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    @ClaudeRepository
    fun provideClaudeRepository(
        apiService: ClaudeApiService,
        tokenRefreshService: ClaudeTokenRefreshService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = ClaudeRepositoryImpl(apiService, tokenRefreshService, prefsManager)

    @Provides
    @Singleton
    @CodexRepository
    fun provideCodexRepository(
        apiService: CodexApiService,
        tokenRefreshService: CodexTokenRefreshService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = CodexRepositoryImpl(apiService, tokenRefreshService, prefsManager)

    @Provides
    @Singleton
    @CodexFeelolRepository
    fun provideCodexFeelolRepository(
        apiService: CodexFeelolApiService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = CodexFeelolRepositoryImpl(apiService, prefsManager)

    @Provides
    @Singleton
    @GeminiRepository
    fun provideGeminiRepository(
        apiService: GeminiApiService,
        tokenRefreshService: GeminiTokenRefreshService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = GeminiRepositoryImpl(apiService, tokenRefreshService, prefsManager)

    @Provides
    @Singleton
    @DeepSeekRepository
    fun provideDeepSeekRepository(
        apiService: DeepSeekApiService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = DeepSeekRepositoryImpl(apiService, prefsManager)

    @Provides
    @Singleton
    @ChatGPTPlusRepository
    fun provideChatGPTPlusRepository(
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = ChatGPTPlusRepositoryImpl(prefsManager)

    @Provides
    @Singleton
    @MiMoRepository
    fun provideMiMoRepository(
        apiService: MiMoApiService,
        prefsManager: EncryptedPrefsManager,
        @MiMoClient client: OkHttpClient,
        json: Json
    ): QuotaRepository = MiMoRepositoryImpl(apiService, prefsManager, client, json)
}

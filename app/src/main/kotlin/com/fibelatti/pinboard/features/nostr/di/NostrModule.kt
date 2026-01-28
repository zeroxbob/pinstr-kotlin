package com.fibelatti.pinboard.features.nostr.di

import android.content.Context
import com.fibelatti.pinboard.BuildConfig
import com.fibelatti.pinboard.features.nostr.data.NostrClient
import com.fibelatti.pinboard.features.nostr.data.NostrEventMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NostrOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NostrModule {

    @Provides
    @Singleton
    @NostrOkHttpClient
    fun provideNostrOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            // Add HTTP logging interceptor
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Timber.tag("NostrOkHttp").d(message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)

            // Add Chucker interceptor
            try {
                val chuckerClass = Class.forName("com.chuckerteam.chucker.api.ChuckerInterceptor\$Builder")
                val constructor = chuckerClass.getConstructor(Context::class.java)
                val chuckerBuilder = constructor.newInstance(context)
                val buildMethod = chuckerClass.getMethod("build")
                val chuckerInterceptor = buildMethod.invoke(chuckerBuilder) as okhttp3.Interceptor
                builder.addInterceptor(chuckerInterceptor)
            } catch (e: Exception) {
                Timber.w("Chucker not available: ${e.message}")
            }
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideNostrClient(
        @NostrOkHttpClient okHttpClient: OkHttpClient,
    ): NostrClient = NostrClient(okHttpClient)

    @Provides
    fun provideNostrEventMapper(): NostrEventMapper = NostrEventMapper()
}

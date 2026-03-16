package com.swisscom.health.des.cdr.client.config

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import com.mayakapps.kache.ObjectKache
import com.swisscom.health.des.cdr.client.common.Constants.EMPTY_STRING
import com.swisscom.health.des.cdr.client.common.DTOs
import com.swisscom.health.des.cdr.client.config.OAuth2AuthNService.AuthNResponse
import com.swisscom.health.des.cdr.client.config.ProxyConfiguration.Disabled
import com.swisscom.health.des.cdr.client.config.ProxyConfiguration.Enabled
import com.swisscom.health.des.cdr.client.handler.ConfigValidationService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.time.delay
import okhttp3.OkHttpClient
import okhttp3.Response
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.support.RetryTemplate
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}

/**
 * A Spring configuration class for creating and configuring beans used by the CDR client.
 */
@Suppress("TooManyFunctions")
@Configuration
internal class CdrClientContext {

    /**
     * Creates a proxy configuration bean that will be used by both OkHttp and OAuth2 clients.
     */
    @Bean
    fun proxyConfiguration(config: CdrClientConfig, configValidationService: ConfigValidationService): ProxyConfiguration = when {
        config.proxyConfig == null || config.proxyConfig.url.value == EMPTY_STRING -> Disabled.also {
            logger.debug { "No proxy URL configured, proceeding without HTTP proxy" }
        }

        configValidationService.validateProxySetting(config.proxyConfig.url.value) is DTOs.ValidationResult.Failure ->
            Disabled.also { logger.error { "Invalid proxy URL '${config.proxyConfig.url.value}': must start with http:// or https://" } }

        else -> runCatching {
            val proxyUri = URI(config.proxyConfig.url.value)

            // Validate that we have a host and port
            if (proxyUri.host == null) {
                logger.error { "Invalid proxy URL '${config.proxyConfig.url.value}': missing host" }
                Disabled
            } else {
                // Use appropriate default port based on scheme
                val port = getProxyPort(proxyUri)

                // Extract credentials from config
                val username = config.proxyConfig.username.value.takeIf { it.isNotBlank() }
                val password = config.proxyConfig.password.value.takeIf { it.isNotBlank() }

                Enabled(
                    proxy = Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress(proxyUri.host, port)
                    ),
                    username = username,
                    password = password
                ).also {
                    if (username != null) {
                        logger.info { "Configured HTTP proxy with authentication: '${config.proxyConfig.url.value}' (username: $username)" }
                    } else {
                        logger.info { "Configured HTTP proxy: '${config.proxyConfig.url.value}'" }
                    }
                }
            }
        }.getOrElse { e ->
            when (e) {
                is URISyntaxException -> logger.error(e) { "Invalid proxy URL syntax: '${config.proxyConfig.url.value}'" }
                is IllegalArgumentException -> logger.error(e) { "Failed to configure proxy with URL '${config.proxyConfig.url.value}'" }
                else -> logger.error(e) { "Unexpected error configuring proxy with URL '${config.proxyConfig.url.value}'" }
            }
            Disabled
        }
    }

    /**
     * Configures system-wide proxy authenticator for Nimbus JWT HTTP client.
     * This is required because Nimbus uses Java's built-in HttpURLConnection which relies on
     * the system-wide Authenticator for proxy authentication.
     */
    @Bean
    fun proxyAuthenticator(proxyConfiguration: ProxyConfiguration): Authenticator? {
        return when (proxyConfiguration) {
            is Enabled -> {
                if (proxyConfiguration.username != null && proxyConfiguration.password != null) {
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication? {
                            return if (requestorType == RequestorType.PROXY) {
                                logger.debug { "Providing proxy authentication for '${requestingHost}:${requestingPort}'" }
                                PasswordAuthentication(
                                    proxyConfiguration.username,
                                    proxyConfiguration.password.toCharArray()
                                )
                            } else {
                                null
                            }
                        }
                    }.also {
                        Authenticator.setDefault(it)
                        logger.info { "Configured system-wide proxy authenticator for user: '${proxyConfiguration.username}'" }
                    }
                } else {
                    logger.debug { "No proxy credentials configured, skipping proxy authenticator setup" }
                    null
                }
            }
            is Disabled -> {
                logger.debug { "Proxy disabled, skipping proxy authenticator setup" }
                null
            }
        }
    }

    private fun getProxyPort(proxyUri: URI): Int = when {
        proxyUri.port != UNDEFINED_PORT -> proxyUri.port
        else -> when (proxyUri.scheme?.lowercase()) {
            "https" -> DEFAULT_HTTPS_PORT
            "http" -> DEFAULT_HTTP_PORT
            // should never happen, as the calling function already validated the scheme, but we need to handle it to satisfy the compiler
            else -> UNDEFINED_PORT
        }
    }

    /**
     * Creates and returns an instance of the OkHttpClient.
     *
     * @param builder The OkHttpClient.Builder used to build the client.
     * @return The fully constructed OkHttpClient.
     */
    @Bean
    fun okHttpClient(
        builder: OkHttpClient.Builder,
        oAuth2AuthNService: OAuth2AuthNService,
        @Value($$"${client.connection-timeout-ms}") timeout: Long,
        @Value($$"${client.read-timeout-ms}") readTimeout: Long,
        proxyConfiguration: ProxyConfiguration
    ): OkHttpClient =
        builder
            .connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(readTimeout, TimeUnit.MILLISECONDS)
            .apply {
                // Configure proxy if enabled
                if (proxyConfiguration is Enabled) {
                    proxy(proxyConfiguration.proxy)
                    logger.info { "Configured OkHttp with HTTP proxy: '${proxyConfiguration.proxy}'" }

                    // Add proxy authenticator if credentials are provided
                    if (proxyConfiguration.username != null && proxyConfiguration.password != null) {
                        proxyAuthenticator { _, response ->
                            val credential = okhttp3.Credentials.basic(
                                proxyConfiguration.username,
                                proxyConfiguration.password
                            )
                            response.request.newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        }
                        logger.info { "Configured OkHttp with proxy authentication for user: '${proxyConfiguration.username}'" }
                    }
                }
            }
            .addInterceptor { chain ->
                oAuth2AuthNService.getAccessToken()
                    .let { authNResponse ->
                        when (authNResponse) {
                            is AuthNResponse.Success -> {
                                chain
                                    .request()
                                    .newBuilder()
                                    .run {
                                        header("Authorization", "Bearer ${authNResponse.response.tokens.accessToken.value}")
                                        build()
                                    }.let { authenticatedRequest ->
                                        chain.proceed(authenticatedRequest)
                                    }
                            }

                            else -> chain.proceed(chain.request()) // unauthenticated call; will probably fail with 401/403
                                .also { _ ->
                                    logger.warn { "Authentication failed, proceeding unauthenticated; authentication response: '$authNResponse'" }
                                }
                        }
                    }
            }
            .addInterceptor { chain ->
                val response: Response = chain.proceed(chain.request())

                @Suppress("MagicNumber")
                if (response.code in 500..599) {
                    throw HttpServerErrorException(
                        message = "Received error status code '${response.code}'.",
                        statusCode = response.code,
                        responseBody = response.body.string()
                    )
                }

                response
            }
            .build()

    /**
     * Creates and returns an instance of the OkHttpClient.Builder if one does not already exist.
     *
     * @return The OkHttpClient.Builder instance.
     */
    @Bean
    @ConditionalOnMissingBean
    fun okHttpClientBuilder(): OkHttpClient.Builder? {
        return OkHttpClient.Builder()
    }

    /**
     * Creates a coroutine dispatcher for blocking I/O operations with limited parallelism.
     *
     * Note: As we use the non-blocking `delay()` to wait for the file-size-growth test and
     * to wait in case a re-try of an upload is required, a lot more files than the configured
     * thread pool size can be enrolled in the upload process at the same time. Only the
     * blocking i/o operations are limited to the configured thread pool size.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Bean(name = ["limitedParallelismCdrUploadsDispatcher"])
    fun limitedParallelCdrUploadsDispatcher(config: CdrClientConfig): CoroutineDispatcher {
        return Dispatchers.IO.limitedParallelism(config.pushThreadPoolSize)
    }

    /**
     * Creates a coroutine dispatcher for blocking I/O operations with limited parallelism.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Bean(name = ["limitedParallelismCdrDownloadsDispatcher"])
    fun limitedParallelCdrDownloadsDispatcher(config: CdrClientConfig): CoroutineDispatcher {
        return Dispatchers.IO.limitedParallelism(config.pullThreadPoolSize)
    }

    /**
     * Creates a cache to store fully qualified file names of files that are currently being processed
     * to avoid a race condition between processing files by the polling and event trigger processes.
     */
    @Bean
    fun processingInProgressCache(config: CdrClientConfig): ObjectKache<String, Path> =
        InMemoryKache(maxSize = config.filesInProgressCacheSize.toBytes())
        {
            strategy = KacheStrategy.LRU
            onEntryRemoved = { evicted: Boolean, key: String, _: Path, _: Path? ->
                if (evicted) {
                    logger.warn {
                        "The file object with key '$key' has been evicted from the processing cache because the capacity limit of the cache " +
                                "has been reached; this indicates a very large number of files in the source directories that cannot be processed. " +
                                "Please investigate. "
                    }
                }
            }
        }

    /**
     * Creates and returns a spring retry-template that retries on IOExceptions up to three times before bailing out.
     */
    @Bean(name = ["retryIoAndServerErrors"])
    @Suppress("MagicNumber")
    fun retryIOExceptionsAndServerErrorsTemplate(
        @Value($$"${client.retry-template.retries}")
        retries: Int,
        @Value($$"${client.retry-template.initial-delay}")
        initialDelay: Duration,
        @Value($$"${client.retry-template.multiplier}")
        multiplier: Double,
        @Value($$"${client.retry-template.max-delay}")
        maxDelay: Duration
    ): RetryTemplate = RetryTemplate.builder()
        .maxAttempts(retries) // 1 initial attempt + retries
        .exponentialBackoff(initialDelay, multiplier, maxDelay, true)
        .retryOn(IOException::class.java)
        .retryOn(HttpServerErrorException::class.java)
        .traversingCauses()
        .build()

    @Bean
    @ConditionalOnProperty(prefix = "client", name = ["file-busy-test-strategy"], havingValue = "FILE_SIZE_CHANGED")
    fun fileSizeChanged(@Value($$"${client.file-size-busy-test-interval:PT0.25S}") testInterval: Duration): FileBusyTester =
        require(!testInterval.isZero && !testInterval.isNegative).run {
            FileBusyTester.FileSizeChanged(testInterval)
                .also { logger.info { "Using file-busy-test strategy 'FILE_SIZE_CHANGED', sampling file at interval '$testInterval'" } }
        }

    @Bean
    @ConditionalOnProperty(prefix = "client", name = ["file-busy-test-strategy"], havingValue = "ALWAYS_BUSY")
    fun alwaysBusyFileTester(): FileBusyTester = FileBusyTester.AlwaysBusy.also { logger.info { "Using file-busy-test strategy 'ALWAYS_BUSY'" } }

    @Bean
    @ConditionalOnProperty(prefix = "client", name = ["file-busy-test-strategy"], havingValue = "NEVER_BUSY")
    fun neverBusyFileTester(): FileBusyTester = FileBusyTester.NeverBusy.also { logger.info { "Using file-busy-test strategy 'NEVER_BUSY'" } }

    @Bean
    @ConditionalOnMissingBean(FileBusyTester::class)
    fun defaultBusyFileTester(): FileBusyTester =
        FileBusyTester.NeverBusy.also { logger.warn { "No file-busy-test strategy defined, defaulting to 'NEVER_BUSY'" } }


    companion object {
        private const val UNDEFINED_PORT = -1
        private const val DEFAULT_HTTP_PORT = 80
        private const val DEFAULT_HTTPS_PORT = 443
    }
}


/**
 * Proxy configuration for HTTP clients.
 */
internal sealed interface ProxyConfiguration {
    object Disabled : ProxyConfiguration

    data class Enabled(
        val proxy: Proxy,
        val username: String? = null,
        val password: String? = null
    ) : ProxyConfiguration
}

internal class HttpServerErrorException(message: String, val statusCode: Int, val responseBody: String) : RuntimeException(message, null, false, false) {
    override fun toString(): String {
        return "HttpServerErrorException(statusCode='$statusCode', responseBody='$responseBody', message='${message}')"
    }
}

internal class WrongCredentialsException(message: String) : RuntimeException(message, null, false, false)

sealed interface FileBusyTester {
    suspend fun isBusy(file: Path): Boolean

    object NeverBusy : FileBusyTester {
        override suspend fun isBusy(file: Path): Boolean = false
    }

    // Only useful for testing
    object AlwaysBusy : FileBusyTester {
        override suspend fun isBusy(file: Path): Boolean = true
    }

    class FileSizeChanged(private val testInterval: Duration) : FileBusyTester {
        override suspend fun isBusy(file: Path): Boolean = runCatching {
            logger.debug { "'${file.name}' busy state check..." }
            val startSize = file.fileSize()
            delay(testInterval)
            val endSize = file.fileSize()
            (startSize != endSize).also { logger.debug { "'${file.name}' busy state: '$it'; start size: '$startSize', end size: '$endSize'" } }
        }.fold(
            onSuccess = { it },
            onFailure = { t: Throwable ->
                when (t) {
                    is IOException -> {
                        logger.warn { "Failed to determine file size for file '$file': ${t.message}" }
                        false
                    }

                    else -> throw t
                }
            }
        )
    }

}

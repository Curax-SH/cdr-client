package com.swisscom.health.des.cdr.client.config

import com.swisscom.health.des.cdr.client.handler.ConfigValidationService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.Proxy

/**
 * Unit tests for proxy configuration validation logic in CdrClientContext.
 *
 * These tests verify that the proxyConfiguration bean correctly validates and handles:
 * - Valid HTTP and HTTPS proxy URLs
 * - Invalid proxy URLs (missing http/https prefix, invalid format, etc.)
 * - Default port handling (80 for http, 443 for https)
 * - Explicit port specification
 * - Empty/blank proxy URLs
 */
internal class ProxyConfigurationValidationTest {

    private val beansConfig = ProxyBeansConfig()
    private val configValidationService = ConfigValidationService(mockk(relaxed = true))

    @Test
    fun `empty proxy URL returns Disabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(url = ProxyUrl(""), username = ProxyUsername(""), password = ProxyPassword(""))

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for empty URL")
    }

    @Test
    fun `blank proxy URL returns Disabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(url = ProxyUrl("   "), username = ProxyUsername(""), password = ProxyPassword(""))

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for blank URL")
    }

    @Test
    fun `valid HTTP proxy URL with explicit port creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("http://proxy.example.com:8080"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNotNull(result)
        assertEquals(Proxy.Type.HTTP, result!!.type())
        val address = (result.address() as java.net.InetSocketAddress)
        assertEquals("proxy.example.com", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun `valid HTTPS proxy URL with explicit port creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("https://secure-proxy.example.com:8443"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNotNull(result)
        assertEquals(Proxy.Type.HTTP, result!!.type())
        val address = (result.address() as java.net.InetSocketAddress)
        assertEquals("secure-proxy.example.com", address.hostString)
        assertEquals(8443, address.port)
    }

    @Test
    fun `HTTP proxy URL without port defaults to port 80`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(url = ProxyUrl("http://proxy.example.com"), username = ProxyUsername(""), password = ProxyPassword(""))

        val result = beansConfig.proxy(config, configValidationService)
        assertNotNull(result)
        val address = (result!!.address() as java.net.InetSocketAddress)
        assertEquals("proxy.example.com", address.hostString)
        assertEquals(80, address.port)
    }

    @Test
    fun `HTTPS proxy URL without port defaults to port 443`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("https://secure-proxy.example.com"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNotNull(result)
        val address = (result!!.address() as java.net.InetSocketAddress)
        assertEquals("secure-proxy.example.com", address.hostString)
        assertEquals(443, address.port)
    }

    @Test
    fun `proxy URL without http or https prefix returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(url = ProxyUrl("proxy.example.com:8080"), username = ProxyUsername(""), password = ProxyPassword(""))

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for URL without http/https prefix")
    }

    @Test
    fun `proxy URL with ftp scheme returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("ftp://proxy.example.com:8080"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for FTP scheme")
    }

    @Test
    fun `proxy URL with socks scheme returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("socks://proxy.example.com:8080"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for SOCKS scheme")
    }

    @Test
    fun `proxy URL with invalid format returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("http://invalid url with spaces"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for invalid URL format")
    }

    @Test
    fun `proxy URL with missing host returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(url = ProxyUrl("http://:8080"), username = ProxyUsername(""), password = ProxyPassword(""))

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for URL with missing host")
    }

    @Test
    fun `proxy URL with localhost creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(url = ProxyUrl("http://localhost:3128"), username = ProxyUsername(""), password = ProxyPassword(""))

        val result = beansConfig.proxy(config, configValidationService)
        assertNotNull(result)
        val address = (result!!.address() as java.net.InetSocketAddress)
        assertEquals("localhost", address.hostString)
        assertEquals(3128, address.port)
    }

    @Test
    fun `proxy URL with IP address creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("http://192.168.1.100:8080"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNotNull(result)
        val address = (result!!.address() as java.net.InetSocketAddress)
        assertEquals("192.168.1.100", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun `proxy URL with IPv6 address creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(url = ProxyUrl("http://[::1]:8080"), username = ProxyUsername(""), password = ProxyPassword(""))

        val result = beansConfig.proxy(config, configValidationService)
        assertNotNull(result)
        val address = (result!!.address() as java.net.InetSocketAddress)
        // Note: URI parser includes the brackets in the host for IPv6 addresses
        assertEquals("0:0:0:0:0:0:0:1", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun `proxy URL with authentication info is parsed correctly`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("http://user:pass@proxy.example.com:8080"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        // Note: Authentication info is not used in proxy configuration currently
        // but the URL should still parse and create a valid proxy
        assertNotNull(result)
        val address = (result!!.address() as java.net.InetSocketAddress)
        assertEquals("proxy.example.com", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun `proxy URL with path is parsed correctly`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("http://proxy.example.com:8080/path"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        // Path should be ignored for proxy configuration
        assertNotNull(result)
        val address = (result!!.address() as java.net.InetSocketAddress)
        assertEquals("proxy.example.com", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun `proxy URL with very large port number returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("http://proxy.example.com:99999"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        // Port number > 65535 should cause IllegalArgumentException
        assertNull(result, "Expected null for invalid port number")
    }

    @Test
    fun `proxy URL with negative port number returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("http://proxy.example.com:-1"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for negative port number")
    }

    @Test
    fun `proxy URL with non-numeric port returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyConfig } returns ProxyConfig(
            url = ProxyUrl("http://proxy.example.com:abc"),
            username = ProxyUsername(""),
            password = ProxyPassword("")
        )

        val result = beansConfig.proxy(config, configValidationService)
        assertNull(result, "Expected null for non-numeric port")
    }
}

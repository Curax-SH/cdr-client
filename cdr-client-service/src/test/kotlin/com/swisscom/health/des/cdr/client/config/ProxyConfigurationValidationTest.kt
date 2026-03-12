package com.swisscom.health.des.cdr.client.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    private val context = CdrClientContext()

    @Test
    fun `empty proxy URL returns Disabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for empty URL, but got ${result::class.simpleName}"
        }
    }

    @Test
    fun `blank proxy URL returns Disabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("   ")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for blank URL, but got ${result::class.simpleName}"
        }
    }

    @Test
    fun `valid HTTP proxy URL with explicit port creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://proxy.example.com:8080")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for valid HTTP URL with port"
        }
        val enabled = result as ProxyConfiguration.Enabled
        assertEquals("proxy.example.com", enabled.host)
        assertEquals(8080, enabled.port)
        assertNotNull(enabled.proxy)
        assertEquals(Proxy.Type.HTTP, enabled.proxy.type())
    }

    @Test
    fun `valid HTTPS proxy URL with explicit port creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("https://secure-proxy.example.com:8443")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for valid HTTPS URL with port"
        }
        val enabled = result as ProxyConfiguration.Enabled
        assertEquals("secure-proxy.example.com", enabled.host)
        assertEquals(8443, enabled.port)
        assertNotNull(enabled.proxy)
        assertEquals(Proxy.Type.HTTP, enabled.proxy.type())
    }

    @Test
    fun `HTTP proxy URL without port defaults to port 80`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://proxy.example.com")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for HTTP URL without port"
        }
        val enabled = result as ProxyConfiguration.Enabled
        assertEquals("proxy.example.com", enabled.host)
        assertEquals(80, enabled.port)
        assertNotNull(enabled.proxy)
    }

    @Test
    fun `HTTPS proxy URL without port defaults to port 443`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("https://secure-proxy.example.com")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for HTTPS URL without port"
        }
        val enabled = result as ProxyConfiguration.Enabled
        assertEquals("secure-proxy.example.com", enabled.host)
        assertEquals(443, enabled.port)
        assertNotNull(enabled.proxy)
    }

    @Test
    fun `proxy URL without http or https prefix returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("proxy.example.com:8080")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for URL without http/https prefix"
        }
    }

    @Test
    fun `proxy URL with ftp scheme returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("ftp://proxy.example.com:8080")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for FTP scheme"
        }
    }

    @Test
    fun `proxy URL with socks scheme returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("socks://proxy.example.com:8080")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for SOCKS scheme"
        }
    }

    @Test
    fun `proxy URL with invalid format returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://invalid url with spaces")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for invalid URL format"
        }
    }

    @Test
    fun `proxy URL with missing host returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://:8080")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for URL with missing host"
        }
    }

    @Test
    fun `proxy URL with localhost creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://localhost:3128")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for localhost proxy"
        }
        val enabled = result as ProxyConfiguration.Enabled
        assertEquals("localhost", enabled.host)
        assertEquals(3128, enabled.port)
    }

    @Test
    fun `proxy URL with IP address creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://192.168.1.100:8080")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for IP address proxy"
        }
        val enabled = result as ProxyConfiguration.Enabled
        assertEquals("192.168.1.100", enabled.host)
        assertEquals(8080, enabled.port)
    }

    @Test
    fun `proxy URL with IPv6 address creates Enabled configuration`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://[::1]:8080")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for IPv6 address proxy"
        }
        val enabled = result as ProxyConfiguration.Enabled
        // Note: URI parser includes the brackets in the host for IPv6 addresses
        assertEquals("[::1]", enabled.host)
        assertEquals(8080, enabled.port)
    }

    @Test
    fun `proxy URL with authentication info is parsed correctly`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://user:pass@proxy.example.com:8080")

        val result = context.proxyConfiguration(config)

        // Note: Authentication info is not used in proxy configuration currently
        // but the URL should still parse and create a valid proxy
        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for URL with authentication"
        }
        val enabled = result as ProxyConfiguration.Enabled
        assertEquals("proxy.example.com", enabled.host)
        assertEquals(8080, enabled.port)
    }

    @Test
    fun `proxy URL with path is parsed correctly`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://proxy.example.com:8080/path")

        val result = context.proxyConfiguration(config)

        // Path should be ignored for proxy configuration
        assertTrue(result is ProxyConfiguration.Enabled) {
            "Expected ProxyConfiguration.Enabled for URL with path"
        }
        val enabled = result as ProxyConfiguration.Enabled
        assertEquals("proxy.example.com", enabled.host)
        assertEquals(8080, enabled.port)
    }

    @Test
    fun `proxy URL with very large port number returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://proxy.example.com:99999")

        val result = context.proxyConfiguration(config)

        // Port number > 65535 should cause IllegalArgumentException
        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for invalid port number"
        }
    }

    @Test
    fun `proxy URL with negative port number returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://proxy.example.com:-1")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for negative port number"
        }
    }

    @Test
    fun `proxy URL with non-numeric port returns Disabled`() {
        val config = mockk<CdrClientConfig>()
        every { config.proxyUrl } returns ProxyUrl("http://proxy.example.com:abc")

        val result = context.proxyConfiguration(config)

        assertTrue(result is ProxyConfiguration.Disabled) {
            "Expected ProxyConfiguration.Disabled for non-numeric port"
        }
    }
}



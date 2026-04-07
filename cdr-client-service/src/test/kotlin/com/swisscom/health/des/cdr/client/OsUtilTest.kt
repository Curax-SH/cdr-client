package com.swisscom.health.des.cdr.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class OsUtilTest {

    @Test
    fun `getOsInfo should return OS information`() {
        val osInfo = getOsInfo()

        assertThat(osInfo).isNotBlank()
        assertThat(osInfo).containsAnyOf("Windows", "Mac", "Linux", "Darwin", "Unknown")
        assertThat(osInfo.length).isGreaterThan(3)
    }

    @Test
    fun `getOsInfo should include architecture`() {
        val osInfo = getOsInfo()
        assertThat(osInfo).containsAnyOf("amd64", "x86_64", "aarch64", "arm64", "x86")
    }
}


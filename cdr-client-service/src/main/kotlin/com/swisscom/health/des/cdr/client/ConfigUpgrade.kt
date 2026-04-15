package com.swisscom.health.des.cdr.client

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * Copies the external default (customer) SpringBoot configuration file to its destination, if it does not already exist.
 */
object ConfigUpgrade {

    /**
     * The list of [upgrade steps][UpgradeStep] to be applied.
     *
     * The order matters!
     */
    @JvmStatic
    private val UPGRADE_STEPS: List<UpgradeStep> = listOf(
        UpgradeStep.V10ToV11,
        UpgradeStep.V11ToV12
    )

    @JvmStatic
    private val YAML_MAPPER: YAMLMapper =
        YAMLMapper.Builder(
            YAMLMapper(
                YAMLFactory()
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                    .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            )
        ).run {
            addModule(kotlinModule())
            build()
                .apply { setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE) }
        }

    /**
     * Applies all known upgrade steps to the external SpringBoot configuration file at [configLocation].
     * The upgrade steps assume the configuration file is in YAML format. Other formats are not supported.
     *
     * @param configLocation path to the external SpringBoot configuration file
     * @return the [UpgradeResult] of the upgrade process, containing the final configuration version and,
     * in case of failure, the reason for the failure
     */
    fun applyPendingUpgradeSteps(configLocation: Path): UpgradeResult {
        val upgradeStepResults: List<UpgradeStepResult> = UPGRADE_STEPS
            .fold(
                initial = emptyList(),
                operation = { acc: List<UpgradeStepResult>, upgradeStep: UpgradeStep ->
                    // if we encountered an upgrade error in a previous step, there is no point in trying further upgrades
                    if (acc.isNotEmpty() && acc.last() is UpgradeStepResult.Failure) {
                        acc
                    } else {
                        acc + upgradeStep.upgrade(
                            configRoot = if (acc.isEmpty()) {
                                YAML_MAPPER.readTree(configLocation.inputStream(StandardOpenOption.READ)) as ObjectNode
                            } else {
                                acc.last().configRoot
                            }
                        )
                    }
                }
            )

        return when (val lastUpgradeStep = upgradeStepResults.last()) {
            is UpgradeStepResult.Success -> runCatching {
                persistConfig(configRoot = lastUpgradeStep.configRoot, configLocation = configLocation)
            }.fold(
                onSuccess = { _ -> UpgradeResult.Success(version = getConfigVersion(lastUpgradeStep.configRoot)) },
                onFailure = { t -> UpgradeResult.Failure(version = getConfigVersion(lastUpgradeStep.configRoot), reason = t.toString()) }
            )

            is UpgradeStepResult.Failure -> UpgradeResult.Failure(
                version = getConfigVersion(upgradeStepResults.last().configRoot),
                reason = lastUpgradeStep.reason
            )

            is UpgradeStepResult.NoStep -> UpgradeResult.AlreadyAtLatestVersion
        }
    }

    private fun persistConfig(configLocation: Path, configRoot: ObjectNode) {
        YAML_MAPPER.writeValue(configLocation.outputStream().writer(), configRoot)
    }
}

sealed class UpgradeResult {
    object AlreadyAtLatestVersion : UpgradeResult()
    data class Success(val version: Version) : UpgradeResult()
    data class Failure(val version: Version, val reason: String) : UpgradeResult()
}

private sealed interface UpgradeStepResult {
    val configRoot: ObjectNode

    data class NoStep(override val configRoot: ObjectNode) : UpgradeStepResult
    data class Success(override val configRoot: ObjectNode) : UpgradeStepResult
    data class Failure(override val configRoot: ObjectNode, val reason: String) : UpgradeStepResult
}

/**
 * An atomic upgrade step from a source version to a target version of the configuration.
 * If any part of the upgrade step fails or of no upgrade is performed, the original input
 * object tree is returned as the [UpgradeStepResult.configRoot].
 */
private sealed class UpgradeStep(
    private val fromVersion: Version,
    private val toVersion: Version,
) {

    protected abstract fun upgradeImpl(configRoot: ObjectNode): UpgradeStepResult

    /**
     * Upgrades the configuration if the current configuration version matches the expected [fromVersion]
     * of this upgrade step. If the version matches, the [upgradeImpl] method is called to perform the
     * actual upgrade. If the upgrade is successful, the configuration version is updated to the
     * [toVersion] of this upgrade step. If the version does not match, a [UpgradeStepResult.NoStep] is
     * returned, indicating that this upgrade step is not applicable to the current configuration version.
     *
     * @param configRoot the root node of the configuration to be upgraded
     * @return the result of the upgrade step, which can be a success, failure, or no step if the version
     * does not match
     */
    fun upgrade(configRoot: ObjectNode): UpgradeStepResult =
        if (fromVersion == getConfigVersion(configRoot)) {
            val newObjectTree: ObjectNode = configRoot.deepCopy()
            upgradeImpl(configRoot = newObjectTree).let { result ->
                when (result) {
                    is UpgradeStepResult.Success -> result.also {
                        result.configRoot.put(PROPERTY_NAME_VERSION, toVersion.value)
                    }

                    is UpgradeStepResult.Failure -> result.copy(configRoot = configRoot) // reset config to the version before we failed
                    is UpgradeStepResult.NoStep -> result.copy(configRoot = configRoot) // no step would normally mean no alterations, but let's be safe
                }
            }
        } else {
            UpgradeStepResult.NoStep(configRoot = configRoot)
        }


    /**
     * Add new properties:
     * * `client.proxy-url`; default value is an empty string
     */
    object V10ToV11 : UpgradeStep(fromVersion = Version("1.0"), toVersion = Version("1.1")) {

        private const val PROPERTY_NAME_CLIENT = "client"
        private const val PROPERTY_NAME_PROXY_URL = "proxy-url"

        override fun upgradeImpl(configRoot: ObjectNode): UpgradeStepResult =
            configRoot[PROPERTY_NAME_CLIENT]
                ?.let { it as ObjectNode }
                ?.let { clientNode: ObjectNode ->
                    clientNode.put(PROPERTY_NAME_PROXY_URL, "")
                    UpgradeStepResult.Success(
                        configRoot = configRoot
                    )
                }
                ?: UpgradeStepResult.Failure(
                    configRoot = configRoot,
                    reason = "Failed to look up 'client' object node"
                )

    }

    /**
     * Remove property:
     * * `client.proxy-url`
     *
     * and replace it with:
     *
     * * `client.proxy-config.url` (re-apply the value of `client.proxy-url`)
     * * `client.proxy-config.username`
     * * `client.proxy-config.password`
     */
    object V11ToV12 : UpgradeStep(fromVersion = Version("1.1"), toVersion = Version("1.2")) {

        private const val PROPERTY_NAME_CLIENT = "client"
        private const val PROPERTY_NAME_PROXY_URL = "proxy-url"
        private const val PROPERTY_NAME_PROXY_CONFIG = "proxy-config"
        private const val PROPERTY_NAME_PROXY_CONFIG_URL = "url"
        private const val PROPERTY_NAME_PROXY_CONFIG_USERNAME = "username"
        private const val PROPERTY_NAME_PROXY_CONFIG_PASSWORD = "password"

        override fun upgradeImpl(configRoot: ObjectNode): UpgradeStepResult =
            configRoot.at("/$PROPERTY_NAME_CLIENT/$PROPERTY_NAME_PROXY_URL")
                .takeUnless { it.isMissingNode }
                ?.textValue()
                ?.let { proxyUrl: String ->
                    configRoot[PROPERTY_NAME_CLIENT]
                        ?.let { it as ObjectNode }
                        ?.let { clientNode: ObjectNode ->
                            clientNode.remove(PROPERTY_NAME_PROXY_URL)
                            clientNode.putObject(PROPERTY_NAME_PROXY_CONFIG).run {
                                put(PROPERTY_NAME_PROXY_CONFIG_URL, proxyUrl)
                                put(PROPERTY_NAME_PROXY_CONFIG_USERNAME, "")
                                put(PROPERTY_NAME_PROXY_CONFIG_PASSWORD, "")
                            }
                            UpgradeStepResult.Success(
                                configRoot = configRoot
                            )
                        }
                        ?: UpgradeStepResult.Failure(
                            configRoot = configRoot,
                            reason = "Failed to look up '$PROPERTY_NAME_CLIENT' object node"
                        )
                }
                ?: UpgradeStepResult.Failure(
                    configRoot = configRoot,
                    reason = "Failed to look up '/$PROPERTY_NAME_CLIENT/$PROPERTY_NAME_PROXY_URL' object node"
                )
    }
}

@JvmInline
value class Version(val value: String)

private fun getConfigVersion(configRoot: ObjectNode): Version =
    Version(configRoot[PROPERTY_NAME_VERSION]?.asText() ?: "1.0") // the first version of the configuration had no version property

private const val PROPERTY_NAME_VERSION = "version"

/*
 * Copyright 2023 Grabtaxi Holdings PTE LTD (GRAB)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.grab.grazel.gradle.variant

import com.android.build.gradle.api.BaseVariant
import com.android.builder.model.BuildType
import com.grab.grazel.di.qualifiers.RootProject
import com.grab.grazel.gradle.ConfigurationScope
import com.grab.grazel.gradle.isAndroidApplication
import org.gradle.api.Project
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Variant matcher helps in resolving android variant graph accounting for variant combinations of graph
 * such as
 *  * App module contains a flavor and library module does not
 *  * App module contains a build type and library module does not
 *  * App module contains flavor dimensions but library module does not.
 * The returned [MatchedVariant] can be used for migration
 *
 * Notes:
 *   Currently `missingDimension` are not handled.
 *   Functionality of this class can be merged with [VariantBuilder] if needed.
 */
internal interface VariantMatcher {
    /**
     * For given [project], returns [Set] of [MatchedVariant] generated by accounting for
     * * build type or its fallbacks
     * * flavors or its fallbacks
     *
     * The method accounts for various combination of variant setup between with app module and
     * library module [project]. The app module's variants are used as source truth to generate
     * the [MatchedVariant] since app module typically in the entry point to build.
     *
     * Throws error when either build type or flavor cannot be matched successfully.
     */
    fun matchedVariants(
        project: Project,
        scope: ConfigurationScope
    ): Set<MatchedVariant>
}

internal data class MatchedVariant(
    /**
     * The actual name of the matched variant
     */
    val variantName: String,
    /**
     * Sorted set of flavors contributing to this variant
     */
    val flavors: Set<String>,
    /**
     * The matched app module build type for this variant
     */
    val buildType: String,
    /**
     * The actual library variant that will be used for migration.
     */
    val variant: BaseVariant,
)

private val HUMPS = "(?<=.)(?=\\p{Upper})".toRegex()
internal val MatchedVariant.nameSuffix get() = "-${variantName.replace(HUMPS, "-").toLowerCase()}"

@Singleton
internal class DefaultVariantMatcher
@Inject
constructor(
    @param:RootProject private val rootProject: Project,
    private val androidVariantDataSource: AndroidVariantDataSource,
) : VariantMatcher {
    /**
     * In order to match variants correctly we need to derive the matching from active android application
     * project which will be the source of truth for buildable variants. We find the application project
     * here with the following caveats
     *  * Multiple application projects in a project module graph is not supported
     *  TODO("Support multiple android application modules")
     */
    private val appProject: Project by lazy {
        rootProject.subprojects
            .filter(Project::isAndroidApplication)
            .let { appModules ->
                when (appModules.size) {
                    0 -> error("At least one android application module required")
                    1 -> appModules.first()
                    else -> error("Multiple android applications modules in the graph are not supported")
                }
            }
    }

    override fun matchedVariants(
        project: Project,
        scope: ConfigurationScope
    ): Set<MatchedVariant> {
        /**
         * In order to generate set of matched variants, app variants are used as source of truth,
         * then for each app variant we find a suitable variant on the module variant. The implementation
         * accounts for flavor dimensions and fallbacks as well as build type and fallbacks.
         *
         * To do this we find a set of candidates and then filter them based on matches in each build type
         * or flavors. First build type is used to find the candidates since it only one dimension,
         * then since flavors can have multiple candidates we account for partial matches and then
         * use fallbacks in each unmatched flavors to find a matching one
         */
        val appVariants = androidVariantDataSource.getMigratableVariants(appProject, scope)

        val libraryVariants = androidVariantDataSource.getMigratableVariants(project, scope)
        val libraryVariantsByBuildType = libraryVariants.groupBy { it.buildType.name }
        val libraryVariantsByName = libraryVariants.groupBy { it.name }

        val appBuildTypeFallbacks = androidVariantDataSource.buildTypeFallbacks(appProject)
        val appFlavorFallbacks = androidVariantDataSource.flavorFallbacks(appProject)

        // First build type
        return appVariants.map { variant ->
            val appVariantFlavors = variant.productFlavors.map { it.name }.toSet()
            val buildType = variant.buildType

            val variantName = variant.name
                .replace("UnitTest", "")
                .replace("AndroidTest", "")

            if (libraryVariantsByName.containsKey(variant.name)) {
                // Direct match, no custom matching needed
                val candidate = libraryVariantsByName.getValue(variant.name).first()
                return@map MatchedVariant(
                    variantName = variantName,
                    flavors = emptySet(),
                    buildType = variant.buildType.name,
                    variant = candidate
                )
            }

            // Calculate the build type candidates first
            val buildTypeCandidates = calcBuildTypeCandidates(
                libraryVariantsByBuildType = libraryVariantsByBuildType,
                appBuildType = buildType,
                appBuildTypeFallbacks = appBuildTypeFallbacks,
                libraryProject = project
            )

            // Refine and get flavor candidates
            val matchedVariant = calcFlavorCandidates(
                appVariantFlavors,
                appFlavorFallbacks,
                buildTypeCandidates,
                project
            )

            MatchedVariant(
                variantName = variantName,
                flavors = appVariantFlavors,
                buildType = variant.buildType.name,
                variant = matchedVariant
            )
        }.toSet()
    }

    /**
     * Tries to find list of [BaseVariant] that match the [appBuildType], in case direct match is not
     * found, `matchingFallbacks` is used to find the fallback build type in library module.
     */
    private fun calcBuildTypeCandidates(
        libraryVariantsByBuildType: Map<String, List<BaseVariant>>,
        appBuildType: BuildType,
        appBuildTypeFallbacks: Map<String, Set<String>>,
        libraryProject: Project
    ): Set<BaseVariant> {
        val variantCandidates = mutableListOf<BaseVariant>()
        if (libraryVariantsByBuildType.containsKey(appBuildType.name)) {
            val buildTypeCandidates = libraryVariantsByBuildType.getOrDefault(
                appBuildType.name,
                emptySet()
            )
            variantCandidates.addAll(buildTypeCandidates)
        } else {
            // Try using matching fallbacks
            appBuildTypeFallbacks
                .getOrDefault(appBuildType.name, emptySet())
                .mapNotNull { fallbackBuildType -> libraryVariantsByBuildType[fallbackBuildType] }
                .firstOrNull()
                ?.let { variantCandidates.addAll(it) }
                ?: error(
                    "Could not match app build type '${appBuildType.name}' with " +
                        "${libraryProject.path}'s build type, ensure build type `matchingFallbacks` " +
                        "are specified in ${appProject.path}"
                )
        }
        return variantCandidates.toSet()
    }

    /**
     * For given [flavors] and flavor fallbacks in the app module, tries to find a [BaseVariant] that
     * at least has one match in each flavors' fallbacks.
     *
     * For example for
     * ```
     *   flavors: free
     *   appFlavorFallbacks: ["free" : "paid"]
     *   candidates: ["paidDebug", "anotherDebug"]
     * ```
     * The candidate result will be `paidDebug`
     */
    private fun findMatchingCandidateWithFallbacks(
        flavors: Set<String>,
        appFlavorFallbacks: Map<String, Set<String>>,
        candidates: Collection<BaseVariant>,
    ): BaseVariant? = flavors.mapNotNull { flavor ->
        val fallbacks = appFlavorFallbacks.getOrDefault(flavor, emptySet())
        candidates.firstOrNull { candidate ->
            candidate.productFlavors.any { it.name in fallbacks }
        }
    }.firstOrNull()

    /**
     * Given [buildTypeCandidates] that match the app modules build type, this method attempts to
     * find a [BaseVariant] among them that matches with the [appVariantFlavors]. In case a perfect
     * match is not found, flavor fallbacks is used to find the closest match failing which error is
     * thrown.
     */
    private fun calcFlavorCandidates(
        appVariantFlavors: Set<String>,
        appFlavorFallbacks: Map<String, Set<String>>,
        buildTypeCandidates: Set<BaseVariant>,
        libraryProject: Project
    ): BaseVariant {
        val candidateFlavors = buildTypeCandidates
            .flatMap { it.productFlavors }
            .map { it.name }
            .groupBy { it }

        val matchedVariant = if (candidateFlavors.isEmpty()) {
            // Library does not have flavors, build type match is enough
            buildTypeCandidates.first()
        } else {
            // Library has flavors, app flavors should match with library's or should have fallbacks
            // Check if there is any full match in requested flavors and return early, if not
            // check for partial matches and their fallbacks.

            val fullMatchCandidates = buildTypeCandidates.filter {
                it.productFlavors.all { flavor -> flavor.name in appVariantFlavors }
            }
            val partialMatchCandidates = buildTypeCandidates.filter {
                it.productFlavors.any { flavor -> flavor.name in appVariantFlavors }
            }
            val isFullMatch = fullMatchCandidates.isNotEmpty()
            val isPartialMatch = partialMatchCandidates.isNotEmpty()
            when {
                isFullMatch -> fullMatchCandidates.first()
                isPartialMatch -> {
                    // Only some flavor match, ensure their fallbacks match or fail
                    val matchedFlavors = partialMatchCandidates
                        .flatMap { it.productFlavors.map { flavor -> flavor.name } }
                        .toSet()
                    val unmatchedFlavors = (appVariantFlavors - matchedFlavors)
                    findMatchingCandidateWithFallbacks(
                        unmatchedFlavors,
                        appFlavorFallbacks,
                        partialMatchCandidates
                    ) ?: error(
                        "Could not match $unmatchedFlavors with ${libraryProject.path}'s flavors, " +
                            "ensure flavor `matchingFallbacks` are specified in ${appProject.path}"
                    )
                }
                else -> {
                    // App's flavor and library's flavor should match else error.
                    findMatchingCandidateWithFallbacks(
                        appVariantFlavors,
                        appFlavorFallbacks,
                        buildTypeCandidates
                    ) ?: error(
                        "Could not match $appVariantFlavors with ${libraryProject.path}'s flavors, " +
                            "ensure flavor `matchingFallbacks` are specified in ${appProject.path}"
                    )
                }
            }
        }
        return matchedVariant
    }
}


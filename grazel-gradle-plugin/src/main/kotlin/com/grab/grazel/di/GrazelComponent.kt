/*
 * Copyright 2022 Grabtaxi Holdings PTE LTD (GRAB)
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

package com.grab.grazel.di

import com.grab.grazel.GrazelExtension
import com.grab.grazel.di.qualifiers.RootProject
import com.grab.grazel.gradle.ConfigurationDataSource
import com.grab.grazel.gradle.DefaultConfigurationDataSource
import com.grab.grazel.gradle.DefaultGradleProjectInfo
import com.grab.grazel.gradle.DefaultRepositoryDataSource
import com.grab.grazel.gradle.GradleProjectInfo
import com.grab.grazel.gradle.MigrationChecker
import com.grab.grazel.gradle.MigrationCriteriaModule
import com.grab.grazel.gradle.RepositoryDataSource
import com.grab.grazel.gradle.dependencies.DependenciesDataSource
import com.grab.grazel.gradle.dependencies.DependenciesGraphsBuilder
import com.grab.grazel.gradle.dependencies.DependenciesModule
import com.grab.grazel.gradle.dependencies.DependencyGraphs
import com.grab.grazel.gradle.dependencies.MavenInstallArtifactsCalculator
import com.grab.grazel.gradle.variant.AndroidVariantDataSource
import com.grab.grazel.gradle.variant.AndroidVariantsExtractor
import com.grab.grazel.gradle.variant.DefaultAndroidVariantDataSource
import com.grab.grazel.gradle.variant.DefaultAndroidVariantsExtractor
import com.grab.grazel.gradle.variant.VariantBuilder
import com.grab.grazel.gradle.variant.VariantMatcher
import com.grab.grazel.gradle.variant.VariantModule
import com.grab.grazel.hybrid.HybridBuildExecutor
import com.grab.grazel.hybrid.HybridBuildModule
import com.grab.grazel.migrate.android.AndroidInstrumentationBinaryDataExtractor
import com.grab.grazel.migrate.builder.AndroidBinaryTargetBuilderModule
import com.grab.grazel.migrate.builder.AndroidInstrumentationBinaryTargetBuilderModule
import com.grab.grazel.migrate.builder.AndroidLibTargetBuilderModule
import com.grab.grazel.migrate.builder.KtAndroidLibTargetBuilderModule
import com.grab.grazel.migrate.builder.KtLibTargetBuilderModule
import com.grab.grazel.migrate.dependencies.ArtifactsPinner
import com.grab.grazel.migrate.dependencies.DefaultArtifactsPinner
import com.grab.grazel.migrate.internal.ProjectBazelFileBuilder
import com.grab.grazel.migrate.internal.RootBazelFileBuilder
import com.grab.grazel.migrate.internal.WorkspaceBuilder
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Lazy
import dagger.Module
import dagger.Provides
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the
import javax.inject.Singleton

@Component(
    modules = [
        GrazelModule::class,
        KtLibTargetBuilderModule::class,
        KtAndroidLibTargetBuilderModule::class,
        AndroidLibTargetBuilderModule::class,
        AndroidBinaryTargetBuilderModule::class,
        AndroidInstrumentationBinaryTargetBuilderModule::class,
    ]
)
@Singleton
internal interface GrazelComponent {

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance @RootProject rootProject: Project
        ): GrazelComponent
    }

    fun extension(): GrazelExtension
    fun migrationChecker(): Lazy<MigrationChecker>
    fun projectBazelFileBuilderFactory(): Lazy<ProjectBazelFileBuilder.Factory>
    fun workspaceBuilderFactory(): Lazy<WorkspaceBuilder.Factory>
    fun rootBazelFileBuilder(): Lazy<RootBazelFileBuilder>
    fun artifactsPinner(): Lazy<ArtifactsPinner>
    fun dependenciesDataSource(): Lazy<DependenciesDataSource>
    fun mavenInstallArtifactsCalculator(): Lazy<MavenInstallArtifactsCalculator>
    fun hybridBuildExecutor(): HybridBuildExecutor

    fun androidInstrumentationBinaryDataExtractor(): Lazy<AndroidInstrumentationBinaryDataExtractor>
    fun variantBuilder(): Lazy<VariantBuilder>
    fun variantMatcher(): Lazy<VariantMatcher>
}

@Module(
    includes = [
        MigrationCriteriaModule::class,
        DependenciesModule::class,
        HybridBuildModule::class,
        VariantModule::class,
    ]
)
internal interface GrazelModule {
    @Binds
    fun DefaultGradleProjectInfo.bindGradleProjectIndo(): GradleProjectInfo

    @Binds
    fun DefaultConfigurationDataSource.bindConfigurationDataSource(): ConfigurationDataSource

    @Binds
    fun DefaultRepositoryDataSource.bindRepositoryDataSource(): RepositoryDataSource

    @Binds
    fun DefaultAndroidVariantsExtractor.bindAndroidVariantsExtractor(): AndroidVariantsExtractor

    @Binds
    fun DefaultArtifactsPinner.bindArtifactsPinner(): ArtifactsPinner

    companion object {
        @Singleton
        @Provides
        fun @receiver:RootProject Project.provideGrazelExtension(): GrazelExtension = the()

        @Provides
        @Singleton
        fun DependenciesGraphsBuilder.provideDependencyGraphs(): DependencyGraphs = build()

        @Provides
        @Singleton
        fun GrazelExtension.provideAndroidVariantDataSource(
            androidVariantsExtractor: DefaultAndroidVariantsExtractor,
            @RootProject rootProject: Project
        ): AndroidVariantDataSource = DefaultAndroidVariantDataSource(
            variantFilter = android.variantFilter,
            androidVariantsExtractor = androidVariantsExtractor
        )

        @Provides
        @Singleton
        fun GrazelExtension.provideKotlinExtension() = rules.kotlin

        @Provides
        @Singleton
        fun GrazelExtension.provideTestExtension() = rules.test

        @Provides
        @Singleton
        fun GrazelExtension.provideMavenInstallExtension() = rules.mavenInstall
    }
}




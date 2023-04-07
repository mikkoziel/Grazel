workspace(name = "grazel")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = "f033fa36f51073eae224f18428d9493966e67c27387728b6be2ebbdae43f140e",
    url = "https://github.com/bazelbuild/rules_kotlin/releases/download/v1.7.0-RC-3/rules_kotlin_release.tgz",
)

KOTLIN_VERSION = "1.6.10"

KOTLINC_RELEASE_SHA = "432267996d0d6b4b17ca8de0f878e44d4a099b7e9f1587a98edc4d27e76c215a"

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories", "kotlinc_version")

KOTLINC_RELEASE = kotlinc_version(
    release = KOTLIN_VERSION,
    sha256 = KOTLINC_RELEASE_SHA,
)

kotlin_repositories(compiler_release = KOTLINC_RELEASE)

register_toolchains("//:kotlin_toolchain")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

git_repository(
    name = "grab_bazel_common",
    commit = "9abdf2229b56e816d08a91acee2cfaf48098ce06",
    remote = "https://github.com/grab/grab-bazel-common.git",
)

load("@grab_bazel_common//android:repositories.bzl", "bazel_common_dependencies")

bazel_common_dependencies()

load("@grab_bazel_common//android:initialize.bzl", "bazel_common_initialize")

bazel_common_initialize(
    buildifier_version = "5.1.0",
    patched_android_tools = True,
)

load("@grab_bazel_common//android:maven.bzl", "pin_bazel_common_artifacts")

pin_bazel_common_artifacts()

DAGGER_TAG = "2.37"

DAGGER_SHA = "0f001ed38ed4ebc6f5c501c20bd35a68daf01c8dbd7541b33b7591a84fcc7b1c"

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "dagger",
    sha256 = DAGGER_SHA,
    strip_prefix = "dagger-dagger-%s" % DAGGER_TAG,
    url = "https://github.com/google/dagger/archive/dagger-%s.zip" % DAGGER_TAG,
)

load("@dagger//:workspace_defs.bzl", "DAGGER_ARTIFACTS", "DAGGER_REPOSITORIES")
load("@grab_bazel_common//:workspace_defs.bzl", "GRAB_BAZEL_COMMON_ARTIFACTS")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "rules_jvm_external",
    sha256 =8c3b207722e5f97f1c83311582a6c11df99226e65e2471086e296561e57cc9547b",
    strip_prefix = "rules_jvm_external-4.4.2",
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/5.1.zip",
)

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven")

maven_install(
    artifacts = DAGGER_ARTIFACTS + GRAB_BAZEL_COMMON_ARTIFACTS + [
        "androidx.annotation:annotation:1.1.0",
        "androidx.appcompat:appcompat:1.3.1",
        "androidx.constraintlayout:constraintlayout-core:1.0.4",
        maven.artifact(
            artifact = "constraintlayout",
            exclusions = [
                "androidx.appcompat:appcompat",
                "androidx.core:core",
            ],
            group = "androidx.constraintlayout",
            version = "2.1.4",
        ),
        "androidx.core:core:1.5.0",
        "androidx.databinding:databinding-adapters:7.2.2",
        "androidx.databinding:databinding-common:7.2.2",
        "androidx.databinding:databinding-compiler:7.2.2",
        "androidx.databinding:databinding-runtime:7.2.2",
        "androidx.databinding:viewbinding:7.2.2",
        "androidx.test.espresso:espresso-core:3.4.0",
        "androidx.test.ext:junit:1.1.3",
        "com.squareup.leakcanary:leakcanary-android:2.10",
        "junit:junit:4.13.2",
        "org.jacoco:org.jacoco.ant:0.8.7",
        "org.jetbrains.kotlin:kotlin-annotation-processing-gradle:1.6.10",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.31",
        "org.jetbrains.kotlin:kotlin-stdlib:1.5.31",
    ],
    excluded_artifacts = ["androidx.test.espresso:espresso-contrib"],
    fail_on_missing_checksum = False,
    jetify = True,
    jetify_include_list = [
        "androidx.annotation:annotation",
        "androidx.constraintlayout:constraintlayout",
        "androidx.constraintlayout:constraintlayout-core",
        "androidx.core:core",
        "androidx.databinding:databinding-adapters",
        "androidx.databinding:databinding-common",
        "androidx.databinding:databinding-compiler",
        "androidx.databinding:databinding-runtime",
        "androidx.databinding:viewbinding",
        "androidx.test.espresso:espresso-core",
        "androidx.test.ext:junit",
        "com.android.support:cardview-v7",
        "com.squareup.leakcanary:leakcanary-android",
        "junit:junit",
        "org.jacoco:org.jacoco.ant",
        "org.jetbrains.kotlin:kotlin-annotation-processing-gradle",
        "org.jetbrains.kotlin:kotlin-stdlib",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8",
    ],
    maven_install_json = "//:maven_install.json",
    override_targets = {
        "androidx.appcompat:appcompat": "@//third_party:androidx_appcompat_appcompat",
    },
    repositories = [
        "https://dl.google.com/dl/android/maven2/",
        "https://repo.maven.apache.org/maven2/",
    ] + DAGGER_REPOSITORIES,
    resolve_timeout = 1000,
    version_conflict_policy = "pinned",
)

load("@maven//:defs.bzl", "pinned_maven_install")

pinned_maven_install()

android_sdk_repository(
    name = "androidsdk",
    api_level = 33,
    build_tools_version = "33.0.1",
)

android_ndk_repository(
    name = "androidndk",
    api_level = 30,
)

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

git_repository(
    name = "tools_android",
    commit = "7224f55d7fafe12a72066eb1a2ad1e1526a854c4",
    remote = "https://github.com/bazelbuild/tools_android.git",
)

load("@tools_android//tools/googleservices:defs.bzl", "google_services_workspace_dependencies")

google_services_workspace_dependencies()

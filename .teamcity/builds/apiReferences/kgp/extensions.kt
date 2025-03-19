package builds.apiReferences.kgp

import BuildParams.KGP_ID
import builds.apiReferences.BuildApiPages
import builds.apiReferences.dependsOnDokkaTemplate
import builds.apiReferences.scriptBuildHtml
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

private class ReferenceVersion(val version: String, val branch: String)

private val KGP_VERSIONS = listOf(
    ReferenceVersion(version = "2.1.0", branch = "v2.1.0"),
    ReferenceVersion(version = "2.1.20-RC3", branch = "v2.1.20-RC3")
)

private const val KGP_API_OUTPUT_DIR = "libraries/tools/gradle/documentation/build/documentation/kotlinlang"
private const val KGP_API_TEMPLATES_DIR = "build/api-reference/templates"

private class KotlinReferencePages(
    apiId: String,
    version: String,
    tagOrBranch: String,
) : BuildApiPages(
    apiId = "$apiId/$version",
    releaseTag = tagOrBranch,
    pagesRoot = KGP_API_OUTPUT_DIR,
    vcsDefaultTrigger = { enabled = false },
    stepDropSnapshot = { null },
    stepBuildHtml = {
        val defaultStep = scriptBuildHtml()
        ScriptBuildStep {
            id = defaultStep.id
            name = defaultStep.name
            //language=bash
            scriptContent = """
                #!/bin/bash
                 set -e -u
                ./gradlew :gradle:documentation:dokkaKotlinlangDocumentation \
                    -PdeployVersion="$version" --no-daemon --no-configuration-cache
            """.trimIndent()
        }
    },
    init = {
        vcs {
            root(GitVcsRoot {
                id = RelativeId("KotlinGradlePlugin${version}VcsRoot")
                name = "$apiId ($version) VCS"
                url = "git@github.com:JetBrains/kotlin.git"

                branch = tagOrBranch
                useTagsAsBranches = true
                branchSpec = """
                    refs/heads/($tagOrBranch)
                    refs/tags/($tagOrBranch)
                """.trimIndent()

                authMethod = uploadedKey {
                    uploadedKey = "teamcity"
                }
            })
        }

        dependencies {
            dependsOnDokkaTemplate(KotlinGradlePluginPrepareDokkaTemplates, KGP_API_TEMPLATES_DIR)
        }
    })

fun Project.kotlinGradlePluginReferences() {
    subProject(Project {
        name = "Kotlin Gradle Plugin"

        KGP_VERSIONS.forEach {
            buildType(
                KotlinReferencePages(
                    apiId = KGP_ID,
                    version = it.version,
                    tagOrBranch = it.branch,
                )
            )
        }
    })
}
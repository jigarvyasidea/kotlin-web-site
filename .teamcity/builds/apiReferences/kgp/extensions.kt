package builds.apiReferences.kgp

import BuildParams.KGP_ID
import builds.apiReferences.BuildApiPages
import builds.apiReferences.dependsOnDokkaTemplate
import builds.apiReferences.scriptBuildHtml
import jetbrains.buildServer.configs.kotlin.Id
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

fun Project.kotlinGradlePluginReferences() {
    subProject {
        id = RelativeId("KotlinGradlePluginProject")
        name = "Kotlin Gradle Plugin"

        val apiId = KGP_ID

        KGP_VERSIONS.forEach {
            val tagOrBranch = it.branch
            val version = it.version

            val itemId = apiId.split("-").joinToString("") { it.capitalize() }
            val itemTcId = "${this@subProject.id}_${itemId}_${
                version.replace(".", "").split("-").joinToString("") { it.capitalize() }
            }"

            val vcs = GitVcsRoot {
                id = RelativeId("${itemTcId}Vcs")
                name = "$itemId $version"
                url = "git@github.com:JetBrains/kotlin.git"

                branch = "refs/${if (tagOrBranch.startsWith("v")) "tags" else "heads"}/$tagOrBranch"
                useTagsAsBranches = true
                branchSpec = ""

                authMethod = uploadedKey {
                    uploadedKey = "teamcity"
                }
            }

            vcsRoot(vcs)

            buildType(object : BuildApiPages(
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
                    name = "$version Pages"
                    vcs { root(vcs) }
                    dependencies {
                        dependsOnDokkaTemplate(KotlinGradlePluginPrepareDokkaTemplates, KGP_API_TEMPLATES_DIR)
                    }
                }) {
                override var id: Id? = RelativeId("${itemTcId}Build")
            })
        }
    }
}

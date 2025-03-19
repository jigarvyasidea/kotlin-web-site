import builds.apiReferences.BuildApiPages
import builds.apiReferences.dependsOnDokkaTemplate
import builds.apiReferences.kgp.KotlinGradlePluginPrepareDokkaTemplates
import builds.apiReferences.scriptBuildHtml
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

private fun String.camelCase(delim: String = "-", join: String = "") =
    this.split(delim).joinToString(join) { it.capitalize() }

class ReferenceVersion(
    val version: String, val tagOrBranch: String, val vcsUrl: String, val templateDir: String, val outputDir: String
)

interface APIReference {
    val id: String
    val versions: List<ReferenceVersion>
}

object VCS {
    fun tag(name: String) = "refs/tags/$name"
    fun branch(name: String) = "refs/heads/$name"
}

fun Project.kotlinApiReferences(api: APIReference) {
    val projectId = api.id.camelCase()

    subProject {
        id = RelativeId(projectId)
        name = api.id.camelCase(join = " ")

        for (ver in api.versions) {
            val version = ver.version

            val itemTcId = "${this@subProject.id}_${projectId}_${
                version.replace(".", "").camelCase()
            }"

            val vcs = GitVcsRoot {
                id = RelativeId("${itemTcId}Vcs")
                name = "$projectId $version"
                url = ver.vcsUrl

                branch = ver.tagOrBranch
                useTagsAsBranches = true
                branchSpec = ""

                authMethod = uploadedKey {
                    uploadedKey = "teamcity"
                }
            }

            vcsRoot(vcs)

            buildType(object : BuildApiPages(
                apiId = api.id,
                releaseTag = ver.version,
                pagesRoot = ver.outputDir,
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
                    id = RelativeId("${itemTcId}Build")
                    name = "$version pages"
                    vcs { root(vcs) }
                    dependencies {
                        dependsOnDokkaTemplate(KotlinGradlePluginPrepareDokkaTemplates, ver.templateDir)
                    }
                }) {})
        }
    }
}

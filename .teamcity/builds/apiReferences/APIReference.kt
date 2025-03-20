import builds.TemplateSearchIndex
import builds.apiReferences.BuildApiPages
import builds.apiReferences.dependsOnDokkaTemplate
import builds.apiReferences.scriptBuildHtml
import builds.apiReferences.templates.PrepareDokkaTemplate
import jetbrains.buildServer.configs.kotlin.*
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

fun buildVcsRoot(projectPrefix: String, projectName: String, version: ReferenceVersion) = GitVcsRoot {
    id = RelativeId("${projectPrefix}Vcs")
    name = "$projectName ${version.version}"
    url = version.vcsUrl

    branch = version.tagOrBranch
    useTagsAsBranches = true
    branchSpec = ""

    authMethod = uploadedKey {
        uploadedKey = "teamcity"
    }
}

fun buildTemplateBuild(projectPrefix: String, projectName: String, version: ReferenceVersion, algoliaIndex: String) =
    BuildType {
        id = RelativeId("${projectPrefix}Vcs")
        name = "${version.version} templates"
        description = "Build Dokka Templates for $projectName"

        templates(PrepareDokkaTemplate)

        params {
            param("env.ALGOLIA_INDEX_NAME", algoliaIndex)
        }
    }

fun buildPagesBuild(
    projectPrefix: String,
    api: APIReference,
    vcs: VcsRoot,
    templateBuild: BuildType,
    version: ReferenceVersion,
) = BuildApiPages(
    apiId = api.id,
    releaseTag = version.version,
    pagesRoot = version.outputDir,
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
                    -PdeployVersion="${version.version}" --no-daemon --no-configuration-cache
            """.trimIndent()
        }
    },
    init = {
        id = RelativeId("${projectPrefix}Build")
        name = "${version.version} pages"
        vcs { root(vcs) }
        dependencies {
            dependsOnDokkaTemplate(templateBuild, version.templateDir)
        }
    })


fun buildIndexBuild(
    projectName: String, projectPrefix: String, api: APIReference, pagesBuild: BuildType
): TemplateSearchIndex = TemplateSearchIndex {
    id = RelativeId("${projectPrefix}Search")
    name = "$projectName search"
    description = "Build search index for $projectName"

    params {
        param("env.ALGOLIA_INDEX_NAME", api.id)
    }

    dependencies {
        dependency(pagesBuild) {
            snapshot {}
            artifacts {
                artifactRules = """
                        pages.zip!** => dist/api/${api.id}
                    """.trimIndent()
                cleanDestination = true
            }
        }
    }
}

fun Project.kotlinApiReferences(
    api: APIReference,
    buildVcs: (String, String, ReferenceVersion) -> VcsRoot = ::buildVcsRoot,
    buildTemplate: (String, String, ReferenceVersion, String) -> BuildType = ::buildTemplateBuild,
    buildPages: (String, APIReference, VcsRoot, BuildType, ReferenceVersion) -> BuildType = ::buildPagesBuild,
    buildIndex: (String, String, APIReference, BuildType) -> BuildType = ::buildIndexBuild
) {
    val projectId = api.id.camelCase()
    val projectRelativeId = RelativeId(projectId)
    val projectName = api.id.camelCase(join = " ")

    subProject {
        id = projectRelativeId
        name = projectName

        val latestVersion = api.versions.lastOrNull() ?: error("No versions found for ${api.id}")
        var oldVersions = Dependencies()

        for (version in api.versions) {
            val projectPrefix = "${this@subProject.id}_${projectId}_${
                version.version.replace(".", "").camelCase()
            }"

            val vcs = buildVcs(projectPrefix, projectId, version)
            val tmplBuild = buildTemplate(projectPrefix, projectName, version, api.id)
            val pagesBuild = buildPages(projectPrefix, api, vcs, tmplBuild, version)

            vcsRoot(vcs)
            buildType(tmplBuild)
            buildType(pagesBuild)

            if (latestVersion == version) {
                buildType(buildIndex(projectName, projectRelativeId.value, api, pagesBuild))

                oldVersions.copyTo(pagesBuild.dependencies)
                oldVersions = pagesBuild.dependencies
            } else {
                oldVersions.dependency(pagesBuild) {
                    snapshot {}
                    artifacts {
                        artifactRules = """
                            pages.zip!** => build/documentation/kotlinlangOld/${version.version}
                        """.trimIndent()
                        cleanDestination = true
                    }
                }
            }
        }
    }
}

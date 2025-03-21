package builds.apiReferences

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId

private fun String.camelCase(delim: String = "-", join: String = "") =
    this.split(delim).joinToString(join) { it.capitalize() }

typealias ProjectReferenceAttachBuild = (project: Project, version: String) -> BuildType

open class ReferenceProject(val urlPart: String) {
    init {
        if (urlPart.isBlank()) throw IllegalArgumentException("urlPart cannot be blank")
    }

    val projectName = urlPart.camelCase(join = " ")

    val project = Project {
        id = RelativeId(urlPart.camelCase())
        name = projectName
        description = "Project for https://kotlinlang.org/api/$urlPart/"
    }

    protected val versions = mutableListOf<Pair<String, BuildType>>()

    fun getCurrentVersion(): BuildType? = this.versions.lastOrNull()?.second

    fun addReference(version: String, buildReference: ProjectReferenceAttachBuild) {
        versions.add(version to buildReference(this.project, version))
    }

    fun build() {
        val currentVersion = getCurrentVersion()
        if (currentVersion == null) throw IllegalStateException("Current version is not set for $projectName")

        project.apply {
            buildType {
                id = RelativeId("Latest")
                name = "$projectName Latest"
                description = "The latest stable version for $projectName"

                type = Type.COMPOSITE

                dependencies {
                    dependency(currentVersion) {
                        snapshot {}
                    }
                }
            }

            buildType {
                id = RelativeId("Search")
                name = "$projectName search"
                description = "Build search index for $projectName"

                params {
                    param("env.ALGOLIA_INDEX_NAME", urlPart)
                }

                dependencies {
                    dependency(currentVersion) {
                        snapshot {}
                        artifacts {
                            artifactRules = """
                                pages.zip!** => dist/api/${urlPart}
                            """.trimIndent()
                            cleanDestination = true
                        }
                    }
                }
            }
        }
    }
}

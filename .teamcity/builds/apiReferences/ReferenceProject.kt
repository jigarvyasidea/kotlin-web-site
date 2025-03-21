package builds.apiReferences

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId
import vcsRoots.KotlinLangOrg

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

        params {
            param("PAGES_DIR", DEFAULT_DOKKA_PATH)
            param("env.ALGOLIA_INDEX_NAME", urlPart)
        }
    }

    protected val versions = mutableListOf<Pair<BuildType, String>>()

    fun getCurrentVersion(): Pair<BuildType, String>? = this.versions.lastOrNull()

    fun addReference(version: String, buildReference: ProjectReferenceAttachBuild) {
        versions.add(buildReference(this.project, version) to version)
    }

    fun build() {
        val (currentVersion) = getCurrentVersion()
            ?: throw IllegalStateException("Current version is not set for $projectName")

        project.apply {
            buildType {
                id = RelativeId("Latest")
                name = "API Pages"
                description = "The latest stable version for $projectName"

                vcs {
                    root(KotlinLangOrg)
                }

                steps {
                    step(scriptNoRobots("%PAGES_DIR%"))
                    step(scriptGenerateSitemap("%PAGES_DIR%"))
                }

                dependencies {
                    dependency(currentVersion) {
                        snapshot {}
                    }
                }
            }

            buildType {
                id = RelativeId("Search")
                name = "API Search Index"
                description = "Build search index for $projectName"

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

        currentVersion.apply {
            dependencies {
                for ((previousVersion, version) in versions) {
                    if (previousVersion == currentVersion) continue
                    artifacts(previousVersion) {
                        buildRule = tag("release")
                        artifactRules = """
                            pages.zip!** => %OLD_VERSIONS_DIR%/$version/
                        """.trimIndent()
                        cleanDestination = true
                    }
                }
            }
        }
    }
}

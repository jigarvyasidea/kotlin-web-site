package builds.apiReferences.kgp

import builds.apiReferences.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

private const val REPO = "https://github.com/JetBrains/kotlin.git"
private const val OUTPUT_DIR = "libraries/tools/gradle/documentation/build/documentation/kotlinlang"
private const val TEMPLATES_DIR = "build/api-reference/templates"
private const val PREVIOUS_DIR = "libraries/tools/gradle/documentation/build/documentation/kotlinlangOld"

class KotlinGradleAPI(init: KotlinGradleAPI.() -> Unit) : ReferenceProject("kotlin-gradle-plugin") {
    init {
        project.params {
            param("PAGES_DIR", OUTPUT_DIR)
        }
        init()
        build()
    }

    fun addReference(version: String, tagsOrBranch: String) {
        addReference(version) { project, version ->
            val vcs = makeReferenceVcs(version, REPO, tagsOrBranch)
            val template = TemplateDep(TEMPLATES_DIR, makeReferenceTemplate(version, urlPart))
            val pages = makeReferencePages(version, vcs, template) {
                script {
                    name = "Build API reference pages"
                    //language=bash
                    scriptContent = """
                        #!/bin/bash
                        set -e -u
                        ./gradlew :gradle:documentation:dokkaKotlinlangDocumentation \
                            -PdeployVersion="$version" --no-daemon --no-configuration-cache
                    """.trimIndent()
                }
            }

            pages.params {
                param("OLD_VERSIONS_DIR", PREVIOUS_DIR)
            }

            pages
        }
    }
}

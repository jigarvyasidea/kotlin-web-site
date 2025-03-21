package builds.apiReferences.kgp

import builds.apiReferences.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

private const val REPO = "https://github.com/JetBrains/kotlin.git"
private const val OUTPUT_DIR = "libraries/tools/gradle/documentation/build/documentation/kotlinlang"
private const val TEMPLATES_DIR = "build/api-reference/templates"

class KGPReference(init: KGPReference.() -> Unit) : ReferenceProject("kotlin-gradle-plugin") {
    init {
        init()
    }

    fun addReference(version: String, tagsOrBranch: String) {
        addReference(version) { project, version ->
            val vcs = makeReferenceVcs(version, REPO, tagsOrBranch)
            val template = TemplateDep(TEMPLATES_DIR, makeReferenceTemplate(version, urlPart))

            makeReferencePages(version, OUTPUT_DIR, vcs, template) {
                script {
                    //language=bash
                    scriptContent = """
                        #!/bin/bash
                        set -e -u
                        ./gradlew :gradle:documentation:dokkaKotlinlangDocumentation \
                            -PdeployVersion="$version" --no-daemon --no-configuration-cache
                    """.trimIndent()
                }
            }
        }
    }
}

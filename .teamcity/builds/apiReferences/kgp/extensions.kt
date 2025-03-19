package builds.apiReferences.kgp

import APIReference
import ReferenceVersion

private const val KGP_API_OUTPUT_DIR = "libraries/tools/gradle/documentation/build/documentation/kotlinlang"
private const val KGP_API_TEMPLATES_DIR = "build/api-reference/templates"

class KGPReference : APIReference {
    override val id = "kotlin-gradle-plugin"
    override val versions = mutableListOf<ReferenceVersion>()
}

fun KGPReference.addVersion(
    version: String,
    branch: String,
    vcsUrl: String = "https://github.com/JetBrains/kotlin.git",
    templateDir: String = KGP_API_TEMPLATES_DIR,
    outputDir: String = KGP_API_OUTPUT_DIR
) {
    this.versions.add(
        ReferenceVersion(
            version, branch, vcsUrl, templateDir, outputDir
        )
    )
}

fun kgpReference(init: KGPReference.() -> Unit): KGPReference {
    val instance = KGPReference()
    instance.init()
    return instance
}

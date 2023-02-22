import com.google.gson.Gson
import com.google.gson.JsonObject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.util.regex.Pattern

class NpmPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = createExtension(project)

        // Register npm install and configuring it to be cached when possible
        project.tasks.register<NpmInstallTask>("npmInstall") {
            inputs.file(extension.packageJson.get())
            outputs.dir(extension.nodeModules.get())
        }

        // Register service to be able to launch and kill npm processes / subprocess
        val serviceProvider = project.gradle.sharedServices.registerIfAbsent("npmService", NpmService::class.java) {
            parameters.workingDir.set(extension.workingDir.get())
        }

        if (extension.includeAllScripts.get()) {
            // Read package json
            val packageJsonTxt = extension.packageJson.get().readText()
            val packageJson = Gson().fromJson(packageJsonTxt, JsonObject::class.java)
            val scripts = packageJson.get("scripts").asJsonObject

            // Register package.json scripts as gradle tasks
            scripts.keySet().forEach { command ->
                val task = project.tasks.register<NpmScriptTask>(command.toGradleName(), command)
                task.configure {
                    if (extension.taskDependingOnNpmInstall.get()) dependsOn("npmInstall")
                    group = extension.defaultTaskGroup.get()
                    getNpmService().set(serviceProvider)
                    usesService(serviceProvider)
                }
            }
        }
    }

    private fun createExtension(project: Project): NpmPluginExtension {
        val extension = project.extensions.create<NpmPluginExtension>("npm")
        extension.packageJson.convention(project.file("package.json"))
        extension.nodeModules.convention(project.file("node_modules"))
        extension.workingDir.convention(project.projectDir)
        extension.defaultTaskGroup.convention("scripts")
        extension.includeAllScripts.convention(true)
        extension.taskDependingOnNpmInstall.convention(true)
        return extension
    }

    // Pattern to use camel case instead of the following characters: [/, \, :, <, >, ", ?, *, |]
    private val pattern = Pattern.compile("[\\:\\<\\>\"?*|/\\\\]([a-z])")

    private fun String.toGradleName(): String {
        return pattern.matcher(this).replaceAll { it.group(1).uppercase() }
    }
}
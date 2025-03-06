package grails.views.gradle

import grails.views.gradle.util.GrailsNameUtils
import grails.views.gradle.util.SourceSets
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar

/**
 * Abstract implementation of a plugin that compiles views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AbstractGroovyTemplatePlugin implements Plugin<Project> {

    final Class<? extends AbstractGroovyTemplateCompileTask> taskClass
    final String fileExtension
    final String pathToSource

    AbstractGroovyTemplatePlugin(Class<? extends AbstractGroovyTemplateCompileTask> taskClass, String fileExtension) {
        this.taskClass = taskClass
        this.fileExtension = fileExtension
        this.pathToSource = 'grails-app/views'
    }

    AbstractGroovyTemplatePlugin(Class<? extends AbstractGroovyTemplateCompileTask> taskClass, String fileExtension, String pathToSource) {
        this.taskClass = taskClass
        this.fileExtension = fileExtension
        this.pathToSource = pathToSource
    }

    @Override
    @CompileDynamic
    void apply(Project project) {
        TaskContainer tasks = project.tasks
        String upperCaseName = GrailsNameUtils.getClassName(fileExtension)
        AbstractGroovyTemplateCompileTask templateCompileTask = (AbstractGroovyTemplateCompileTask) tasks.register(
                "compile${upperCaseName}Views".toString(),
                (Class<? extends Task>) taskClass
        ).get()
        SourceSetOutput output = SourceSets.findMainSourceSet(project)?.output
        FileCollection classesDir = resolveClassesDirs(output, project)
        File destDir = new File(project.layout.buildDirectory.get().asFile, "${templateCompileTask.fileExtension.get()}-classes/main")
        output?.dir(destDir)
        project.afterEvaluate {
            def grailsExt = project.extensions.findByName('grails')
            if (grailsExt?.pathingJar && Os.isFamily(Os.FAMILY_WINDOWS)) {
                Jar pathingJar = (Jar) tasks.named('pathingJar').get()
                ConfigurableFileCollection allClasspath = project.files(
                        "${project.layout.buildDirectory.get().asFile}/classes/groovy/main",
                        "${project.layout.buildDirectory.get().asFile}/resources/main",
                        "${project.layout.projectDirectory.getAsFile()}/gsp-classes",
                        pathingJar.archiveFile.get().asFile
                )
                templateCompileTask.dependsOn(pathingJar)
                templateCompileTask.classpath = allClasspath
            }
        }
        def allClasspath = classesDir + project.configurations.named('compileClasspath').get()
        templateCompileTask.destinationDirectory.set(destDir)
        templateCompileTask.classpath = allClasspath
        templateCompileTask.packageName.set(project.name)
        templateCompileTask.setSource(project.file("${project.projectDir}/$pathToSource"))
        templateCompileTask.dependsOn(tasks.named('classes').get())
        tasks.withType(Jar).configureEach { Task task ->
            if (task.name in ['jar', 'bootJar', 'war', 'bootWar']) {
                task.dependsOn(templateCompileTask)
            }
        }
        tasks.named('resolveMainClassName').configure { Task task ->
            task.dependsOn(templateCompileTask)
        }
        tasks.named('compileIntegrationTestGroovy').configure { Task task ->
            task.dependsOn(templateCompileTask)
        }
        tasks.named('integrationTest').configure { Task task ->
            task.dependsOn(templateCompileTask)
        }
    }

    @CompileDynamic
    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        return output.classesDirs ?: project.files(new File(project.layout.buildDirectory.get().asFile, 'classes/groovy/main'))
    }
}

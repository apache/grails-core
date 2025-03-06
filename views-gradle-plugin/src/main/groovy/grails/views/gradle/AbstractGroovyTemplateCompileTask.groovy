package grails.views.gradle

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import org.gradle.work.InputChanges

import javax.inject.Inject

/**
 * Abstract Gradle task for compiling templates, using GenericGroovyTemplateCompiler
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class AbstractGroovyTemplateCompileTask extends AbstractCompile {

    @Input
    @Optional
    final Property<String> packageName

    @InputDirectory
    final DirectoryProperty srcDir

    @Nested
    final ViewCompileOptions compileOptions

    @Input
    final Property<String> fileExtension

    @Input
    final Property<String> scriptBaseName

    @Input
    final Property<String> compilerName

    @Inject
    AbstractGroovyTemplateCompileTask(ObjectFactory objectFactory) {
        packageName = objectFactory.property(String).convention(project.name ?: project.projectDir.canonicalFile.name)
        srcDir = objectFactory.directoryProperty()
        compileOptions = new ViewCompileOptions(objectFactory)
        fileExtension = objectFactory.property(String)
        scriptBaseName = objectFactory.property(String)
        compilerName = objectFactory.property(String)
    }

    @Override
    void setSource(Object source) {
        srcDir.set(project.layout.projectDirectory.dir(source.toString()))
        if (!srcDir.getAsFile().get().isDirectory()) {
            throw new IllegalArgumentException("The source for GSP compilation must be a single directory, but was $source")
        }
        super.setSource(source)
    }

    @TaskAction
    void execute(InputChanges inputs) {
        compile()
    }

    protected void compile() {
        Iterable<String> projectPackageNames = getProjectPackageNames(project.projectDir)

        ExecResult result = project.javaexec(
                new Action<JavaExecSpec>() {
                    @Override @CompileDynamic
                    void execute(JavaExecSpec javaExecSpec) {
                        javaExecSpec.mainClass.set(compilerName)
                        javaExecSpec.classpath = classpath

                        List<String> jvmArgs = compileOptions.forkOptions.jvmArgs
                        if (jvmArgs) {
                            javaExecSpec.jvmArgs(jvmArgs)
                        }
                        javaExecSpec.maxHeapSize = compileOptions.forkOptions.memoryMaximumSize
                        javaExecSpec.minHeapSize = compileOptions.forkOptions.memoryInitialSize

                        String packageImports = projectPackageNames.join(',') ?: packageName.get()
                        List<String> arguments = [
                                srcDir.get().asFile.canonicalPath,
                                destinationDirectory.get().asFile.canonicalPath,
                                targetCompatibility,
                                packageImports,
                                packageName.get(),
                                project.file('grails-app/conf/application.yml').canonicalPath,
                                compileOptions.encoding.get()
                        ] as List<String>

                        prepareArguments(arguments)
                        javaExecSpec.args(arguments)
                    }
                }
        )
        result.assertNormalExitValue()
    }

    void prepareArguments(List<String> arguments) {
        // no-op
    }

    Iterable<String> getProjectPackageNames(File baseDir) {
        File rootDir = baseDir ? new File(baseDir, "grails-app${File.separator}domain") : null
        Set<String> packageNames = []
        if (rootDir?.exists()) {
            populatePackages(rootDir, packageNames, '')
        }
        return packageNames
    }

    protected populatePackages(File rootDir, Collection<String> packageNames, String prefix) {
        rootDir.eachDir { File dir ->
            String dirName = dir.name
            if (!dir.hidden && !dirName.startsWith('.')) {
                packageNames << "${prefix}${dirName}".toString()
                populatePackages(dir, packageNames, "${prefix}${dirName}.")
            }
        }
    }
}

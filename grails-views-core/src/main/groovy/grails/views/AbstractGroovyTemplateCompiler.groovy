package grails.views

import grails.views.compiler.ViewsTransform
import grails.views.resolve.GenericGroovyTemplateResolver
import groovy.io.FileType
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.control.io.FileReaderSource
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.CompletionService
import java.util.concurrent.Future
/**
 * A generic compiler for Groovy templates that are compiled into classes in production
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class AbstractGroovyTemplateCompiler {

    @Delegate CompilerConfiguration configuration = new CompilerConfiguration()

    String packageName = ""
    File sourceDir
    ViewConfiguration viewConfiguration

    AbstractGroovyTemplateCompiler(ViewConfiguration configuration, File sourceDir) {
        this.viewConfiguration = configuration
        this.packageName = configuration.packageName
        this.sourceDir = sourceDir
        configureCompiler(this.configuration)
    }

    AbstractGroovyTemplateCompiler() {
    }

    protected CompilerConfiguration configureCompiler(CompilerConfiguration configuration) {
        configuration.compilationCustomizers.clear()

        ImportCustomizer importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports( viewConfiguration.packageImports )
        importCustomizer.addStaticStars( viewConfiguration.staticImports )

        configuration.addCompilationCustomizers(importCustomizer)
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(newViewsTransform()))
        return configuration
    }

    protected ViewsTransform newViewsTransform() {
        new ViewsTransform(viewConfiguration.extension)
    }

    void compile(List<File> sources) {
        
        ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*2)
        CompletionService completionService = new ExecutorCompletionService(threadPool);
        
        try {
            Integer collationLevel = Runtime.getRuntime().availableProcessors()*2
            if(sources.size() < collationLevel) {
                collationLevel = 1
            }
            configuration.setClasspathList(classpath)
            String pathToSourceDir = sourceDir.canonicalPath
            def collatedSources = sources.collate(collationLevel)
            List<Future<Boolean>> futures = []
            for(int index=0;index < collatedSources.size();index++) {
                def sourceFiles = collatedSources[index]
                futures.add(completionService.submit({ ->
                    CompilerConfiguration configuration = new CompilerConfiguration(this.configuration)
                    for(int viewIndex=0;viewIndex < sourceFiles.size();viewIndex++) {
                        File source = sourceFiles[viewIndex]
                        configureCompiler(configuration)
                        CompilationUnit unit = new CompilationUnit(configuration)
                        String pathToSource = source.canonicalPath
                        String path = pathToSource - pathToSourceDir
                        String templateName = GenericGroovyTemplateResolver.resolveTemplateName(
                                packageName, path
                        )
                        unit.addSource(new SourceUnit(
                                templateName,
                                new FileReaderSource(source, configuration),
                                configuration,
                                unit.classLoader,
                                unit.errorCollector
                        ))
                        unit.compile()
                    }
                    return true
                } as Callable) as Future<Boolean>)
            }

            int pending = futures.size()
                
            while (pending > 0) {
                // Wait for up to 100ms to see if anything has completed.
                // The completed future is returned if one is found; otherwise null.
                // (Tune 100ms as desired)
                def completed = completionService.poll(100, TimeUnit.MILLISECONDS);
                if (completed != null) {
                    Boolean response = completed.get() as Boolean//need this to throw exceptions on main thread it seems
                    --pending;
                }
            } 
        }     
        finally {
                threadPool.shutdown()
        }
                
        

    }

    void compile(File...sources) {
        compile Arrays.asList(sources)
    }

    static void run(String[] args, Class<? extends GenericViewConfiguration> configurationClass, Class<? extends AbstractGroovyTemplateCompiler> compilerClass) {
        if(args.length != 7) {
            System.err.println("Invalid arguments: [${args.join(',')}]")
            System.err.println("""
Usage: java -cp CLASSPATH ${compilerClass.name} [srcDir] [destDir] [targetCompatibility] [packageImports] [packageName] [configFile] [encoding]
""")
            System.exit(1)
        }
        File srcDir = new File(args[0])
        File destinationDir = new File(args[1])
        String targetCompatibility = args[2]
        String[] packageImports = args[3].trim().split(',')
        String packageName = args[4].trim()
        File configFile = new File(args[5])
        String encoding = new File(args[6])

        GenericViewConfiguration configuration = configurationClass.getDeclaredConstructor().newInstance()
        configuration.packageName = packageName
        configuration.encoding = encoding
        configuration.packageImports = packageImports

        configuration.readConfiguration(configFile)

        AbstractGroovyTemplateCompiler compiler = compilerClass.getDeclaredConstructor(ViewConfiguration, File).newInstance(configuration, srcDir)
        compiler.setTargetDirectory( destinationDir )
        compiler.setSourceEncoding( configuration.encoding )
        if(targetCompatibility != null) {
            compiler.setTargetBytecode( targetCompatibility )
        }

        String fileExtension = configuration.extension
        compiler.setDefaultScriptExtension(fileExtension)

        List<File> allFiles = []
        srcDir.eachFileRecurse(FileType.FILES) { File f ->
            if(f.name.endsWith(fileExtension)) {
                allFiles.add(f)
            }
        }
        compiler.compile(allFiles)
    }
}

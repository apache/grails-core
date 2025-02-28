package grails.boot

import grails.compiler.ast.ClassInjector
import grails.core.GrailsApplication
import grails.io.IOUtils
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.util.BuildSettings
import grails.util.Environment
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.grails.boot.internal.JavaCompiler
import org.grails.compiler.injection.AbstractGrailsArtefactTransformer
import org.grails.compiler.injection.GrailsAwareInjectionOperation
import org.grails.core.util.BeanCreationProfilingPostProcessor
import org.grails.io.watch.DirectoryWatcher
import org.grails.io.watch.FileExtensionFileChangeListener
import org.grails.plugins.BinaryGrailsPlugin
import org.grails.plugins.support.WatchPattern
import org.springframework.boot.ResourceBanner
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.context.WebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.ResourceLoader

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Extends the {@link SpringApplication} with reloading behavior and other Grails features
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@Slf4j
@CompileStatic
class GrailsApp extends SpringApplication {

    private static final String GRAILS_BANNER = 'grails-banner.txt'
    private static final String SPRING_PROFILES = 'spring.profiles.active'

    private static boolean developmentModeActive = false
    private static DirectoryWatcher directoryWatcher

    boolean enableBeanCreationProfiler = false
    ConfigurableEnvironment configuredEnvironment

    /**
     * Create a new {@link GrailsApp} instance. The application context will load
     * beans from the specified sources (see {@link SpringApplication class-level}
     * documentation for details. The instance can be customized before calling
     * {@link #run(String...)}.
     * @param sources the bean sources
     * @see #run(Object, String[])
     * @see #GrailsApp(org.springframework.core.io.ResourceLoader, Class<?>...)
     */
    GrailsApp(Class<?>... sources) {
        super(sources)
    }

    /**
     * Create a new {@link GrailsApp} instance. The application context will load
     * beans from the specified sources (see {@link SpringApplication class-level}
     * documentation for details. The instance can be customized before calling
     * {@link #run(String...)}.
     * @param resourceLoader the resource loader to use
     * @param sources the bean sources
     * @see #run(Object, String[])
     * @see #GrailsApp(org.springframework.core.io.ResourceLoader, Class<?>...)
     */
    GrailsApp(ResourceLoader resourceLoader, Class<?>... sources) {
        super(resourceLoader, sources)
    }

    @Override
    ConfigurableApplicationContext run(String... args) {
        ConfigurableApplicationContext applicationContext = super.run(args)
        Environment environment = Environment.getCurrent()

        log.info("Application starting in environment: {}", environment.getName())
        log.debug("Application directory discovered as: {}", IOUtils.findApplicationDirectory())
        log.debug("Current base directory is [{}]. Reloading base directory is [{}]", new File("."), BuildSettings.BASE_DIR)

        if (environment.isReloadEnabled()) {
            log.debug("Reloading status: {}", environment.isReloadEnabled())
            enableDevelopmentModeWatch(environment, applicationContext)
            environment.isDevtoolsRestart()
        }

        printRunStatus(applicationContext)
        return applicationContext
    }

    @Override
    protected ConfigurableApplicationContext createApplicationContext() {
        setAllowBeanDefinitionOverriding(true)
        setAllowCircularReferences(true)
        ConfigurableApplicationContext applicationContext = super.createApplicationContext()

        if (enableBeanCreationProfiler) {
            def processor = new BeanCreationProfilingPostProcessor()
            applicationContext.getBeanFactory().addBeanPostProcessor(processor)
            applicationContext.addApplicationListener(processor)
        }
        return applicationContext
    }

    @Override
    protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
        configurePropertySources(environment, args)

        String[] springProfile = environment.getProperty(SPRING_PROFILES, String[])
        if (springProfile) {
            environment.setActiveProfiles(springProfile)
        }

        def env = Environment.current
        environment.addActiveProfile(env.name)
        configuredEnvironment = environment
    }

    @CompileDynamic // TODO: Report Groovy VerifierError
    protected void enableDevelopmentModeWatch(Environment environment, ConfigurableApplicationContext applicationContext, String... args) {
        def location = environment.getReloadLocation()

        if (location) {
            directoryWatcher = new DirectoryWatcher()

            Queue<File> changedFiles = new ConcurrentLinkedQueue<>()
            Queue<File> newFiles = new ConcurrentLinkedQueue<>()

            directoryWatcher.addListener(new FileExtensionFileChangeListener(['groovy', 'java']) {
                @Override
                void onChange(File file, List<String> extensions) {
                    changedFiles << file.canonicalFile
                }

                @Override
                void onNew(File file, List<String> extensions) {
                    changedFiles << file.canonicalFile
                    // For some bizarre reason Windows fires onNew events even for files that have
                    // just been modified and not created
                    if (System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
                        return
                    }
                    newFiles << file.canonicalFile
                }
            })

            def pluginManager = applicationContext.getBean(GrailsPluginManager)
            def pluginManagerListener = createPluginManagerListener(applicationContext)
            directoryWatcher.addListener(pluginManagerListener)

            File baseDir = new File(location).canonicalFile
            String baseDirPath = baseDir.canonicalPath
            List<File> watchBaseDirectories = [baseDir]
            for (GrailsPlugin plugin in pluginManager.allPlugins) {
                if (plugin instanceof BinaryGrailsPlugin) {
                    BinaryGrailsPlugin binaryGrailsPlugin = (BinaryGrailsPlugin) plugin
                    def pluginDirectory = binaryGrailsPlugin.projectDirectory
                    if (pluginDirectory != null) {
                        watchBaseDirectories << pluginDirectory
                    }
                }
            }

            for (dir in watchBaseDirectories) {
                configureDirectoryWatcher(directoryWatcher, dir.absolutePath)
            }

            for (GrailsPlugin plugin in pluginManager.allPlugins) {
                def watchedResourcePatterns = plugin.getWatchedResourcePatterns()
                if (watchedResourcePatterns != null) {

                    for (WatchPattern wp in new ArrayList<WatchPattern>(watchedResourcePatterns)) {
                        boolean first = true
                        for (watchBase in watchBaseDirectories) {
                            if (!first) {
                                if (wp.file != null) {
                                    String relativePath = wp.file.canonicalPath - baseDirPath
                                    File watchFile = new File(watchBase, relativePath)
                                    // the base project will already been in the list of watch patterns, but we add any subprojects here
                                    plugin.watchedResourcePatterns.add(new WatchPattern(file: watchFile, extension: wp.extension))
                                } else if (wp.directory != null) {
                                    String relativePath = wp.directory.canonicalPath - baseDirPath
                                    File watchDir = new File(watchBase, relativePath)
                                    // the base project will already been in the list of watch patterns, but we add any subprojects here
                                    plugin.watchedResourcePatterns.add(new WatchPattern(directory: watchDir, extension: wp.extension))
                                }
                            }
                            first = false
                            if (wp.file) {
                                String relativePath = wp.file.canonicalPath - baseDirPath
                                def resolvedPath = new File(watchBase, relativePath)
                                directoryWatcher.addWatchFile(resolvedPath)
                            } else if (wp.directory && wp.extension) {
                                String relativePath = wp.directory.canonicalPath - baseDirPath
                                def resolvedPath = new File(watchBase, relativePath)
                                directoryWatcher.addWatchDirectory(resolvedPath, wp.extension)
                            }
                        }
                    }
                }
            }


            developmentModeActive = true
            Thread.start {
                CompilerConfiguration compilerConfig = new CompilerConfiguration()
                compilerConfig.setTargetDirectory(new File(location, BuildSettings.BUILD_CLASSES_PATH))

                while (isDevelopmentModeActive()) {
                    // Workaround for some IDE / OS combos - 2 events (new + update) for the same file
                    def uniqueChangedFiles = changedFiles as Set


                    def i = uniqueChangedFiles.size()
                    try {
                        if (i > 1) {
                            changedFiles.clear()
                            for (f in uniqueChangedFiles) {
                                recompile(f, compilerConfig, location)
                                if (newFiles.contains(f)) {
                                    newFiles.remove(f)
                                }
                                pluginManager.informOfFileChange(f)
                                sleep 1000
                            }
                        } else if (i == 1) {
                            changedFiles.clear()
                            def changedFile = uniqueChangedFiles[0]
                            changedFile = changedFile.canonicalFile
                            // Groovy files within the 'conf' directory are not compiled
                            String confPath = "${File.separator}grails-app${File.separator}conf${File.separator}"
                            if (changedFile.path.contains(confPath)) {
                                pluginManager.informOfFileChange(changedFile)
                            } else {
                                recompile(changedFile, compilerConfig, location)
                                if (newFiles.contains(changedFile)) {
                                    newFiles.remove(changedFile)
                                }
                                pluginManager.informOfFileChange(changedFile)
                            }
                        }

                        newFiles.clear()
                    } catch (CompilationFailedException cfe) {
                        log.error("Compilation Error: $cfe.message", cfe)
                    }

                    sleep(1000)
                }

            }
            directoryWatcher.start()
        }
    }

    static boolean isDevelopmentModeActive() {
        return developmentModeActive
    }

    static void setDevelopmentModeActive(boolean active) {
        developmentModeActive = active
        if (directoryWatcher != null) {
            directoryWatcher.active = active
        }
    }

    protected void recompile(File changedFile, CompilerConfiguration compilerConfig, String location) {
        File appDir = null
        def changedPath = changedFile.path

        String grailsAppDir = "${File.separator}grails-app"
        String sourceMainGroovy = "${File.separator}src${File.separator}main${File.separator}groovy"

        if (changedPath.contains(grailsAppDir)) {
            appDir = new File(changedPath.substring(0, changedPath.indexOf(grailsAppDir)))
        } else if (changedPath.contains(sourceMainGroovy)) {
            appDir = new File(changedPath.substring(0, changedPath.indexOf(sourceMainGroovy)))
        }
        def baseFileLocation = appDir?.absolutePath ?: location
        compilerConfig.setTargetDirectory(new File(baseFileLocation, BuildSettings.BUILD_CLASSES_PATH))
        println "File $changedFile changed, recompiling..."
        if (changedFile.name.endsWith('.java')) {
            if (JavaCompiler.isAvailable()) {
                JavaCompiler.recompile(compilerConfig, changedFile)
            } else {
                log.error("Cannot recompile [$changedFile.name], the current JVM is not a JDK (recompilation will not work on a JRE missing the compiler APIs).")
            }
        } else {
            compileGroovyFile(compilerConfig, changedFile)
        }
    }

    protected void compileGroovyFile(CompilerConfiguration compilerConfig, File changedFile) {
        ClassInjector[] classInjectors = GrailsAwareInjectionOperation.getClassInjectors()
        for (ClassInjector classInjector in classInjectors) {
            if (classInjector instanceof AbstractGrailsArtefactTransformer) {
                ((AbstractGrailsArtefactTransformer) classInjector).clearCachedState()
            }
        }
        // only one change, just to a simple recompile and propagate the change
        def unit = new CompilationUnit(compilerConfig)
        unit.addSource(changedFile)
        unit.compile()
    }

    /**
     * Creates and returns a file change listener for notifying the plugin manager of changes.
     * @param applicationContext - The running {@link org.springframework.context.ApplicationContext}
     * @return {@link DirectoryWatcher.FileChangeListener}
     */
    protected static DirectoryWatcher.FileChangeListener createPluginManagerListener(ConfigurableApplicationContext applicationContext) {
        def pluginManager = applicationContext.getBean(GrailsPluginManager)
        return new DirectoryWatcher.FileChangeListener() {
            @Override
            void onChange(File file) {
                if (!file.name.endsWith('.groovy') && !file.name.endsWith('.java')) {
                    pluginManager.informOfFileChange(file)
                }
            }

            @Override
            void onNew(File file) {
                if (!file.name.endsWith('.groovy') && !file.name.endsWith('.java')) {
                    pluginManager.informOfFileChange(file)
                }
            }
        }
    }

    protected void configureDirectoryWatcher(DirectoryWatcher directoryWatcher, String location) {
        directoryWatcher.addWatchDirectory(new File(location, "grails-app"), ['groovy', 'java'])
        directoryWatcher.addWatchDirectory(new File(location, "src/main/groovy"), ['groovy', 'java'])
        directoryWatcher.addWatchDirectory(new File(location, "src/main/java"), ['groovy', 'java'])
    }

    protected printRunStatus(ConfigurableApplicationContext applicationContext) {
        try {
            GrailsApplication app = applicationContext.getBean(GrailsApplication)
            String protocol = app.config.getProperty('server.ssl.key-store') ? 'https' : 'http'
            String contextPath = app.config.getProperty('server.servlet.context-path', '')
            String hostName = app.config.getProperty('server.address', 'localhost')
            int port
            if (applicationContext instanceof WebServerApplicationContext) {
                port = applicationContext.webServer.port
            }
            println("Grails application running at ${protocol}://${hostName}:${port}${contextPath} in environment: ${Environment.current.name}")

        } catch (ignore) {
        }
    }

    /**
     * Static helper that can be used to run a {@link GrailsApp} from the
     * specified source using default settings.
     * @param source the source to load
     * @param args the application arguments (usually passed from a Java main method)
     * @return the running {@link org.springframework.context.ApplicationContext}
     */
    static ConfigurableApplicationContext run(Class<?> source, String... args) {
        return run([source] as Class[], args)
    }

    /**
     * Static helper that can be used to run a {@link GrailsApp} from the
     * specified sources using default settings and user supplied arguments.
     * @param sources the sources to load
     * @param args the application arguments (usually passed from a Java main method)
     * @return the running {@link org.springframework.context.ApplicationContext}
     */
    static ConfigurableApplicationContext run(Class<?>[] sources, String[] args) {
        GrailsApp grailsApp = new GrailsApp(sources)
        grailsApp.banner = new ResourceBanner(new ClassPathResource(GRAILS_BANNER))
        return grailsApp.run(args)
    }
}

/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.util

import grails.io.IOUtils
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.grails.io.support.Resource
import org.grails.io.support.UrlResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.util.function.Supplier
import java.util.jar.Attributes
import java.util.jar.Manifest

/**
 * Represents the current environment.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
enum Environment {

    /** The development environment */
    DEVELOPMENT,

    /** The production environment */
    PRODUCTION,

    /** The test environment */
    TEST,

    /**
     * For the application data source, primarily for backward compatibility for those applications
     * that use ApplicationDataSource.groovy.
     */
    APPLICATION,

    /** A custom environment */
    CUSTOM

    /**
     * Initialize the Logger lazily because:
     * https://github.com/apache/grails-core/issues/11476
     */
    private static final Supplier<Logger> LOG = SupplierUtil.memoized(() -> LoggerFactory.getLogger(Environment.class))

    /**
     * Constant used to resolve the environment via System.getProperty(Environment.KEY)
     */
    public static String KEY = "grails.env"

    /**
     * Constant used to resolve the environment via System.getenv(Environment.ENV_KEY).
     */
    public static final String ENV_KEY = "GRAILS_ENV"

    /**
     * The name of the GRAILS_HOME environment variable
     */
    public static String ENV_GRAILS_HOME = "GRAILS_HOME"
    /**
     * Specify whether reloading is enabled for this environment
     */
    public static String RELOAD_ENABLED = "grails.reload.enabled"

    /**
     * Constant indicating whether run-app or test-app was executed
     */
    public static String RUN_ACTIVE = "grails.run.active"

    /**
     * Whether the display of full stack traces is needed
     */
    public static String FULL_STACKTRACE = "grails.full.stacktrace"

    /**
     * The location where to reload resources from
     */
    public static final String RELOAD_LOCATION = "grails.reload.location"

    /**
     * Whether interactive mode is enabled
     */
    public static final String INTERACTIVE_MODE_ENABLED = "grails.interactive.mode.enabled"

    /**
     * Constants that indicates whether this GrailsApplication is running in the default environment
     */
    public static final String DEFAULT = "grails.env.default"

    /**
     * Whether Grails is in the middle of bootstrapping or not
     */
    public static final String INITIALIZING = "grails.env.initializing"

    /**
     * Whether Grails has been executed standalone via the static void main method and not loaded in via the container
     */
    public static final String STANDALONE = "grails.env.standalone"

    private static final String PRODUCTION_ENV_SHORT_NAME = "prod"

    private static final String DEVELOPMENT_ENVIRONMENT_SHORT_NAME = "dev"
    private static final String TEST_ENVIRONMENT_SHORT_NAME = "test"

    @SuppressWarnings("unchecked")
    private static Map<String, String> envNameMappings = CollectionUtils.<String, String> newMap(
            DEVELOPMENT_ENVIRONMENT_SHORT_NAME, DEVELOPMENT.getName(),
            PRODUCTION_ENV_SHORT_NAME, PRODUCTION.getName(),
            TEST_ENVIRONMENT_SHORT_NAME, TEST.getName())
    private static Holder<Environment> cachedCurrentEnvironment = new Holder<>("Environment")
    private static final boolean DEVELOPMENT_MODE = getCurrent() == DEVELOPMENT && BuildSettings.GRAILS_APP_DIR_PRESENT
    private static Boolean RELOADING_AGENT_ENABLED = null
    private static boolean initializingState = false

    private static final String GRAILS_IMPLEMENTATION_TITLE = "Grails"
    private static final String GRAILS_VERSION
    private static final boolean STANDALONE_DEPLOYED
    private static final boolean WAR_DEPLOYED

    static {
        Package p = Environment.class.getPackage()
        String version = p != null ? p.getImplementationVersion() : null
        if (version == null || isBlank(version)) {
            try {
                URL manifestURL = IOUtils.findResourceRelativeToClass(Environment.class, "/META-INF/MANIFEST.MF")
                Manifest grailsManifest = null
                if (manifestURL != null) {
                    Resource r = new UrlResource(manifestURL)
                    if (r.exists()) {
                        InputStream inputStream = null
                        Manifest mf = null
                        try {
                            inputStream = r.getInputStream()
                            mf = new Manifest(inputStream)
                        } finally {
                            try {
                                inputStream.close()
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                        String implTitle = mf.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_TITLE)
                        if (!isBlank(implTitle) && implTitle.equals(GRAILS_IMPLEMENTATION_TITLE)) {
                            grailsManifest = mf
                        }
                    }
                }

                if (grailsManifest != null) {
                    version = grailsManifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION)
                }

                if (isBlank(version)) {
                    version = "Unknown"
                }
            }
            catch (Exception e) {
                version = "Unknown"
            }
        }
        GRAILS_VERSION = version

        URL url = Environment.class.getResource("")
        if (url != null) {

            String protocol = url.getProtocol()
            if (protocol.equals("jar")) {
                String fullPath = url.toString()
                if (fullPath.contains(IOUtils.RESOURCE_WAR_PREFIX)) {
                    STANDALONE_DEPLOYED = true
                } else {
                    int i = fullPath.indexOf(IOUtils.RESOURCE_JAR_PREFIX)
                    if (i > -1) {
                        fullPath = fullPath.substring(i + IOUtils.RESOURCE_JAR_PREFIX.length())
                        STANDALONE_DEPLOYED = fullPath.contains(IOUtils.RESOURCE_JAR_PREFIX)

                    } else {
                        STANDALONE_DEPLOYED = false
                    }

                }
            } else {
                STANDALONE_DEPLOYED = false
            }
        } else {
            STANDALONE_DEPLOYED = false
        }

        URL loadedLocation = Environment.class.getClassLoader().getResource(Metadata.FILE)
        if (loadedLocation != null) {
            String path = loadedLocation.getPath()
            WAR_DEPLOYED = isWebPath(path)
        } else {

            loadedLocation = Thread.currentThread().getContextClassLoader().getResource(Metadata.FILE)
            if (loadedLocation != null) {
                String path = loadedLocation.getPath()
                WAR_DEPLOYED = isWebPath(path)
            } else {
                WAR_DEPLOYED = false
            }
        }
    }

    public static Throwable currentReloadError = null
    public static MultipleCompilationErrorsException currentCompilationError = null
    private String name
    private String reloadLocation

    Environment() {
        initialize()
    }

    /**
     * @return The current Grails version
     */
    static String getGrailsVersion() {
        return GRAILS_VERSION
    }

    static void setCurrentReloadError(Throwable currentReloadError) {
        Environment.@currentReloadError = currentReloadError
    }

    static MultipleCompilationErrorsException getCurrentCompilationError() {
        return currentCompilationError
    }

    static Throwable getCurrentReloadError() {
        return currentReloadError
    }

    static boolean isReloadInProgress() {
        return Boolean.getBoolean("grails.reloading.in.progress")
    }

    private void initialize() {
        name = toString().toLowerCase(Locale.ENGLISH)
    }

    /**
     * Returns the current environment which is typcally either DEVELOPMENT, PRODUCTION or TEST.
     * For custom environments CUSTOM type is returned.
     *
     * @return The current environment.
     */
    static Environment getCurrent() {
        String envName = getEnvironmentInternal()

        Environment env
        if (!isBlank(envName)) {
            env = getEnvironment(envName)
            if (env != null) {
                return env
            }
        }


        Environment current = cachedCurrentEnvironment.get()
        if (current != null) {
            return current
        }
        return cacheCurrentEnvironment()
    }

    private static Environment resolveCurrentEnvironment() {
        String envName = getEnvironmentInternal()

        if (isBlank(envName)) {
            Metadata metadata = Metadata.getCurrent()
            if (metadata != null) {
                envName = metadata.getEnvironment()
            }
            if (isBlank(envName)) {
                return DEVELOPMENT
            }
        }

        Environment env = getEnvironment(envName)
        if (env == null) {
            try {
                env = valueOf(envName.toUpperCase())
            }
            catch (IllegalArgumentException e) {
                // ignore
            }
        }
        if (env == null) {
            env = CUSTOM
            env.setName(envName)
        }
        return env
    }

    private static Environment cacheCurrentEnvironment() {
        Environment env = resolveCurrentEnvironment()
        cachedCurrentEnvironment.set(env)
        return env
    }

    /**
     * @see #getCurrent()
     * @return the current environment
     */
    static Environment getCurrentEnvironment() {
        return getCurrent()
    }

    /**
     * Reset the current environment
     */
    static void reset() {
        cachedCurrentEnvironment.set(null)
        Metadata.reset()
    }

    /**
     * Returns true if the application is running in development mode (within grails run-app)
     *
     * @return true if the application is running in development mode
     */

    static boolean isDevelopmentMode() {
        return DEVELOPMENT_MODE
    }

    /**
     * This method will return true if the 'grails-app' directory was found, regardless of whether reloading is active or not
     *
     * @return True if the development sources are present
     */
    static boolean isDevelopmentEnvironmentAvailable() {
        return BuildSettings.GRAILS_APP_DIR_PRESENT && !isStandaloneDeployed() && !isWarDeployed()
    }

    /**
     * This method will return true the application is run
     *
     * @return True if the development sources are present
     */
    static boolean isDevelopmentRun() {
        Environment env = getCurrent()
        return isDevelopmentEnvironmentAvailable() && Boolean.getBoolean(RUN_ACTIVE) && (env == Environment.DEVELOPMENT)
    }

    /**
     * Checks if the run of the app is due to spring dev-tools or not.
     * @return True if spring-dev-tools restart
     */
    static boolean isDevtoolsRestart() {
        File pidFile = new File(BuildSettings.TARGET_DIR.toString() + File.separator + ".grailspid")
        LOG.get().debug("Looking for pid file at: {}", pidFile)
        boolean isDevToolsRestart = false
        try {
            if (isDevelopmentMode()) {
                String pid = ManagementFactory.getRuntimeMXBean().getName()
                if (pidFile.exists()) {
                    if (pid.equals(Files.readAllLines(pidFile.toPath()).get(0))) {
                        LOG.get().debug("spring-dev-tools restart detected.")
                        isDevToolsRestart = true
                    } else {
                        LOG.get().debug("spring-dev-tools first app start - creating pid file.")
                        writeDevToolsPidFile(pidFile, pid)
                    }
                } else {
                    LOG.get().debug("spring-dev-tools pid file did not exist.")
                    writeDevToolsPidFile(pidFile, pid)
                }
            }
        } catch (Exception ex) {
            LOG.get().error("spring-dev-tools restart detection error: {}", ex)
        }
        LOG.get().debug("spring-dev-tools restart: {}", isDevToolsRestart)
        return isDevToolsRestart
    }

    private static void writeDevToolsPidFile(File pidFile, String content) {
        BufferedWriter writer = null
        try {
            writer = new BufferedWriter(new FileWriter(pidFile))
            writer.write(content)
        } catch (Exception ex) {
            LOG.get().error("spring-dev-tools restart unable to write pid file: {}", ex)
        } finally {
            try {
                if (writer != null) {
                    writer.flush()
                    writer.close()
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Check whether the application is deployed
     * @return true if is
     */
    static boolean isWarDeployed() {
        if (!isStandalone()) {
            return WAR_DEPLOYED
        }
        return false
    }

    private static boolean isWebPath(String path) {
        // Workaround for weblogic who repacks files from 'classes' into a new jar under lib/
        return path.contains("/WEB-INF/classes") || path.contains("_wl_cls_gen.jar!/")
    }

    /**
     * Whether the application has been executed standalone via static void main.
     *
     * This method will return true when the application is executed via `java -jar` or
     * if the application is run directly via the main method within an IDE
     *
     * @return True if it is running standalone outside of a servlet container
     */
    static boolean isStandalone() {
        return Boolean.getBoolean(STANDALONE)
    }

    /**
     * Whether the application is running standalone within a JAR
     *
     * This method will return true only if the the application is executed via `java -jar`
     * and not if it is run via the main method within an IDE
     *
     * @return True if it is running standalone outside a servlet container from within a JAR or WAR file
     */
    static boolean isStandaloneDeployed() {
        return isStandalone() && STANDALONE_DEPLOYED
    }

    /**
     * Whether this is a fork of the Grails command line environment
     * @return True if it is a fork
     */
    static boolean isFork() {
        return Boolean.getBoolean("grails.fork.active")
    }

    /**
     * Returns whether the environment is running within the Grails shell (executed via the 'grails' command line in a terminal window)
     * @return true if is
     */
    static boolean isWithinShell() {
        return DefaultGroovyMethods.getRootLoader(Environment.class.getClassLoader()) != null
    }

    /**
     * @return Return true if the environment has been set as a System property
     */
    static boolean isSystemSet() {
        return getEnvironmentInternal() != null
    }

    /**
     * Returns the environment for the given short name
     * @param shortName The short name
     * @return The Environment or null if not known
     */
    static Environment getEnvironment(String shortName) {
        final String envName = envNameMappings.get(shortName)
        if (envName != null) {
            return valueOf(envName.toUpperCase())
        } else {
            try {
                return valueOf(shortName.toUpperCase())
            } catch (IllegalArgumentException ise) {
                return null
            }
        }
    }

    /**
     * Takes an environment specific DSL block like:
     *
     * <code>
     * environments {
     *      development {}
     *      production {}
     * }
     * </code>
     *
     * And returns the closure that relates to the current environment
     *
     * @param closure The top level closure
     * @return The environment specific block or null if non exists
     */
    static Closure<?> getEnvironmentSpecificBlock(Closure<?> closure) {
        final Environment env = getCurrent()
        return getEnvironmentSpecificBlock(env, closure)
    }

    /**
     * Takes an environment specific DSL block like:
     *
     * <code>
     * environments {
     *      development {}
     *      production {}
     * }
     * </code>
     *
     * And returns the closure that relates to the specified
     *
     * @param env The environment to use
     * @param closure The top level closure
     * @return The environment specific block or null if non exists
     */
    static Closure<?> getEnvironmentSpecificBlock(Environment env, Closure<?> closure) {
        if (closure == null) {
            return null
        }

        final EnvironmentBlockEvaluator evaluator = evaluateEnvironmentSpecificBlock(env, closure)
        return evaluator.getCallable()
    }

    /**
     * Takes an environment specific DSL block like:
     *
     * <code>
     * environments {
     *      development {}
     *      production {}
     * }
     * </code>
     *
     * And executes the closure that relates to the current environment
     *
     * @param closure The top level closure
     * @return The result of the closure execution
     */
    static Object executeForCurrentEnvironment(Closure<?> closure) {
        final Environment env = getCurrent()
        return executeForEnvironment(env, closure)
    }

    /**
     * Takes an environment specific DSL block like:
     *
     * <code>
     * environments {
     *      development {}
     *      production {}
     * }
     * </code>
     *
     * And executes the closure that relates to the specified environment
     *
     * @param env The environment to use
     * @param closure The top level closure
     * @return The result of the closure execution
     */
    static Object executeForEnvironment(Environment env, Closure<?> closure) {
        if (closure == null) {
            return null
        }

        final EnvironmentBlockEvaluator evaluator = evaluateEnvironmentSpecificBlock(env, closure)
        return evaluator.execute()
    }

    private static EnvironmentBlockEvaluator evaluateEnvironmentSpecificBlock(Environment environment, Closure<?> closure) {
        final EnvironmentBlockEvaluator evaluator = new EnvironmentBlockEvaluator(environment)
        closure.setDelegate(evaluator)
        closure.call()
        return evaluator
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0
    }

    /**
     * @return the name of the environment
     */
    String getName() {
        return name
    }

    /**
     * Set the name.
     * @param name the name
     */
    void setName(String name) {
        this.name = name
    }

    /**
     * @return Returns whether reload is enabled for the environment
     */
    boolean isReloadEnabled() {
        final boolean reloadOverride = Boolean.getBoolean(RELOAD_ENABLED)
        getReloadLocation()
        final boolean reloadLocationSpecified = hasLocation(reloadLocation)
        return this == DEVELOPMENT && reloadLocationSpecified ||
                reloadOverride && reloadLocationSpecified
    }

    /**
     *
     * @return Whether interactive mode is enabled
     */
    static boolean isInteractiveMode() {
        return Boolean.getBoolean(INTERACTIVE_MODE_ENABLED)
    }

    /**
     *
     * @return Whether interactive mode is enabled
     */
    static boolean isInitializing() {
        return initializingState
    }

    static void setInitializing(boolean initializing) {
        initializingState = initializing
        System.setProperty(INITIALIZING, String.valueOf(initializing))
    }

    /**
     * @return true if the reloading agent is active
     */
    static boolean isReloadingAgentEnabled() {
        if (RELOADING_AGENT_ENABLED != null) {
            return RELOADING_AGENT_ENABLED
        }
        try {
            Class.forName("org.springframework.boot.devtools.RemoteSpringApplication")
            RELOADING_AGENT_ENABLED = getCurrent().isReloadEnabled()
            LOG.get().debug("Found spring-dev-tools on the class path")
        }
        catch (ClassNotFoundException e) {
            RELOADING_AGENT_ENABLED = false
            try {
                Class.forName("org.springsource.loaded.TypeRegistry")
                String jvmVersion = System.getProperty("java.specification.version")
                LOG.get().debug("Found spring-loaded on the class path")
                RELOADING_AGENT_ENABLED = getCurrent().isReloadEnabled()
            }
            catch (ClassNotFoundException e1) {
                RELOADING_AGENT_ENABLED = false
            }
        }
        return RELOADING_AGENT_ENABLED
    }

    /**
     * @return Obtains the location to reload resources from
     */
    String getReloadLocation() {
        if (this.reloadLocation != null) {
            return this.reloadLocation
        }
        String location = getReloadLocationInternal()
        if (hasLocation(location)) {
            reloadLocation = location
            return location
        }
        return "." // default to the current directory
    }

    private boolean hasLocation(String location) {
        return location != null && location.length() > 0
    }

    /**
     * @return Whether a reload location is specified
     */
    boolean hasReloadLocation() {
        getReloadLocation()
        return hasLocation(reloadLocation)
    }

    private String getReloadLocationInternal() {
        String location = System.getProperty(RELOAD_LOCATION)
        if (!hasLocation(location)) {
            location = System.getProperty(BuildSettings.APP_BASE_DIR)
        }
        if (!hasLocation(location)) {
            File current = new File(".", "grails-app")
            if (current.exists()) {
                location = current.getParentFile().getAbsolutePath()
            } else {
                current = new File(".", "settings.gradle")
                if (current.exists()) {
                    // multi-project build
                    location = IOUtils.findApplicationDirectory()
                }
            }
        }
        return location
    }

    private static String getEnvironmentInternal() {
        String envName = System.getProperty(KEY)
        return isBlank(envName) ? System.getenv(ENV_KEY) : envName
    }

}

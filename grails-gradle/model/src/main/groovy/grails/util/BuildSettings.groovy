/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import java.util.regex.Pattern

/**
 * Build time settings and configuration
 *
 * @author Graeme Rocher
 */
@CompileStatic
class BuildSettings {

    /**
     * The http proxy username
     */
    public static final String PROXY_HTTP_USER = "http.proxyUser"
    /**
     * The http proxy password
     */
    public static final String PROXY_HTTP_PASSWORD = "http.proxyPassword"
    /**
     * The proxy selector object to use when connecting remotely from the CLI
     */
    public static final String PROXY_SELECTOR = "grails.proxy.selector"
    /**
     * The authenticator to use when connecting remotely from the CLI
     */
    public static final String AUTHENTICATOR = "grails.proxy.authenticator"
    /**
     * Name of the System property that specifies the main class name
     */
    public static final String MAIN_CLASS_NAME = "org.grails.MAIN_CLASS_NAME"

    /**
     * The name of the profile being used
     */
    public static final String PROFILE = "grails.profile"
    /**
     * Specifies the profile repositories to use
     */
    public static final String PROFILE_REPOSITORIES = "grails.profiles.repositories"

    public static final String BUILD_SCOPE = "build"
    public static final String COMPILE_SCOPE = "compileClasspath"
    public static final String RUNTIME_SCOPE = "runtimeClasspath"
    public static final String TEST_SCOPE = "testCompileClasspath"
    public static final String PROVIDED_SCOPE = "provided"

    public static final String BUILD_SCOPE_DESC = "Dependencies for the build system only"
    public static final String COMPILE_SCOPE_DESC = "Dependencies placed on the classpath for compilation"
    public static final String RUNTIME_SCOPE_DESC = "Dependencies needed at runtime but not for compilation"
    public static final String TEST_SCOPE_DESC = "Dependencies needed for test compilation and execution but not at runtime"
    public static final String PROVIDED_SCOPE_DESC = "Dependencies needed at development time, but not during deployment"

    public static final Map<String, String> SCOPE_TO_DESC = [
            (BUILD_SCOPE): BUILD_SCOPE_DESC,
            (PROVIDED_SCOPE): PROVIDED_SCOPE_DESC,
            (COMPILE_SCOPE): COMPILE_SCOPE_DESC,
            (RUNTIME_SCOPE): RUNTIME_SCOPE_DESC,
            (TEST_SCOPE): TEST_SCOPE_DESC
    ]

    public static final Pattern JAR_PATTERN = ~/^\S+\.jar$/

    /**
     * The compiler source level to use
     */
    public static final String COMPILER_SOURCE_LEVEL = "grails.project.source.level"

    /**
     * The compiler source level to use
     */
    public static final String COMPILER_TARGET_LEVEL = "grails.project.target.level"
    /**
     * The version of the servlet API
     */
    public static final String SERVLET_VERSION = "grails.servlet.version"
    /**
     * The base directory of the application
     */
    public static final String APP_BASE_DIR = "base.dir"
    /**
     * The name of the system property for the Grails work directory.
     */
    public static final String WORK_DIR = "grails.work.dir"

    /**
     * The name of the system property for the project work directory
     */
    public static final String PROJECT_WORK_DIR = "grails.project.work.dir"

    public static final String OFFLINE_MODE= "grails.offline.mode"

    /**
     * The name of the system property for {@link #}.
     */
    public static final String PROJECT_RESOURCES_DIR = "grails.project.resource.dir"

    /**
     * The name of the system property for project source directory. Must be set if changed from src/main/groovy
     */
    public static final String PROJECT_SOURCE_DIR = "grails.project.source.dir"

    /**
     * The name of the system property for the project classes directory. Must be set if changed from build/main/classes.
     */
    public static final String PROJECT_CLASSES_DIR = "grails.project.class.dir"

    /**
     * The name of the system property for project test classes directory. Must be set if changed from build/test/classes
     */
    public static final String PROJECT_TEST_CLASSES_DIR = "grails.project.test.class.dir"

    /**
     * The name of the system property for test reported directory
     */
    public static final String PROJECT_TEST_REPORTS_DIR = "grails.project.test.reports.dir"

    /**
     * The name of the system property for documentation output directory
     */
    public static final String PROJECT_DOCS_OUTPUT_DIR = "grails.project.docs.output.dir"

    /**
     * The name of the property specification test locations, must be set of the directory is changed from src/test/groovy
     */
    public static final String PROJECT_TEST_SOURCE_DIR = "grails.project.test.source.dir"

    /**
     * The name of the system property for the the project target directory. Must be set if Gradle build location is changed.
     */
    public static final String PROJECT_TARGET_DIR = "grails.project.target.dir"

    /**
     * The name of the WAR file of the project
     */
    public static final String PROJECT_WAR_FILE = "grails.project.war.file"

    /**
     * The name of the WAR file of the project
     */
    public static final String PROJECT_AUTODEPLOY_DIR = "grails.project.autodeploy.dir"

    /**
     * A system property with this name is populated in the preparation phase of functional testing
     * with the base URL that tests should be run against.
     */
    public static final String FUNCTIONAL_BASE_URL_PROPERTY = 'grails.testing.functional.baseUrl'

    /**
     * The name of the working directory for commands that don't belong to a project (like create-app)
     */
    public static final String CORE_WORKING_DIR_NAME = '.core'

    /**
     *  A property name to enable/disable AST conversion of closures actions&tags to methods
     */
    public static final String CONVERT_CLOSURES_KEY = "grails.compile.artefacts.closures.convert"


    /**
     * The location of the local Grails installation. Will be null if not known
     */
    public static final File GRAILS_HOME = System.getProperty('grails.home') ? new File(System.getProperty('grails.home')) : null

    /**
     * The base directory of the project
     */
    public static final File BASE_DIR

    /**
     * Whether the application is running inside the development environment or deployed
     */
    public static final boolean GRAILS_APP_DIR_PRESENT

    /**
     * The target directory of the project, null outside of the development environment
     */
    public static final File TARGET_DIR
    /**
     * The resources directory of the project, null outside of the development environment
     */
    public static final File RESOURCES_DIR
    /**
     * The classes directory of the project, null outside of the development environment
     */
    public static final File CLASSES_DIR
    public static final String RUN_EXECUTED = "grails.run.executed"

    /**
     * The path to the build classes directory
     */
    public static final String BUILD_CLASSES_PATH

    /**
     * The path to the build resources directory
     */
    public static final String BUILD_RESOURCES_PATH = "build/resources/main"

    public static final File SETTINGS_FILE = new File("${System.getProperty('user.home')}/.grails/settings.groovy")

    /**
     * @return The version of Grails being used
     */
    static String getGrailsVersion() {
        BuildSettings.package.implementationVersion
    }

    /**
     * @return Whether the current version of Grails being used is a development version
     */
    static boolean isDevelopmentGrailsVersion() {
        BuildSettings.package.implementationVersion.endsWith('-SNAPSHOT')
    }

    static {
        boolean grailsAppDirPresent = new File( "grails-app").exists() || new File( "Application.groovy").exists()
        if(!grailsAppDirPresent) {
            CLASSES_DIR = null
            BUILD_CLASSES_PATH = "build/classes/main"
        }
        else {
            String fromSystem = System.getProperty(PROJECT_CLASSES_DIR)
            if(fromSystem) {
                CLASSES_DIR = new File(fromSystem)
                BUILD_CLASSES_PATH = fromSystem
            }
            else  {
                File groovyDir = new File("build/classes/groovy/main")
                if(groovyDir.exists()) {
                    BUILD_CLASSES_PATH = "build/classes/groovy/main"
                    CLASSES_DIR = groovyDir
                }
                else {
                    BUILD_CLASSES_PATH = "build/classes/main"
                    CLASSES_DIR = new File("build/classes/main")
                }
            }
        }
        BASE_DIR = System.getProperty(APP_BASE_DIR) ? new File(System.getProperty(APP_BASE_DIR)) :  ( IOUtils.findApplicationDirectoryFile() ?: new File("."))
        GRAILS_APP_DIR_PRESENT = new File(BASE_DIR, "grails-app").exists() || new File(BASE_DIR, "Application.groovy").exists()
        TARGET_DIR = new File(BASE_DIR, "build")
        RESOURCES_DIR = !GRAILS_APP_DIR_PRESENT ? null : (System.getProperty(PROJECT_RESOURCES_DIR) ? new File(System.getProperty(PROJECT_RESOURCES_DIR)) : new File(TARGET_DIR, "resources/main"))
    }
}

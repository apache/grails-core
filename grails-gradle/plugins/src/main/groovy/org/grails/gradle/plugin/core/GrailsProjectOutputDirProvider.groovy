/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.gradle.plugin.core

import java.nio.file.Path

import groovy.transform.CompileStatic

import org.gradle.api.file.Directory
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

import grails.util.BuildSettings

/**
 * Provides a system property naming one of the build's directories, relative to the project directory, to forked
 * JVM tasks: {@code grails.project.class.dir} for the main source set's Groovy classes
 * ({@link BuildSettings#PROJECT_CLASSES_DIR}), {@code grails.project.resource.dir} for its resources
 * ({@link BuildSettings#PROJECT_RESOURCES_DIR}), and {@code grails.project.target.dir} for the build directory itself
 * ({@link BuildSettings#PROJECT_TARGET_DIR}), where development keeps its restart marker ({@code .grailspid}).
 *
 * <p>In development a changed source file is compiled again into {@link BuildSettings#BUILD_CLASSES_PATH}, and a
 * changed message bundle is copied into {@link BuildSettings#BUILD_RESOURCES_PATH}, each resolved against the directory
 * of the project that owns the file; {@link BuildSettings#RESOURCES_DIR} is where the application reads resources
 * from in development. Without these properties they fall back to {@code build/classes/groovy/main} and
 * {@code build/resources/main}, so an application whose build directory is elsewhere (a second instance of one
 * checkout built with {@code -PbuildDir}, say) had each change compiled or copied where it does not load from: the
 * artefact was reloaded from the class it already had, and the old code went on answering.</p>
 *
 * <p>The path is relative because it is joined to a project directory, and its names are separated by {@code /} on
 * every platform, as the fallbacks are, because {@code IOUtils} compares the classes path with class locations
 * written as URLs. When the directory has no path relative to the project, such as one on another drive on Windows,
 * no property is passed, the application keeps the fallback, and a warning names both directories and what that
 * property's fallback means ({@link #withoutIt}).</p>
 *
 * <p>A build that sets the property on the task itself ({@code systemProperty 'grails.project.class.dir', ...}) keeps
 * its value: nothing is passed then. The task's system properties are read when the task runs, so a value set after
 * the plugin is applied counts too.</p>
 *
 * <p>A directory outside the project gives a path that climbs out of it ({@code ../}). Joining it, as
 * {@code GrailsApp.recompile}, {@code MainClassFinder} and the i18n plugin do, resolves correctly; the {@code IOUtils}
 * lookups that compare it with a class location find no match and fall back as they did before.</p>
 *
 * <p>The paths are also joined to the directory of a plugin subproject whose sources development watches, so they
 * assume every project lays out its build directory in the same way relative to itself, as Gradle's {@code -PbuildDir}
 * or an {@code allprojects} block does. A plugin subproject whose build directory is laid out differently from the
 * application's has its changes compiled where it does not load classes from.</p>
 *
 * <p>All four values are {@link Internal}, as for {@link GrailsAppBaseDirProvider}: the classes and resources
 * directories are the task's classpath, already tracked there; the build directory itself only says where the
 * application writes its restart marker while it runs, which nothing the task produces depends on; and the task's
 * system properties are its inputs already. The directory is read when the task runs, so a build directory changed
 * after the plugin is applied is the one passed.</p>
 */
@CompileStatic
class GrailsProjectOutputDirProvider implements CommandLineArgumentProvider {

    private static final Logger LOG = Logging.getLogger(GrailsProjectOutputDirProvider)

    @Internal
    final String systemProperty

    @Internal
    final File projectDir

    @Internal
    final Provider<Directory> directory

    @Internal
    final Map<String, ?> taskSystemProperties

    /**
     * @param systemProperty the system property to pass, {@link BuildSettings#PROJECT_CLASSES_DIR},
     *        {@link BuildSettings#PROJECT_RESOURCES_DIR} or {@link BuildSettings#PROJECT_TARGET_DIR}
     * @param projectDir the project directory, which the path is relative to
     * @param directory the output directory the property names
     * @param taskSystemProperties the task's own system properties, a value set there is kept
     */
    GrailsProjectOutputDirProvider(String systemProperty, File projectDir, Provider<Directory> directory,
                                   Map<String, ?> taskSystemProperties) {
        this.systemProperty = systemProperty
        this.projectDir = projectDir
        this.directory = directory
        this.taskSystemProperties = taskSystemProperties
    }

    @Override
    Iterable<String> asArguments() {
        if (taskSystemProperties.containsKey(systemProperty)) {
            return []
        }
        File dir = directory.get().asFile
        String relative = relativePath(projectDir.toPath(), dir.toPath())
        if (relative == null) {
            LOG.warn('{} is not passed: {} has no path relative to the project directory {}, so {}.',
                    systemProperty, dir, projectDir, withoutIt(systemProperty))
            return []
        }
        relative ? ["-D${systemProperty}=${relative}".toString()] : []
    }

    /**
     * What the application does without {@code systemProperty}, for the warning when it cannot be passed: each
     * property falls back to a directory under {@code build/}, and what that breaks differs.
     */
    static String withoutIt(String systemProperty) {
        switch (systemProperty) {
            case BuildSettings.PROJECT_CLASSES_DIR:
                return 'development reloading compiles a changed class into build/classes/groovy/main, which the ' +
                        'application does not load classes from, and the change is not reloaded'
            case BuildSettings.PROJECT_RESOURCES_DIR:
                return 'the application reads its resources from build/resources/main, and development reloading ' +
                        'copies a changed message bundle there, not into the build\'s own resources directory'
            case BuildSettings.PROJECT_TARGET_DIR:
                return 'development keeps its restart marker (.grailspid) in build/, and logs an error each time ' +
                        'the application starts if there is no build/'
            default:
                return 'the application falls back to a directory under build/'
        }
    }

    /**
     * {@code directory} relative to {@code projectDir}, its names separated by {@code /}; null when it has no path
     * relative to the project, such as one on another drive on Windows.
     */
    static String relativePath(Path projectDir, Path directory) {
        try {
            projectDir.relativize(directory).collect { Path name -> name.toString() }.join('/')
        }
        catch (IllegalArgumentException ignored) {
            null
        }
    }
}

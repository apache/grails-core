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
package org.apache.grails.buildsrc

import java.util.jar.JarEntry
import java.util.jar.JarFile

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a package is in more than one jar of a module: its runtime jar and the companions of its
 * tools, {@code -cli} and {@code -cli-standalone}. A package of this project is in one jar only, so no
 * package is split between two jars an application or a tool has on its class path, and each jar can be
 * a module of its own. A companion whose classes would share a package with another jar of the module
 * has them in a package of its own, such as {@code org.apache.grails.security.cli.standalone} for the
 * standalone tools of {@code grails-spring-security}.
 *
 * <p>The packages are those of the classes in each jar, without what is under {@code META-INF} (other
 * than the classes of a multi-release jar) and without the unnamed package. The report lists the
 * packages of each jar.</p>
 *
 * @since 9.0
 */
@CacheableTask
@CompileStatic
abstract class CompanionPackagesTask extends DefaultTask {

    static final String TASK_NAME = 'checkCompanionPackages'

    private static final String CLASS_ENDING = '.class'

    private static final String META_INF = 'META-INF/'

    private static final String VERSIONS = 'META-INF/versions/'

    /** The jars of the module: the runtime jar and the companions. A jar that was not built is left out. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getJars()

    /** Identifies the module in the failure. */
    @Input
    abstract Property<String> getProjectPath()

    /** The packages of each jar. */
    @OutputFile
    abstract RegularFileProperty getReport()

    @TaskAction
    void check() {
        Map<String, Set<String>> packagesByJar = new TreeMap<String, Set<String>>()
        for (File jar : jars.files) {
            if (jar.isFile()) {
                packagesByJar.put(jar.name, packages(jar))
            }
        }
        StringBuilder written = new StringBuilder()
        packagesByJar.each { String jar, Set<String> packages ->
            written.append(jar).append(':').append(packages ? ' ' + packages.join(' ') : '').append('\n')
        }
        File reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.setText(written.toString(), 'UTF-8')

        List<String> split = splitPackages(packagesByJar)
        if (split) {
            throw new GradleException("A package of ${projectPath.get()} is in two of its jars: ${split.join('; ')}. A package is in one jar " +
                    'only: give the classes of the companion a package of its own, such as a sub-package of the companion ' +
                    '(org.example.cli.standalone for the standalone tools).')
        }
    }

    /**
     * @param packagesByJar the packages of each jar, by the name of the jar
     * @return a line for each package that is in two of the jars, naming the package and the jars
     */
    static List<String> splitPackages(Map<String, Set<String>> packagesByJar) {
        List<String> split = []
        List<String> names = new ArrayList<String>(packagesByJar.keySet())
        for (int first = 0; first < names.size(); first++) {
            for (int second = first + 1; second < names.size(); second++) {
                Set<String> shared = new TreeSet<String>(packagesByJar[names[first]])
                shared.retainAll(packagesByJar[names[second]])
                for (String name : shared) {
                    split << "${name} is in ${names[first]} and in ${names[second]}".toString()
                }
            }
        }
        split
    }

    /**
     * @param jar a jar
     * @return the packages of the classes in it
     */
    static Set<String> packages(File jar) {
        Set<String> packages = new TreeSet<String>()
        new JarFile(jar).withCloseable { JarFile archive ->
            for (JarEntry entry : Collections.list(archive.entries())) {
                String path = entry.name
                if (entry.directory || !path.endsWith(CLASS_ENDING)) {
                    continue
                }
                if (path.startsWith(VERSIONS)) {
                    // a class of a multi-release jar, under META-INF/versions/<release>/
                    int release = path.indexOf('/', VERSIONS.length())
                    path = release < 0 ? '' : path.substring(release + 1)
                }
                else if (path.startsWith(META_INF)) {
                    continue
                }
                int last = path.lastIndexOf('/')
                if (last > 0) {
                    packages << path.substring(0, last).replace('/', '.')
                }
            }
        }
        packages
    }
}

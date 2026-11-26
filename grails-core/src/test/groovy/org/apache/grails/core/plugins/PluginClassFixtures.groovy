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
package org.apache.grails.core.plugins

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import groovy.transform.CompileStatic

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration

import grails.plugins.metadata.PluginDefinition

/**
 * Plugin classes compiled into a directory of their own, laid out as a plugin's build lays them out, so that
 * what is found relative to a plugin class can be arranged per test.
 */
@CompileStatic
class PluginClassFixtures {

    /**
     * Compiles a plugin class with the given name into {@code root}. The compiler writes the plugin's
     * descriptor beside it, as a plugin's build does.
     */
    static void compilePlugin(Path root, String className) {
        int dot = className.lastIndexOf('.')
        String simpleName = className.substring(dot + 1)
        compile(root, "${simpleName}.groovy",
                "package ${className.substring(0, dot)}\n@grails.plugins.metadata.GrailsPluginDescriptor\n" +
                        "class ${simpleName} extends grails.plugins.Plugin {\n}\n")
    }

    /**
     * Compiles Groovy source into {@code root}, so that its classes have class files of their own, as a
     * plugin jar's do, without being on the test class path.
     */
    static void compile(Path root, String fileName, String source) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = root.toFile()
        CompilationUnit unit = new CompilationUnit(configuration, null, new GroovyClassLoader(PluginClassFixtures.classLoader))
        unit.addSource(fileName, source)
        unit.compile()
    }

    /**
     * A class loader that parses plugin classes at run time, whose class path holds a descriptor recording the
     * version of each, as a plugin jar does. A plugin class registered directly takes its version from it.
     *
     * @param versionsByClass the version of each plugin class, by class name
     */
    static GroovyClassLoader versionedPluginLoader(Path root, Map<String, String> versionsByClass) {
        versionsByClass.each { String className, String version ->
            String name = PluginDefinition.deriveName(className, null)
            Path imports = root.resolve(PluginUtils.PLUGINS_IMPORTS_PATH)
            Files.createDirectories(imports.parent)
            Files.writeString(imports, name + '\n', StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            write(root, PluginUtils.getPluginDescriptorPath(name),
                    "format.version=1\nname=${name}\nclass=${className}\nversion=${version}\n")
        }
        new GroovyClassLoader(loaderOver(root))
    }

    /**
     * Writes {@code content} to {@code path} under {@code root}, creating the directories it needs.
     */
    static void write(Path root, String path, String content) {
        Path file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    /**
     * A class loader over {@code roots} whose classes are loaded from them, the plugin classes included.
     */
    static URLClassLoader loaderOver(Path... roots) {
        new URLClassLoader(roots.collect { Path root -> root.toUri().toURL() } as URL[],
                PluginClassFixtures.classLoader)
    }
}

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
package org.apache.grails.forge.cli.plugin

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import org.apache.grails.forge.core.bom.BomVersions
import org.apache.grails.forge.core.pack.BundledPackFeatures
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackLoader
import spock.lang.Shared
import spock.lang.Specification

/**
 * The list of the plugins of this release of Grails that the command line has in itself, held to what
 * it is a list of: the plugin classes in the sources of the framework, the Grails BOM and the features
 * of the command line.
 */
class BundledPluginsSpec extends Specification {

    /** The directories of the repository that hold no plugin the framework publishes for applications */
    static final Set<String> NOT_OF_THE_FRAMEWORK = ['grails-forge', 'grails-test-examples', 'grails-test-suite-uber', 'grails-test-suite-web',
                                                     'grails-test-suite-persistence', 'grails-test-suite-base', 'end-to-end', 'grails-doc',
                                                     'grails-gradle', 'build-logic', 'build', 'node_modules'] as Set<String>

    @Shared
    File repository = new File(System.getProperty('user.dir'), '../..').canonicalFile

    void "the list has a line for every plugin class of the framework, and none for one that is gone"() {
        given: 'the plugin classes among the main sources of the framework'
        Set<String> inSources = new TreeSet<>()
        Path root = repository.toPath()
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                String name = directory.fileName.toString()
                boolean skipped = directory != root && (name.startsWith('.') || NOT_OF_THE_FRAMEWORK.contains(name) ||
                        name in ['test', 'integration-test', 'testFixtures'] || Files.isSymbolicLink(directory))
                return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String path = root.relativize(file).toString().replace(File.separator, '/')
                if (path.endsWith('GrailsPlugin.groovy') && path.contains('/src/main/groovy/')) {
                    inSources << path
                }
                return FileVisitResult.CONTINUE
            }
        })

        expect: 'the sources are those of the framework'
        new File(repository, 'grails-forge/grails-forge-cli').isDirectory()
        inSources.size() > 40

        and: 'add a line to plugins.tsv for a plugin the framework got, and take the line of one that is gone out'
        BundledPlugins.descriptors().values() as TreeSet == inSources
    }

    void "every plugin of the list is one the Grails BOM manages, so its version is known"() {
        given:
        List<BomVersions> boms = BomVersions.SHIPPED.collect { BomVersions.of(it) }

        expect:
        boms.every { it != null }
        BundledPlugins.all().findAll { PluginRecord plugin -> !boms.any { it.versionOf(plugin.coordinates) } }*.coordinates == []
    }

    void "the list is sorted, has each plugin once, and says of each in one line of text what it is"() {
        given:
        List<PluginRecord> plugins = BundledPlugins.all()

        expect:
        plugins*.coordinates == plugins*.coordinates.sort(false)
        plugins*.coordinates.toUnique().size() == plugins.size()
        plugins*.name.toUnique().size() == plugins.size()
        plugins.every { PluginRecord plugin ->
            plugin.ofGrails && plugin.group.startsWith('org.apache.grails') && plugin.artifact.startsWith('grails-') &&
                    plugin.name ==~ /[\x20-\x7e]{2,60}/ && plugin.description ==~ /[\x20-\x7e]{10,200}/ && !plugin.description.endsWith('.') &&
                    !(plugin.name + plugin.description).contains('@') &&
                    (plugin.documentation == null || plugin.documentation == PluginRecord.address(plugin.documentation)) &&
                    plugin.source == BundledPlugins.SOURCE && plugin.issues == BundledPlugins.ISSUES && plugin.license == 'Apache-2.0' &&
                    plugin.versions.isEmpty() && plugin.deprecated == null && plugin.repository == null
        }
    }

    void "a feature the list names for a plugin is a feature of the command line that adds that plugin to an application"() {
        given: 'the feature packs the command line has in itself, each with the artifacts it adds'
        ClassLoader loader = BundledPackFeatures.classLoader
        PackLoader packs = new PackLoader()
        Map<String, Set<String>> adds = [:]
        for (String name : BundledPackFeatures.names(loader)) {
            Pack pack = packs.fromClasspath(loader, name)
            adds[pack.descriptor.name] = pack.descriptor.dependencies.collect { "${it.groupId}:${it.artifactId}".toString() } as Set
        }
        List<PluginRecord> withFeatures = BundledPlugins.all().findAll { it.features }

        expect:
        withFeatures.size() > 15
        withFeatures.collectMany { PluginRecord plugin ->
            plugin.features.findAll { String feature -> !adds[feature]?.contains(plugin.coordinates) }.collect { "${plugin.coordinates}: ${it}".toString() }
        } == []

        and: 'a feature that has the name of a plugin, or of its artifact, is named for it'
        BundledPlugins.all().findAll { PluginRecord plugin -> adds[plugin.artifact]?.contains(plugin.coordinates) && !plugin.features.contains(plugin.artifact) }*.coordinates == []
    }
}

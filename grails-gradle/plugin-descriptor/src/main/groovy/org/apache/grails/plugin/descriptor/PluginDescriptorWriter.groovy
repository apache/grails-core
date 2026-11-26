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
package org.apache.grails.plugin.descriptor

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import javax.xml.stream.XMLOutputFactory

import groovy.io.FileType
import groovy.transform.CompileStatic

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

import grails.plugins.metadata.PluginDefinition

/**
 * Discovers compiled plugin and artefact classes and writes their classpath index.
 * Shared with the framework build, which must bootstrap before Gradle plugins are available.
 *
 * @since 9.0
 */
@CompileStatic
final class PluginDescriptorWriter {

    private PluginDescriptorWriter() {
    }

    static final String PLUGINS_IMPORTS_PATH = 'META-INF/grails/plugins.imports'
    static final String PLUGIN_DESCRIPTORS_PREFIX = 'META-INF/grails/plugins/'
    static final String PLUGIN_PROPERTIES_FILE = 'plugin.properties'

    private static final String GRAILS_PLUGIN_SUFFIX = 'GrailsPlugin'
    private static final String CLASS_FILE_EXTENSION = '.class'
    private static final String PLUGIN_DESCRIPTOR_ANNOTATION = 'Lgrails/plugins/metadata/GrailsPluginDescriptor;'
    private static final String COMPILED_PLUGIN_METADATA = 'Lgrails/plugins/metadata/CompiledPluginMetadata;'
    private static final String ARTEFACT_ANNOTATION = 'Lgrails/artefact/Artefact;'

    static ScanResult scan(Set<File> classesDirs) {
        List<ScannedPlugin> plugins = []
        Map<String, SortedSet<String>> artefactsByType = new TreeMap<>()

        classesDirs.each { File dir ->
            if (!dir.exists()) {
                return
            }
            dir.eachFileRecurse(FileType.FILES) { File file ->
                if (!file.name.endsWith(CLASS_FILE_EXTENSION) || file.name.contains('$')) {
                    return
                }
                ClassReader reader
                try {
                    reader = new ClassReader(file.bytes)
                }
                catch (IOException | IllegalArgumentException e) {
                    throw new IllegalArgumentException("Could not read ${file} while generating plugin descriptors", e)
                }
                PluginClassScanner scanner = new PluginClassScanner()
                reader.accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)

                if (scanner.isConcrete()) {
                    String fqcn = reader.className.replace('/' as char, '.' as char)
                    int lastDot = fqcn.lastIndexOf('.')
                    String simpleName = lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn
                    boolean matchesSuffix = simpleName.endsWith(GRAILS_PLUGIN_SUFFIX) &&
                            simpleName.length() > GRAILS_PLUGIN_SUFFIX.length()
                    if (!scanner.frameworkPluginWrapper &&
                            (scanner.hasPluginDescriptorAnnotation || matchesSuffix)) {
                        plugins.add(scanner.toScannedPlugin(fqcn))
                    }
                    else if (scanner.isProvidedArtefact()) {
                        artefactsByType.computeIfAbsent(scanner.artefactType) { new TreeSet<String>() }.add(fqcn)
                    }
                }
            }
        }

        plugins.sort { ScannedPlugin a, ScannedPlugin b ->
            int cmp = a.logicalName <=> b.logicalName
            cmp != 0 ? cmp : ((a.className ?: '') <=> (b.className ?: ''))
        }
        new ScanResult(plugins, artefactsByType)
    }

    static void writeDescriptors(
            List<ScannedPlugin> plugins,
            Map<String, SortedSet<String>> artefactsByType,
            String defaultVersion,
            File pluginConfigFile,
            File outDir,
            boolean publishXml = true) {
        Map<String, PluginDefinition> definitions = new TreeMap<>()
        for (ScannedPlugin plugin : plugins) {
            PluginDefinition definition = plugin.toDefinition(artefactsByType, defaultVersion)
            PluginDefinition previous = definitions.putIfAbsent(definition.pluginName, definition)
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Grails plugin logical name '${definition.pluginName}' declared by " +
                        "[${previous.providedPlugins}] and [${definition.providedPlugins}]")
            }
        }
        for (PluginDefinition definition : definitions.values()) {
            File pluginDir = new File(outDir, PLUGIN_DESCRIPTORS_PREFIX + definition.pluginName)
            pluginDir.mkdirs()
            File descriptorFile = new File(pluginDir, PLUGIN_PROPERTIES_FILE)
            descriptorFile.withWriter('UTF-8') { Writer writer -> definition.write(writer) }

            if (pluginConfigFile != null && pluginConfigFile.isFile()) {
                Files.copy(
                        pluginConfigFile.toPath(),
                        new File(pluginDir, pluginConfigFile.name).toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                )
            }
        }

        File importsFile = new File(outDir, PLUGINS_IMPORTS_PATH)
        importsFile.parentFile.mkdirs()
        importsFile.setText(definitions.keySet().join('\n') + '\n', StandardCharsets.UTF_8.name())
        if (publishXml && plugins) {
            writeXmlProjection(outDir, definitions.values())
        }
    }

    private static void writeXmlProjection(File outDir, Collection<PluginDefinition> definitions) {
        new File(outDir, 'META-INF/grails-plugin.xml').withWriter('UTF-8') { Writer writer ->
            def xml = XMLOutputFactory.newFactory().createXMLStreamWriter(writer)
            xml.writeStartElement('plugin')
            if (definitions.size() == 1) {
                PluginDefinition definition = definitions.first()
                xml.writeAttribute('name', definition.pluginName)
                if (definition.pluginVersion) {
                    xml.writeAttribute('version', definition.pluginVersion)
                }
                if (definition.grailsVersionRange) {
                    xml.writeAttribute('grailsVersion', definition.grailsVersionRange)
                }
                if (definition.dependsOn) {
                    xml.writeStartElement('dependsOn')
                    new TreeMap<>(definition.dependsOn).each { String key, String constraint ->
                        if (key ==~ /[A-Za-z_][A-Za-z0-9._-]*/) {
                            xml.writeAttribute(key, constraint)
                        }
                    }
                    xml.writeEndElement()
                }
            }
            Set<String> classes = new TreeSet<>()
            for (PluginDefinition definition : definitions) {
                for (String name : definition.providedPlugins) {
                    xml.writeStartElement('type')
                    xml.writeCharacters(name)
                    xml.writeEndElement()
                }
                classes.addAll(definition.providedClasses)
            }
            xml.writeStartElement('resources')
            for (String name : classes) {
                xml.writeStartElement('resource')
                xml.writeCharacters(name)
                xml.writeEndElement()
            }
            xml.writeEndElement()
            xml.writeEndElement()
            xml.close()
        }
    }

    static String normalizePluginName(String raw) {
        PluginDefinition.normalizePluginName(raw)
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null
        }
        String trimmed = value.trim()
        trimmed.isEmpty() ? null : trimmed
    }

    static String deriveLogicalName(String fqcn, String explicitName) {
        if (explicitName != null && !explicitName.isBlank()) {
            return normalizePluginName(explicitName)
        }
        int lastDot = fqcn.lastIndexOf('.')
        String simpleName = lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn
        if (simpleName.endsWith(GRAILS_PLUGIN_SUFFIX) && simpleName.length() > GRAILS_PLUGIN_SUFFIX.length()) {
            simpleName = simpleName.substring(0, simpleName.length() - GRAILS_PLUGIN_SUFFIX.length())
        }
        if (simpleName.isEmpty()) {
            return simpleName
        }
        if (simpleName.length() > 1 && Character.isUpperCase(simpleName.charAt(0)) && Character.isUpperCase(simpleName.charAt(1))) {
            return simpleName
        }
        Character.toLowerCase(simpleName.charAt(0)).toString() + simpleName.substring(1)
    }

    private static boolean isExcluded(String className, List<String> patterns) {
        if (!patterns) {
            return false
        }
        String path = className.replace('.' as char, '/' as char)
        patterns.any { String pattern -> antMatch(pattern, path) }
    }

    static boolean antMatch(String pattern, String path) {
        if (pattern == null || path == null) {
            return false
        }
        StringBuilder regex = new StringBuilder(pattern.length() * 2)
        int i = 0
        int len = pattern.length()
        while (i < len) {
            if (pattern.startsWith('**/', i)) {
                regex.append('(?:.*/)?')
                i += 3
            }
            else if (pattern.startsWith('**', i)) {
                regex.append('.*')
                i += 2
            }
            else {
                char ch = pattern.charAt(i)
                if (ch == ('*' as char)) {
                    regex.append('[^/]*')
                }
                else if (ch == ('?' as char)) {
                    regex.append('[^/]')
                }
                else if ('\\.^$+()[]{}|'.indexOf(ch as int) >= 0) {
                    regex.append('\\').append(ch)
                }
                else {
                    regex.append(ch)
                }
                i++
            }
        }
        path.matches(regex.toString())
    }

    static class ScanResult {
        final List<ScannedPlugin> plugins
        final Map<String, SortedSet<String>> artefactsByType

        ScanResult(List<ScannedPlugin> plugins, Map<String, SortedSet<String>> artefactsByType) {
            this.plugins = plugins
            this.artefactsByType = artefactsByType
        }
    }

    static class ScannedPlugin {
        final String className
        final String logicalName
        final String version
        final String grailsVersion
        final List<String> loadAfter
        final List<String> loadBefore
        final Map<String, String> dependsOn
        final List<String> evict
        final List<String> observe
        final List<String> environmentsIncludes
        final List<String> environmentsExcludes
        final List<String> pluginExcludes
        final boolean enabled
        final boolean metadataComplete

        ScannedPlugin(
                String className,
                String logicalName,
                String version,
                String grailsVersion,
                List<String> loadAfter,
                List<String> loadBefore,
                Map<String, String> dependsOn,
                List<String> evict,
                List<String> observe,
                List<String> environmentsIncludes,
                List<String> environmentsExcludes,
                List<String> pluginExcludes,
                boolean enabled,
                boolean metadataComplete) {
            this.className = className
            this.logicalName = logicalName
            this.version = version
            this.grailsVersion = grailsVersion
            this.loadAfter = loadAfter
            this.loadBefore = loadBefore
            this.dependsOn = dependsOn
            this.evict = evict
            this.observe = observe
            this.environmentsIncludes = environmentsIncludes
            this.environmentsExcludes = environmentsExcludes
            this.pluginExcludes = pluginExcludes
            this.enabled = enabled
            this.metadataComplete = metadataComplete
        }

        PluginDefinition toDefinition(Map<String, SortedSet<String>> artefactsByType, String defaultVersion) {
            Map<String, List<String>> included = [:]
            artefactsByType.each { String type, SortedSet<String> names ->
                included.put(type, names.findAll { String name -> !isExcluded(name, pluginExcludes) }.toList())
            }
            new PluginDefinition.Builder(
                    pluginName: logicalName, pluginVersion: trimToNull(version) ?: defaultVersion,
                    providedPlugins: className ? [className] : [], providedArtefactsByType: included,
                    grailsVersionRange: grailsVersion, dependsOn: dependsOn, loadAfter: loadAfter,
                    loadBefore: loadBefore, observe: observe, evict: evict, environmentsIncludes: environmentsIncludes,
                    environmentsExcludes: environmentsExcludes, enabled: enabled, metadataComplete: metadataComplete
            ).build()
        }

        ScannedPlugin withLogicalName(String overrideName) {
            new ScannedPlugin(
                    className, overrideName, version, grailsVersion,
                    loadAfter, loadBefore, dependsOn, evict, observe,
                    environmentsIncludes, environmentsExcludes, pluginExcludes,
                    enabled, metadataComplete
            )
        }

        ScannedPlugin withExtraExcludes(List<String> extra) {
            List<String> combined = new ArrayList<>(pluginExcludes)
            combined.addAll(extra)
            new ScannedPlugin(
                    className, logicalName, version, grailsVersion,
                    loadAfter, loadBefore, dependsOn, evict, observe,
                    environmentsIncludes, environmentsExcludes, combined,
                    enabled, metadataComplete
            )
        }
    }

    private static class PluginClassScanner extends ClassVisitor {

        private static final Set<String> FRAMEWORK_PLUGIN_SUPERCLASSES = [
                'org/grails/plugins/AbstractGrailsPlugin',
                'org/grails/plugins/DefaultGrailsPlugin',
                'org/grails/plugins/BinaryGrailsPlugin',
        ].toSet()
        private static final String GRAILS_PLUGIN_INTERFACE = 'grails/plugins/GrailsPlugin'
        private static final String GRAILS_PLUGIN_METADATA_ANNOTATION = 'Lgrails/plugins/metadata/GrailsPlugin;'

        int access
        boolean frameworkPluginWrapper
        boolean genericClass
        boolean hasGrailsPluginAnnotation
        boolean hasPluginDescriptorAnnotation
        String explicitName
        String version
        String grailsVersion
        List<String> loadAfter = []
        List<String> loadBefore = []
        List<String> dependencyDeclarations = []
        List<String> evict = []
        List<String> observe = []
        List<String> environmentsIncludes = []
        List<String> environmentsExcludes = []
        List<String> pluginExcludes = []
        boolean enabled = true
        String artefactType

        PluginClassScanner() {
            super(Opcodes.ASM9)
        }

        @Override
        void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.access = access
            this.genericClass = signature != null && signature.startsWith('<')
            this.frameworkPluginWrapper = (superName in FRAMEWORK_PLUGIN_SUPERCLASSES) ||
                    (interfaces != null && GRAILS_PLUGIN_INTERFACE in interfaces)
        }

        boolean isConcrete() {
            (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION |
                    Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_MODULE)) == 0
        }

        boolean isProvidedArtefact() {
            artefactType != null && (hasGrailsPluginAnnotation || !genericClass)
        }

        @Override
        AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (PLUGIN_DESCRIPTOR_ANNOTATION == descriptor) {
                hasPluginDescriptorAnnotation = true
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    void visit(String name, Object value) {
                        if ('name' == name && value instanceof String) {
                            explicitName = (String) value
                        }
                        else if ('version' == name && value instanceof String) {
                            version = (String) value
                        }
                        else if ('grailsVersion' == name && value instanceof String) {
                            grailsVersion = (String) value
                        }
                        else if ('enabled' == name && value instanceof Boolean) {
                            enabled = (Boolean) value
                        }
                    }

                    @Override
                    AnnotationVisitor visitArray(String name) {
                        if ('loadAfter' == name) {
                            return stringArrayCollector(loadAfter)
                        }
                        if ('loadBefore' == name) {
                            return stringArrayCollector(loadBefore)
                        }
                        if ('evict' == name) {
                            return stringArrayCollector(evict)
                        }
                        if ('observe' == name) {
                            return stringArrayCollector(observe)
                        }
                        if ('environmentsIncludes' == name) {
                            return stringArrayCollector(environmentsIncludes)
                        }
                        if ('environmentsExcludes' == name) {
                            return stringArrayCollector(environmentsExcludes)
                        }
                        if ('pluginExcludes' == name) {
                            return stringArrayCollector(pluginExcludes)
                        }
                        if ('dependsOn' == name) {
                            return stringArrayCollector(dependencyDeclarations)
                        }
                        null
                    }
                }
            }
            if (COMPILED_PLUGIN_METADATA == descriptor) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    void visit(String name, Object value) {
                        if ('grailsVersion' == name) {
                            grailsVersion = (String) value
                        }
                        else if ('version' == name) {
                            version = (String) value
                        }
                    }

                    @Override
                    AnnotationVisitor visitArray(String name) {
                        if ('pluginExcludes' == name) {
                            return stringArrayCollector(pluginExcludes)
                        }
                        null
                    }
                }
            }
            if (GRAILS_PLUGIN_METADATA_ANNOTATION == descriptor) {
                hasGrailsPluginAnnotation = true
                return null
            }
            if (ARTEFACT_ANNOTATION == descriptor) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    void visit(String name, Object value) {
                        if ('value' == name && value instanceof String) {
                            artefactType = (String) value
                        }
                    }
                }
            }
            null
        }

        private static AnnotationVisitor stringArrayCollector(List<String> target) {
            new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                void visit(String name, Object value) {
                    if (value instanceof String && !((String) value).isBlank()) {
                        target.add(((String) value).trim())
                    }
                }
            }
        }

        ScannedPlugin toScannedPlugin(String fqcn) {
            new ScannedPlugin(
                    fqcn,
                    deriveLogicalName(fqcn, explicitName),
                    version,
                    grailsVersion,
                    loadAfter,
                    loadBefore,
                    PluginDefinition.parseDependencies(dependencyDeclarations),
                    evict,
                    observe,
                    environmentsIncludes,
                    environmentsExcludes,
                    pluginExcludes,
                    enabled,
                    hasPluginDescriptorAnnotation
            )
        }
    }
}

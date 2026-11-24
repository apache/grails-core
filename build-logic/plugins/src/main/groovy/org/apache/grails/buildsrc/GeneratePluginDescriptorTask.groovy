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

import java.nio.charset.StandardCharsets

import groovy.io.FileType
import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Scans this project's compiled main classes with ASM for Grails plugin descriptor classes
 * ({@code *GrailsPlugin} or {@code @GrailsPluginDescriptor}) and {@code @Artefact} classes,
 * writing {@code META-INF/grails/plugins.imports} and per-plugin
 * {@code META-INF/grails/plugins/<name>/plugin.properties} descriptors.
 *
 * <p>Modules that declare no plugin descriptor classes produce an empty output directory, so this
 * task is safe to register on every framework library module.</p>
 *
 * @since 9.0
 */
@CacheableTask
@CompileStatic
abstract class GeneratePluginDescriptorTask extends DefaultTask {

    static final String PLUGINS_IMPORTS_PATH = 'META-INF/grails/plugins.imports'
    static final String PLUGIN_DESCRIPTORS_PREFIX = 'META-INF/grails/plugins/'
    static final String PLUGIN_PROPERTIES_FILE = 'plugin.properties'
    static final String FORMAT_VERSION = '1'

    private static final String GRAILS_PLUGIN_SUFFIX = 'GrailsPlugin'
    private static final String CLASS_FILE_EXTENSION = '.class'
    private static final String PLUGIN_DESCRIPTOR_ANNOTATION = 'Lgrails/plugins/metadata/GrailsPluginDescriptor;'
    private static final String PLUGIN_EXCLUDES_ANNOTATION = 'Lgrails/plugins/metadata/PluginExcludes;'
    private static final String ARTEFACT_ANNOTATION = 'Lgrails/artefact/Artefact;'

    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getClassesDirs()

    @Input
    @Optional
    abstract Property<String> getProjectVersion()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generate() {
        File outDir = outputDirectory.get().asFile
        if (outDir.exists()) {
            outDir.deleteDir()
        }
        outDir.mkdirs()

        ScanResult result = scan(classesDirs.files)
        if (result.plugins.isEmpty()) {
            return
        }

        String defaultVersion = projectVersion.getOrNull()
        writeDescriptors(result, defaultVersion, outDir)
    }

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
                    throw new GradleException("Could not read ${file} while generating plugin descriptors", e)
                }
                PluginClassScanner scanner = new PluginClassScanner()
                reader.accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)

                if (scanner.isConcrete()) {
                    String fqcn = reader.className.replace('/' as char, '.' as char)
                    if (!scanner.frameworkPluginWrapper &&
                            (scanner.hasPluginDescriptorAnnotation || fqcn.endsWith(GRAILS_PLUGIN_SUFFIX))) {
                        plugins.add(scanner.toScannedPlugin(fqcn))
                    }
                    else if (scanner.isProvidedArtefact()) {
                        artefactsByType.computeIfAbsent(scanner.artefactType) { new TreeSet<String>() }.add(fqcn)
                    }
                }
            }
        }

        plugins.sort { ScannedPlugin a, ScannedPlugin b -> a.logicalName <=> b.logicalName }
        new ScanResult(plugins, artefactsByType)
    }

    static void writeDescriptors(ScanResult result, String defaultVersion, File outDir) {
        SortedSet<String> importNames = new TreeSet<>()
        for (ScannedPlugin plugin : result.plugins) {
            importNames.add(plugin.logicalName)
            File descriptorFile = new File(outDir, "${PLUGIN_DESCRIPTORS_PREFIX}${plugin.logicalName}/${PLUGIN_PROPERTIES_FILE}")
            descriptorFile.parentFile.mkdirs()
            descriptorFile.setText(renderProperties(plugin, result.artefactsByType, defaultVersion), StandardCharsets.UTF_8.name())
        }

        File importsFile = new File(outDir, PLUGINS_IMPORTS_PATH)
        importsFile.parentFile.mkdirs()
        importsFile.setText(importNames.join('\n') + '\n', StandardCharsets.UTF_8.name())
    }

    static String renderProperties(
            ScannedPlugin plugin,
            Map<String, SortedSet<String>> artefactsByType,
            String defaultVersion) {
        Map<String, String> entries = new TreeMap<>()
        entries.put('format.version', FORMAT_VERSION)
        entries.put('name', plugin.logicalName)

        String version = plugin.version ?: defaultVersion
        if (version) {
            entries.put('version', version)
        }
        if (plugin.className) {
            entries.put('class', plugin.className)
        }
        entries.put('metadata.complete', Boolean.toString(plugin.metadataComplete))

        if (plugin.grailsVersion) {
            entries.put('grailsVersion', plugin.grailsVersion)
        }
        if (plugin.loadAfter) {
            entries.put('loadAfter', plugin.loadAfter.collect { normalizePluginName(it) }.join(','))
        }
        if (plugin.loadBefore) {
            entries.put('loadBefore', plugin.loadBefore.collect { normalizePluginName(it) }.join(','))
        }
        for (Map.Entry<String, String> dep : plugin.dependsOn.entrySet()) {
            entries.put("dependsOn.${normalizePluginName(dep.key)}".toString(), dep.value)
        }
        if (plugin.evict) {
            entries.put('evict', plugin.evict.collect { normalizePluginName(it) }.join(','))
        }
        if (plugin.observe) {
            entries.put('observe', plugin.observe.join(','))
        }
        if (plugin.environmentsIncludes) {
            entries.put('environments.includes', plugin.environmentsIncludes.join(','))
        }
        if (plugin.environmentsExcludes) {
            entries.put('environments.excludes', plugin.environmentsExcludes.join(','))
        }
        if (!plugin.enabled) {
            entries.put('enabled', 'false')
        }

        for (Map.Entry<String, SortedSet<String>> entry : artefactsByType.entrySet()) {
            Collection<String> included = entry.value.findAll { String className ->
                !isExcluded(className, plugin.pluginExcludes)
            }
            if (included) {
                entries.put("artefacts.${entry.key}".toString(), included.join(','))
            }
        }

        StringBuilder sb = new StringBuilder()
        sb.append('# Generated by Grails. Do not edit.\n')
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            sb.append(escapeKey(entry.key)).append('=').append(escapeValue(entry.value)).append('\n')
        }
        sb.toString()
    }

    static String normalizePluginName(String raw) {
        if (!raw) {
            return raw
        }
        String trimmed = raw.trim()
        if (!trimmed.contains('-')) {
            return trimmed
        }
        StringBuilder sb = new StringBuilder()
        boolean upperNext = false
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i)
            if (ch == ('-' as char)) {
                upperNext = true
            }
            else if (upperNext) {
                sb.append(Character.toUpperCase(ch))
                upperNext = false
            }
            else {
                sb.append(i == 0 ? Character.toLowerCase(ch) : ch)
            }
        }
        sb.toString()
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
        String regex = pattern
                .replace('.', '\\.')
                .replace('**/', '(.+/)?')
                .replace('**', '.*')
                .replace('*', '[^/]*')
                .replace('?', '.')
        path.matches(regex)
    }

    private static String escapeKey(String key) {
        key.replace('\\', '\\\\').replace('=', '\\=').replace(':', '\\:').replace(' ', '\\ ')
    }

    private static String escapeValue(String value) {
        value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
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
        boolean hasNoArgConstructor
        boolean hasGrailsPluginAnnotation
        boolean hasPluginDescriptorAnnotation
        String explicitName
        String version
        String grailsVersion
        List<String> loadAfter = []
        List<String> loadBefore = []
        Map<String, String> dependsOn = new LinkedHashMap<>()
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

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if ('<init>' == name && '()V' == descriptor && (access & Opcodes.ACC_PRIVATE) == 0) {
                hasNoArgConstructor = true
            }
            null
        }

        boolean isConcrete() {
            (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION)) == 0
        }

        /**
         * Returns {@code true} when this {@code @Artefact}-annotated class should be published as a
         * concrete plugin artefact. Classes under {@code grails-app/} are stamped with
         * {@code @grails.plugins.metadata.GrailsPlugin} by the compiler; hand-annotated base classes
         * in {@code src/main/} (such as {@code RestfulController<T>} or {@code GormService<T>}) carry
         * generic type parameters and/or require constructor arguments and must not be registered as
         * standalone plugin artefacts.
         */
        boolean isProvidedArtefact() {
            artefactType != null && (hasGrailsPluginAnnotation || (!genericClass && hasNoArgConstructor))
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
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                void visit(String ignored, Object value) {
                                    if (value instanceof String && !((String) value).isBlank()) {
                                        String spec = ((String) value).trim()
                                        int sep = spec.indexOf(':')
                                        if (sep > 0) {
                                            String depName = spec.substring(0, sep).trim()
                                            String depVersion = spec.substring(sep + 1).trim()
                                            dependsOn.put(depName, depVersion ?: '*')
                                        }
                                        else {
                                            dependsOn.put(spec, '*')
                                        }
                                    }
                                }
                            }
                        }
                        null
                    }
                }
            }
            if (PLUGIN_EXCLUDES_ANNOTATION == descriptor) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    AnnotationVisitor visitArray(String name) {
                        if ('value' == name) {
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
                    dependsOn,
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

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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import groovy.io.FileType
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.Opcodes

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import grails.util.GrailsNameUtils

/**
 * Generates {@code META-INF/grails/plugins.imports}, per-plugin
 * {@code META-INF/grails/plugins/<name>/plugin.properties} descriptors, and namespaced plugin
 * configuration files ({@code META-INF/grails/plugins/<name>/plugin.yml} or {@code plugin.groovy})
 * by scanning compiled plugin classes with ASM.
 *
 * <p>Supports three plugin authoring models:</p>
 * <ul>
 *     <li>Traditional {@code *GrailsPlugin} Groovy classes</li>
 *     <li>Java or Groovy classes annotated with {@code @GrailsPluginDescriptor}</li>
 *     <li>Descriptor-less starter plugins with no plugin class at all (metadata taken from
 *     {@code grails { plugin { ... } }} and project coordinates)</li>
 * </ul>
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

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getPluginConfigFiles()

    @Input
    abstract Property<String> getProjectName()

    @Input
    @Optional
    abstract Property<String> getProjectVersion()

    @Nested
    abstract GrailsPluginOptions getPluginOptions()

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
        List<ScannedPlugin> plugins = new ArrayList<>(result.plugins)

        String configuredName = trimToNull(pluginOptions.name.getOrNull())
        String configuredVersion = trimToNull(pluginOptions.version.getOrNull()) ?: trimToNull(projectVersion.getOrNull())
        List<String> extraExcludes = pluginOptions.pluginExcludes.getOrElse([])

        if (plugins.isEmpty()) {
            if (!pluginOptions.starter.getOrElse(false)) {
                return
            }
            String rawName = configuredName ?: projectName.get()
            String logicalName = normalizePluginName(rawName)
            plugins.add(new ScannedPlugin(
                    null,
                    logicalName,
                    configuredVersion ?: '0.1',
                    trimToNull(pluginOptions.grailsVersion.getOrNull()),
                    pluginOptions.loadAfter.getOrElse([]),
                    pluginOptions.loadBefore.getOrElse([]),
                    pluginOptions.dependsOn.getOrElse([:]),
                    pluginOptions.evict.getOrElse([]),
                    pluginOptions.observe.getOrElse([]),
                    pluginOptions.environmentsIncludes.getOrElse([]),
                    pluginOptions.environmentsExcludes.getOrElse([]),
                    extraExcludes,
                    pluginOptions.enabled.getOrElse(Boolean.TRUE),
                    true
            ))
        }
        else {
            if (configuredName != null && plugins.size() == 1) {
                plugins = [plugins[0].withLogicalName(normalizePluginName(configuredName))]
            }
            if (extraExcludes) {
                plugins = plugins.collect { ScannedPlugin p -> p.withExtraExcludes(extraExcludes) }
            }
        }

        File configFile = resolvePluginConfigFile(pluginConfigFiles.files)
        writeDescriptors(plugins, result.artefactsByType, configuredVersion, configFile, outDir)
    }

    private static File resolvePluginConfigFile(Set<File> candidates) {
        Collection<File> existing = candidates.findAll { File f -> f.isFile() }
        if (existing.size() > 1) {
            throw new GradleException(
                    "A plugin may define a plugin.yml or a plugin.groovy, but not both: ${existing*.name.sort().join(', ')}"
            )
        }
        existing ? existing.iterator().next() : null
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
            File outDir) {
        SortedSet<String> importNames = new TreeSet<>()
        Map<String, String> seenPluginNames = new LinkedHashMap<>()
        for (ScannedPlugin plugin : plugins) {
            String previous = seenPluginNames.putIfAbsent(plugin.logicalName, plugin.className ?: 'grails.plugin')
            if (previous != null) {
                throw new GradleException(
                        "Duplicate Grails plugin logical name '${plugin.logicalName}' declared by [${previous}] and [${plugin.className ?: 'grails.plugin'}]"
                )
            }
            importNames.add(plugin.logicalName)
            File pluginDir = new File(outDir, "${PLUGIN_DESCRIPTORS_PREFIX}${plugin.logicalName}")
            pluginDir.mkdirs()

            File descriptorFile = new File(pluginDir, PLUGIN_PROPERTIES_FILE)
            descriptorFile.setText(renderProperties(plugin, artefactsByType, defaultVersion), StandardCharsets.UTF_8.name())

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
        importsFile.setText(importNames.join('\n') + '\n', StandardCharsets.UTF_8.name())
    }

    static String renderProperties(
            ScannedPlugin plugin,
            Map<String, SortedSet<String>> artefactsByType,
            String defaultVersion) {
        Map<String, String> entries = new TreeMap<>()
        entries.put('format.version', FORMAT_VERSION)
        entries.put('name', plugin.logicalName)

        String version = trimToNull(plugin.version) ?: trimToNull(defaultVersion)
        if (version) {
            entries.put('version', version)
        }
        if (plugin.className) {
            entries.put('class', plugin.className)
        }
        entries.put('metadata.complete', Boolean.toString(plugin.metadataComplete))

        String grailsVer = trimToNull(plugin.grailsVersion)
        if (grailsVer) {
            entries.put('grailsVersion', grailsVer)
        }
        if (plugin.loadAfter) {
            entries.put('loadAfter', plugin.loadAfter.collect { normalizePluginName(it) }.findAll { it }.join(','))
        }
        if (plugin.loadBefore) {
            entries.put('loadBefore', plugin.loadBefore.collect { normalizePluginName(it) }.findAll { it }.join(','))
        }
        for (Map.Entry<String, String> dep : plugin.dependsOn.entrySet()) {
            String depKey = trimToNull(dep.key)
            if (depKey) {
                entries.put("dependsOn.${normalizePluginName(depKey)}".toString(), trimToNull(dep.value) ?: '*')
            }
        }
        if (plugin.evict) {
            entries.put('evict', plugin.evict.collect { normalizePluginName(it) }.findAll { it }.join(','))
        }
        if (plugin.observe) {
            entries.put('observe', plugin.observe.collect { normalizePluginName(it) }.findAll { it }.join(','))
        }
        if (plugin.environmentsIncludes) {
            entries.put('environments.includes', plugin.environmentsIncludes.collect { trimToNull(it) }.findAll { it }.join(','))
        }
        if (plugin.environmentsExcludes) {
            entries.put('environments.excludes', plugin.environmentsExcludes.collect { trimToNull(it) }.findAll { it }.join(','))
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
        String trimmed = trimToNull(raw)
        if (trimmed == null) {
            return null
        }
        trimmed.contains('-')
                ? GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(trimmed)
                : trimmed
    }

    private static String trimToNull(String value) {
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
            return GrailsNameUtils.getLogicalPropertyName(simpleName, GRAILS_PLUGIN_SUFFIX)
        }
        GrailsNameUtils.getPropertyName(simpleName)
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

    private static String escapeKey(String key) {
        StringBuilder sb = new StringBuilder(key.length())
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i)
            if (ch == ('\\' as char)) {
                sb.append('\\\\')
            }
            else if (ch == ('=' as char)) {
                sb.append('\\=')
            }
            else if (ch == (':' as char)) {
                sb.append('\\:')
            }
            else if (ch == (' ' as char)) {
                sb.append('\\ ')
            }
            else if (ch == ('\t' as char)) {
                sb.append('\\t')
            }
            else if (ch == ('\n' as char)) {
                sb.append('\\n')
            }
            else if (ch == ('\r' as char)) {
                sb.append('\\r')
            }
            else if (ch == ('\f' as char)) {
                sb.append('\\f')
            }
            else {
                sb.append(ch)
            }
        }
        sb.toString()
    }

    private static String escapeValue(String value) {
        StringBuilder sb = new StringBuilder(value.length())
        boolean leading = true
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i)
            if (ch == (' ' as char)) {
                sb.append(leading ? '\\ ' : ' ')
            }
            else if (ch == ('\\' as char)) {
                sb.append('\\\\')
                leading = false
            }
            else if (ch == ('\t' as char)) {
                sb.append('\\t')
                leading = false
            }
            else if (ch == ('\n' as char)) {
                sb.append('\\n')
                leading = false
            }
            else if (ch == ('\r' as char)) {
                sb.append('\\r')
                leading = false
            }
            else if (ch == ('\f' as char)) {
                sb.append('\\f')
                leading = false
            }
            else {
                sb.append(ch)
                leading = false
            }
        }
        sb.toString()
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

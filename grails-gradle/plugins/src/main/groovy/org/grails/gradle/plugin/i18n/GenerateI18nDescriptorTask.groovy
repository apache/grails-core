/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.i18n

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the message-bundle descriptor an artifact contributes to Spring Boot's message source.
 *
 * <p>Grails applications and plugins both ship their bundles at the root of their jar, so at runtime
 * the only way to find them used to be a {@code classpath*:*.properties} scan. That scan is what
 * GraalVM native images cannot do, and it also costs start-up time on the JVM. Recording the answer
 * at build time removes the scan: the descriptor is read back through an exact-name
 * {@code ClassLoader.getResources} lookup, which works in a native image and needs no wildcard
 * resource metadata.</p>
 *
 * <p>The generated {@code META-INF/grails/i18n.properties} is an internal build format, never
 * hand-authored:</p>
 *
 * <pre><code>format.version=1
 * artifact.type=plugin
 * artifact.name=spring-security-core
 * artifact.version=8.0.0
 * basenames=spring-security-core,spring-security-core-validation
 * locales=de,fr</code></pre>
 *
 * <p>The file-name interpretation itself lives in {@link I18nBundleIndex}, which also documents the
 * base-name/locale convention and how to override it.</p>
 *
 * @since 8.0
 */
@DisableCachingByDefault(because = 'Writing a handful of lines is cheaper than a build-cache round trip')
@CompileStatic
abstract class GenerateI18nDescriptorTask extends DefaultTask {

    /** Path of the generated descriptor within the artifact. */
    static final String DESCRIPTOR_PATH = 'META-INF/grails/i18n.properties'

    /** Descriptor format version, so a future reader can reject an artifact it does not understand. */
    static final String FORMAT_VERSION = '1'

    /** {@code artifact.type} for an application. */
    static final String TYPE_APPLICATION = 'application'

    /** {@code artifact.type} for a plugin. */
    static final String TYPE_PLUGIN = 'plugin'

    /**
     * The bundle sources, normally {@code grails-app/i18n}.
     *
     * <p>Only the file <em>names</em> matter, never the contents — {@code native2ascii} and the
     * {@code EscapeUnicode} filter rewrite bundle contents but leave names alone, so this task reads
     * the sources directly and never has to wait for, or read back, the processed resources.</p>
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract DirectoryProperty getBundleDirectory()

    /** Either {@link #TYPE_APPLICATION} or {@link #TYPE_PLUGIN}. */
    @Input
    abstract Property<String> getArtifactType()

    /** The application or plugin name; for a plugin it also constrains the permitted base names. */
    @Input
    abstract Property<String> getArtifactName()

    /** The artifact version, recorded for diagnostics only — it plays no part in ordering. */
    @Input
    @Optional
    abstract Property<String> getArtifactVersion()

    /** Base names declared through {@code grails { i18n { basenames } }}. */
    @Input
    abstract ListProperty<String> getDeclaredBasenames()

    /** Directory contributed to {@code processResources}; holds {@link #DESCRIPTOR_PATH}. */
    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generate() {
        List<String> fileNames = []
        bundleDirectory.get().asFile.listFiles()?.each { File file ->
            if (file.file && file.name.endsWith(I18nBundleIndex.PROPERTIES_SUFFIX)) {
                fileNames << file.name
            }
        }

        I18nBundleIndex index = I18nBundleIndex.from(fileNames, declaredBasenames.get())

        String type = artifactType.get()
        String name = artifactName.get()
        if (type == TYPE_PLUGIN) {
            validatePluginNamespace(index.basenames, name)
        }

        File descriptor = outputDirectory.get().file(DESCRIPTOR_PATH).asFile
        if (index.empty) {
            descriptor.delete()
            descriptor.parentFile.delete()
            return
        }

        descriptor.parentFile.mkdirs()
        StringBuilder text = new StringBuilder()
        text << '# Generated by the Grails Gradle plugin. Do not edit.\n'
        text << "format.version=${FORMAT_VERSION}\n"
        text << "artifact.type=${type}\n"
        text << "artifact.name=${name}\n"
        if (artifactVersion.present && artifactVersion.get()) {
            text << "artifact.version=${artifactVersion.get()}\n"
        }
        text << "basenames=${index.basenames.join(',')}\n"
        text << "locales=${index.locales.join(',')}\n"
        descriptor.setText(text.toString(), 'UTF-8')
    }

    /**
     * Requires every plugin base name to be {@code <plugin-name>} or {@code <plugin-name>-*}.
     *
     * <p>Spring Boot's {@code ResourceBundleMessageSource} resolves a base name to the first matching
     * resource on the classpath, so two plugins sharing a base name would shadow one another instead
     * of both contributing. Plugin names are already unique within an application, so namespacing
     * base names on the plugin name makes collisions impossible while still allowing a plugin to ship
     * several logical bundles.</p>
     */
    private static void validatePluginNamespace(List<String> basenames, String pluginName) {
        List<String> offenders = basenames.findAll { String base ->
            base != pluginName && !base.startsWith(pluginName + '-')
        }
        if (offenders) {
            throw new InvalidUserDataException("""\
Plugin '${pluginName}' ships message bundles outside its own namespace: \
${offenders.collect { "'${it}${I18nBundleIndex.PROPERTIES_SUFFIX}'" }.join(', ')}.
A plugin's base names must be '${pluginName}' or '${pluginName}-*' so they cannot collide with an \
application's bundles or another plugin's. Spring resolves a base name to the first match on the \
classpath, so a colliding bundle is silently shadowed rather than merged.
Rename '${offenders.first()}${I18nBundleIndex.PROPERTIES_SUFFIX}' to \
'${pluginName}${I18nBundleIndex.PROPERTIES_SUFFIX}', or to \
'${pluginName}-<name>${I18nBundleIndex.PROPERTIES_SUFFIX}' if the plugin ships more than one bundle. \
Locale variants follow the base name, so '${pluginName}_fr${I18nBundleIndex.PROPERTIES_SUFFIX}'.
${I18nBundleIndex.UPGRADE_REFERENCE}""")
        }
    }
}

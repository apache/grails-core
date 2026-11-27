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
package org.grails.compiler.injection

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CompileUnit
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.SyntaxException

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore

import grails.plugins.metadata.GrailsPluginDescriptor
import grails.plugins.metadata.PluginDefinition
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter
import org.grails.compiler.beans.GrailsBeansASTTransformation

/**
 * Orders the auto-configuration a plugin's {@code beans} block compiles onto (its sibling) as the plugins
 * are ordered. The sibling comes after the siblings of the plugins the descriptor depends on or names in
 * {@code after}, and before those of the plugins it names in {@code before}; a plugin without a sibling is
 * looked through, to the siblings of the plugins it is ordered against in the same direction. The order is
 * merged into the sibling's {@code @AutoConfiguration} as {@code afterName} and {@code beforeName}, beside
 * any ordering against auto-configurations that are not Grails plugins'.
 *
 * <p>The other plugins are found in the same compilation, and on the class path through their
 * {@code plugin.properties}, whose {@code autoConfiguration} names their sibling. A hand-written ordering
 * on the plugin that names another plugin's sibling is an error: the plugins' order is declared once, on
 * {@link GrailsPluginDescriptor}.</p>
 */
@CompileStatic
@PackageScope
final class PluginAutoConfigurationOrder {

    private static final ClassNode DESCRIPTOR = ClassHelper.make(GrailsPluginDescriptor)
    private static final ClassNode AUTO_CONFIGURATION = ClassHelper.make(AutoConfiguration)
    private static final ClassNode AUTO_CONFIGURE_AFTER = ClassHelper.make(AutoConfigureAfter)
    private static final ClassNode AUTO_CONFIGURE_BEFORE = ClassHelper.make(AutoConfigureBefore)
    private static final String CATALOG_METADATA = PluginAutoConfigurationOrder.name + '.catalog'
    private static final String AFTER_NAME = 'afterName'
    private static final String BEFORE_NAME = 'beforeName'

    private PluginAutoConfigurationOrder() {
    }

    /** A plugin as far as ordering goes: the plugins it comes after and before, and its sibling. */
    private static final class OrderedPlugin {
        final String name
        final List<String> after
        final List<String> before
        final String autoConfiguration

        OrderedPlugin(String name, List<String> after, List<String> before, String autoConfiguration) {
            this.name = name
            this.after = after
            this.before = before
            this.autoConfiguration = autoConfiguration
        }
    }

    /** The plugins of a compilation and of its class path, read once for every plugin compiled. */
    private static final class Catalog {
        final Map<String, OrderedPlugin> compiled = [:]
        final Map<String, Optional<OrderedPlugin>> published = [:]
        final ClassLoader classLoader
        private Map<String, String> pluginsBySibling

        Catalog(CompileUnit unit, ClassLoader classLoader) {
            this.classLoader = classLoader
            for (ModuleNode module : unit.modules) {
                for (ClassNode type : module.classes) {
                    OrderedPlugin plugin = compiledPlugin(type)
                    if (plugin != null) {
                        compiled.put(plugin.name, plugin)
                    }
                }
            }
        }

        OrderedPlugin find(String name) {
            compiled.get(name) ?: published.computeIfAbsent(name) { String key -> Optional.ofNullable(read(key)) }.orElse(null)
        }

        private OrderedPlugin read(String name) {
            URL url = classLoader?.getResource(PluginDescriptorWriter.PLUGIN_DESCRIPTORS_PREFIX + name + '/' +
                    PluginDescriptorWriter.PLUGIN_PROPERTIES_FILE)
            if (url == null) {
                return null
            }
            try {
                PluginDefinition definition = (PluginDefinition) url.withReader('UTF-8') { Reader reader ->
                    PluginDefinition.read(name, reader)
                }
                new OrderedPlugin(definition.pluginName, definition.orderedAfter, definition.before, definition.autoConfiguration)
            }
            catch (IOException | IllegalArgumentException ignored) {
                // a descriptor this compiler cannot read orders nothing; discovery reports it at startup
                null
            }
        }

        /** Every plugin's sibling, compiled now or published on the class path, with the name of its plugin. */
        Map<String, String> pluginsBySibling() {
            if (pluginsBySibling == null) {
                Map<String, String> siblings = [:]
                Enumeration<URL> imports = classLoader?.getResources(PluginDescriptorWriter.PLUGINS_IMPORTS_PATH)
                while (imports?.hasMoreElements()) {
                    for (String line : imports.nextElement().getText('UTF-8').readLines()) {
                        String name = line.trim()
                        if (name && !name.startsWith('#')) {
                            String sibling = find(name)?.autoConfiguration
                            if (sibling) {
                                siblings.put(sibling, name)
                            }
                        }
                    }
                }
                compiled.values().each { OrderedPlugin plugin ->
                    if (plugin.autoConfiguration) {
                        siblings.put(plugin.autoConfiguration, plugin.name)
                    }
                }
                pluginsBySibling = siblings
            }
            pluginsBySibling
        }
    }

    /**
     * Orders the sibling of a plugin compiled in this compilation, if it has one, and reports a hand-written
     * ordering on it that names another plugin's sibling.
     */
    static void apply(ClassNode pluginClass, SourceUnit source, CompilationUnit compilationUnit) {
        ClassNode sibling = (ClassNode) pluginClass.getNodeMetaData(GrailsBeansASTTransformation.AUTO_CONFIGURATION_SIBLING_METADATA)
        OrderedPlugin plugin = compiledPlugin(pluginClass)
        if (sibling == null || plugin == null) {
            return
        }
        AnnotationNode autoConfiguration = sibling.getAnnotations(AUTO_CONFIGURATION).find()
        if (autoConfiguration == null) {
            // reported by the beans transform: a plugin with a beans block must be an @AutoConfiguration
            return
        }
        Catalog catalog = catalog(compilationUnit)
        rejectHandWrittenPluginOrder(sibling, plugin, catalog, source)

        Set<String> after = siblings(plugin.after, catalog, true, [plugin.name] as Set<String>)
        Set<String> before = siblings(plugin.before, catalog, false, [plugin.name] as Set<String>)
        after.remove(sibling.name)
        before.remove(sibling.name)
        merge(autoConfiguration, AFTER_NAME, after)
        merge(autoConfiguration, BEFORE_NAME, before)
    }

    private static Catalog catalog(CompilationUnit compilationUnit) {
        CompileUnit unit = compilationUnit.AST
        Catalog catalog = (Catalog) unit.getNodeMetaData(CATALOG_METADATA)
        if (catalog == null) {
            catalog = new Catalog(unit, compilationUnit.classLoader)
            unit.putNodeMetaData(CATALOG_METADATA, catalog)
        }
        catalog
    }

    private static OrderedPlugin compiledPlugin(ClassNode type) {
        AnnotationNode descriptor = type.getAnnotations(DESCRIPTOR).find()
        if (descriptor == null) {
            return null
        }
        List<String> names = strings(descriptor.getMember('name'))
        String name = PluginDefinition.deriveName(type.name, names ? names.first() : null)
        Set<String> after = new LinkedHashSet<>(PluginDefinition.parseDependencies(strings(descriptor.getMember('dependsOn'))).keySet()
                .collect { String dependency -> PluginDefinition.normalizePluginName(dependency) })
        after.addAll(strings(descriptor.getMember('after')).collect { String other -> PluginDefinition.normalizePluginName(other) })
        List<String> before = strings(descriptor.getMember('before')).collect { String other -> PluginDefinition.normalizePluginName(other) }
        ClassNode sibling = (ClassNode) type.getNodeMetaData(GrailsBeansASTTransformation.AUTO_CONFIGURATION_SIBLING_METADATA)
        new OrderedPlugin(name, List.copyOf(after), before, sibling?.name)
    }

    /**
     * The siblings of the named plugins; through a plugin without one, those of the plugins it comes after (or
     * before) in turn.
     */
    private static Set<String> siblings(List<String> names, Catalog catalog, boolean after, Set<String> visited) {
        Set<String> found = new LinkedHashSet<>()
        for (String name : names) {
            if (!visited.add(name)) {
                continue
            }
            OrderedPlugin other = catalog.find(name)
            if (other?.autoConfiguration) {
                found.add(other.autoConfiguration)
            }
            else if (other != null) {
                found.addAll(siblings(after ? other.after : other.before, catalog, after, visited))
            }
        }
        found
    }

    private static void rejectHandWrittenPluginOrder(ClassNode sibling, OrderedPlugin plugin, Catalog catalog, SourceUnit source) {
        for (AnnotationNode annotation : sibling.annotations) {
            List<String> members
            if (annotation.classNode == AUTO_CONFIGURATION) {
                members = ['before', 'after', BEFORE_NAME, AFTER_NAME]
            }
            else if (annotation.classNode == AUTO_CONFIGURE_AFTER || annotation.classNode == AUTO_CONFIGURE_BEFORE) {
                members = ['value', 'name']
            }
            else {
                continue
            }
            for (String member : members) {
                for (String named : strings(annotation.getMember(member))) {
                    String other = catalog.pluginsBySibling().get(named)
                    if (other != null && other != plugin.name) {
                        String message = "@${annotation.classNode.nameWithoutPackage}(${member} = ...) on the Grails " +
                                "plugin ${plugin.name} names ${named}, the auto-configuration of the Grails plugin " +
                                "${other}: use after/before on @GrailsPluginDescriptor instead, and the compiler " +
                                'orders the plugins\' auto-configurations as it orders the plugins'
                        source.addErrorAndContinue(new SyntaxException(message, annotation.lineNumber, annotation.columnNumber))
                    }
                }
            }
        }
    }

    private static void merge(AnnotationNode annotation, String member, Set<String> generated) {
        if (!generated) {
            return
        }
        Set<String> names = new LinkedHashSet<>(strings(annotation.getMember(member)))
        names.addAll(generated)
        ListExpression list = new ListExpression()
        names.each { String name -> list.addExpression(new ConstantExpression(name)) }
        annotation.setMember(member, list)
    }

    /** The strings, or the names of the classes, an annotation member holds. */
    private static List<String> strings(Expression value) {
        if (value instanceof ListExpression) {
            return ((ListExpression) value).expressions.collectMany { Expression element -> strings(element) }
        }
        if (value instanceof ClassExpression) {
            return [value.type.name]
        }
        if (value instanceof ConstantExpression && ((ConstantExpression) value).value instanceof String) {
            String text = ((String) ((ConstantExpression) value).value).trim()
            return text ? [text] : []
        }
        []
    }
}

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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.LookupFailedException
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo

/**
 * What a template of a feature pack can refer to: template API {@link PackDescriptor#TEMPLATE_API}.
 * It is made of text, numbers, lists and maps only, and none of them can be changed, so a template
 * depends on these names and not on the classes of Forge, which are free to change. A name that is
 * not here is an error. A template that imports a class of Forge can do so, and is then no longer written
 * against this API alone.
 *
 * <table>
 * <caption>The names of template API 1</caption>
 * <tr><td>{@code project.name}</td><td>the name of the application, {@code shop}</td></tr>
 * <tr><td>{@code project.packageName}</td><td>its package, {@code com.example}</td></tr>
 * <tr><td>{@code project.packagePath}</td><td>the package as a path, {@code com/example}</td></tr>
 * <tr><td>{@code project.className}</td><td>the name as that of a class, {@code Shop}</td></tr>
 * <tr><td>{@code project.naturalName}</td><td>the name as words, {@code Shop}</td></tr>
 * <tr><td>{@code project.propertyName}</td><td>the name as that of a property, {@code shop}</td></tr>
 * <tr><td>{@code features}</td><td>the names of every feature of the application, in order of name</td></tr>
 * <tr><td>{@code applicationType}</td><td>{@code web}, {@code rest-api}, {@code plugin} or {@code web-plugin}</td></tr>
 * <tr><td>{@code options.jdk}</td><td>the version of Java the application is for, a number</td></tr>
 * <tr><td>{@code options.data}</td><td>the Grails Data implementation, as {@code --data} takes it: {@code hibernate5}, {@code hibernate7}, {@code mongodb} or {@code neo4j}</td></tr>
 * <tr><td>{@code options.servlet}</td><td>the servlet container, as {@code --servlet} takes it: {@code tomcat}, {@code jetty}, {@code undertow} or {@code none}</td></tr>
 * <tr><td>{@code options.reloading}</td><td>how the application reloads in development, as {@code --reloading} takes it: {@code devtools}, {@code jrebel} or {@code none}</td></tr>
 * <tr><td>{@code grailsVersion}</td><td>the version of Grails the application is generated for</td></tr>
 * <tr><td>{@code pack.name}</td><td>the name of the feature of the pack</td></tr>
 * </table>
 *
 * <p>The templates of a pack Forge has in itself that names artifacts under {@code versionsOfForge} have
 * one name more, which is not of the template API: {@code versionsOfForge}, the version of each of
 * those artifacts by its name.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackModel {

    private PackModel() {
    }

    /**
     * @param generatorContext the application that is generated, with its features chosen
     * @param descriptor the descriptor of the pack the template is of
     * @return the model, which cannot be changed
     */
    static Map<String, Object> of(GeneratorContext generatorContext, PackDescriptor descriptor) {
        Map<String, Object> model = model(generatorContext.getProject().getProperties(), generatorContext.getFeatures(),
                generatorContext.getApplicationType(), generatorContext.getJdkVersion(), generatorContext.getGorm(),
                generatorContext.getServlet(), generatorContext.getDevelopmentReloading(), descriptor)
        if (descriptor.versionsOfForge) {
            Map<String, Object> versions = new LinkedHashMap<>()
            descriptor.versionsOfForge.each { String artifact, String otherwise ->
                try {
                    versions.put(artifact, generatorContext.resolveCoordinate(artifact).getVersion() ?: otherwise)
                } catch (LookupFailedException ignored) {
                    versions.put(artifact, otherwise)
                }
            }
            model.put('versionsOfForge', new Names('versionsOfForge', versions))
        }
        return Collections.unmodifiableMap(model)
    }

    /**
     * Models of applications that are made up, for checking the templates of a pack where no application
     * is generated: an application {@code com.example.shop} with the feature of the pack and the features
     * it requires, and nothing else. There are as many as it takes for every type of application the
     * descriptor names and every value of {@code options.data}, {@code options.servlet} and
     * {@code options.reloading} to be in one of them; they are not every combination, and a combination
     * can be one no application has. The models have the names {@link #of} gives a template of a pack
     * that is not of Forge.
     *
     * @param descriptor the descriptor of the pack the templates are of
     * @return the models, none of which can be changed, each with what tells it from the others
     */
    static Map<String, Map<String, Object>> samples(PackDescriptor descriptor) {
        Map<String, String> project = NameUtils.parse('com.example.shop').getProperties()
        Set<String> features = new TreeSet<String>(descriptor.requires)
        features.add(descriptor.name)
        List<ApplicationType> types = ApplicationType.values().findAll { ApplicationType type -> descriptor.applicationTypes.contains(type) }
        GormImpl[] data = GormImpl.values()
        ServletImpl[] servlets = ServletImpl.values()
        DevelopmentReloading[] reloading = DevelopmentReloading.values()
        int count = [types.size(), data.length, servlets.length, reloading.length].max()
        Map<String, Map<String, Object>> samples = new LinkedHashMap<>()
        for (int index = 0; index < count; index++) {
            Map<String, Object> model = model(project, features, types[index % types.size()], JdkVersion.DEFAULT_OPTION,
                    data[index % data.length], servlets[index % servlets.length], reloading[index % reloading.length], descriptor)
            samples.put("applicationType ${model.get('applicationType')}, options ${model.get('options')}".toString(),
                    Collections.unmodifiableMap(model))
        }
        return samples
    }

    private static Map<String, Object> model(Map<String, String> project, Collection<String> features, ApplicationType applicationType,
                                             JdkVersion jdk, GormImpl data, ServletImpl servlet, DevelopmentReloading reloading,
                                             PackDescriptor descriptor) {
        Map<String, Object> model = new LinkedHashMap<>()
        model.put('project', new Names('project', project))
        model.put('features', Collections.unmodifiableList(new ArrayList<String>(features).toSorted()))
        model.put('applicationType', applicationType.getName().replace('_', '-'))
        // as the options of the create commands are written
        model.put('options', new Names('options', [
                jdk: jdk.majorVersion(),
                data: data.name().toLowerCase(Locale.ROOT),
                servlet: servlet.name().toLowerCase(Locale.ROOT),
                reloading: reloading.name().toLowerCase(Locale.ROOT),
        ] as Map<String, Object>))
        model.put('grailsVersion', VersionInfo.getGrailsVersion())
        model.put('pack', new Names('pack', [name: descriptor.name] as Map<String, Object>))
        return model
    }

    /**
     * The names of one part of the model. It cannot be changed, and asking it for a name it has not is
     * an error where a map would give nothing: a template with a name misspelled, or written for a
     * later template API, fails instead of writing a wrong file.
     */
    private static final class Names extends AbstractMap<String, Object> {

        private final String of
        private final Map<String, Object> values

        Names(String of, Map<String, ?> values) {
            this.of = of
            this.values = Collections.unmodifiableMap(new TreeMap<String, Object>(values))
        }

        @Override
        Set<Map.Entry<String, Object>> entrySet() {
            return values.entrySet()
        }

        @Override
        Object get(Object name) {
            if (!containsKey(name)) {
                List<String> names = []
                for (String each : values.keySet()) {
                    names << "${this.of}.${each}".toString()
                }
                throw new MissingPropertyException("There is no '${this.of}.${name}' for a template: template API ${PackDescriptor.TEMPLATE_API} " +
                        "has ${names}")
            }
            return values.get(name)
        }

        @Override
        boolean containsKey(Object name) {
            return name instanceof String && values.containsKey(name)
        }

        @Override
        Object getOrDefault(Object name, Object otherwise) {
            return containsKey(name) ? values.get(name) : otherwise
        }

        @Override
        Object put(String name, Object value) {
            throw new UnsupportedOperationException("The ${of} of a template cannot be changed")
        }

        @Override
        void setProperty(String name, Object value) {
            put(name, value)
        }

        // a name after a dot is a name of the model and nothing else: not a property of this class
        @Override
        Object getProperty(String name) {
            return get(name)
        }
    }
}

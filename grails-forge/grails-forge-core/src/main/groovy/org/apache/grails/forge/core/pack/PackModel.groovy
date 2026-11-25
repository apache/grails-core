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
 * What a template of a feature pack can refer to: the template API, of which there is one, 1.
 * It is made of text, numbers, lists and maps only, and none of them can be changed, so a template
 * depends on these names and not on the classes of Forge, which are free to change. A name that is
 * not here is an error. A template that imports a class of Forge can do so, and is then no longer written
 * against this API alone.
 *
 * <p>A pack says which template API its templates are written against, and its model is made for that
 * one in one place, which {@link #of(GeneratorContext, PackDescriptor)} and {@link #samples} both go
 * through, so a model that is made up for a check has what a generation gives. A template API is not
 * changed once it is released: what a later version of Forge adds for templates is a template API of
 * its own beside the earlier ones, in {@link #APIS} and in that place. The table here is what the
 * guide says of the names, and {@code PackTemplateApiSpec} holds the two and the models to each other.</p>
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
 * <tr><td>{@code applicationType}</td><td>{@code web}, {@code rest-api}, {@code plugin}, {@code web-plugin} or {@code rest-api-plugin}</td></tr>
 * <tr><td>{@code options.jdk}</td><td>the version of Java the application is for, a number</td></tr>
 * <tr><td>{@code options.data}</td><td>the Grails Data implementation, as {@code --data} takes it: {@code hibernate5}, {@code hibernate7}, {@code mongodb} or {@code neo4j}</td></tr>
 * <tr><td>{@code options.servlet}</td><td>the servlet container, as {@code --servlet} takes it: {@code tomcat}, {@code jetty}, {@code undertow} or {@code none}</td></tr>
 * <tr><td>{@code options.reloading}</td><td>how the application reloads in development, as {@code --reloading} takes it: {@code devtools}, {@code jrebel} or {@code none}</td></tr>
 * <tr><td>{@code grailsVersion}</td><td>the version of Grails the application is generated for</td></tr>
 * <tr><td>{@code pack.name}</td><td>the name of the feature of the pack</td></tr>
 * <tr><td>{@code parameters}</td><td>the value of each parameter the descriptor declares, by its name, as {@code parameters.store}: {@code true} or {@code false} for a {@code boolean}, text for the others</td></tr>
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

    /** The template APIs this version of Forge renders the templates of */
    public static final List<Integer> APIS = List.of(1)

    private PackModel() {
    }

    /**
     * @param generatorContext the application that is generated, with its features chosen
     * @param descriptor the descriptor of the pack the template is of
     * @return the model of the template API the descriptor names, which cannot be changed
     * @throws IllegalArgumentException when this version of Forge does not render that template API
     */
    static Map<String, Object> of(GeneratorContext generatorContext, PackDescriptor descriptor) {
        Map<String, Object> model = model(generatorContext.getProject().getProperties(), generatorContext.getFeatures(),
                generatorContext.getApplicationType(), generatorContext.getJdkVersion(), generatorContext.getApplicationOptions().getGormImpl(),
                generatorContext.getApplicationOptions().getServletImpl(), generatorContext.getApplicationOptions().getDevelopmentReloading(), descriptor,
                PackParameter.values(descriptor, generatorContext.getOptions()))
        if (descriptor.versionsOfForge) {
            Map<String, Object> versions = new LinkedHashMap<>()
            descriptor.versionsOfForge.each { String artifact, String otherwise ->
                try {
                    versions.put(artifact, generatorContext.resolveCoordinate(artifact).getVersion() ?: otherwise)
                } catch (LookupFailedException ignored) {
                    versions.put(artifact, otherwise)
                }
            }
            model.put('versionsOfForge', new Names(descriptor.templateApi, 'versionsOfForge', versions))
        }
        return Collections.unmodifiableMap(model)
    }

    /**
     * Models of applications that are made up, for checking the templates of a pack where no application
     * is generated: an application {@code com.example.shop} with the feature of the pack and the features
     * it requires, and nothing else. There are as many as it takes for every type of application the
     * descriptor names and every value of {@code options.jdk}, {@code options.data},
     * {@code options.servlet} and {@code options.reloading} to be in one of them: the first has the first
     * value of each, the second the second, and one that has run out of values starts over. A parameter
     * of the pack that is a {@code boolean} or a {@code choice} has each of its values in turn alike,
     * the default first; one that is text has its default. They are not
     * every combination, and a combination can be one no application has. The models have the names
     * {@link #of} gives a template of a pack that is not of Forge.
     *
     * @param descriptor the descriptor of the pack the templates are of
     * @return the models, none of which can be changed, each with what tells it from the others
     */
    static Map<String, Map<String, Object>> samples(PackDescriptor descriptor) {
        Map<String, String> project = NameUtils.parse('com.example.shop').getProperties()
        // a feature that is required by its full name is there by its name, as it is in an application
        Set<String> features = new TreeSet<String>(descriptor.requires.collect { String required -> PackFeature.nameOf(required) })
        features.add(descriptor.name)
        List<ApplicationType> types = ApplicationType.values().findAll { ApplicationType type -> descriptor.applicationTypes.contains(type) }
        GormImpl[] data = GormImpl.values()
        ServletImpl[] servlets = ServletImpl.values()
        DevelopmentReloading[] reloading = DevelopmentReloading.values()
        JdkVersion[] jdks = JdkVersion.values()
        // the values a parameter is rendered with, the default first
        Map<String, List<Object>> values = new TreeMap<>()
        for (PackParameter parameter : descriptor.parameters) {
            List<Object> all = parameter.type == PackParameter.BOOLEAN ? [true, false] as List<Object> :
                    parameter.type == PackParameter.CHOICE ? new ArrayList<Object>(parameter.choices) : [parameter.defaultValue] as List<Object>
            all.remove(parameter.defaultValue)
            all.add(0, parameter.defaultValue)
            values.put(parameter.name, all)
        }
        int count = ([types.size(), data.length, servlets.length, reloading.length, jdks.length] + values.values()*.size()).max()
        Map<String, Map<String, Object>> samples = new LinkedHashMap<>()
        for (int index = 0; index < count; index++) {
            Map<String, Object> parameters = new TreeMap<>()
            values.each { String name, List<Object> all -> parameters.put(name, all[index % all.size()]) }
            Map<String, Object> model = model(project, features, types[index % types.size()], jdks[index % jdks.length],
                    data[index % data.length], servlets[index % servlets.length], reloading[index % reloading.length], descriptor, parameters)
            samples.put("applicationType ${model.get('applicationType')}, options ${model.get('options')}".toString() +
                    (parameters.isEmpty() ? '' : ", parameters ${parameters}".toString()), Collections.unmodifiableMap(model))
        }
        return samples
    }

    /**
     * The model of the template API the descriptor names, for an application that is generated and for
     * one that is made up alike: the one place a template API has its names from.
     */
    @SuppressWarnings('ParameterCount')
    private static Map<String, Object> model(Map<String, String> project, Collection<String> features, ApplicationType applicationType,
                                             JdkVersion jdk, GormImpl data, ServletImpl servlet, DevelopmentReloading reloading,
                                             PackDescriptor descriptor, Map<String, Object> parameters) {
        switch (descriptor.templateApi) {
            case 1:
                return api1(project, features, applicationType, jdk, data, servlet, reloading, descriptor, parameters)
            default:
                throw new IllegalArgumentException("The feature pack ${descriptor.name} is written against template API " +
                        "${descriptor.templateApi}, and this version of Forge renders the template APIs ${APIS}")
        }
    }

    @SuppressWarnings('ParameterCount')
    private static Map<String, Object> api1(Map<String, String> project, Collection<String> features, ApplicationType applicationType,
                                            JdkVersion jdk, GormImpl data, ServletImpl servlet, DevelopmentReloading reloading,
                                            PackDescriptor descriptor, Map<String, Object> parameters) {
        int api = 1
        Map<String, Object> model = new LinkedHashMap<>()
        model.put('project', new Names(api, 'project', project))
        model.put('features', Collections.unmodifiableList(new ArrayList<String>(features).toSorted()))
        model.put('applicationType', applicationType.getName().replace('_', '-'))
        // as the options of the create commands are written
        model.put('options', new Names(api, 'options', [
                jdk: jdk.majorVersion(),
                data: data.name().toLowerCase(Locale.ROOT),
                servlet: servlet.name().toLowerCase(Locale.ROOT),
                reloading: reloading.name().toLowerCase(Locale.ROOT),
        ] as Map<String, Object>))
        model.put('grailsVersion', VersionInfo.getGrailsVersion())
        model.put('pack', new Names(api, 'pack', [name: descriptor.name] as Map<String, Object>))
        // the parameters the descriptor declares, each with the value it was given or its default
        model.put('parameters', new Names(api, 'parameters', parameters))
        return model
    }

    /**
     * The names of one part of the model. It cannot be changed, and asking it for a name it has not is
     * an error where a map would give nothing: a template with a name misspelled, or written for a
     * later template API, fails instead of writing a wrong file.
     */
    private static final class Names extends AbstractMap<String, Object> {

        private final int api
        private final String of
        private final Map<String, Object> values

        Names(int api, String of, Map<String, ?> values) {
            this.api = api
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
                throw new MissingPropertyException("There is no '${this.of}.${name}' for a template: template API ${this.api} " +
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

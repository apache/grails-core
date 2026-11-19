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
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.util.VersionInfo

/**
 * What a template of a feature pack can refer to: template API {@link PackDescriptor#TEMPLATE_API}.
 * It is made of text, numbers, lists and maps only, and none of them can be changed, so a template
 * depends on these names and not on the classes of Forge, which are free to change.
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
 * <tr><td>{@code options.data}</td><td>the Grails Data implementation, such as {@code hibernate7}</td></tr>
 * <tr><td>{@code options.servlet}</td><td>the servlet container, such as {@code tomcat}</td></tr>
 * <tr><td>{@code options.reloading}</td><td>how the application reloads in development, such as {@code devtools}</td></tr>
 * <tr><td>{@code grailsVersion}</td><td>the version of Grails the application is generated for</td></tr>
 * <tr><td>{@code pack.name}</td><td>the name of the feature of the pack</td></tr>
 * </table>
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
        Map<String, Object> model = new LinkedHashMap<>()
        model.put('project', Collections.unmodifiableMap(new TreeMap<String, String>(generatorContext.getProject().getProperties())))
        model.put('features', Collections.unmodifiableList(new ArrayList<String>(generatorContext.getFeatures()).toSorted()))
        model.put('applicationType', generatorContext.getApplicationType().getName().replace('_', '-'))
        model.put('options', Collections.unmodifiableMap([
                jdk: generatorContext.getJdkVersion().majorVersion(),
                data: generatorContext.getGorm().getName(),
                servlet: generatorContext.getServlet().getName(),
                reloading: generatorContext.getDevelopmentReloading().getName(),
        ] as Map<String, Object>))
        model.put('grailsVersion', VersionInfo.getGrailsVersion())
        model.put('pack', Collections.unmodifiableMap([name: descriptor.name] as Map<String, Object>))
        return Collections.unmodifiableMap(model)
    }
}

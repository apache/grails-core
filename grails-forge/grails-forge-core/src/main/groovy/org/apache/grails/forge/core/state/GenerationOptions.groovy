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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

/**
 * The options an application was generated with, written as the create commands take them:
 * {@code jdk: 21}, {@code servlet: tomcat}, {@code data: hibernate7}, {@code reloading: devtools}.
 * An application that was created as a project of a Gradle build that is not its own also has
 * {@code layout: subproject}; one that is a build of its own has no {@code layout}.
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode
final class GenerationOptions {

    final JdkVersion jdk
    final ServletImpl servlet
    final GormImpl data
    final DevelopmentReloading reloading

    /** Whether the application has only its own files, and not those of the root of a Gradle build. */
    final boolean subproject

    /** What {@code layout} is for an application that is a project of a build that is not its own. */
    public static final String SUBPROJECT = 'subproject'

    GenerationOptions(JdkVersion jdk, ServletImpl servlet, GormImpl data, DevelopmentReloading reloading) {
        this(jdk, servlet, data, reloading, false)
    }

    GenerationOptions(JdkVersion jdk, ServletImpl servlet, GormImpl data, DevelopmentReloading reloading, boolean subproject) {
        this.jdk = jdk
        this.servlet = servlet
        this.data = data
        this.reloading = reloading
        this.subproject = subproject
    }

    @PackageScope
    static GenerationOptions of(Options options) {
        return new GenerationOptions(options.getJavaVersion(), options.getServletImpl(), options.getGormImpl(), options.getDevelopmentReloading(), options.isSubproject())
    }

    @PackageScope
    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>()
        map.put('jdk', jdk.majorVersion())
        map.put('servlet', written(servlet))
        map.put('data', written(data))
        map.put('reloading', written(reloading))
        if (subproject) {
            // written only for such an application, so that the state of every other one is what it was
            map.put('layout', SUBPROJECT)
        }
        return map
    }

    @PackageScope
    static GenerationOptions read(StateSection section) {
        int major = section.number('jdk', 1, 999)
        JdkVersion jdk = JdkVersion.values().find { JdkVersion each -> each.majorVersion() == major }
        if (jdk == null) {
            throw section.problem('jdk', "is ${major}, a version of Java this version of Grails does not generate for")
        }
        GenerationOptions options = new GenerationOptions(
                jdk,
                ServletImpl.valueOf(named(section, 'servlet', ServletImpl.values() as List<Enum>)),
                GormImpl.valueOf(named(section, 'data', GormImpl.values() as List<Enum>)),
                DevelopmentReloading.valueOf(named(section, 'reloading', DevelopmentReloading.values() as List<Enum>)),
                section.has('layout') && section.oneOf('layout', [SUBPROJECT]) == SUBPROJECT)
        section.end()
        return options
    }

    @PackageScope
    static String written(Enum value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-')
    }

    private static String named(StateSection section, String key, List<Enum> values) {
        String value = section.oneOf(key, values.collect { Enum each -> written(each) })
        return values.find { Enum each -> written(each) == value }.name()
    }
}

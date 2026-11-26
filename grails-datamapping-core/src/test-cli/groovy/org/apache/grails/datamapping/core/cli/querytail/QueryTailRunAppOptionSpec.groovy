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
package org.apache.grails.datamapping.core.cli.querytail

import spock.lang.Specification

import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.apache.grails.gradle.model.cli.CommandCatalogBuilder
import org.apache.grails.gradle.model.cli.CommandOptions

/**
 * The option of {@code run-app} that turns capture on, {@code --query-tail}: the command line artifact
 * of Grails Data declares it, beside the command that reads the capture, and the build of a project
 * lists it as it lists the options of any plugin.
 */
class QueryTailRunAppOptionSpec extends Specification {

    private static final String ORIGIN = 'org.apache.grails.data:grails-datamapping-core-cli:test'

    void "the command line artifact declares --query-tail for run-app, which sets the property that turns capture on"() {
        given: 'the declarations of this module among those of the class path, in the directory or the jar they are in'
        List<File> elements = getClass().classLoader.getResources(CommandOptions.LOCATION).toList()
                .findAll { URL url -> url.toString().contains('grails-datamapping-core') }
                .collect { URL url -> elementOf(url) }

        when: 'the catalog of a project that has the artifact is put together'
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        elements.each { File element -> builder.add(ORIGIN, element) }
        Map<String, Object> catalog = builder.build()

        then:
        elements.size() == 1
        catalog.diagnostics == []
        catalog.commandOptions == [[command: 'run-app', name: 'query-tail', description: 'Capture every statement GORM runs, for the query-tail command.',
                                    type: 'boolean', property: 'grails.gorm.queryTail.enabled', value: 'true', origin: ORIGIN]]

        and: 'the property is the one the application reads, and the option is the one the command names where there is no capture'
        QueryTailSettings.SETTING_ENABLED == catalog.commandOptions[0].property
        QueryTailRunner.START_HINT.contains("grails run-app --${catalog.commandOptions[0].name}")
    }

    // the directory, or the jar, a resource of the class path is in
    private static File elementOf(URL resource) {
        String location = resource.toString()
        if (location.startsWith('jar:')) {
            return new File(new URI(location.substring(4, location.indexOf('!/'))))
        }
        File file = new File(resource.toURI())
        CommandOptions.LOCATION.split('/').each { String step -> file = file.parentFile }
        return file
    }
}

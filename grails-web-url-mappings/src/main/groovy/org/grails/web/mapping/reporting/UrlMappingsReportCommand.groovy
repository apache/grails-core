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
package org.grails.web.mapping.reporting

import grails.dev.commands.ApplicationCommand
import grails.dev.commands.ExecutionContext
import grails.web.mapping.UrlMappings
import grails.web.mapping.reporting.UrlMappingsRenderer
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Commons


/**
 * A {@link ApplicationCommand} that renders the URL mappings
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@EqualsAndHashCode
@Commons
class UrlMappingsReportCommand implements ApplicationCommand {

    final String description = "Prints out a report of the project's URL mappings"

    @Override
    boolean handle(ExecutionContext executionContext) {
        try {
            def urlMappings = applicationContext.getBean("grailsUrlMappingsHolder", UrlMappings)

            UrlMappingsRenderer renderer = new AnsiConsoleUrlMappingsRenderer()
            renderer.render(urlMappings.getUrlMappings().toList())
            return true
        } catch (Throwable e) {
            log.error("Failed to render URL mappings: ${e.message}", e)
            return false
        }
    }
}

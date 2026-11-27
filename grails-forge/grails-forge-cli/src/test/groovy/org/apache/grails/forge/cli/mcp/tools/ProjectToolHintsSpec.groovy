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
package org.apache.grails.forge.cli.mcp.tools

import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.McpTool
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The hints of a tool of the application or of a plugin reach the client only where the server vouches
 * for the tool's origin: the application, and the plugins of Grails. A plugin cannot have its calls run
 * beside others, or approved with less friction, by saying that it changes nothing.
 */
class ProjectToolHintsSpec extends Specification {

    static final Map<String, Object> READ_ONLY = [readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false]

    @Unroll
    void "the hints of a tool of #origin are passed on: #vouched"() {
        expect:
        ProjectToolHints.vouchedFor(origin) == vouched
        ProjectToolHints.annotations([origin: origin, hints: READ_ONLY]) == (vouched ? READ_ONLY : [:])

        where:
        origin                                                           || vouched
        'application'                                                    || true
        'org.apache.grails:grails-data-hibernate7-dbmigration-cli:9.0.0' || true
        'org.apache.grails.data:grails-data-tools:9.0.0'                 || true
        'org.apache.grails:x'                                            || true
        'org.example:example-tools-cli:1.0.0'                            || false
        'org.apache.grailsx:spoof:1.0.0'                                 || false
        'org.apache:grails:1.0.0'                                        || false
        'com.org.apache.grails:x:1.0.0'                                  || false
        'org.apache.grails'                                              || false
        ':org.apache.grails:x:1'                                         || false
        'Application'                                                    || false
        'built-in'                                                       || false
        'feature:acme-audit'                                             || false
        ''                                                               || false
        null                                                             || false
    }

    void "a tool that declares no hints has the defaults of the protocol, and a hint it leaves out has its default"() {
        expect:
        ProjectToolHints.annotations([origin: 'application']) == [:]
        ProjectToolHints.annotations([origin: 'application', hints: [:]]) == [:]
        ProjectToolHints.annotations([origin: 'application', hints: [readOnlyHint: true, openWorldHint: 'no']]) ==
                [readOnlyHint: true, destructiveHint: true, idempotentHint: false, openWorldHint: true]
        ProjectToolHints.annotations([origin: 'application', hints: 'read-only']) == [:]
        ProjectToolHints.DEFAULTS == [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: true]
    }

    void "the tools of the sample get their hints by their origin, and their declaration stays in _meta"() {
        when:
        Map<String, McpTool> tools = McpToolSamples.tools('application-with-tools').tools.collectEntries { [(it.name): it] }

        then: 'of the application, and of plugins of Grails'
        tools['audit-report'].annotations == tools['audit-report'].meta[McpNames.META_COMMAND].hints
        tools['dbm-update'].annotations == tools['dbm-update'].meta[McpNames.META_COMMAND].hints
        tools['generate-all'].annotations == tools['generate-all'].meta[McpNames.META_COMMAND].hints

        and: 'not of another plugin: it says it changes nothing, and is not taken at its word'
        tools['example-report'].annotations == [:]
        tools['example-report'].meta[McpNames.META_COMMAND].hints == READ_ONLY

        and: 'so it is not offered with --read-only'
        !('example-report' in McpToolSamples.tools('application-with-tools', true).tools*.name)
    }
}

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
package org.apache.grails.gradle.model.cli

import spock.lang.Specification

/**
 * The names a descriptor gives what a tool declares of its command line and of what it does: members
 * a reader of format 1 that does not know them ignores, so they take no name a member of the format
 * already has, and the values of {@code incomplete} and {@code type} that the writer, the catalog and
 * the command line agree on.
 */
class ToolDescriptorDeclarationsSpec extends Specification {

    def "the members of what a tool declares are new members of format 1, which a reader that does not know them ignores"() {
        given:
        Map<String, Object> written = (Map<String, Object>) StrictJson.parse(
                ToolDescriptor.describe('example.ReportTool', 'report', 'Reports', ToolDescriptor.TARGET_APPLICATION, true, []), ToolDescriptor.MAX_DEPTH)
        List<String> declarations = [ToolDescriptor.MEMBER_OPTIONS, ToolDescriptor.MEMBER_PARAMETERS, ToolDescriptor.MEMBER_HINTS,
                                     ToolDescriptor.MEMBER_READS_INPUT, ToolDescriptor.MEMBER_INCOMPLETE]

        expect:
        declarations == ['options', 'parameters', 'hints', 'readsInput', 'incomplete']
        declarations.every { !written.containsKey(it) }
        !declarations.any { it in [ToolDescriptor.MEMBER_TARGET, ToolDescriptor.MEMBER_STARTS_APPLICATION, ToolDescriptor.MEMBER_DEPRECATED] }
    }

    def "a descriptor with the members of what a tool declares is read as it was without them"() {
        given:
        String plain = ToolDescriptor.describe('example.ReportTool', 'report', 'Reports')
        String declaring = plain.substring(0, plain.lastIndexOf('}')) +
                ',"options":[{"names":["--since","-s"],"arity":"1","type":"string","label":"DATE"}],"parameters":[],' +
                '"hints":{"readOnlyHint":true,"destructiveHint":true,"idempotentHint":false,"openWorldHint":true},"readsInput":true,"incomplete":["arguments"]}'

        when:
        ToolDescriptor.Described read = ToolDescriptor.read('example.ReportTool', declaring.getBytes('US-ASCII'))

        then:
        read.name == 'report'
        read.description == 'Reports'
        read.target == ToolDescriptor.TARGET_APPLICATION
        read.startsApplication
    }

    def "a tool lacks its description, the declaration of its arguments or its hints, each said once"() {
        expect:
        ToolDescriptor.INCOMPLETE == ['description', 'arguments', 'hints']
        ToolDescriptor.INCOMPLETE == [ToolDescriptor.INCOMPLETE_DESCRIPTION, ToolDescriptor.INCOMPLETE_ARGUMENTS, ToolDescriptor.INCOMPLETE_HINTS]

        when:
        ToolDescriptor.INCOMPLETE << 'more'

        then:
        thrown(UnsupportedOperationException)
    }

    def "the type of an option or a parameter is one of the types of help --json"() {
        expect:
        ToolDescriptor.TYPES == ['boolean', 'string', 'integer', 'number', 'map']
        ToolDescriptor.TYPES == [ToolDescriptor.TYPE_BOOLEAN, ToolDescriptor.TYPE_STRING, ToolDescriptor.TYPE_INTEGER,
                                 ToolDescriptor.TYPE_NUMBER, ToolDescriptor.TYPE_MAP]

        when:
        ToolDescriptor.TYPES << 'object'

        then:
        thrown(UnsupportedOperationException)
    }
}

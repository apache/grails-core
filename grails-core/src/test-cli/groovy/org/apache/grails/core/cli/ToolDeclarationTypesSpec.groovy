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
package org.apache.grails.core.cli

import java.lang.reflect.Field

import groovy.transform.CompileStatic

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.build.parsing.CommandLineParser

/**
 * The types a tool declares what it takes and what it does with: the options and parameters of its
 * command line, its hints, whether it reads what is typed, and the result it gives besides what it
 * prints. These are what is read of a tool without running it, so their members and their defaults
 * are part of every tool that is written.
 */
class ToolDeclarationTypesSpec extends Specification {

    @Unroll
    def "the hint #hint is #defaultValue where a tool does not say otherwise, as for a tool of the Model Context Protocol"() {
        expect:
        ToolHints.getMethod(hint).defaultValue == defaultValue

        where:
        hint              || defaultValue
        'readOnlyHint'    || false
        'destructiveHint' || true
        'idempotentHint'  || false
        'openWorldHint'   || true
    }

    def "the hints are the four of a tool of the Model Context Protocol, given on the class and read at run time"() {
        when:
        ToolHints hints = DeclarationsSpecReportTool.getAnnotation(ToolHints)

        then:
        ToolHints.declaredMethods*.name as Set == ['readOnlyHint', 'destructiveHint', 'idempotentHint', 'openWorldHint'] as Set
        hints.readOnlyHint()
        hints.destructiveHint()
        !hints.idempotentHint()
        !hints.openWorldHint()
    }

    def "a tool declares its options and parameters on its fields, with what is not given left at its default"() {
        when:
        ToolOption since = field('since').getAnnotation(ToolOption)
        ToolOption format = field('format').getAnnotation(ToolOption)
        ToolParameter output = field('output').getAnnotation(ToolParameter)

        then:
        since.names() == ['--since', '-s'] as String[]
        since.label() == 'DATE'
        since.description() == 'The first day of the report'
        !since.required()
        format.defaultValue() == 'TEXT'
        format.label() == ''
        format.arity() == ''

        and: 'a parameter is required unless it says otherwise'
        output.index() == 0
        output.label() == 'OUTPUT'
        output.required()
        output.arity() == ''
        !field('target').getAnnotation(ToolParameter).required()
    }

    def "a tool declares on its class the options and parameters it reads itself, more than one of each"() {
        when:
        ToolOption[] options = DeclarationsSpecReportTool.getAnnotationsByType(ToolOption)
        ToolParameter[] parameters = DeclarationsSpecReportTool.getAnnotationsByType(ToolParameter)

        then: 'in the order they are written, each with how many values it takes'
        options*.names() == [['--verbose'] as String[], ['--contexts'] as String[]]
        options*.arity() == ['0', '1']
        parameters*.label() == ['TAG', 'REST']
        parameters*.arity() == ['1', '0..*']

        and: 'held together as the class has them'
        DeclarationsSpecReportTool.getAnnotation(ToolOptions).value().length == 2
        DeclarationsSpecReportTool.getAnnotation(ToolParameters).value().length == 2
    }

    def "a tool reads nothing that is typed unless it says that it does"() {
        expect:
        !new DeclarationsSpecReportTool().readsInput
        new DeclarationsSpecAskingTool().readsInput
        new DeclarationsSpecAskingStandaloneTool().readsInput
    }

    def "a tool gives a result besides what it prints, which is no part of what makes two contexts the same or of how one is shown"() {
        given:
        ExecutionContext context = new ExecutionContext(new CommandLineParser().parse('report'))

        expect:
        context.result == null

        when:
        new DeclarationsSpecReportTool().handle(context)

        then:
        context.result == [count: 3, files: ['a.txt', 'b.txt', 'c.txt']]
        context == new ExecutionContext(context.commandLine)
        context.hashCode() == new ExecutionContext(context.commandLine).hashCode()
        !context.toString().contains('a.txt')

        and: 'a context is made as it was before'
        ExecutionContext.declaredConstructors.every { !it.parameterTypes.toList().contains(Object) }
    }

    private static Field field(String name) {
        DeclarationsSpecReportTool.getDeclaredField(name)
    }
}

enum DeclarationsSpecFormat {
    TEXT, JSON
}

@CompileStatic
@ToolHints(readOnlyHint = true, openWorldHint = false)
@ToolOption(names = ['--verbose'], arity = '0', description = 'Says more')
@ToolOption(names = ['--contexts'], arity = '1', label = 'CONTEXTS', description = 'The contexts to run')
@ToolParameter(index = 1, label = 'TAG', arity = '1', description = 'The tag')
@ToolParameter(index = 2, label = 'REST', arity = '0..*', required = false, description = 'The rest')
class DeclarationsSpecReportTool implements ApplicationTool {

    @ToolOption(names = ['--since', '-s'], label = 'DATE', description = 'The first day of the report')
    String since

    @ToolOption(names = ['--format'], defaultValue = 'TEXT', description = 'The format of the report')
    DeclarationsSpecFormat format = DeclarationsSpecFormat.TEXT

    @ToolParameter(index = 0, label = 'OUTPUT', description = 'The file to write')
    String output

    @ToolParameter(index = 3, label = 'TARGET', description = 'What the report is of', required = false)
    String target

    @Override
    boolean handle(ExecutionContext executionContext) {
        executionContext.result = [count: 3, files: ['a.txt', 'b.txt', 'c.txt']]
        true
    }
}

@CompileStatic
class DeclarationsSpecAskingTool implements ApplicationTool {

    boolean readsInput = true

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class DeclarationsSpecAskingStandaloneTool implements StandaloneTool, ToolSupport {

    boolean readsInput = true

    boolean handle() {
        true
    }
}

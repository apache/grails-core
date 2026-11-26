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

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.grails.build.parsing.CommandLineParser

/**
 * The types a tool of an application or a plugin is written with: the two kinds of tool, the
 * helpers either kind can add, the names Grails 8 had for them, and what a tool is told of its
 * project.
 */
class ToolTypesSpec extends Specification {

    @TempDir
    File dir

    @Unroll
    def "the class #type.simpleName is the tool #name"() {
        expect:
        type.getDeclaredConstructor().newInstance().name == name
        type.getDeclaredConstructor().newInstance().description == name

        where:
        type                          || name
        TypesSpecCreateReportTool     || 'types-spec-create-report'
        TypesSpecCreateReportCommand  || 'types-spec-create-report'
        TypesSpecFilesTool            || 'types-spec-files'
        TypesSpecToolCommand          || 'types-spec-tool'
        TypesSpecOldCommand           || 'types-spec-old'
    }

    def "an application tool starts the application unless it says that it does not"() {
        expect:
        new TypesSpecCreateReportTool().startsApplication
        new TypesSpecCreateReportTool().applicationContext == null
        !new TypesSpecCreateReportCommand().startsApplication
        new TypesSpecOldCommand().startsApplication
    }

    def "a tool is of one kind, and a class written with a name of Grails 8 is an application tool"() {
        expect:
        GrailsTool.isAssignableFrom(ApplicationTool)
        GrailsTool.isAssignableFrom(StandaloneTool)
        GrailsTool.isAssignableFrom(ToolSupport)
        !ApplicationTool.isAssignableFrom(StandaloneTool)
        !StandaloneTool.isAssignableFrom(ApplicationTool)

        and:
        ApplicationTool.isAssignableFrom(ApplicationCommand)
        ApplicationCommand.isAssignableFrom(GrailsApplicationCommand)
        ToolSupport.isAssignableFrom(GrailsApplicationCommand)
        ApplicationCommand.getAnnotation(Deprecated) != null
        GrailsApplicationCommand.getAnnotation(Deprecated) != null

        and:
        new TypesSpecOldCommand() instanceof ApplicationTool
        new TypesSpecOldCommand() instanceof ToolSupport
        !(new TypesSpecFilesTool() instanceof ApplicationTool)
        !new TypesSpecFilesTool().hasProperty('applicationContext')
    }

    @Unroll
    def "a tool with the helpers, #kind, is given its arguments, its context and the directory of the project"() {
        given:
        ExecutionContext context = new ExecutionContext(new CommandLineParser().parse('name', 'one', '--flag=value', 'two'))

        expect:
        tool.handle(context)
        tool.seen == ['one', 'two']
        tool.executionContext.is(context)
        tool.templateRenderer != null
        tool.fileSystemInteraction != null
        tool.file('build.gradle').path.endsWith('build.gradle')
        tool.model('example.Book').className == 'Book'

        where:
        kind                              | tool
        'standalone'                      | new TypesSpecFilesTool()
        'of the application'              | new TypesSpecCreateReportCommand()
        'written with a name of Grails 8' | new TypesSpecOldCommand()
    }

    def "a tool is told what its project is from the record of the command line, and nothing of a project without one"() {
        given:
        File recorded = new File(dir, 'recorded')
        new File(recorded, '.grails').mkdirs()
        new File(recorded, '.grails/forge.lock.yml').text = 'format: 1\napplicationType: rest-api\nfeatures:\n- name: views-json\n  source: bundled-pack\n- name: h2\n'

        when:
        ProjectInfo project = ProjectInfo.of(recorded)

        then:
        project.recorded
        project.applicationType == 'rest-api'
        project.features == ['views-json', 'h2']

        when:
        project.features << 'gsp'

        then:
        thrown(UnsupportedOperationException)

        when:
        ProjectInfo none = ProjectInfo.of(new File(dir, 'not-recorded'))

        then:
        !none.recorded
        none.applicationType == null
        none.features == []
    }

    def "the execution context has what the project is, read once from the directory of the project"() {
        given:
        ExecutionContext context = new ExecutionContext(new CommandLineParser().parse('name'))

        when:
        ProjectInfo project = context.project

        then: 'whatever the directory the tests run in is, there is an answer, and it is the same one'
        project != null
        project.recorded == ProjectInfo.of(context.baseDir).recorded
        project.applicationType == ProjectInfo.of(context.baseDir).applicationType
        project.features == ProjectInfo.of(context.baseDir).features
        context.project.is(project)

        and: 'it is no part of what makes two contexts the same, or of how one is shown'
        context == new ExecutionContext(context.commandLine)
        !context.toString().contains('ProjectInfo')
    }
}

class TypesSpecCreateReportTool implements ApplicationTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class TypesSpecCreateReportCommand implements ApplicationTool, ToolSupport {

    boolean startsApplication = false

    List<String> seen

    boolean handle() {
        seen = args
        true
    }
}

class TypesSpecFilesTool implements StandaloneTool, ToolSupport {

    List<String> seen

    boolean handle() {
        seen = args
        true
    }
}

class TypesSpecToolCommand implements StandaloneTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class TypesSpecOldCommand implements GrailsApplicationCommand {

    List<String> seen

    boolean handle() {
        seen = args
        true
    }
}

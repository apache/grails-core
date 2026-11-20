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

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.IgnoreIf
import spock.lang.TempDir

/**
 * That a pack can write the views applications really have: every view of the example applications in
 * the sources of Grails, made a template the way the guide says a view is made one, is written back
 * byte for byte.
 */
class PackTemplateViewsSpec extends ApplicationContextSpec {

    @TempDir
    Path directory

    /**
     * What the guide tells the author of a pack to do with a view: a backslash in front of every
     * expression, scriptlet, directive and comment of the view, and in front of its instruction tags.
     * The other tags of the view need nothing.
     */
    static String asTemplate(String view) {
        return view.replace('${', '\\${').replace('<%', '\\<%').replace('%{', '\\%{')
                .replaceAll('<(/?)g:(' + PackTemplateSource.INSTRUCTIONS.toSorted { -it.length() }.join('|') + ')(?=[\\s>/])', '\\\\<$1g:$2')
    }

    @IgnoreIf(value = { RepositoryFiles.views().isEmpty() }, reason = 'the example applications are not beside Forge in these sources')
    void "every view of the example applications is written back as it is by a template made of it"() {
        given:
        Map<String, File> views = RepositoryFiles.views()
        Map<String, String> wrong = new TreeMap<>()

        when:
        views.each { String path, File file ->
            String view = file.getText('UTF-8')
            String written = write(asTemplate(view))
            if (written != view) {
                wrong.put(path, written)
            }
        }

        then: 'the views are of every kind: with layouts, scriptlets, directives, comments, tags of plugins and maps in attributes'
        views.size() > 500
        views.values().count { it.getText('UTF-8').contains('<head>') } > 50
        views.values().count { it.getText('UTF-8').contains('<%--') } > 50
        views.values().count { it.getText('UTF-8').contains('<%@ page') } > 50
        views.values().count { it.getText('UTF-8') =~ /="\[[^"]*:[^"]*\]"/ } > 10
        views.values().count { it.getText('UTF-8').contains('<g:if') } > 50
        views.values().count { it.getText('UTF-8').contains('<asset:') } > 50

        and:
        wrong.keySet().toList() == []
    }

    void "a view with a directive, a licence in a comment, a scriptlet and tags of every kind is written back as it is"() {
        given:
        String view = '''\
%{--
  - Licensed to Example under one or more agreements, see ${notice}.
  --}%
<%-- and a comment of the other form --%>
<%@ page import="com.example.Book" contentType="text/html;charset=UTF-8" %>
<!doctype html>
<html>
<head>
    <meta name="layout" content="main"/>
    <g:set var="entityName" value="${message(code: 'book.label', default: 'Book')}"/>
    <title><g:message code="default.show.label" args="[entityName]"/></title>
    <asset:stylesheet src="application.css"/>
</head>
<body class="show">
<sec:ifLoggedIn>
    <g:link class="list" action="index" params="[max: 10, sort: 'title']"><g:message code="default.list.label" args="[entityName]" /></g:link>
</sec:ifLoggedIn>
<g:if test="${flash.message}">
    <div class="message" role="status">${flash.message}</div>
</g:if>
<g:elseif test="${book?.authors}">
    <ul>
    <g:each in="${book.authors}" var="author" status="i">
        <li class="${(i % 2) == 0 ? 'even' : 'odd'}"><tmpl:author author="${author}"/></li>
    </g:each>
    </ul>
</g:elseif>
<g:else><p>None</p></g:else>
<% def total = book.pages ?: 0 %>
<p><%= total %> pages, $5 or more, 100%</p>
<g:form resource="${this.book}" method="DELETE">
    <fieldset class="buttons">
        <g:link class="edit" action="edit" resource="${this.book}"><g:message code="default.button.edit.label" default="Edit" /></g:link>
        <input class="delete" type="submit" value="${message(code: 'default.button.delete.label', default: 'Delete')}"
               onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure?')}');" />
    </fieldset>
</g:form>
<g:render template="/shared/footer" model="[book: book, year: 2024]"/>
<script>var a = {b: [1, 2]}; if (a.b.length > 1 && a < 3) { $('#x').text(`${a.b}`); }</script>
</body>
</html>
'''

        expect:
        write(asTemplate(view)) == view
    }

    private String write(String template) {
        Path packDirectory = Files.createDirectories(directory.resolve("pack${Files.list(directory).withCloseable { it.count() }}"))
        Files.writeString(packDirectory.resolve('feature.yml'), PackGenerationSpec.HEADER + 'name: acme-views\n' +
                "files:\n  - from: view.gsp\n    to: grails-app/views/acme/view.gsp\n")
        Files.createDirectories(packDirectory.resolve('templates'))
        Files.writeString(packDirectory.resolve('templates/view.gsp'), template)
        Pack pack = new PackLoader().fromDirectory(packDirectory)
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        try {
            GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                    ['acme-views'], ConsoleOutput.NOOP, [pack])
            generator.generate(ApplicationType.WEB, project, output, context)
        } catch (IllegalArgumentException e) {
            return "FAILS ${e.message}"
        } finally {
            packDirectory.toFile().deleteDir()
        }
        return output.getProject()['grails-app/views/acme/view.gsp']
    }
}

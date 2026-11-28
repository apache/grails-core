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
import spock.lang.Requires
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * That a pack can write the views applications really have: a view of the sources of Grails, made a
 * template the way the guide says a view is made one, is written back byte for byte.
 *
 * <p>The views are copies, under {@code pack-template-views} of the resources of the tests, each
 * chosen for what a page reads in it; {@code views.txt} there says where each is from. Every
 * {@code .gsp} file of the sources of Grails is tried instead of the copies when the tests are run with
 * {@code -PallRepositoryViews}:
 * {@code ./gradlew :grails-forge-core:test --tests '*PackTemplateViewsSpec' -PallRepositoryViews}.</p>
 */
class PackTemplateViewsSpec extends ApplicationContextSpec {

    static final String ALL = 'forge.packTemplateViews.all'

    @TempDir
    Path directory

    /**
     * What the guide tells the author of a pack to do with a view: a backslash in front of every
     * <code>${</code>, {@code <%} and <code>%{</code> of the view and in front of its instruction tags,
     * and the backslashes the view already has in front of one of those or of any tag doubled.
     */
    static String asTemplate(String view) {
        return view.replaceAll('(\\\\*)(\\$\\{|<%|%\\{|</?([A-Za-z]\\w*):(\\w*))') { String all, String backslashes, String read, String namespace, String name ->
            boolean escaped = namespace == null || (namespace == PackTemplateSource.NAMESPACE && name in PackTemplateSource.INSTRUCTIONS)
            return backslashes * 2 + (escaped ? '\\' : '') + read
        }
    }

    /**
     * The same with a backslash in front of every tag of the view: an author need not know which tags
     * are instructions.
     */
    static String asTemplateWithEveryTagEscaped(String view) {
        return view.replaceAll('(\\\\*)(\\$\\{|<%|%\\{|</?[A-Za-z]\\w*:)', '$1$1\\\\$2')
    }

    static Map<String, String> copies() {
        Map<String, String> views = new TreeMap<>()
        PackTemplateViewsSpec.getResourceAsStream('/pack-template-views/views.txt').getText('UTF-8').readLines()
                .findAll { String line -> line && !line.startsWith('#') }
                .each { String line ->
                    String name = line.split(/ \\| /)[0]
                    views.put(name, PackTemplateViewsSpec.getResourceAsStream("/pack-template-views/${name}").getText('UTF-8'))
                }
        return views
    }

    void "the copies of views are of every kind of thing a page reads"() {
        given:
        Collection<String> views = copies().values()

        expect:
        views.size() > 25
        ['@{', '<%--', '%{--', '<%@', '<% ', '<%=', '<head>', '<body', '<meta', '<title>', '<g:layoutBody', '<sec:', '<asset:', '<f:', '<s2ui:',
         '<g:if', '<g:else>', '<g:elseif', '<g:each', '<g:while', '<g:collect', '<g:findAll', '<g:set', '<g:render', '<g:form', '<g:link',
         '<g:message', '<g:hasErrors', '<g:eachError', '<g:applyLayout', '<g:include', '<g:formatDate', '<g:select', '<g:paginate', '<script',
         '<style', '\\${', '?.', '&hellip;', '<!--', ' />', '="[', "='"].every { String kind -> views.count { it.contains(kind) } >= 1 }
    }

    @Unroll
    void "the view #name is written back as it is by a template made of it"() {
        expect:
        write(asTemplate(view)) == view
        write(asTemplateWithEveryTagEscaped(view)) == view

        where:
        name << copies().keySet()
        view = copies()[name]
    }

    void "views in which the things a template reads share a character are written back as they are"() {
        given: 'the starts of what is read, of which <% and %{ share the percent sign, next to each other'
        List<String> views = ['a<%{-- c --}%b', 'a<%{ x }%b', '<%{', 'a%><%{}b', 'a<%{<g:if', 'a<%{<g:link>b', 'a\\<%{ x }%b', 'a\\\\<%{b', '<%{${a}', '<%{%{b}%', '%{<%{<%', '<%%{b',
                              '$<%{', '<<%{', '<%{-- <%{ --}%', '^p<%{ }%', '<%{}%>', '${<%{'].collect { String view -> view + ' and more' }

        and: 'views made of them, of a backslash and of what ends them'
        List<String> parts = ['<', '%', '{', '$', '\\', '}', '-', '>', '<%', '%{', '${', '<g:if', '</g:if>', '<x:y>', '@{', '!{', '^p', 'a', ' ']
        Random random = new Random(16348)
        60.times {
            views << 'a' + (1..(1 + random.nextInt(6))).collect { parts[random.nextInt(parts.size())] }.join('') + 'aaaa'
        }

        expect:
        writeAll(views.collect { String view -> asTemplate(view) }) == views
        writeAll(views.collect { String view -> asTemplateWithEveryTagEscaped(view) }) == views
    }

    @Requires(value = { System.getProperty(ALL) == 'true' }, reason = 'takes every view of the sources of Grails and is asked for with -PallRepositoryViews')
    void "every view of the sources of Grails is written back as it is by a template made of it"() {
        given:
        Map<String, File> views = RepositoryFiles.views()
        Map<String, String> wrong = new TreeMap<>()

        when:
        views.each { String path, File file ->
            String view = file.getText('UTF-8')
            if (write(asTemplate(view)) != view) {
                wrong.put(path, 'with its instructions escaped')
            } else if (write(asTemplateWithEveryTagEscaped(view)) != view) {
                wrong.put(path, 'with every tag escaped')
            }
        }
        println "PackTemplateViewsSpec: ${views.size()} views of the sources of Grails, ${views.size() - wrong.size()} written back as they are, ${wrong.size()} not"

        then:
        views.size() > 500
        wrong == [:]
    }

    void "a view with a directive, a licence in a comment, a scriptlet and tags of every kind is written back as it is"() {
        given:
        String view = '''\
@{ model="com.example.Book book; List<com.example.Author> authors" }
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
<p title="\\${escaped} and \\\\${twice}">!{ x }! ^p c:\\dir\\file \\<% \\%{ \\<g:if> \\<g:link> \\<p></p>
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

    /**
     * What a pack with these templates writes, each to a file of its own, in one generation.
     */
    private List<String> writeAll(List<String> templates) {
        Path packDirectory = Files.createDirectories(directory.resolve("pack${Files.list(directory).withCloseable { it.count() }}"))
        Files.createDirectories(packDirectory.resolve('templates'))
        StringBuilder files = new StringBuilder('files:\n')
        templates.eachWithIndex { String template, int index ->
            files.append("  - from: view${index}.gsp\n    to: grails-app/views/acme/view${index}.gsp\n")
            Files.writeString(packDirectory.resolve("templates/view${index}.gsp"), template)
        }
        Files.writeString(packDirectory.resolve('feature.yml'), PackGenerationSpec.HEADER + 'name: acme-views\n' + files)
        Pack pack = new PackLoader().fromDirectory(packDirectory)
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                ['acme-views'], ConsoleOutput.NOOP, [pack])
        generator.generate(ApplicationType.WEB, project, output, context)
        packDirectory.toFile().deleteDir()
        return (0..<templates.size()).collect { int index -> output.getProject()["grails-app/views/acme/view${index}.gsp".toString()] }
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

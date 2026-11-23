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
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.ForgeContexts
import org.grails.forge.ForgeCoreConfiguration
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.IgnoreIf
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * What a template of a feature pack is to the generator: when it is compiled, which of its tags are
 * instructions and that every other tag is text, and what a template that fails says.
 */
class PackTemplateSpec extends ApplicationContextSpec {

    static final String SEEN = 'org.apache.grails.forge.core.pack.PackTemplateSpec.seen'

    // has the class of the page it is compiled to remembered, each time it is rendered
    static final String REMEMBERS_ITS_CLASS = "<% System.properties.computeIfAbsent('${SEEN}') { [] } << getClass() %>\${pack.name}"

    static final int LIMIT = 100_000

    @TempDir
    Path directory

    void cleanup() {
        System.properties.remove(SEEN)
    }

    void "a template that is written to several places is compiled once for a generation, and again for the next"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: c.txt.gsp\n    to: a.txt\n  - from: c.txt.gsp\n    to: b.txt\n" +
                "  - from: same.txt.gsp\n    to: c.txt\n", ['c.txt.gsp': REMEMBERS_ITS_CLASS, 'same.txt.gsp': REMEMBERS_ITS_CLASS])

        when:
        Map<String, String> first = generate(['acme-audit'], [pack])
        Map<String, String> second = generate(['acme-audit'], [pack])
        List<Class<?>> seen = System.properties[SEEN] as List<Class<?>>

        then:
        [first, second].every { it['a.txt'] == 'acme-audit' && it['b.txt'] == 'acme-audit' && it['c.txt'] == 'acme-audit' }
        seen.size() == 6

        and: 'one class for the three files of a generation, two of them of one source and one of another with the same text'
        seen[0].is(seen[1]) && seen[0].is(seen[2])
        seen[3].is(seen[4]) && seen[3].is(seen[5])

        and: 'which is not the class of another generation: a compiled page is not kept between two'
        !seen[0].is(seen[3])
    }

    void "templates of two packs are each compiled, also when they have the same text"() {
        given:
        Pack first = pack('acme-a', "files:\n  - from: c.txt.gsp\n    to: a.txt\n", ['c.txt.gsp': REMEMBERS_ITS_CLASS])
        Pack second = pack('acme-b', "files:\n  - from: c.txt.gsp\n    to: b.txt\n", ['c.txt.gsp': REMEMBERS_ITS_CLASS])

        when:
        Map<String, String> output = generate(['acme-a', 'acme-b'], [first, second])
        List<Class<?>> seen = System.properties[SEEN] as List<Class<?>>

        then:
        output['a.txt'] == 'acme-a'
        output['b.txt'] == 'acme-b'
        seen.size() == 2
        !seen[0].is(seen[1])
    }

    void "the templates of the packs of Forge are compiled once, whatever number of generations use them"() {
        given: 'a Forge of its own, which has rendered nothing yet'
        ConfigurableApplicationContext forge = ForgeContexts.create()
        BundledPackFeatures bundled = forge.getBean(BundledPackFeatures)
        ProjectGenerator generator = forge.getBean(ProjectGenerator)

        expect:
        bundled.compiledTemplates == 0

        when:
        List<Map<String, String>> outputs = (1..4).collect { generate(generator, ['asciidoctor'], [], ApplicationType.WEB, 'example.grails.foo') }

        then: 'the pack has two templates, and the URL mappings, the functional test and the Spring resources, which every web application has, are three more: each is compiled once, or not at all where the page the build compiled for it is taken'
        bundled.compiledTemplates + bundled.precompiledTemplates == 5
        outputs[0]['src/docs/asciidoc/index.adoc'].startsWith('= Your Project\n')
        outputs[0]['gradle/asciidoc.gradle'] =~ /version '\d[^']*'/
        outputs.every { it['src/docs/asciidoc/index.adoc'] == outputs[0]['src/docs/asciidoc/index.adoc'] && it['gradle/asciidoc.gradle'] == outputs[0]['gradle/asciidoc.gradle'] }

        cleanup:
        forge.close()
    }

    void "a template of a pack of Forge that is rendered for many applications at once writes each its own"() {
        given: 'a pack of Forge whose template writes what the application is, in a Forge that has it'
        Path resources = directory.resolve('resources')
        Path packDirectory = Files.createDirectories(resources.resolve(Pack.BUNDLED_DIRECTORY).resolve('acme-of-forge/templates'))
        Files.writeString(resources.resolve(Pack.BUNDLED_INDEX), 'acme-of-forge\n')
        Files.writeString(packDirectory.parent.resolve('feature.yml'), PackGenerationSpec.HEADER + 'name: acme-of-forge\n' +
                "files:\n  - from: who.txt.gsp\n    to: who.txt\n")
        Files.writeString(packDirectory.resolve('who.txt.gsp'), '${project.name} of ${project.packageName}\n' +
                '<% 40.times { i -> %>${project.className}${i};<% } %>\n' +
                '<g:link controller="${project.propertyName}"><g:if test="${project.name.endsWith(\'7\')}">seven</g:if><g:else>other</g:else></g:link>\n')
        ClassLoader loader = new URLClassLoader([resources.toUri().toURL()] as URL[], (ClassLoader) null)
        AnnotationConfigApplicationContext forge = new AnnotationConfigApplicationContext()
        forge.register(ForgeCoreConfiguration)
        forge.registerBean('packsOfTheSpec', BundledPackFeatures, { new BundledPackFeatures(loader) } as Supplier<BundledPackFeatures>)
        forge.refresh()
        BundledPackFeatures bundled = forge.getBean('packsOfTheSpec', BundledPackFeatures)
        ProjectGenerator generator = forge.getBean(ProjectGenerator)
        int threads = 8
        ExecutorService pool = Executors.newFixedThreadPool(threads)
        CyclicBarrier together = new CyclicBarrier(threads)
        Closure<String> expected = { String name ->
            String className = name.capitalize()
            "${name} of example.grails\n${(0..<40).collect { "${className}${it};" }.join('')}\n" +
                    "<g:link controller=\"${name}\">${name.endsWith('7') ? 'seven' : 'other'}</g:link>\n"
        }

        when: 'the first generations are made at once, so that the template is compiled while others ask for it'
        List<Future<List<String>>> wrong = (0..<threads).collect { int thread ->
            pool.submit({
                together.await(60, TimeUnit.SECONDS)
                List<String> mismatches = []
                for (int i = 0; i < 25; i++) {
                    String name = "app${thread}x${i}"
                    String written = generate(generator, ['acme-of-forge'], [], ApplicationType.WEB, "example.grails.${name}")['who.txt']
                    if (written != expected(name).toString()) {
                        mismatches << written
                    }
                }
                return mismatches
            } as Callable<List<String>>)
        }

        then:
        wrong.collect { it.get(240, TimeUnit.SECONDS) }.flatten() == []
        bundled.compiledTemplates == 1

        cleanup:
        pool.shutdownNow()
        forge.close()
        loader.close()
    }

    @Unroll
    void "the tag #tag is an instruction to the generator and is not written"() {
        expect:
        render(source) == written

        where:
        tag         | source                                                                                 || written
        'g:if'      | '<g:if test="${features.contains(\'acme-audit\')}">yes</g:if>'                         || 'yes'
        'g:else'    | '<g:if test="${features.contains(\'no-such\')}">yes</g:if><g:else>no</g:else>'         || 'no'
        'g:elseif'  | '<g:if test="${false}">a</g:if><g:elseif test="${true}">b</g:elseif><g:else>c</g:else>' || 'b'
        'g:unless'  | '<g:unless test="${false}">u</g:unless>'                                               || 'u'
        'g:each'    | '<g:each in="${[5, 6]}" var="n" status="i">${i}:${n},</g:each>'                        || '0:5,1:6,'
        'g:while'   | '<% int i = 0 %><g:while test="${i < 3}">${i++}</g:while>'                             || '012'
        'g:collect' | '<g:collect in="${[1, 2]}" expr="${it * 2}">${it},</g:collect>'                        || '2,4,'
        'g:findAll' | '<g:findAll in="${[1, 2, 3]}" expr="${it > 1}">${it},</g:findAll>'                     || '2,3,'
        'g:grep'    | '<g:grep in="${[\'a\', \'b\']}" filter="${~/a/}">${it},</g:grep>'                      || 'a,'
        'g:def'     | '<g:def var="v" value="${3}"/>${v}'                                                    || '3'
    }

    void "the instructions are the tags the compiler of pages compiles into a page, and no other"() {
        expect: 'what the guide lists and the generator leaves to the compiler is what the compiler takes'
        PackTemplateSource.INSTRUCTIONS.every { org.grails.gsp.compiler.tags.GrailsTagRegistry.instance.isSyntaxTag(it) }
        ['set', 'message', 'link', 'render', 'form', 'IF', 'elseIf', 'for', 'var'].every { !org.grails.gsp.compiler.tags.GrailsTagRegistry.instance.isSyntaxTag(it) }
        PackTemplateSource.INSTRUCTIONS.size() == 10
    }

    void "the tags of the tag libraries of Grails are no instructions: they are written as they are"() {
        given:
        String source = ['set', 'message', 'link', 'createLink', 'resource', 'render', 'include', 'applyLayout', 'layoutBody', 'layoutHead',
                         'form', 'textField', 'textArea', 'hiddenField', 'passwordField', 'checkBox', 'radio', 'select', 'submitButton',
                         'actionSubmit', 'fieldValue', 'hasErrors', 'eachError', 'renderErrors', 'formatDate', 'formatNumber', 'paginate',
                         'sortableColumn', 'javascript', 'img', 'external', 'meta', 'join', 'cookie', 'header', 'pageProperty', 'ifPageProperty']
                .collect { String name -> "<g:${name} a=\"1\" b='[c: d]'  />text</g:${name}>".toString() }.join('\n')

        expect:
        render(source) == source
    }

    @Unroll
    void "a tag that is no instruction is text, and is written as the template has it: #what"() {
        expect:
        render(source) == (written ?: source)

        where:
        what                                                 | source                                                                || written
        'its quotes and the space between its attributes'    | '<g:link action=\'show\'   id="1"\n   class="a b">x</g:link>'        || null
        'quotes inside quotes'                               | '<g:x a=\'say "hi"\' b="it\'s"/>'                                     || null
        'a space in front of the end of an empty tag'        | '<g:x a="1" />'                                                       || null
        'an empty body'                                      | '<g:message code="a"></g:message>'                                    || null
        'an attribute that is a map'                         | '<g:link action="show" params="[id: 1]">x</g:link>'                   || null
        'a map that names what the template has not'         | '<g:link params="[id: book.id]" args="[entityName]">x</g:link>'       || null
        'an attribute that is a list'                        | '<g:message code="a" args="[\'x\', \'y\']"/>'                         || null
        'an attribute that is an empty map'                  | '<g:render template="x" model="[:]"/>'                                || null
        'an attribute with brackets that is no Groovy'       | '<g:x title="[Draft] of [1]"/>'                                       || null
        'an attribute with a greater-than sign'              | '<g:x title="a > b" other="c">d</g:x>'                                || null
        'a tmpl tag'                                         | '<tmpl:row a="1"/>'                                                   || null
        'a link tag'                                         | '<link:book a="1">x</link:book>'                                      || null
        'a tag of a plugin'                                  | '<sec:ifLoggedIn><asset:javascript src="a.js"/></sec:ifLoggedIn>'     || null
        'an end tag by itself'                               | 'a</g:form>b'                                                         || null
        'a start tag by itself'                              | 'a<g:form action="b">'                                                || null
        'a name of an instruction in other letters'          | '<G:IF test="x">y</G:IF><g:If test="x"/><g:ifx/>'                     || null
        'a name of an instruction in another namespace'      | '<my:if test="x">y</my:if><my:each in="z"/>'                          || null
        'g:set, which sets nothing'                          | 'a<g:set var="x" value="${project.name}"/>b'                          || 'a<g:set var="x" value="foo"/>b'
        'the expressions of its attributes have their values' | '<g:x a="${project.name}" b="${null}" c="${true}" d="${[project.name]}"/>' || '<g:x a="foo" b="" c="true" d="[foo]"/>'
        'the expressions of its body have their values'      | '<x:y a="${project.name}" b="2">in ${pack.name}</x:y>'                || '<x:y a="foo" b="2">in acme-audit</x:y>'
        'an expression of several lines in an attribute'     | '<g:x a="${[1,\n 2].size()}"\n b="3"/>'                               || '<g:x a="2"\n b="3"/>'
        'an escaped expression stays one, in an attribute'   | '<g:link action="show" id="\\${book.id}">\\${book.title} of ${project.name}</g:link>' || '<g:link action="show" id="${book.id}">${book.title} of foo</g:link>'
        'an escaped expression that is a map stays one'      | '<g:render template="x" model="\\${[a: 1]}"/>'                        || '<g:render template="x" model="${[a: 1]}"/>'
        'the instructions in its body run'                   | '<g:form action="save"><g:if test="${true}">in</g:if><g:textField name="${project.name}"/></g:form>' || '<g:form action="save">in<g:textField name="foo"/></g:form>'
        'inside an instruction'                              | '<g:if test="${true}"><g:set var="a" value="1"/></g:if>'              || '<g:set var="a" value="1"/>'
        'inside each turn of an instruction'                 | '<g:each in="${[1, 2]}" var="n"><g:link id="${n}">\n</g:link></g:each>' || '<g:link id="1">\n</g:link><g:link id="2">\n</g:link>'
    }

    @Unroll
    void "the text of a file is written as it is, whatever a page of a web application would make of it: #what"() {
        expect:
        render(source) == written

        where:
        what                             | source                                                                                              || written
        'the document of a view'         | '<!doctype html>\n<html>\n<head>\n    <meta name="layout" content="main"/>\n    <meta charset="utf-8">\n    <title>${project.name}</title>\n</head>\n<body class="a">\n<p>hi</p>\n</body>\n</html>\n' || '<!doctype html>\n<html>\n<head>\n    <meta name="layout" content="main"/>\n    <meta charset="utf-8">\n    <title>foo</title>\n</head>\n<body class="a">\n<p>hi</p>\n</body>\n</html>\n'
        'a meta by itself'               | 'a\n<meta name="x" content="${project.name}">\nb\n'                                                 || 'a\n<meta name="x" content="foo">\nb\n'
        'a title by itself'              | '<title>x</title>'                                                                                  || '<title>x</title>'
        'XML with such names'            | '<book><title>x</title><body>x</body><head/></book>\n'                                              || '<book><title>x</title><body>x</body><head/></book>\n'
        'Markdown with such a name'      | 'Put it in `<body>` and `<head>`.\n'                                                                || 'Put it in `<body>` and `<head>`.\n'
        'what would be encoded for HTML' | '${"<b>&amp;\'\\""}'                                                                                || '<b>&amp;\'"'
        'spaces and line breaks'         | '  a  \n\n\t<g:x/>  \n  <% if (true) { %>\n  b\n  <% } %>\n\n'                                       || '  a  \n\n\t<g:x/>  \n  b\n\n'
        'Windows line breaks'            | 'a\r\n<g:x a="1"/>\r\n${project.name}\r\n'                                                          || 'a\r\n<g:x a="1"/>\r\nfoo\r\n'
    }

    @Unroll
    void "a backslash in front of what the generator would read writes it instead: #what"() {
        expect:
        render(source) == written

        where:
        what                                  | source                                                                          || written
        'an expression'                       | 'x \\${book.title} y ${project.name}'                                           || 'x ${book.title} y foo'
        'a scriptlet'                         | '\\<% if (book) { %>\\${book}\\<% } %>'                                         || '<% if (book) { %>${book}<% } %>'
        'a scriptlet that writes'             | '\\<%= book.title %>'                                                           || '<%= book.title %>'
        'a directive'                         | '\\<%@ page import="${project.packageName}.Book" %>\n'                          || '<%@ page import="example.grails.Book" %>\n'
        'a directive that is one of a page'   | '\\<%@ page contentType="text/plain" expressionCodec="html" trimLogicLines="false" grailsLayoutPreprocess="true" %>\n<head/>${"<"}\n' || '<%@ page contentType="text/plain" expressionCodec="html" trimLogicLines="false" grailsLayoutPreprocess="true" %>\n<head/><\n'
        'a comment'                           | '\\<%-- a comment\n of ${project.name} --%>\n'                                  || '<%-- a comment\n of foo --%>\n'
        'a comment of the other form'         | '\\%{--\n  A licence.\n--}%\n<p/>\n'                                            || '%{--\n  A licence.\n--}%\n<p/>\n'
        'a scriptlet of the other form'       | '\\%{ int a = 1 }%'                                                             || '%{ int a = 1 }%'
        'an instruction tag'                  | '\\<g:each in="\\${books}" var="b">\\${b} ${project.name}\\</g:each>'           || '<g:each in="${books}" var="b">${b} foo</g:each>'
        'an instruction tag that is empty'    | '\\<g:def var="a" value="\\${1}"/>'                                             || '<g:def var="a" value="${1}"/>'
        'a tag that is text anyway'           | '\\<g:link action="a">x\\</g:link> \\<g:set var="a" value="1"/> \\<my:if a="b">\\</my:if>' || '<g:link action="a">x</g:link> <g:set var="a" value="1"/> <my:if a="b"></my:if>'
        'a line of them only is still a line' | 'a\n\\<% if (x) { %>\nb\n\\<% } %>\n\\<%-- c --%>\nd\n'                          || 'a\n<% if (x) { %>\nb\n<% } %>\n<%-- c --%>\nd\n'
    }

    void "a backslash in front of anything else is a backslash"() {
        expect:
        render('a\\b \\<p>x\\</p> \\$5 \\% \\{ \\} \\@{x} \\!{y}! \\n \\d+ c:\\dir\\ \\') == 'a\\b \\<p>x\\</p> \\$5 \\% \\{ \\} \\@{x} \\!{y}! \\n \\d+ c:\\dir\\ \\'
        render('\\begin{document} \\section{${project.name}} $x^2$ 50\\% \\textbf{a}') == '\\begin{document} \\section{foo} $x^2$ 50\\% \\textbf{a}'
    }

    @Unroll
    void "backslashes in front of what the generator reads, and of a tag, are an escape two by two: #source"() {
        expect:
        render(source.replace('|', '\\')) == written.replace('|', '\\')

        where: 'a backslash is written as a bar here'
        source                                    || written
        '|${book}'                                || '${book}'
        '||${project.name}'                       || '|foo'
        '|||${book}'                              || '|${book}'
        '||||${project.name}'                     || '||foo'
        '|||||${book}'                            || '||${book}'
        'C:|Users||${project.name}|file'          || 'C:|Users|foo|file'
        'C:|Users|${project.name}|file'           || 'C:|Users${project.name}|file'
        '|<% a %>'                                || '<% a %>'
        '||<% out << 1 %>.'                       || '|1.'
        '|||<% a %>'                              || '|<% a %>'
        '||||<%= 2 %>'                            || '||2'
        '|%{-- c --}%'                            || '%{-- c --}%'
        '||%{-- c --}%.'                          || '|.'
        '|||%{ a }%'                              || '|%{ a }%'
        '|<%{ a }% b'                             || '<%{ a }% b'
        '|<%{-- c --}%.'                          || '<%{-- c --}%.'
        '|||<%{ a }%'                             || '|<%{ a }%'
        '|<%{|<g:if'                              || '<%{<g:if'
        '^p|<%{ }%'                               || '^p<%{ }%'
        '<|%{ a }%'                               || '<%{ a }%'
        '|<%|{ a }%'                              || '<%|{ a }%'
        '|<%${project.name}'                      || '<%foo'
        '|<%%{-- c --}%.'                         || '<%.'
        '${"<"}%{ out << 1 }%.'                   || '<1.'
        '|<g:if test="x">'                        || '<g:if test="x">'
        '||<g:if test="${true}">y||</g:if>'       || '|y|'
        '|||<g:if test="x">'                      || '|<g:if test="x">'
        '|<g:link a="b">'                         || '<g:link a="b">'
        '||<g:link a="b">'                        || '|<g:link a="b">'
        '|||<g:link a="b">|||</g:link>'           || '|<g:link a="b">|</g:link>'
        '|<my:tag/>||<my:tag/>'                   || '<my:tag/>|<my:tag/>'
        '${project.name}|${project.name}'         || 'foo${project.name}'
        '${project.name}||${project.name}'        || 'foo|foo'
        '<% out << 1 %>|<% a %>'                  || '1<% a %>'
        '<g:x a="|${b}" c="||${project.name}"/>'  || '<g:x a="${b}" c="|foo"/>'
        'a|'                                      || 'a|'
        'a||'                                     || 'a||'
        'a|<'                                     || 'a|<'
        'a|$'                                     || 'a|$'
        'a|$ {b}'                                 || 'a|$ {b}'
    }

    void "a dollar that starts no expression is a dollar"() {
        expect:
        render('cost $5 and $name and $ {x} and 100%') == 'cost $5 and $name and $ {x} and 100%'
    }

    @Unroll
    void "what a page would read and a template has no use for is text: #what"() {
        expect:
        render(source) == (written ?: source)

        where:
        what                                             | source                                                                       || written
        'the first line of a scaffolded view'            | '@{ model="List<demo.Book> bookList; Number bookCount" }\n<!DOCTYPE html>\n<html/>\n' || null
        'a short directive of a page'                    | 'a @{ page import="java.util.List" } b @{page contentType="text/plain"}\n'  || null
        'a link of Thymeleaf and a mixin of LESS'        | '<a th:href="@{/css/main.css}">x</a> .mixin(@{name}) { color: @{base}; } @media{x}\n' || null
        'a declaration of a page'                        | 'a !{ int n = 0 }! b !{ c }% d\n'                                           || null
        'JavaScript with a negated object'               | 'if (!{}.a) { b() } x!{y}!z\n'                                              || null
        'a circumflex in front of letters'               | '<td>^td</td> x^2 + y^n = ^z a^b% ^p%\n'                                    || null
        'the form that takes a tag out, escaped'         | '<p>^p\\%{ x }%</p>\n'                                                    || '<p>^p%{ x }%</p>\n'
        'the keys of a workflow, escaped'                | 'token: \\${{ secrets.TOKEN }}\n'                                         || 'token: ${{ secrets.TOKEN }}\n'
        'a format of curl, escaped'                      | 'curl -w "\\%{http_code}" -o /dev/null https://example.org/\n'            || 'curl -w "%{http_code}" -o /dev/null https://example.org/\n'
    }

    void "the form that takes a tag out of a page does not take it out of a template"() {
        expect: 'the scriptlet in it runs, as one anywhere does'
        render('<p>^p%{ out << project.name }%</p>') == '<p>^pfoo</p>'
        render('<td>a</td><td>^td%{-- gone --}%</td><td>b</td>') == '<td>a</td><td>^td</td><td>b</td>'
    }

    @Unroll
    void "an instruction that #problem is refused, and it is said why"() {
        when:
        render(source)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The template t.txt.gsp of the feature acme-audit cannot be rendered: ${says}"

        where:
        problem                                  | source                                              || says
        'asks for the environment'               | 'one\n<g:if env="production">x</g:if>\n'           || 'line 2: the instruction g:if has the attribute env, which a template cannot have: it would ask for the environment the generator runs in, and that of the application is not known when it is generated'
        'asks for the environment and a test'    | 'one\ntwo\n<g:unless test="${true}"\n env="test">x</g:unless>' || 'line 3: the instruction g:unless has the attribute env, which a template cannot have: it would ask for the environment the generator runs in, and that of the application is not known when it is generated'
        'has a test that is a word'              | '<g:if test="false">x</g:if>'                       || 'line 1: the test of the instruction g:if has to be one expression, test="${...}": anything else is text, which is always true'
        'has a test that is not in an expression' | 'one\n<g:if test="${true}">a</g:if><g:elseif test="1 == 2">x</g:elseif>' || 'line 2: the test of the instruction g:elseif has to be one expression, test="${...}": anything else is text, which is always true'
        'has a test with text beside the expression' | "<g:while test='\${false} '>x</g:while>"        || 'line 1: the test of the instruction g:while has to be one expression, test="${...}": anything else is text, which is always true'
        'has a test of two expressions'          | '<g:unless test="${true}${false}">x</g:unless>'      || 'line 1: the test of the instruction g:unless has to be one expression, test="${...}": anything else is text, which is always true'
    }

    void "an instruction whose test is one expression is taken, whatever is in the expression"() {
        expect:
        render('<g:if test="${features.contains("acme-audit") && [a: \'>\'].a == \'>\' && "}".size() == 1}">a</g:if>' +
                "<g:unless test='\${ { -> false }() }'>b</g:unless><g:if  test = \"\${ true }\"  >c</g:if>" +
                '<g:each in="${[1]}" var="env">${env}</g:each><g:x env="production" test="false"/>') == 'abc1<g:x env="production" test="false"/>'
    }

    void "what is in a scriptlet, an expression or a comment is theirs, also when it looks like a tag"() {
        expect: 'what a scriptlet and an expression write is what they say'
        render('<% out << \'<g:link a="[b: 1]"   >\' %>|${\'<x:y  >\'}|${"<g:if test=\'a\'>"}|<%= "</g:each>" %>') ==
                '<g:link a="[b: 1]"   >|<x:y  >|<g:if test=\'a\'>|</g:each>'

        and: 'a comment is left out with what is in it'
        render('a<%-- <g:link>x</g:link> ${nope} --%>b%{-- <g:if> --}%c') == 'abc'

        and: 'an instruction in a comment of HTML, which is text, runs'
        render('<!-- <g:if test="${true}">x</g:if> <g:link/> -->') == '<!-- x <g:link/> -->'
    }

    @IgnoreIf(value = { !RepositoryFiles.hasGuide() }, reason = 'these sources are no checkout of Grails, and the guide is not beside Forge in them')
    @Unroll
    void "the example #example of the guide writes what the guide says it writes"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: t.txt.gsp\n    to: t.txt\n", ['t.txt.gsp': RepositoryFiles.example(example)], example)

        expect:
        generate(beanContext.getBean(ProjectGenerator), ['acme-audit'], [pack], ApplicationType.WEB, 'com.example.shop')['t.txt'] ==
                RepositoryFiles.example("${example}-written")

        where:
        example << ['template', 'instructions', 'text-tags', 'view', 'escapes']
    }

    void "a view that has nothing to fill in is a file of another name in the pack, and is written as it is"() {
        given:
        String view = '<g:if test="${book}">\n    <g:link action="show" id="${book.id}"  class=\'a\'>${book.title}</g:link>\n</g:if>\n<g:render template="row" model="[book: book]"/>\n'
        Pack pack = pack('acme-audit', "files:\n  - from: show.gsp.txt\n    to: grails-app/views/book/show.gsp\n", ['show.gsp.txt': view])

        expect:
        generate(['acme-audit'], [pack])['grails-app/views/book/show.gsp'] == view
    }

    void "a name a tag that is text would have set in a view is not there"() {
        when:
        render('one\n<g:set var="x" value="1"/>${x}\nthree')

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith("The template t.txt.gsp of the feature acme-audit cannot be rendered: line 2: There is no 'x' for a template")
    }

    @Unroll
    void "a template that #problem says on which of its lines"() {
        when:
        render(source)

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith("The template t.txt.gsp of the feature acme-audit cannot be rendered: ${says}")
        !e.message.contains('_gsp')
        !e.message.contains('Byte array resource')
        !(e.message =~ /[\p{Cc}]/)
        e.message.length() < 700
        e.cause != null

        where:
        problem                                           | source                                                                          || says
        'throws in a scriptlet'                           | 'one\ntwo\n<% throw new IllegalStateException("boom") %>\nfour\n'               || 'line 3: boom'
        'throws on its last line'                         | 'one\ntwo\n<% throw new IllegalStateException("boom") %>'                       || 'line 3: boom'
        'throws on its only line'                         | '<% throw new IllegalStateException("boom") %>'                                 || 'line 1: boom'
        'throws after a comment of several lines'         | '<%-- a\n header\n of three lines --%>\nfour\n<% throw new IllegalStateException("boom") %>\nsix\n' || 'line 5: boom'
        'throws after a comment of one line'              | 'one\n<%-- c --%>\n<% throw new IllegalStateException("boom") %>\n'             || 'line 3: boom'
        'throws after an expression of several lines'     | '${[1,\n 2,\n 3].size()}\nfour\n<% features.nope() %>\nsix\n'                   || 'line 5: No signature of method: nope'
        'throws after a scriptlet of several lines'       | '<%\n int a = 1\n int b = 2\n%>\nfive\n<% features.nope() %>\n'                 || 'line 6: No signature of method: nope'
        'throws after tags of several lines that are text' | '<g:link\n  action="a"\n  params="[b: 1]">\nfour</g:link>\n<% features.nope() %>\n' || 'line 5: No signature of method: nope'
        'throws after escapes'                            | '\\<%@ page import="a.B" %>\n\\%{-- c\n --}%\n\\<g:if test="x">\n<% features.nope() %>\n' || 'line 5: No signature of method: nope'
        'throws in a scriptlet of several lines'          | 'one\ntwo\n<%\n\n features.nope()\n%>\nseven\n'                                 || 'line 3: No signature of method: nope'
        'throws in a closure'                             | 'one\n<% [1].each { %>\nthree\n<% it.nope() %>\n<% } %>\n'                      || 'line 4: No signature of method: nope'
        'throws in the body of an instruction'            | 'one\n<g:each in="${[1]}" var="n">\nthree\n<% n.nope() %>\n</g:each>\n'         || 'line 4: No signature of method: nope'
        'throws in the body of a tag that is text'        | 'one\n<g:form>\nthree\n<% throw new IllegalStateException("boom") %>\n</g:form>\n' || 'line 4: boom'
        'throws in the test of an instruction'            | 'one\ntwo\n<g:if test="${features.nope()}">x</g:if>\n'                          || 'line 3: No signature of method: nope'
        'changes what it is given'                        | 'one\n<% features << "x" %>\n'                                                  || 'line 2: java.lang.UnsupportedOperationException'
        'names what there is not, in a scriptlet'         | 'one\ntwo\n<% if (flags) { %>x<% } %>\n'                                        || "line 3: There is no 'flags' for a template: template API 1 has "
        'asserts what is not so'                          | 'one\ntwo\n<% assert features.contains("nope") : "wanted" %>\n'                 || 'line 3: wanted'
        'calls itself without end'                        | 'one\ntwo\n<% def f; f = { f() }; f() %>\n'                                     || 'line 3: it calls itself without end'
        'calls itself without end in an instruction'      | 'one\n<g:each in="${[1]}">\n<% def f; f = { f() }; f() %>\n</g:each>\n'         || 'line 3: it calls itself without end'
        'calls itself without end in an expression'       | 'one\n${({ -> def f; f = { f() }; f() })()}\n'                                  || 'line 2: it calls itself without end'
        'fails in an expression'                          | 'one\ntwo\n${features.nope()}'                                                  || 'line 3: No signature of method: nope'
        'fails in an expression in a tag that is text'    | 'one\n<g:link\n id="${features.nope()}"/>'                                      || 'line 3: No signature of method: nope'
        'has a scriptlet that does not compile'           | 'one\ntwo\n<% def x = ) %>\nfour\n'                                             || 'line 3: it does not compile: '
        'has an expression that does not compile'         | 'one\n${1 +* }\nthree\n'                                                        || 'line 2: it does not compile: '
        'has a test that does not compile'                | 'one\ntwo\nthree\n<g:if test="${)}">x</g:if>'                                   || 'line 4: it does not compile: '
        'does not compile after a comment'                | '<%-- a\n header\n of three lines --%>\nfour\n<% def x = ) %>\nsix\n'           || 'line 5: it does not compile: '
        'does not compile after an expression of several lines' | '${[1,\n 2,\n 3].size()}\nfour\n<% new no.such.Thing() %>\nsix\n'         || 'line 5: it does not compile: unable to resolve class no.such.Thing'
        'imports a class there is not'                    | 'one\ntwo\nthree\n<%@ page import="no.such.Thing" %>\nfive\nsix\n'              || 'line 4: it does not compile: unable to resolve class no.such.Thing'
        'leaves a block open'                             | 'one\n<% if (true) { %>\nthree\n'                                               || "line 3: it does not compile: Missing '}'"
        'leaves an instruction open'                      | 'one\n<g:if test="${true}">\nthree\n'                                           || 'line 3: Grails tags were not closed! [[<g:if>]]'
        'has an instruction without what it needs'        | 'one\n<g:if>x</g:if>\nthree\n'                                                  || 'line 2: Tag [if] must have one or both of the attributes [test] or [env]'
        'has an expression with a bracket nothing opened' | 'one\n${a]}b\n'                                                               || 'line 2: it does not compile: '
        'has an expression that is not closed'            | 'one\ntwo\nthree ${project.name\nfour\n'                                      || 'line 3: Unclosed GSP expression'
        'has a declaration, which a page cannot have'     | 'one\n<%! int n = 0 %>\nthree\n'                                                || 'line 2: JSP-style declaration blocks'
    }

    @Unroll
    void "a template that is #problem is said to be, in words"() {
        when:
        render(source)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The template t.txt.gsp of the feature acme-audit cannot be rendered: ${says}"

        where:
        problem                                         | source                                                          || says
        'more than the page of a template can hold'     | '${project.name} <g:if test="${true}">a</g:if>\n' * 6000        || 'it has more expressions, scriptlets and instructions than the page of one template can hold: divide it into several files'
        'an expression too long for the compiler'       | '${1' + ' + 1' * 6000 + '}'                                     || 'it is nested too deeply to be compiled: an expression has too many parts, or instructions are inside each other too many times'
        'instructions too deep for the compiler'        | '<g:if test="${true}">' * 3000 + 'x' + '</g:if>' * 3000         || 'it is nested too deeply to be compiled: an expression has too many parts, or instructions are inside each other too many times'
    }

    void "a name that is missing because a g:set of the template is text is said to be"() {
        when:
        render('one\n<g:set var="y" value="${project.name}"/>\n${y}\n')

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith("The template t.txt.gsp of the feature acme-audit cannot be rendered: line 3: There is no 'y' for a template: template API 1 has ")
        e.message.endsWith('. The <g:set var="y"> of the template is text, which is written and sets nothing: use <g:def>')

        when: 'in a scriptlet, and another name'
        render('<g:set var="y" value="1"/><% out << y %>')

        then:
        e = thrown()
        e.message.endsWith('. The <g:set var="y"> of the template is text, which is written and sets nothing: use <g:def>')

        when:
        render('<g:set var="y" value="1"/>${z}')

        then:
        e = thrown()
        e.message.endsWith('project]')
    }

    void "a template whose page has more than a thousand lines says on which of its own it fails"() {
        when:
        render((1..499).collect { int line -> "<% int a${line} = ${line} %>\n" }.join('') + 'five hundred\n<% features.nope() %>\n')

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith('The template t.txt.gsp of the feature acme-audit cannot be rendered: line 501: No signature of method: nope')
    }

    @Unroll
    void "a template that writes without end #where is stopped, and said to write too much"() {
        when:
        render(source)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The template t.txt.gsp of the feature acme-audit writes more than the 1048576 bytes a file of a feature pack can have'

        where:
        where                                  | source
        'in a scriptlet'                       | '<% while (true) { %>' + ('x' * 1000) + '<% } %>'
        'in the body of an instruction'        | '<g:each in="${[1]}"><% while (true) { %>' + ('x' * 1000) + '<% } %></g:each>'
        'in an instruction in an instruction'  | '<g:if test="${true}"><g:while test="${true}">' + ('x' * 1000) + '</g:while></g:if>'
        'in the body of a tag that is text'    | '<g:form><% while (true) { %>' + ('x' * 1000) + '<% } %></g:form>'
        'in an expression'                     | '${(1..10000000).each { out << "' + ('x' * 1000) + '" }}'
    }

    void "a template that fails says the same every time it is rendered"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: bad.txt.gsp\n    to: a.txt\n  - from: bad.txt.gsp\n    to: b.txt\n",
                ['bad.txt.gsp': 'one\n<% def x = ) %>\n'])

        when:
        List<String> messages = (1..2).collect {
            try {
                generate(['acme-audit'], [pack])
                return 'generated'
            } catch (IllegalArgumentException e) {
                return e.message
            }
        }

        then:
        messages.every { it.startsWith('The template bad.txt.gsp of the feature acme-audit cannot be rendered: line 2: it does not compile: ') }
    }

    @Unroll
    void "a template that #problem is compiled once, however often it is rendered"() {
        given:
        PackPages pages = new PackPages(getClass().classLoader)

        when:
        List<String> messages = (1..10).collect {
            try {
                pages.render('acme', source, 'a template', 1, [:], LIMIT)
                return 'rendered'
            } catch (IllegalArgumentException e) {
                return e.message
            }
        }

        then:
        messages.toSet() == [says].toSet()
        pages.compiled == 1

        where:
        problem                  | source                 || says
        'the parser refuses'     | 'one\n<g:if>x</g:if>'  || 'The template a template cannot be rendered: line 2: Tag [if] must have one or both of the attributes [test] or [env]'
        'does not compile'       | '<% new no.such.Thing() %>' || 'The template a template cannot be rendered: line 1: it does not compile: unable to resolve class no.such.Thing'
        'fails when it is run'   | '<% throw new IllegalStateException("no") %>' || 'The template a template cannot be rendered: line 1: no'
    }

    void "a template that is interrupted fails, and leaves the thread interrupted"() {
        given:
        PackPages pages = new PackPages(getClass().classLoader)

        when:
        pages.render('acme', '<% throw new InterruptedException("stop") %>', 'a template', 1, [:], LIMIT)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The template a template cannot be rendered: line 1: stop'
        Thread.interrupted()

        when: 'what interrupts it is inside what a page throws'
        pages.render('acme', '${[Thread.currentThread().interrupt(), Thread.sleep(60_000)]}', 'a template', 1, [:], LIMIT)

        then:
        thrown(IllegalArgumentException)
        Thread.interrupted()
    }

    void "the templates of two owners are two pages, whatever their names and their sources are"() {
        given: 'two owners and sources that are the same characters one after the other, with a separator between them'
        PackPages pages = new PackPages(getClass().classLoader)
        String separator = '\u0000' + PackPages.DIRECTIVES

        when:
        String first = new String(pages.render('a', 'Q' + separator + 'tail', 'a template', 1, [:], LIMIT), 'UTF-8')
        String second = new String(pages.render('a' + separator + 'Q', 'tail', 'a template', 1, [:], LIMIT), 'UTF-8')

        then:
        pages.compiled == 2
        first.startsWith('Q')
        second == 'tail'

        when: 'and the same owner and source again'
        pages.render('a', 'Q' + separator + 'tail', 'a template', 1, [:], LIMIT)
        pages.render('ab', 'c', 'a template', 1, [:], LIMIT)
        pages.render('a', 'bc', 'a template', 1, [:], LIMIT)

        then:
        pages.compiled == 4
    }

    void "one set of compiled pages renders for many threads at once, each with its own model"() {
        given: 'four templates, each with its number in what it writes'
        PackPages pages = new PackPages(getClass().classLoader)
        List<String> sources = (0..3).collect { int template ->
            ('<% 3.times { i -> %>\n${n}-${i}-' + template + '\n<% } %>\n<g:if test="${n % 2 == 0}">even</g:if><g:else>odd</g:else><g:x n="${n}"/>\n').toString()
        }
        int threads = 8
        ExecutorService pool = Executors.newFixedThreadPool(threads)
        CyclicBarrier together = new CyclicBarrier(threads)

        when:
        List<Future<List<String>>> wrong = (0..<threads).collect { int thread ->
            pool.submit({
                together.await(60, TimeUnit.SECONDS)
                List<String> mismatches = []
                for (int i = 0; i < 200; i++) {
                    int template = (thread + i) % sources.size()
                    int n = thread * 1000 + i
                    String written = new String(pages.render('acme', sources[template], 'a template', 1, [n: n] as Map<String, Object>, LIMIT), 'UTF-8')
                    String expected = "${n}-0-${template}\n${n}-1-${template}\n${n}-2-${template}\n${n % 2 == 0 ? 'even' : 'odd'}<g:x n=\"${n}\"/>\n"
                    if (written != expected) {
                        mismatches << written
                    }
                }
                return mismatches
            } as Callable<List<String>>)
        }

        then:
        wrong.collect { it.get(120, TimeUnit.SECONDS) }.flatten() == []
        pages.compiled == sources.size()

        cleanup:
        pool.shutdownNow()
    }

    private String render(String source) {
        // a directory of its own for each: a row of a table has no name a directory can have
        Pack pack = pack('acme-audit', "files:\n  - from: t.txt.gsp\n    to: t.txt\n", ['t.txt.gsp': source], "pack${Files.list(directory).withCloseable { it.count() }}")
        return generate(['acme-audit'], [pack])['t.txt']
    }

    private Pack pack(String name, String body, Map<String, String> files = [:], String directoryName = name) {
        Path packDirectory = Files.createDirectories(directory.resolve(directoryName))
        Files.writeString(packDirectory.resolve('feature.yml'), PackGenerationSpec.HEADER + 'name: ' + name + '\n' + body)
        Files.createDirectories(packDirectory.resolve('templates'))
        files.each { String path, String text -> Files.writeString(packDirectory.resolve('templates').resolve(path), text) }
        return new PackLoader().fromDirectory(packDirectory)
    }

    private Map<String, String> generate(List<String> features, List<Pack> packs) {
        return generate(beanContext.getBean(ProjectGenerator), features, packs, ApplicationType.WEB, 'example.grails.foo')
    }

    private static Map<String, String> generate(ProjectGenerator generator, List<String> features, List<Pack> packs, ApplicationType type, String name) {
        Project project = NameUtils.parse(name)
        MapOutputHandler output = new MapOutputHandler()
        GeneratorContext context = generator.createGeneratorContext(type, project, new Options(), OperatingSystem.LINUX,
                new ArrayList<String>(features), ConsoleOutput.NOOP, packs)
        generator.generate(type, project, output, context)
        return output.getProject()
    }
}

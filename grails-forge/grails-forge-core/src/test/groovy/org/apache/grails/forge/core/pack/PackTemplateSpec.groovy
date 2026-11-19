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
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.ForgeContexts
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
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * What a template of a feature pack is to the generator: when it is compiled, which of its tags are
 * instructions and which are written out, and what a template that fails says.
 */
class PackTemplateSpec extends ApplicationContextSpec {

    static final String SEEN = 'org.apache.grails.forge.core.pack.PackTemplateSpec.seen'

    // has the class of the page it is compiled to remembered, each time it is rendered
    static final String REMEMBERS_ITS_CLASS = "<% System.properties.computeIfAbsent('${SEEN}') { [] } << getClass() %>\${pack.name}"

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

    void "the templates of a pack of Forge are compiled once, for every generation and for those made at once"() {
        given: 'a Forge of its own, which has rendered nothing yet'
        ConfigurableApplicationContext forge = ForgeContexts.create()
        BundledPackFeatures bundled = forge.getBean(BundledPackFeatures)
        ProjectGenerator generator = forge.getBean(ProjectGenerator)
        int threads = 8
        ExecutorService pool = Executors.newFixedThreadPool(threads)
        CyclicBarrier together = new CyclicBarrier(threads)

        expect:
        bundled.compiledTemplates == 0

        when: 'the first generations are made at once, so that the templates are compiled while others ask for them'
        List<Future<List<Map<String, String>>>> made = (1..threads).collect { int thread ->
            pool.submit({
                together.await(60, TimeUnit.SECONDS)
                (1..5).collect { generate(generator, ['asciidoctor'], [], ApplicationType.WEB, "example.grails.app${thread}") }
            } as Callable<List<Map<String, String>>>)
        }
        Map<Integer, List<Map<String, String>>> byThread = [:]
        made.eachWithIndex { Future<List<Map<String, String>>> each, int index -> byThread[index + 1] = each.get(120, TimeUnit.SECONDS) }
        Map<String, String> after = generate(generator, ['asciidoctor'], [], ApplicationType.WEB, 'example.grails.app1')

        then: 'the pack has two templates, and they are compiled once each'
        bundled.compiledTemplates == 2

        and: 'every generation has what the templates write'
        after['src/docs/asciidoc/index.adoc'].startsWith('= Your Project\n')
        after['gradle/asciidoc.gradle'] =~ /version '\d[^']*'/
        byThread.size() == threads
        byThread.values().every { List<Map<String, String>> outputs ->
            outputs.size() == 5 && outputs.every {
                it['src/docs/asciidoc/index.adoc'] == after['src/docs/asciidoc/index.adoc'] &&
                        it['gradle/asciidoc.gradle'] == after['gradle/asciidoc.gradle']
            }
        }

        cleanup:
        pool.shutdownNow()
        forge.close()
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

    void "the tags of the tag libraries of Grails are no instructions: they are written out"() {
        given:
        String source = ['set', 'message', 'link', 'createLink', 'resource', 'render', 'include', 'applyLayout', 'layoutBody', 'layoutHead',
                         'form', 'textField', 'textArea', 'hiddenField', 'passwordField', 'checkBox', 'radio', 'select', 'submitButton',
                         'actionSubmit', 'fieldValue', 'hasErrors', 'eachError', 'renderErrors', 'formatDate', 'formatNumber', 'paginate',
                         'sortableColumn', 'javascript', 'img', 'external', 'meta', 'join', 'cookie', 'header', 'pageProperty', 'ifPageProperty']
                .collect { String name -> "<g:${name} a=\"1\"/>".toString() }.join('\n')

        expect:
        render(source) == source
    }

    void "one set of compiled pages renders for many threads at once, each with its own model"() {
        given: 'four templates, each with its number in what it writes'
        PackPages pages = new PackPages(getClass().classLoader)
        List<String> sources = (0..3).collect { int template ->
            ('<% 3.times { i -> %>\n${n}-${i}-' + template + '\n<% } %>\n<g:if test="${n % 2 == 0}">even</g:if><g:else>odd</g:else>\n').toString()
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
                    String written = new String(pages.render('acme', sources[template], 'a template', 1, [n: n] as Map<String, Object>, 10_000), 'UTF-8')
                    String expected = "${n}-0-${template}\n${n}-1-${template}\n${n}-2-${template}\n${n % 2 == 0 ? 'even' : 'odd'}\n"
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

    @Unroll
    void "a tag that is no instruction is written out: #what"() {
        expect:
        render(source) == written

        where:
        what                                                    | source                                                                  || written
        'g:set, which sets nothing'                             | 'a<g:set var="x" value="${project.name}"/>b'                            || 'a<g:set var="x" value="foo"/>b'
        'g:message'                                             | '<g:message code="a.b"/>'                                               || '<g:message code="a.b"/>'
        'g:link, with its body'                                 | '<g:link controller="book" action="list">All ${1 + 1}</g:link>'         || '<g:link controller="book" action="list">All 2</g:link>'
        'g:render'                                              | '<g:render template="/a/${project.name}"/>'                             || '<g:render template="/a/foo"/>'
        'a tag without attributes'                              | '<g:layoutBody/>'                                                       || '<g:layoutBody/>'
        'a tag of another namespace'                            | '<x:y a="${project.name}" b="2">in ${pack.name}</x:y>'                  || '<x:y a="foo" b="2">in acme-audit</x:y>'
        'a name of an instruction in other letters'             | '<G:IF test="${true}">x</G:IF>'                                         || '<G:IF test="true">x</G:IF>'
        'its attributes in their order'                         | '<g:x z="1" a="2" m="3"/>'                                              || '<g:x z="1" a="2" m="3"/>'
        'the instructions in its body run'                      | '<g:form action="save"><g:if test="${true}">in</g:if><g:textField name="${project.name}"/></g:form>' || '<g:form action="save">in<g:textField name="foo"/></g:form>'
        'inside an instruction'                                 | '<g:if test="${true}"><g:set var="a" value="1"/></g:if>'                || '<g:set var="a" value="1"/>'
        'the expressions of its attributes have their values'   | '<g:x a="${project.name}" b="${null}" c="${true}" d="${[project.name]}"/>' || '<g:x a="foo" b="null" c="true" d="[foo]"/>'
        'an escaped expression stays one, in an attribute too'  | '<g:link action="show" id="\\${book.id}">\\${book.title} of ${project.name}</g:link>' || '<g:link action="show" id="${book.id}">${book.title} of foo</g:link>'
        'an escaped expression that is a map stays one'         | '<g:render template="x" model="\\${[a: 1]}"/>'                          || '<g:render template="x" model="${[a: 1]}"/>'
    }

    @Unroll
    void "a tag that is written out is written anew, not copied: #what"() {
        expect:
        render(source) == written

        where:
        what                                               | source                                                     || written
        'its attributes are a space apart and in quotes'   | '<g:link action=\'show\'   id="1"\n   class="a b">x</g:link>' || '<g:link action="show" id="1" class="a b">x</g:link>'
        'the quotes are those its values can be in'        | '<g:x a=\'say "hi"\' b="it\'s"/>'                          || '<g:x a=\'say "hi"\' b="it\'s"/>'
        'an empty body is none'                            | '<g:message code="a"></g:message>'                         || '<g:message code="a"/>'
        'an attribute that is a map is evaluated'          | '<g:render template="x" model="[a: 1]"/>'                  || '<g:render template="x" model="{a=1}"/>'
        'a tmpl tag is the g:render it stands for'         | '<tmpl:row a="1"/>'                                        || '<g:render template="row" model="{a=1}"/>'
        'a link tag is the g:link it stands for'           | '<link:book a="1"/>'                                       || '<g:link mapping="book" params="{a=1}"/>'
    }

    @Unroll
    void "what a view of the application needs as it is can be written: #what"() {
        expect:
        render(source) == written

        where:
        what                                             | source                                                                           || written
        'an expression, with a backslash'                | 'x \\${book.title} y ${project.name}'                                            || 'x ${book.title} y foo'
        'a dollar that starts no expression'             | 'cost $5 and $name'                                                              || 'cost $5 and $name'
        'an instruction tag, its < as an expression'     | '${\'<\'}g:each in="\\${books}" var="b">\\${b} ${project.name}${\'<\'}/g:each>'  || '<g:each in="${books}" var="b">${b} foo</g:each>'
        'any tag as typed, its < as an expression'       | '${\'<\'}g:render template="x" model="[a: 1]"/>'                                 || '<g:render template="x" model="[a: 1]"/>'
        'an instruction tag, as the text of an expression' | '${\'<g:if test="\\${book}">\'}t${\'</g:if>\'}'                                || '<g:if test="${book}">t</g:if>'
    }

    void "the examples of the guide write what the guide says they write"() {
        expect: 'instructions within a line'
        render('audit.store=<g:if test="${options.data == \'mongodb\'}">collection</g:if><g:else>table</g:else>\n' +
                'audit.features=<g:each in="${features}" var="feature">${feature} </g:each>\n') =~ /^audit\.store=table\naudit\.features=([a-z0-9-]+ )*acme-audit ([a-z0-9-]+ )*\n$/

        and: 'a line that holds only a tag writes its line break, and one that holds only a scriptlet does not'
        render('<g:if test="${true}">\nyes\n</g:if>\n<% if (true) { %>\nyes\n<% } %>\n') == '\nyes\n\nyes\n'

        and: 'a view with expressions of its own'
        render('<h1>\\${book.title} in ${project.naturalName}</h1>\n<g:link action="edit" id="\\${book.id}">Edit</g:link>\n') ==
                '<h1>${book.title} in Foo</h1>\n<g:link action="edit" id="${book.id}">Edit</g:link>\n'

        and: 'a view with instructions of its own, and a tag as it is typed'
        render('${\'<\'}g:each in="\\${books}" var="book">\n    <li>\\${book.title}</li>\n${\'<\'}/g:each>\n' +
                '${\'<\'}g:render template="row" model="[book: book]"/>\n') ==
                '<g:each in="${books}" var="book">\n    <li>${book.title}</li>\n</g:each>\n<g:render template="row" model="[book: book]"/>\n'
    }

    void "a backslash does not keep an instruction tag from running, and an instruction in a comment of HTML runs"() {
        expect:
        render('\\<g:if test="${true}">t\\</g:if>') == '\\t\\'
        render('<!-- <g:if test="${true}">x</g:if> -->') == '<!-- x -->'
        render('a<%-- <g:if test="${true}">x</g:if> --%>b') == 'ab'
    }

    void "a view that has nothing to fill in is a file of another name in the pack, and is written as it is"() {
        given:
        String view = '<g:if test="${book}">\n    <g:link action="show" id="${book.id}"  class=\'a\'>${book.title}</g:link>\n</g:if>\n<g:render template="row" model="[book: book]"/>\n'
        Pack pack = pack('acme-audit', "files:\n  - from: show.gsp.txt\n    to: grails-app/views/book/show.gsp\n", ['show.gsp.txt': view])

        expect:
        generate(['acme-audit'], [pack])['grails-app/views/book/show.gsp'] == view
    }

    void "a name a tag that is written out would have set is not there"() {
        when:
        render('one\n<g:set var="x" value="1"/>${x}\nthree')

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith("The template t.txt.gsp of the feature acme-audit cannot be rendered: Error evaluating expression [x] on line [2]: There is no 'x' for a template")
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
        'throws in a scriptlet of several lines'          | 'one\ntwo\n<%\n\n features.nope()\n%>\nseven\n'                                 || 'line 3: No signature of method: nope'
        'throws in a closure'                             | 'one\n<% [1].each { %>\nthree\n<% it.nope() %>\n<% } %>\n'                      || 'line 4: No signature of method: nope'
        'throws in the body of an instruction'            | 'one\n<g:each in="${[1]}" var="n">\nthree\n<% n.nope() %>\n</g:each>\n'         || 'line 4: No signature of method: nope'
        'throws in the body of a tag that is written out' | 'one\n<g:form>\nthree\n<% throw new IllegalStateException("boom") %>\n</g:form>\n' || 'line 4: Error executing tag <g:form>: boom'
        'throws in the test of an instruction'            | 'one\ntwo\n<g:if test="${features.nope()}">x</g:if>\n'                          || 'line 3: No signature of method: nope'
        'changes what it is given'                        | 'one\n<% features << "x" %>\n'                                                  || 'line 2: java.lang.UnsupportedOperationException'
        'names what there is not, in a scriptlet'         | 'one\ntwo\n<% if (flags) { %>x<% } %>\n'                                        || "line 3: There is no 'flags' for a template: template API 1 has "
        'asserts what is not so'                          | 'one\ntwo\n<% assert features.contains("nope") : "wanted" %>\n'                 || 'line 3: wanted'
        'calls itself without end'                        | 'one\ntwo\n<% def f; f = { f() }; f() %>\n'                                     || 'line 3: it calls itself without end'
        'fails in an expression'                          | 'one\ntwo\n${features.nope()}'                                                  || 'Error evaluating expression [features.nope()] on line [3]: '
        'has a scriptlet that does not compile'           | 'one\ntwo\n<% def x = ) %>\nfour\n'                                             || "line 3: it does not compile: "
        'has an expression that does not compile'         | 'one\n${1 +* }\nthree\n'                                                        || "line 2: it does not compile: "
        'has a test that does not compile'                | 'one\ntwo\nthree\n<g:if test="${)}">x</g:if>'                                   || "line 4: it does not compile: "
        'does not compile after a comment'                | '<%-- a\n header\n of three lines --%>\nfour\n<% def x = ) %>\nsix\n'           || "line 5: it does not compile: "
        'imports a class there is not'                    | 'one\ntwo\nthree\n<%@ page import="no.such.Thing" %>\nfive\nsix\n'              || 'line 4: it does not compile: unable to resolve class no.such.Thing'
        'leaves a block open'                             | 'one\n<% if (true) { %>\nthree\n'                                               || "at its end: it does not compile: Missing '}'"
        'leaves an instruction open'                      | 'one\n<g:if test="${true}">\nthree\n'                                           || 'at its end: Grails tags were not closed! [[<g:if>]]'
        'has an instruction without what it needs'        | 'one\n<g:if>x</g:if>\nthree\n'                                                  || 'line 2: Tag [if] must have one or both of the attributes [test] or [env]'
        'has a declaration, which a page cannot have'     | 'one\n<%! int n = 0 %>\nthree\n'                                                || 'line 2: JSP-style declaration blocks'
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

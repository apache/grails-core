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

import grails.util.Environment
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.ForgeContexts
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.GormImpl
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.grails.gsp.compiler.GroovyPageParser
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The pages of the templates of the packs of Forge that the build compiles: that there is one for
 * every template, that a template gives the same bytes from it as when it is compiled where it is
 * rendered, and that it is taken for the template it was compiled from and for nothing else.
 *
 * <p>The build compiles the pages for the production environment, which the command line and the web
 * application run in, and the tests run in another: each feature here sets the environment the
 * command line has, unless it says otherwise. A page and how it came to be cannot be seen in what a
 * generator writes, so most of this asks {@link PackPages}, which the generator renders with.</p>
 */
class PrecompiledPackPagesSpec extends ApplicationContextSpec {

    private static final ClassLoader FORGE = PackPages.classLoader

    @Shared
    List<Map<String, Object>> templates = templatesOfForge()

    // the models of the templates of a feature, see modelsOf
    @Shared
    Map<String, List<Map<String, Object>>> models = [:]

    @TempDir
    Path directory

    private String environmentBefore
    private String precompiledBefore

    void setup() {
        environmentBefore = System.getProperty(Environment.KEY)
        precompiledBefore = System.getProperty(BundledPackFeatures.PRECOMPILED_PROPERTY)
        System.setProperty(Environment.KEY, Environment.PRODUCTION.getName())
    }

    void cleanup() {
        restore(Environment.KEY, environmentBefore)
        restore(BundledPackFeatures.PRECOMPILED_PROPERTY, precompiledBefore)
    }

    void "the build compiled a page for every template of every pack of Forge, and for nothing else"() {
        given:
        PrecompiledPackPages ofBuild = PrecompiledPackPages.ofForge()
        Set<String> keys = templates.collect { PackPages.key(it.owner as String, it.text as String) } as Set<String>

        expect: 'packs with templates, or this would say nothing'
        keys.size() > 20

        and: 'a template that is added to a pack and not compiled by the build is found here'
        keys.findAll { !ofBuild.has(it) } == [] as Set
        ofBuild.size() == keys.size()

        and: 'the index is what the build writes: a line for each, in the order of the keys'
        List<String> lines = new String(FORGE.getResourceAsStream(PrecompiledPackPages.INDEX).readAllBytes(), StandardCharsets.UTF_8).readLines()
        lines*.split('\t')*.getAt(0) == keys.toSorted()
        lines.every { String line -> line.split('\t')[2] == "${PrecompiledPackPages.PACKAGE}.${PrecompiledPackPages.CLASS_PREFIX}${line.split('\t')[0]}" }
    }

    void "every template of every pack of Forge is taken from the build and not compiled"() {
        given:
        PackPages pages = new PackPages(FORGE, PrecompiledPackPages.ofForge())

        when:
        templates.each { render(pages, it, modelsOf(it).first()) }

        then:
        pages.precompiled == templates.collect { PackPages.key(it.owner as String, it.text as String) }.toSet().size()
        pages.compiled == 0
    }

    @Unroll
    void "the template #from of #owner writes the same bytes from the page of the build as when it is compiled"() {
        given:
        Map<String, Object> template = templates.find { it.owner == owner && it.from == from }
        PackPages ofBuild = new PackPages(FORGE, PrecompiledPackPages.ofForge())
        PackPages compiling = new PackPages(FORGE)
        List<Map<String, Object>> models = modelsOf(template)

        when:
        List<Object> taken = models.collect { render(ofBuild, template, it) }
        List<Object> compiled = models.collect { render(compiling, template, it) }

        then: 'one way took the page, the other compiled it'
        ofBuild.precompiled == 1 && ofBuild.compiled == 0
        compiling.precompiled == 0 && compiling.compiled == 1

        and: 'for the application of each kind and of each value of an option: byte for byte, or the same refusal'
        models.size() >= ApplicationType.values().size()
        (0..<models.size()).every { int i ->
            taken[i] instanceof byte[] ? Arrays.equals((byte[]) taken[i], (byte[]) compiled[i]) : taken[i] == compiled[i]
        }

        and: 'and it wrote something for at least one of them'
        taken.any { it instanceof byte[] && ((byte[]) it).length > 0 }

        where:
        [owner, from] << templatesOfForge().collect { [it.owner, it.from] }
    }

    void "applications are the same, file for file, with the pages of the build and with every template compiled"() {
        given: 'a Forge that takes the pages of the build, and one that is told to compile every template'
        ConfigurableApplicationContext taking = ForgeContexts.create()
        ConfigurableApplicationContext compiling = ForgeContexts.create()
        List<String> packs = BundledPackFeatures.names(FORGE)
        List<List<Object>> applications = ApplicationType.values().collect { ApplicationType type -> [type, []] as List<Object> } +
                packs.findAll { taking.getBean(FeatureRegistry).find(ApplicationType.WEB, it, false).present }
                        .collect { String pack -> [ApplicationType.WEB, [pack]] as List<Object> }

        when:
        List<Object> ofBuild = applications.collect { generate(taking, it[0] as ApplicationType, it[1] as List<String>) }
        System.setProperty(BundledPackFeatures.PRECOMPILED_PROPERTY, 'false')
        List<Object> compiled = applications.collect { generate(compiling, it[0] as ApplicationType, it[1] as List<String>) }

        then:
        ofBuild == compiled
        ofBuild.count { it instanceof Map } > packs.size() / 2

        and: 'the first compiled nothing, and the second took nothing'
        taking.getBean(BundledPackFeatures).precompiledTemplates > 10
        taking.getBean(BundledPackFeatures).compiledTemplates == 0
        compiling.getBean(BundledPackFeatures).precompiledTemplates == 0
        compiling.getBean(BundledPackFeatures).compiledTemplates == taking.getBean(BundledPackFeatures).precompiledTemplates

        cleanup:
        taking?.close()
        compiling?.close()
    }

    void "a template that differs by one character from one the build compiled is compiled"() {
        given:
        Map<String, Object> template = templates.find { (it.text as String).contains('${') }
        String text = template.text
        Map<String, Object> model = modelsOf(template).first()
        PackPages pages = new PackPages(FORGE, PrecompiledPackPages.ofForge())
        int at = text.indexOf('${')

        when: 'the template itself'
        byte[] original = pages.render(template.owner as String, text, 'a template', 1, model, PackLoader.MAX_FILE_BYTES)

        then:
        pages.precompiled == 1 && pages.compiled == 0

        when: 'one character more in front of its first expression, at its end, and one character another'
        byte[] inFront = pages.render(template.owner as String, text.substring(0, at) + '!' + text.substring(at), 'a template', 1, model, PackLoader.MAX_FILE_BYTES)
        byte[] atEnd = pages.render(template.owner as String, text + '!', 'a template', 1, model, PackLoader.MAX_FILE_BYTES)
        byte[] another = pages.render(template.owner as String, text.substring(0, at) + '#' + text.substring(at + 1), 'a template', 1, model, PackLoader.MAX_FILE_BYTES)

        then: 'each is compiled, and writes what it says and not what the page of the build writes'
        pages.precompiled == 1 && pages.compiled == 3
        new String(inFront, StandardCharsets.UTF_8).length() == new String(original, StandardCharsets.UTF_8).length() + 1
        new String(atEnd, StandardCharsets.UTF_8) == new String(original, StandardCharsets.UTF_8) + '!'
        new String(another, StandardCharsets.UTF_8).contains('#{')

        when: 'the text of the template, as the template of another feature'
        byte[] ofAnother = pages.render('acme-other', text, 'a template', 1, model, PackLoader.MAX_FILE_BYTES)

        then: 'a page is of one feature: the page of the build is not given to it'
        pages.precompiled == 1 && pages.compiled == 4
        Arrays.equals(ofAnother, original)
    }

    @Unroll
    void "in the environment #environment a template with an expression is #how, and writes the same"() {
        given: 'a page is compiled from one source for development and test, and from another for every other environment'
        Map<String, Object> template = templates.find { (it.text as String).contains('${') }
        Map<String, Object> model = modelsOf(template).first()
        byte[] inProduction = new PackPages(FORGE).render(template.owner as String, template.text as String, 'a template', 1, model, PackLoader.MAX_FILE_BYTES)
        System.setProperty(Environment.KEY, environment)
        PackPages pages = new PackPages(FORGE, PrecompiledPackPages.ofForge())

        when:
        byte[] rendered = pages.render(template.owner as String, template.text as String, 'a template', 1, model, PackLoader.MAX_FILE_BYTES)

        then:
        pages.precompiled == (taken ? 1 : 0)
        pages.compiled == (taken ? 0 : 1)
        Arrays.equals(rendered, inProduction)

        where:
        environment   | taken
        'production'  | true
        'development' | false
        'test'        | false
        'staging'     | true

        how = taken ? 'taken from the build' : 'compiled'
    }

    void "the system property that has pages compiled statically changes nothing: a template of a pack says that it is not"() {
        given:
        String before = System.getProperty(GroovyPageParser.CONFIG_PROPERTY_GSP_COMPILESTATIC)
        Map<String, Object> template = templates.find { (it.text as String).contains('${') }
        Map<String, Object> model = modelsOf(template).first()
        byte[] without = new PackPages(FORGE).render(template.owner as String, template.text as String, 'a template', 1, model, PackLoader.MAX_FILE_BYTES)
        System.setProperty(GroovyPageParser.CONFIG_PROPERTY_GSP_COMPILESTATIC, 'true')
        PackPages pages = new PackPages(FORGE, PrecompiledPackPages.ofForge())
        PackPages compiling = new PackPages(FORGE)

        expect: 'the directive every template starts with decides, so the source of the page is the one the build compiled'
        Arrays.equals(pages.render(template.owner as String, template.text as String, 'a template', 1, model, PackLoader.MAX_FILE_BYTES), without)
        Arrays.equals(compiling.render(template.owner as String, template.text as String, 'a template', 1, model, PackLoader.MAX_FILE_BYTES), without)
        pages.precompiled == 1 && pages.compiled == 0

        cleanup:
        restore(GroovyPageParser.CONFIG_PROPERTY_GSP_COMPILESTATIC, before)
    }

    void "a template that fails says the same from the page of the build as when it is compiled"() {
        given: 'for every template: no model, a model without the project, and one whose every value fails when it is asked anything'
        PackPages ofBuild = new PackPages(FORGE, PrecompiledPackPages.ofForge())
        PackPages compiling = new PackPages(FORGE)
        List<List<Object>> cases = []
        templates.each { Map<String, Object> template ->
            Map<String, Object> model = modelsOf(template).first()
            cases << [template, [:]]
            cases << [template, model.findAll { String name, Object value -> name != 'project' }]
            cases << [template, model.collectEntries { String name, Object value -> [(name): new Failing(name)] }]
        }

        when:
        List<String> taken = cases.collect { failure(ofBuild, it[0] as Map<String, Object>, it[1] as Map<String, Object>) }
        List<String> compiled = cases.collect { failure(compiling, it[0] as Map<String, Object>, it[1] as Map<String, Object>) }

        then: 'one way took every page and the other compiled every one'
        ofBuild.compiled == 0 && compiling.precompiled == 0
        ofBuild.precompiled == compiling.compiled

        and: 'the message, with its line, and what it was caused by are the same, case for case'
        (0..<cases.size()).findAll { int i -> taken[i] != compiled[i] } == []

        and: 'the cases are failures of each kind: a name that is missing, and an expression that throws, on a line'
        taken.count { it.contains("There is no '") } > templates.size() / 2
        taken.count { it.contains('is not to be asked') } > templates.size() / 2
        taken.count { it =~ /cannot be rendered: line \d+: / } > 0
        taken.count { it =~ /on line \[\d+\]/ } == 0
        !taken.any { it.contains(PrecompiledPackPages.CLASS_PREFIX) || it.contains('_gsp') }
    }

    @Unroll
    void "with #what every template is compiled and nothing is said"() {
        given:
        String index = new String(FORGE.getResourceAsStream(PrecompiledPackPages.INDEX).readAllBytes(), StandardCharsets.UTF_8)
        Path root = Files.createDirectories(directory.resolve('root'))
        String written = content.call(index)
        if (written != null) {
            Files.createDirectories(root.resolve(PrecompiledPackPages.INDEX).parent)
            Files.writeString(root.resolve(PrecompiledPackPages.INDEX), written)
        }
        // a class loader with an index of its own. The classes of the build are not its own: under Forge
        // it finds them above itself, alone it finds none
        URLClassLoader loader = new URLClassLoader([root.toUri().toURL()] as URL[], underForge ? FORGE : (ClassLoader) null) {

            @Override
            InputStream getResourceAsStream(String name) {
                return findResource(name)?.openStream()
            }
        }
        PrecompiledPackPages precompiled = PrecompiledPackPages.of(loader)
        Map<String, Object> template = templates.find { (it.text as String).contains('${') }
        Map<String, Object> model = modelsOf(template).first()
        PackPages pages = new PackPages(FORGE, precompiled)
        PrintStream err = System.err
        ByteArrayOutputStream said = new ByteArrayOutputStream()
        System.setErr(new PrintStream(said, true))

        when:
        byte[] rendered = pages.render(template.owner as String, template.text as String, 'a template', 1, model, PackLoader.MAX_FILE_BYTES)
        System.setErr(err)

        then:
        precompiled.size() == (named ? index.readLines().size() : 0)
        pages.precompiled == 0 && pages.compiled == 1
        Arrays.equals(rendered, new PackPages(FORGE).render(template.owner as String, template.text as String, 'a template', 1, model, PackLoader.MAX_FILE_BYTES))
        said.toString() == ''

        cleanup:
        System.setErr(err)
        loader?.close()

        where:
        what                                                    | underForge | named      | content
        'no index'                                              | true       | false      | { String i -> null }
        'an empty index'                                        | true       | false      | { String i -> '' }
        'an index that is no text'                              | true       | false      | { String i -> new String([0, 1, 9, 9, 10, 127, 9] as char[]) + '\n\t\t\t\n' }
        'an index cut off in a line'                            | true       | false      | { String i -> i.substring(0, i.length() - 40) }
        'an index with a line that is not one of the build'     | true       | false      | { String i -> i + 'what is this\n' }
        'an index that names a template twice'                  | true       | false      | { String i -> i + i.readLines().first() + '\n' }
        'an index that gives a template the class of another'   | true       | false      | { String i -> crossed(i) }
        'an index with another source for each template'        | true       | true       | { String i -> otherSources(i) }
        'the index and no class'                                | false      | true       | { String i -> i }
        'the index in a class loader the classes are not of'    | true       | true       | { String i -> i }
    }

    private static String otherSources(String index) {
        return index.readLines().collect { String line -> line.split('\t')[0] + '\t' + ('0' * 64) + '\t' + line.split('\t')[2] }.join('\n') + '\n'
    }

    void "compiling the templates again gives the classes and the index the build wrote"() {
        given: 'the Java the build compiled for, which the first class of the build names'
        String index = new String(FORGE.getResourceAsStream(PrecompiledPackPages.INDEX).readAllBytes(), StandardCharsets.UTF_8)
        byte[] first = FORGE.getResourceAsStream(index.readLines().first().split('\t')[2].replace('.', '/') + '.class').readAllBytes()
        int java = (((first[6] & 0xFF) << 8) | (first[7] & 0xFF)) - 44
        String targetBefore = System.getProperty('groovy.target.bytecode')
        System.setProperty('groovy.target.bytecode', java as String)

        when:
        SortedMap<String, PackPages.Precompiled> again = PackPagesCompiler.compile(FORGE)
        SortedMap<String, PackPages.Precompiled> andAgain = PackPagesCompiler.compile(FORGE)
        PackPagesCompiler.write(again, directory.resolve('pages'))

        then: 'in this JVM, twice, what another JVM wrote when Forge was built'
        Files.readString(directory.resolve('pages').resolve(PrecompiledPackPages.INDEX)) == index
        again.keySet() == andAgain.keySet()
        again.every { String key, PackPages.Precompiled page ->
            page.classes.size() > 0 && page.classes.keySet() == andAgain[key].classes.keySet() && page.classes.every { String name, byte[] bytes ->
                Arrays.equals(bytes, FORGE.getResourceAsStream(name.replace('.', '/') + '.class')?.readAllBytes()) && Arrays.equals(bytes, andAgain[key].classes[name])
            }
        }

        and: 'every class is of a page, the closures of a page with it: its name is made of the key of its template, in the package of the pages'
        again.every { String key, PackPages.Precompiled page ->
            String name = "${PrecompiledPackPages.PACKAGE}.${PrecompiledPackPages.CLASS_PREFIX}${key}"
            page.classes.keySet().every { it == name || it.startsWith(name + '$') }
        }

        cleanup:
        restore('groovy.target.bytecode', targetBefore)
    }

    void "no class of a page is without a package: a jar with one could not be a module of Java"() {
        given:
        String index = new String(FORGE.getResourceAsStream(PrecompiledPackPages.INDEX).readAllBytes(), StandardCharsets.UTF_8)
        List<Class<?>> classes = index.readLines().collect { String line -> Class.forName(line.split('\t')[2], false, FORGE) }
        Path written = directory.resolve('pages')
        PackPagesCompiler.write(PackPagesCompiler.compile(FORGE), written)

        expect: 'the classes the index names are of the one package, which is of this module alone and has nothing else'
        classes.size() == templates.collect { PackPages.key(it.owner as String, it.text as String) }.toSet().size()
        classes.every { it.packageName == 'org.apache.grails.forge.core.pack.pages' && it.packageName == PrecompiledPackPages.PACKAGE }
        PackPages.packageName != PrecompiledPackPages.PACKAGE

        and: 'what the build writes has every class under the directory of that package, and none beside it'
        Files.walk(written).withCloseable { it.filter { Path file -> file.toString().endsWith('.class') }.toList() }.every { Path file ->
            written.relativize(file.parent).toString().replace(File.separator, '/') == PrecompiledPackPages.PACKAGE.replace('.', '/')
        }
        Files.list(written.resolve(PrecompiledPackPages.PACKAGE.replace('.', '/'))).withCloseable { it.count() } == classes.size()
    }

    void "the closures of a template are classes of the package with its page, and a page taken with them writes and fails as when it is compiled"() {
        given: 'a template with closures, one of them failing on its third line for one model: no template of Forge has a closure as the build compiles it'
        String text = 'first\n<% 3.times { i -> %>${project.name}${i};<% } %>\n<% [1].each { %>${fails ? project.nothing.at.all : \'fine\'}<% } %>\nlast\n'
        PackPages.Precompiled page = PackPages.precompile(FORGE, 'acme-closures', text)
        Path root = directory.resolve('pages')
        PackPagesCompiler.write(new TreeMap<String, PackPages.Precompiled>([(page.key): page]), root)
        // a class loader that has the index and the classes itself, as the one of Forge has those of its packs
        URLClassLoader loader = new URLClassLoader([root.toUri().toURL()] as URL[], FORGE) {

            @Override
            InputStream getResourceAsStream(String name) {
                return findResource(name)?.openStream()
            }
        }
        PackPages taking = new PackPages(FORGE, PrecompiledPackPages.of(loader))
        PackPages compiling = new PackPages(FORGE)
        Map<String, Object> template = [owner: 'acme-closures', from: 'closures.txt.gsp', text: text, descriptor: templates.first().descriptor]
        Map<String, Object> fine = [project: [name: 'shop'], fails: false]
        Map<String, Object> failing = [project: [name: 'shop'], fails: true]

        expect: 'the page and its closures, each of the package and named for the page'
        page.classes.size() == 3
        page.classes.keySet().every { it.startsWith("${PrecompiledPackPages.PACKAGE}.${PrecompiledPackPages.CLASS_PREFIX}${page.key}") }
        page.classes.keySet().count { it.contains('$') } == 2

        and: 'taken, it writes what the compiled one writes'
        failure(taking, template, fine) == 'rendered'
        new String(taking.render('acme-closures', text, 'a template', 1, fine, PackLoader.MAX_FILE_BYTES), StandardCharsets.UTF_8) == 'first\nshop0;shop1;shop2;\nfine\nlast\n'
        Arrays.equals(taking.render('acme-closures', text, 'a template', 1, fine, PackLoader.MAX_FILE_BYTES),
                compiling.render('acme-closures', text, 'a template', 1, fine, PackLoader.MAX_FILE_BYTES))

        and: 'and fails as the compiled one does, on the line of the template the closure is on'
        failure(taking, template, failing) == failure(compiling, template, failing)
        failure(taking, template, failing).contains('cannot be rendered: line 3: ')
        !failure(taking, template, failing).contains(PrecompiledPackPages.CLASS_PREFIX)

        and:
        taking.precompiled == 1 && taking.compiled == 0
        compiling.compiled == 1

        cleanup:
        loader?.close()
    }

    void "what an earlier build wrote for a template that is no more is deleted, and nothing else in the directory"() {
        given:
        Path root = Files.createDirectories(directory.resolve('pages'))
        Path pages = Files.createDirectories(root.resolve(PrecompiledPackPages.PACKAGE.replace('.', '/')))
        Path stale = Files.writeString(pages.resolve(PrecompiledPackPages.CLASS_PREFIX + ('0' * 64) + '.class'), 'of an earlier build')
        Path withoutPackage = Files.writeString(root.resolve(PrecompiledPackPages.CLASS_PREFIX + ('1' * 64) + '.class'), 'of an earlier form of the build')
        Path other = Files.writeString(pages.resolve('Other.class'), 'not of this')
        Map<String, Object> template = templates.first()
        PackPages.Precompiled page = PackPages.precompile(FORGE, template.owner as String, template.text as String)

        when:
        PackPagesCompiler.write(new TreeMap<String, PackPages.Precompiled>([(page.key): page]), root)

        then:
        !Files.exists(stale)
        !Files.exists(withoutPackage)
        Files.exists(other)
        Files.exists(pages.resolve(PrecompiledPackPages.CLASS_PREFIX + page.key + '.class'))
        Files.readString(root.resolve(PrecompiledPackPages.INDEX)).readLines().size() == 1
    }

    // the first two templates with the class of each other
    private static String crossed(String index) {
        List<String> lines = index.readLines()
        List<String> first = lines[0].split('\t') as List<String>
        List<String> second = lines[1].split('\t') as List<String>
        return ([[first[0], first[1], second[2]].join('\t'), [second[0], second[1], first[2]].join('\t')] + lines.drop(2)).join('\n') + '\n'
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property)
        } else {
            System.setProperty(property, value)
        }
    }

    // what the template writes, or what it is refused with
    private static Object render(PackPages pages, Map<String, Object> template, Map<String, Object> model) {
        try {
            return pages.render(template.owner as String, template.text as String, 'a template',
                    (template.descriptor as PackDescriptor).templateApi, model, PackLoader.MAX_FILE_BYTES)
        } catch (IllegalArgumentException e) {
            return e.message
        }
    }

    // what the template is refused with and the kinds of what that was caused by, or that it was rendered
    private static String failure(PackPages pages, Map<String, Object> template, Map<String, Object> model) {
        try {
            pages.render(template.owner as String, template.text as String, "${template.from} of the feature ${template.owner}".toString(),
                    (template.descriptor as PackDescriptor).templateApi, model, PackLoader.MAX_FILE_BYTES)
            return 'rendered'
        } catch (IllegalArgumentException e) {
            List<String> causes = []
            for (Throwable cause = e.cause; cause != null && causes.size() < 10; cause = cause.cause) {
                causes << cause.getClass().name
            }
            return "${e.message} ${causes}".toString()
        }
    }

    // a value of a model that fails whatever it is asked
    static class Failing {

        private final String name

        Failing(String name) {
            this.name = name
        }

        def propertyMissing(String property) {
            throw new IllegalStateException("${name} is not to be asked for ${property}")
        }

        def methodMissing(String method, Object arguments) {
            throw new IllegalStateException("${name} is not to be asked to ${method}")
        }

        @Override
        String toString() {
            throw new IllegalStateException("${name} is not to be asked what it is")
        }
    }

    private static Object generate(ConfigurableApplicationContext forge, ApplicationType type, List<String> features) {
        MapOutputHandler output = new MapOutputHandler()
        Options options = new Options()
        try {
            forge.getBean(ProjectGenerator).generate(type, NameUtils.parse('example.grails.foo'), options, options.operatingSystem, features,
                    output, ConsoleOutput.NOOP)
        } catch (RuntimeException e) {
            // a feature that is refused when it is selected alone
            return e.message
        }
        // the state under .grails has the time of the generation
        return output.getProject().findAll { String path, String content -> !ProjectState.isStatePath(path) }
    }

    /**
     * The models a template is rendered with: that of an application of each kind, with the feature of
     * the template where the kind can have it, and of a web application for each other value of the
     * options a model has.
     */
    private List<Map<String, Object>> modelsOf(Map<String, Object> template) {
        String owner = template.owner
        if (models.containsKey(owner)) {
            return models[owner]
        }
        models[owner] = modelsOf(owner, template.descriptor as PackDescriptor)
        return models[owner]
    }

    private List<Map<String, Object>> modelsOf(String owner, PackDescriptor descriptor) {
        List<List<Object>> applications = ApplicationType.values().collect { ApplicationType type -> [type, new Options()] as List<Object> }
        GormImpl.values().findAll { it != GormImpl.DEFAULT_OPTION }.each { applications << ([ApplicationType.WEB, new Options().withGormImpl(it)] as List<Object>) }
        ServletImpl.values().findAll { it != ServletImpl.DEFAULT_OPTION }.each { applications << ([ApplicationType.WEB, new Options().withServletImpl(it)] as List<Object>) }
        return applications.collect { List<Object> application ->
            GeneratorContext context
            try {
                context = buildGeneratorContext([owner], application[1] as Options, application[0] as ApplicationType)
            } catch (RuntimeException ignored) {
                // not a feature of this kind of application, or not one that is selected alone
                context = buildGeneratorContext([], application[1] as Options, application[0] as ApplicationType)
            }
            return PackModel.of(context, descriptor)
        }
    }

    // every file of every pack of Forge that is rendered, with the name of its feature and its text
    private static List<Map<String, Object>> templatesOfForge() {
        List<Map<String, Object>> templates = []
        PackLoader packs = new PackLoader()
        BundledPackFeatures.names(FORGE).each { String name ->
            Pack pack = packs.fromClasspath(FORGE, name)
            pack.descriptor.files.findAll { PackFile file -> file.isTemplate() }.collect { PackFile file -> file.from }.unique().each { String from ->
                templates << ([owner: pack.descriptor.name, from: from, text: new String(pack.content(from), StandardCharsets.UTF_8),
                               descriptor: pack.descriptor] as Map<String, Object>)
            }
        }
        return templates
    }
}

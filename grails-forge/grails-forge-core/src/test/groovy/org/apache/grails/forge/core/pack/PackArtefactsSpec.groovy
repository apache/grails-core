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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.apache.grails.forge.core.state.StateYaml
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
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The commands a feature pack gives the command line, which its descriptor declares as
 * {@code artefacts}: what a descriptor can say of them, how their templates are read and checked, that
 * an application is generated with the templates as they are, and what its record keeps of them.
 */
class PackArtefactsSpec extends ApplicationContextSpec {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"

    static final String ARTEFACTS = '''\
        artefacts:
          - name: audit-listener
            description: Creates a listener of audit events
            suffix: AuditListener
            template: artefacts/AuditListener.groovy
            to: grails-app/services/{packagePath}/{className}AuditListener.groovy
            testTemplate: artefacts/AuditListenerSpec.groovy
            testTo: src/test/groovy/{packagePath}/{className}AuditListenerSpec.groovy
          - name: audit-report
            template: artefacts/AuditReport.groovy
            to: src/main/groovy/{packagePath}/{className}AuditReport.groovy
        '''.stripIndent()

    // with what a template that is rendered would trip over, and what the build of a plugin would replace
    static final String LISTENER = '@artifact.package@\nclass @artifact.name@AuditListener {\n\n    String name = \'@artifact.propertyName@\'\n' +
            '    String mail = "audit@example.org"\n    String text = "${name} <%= 1 %> @info.app.name@"\n}\n'
    static final String LISTENER_SPEC = '@artifact.package@\nimport spock.lang.Specification\n\nclass @artifact.name@AuditListenerSpec extends Specification {\n}\n'
    static final String REPORT = 'package @artifact.package.name@\n\nclass @artifact.name@AuditReport {\n    // @artifact.package.path@\n}\n'

    static final Map<String, String> TEMPLATES = ['artefacts/AuditListener.groovy'    : LISTENER,
                                                  'artefacts/AuditListenerSpec.groovy': LISTENER_SPEC,
                                                  'artefacts/AuditReport.groovy'      : REPORT]

    static final String LOCKED = '''\
          artefacts:
          - name: audit-listener
            description: Creates a listener of audit events
            suffix: AuditListener
            template: artefacts/AuditListener.groovy
            to: grails-app/services/{packagePath}/{className}AuditListener.groovy
            testTemplate: artefacts/AuditListenerSpec.groovy
            testTo: src/test/groovy/{packagePath}/{className}AuditListenerSpec.groovy
          - name: audit-report
            description: Creates a class (audit-report)
            template: artefacts/AuditReport.groovy
            to: src/main/groovy/{packagePath}/{className}AuditReport.groovy
        '''.stripIndent()

    @TempDir
    Path directory

    void "a descriptor declares the commands of its feature"() {
        when:
        PackDescriptor descriptor = new PackDescriptorParser().parse(HEADER + ARTEFACTS, 'acme.yml')

        then:
        descriptor.artefacts*.commandName == ['create-audit-listener', 'create-audit-report']
        descriptor.artefacts[0].description == 'Creates a listener of audit events'
        descriptor.artefacts[0].suffix == 'AuditListener'
        descriptor.artefacts[0].templates == ['artefacts/AuditListener.groovy', 'artefacts/AuditListenerSpec.groovy']
        descriptor.artefacts[0].testTo == 'src/test/groovy/{packagePath}/{className}AuditListenerSpec.groovy'

        and: 'a command that says nothing of itself has a description, and neither a suffix nor a test'
        descriptor.artefacts[1].description == 'Creates a class (audit-report)'
        descriptor.artefacts[1].suffix == null
        descriptor.artefacts[1].templates == ['artefacts/AuditReport.groovy']

        and: 'a descriptor without any has none, and is equal to no descriptor that has'
        new PackDescriptorParser().parse(HEADER, 'acme.yml').artefacts == []
        new PackDescriptorParser().parse(HEADER, 'acme.yml') != descriptor
    }

    @Unroll
    void "the commands of a descriptor are held to what they are: #problem"() {
        when:
        new PackDescriptorParser().parse(HEADER + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [problem]

        where:
        text | problem
        'artefacts: { name: a }\n' | 'artefacts: must be a list of mappings, but is a mapping'
        entry(name: 'Audit') | "artefacts[0].name: must be lower case letters and digits in words joined by hyphens, starting with a letter: " +
                "the command is create- and the name, but is 'Audit'"
        entry(name: 'controller') | 'artefacts[0].name: create-controller is a command of the command line itself, or was one of the Grails shell, and a pack gives none of that name'
        entry(name: 'app') | 'artefacts[0].name: create-app is a command of the command line itself, or was one of the Grails shell, and a pack gives none of that name'
        entry(name: 'script') | 'artefacts[0].name: create-script is a command of the command line itself, or was one of the Grails shell, and a pack gives none of that name'
        entry([:]) + entry([:]) .replace('artefacts:\n', '') | "artefacts[1].name: 'audit' is the name of an earlier command too"
        entry(name: null) | 'artefacts[0].name: is required'
        entry(template: null) | 'artefacts[0].template: is required'
        entry(to: null) | 'artefacts[0].to: is required'
        entry(template: '../Audit.groovy') | "artefacts[0].template: must be a path below the root with '/' between its at most 32 steps, where a step is not '.' or '..', " +
                "does not start or end with a space or end with '.', is not a name an operating system or Git reserves, and has no character a file system gives a meaning, " +
                "but is '../Audit.groovy'"
        entry(to: "'{packagePath}/Audit.groovy'") | "artefacts[0].to: must have the placeholder {className}, the name of the class that is created, " +
                "as in 'grails-app/services/{packagePath}/{className}Service.groovy', but is '{packagePath}/Audit.groovy'"
        entry(to: "'../{className}.groovy'") | "artefacts[0].to: must be a path below the root with '/' between its at most 32 steps, where a step is not '.' or '..', " +
                "does not start or end with a space or end with '.', is not a name an operating system or Git reserves, and has no character a file system gives a meaning, " +
                "but is '../{className}.groovy'"
        entry(to: "'src/{className}/{version}.groovy'") | "artefacts[0].to: can use the placeholders [packageName, packagePath, className, naturalName, propertyName, name], " +
                "but has '{version}'"
        entry(suffix: 'listener') | "artefacts[0].suffix: must be what the kind of class adds to the name of a class, such as 'Listener': at most 40 letters and digits " +
                "that start with a capital, but is 'listener'"
        entry(testTemplate: 'T.groovy') | 'artefacts[0].testTo: is required with testTemplate: a test has a template and a path, or the command writes none'
        entry(testTo: "'src/test/groovy/{className}Spec.groovy'") | 'artefacts[0].testTemplate: is required with testTo: a test has a template and a path, or the command writes none'
        entry(testTemplate: 'T.groovy', testTo: "'SRC/main/groovy/{className}.groovy'") |
                "artefacts[0].testTo: 'SRC/main/groovy/{className}.groovy' is where the class is written too, at least where the case of letters does not count"
        entry(run: 'true') | "artefacts[0]: has the key 'run', which this version of Forge does not read here; the keys are " +
                '[name, description, suffix, template, to, testTemplate, testTo]'
        entry(description: '"two\\nlines"') | "artefacts[0].description: must be one line of text of at most 200 characters that is not blank and has no control characters, " +
                "but is 'two?lines'"
        'artefacts:\n' + (1..21).collect { "  - name: a${it}\n    template: A.groovy\n    to: 'src/{className}${it}.groovy'\n" }.join('') |
                'artefacts: can have 20 commands, but has 21'
    }

    void "a preset gives the command line no commands"() {
        when:
        new PackDescriptorParser().parse(HEADER.replace('kind: feature', 'kind: preset') + 'applicationType: web\n' + ARTEFACTS, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['artefacts: a preset gives the command line no commands; a feature does, and a preset asks for it with features']
    }

    @Unroll
    void "the templates of the commands are read with the pack of a #from, and are part of what the pack is"() {
        when:
        Pack pack = packOf(from, ARTEFACTS, TEMPLATES)
        Pack other = packOf(from, ARTEFACTS, TEMPLATES + ['artefacts/AuditReport.groovy': REPORT + '\n'], 'other')

        then:
        new String(pack.content('artefacts/AuditListener.groovy'), StandardCharsets.UTF_8) == LISTENER
        new String(pack.content('artefacts/AuditReport.groovy'), StandardCharsets.UTF_8) == REPORT

        and: 'one byte of a template is another pack'
        pack.contentSha256 ==~ /[0-9a-f]{64}/
        pack.contentSha256 != other.contentSha256

        and: 'what the pack adds says the commands, and where their templates go'
        pack.summary().contains('commands it gives the command line of the application, whose templates it writes under ' +
                'src/main/templates/features/acme-audit: create-audit-listener, create-audit-report')

        where:
        from << ['directory', 'jar']
    }

    @Unroll
    void "a template of a command that could not be rendered is refused when the pack of a #from is read: #what"() {
        when:
        packOfBytes(from, ARTEFACTS, TEMPLATES.collectEntries { String path, String text -> [path, text.getBytes(StandardCharsets.UTF_8)] } + files)

        then:
        PackDescriptorException e = thrown()
        e.problems == [problem.replace('TEMPLATES', from == 'jar' ? 'META-INF/grails-forge/templates of the jar' : 'templates of the pack, or is a link or is reached through one')]

        where:
        [from, what, files, problem] << ['directory', 'jar'].collectMany { String each ->
            [
                    [each, 'a token there is not', ['artefacts/AuditReport.groovy': 'class X {\n}\n// @artifact.nme@\n'.bytes],
                     "artefacts[1].template: 'artefacts/AuditReport.groovy' is the template of a command and cannot be rendered: line 3: there is no @artifact.nme@; " +
                             'a template has @artifact.package@, @artifact.package.name@, @artifact.package.path@, @artifact.name@, @artifact.propertyName@'],
                    [each, 'not text', ['artefacts/AuditListenerSpec.groovy': [0xC3, 0x28] as byte[]],
                     "artefacts[0].testTemplate: 'artefacts/AuditListenerSpec.groovy' is the template of a command and is not text in UTF-8"],
                    [each, 'a NUL character', ['artefacts/AuditListener.groovy': 'class X {\u0000}'.bytes],
                     "artefacts[0].template: 'artefacts/AuditListener.groovy' is the template of a command and has a NUL character, which no source file has"],
                    [each, 'too large', ['artefacts/AuditListener.groovy': new byte[PackArtefact.MAX_TEMPLATE_BYTES + 1]],
                     "artefacts[0].template: 'artefacts/AuditListener.groovy' has more than the 262144 bytes a template of a command can have"],
                    [each, 'missing', ['artefacts/AuditReport.groovy': null],
                     "artefacts[1].template: 'artefacts/AuditReport.groovy' is not a file under TEMPLATES"],
            ]
        }
    }

    @Unroll
    void "an application is generated with the templates of the commands of the pack of a #from as they are, and its record has the commands"() {
        given:
        Pack pack = packOf(from, ARTEFACTS, TEMPLATES)

        when:
        Map<String, String> output = generate([pack])

        then: 'byte for byte: nothing of a template is rendered or replaced when the application is generated'
        output['src/main/templates/features/acme-audit/artefacts/AuditListener.groovy'] == LISTENER
        output['src/main/templates/features/acme-audit/artefacts/AuditListenerSpec.groovy'] == LISTENER_SPEC
        output['src/main/templates/features/acme-audit/artefacts/AuditReport.groovy'] == REPORT

        and: 'they are files of the application in its record, with the digests of what was written'
        output['.grails/baseline/files/src/main/templates/features/acme-audit/artefacts/AuditListener.groovy'] == LISTENER
        ProjectState state = ProjectState.of(output['.grails/project.yml'].bytes, output['.grails/forge.lock.yml'].bytes, output['.grails/baseline/index.yml'].bytes)
        state.baseline.files.find { it.path == 'src/main/templates/features/acme-audit/artefacts/AuditListener.groovy' }.sha256 ==
                StateYaml.sha256(LISTENER.getBytes(StandardCharsets.UTF_8))

        and: 'the lock has the commands under the feature, and under no other'
        output['.grails/forge.lock.yml'].contains(LOCKED)
        state.lock.features.find { it.name == 'acme-audit' }.artefacts*.commandName == ['create-audit-listener', 'create-audit-report']
        state.lock.features.findAll { it.name != 'acme-audit' }.every { it.artefacts.isEmpty() }
        output['.grails/forge.lock.yml'].count('artefacts:') == 1

        where:
        from << ['directory', 'jar']
    }

    void "the record of an application whose features give no commands is what it was"() {
        given:
        Pack pack = packOf('directory', '', [:])

        when:
        Map<String, String> output = generate([pack])

        then:
        !output['.grails/forge.lock.yml'].contains('artefacts')
        !output.keySet().any { it.startsWith('src/main/templates') }
    }

    void "a feature Forge has in itself writes the templates of its commands as the feature of a plugin does"() {
        given:
        PackDescriptor descriptor = new PackDescriptorParser().parseOfForge(HEADER + ARTEFACTS, 'acme.yml')
        Pack pack = new Pack(descriptor, 'the features of Forge (acme-audit)',
                TEMPLATES.collectEntries { String path, String text -> [path, text.getBytes(StandardCharsets.UTF_8)] } as Map<String, byte[]>, true)

        when:
        Map<String, String> output = generate([pack])

        then:
        output['src/main/templates/features/acme-audit/artefacts/AuditListener.groovy'] == LISTENER
        output['.grails/forge.lock.yml'].contains('- name: acme-audit\n  source: bundled-pack\n  artefacts:\n  - name: audit-listener\n')
    }

    void "two features of one application cannot give the command line the same command"() {
        given:
        Pack pack = packOf('directory', ARTEFACTS, TEMPLATES)
        Path second = Files.createDirectories(directory.resolve('second/templates'))
        Files.writeString(second.parent.resolve('feature.yml'), HEADER.replace('acme-audit', 'acme-events') +
                "artefacts:\n  - name: audit-report\n    template: R.groovy\n    to: 'src/main/groovy/{className}Report.groovy'\n")
        Files.writeString(second.resolve('R.groovy'), 'class @artifact.name@Report { }\n')

        when:
        generate([pack, new PackLoader().fromDirectory(second.parent)], ['acme-audit', 'acme-events'])

        then:
        IllegalArgumentException e = thrown()
        e.message ==~ /The feature acme-(audit|events) gives the command line the command create-audit-report, and so does the feature acme-(audit|events): an application has one of the two features/
    }

    void "a file of the pack and the template of a command cannot be one file of the application"() {
        given:
        Pack pack = packOf('directory', ARTEFACTS + "files:\n  - from: artefacts/AuditReport.groovy\n    to: src/main/templates/features/acme-audit/artefacts/AuditReport.groovy\n", TEMPLATES)

        when:
        generate([pack])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The features write the file src/main/templates/features/acme-audit/artefacts/AuditReport.groovy more than once'
    }

    @Unroll
    void "the lock of a project is held to what a pack can declare: #what"() {
        given:
        Map<String, String> output = generate([packOf('directory', ARTEFACTS, TEMPLATES)])
        String lock = sealed(output['.grails/forge.lock.yml'].replace(from, to))

        int index = ProjectState.of(output['.grails/project.yml'].bytes, output['.grails/forge.lock.yml'].bytes, output['.grails/baseline/index.yml'].bytes)
                .lock.featureNames.indexOf('acme-audit')

        when:
        ProjectState.of(output['.grails/project.yml'].bytes, lock.bytes, output['.grails/baseline/index.yml'].bytes)

        then:
        ProjectStateException e = thrown()
        e.message == message.replace('IDX', index as String)

        where:
        what                          | from                                            | to                                        || message
        'the name of a command there is' | 'name: audit-report'                         | 'name: controller'                        ||
                '.grails/forge.lock.yml: features[IDX].artefacts[1].name is controller, and create-controller is a command of the command line itself'
        'a file outside the project'  | 'to: src/main/groovy/{packagePath}/{className}' | 'to: ../../{packagePath}/{className}'     ||
                '.grails/forge.lock.yml: features[IDX].artefacts[1].to must be a path below the project that has {className} in it and no placeholder a command does not have'
        'a template outside the feature' | 'template: artefacts/AuditReport.groovy'     | 'template: ../../../../build.gradle'      ||
                '.grails/forge.lock.yml: features[IDX].artefacts[1].template must be the path of a template below the templates of the feature'
        'a key that is not read'      | '    description: Creates a class (audit-report)\n' | '    description: x\n    run: rm\n' ||
                '.grails/forge.lock.yml: features[IDX].artefacts[1].run is not a key this version of Grails reads'
        'a command twice'             | 'name: audit-report'                            | 'name: audit-listener'                    ||
                '.grails/forge.lock.yml: features[IDX].artefacts has a command more than once'
    }

    void "the check of a pack reads the commands and their templates"() {
        given:
        Path pack = directory.resolve('checked')
        Files.createDirectories(pack.resolve('templates/artefacts'))
        Files.writeString(pack.resolve('feature.yml'), HEADER + ARTEFACTS)
        TEMPLATES.each { String path, String text -> Files.writeString(pack.resolve('templates').resolve(path), text) }
        ByteArrayOutputStream out = new ByteArrayOutputStream()

        expect: 'a pack whose commands can be used passes, and nothing of a template of a command is run'
        PackCheck.check(pack) == []
        PackCheck.run([pack.toString()] as String[], new PrintStream(out), new PrintStream(new ByteArrayOutputStream())) == PackCheck.VALID
        out.toString().trim() == 'feature pack checked: templates=0 applications=5'

        when:
        Files.writeString(pack.resolve('templates/artefacts/AuditReport.groovy'), 'class @artifact.className@ { }\n')
        out.reset()

        then:
        PackCheck.check(pack) == ["artefacts[1].template: 'artefacts/AuditReport.groovy' is the template of a command and cannot be rendered: line 1: there is no " +
                                          '@artifact.className@; a template has @artifact.package@, @artifact.package.name@, @artifact.package.path@, @artifact.name@, ' +
                                          '@artifact.propertyName@']
        PackCheck.run([pack.toString()] as String[], new PrintStream(out), new PrintStream(new ByteArrayOutputStream())) == PackCheck.PROBLEMS

        when:
        Files.writeString(pack.resolve('feature.yml'), HEADER + ARTEFACTS.replace('name: audit-report', 'name: service'))

        then:
        PackCheck.check(pack) == ['artefacts[1].name: create-service is a command of the command line itself, or was one of the Grails shell, and a pack gives none of that name']
    }

    // one command, with the given keys in place of those it has; a key that is given as null is left out
    private static String entry(Map<String, String> changed) {
        Map<String, String> keys = [name: 'audit', template: 'Audit.groovy', to: "'src/main/groovy/{className}.groovy'"] + changed
        return 'artefacts:\n' + keys.findAll { it.value != null }.collect { String key, String value -> "    ${key}: ${value}\n" }.join('').replaceFirst(' {4}', '  - ')
    }

    // the lock with the digest it ends with written again for what it now says, and the place of the feature in a message
    private static String sealed(String lock) {
        String text = lock.substring(0, lock.lastIndexOf('seal:'))
        return text + 'seal:\n  sha256: ' + StateYaml.sha256(text.getBytes(StandardCharsets.UTF_8)) + '\n'
    }

    private Pack packOf(String from, String body, Map<String, String> files, String name = 'acme-audit') {
        return packOfBytes(from, body, files.collectEntries { String path, String text -> [path, text.getBytes(StandardCharsets.UTF_8)] } as Map<String, byte[]>, name)
    }

    private Pack packOfBytes(String from, String body, Map<String, byte[]> files, String name = 'acme-audit') {
        String descriptor = HEADER + body
        Map<String, byte[]> present = files.findAll { it.value != null }
        if (from == 'jar') {
            Path jar = directory.resolve("${name}.jar")
            new ZipOutputStream(Files.newOutputStream(jar)).withCloseable { ZipOutputStream zip ->
                (['META-INF/grails-forge/feature.yml': descriptor.getBytes(StandardCharsets.UTF_8)] + present.collectEntries { String path, byte[] content ->
                    ["META-INF/grails-forge/templates/${path}".toString(), content]
                }).each { String path, byte[] content ->
                    zip.putNextEntry(new ZipEntry(path))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            return new PackLoader().fromJar(jar)
        }
        Path packDirectory = Files.createDirectories(directory.resolve(name))
        Files.writeString(packDirectory.resolve('feature.yml'), descriptor)
        present.each { String path, byte[] content ->
            Files.createDirectories(packDirectory.resolve('templates').resolve(path).parent)
            Files.write(packDirectory.resolve('templates').resolve(path), content)
        }
        return new PackLoader().fromDirectory(packDirectory)
    }

    private Map<String, String> generate(List<Pack> packs, List<String> features = ['acme-audit']) {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                new ArrayList<String>(features), ConsoleOutput.NOOP, packs)
        generator.generate(ApplicationType.WEB, project, output, context)
        return output.getProject()
    }
}

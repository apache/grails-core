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
package org.apache.grails.forge.core.state

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackLoader
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.diff.FeatureDiffer
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.io.ZipOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo
import spock.lang.IgnoreIf
import spock.lang.TempDir
import spock.lang.Unroll

class ProjectStateSpec extends ApplicationContextSpec {

    @TempDir
    Path directory

    void "an application is generated with its manifest, its lock and its baseline"() {
        given:
        Path project = generate('shop', ['grails-web-console', 'gradle-settings-file'])

        expect: 'the manifest says what was asked for'
        project.resolve('.grails/project.yml').toFile().getText('UTF-8') == """\
            format: 1
            grailsVersion: ${VersionInfo.grailsVersion}
            applicationType: web
            name: shop
            defaultPackage: com.acme
            options:
              jdk: 21
              servlet: tomcat
              data: hibernate5
              reloading: devtools
            generationState: tracked
            requestedFeatures:
            - name: gradle-settings-file
            - name: grails-web-console
            excludedFeatures: []
            parameters: {}
            """.stripIndent()

        when:
        ProjectState state = ProjectState.read(project)

        then: 'the lock says what that came to, in the order the features were applied in'
        state.manifest.tracked
        state.manifest.requestedFeatures*.name == ['gradle-settings-file', 'grails-web-console']
        state.manifest.excludedFeatures.empty
        state.lock.generatorName == 'grails-forge'
        state.lock.generatorVersion == VersionInfo.version
        state.lock.grailsVersion == VersionInfo.grailsVersion
        state.lock.templateApi == 1
        state.lock.applicationType == ApplicationType.WEB
        state.lock.operatingSystem == OperatingSystem.LINUX
        state.lock.options == state.manifest.options
        state.lock.featureNames == appliedFeatures(ApplicationType.WEB, ['grails-web-console', 'gradle-settings-file'])
        state.lock.featureNames.containsAll(['gradle-settings-file', 'grails-web-console', 'spock', 'gorm-hibernate5'])
        state.lock.features.find { it.name == 'grails-web-console' }.source == LockedFeature.BUNDLED_PACK
        state.lock.features.find { it.name == 'spock' }.source == LockedFeature.FRAMEWORK
        state.lock.features.every { it.origin == null && it.coordinates == null && it.version == null && it.sha256 == null }

        and: 'the lock names the manifest and the index of the baseline it was written with'
        state.lock.manifestSha256 == sha256(project.resolve('.grails/project.yml').toFile().bytes)
        state.lock.baselineIndexSha256 == sha256(project.resolve('.grails/baseline/index.yml').toFile().bytes)

        and: 'the baseline is every file of the application and nothing of the state'
        state.baseline.files*.path == applicationFiles(project)
        state.baseline.files*.path.contains('grails-forge-cli.yml')
        state.baseline.files.every { BaselineFile file ->
            file.sha256 == sha256(project.resolve(file.path).toFile().bytes) &&
                    project.resolve('.grails/baseline/files').resolve(file.path).toFile().bytes == project.resolve(file.path).toFile().bytes
        }

        and: 'Git is told to keep the bytes of the state, which the digests are of'
        project.resolve('.grails/.gitattributes').toFile().getText('UTF-8') == '* -text\n'
        stateFiles(project) - state.baseline.files.collect { '.grails/baseline/files/' + it.path } ==
                ['.grails/.gitattributes', '.grails/baseline/index.yml', '.grails/forge.lock.yml', '.grails/project.yml']

        and: 'with what kind of file each is'
        state.baseline.files.find { it.path == 'gradlew' }.executable
        !state.baseline.files.find { it.path == 'build.gradle' }.executable
        state.baseline.files.find { it.path == 'gradle/wrapper/gradle-wrapper.jar' }.binary
        state.baseline.files.find { it.path == 'grails-app/assets/images/favicon.ico' }.binary
        !state.baseline.files.find { it.path == 'grails-app/assets/images/grails.svg' }.binary
        !state.baseline.files.find { it.path == 'build.gradle' }.binary

        when:
        state.verifyBaseline(project)

        then:
        noExceptionThrown()
    }

    void "the state is the same whenever the same application is generated"() {
        given: 'the properties feature writes a file java.util.Properties puts the time of day in'
        Path first = generate('shop', ['properties', 'grails-quartz'], ApplicationType.WEB, 'first')
        Path second = generate('shop', ['grails-quartz', 'properties'], ApplicationType.WEB, 'second')

        expect:
        stateFiles(first) == stateFiles(second)
        stateFiles(first).every { String path -> first.resolve(path).toFile().bytes == second.resolve(path).toFile().bytes }
        first.resolve('grails-app/conf/application.properties').toFile().bytes == second.resolve('grails-app/conf/application.properties').toFile().bytes
        !first.resolve('grails-app/conf/application.properties').toFile().getText('ISO-8859-1').startsWith('#')
        !first.resolve('grails-app/conf/application.properties').toFile().getText('ISO-8859-1').contains('\r')
    }

    @Unroll
    void "the state of a #type application with #gorm, #servlet, #reloading and Java #jdk is read back"() {
        given:
        Options options = new Options(reloading, gorm, servlet, jdk, OperatingSystem.MACOS_ARCH64)
        Path project = generate('shop', [], type, 'app', options, OperatingSystem.MACOS_ARCH64)

        when:
        ProjectState state = ProjectState.read(project)
        state.verifyBaseline(project)

        then:
        state.manifest.applicationType == type
        state.manifest.name == 'shop'
        state.manifest.defaultPackage == 'com.acme'
        state.manifest.options.jdk == jdk
        state.manifest.options.data == gorm
        state.manifest.options.servlet == servlet
        state.manifest.options.reloading == reloading
        state.manifest.requestedFeatures.empty
        state.lock.operatingSystem == OperatingSystem.MACOS_ARCH64
        state.lock.featureNames == appliedFeatures(type, [], options, OperatingSystem.MACOS_ARCH64)
        state.baseline.files*.path == applicationFiles(project)

        where:
        type                       | gorm                | servlet              | reloading                   | jdk
        ApplicationType.WEB        | GormImpl.HIBERNATE7 | ServletImpl.JETTY    | DevelopmentReloading.NONE   | JdkVersion.JDK_25
        ApplicationType.REST_API   | GormImpl.MONGODB    | ServletImpl.UNDERTOW | DevelopmentReloading.JREBEL | JdkVersion.JDK_21
        ApplicationType.PLUGIN     | GormImpl.HIBERNATE5 | ServletImpl.NONE     | DevelopmentReloading.NONE   | JdkVersion.JDK_21
        ApplicationType.WEB_PLUGIN | GormImpl.NEO4J      | ServletImpl.TOMCAT   | DevelopmentReloading.DEVTOOLS | JdkVersion.JDK_21
    }

    void "the feature of a feature pack is locked as one, without the path it was read from"() {
        given:
        Pack pack = pack('acme-audit')
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('com.acme.shop')
        Path target = Files.createDirectory(directory.resolve('app'))
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                ['acme-audit'], ConsoleOutput.NOOP, [pack])

        when:
        generator.generate(ApplicationType.WEB, project, new FileSystemOutputHandler(target.toFile(), ConsoleOutput.NOOP), context)
        ProjectState state = ProjectState.read(target)
        LockedFeature locked = state.lock.features.find { it.name == 'acme-audit' }

        then:
        state.manifest.requestedFeatures*.name == ['acme-audit']
        locked.source == LockedFeature.PACK
        locked.origin == null
        locked.coordinates == null
        locked.sha256 == null
        !target.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8').contains(directory.toString())
        state.baseline.files*.path.contains('grails-app/conf/audit.txt')
    }

    void "a lock can say which artifact the feature of a feature pack came from"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lock, lock.toFile().getText('UTF-8').replace('features:\n', 'features:\n- name: acme-audit\n  source: pack\n' +
                "  origin: the plugin index\n  coordinates: com.acme:acme-audit-grails\n  version: 2.3.0\n  sha256: ${'ab' * 32}\n"))

        when:
        LockedFeature locked = ProjectState.read(project).lock.features.first()

        then:
        locked.name == 'acme-audit'
        locked.source == LockedFeature.PACK
        locked.origin == 'the plugin index'
        locked.coordinates == 'com.acme:acme-audit-grails'
        locked.version == '2.3.0'
        locked.sha256 == 'ab' * 32
    }

    void "a feature cannot write into the state of the project"() {
        given:
        Pack pack = pack('acme-audit', "'.grails/cache/audit.txt'")
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('com.acme.shop')
        MapOutputHandler output = new MapOutputHandler()

        when:
        generator.generate(ApplicationType.WEB, project, output, generator.createGeneratorContext(ApplicationType.WEB, project,
                new Options(), OperatingSystem.LINUX, ['acme-audit'], ConsoleOutput.NOOP, [pack]))

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The features write the file .grails/cache/audit.txt, and .grails is where the state of the project is kept'
        output.project.isEmpty()
    }

    void "a file that is already there is kept, and the baseline has what the generator would have written"() {
        given:
        Path project = Files.createDirectory(directory.resolve('app'))
        Files.writeString(project.resolve('build.gradle'), '// mine\n')

        when:
        generateInto(project, 'shop', [])
        ProjectState state = ProjectState.read(project)
        state.verifyBaseline(project)

        then:
        project.resolve('build.gradle').toFile().getText('UTF-8') == '// mine\n'
        project.resolve('.grails/baseline/files/build.gradle').toFile().getText('UTF-8').contains('apply plugin')
        state.baseline.files.find { it.path == 'build.gradle' }.sha256 == sha256(project.resolve('.grails/baseline/files/build.gradle').toFile().bytes)
        state.manifest.tracked
    }

    void "an application is not generated over the state of another"() {
        given:
        Path project = generate('shop', [])
        Map<String, String> before = stateFiles(project).collectEntries { [(it): sha256(project.resolve(it).toFile().bytes)] }

        when:
        generateInto(project, 'shop', ['grails-quartz'])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Cannot create the project because the directory already has the state of a generated project in .grails'
        stateFiles(project).collectEntries { [(it): sha256(project.resolve(it).toFile().bytes)] } == before
        !project.resolve('build.gradle').toFile().getText('UTF-8').contains('quartz')
    }

    void "a zip archive of an application has its state"() {
        given:
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)

        when:
        generator.generate(ApplicationType.WEB, NameUtils.parse('com.acme.shop'), new Options(), OperatingSystem.LINUX, [],
                new ZipOutputHandler('shop', bytes), ConsoleOutput.NOOP)
        Path project = unzip(bytes.toByteArray()).resolve('shop')
        ProjectState state = ProjectState.read(project)
        state.verifyBaseline(project)

        then:
        state.manifest.name == 'shop'
        state.baseline.files*.path == applicationFiles(project)
    }

    void "the difference a feature makes shows the manifest and the lock, and not the baseline"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        StringBuilder diff = new StringBuilder()
        ConsoleOutput console = new ConsoleOutput() {
            @Override
            void out(String message) { diff << message << '\n' }
            @Override
            void err(String message) { }
            @Override
            void warning(String message) { }
            @Override
            boolean showStacktrace() { false }
            @Override
            boolean verbose() { false }
        }

        when:
        beanContext.getBean(FeatureDiffer).produceDiff(generator, NameUtils.parse('com.acme.shop'), ApplicationType.WEB, new Options(),
                OperatingSystem.LINUX, ['grails-quartz'], console)

        then:
        diff.contains('+++ .grails/project.yml')
        diff.contains('+- name: grails-quartz')
        diff.contains('+++ .grails/forge.lock.yml')
        diff.contains('+++ build.gradle')
        !diff.contains('.grails/baseline')
    }

    void "the generated .gitignore leaves out only what of .grails is of one machine"() {
        when:
        String ignored = generate('shop', []).resolve('.gitignore').toFile().getText('UTF-8')

        then:
        ['.grails/cache/', '.grails/transactions/', '.grails/run/', '.grails/history'].every { ignored.readLines().contains(it) }
        !ignored.readLines().any { it in ['.grails', '.grails/', '.grails/baseline', '.grails/baseline/', '.grails/project.yml', '.grails/forge.lock.yml'] }
    }

    void "a directory without state, or with a part of it, is refused"() {
        given:
        Path project = generate('shop', [])

        when:
        ProjectState.read(directory)

        then:
        ProjectStateException none = thrown()
        none.message == 'There is no state of a generated project here: .grails/project.yml is missing'
        !ProjectState.exists(directory)
        ProjectState.exists(project)

        when:
        Files.delete(project.resolve('.grails/forge.lock.yml'))
        ProjectState.read(project)

        then:
        ProjectStateException partial = thrown()
        partial.message == 'The state of the project is not complete: .grails/forge.lock.yml is missing'

        when:
        Files.delete(project.resolve('.grails/baseline/index.yml'))
        ProjectState.read(project)

        then:
        ProjectStateException more = thrown()
        more.message == 'The state of the project is not complete: .grails/forge.lock.yml and .grails/baseline/index.yml are missing'
    }

    @Unroll
    void "state is refused when #what"() {
        given:
        Path project = generate('shop', [])
        Path changed = project.resolve(file)
        Files.writeString(changed, change(changed.toFile().getText('UTF-8')))

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message.startsWith(message)
        e.message.readLines().size() == 1

        where:
        what | file | change | message
        'the manifest is of a format of a later version' | '.grails/project.yml' | { it.replace('format: 1', 'format: 2') } |
                '.grails/project.yml: format is 2, and this version of Grails reads the format 1'
        'the lock is of a format of a later version' | '.grails/forge.lock.yml' | { it.replace('format: 1', 'format: 7') } |
                '.grails/forge.lock.yml: format is 7, and this version of Grails reads the format 1'
        'the index is of a format of a later version' | '.grails/baseline/index.yml' | { it.replace('format: 1', 'format: 3') } |
                '.grails/baseline/index.yml: format is 3, and this version of Grails reads the format 1'
        'the manifest has no format' | '.grails/project.yml' | { it.replace('format: 1\n', '') } |
                '.grails/project.yml: format is missing'
        'the manifest was changed after the lock was written' | '.grails/project.yml' | { it.replace('name: shop', 'name: store') } |
                '.grails/forge.lock.yml does not belong to .grails/project.yml: one of them was changed without the other'
        'the manifest was changed only in its layout' | '.grails/project.yml' | { it + '\n' } |
                '.grails/forge.lock.yml does not belong to .grails/project.yml: one of them was changed without the other'
        'the index was changed after the lock was written' | '.grails/baseline/index.yml' | { it.replaceFirst('executable: false', 'executable: true') } |
                '.grails/baseline/index.yml is not the baseline .grails/forge.lock.yml was written with'
        'the manifest is not YAML' | '.grails/project.yml' | { 'format: [1' } |
                '.grails/project.yml is not YAML that can be read: while parsing a flow sequence'
        'the manifest is a list' | '.grails/project.yml' | { '- format\n' } |
                '.grails/project.yml must be a mapping of keys to values'
        'the manifest is empty' | '.grails/project.yml' | { ' \n' } |
                '.grails/project.yml is empty'
        'the manifest uses an anchor' | '.grails/project.yml' | { it.replace('name: shop', 'name: &a shop') } |
                '.grails/project.yml names a value with an anchor (&), which the state of a project does not use'
        'the manifest uses an alias' | '.grails/project.yml' | { it.replace('defaultPackage: com.acme', 'defaultPackage: *a') } |
                '.grails/project.yml repeats a value with an alias (*), which the state of a project does not use'
        'the manifest uses a tag' | '.grails/project.yml' | { it.replace('name: shop', 'name: !!java.net.URL shop') } |
                ".grails/project.yml gives a value the tag 'tag:yaml.org,2002:java.net.URL', and the state of a project does not use tags"
        'the manifest has a key twice' | '.grails/project.yml' | { it + 'name: store\n' } |
                '.grails/project.yml is not YAML that can be read: '
        'the manifest has a key this version does not read' | '.grails/project.yml' | { it + 'preset: acme\n' } |
                '.grails/project.yml: preset is not a key this version of Grails reads'
        'the manifest is nested deeper than it is written' | '.grails/project.yml' | { it.replace('parameters: {}', 'parameters: {a: {b: {c: {d: {e: {f: {g: h}}}}}}}') } |
                '.grails/project.yml has mappings and lists more than 6 deep'
        'the manifest names no kind of application there is' | '.grails/project.yml' | { it.replace('applicationType: web', 'applicationType: desktop') } |
                ".grails/project.yml: applicationType must be one of web, rest-api, web-plugin, plugin, but is 'desktop'"
        'the manifest has an option there is not' | '.grails/project.yml' | { it.replace('servlet: tomcat', 'servlet: resin') } |
                ".grails/project.yml: options.servlet must be one of tomcat, jetty, undertow, none, but is 'resin'"
        'the manifest has a version of Java there is not' | '.grails/project.yml' | { it.replace('jdk: 21', 'jdk: 8') } |
                '.grails/project.yml: options.jdk is 8, a version of Java this version of Grails does not generate for'
        'the manifest has a package that is none' | '.grails/project.yml' | { it.replace('defaultPackage: com.acme', 'defaultPackage: ../../etc') } |
                '.grails/project.yml: defaultPackage is not the name of a package'
        'the manifest has markup of the command line' | '.grails/project.yml' | { it.replace('name: shop', "name: '@|red x|@'") } |
                '.grails/project.yml: name must be one line of at most 255 characters, none of them a control character'
        'the manifest has a state of generation there is not' | '.grails/project.yml' | { it.replace('generationState: tracked', 'generationState: maybe') } |
                ".grails/project.yml: generationState must be one of tracked, untracked, but is 'maybe'"
        'a requested feature has no name' | '.grails/project.yml' | { it.replace('requestedFeatures: []', 'requestedFeatures:\n- version: 1.0') } |
                '.grails/project.yml: requestedFeatures[0].name is missing'
        'the features of the lock are no list' | '.grails/forge.lock.yml' | { it.replace('features:\n', 'features: none\nother:\n') } |
                '.grails/forge.lock.yml: features must be a list'
        'the lock has a feature twice' | '.grails/forge.lock.yml' | { it.replace('features:\n', 'features:\n- name: spock\n  source: framework\n') } |
                '.grails/forge.lock.yml: features has the feature spock more than once'
        'the lock has a feature of the framework with a digest' | '.grails/forge.lock.yml' | { it.replace('features:\n', "features:\n- name: other\n  source: framework\n  sha256: ${'0' * 64}\n") } |
                '.grails/forge.lock.yml: features[0].source is framework, and only a feature of a feature pack that is not part of Forge has an origin, coordinates, a version or a digest'
        'the lock has a digest that is none' | '.grails/forge.lock.yml' | { it.replaceFirst('sha256: [0-9a-f]{64}', 'sha256: abc') } |
                ".grails/forge.lock.yml: manifest.sha256 must be a SHA-256 digest in hexadecimal, but is 'abc'"
        'the index names a file outside the project' | '.grails/baseline/index.yml' | { it.replaceFirst('path: [^\\n]+', 'path: ../build.gradle') } |
                ".grails/baseline/index.yml: files[0].path must be the path of a file inside the project, but is '../build.gradle'"
        'the index names a file by an absolute path' | '.grails/baseline/index.yml' | { it.replaceFirst('path: [^\\n]+', 'path: /etc/passwd') } |
                ".grails/baseline/index.yml: files[0].path must be the path of a file inside the project, but is '/etc/passwd'"
        'the index names a file of the state' | '.grails/baseline/index.yml' | { it.replaceFirst('path: [^\\n]+', 'path: .grails/project.yml') } |
                ".grails/baseline/index.yml: files[0].path must be the path of a file inside the project, but is '.grails/project.yml'"
        'the index names a file of the repository' | '.grails/baseline/index.yml' | { it.replaceFirst('path: [^\\n]+', 'path: .git/hooks/pre-commit') } |
                ".grails/baseline/index.yml: files[0].path must be the path of a file inside the project, but is '.git/hooks/pre-commit'"
        'the index names a file twice' | '.grails/baseline/index.yml' | { it.replaceFirst('path: [^\\n]+', 'path: GRADLEW') } |
                '.grails/baseline/index.yml: the file gradlew is listed more than once'
        'the index has a kind of file there is not' | '.grails/baseline/index.yml' | { it.replaceFirst('kind: text', 'kind: link') } |
                ".grails/baseline/index.yml: files[0].kind must be one of text, binary, but is 'link'"
        'the index does not say whether a file can be executed' | '.grails/baseline/index.yml' | { it.replaceFirst('executable: false', 'executable: yes') } |
                ".grails/baseline/index.yml: files[0].executable must be one of true, false, but is 'yes'"
    }

    void "a file of the state that is larger than it can be is not read"() {
        given:
        Path project = generate('shop', [])
        Files.writeString(project.resolve('.grails/project.yml'), '#' + ('x' * ProjectState.MAX_MANIFEST) + '\n')

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == ".grails/project.yml is larger than it can be: it has ${ProjectState.MAX_MANIFEST + 2} bytes, and can have ${ProjectState.MAX_MANIFEST}"
    }

    void "a file of the state that is not text is refused"() {
        given:
        Path project = generate('shop', [])
        Files.write(project.resolve('.grails/project.yml'), [0xC3, 0x28] as byte[])

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == '.grails/project.yml is not text in UTF-8'
    }

    @IgnoreIf({ os.windows })
    void "the state is not read through a link"() {
        given:
        Path project = generate('shop', [])
        Path elsewhere = Files.createDirectory(directory.resolve('elsewhere'))

        when: 'the manifest is a link to a file'
        Path manifest = project.resolve('.grails/project.yml')
        Files.move(manifest, elsewhere.resolve('project.yml'))
        Files.createSymbolicLink(manifest, elsewhere.resolve('project.yml'))
        ProjectState.read(project)

        then:
        ProjectStateException file = thrown()
        file.message == '.grails/project.yml is not a file'

        when: 'the directory of the state is a link to a directory'
        Files.delete(manifest)
        Files.move(elsewhere.resolve('project.yml'), manifest)
        Files.move(project.resolve('.grails'), elsewhere.resolve('state'))
        Files.createSymbolicLink(project.resolve('.grails'), elsewhere.resolve('state'))
        ProjectState.read(project)

        then:
        ProjectStateException link = thrown()
        link.message == '.grails is not a directory of the project'
    }

    void "a baseline that is not what its index says is refused"() {
        given:
        Path project = generate('shop', [])
        ProjectState state = ProjectState.read(project)
        Path copy = project.resolve('.grails/baseline/files/build.gradle')
        byte[] written = copy.toFile().bytes

        when: 'a file of the baseline is changed'
        Files.writeString(copy, '// changed\n')
        state.verifyBaseline(project)

        then:
        ProjectStateException changed = thrown()
        changed.message == '.grails/baseline/files/build.gradle is not what the generator wrote: its content does not have the digest of the index'

        when: 'a file of the baseline is missing'
        Files.delete(copy)
        state.verifyBaseline(project)

        then:
        ProjectStateException missing = thrown()
        missing.message == '.grails/baseline/files/build.gradle is missing from the baseline'

        when: 'the baseline has a file its index has not'
        Files.write(copy, written)
        Files.writeString(project.resolve('.grails/baseline/files/grails-app/extra.txt'), 'x')
        state.verifyBaseline(project)

        then:
        ProjectStateException extra = thrown()
        extra.message == '.grails/baseline/files/grails-app/extra.txt is not a file of the baseline index'

        when: 'the application itself is changed'
        Files.delete(project.resolve('.grails/baseline/files/grails-app/extra.txt'))
        Files.writeString(project.resolve('build.gradle'), '// edited by its owner\n')
        state.verifyBaseline(project)

        then:
        noExceptionThrown()
    }

    private List<String> appliedFeatures(ApplicationType type, List<String> features, Options options = new Options(),
                                         OperatingSystem operatingSystem = OperatingSystem.LINUX) {
        return beanContext.getBean(ProjectGenerator)
                .createGeneratorContext(type, NameUtils.parse('com.acme.shop'), options, operatingSystem, features, ConsoleOutput.NOOP)
                .features.toList()
    }

    private Path generate(String name, List<String> features, ApplicationType type = ApplicationType.WEB, String into = 'app',
                          Options options = new Options(), OperatingSystem operatingSystem = OperatingSystem.LINUX) {
        Path project = Files.createDirectory(directory.resolve(into))
        generateInto(project, name, features, type, options, operatingSystem)
        return project
    }

    private void generateInto(Path project, String name, List<String> features, ApplicationType type = ApplicationType.WEB,
                              Options options = new Options(), OperatingSystem operatingSystem = OperatingSystem.LINUX) {
        beanContext.getBean(ProjectGenerator).generate(type, NameUtils.parse("com.acme.${name}"), options, operatingSystem,
                features, new FileSystemOutputHandler(project.toFile(), ConsoleOutput.NOOP), ConsoleOutput.NOOP)
    }

    private Pack pack(String name, String to = 'grails-app/conf/audit.txt') {
        Path packDirectory = Files.createDirectories(directory.resolve('packs').resolve(name))
        Files.writeString(packDirectory.resolve('feature.yml'), 'format: 1\nkind: feature\ntemplateApi: 1\n' +
                "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
                "name: ${name}\nfiles:\n  - from: audit.txt\n    to: ${to}\n")
        Files.createDirectories(packDirectory.resolve('templates'))
        Files.writeString(packDirectory.resolve('templates/audit.txt'), 'kept as it is\n')
        return new PackLoader().fromDirectory(packDirectory)
    }

    private static List<String> applicationFiles(Path project) {
        return files(project).findAll { !ProjectState.isStatePath(it) }
    }

    private static List<String> stateFiles(Path project) {
        return files(project).findAll { ProjectState.isStatePath(it) }
    }

    private static List<String> files(Path project) {
        return Files.walk(project).withCloseable { stream ->
            stream.filter { Files.isRegularFile(it) }.collect { project.relativize(it).join('/') }.toSorted()
        }
    }

    private Path unzip(byte[] archive) {
        Path target = Files.createDirectory(directory.resolve('unzipped'))
        new ZipInputStream(new ByteArrayInputStream(archive)).withCloseable { ZipInputStream zip ->
            for (ZipEntry entry = zip.nextEntry; entry != null; entry = zip.nextEntry) {
                Path file = target.resolve(entry.name)
                if (entry.directory) {
                    Files.createDirectories(file)
                } else {
                    Files.createDirectories(file.parent)
                    Files.write(file, zip.readAllBytes())
                }
            }
        }
        return target
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
    }
}

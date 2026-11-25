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

import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import com.github.difflib.UnifiedDiffUtils
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackLoader
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.grails.forge.ApplicationContextSpec
import org.apache.grails.forge.core.state.ProjectLock.LockedRepository
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.diff.FeatureDiffer
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputFailedException
import org.grails.forge.io.OutputHandler
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.io.ZipOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.template.Template
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
        state.lock.options == state.manifest.options
        state.lock.featureNames == appliedFeatures(ApplicationType.WEB, ['grails-web-console', 'gradle-settings-file'])
        state.lock.featureNames.containsAll(['gradle-settings-file', 'grails-web-console', 'spock', 'gorm-hibernate5'])
        state.lock.features.find { it.name == 'grails-web-console' }.source == LockedFeature.BUNDLED_PACK
        state.lock.features.find { it.name == 'gradle' }.source == LockedFeature.FRAMEWORK
        state.lock.features.every { it.origin == null && it.coordinates == null && it.version == null && it.sha256 == null }

        and: 'the lock names the manifest and the index of the baseline it was written with'
        state.lock.manifestSha256 == sha256(project.resolve('.grails/project.yml').toFile().bytes)
        state.lock.baselineIndexSha256 == sha256(project.resolve('.grails/baseline/index.yml').toFile().bytes)

        and: 'the baseline is every file of the application, and nothing of the state; the record of earlier versions is not written'
        state.baseline.files*.path == applicationFiles(project)
        !project.resolve('grails-forge-cli.yml').toFile().exists()
        state.baseline.files.every { BaselineFile file ->
            file.sha256 == sha256(project.resolve(file.path).toFile().bytes) &&
                    project.resolve('.grails/baseline/files').resolve(BaselineIndex.copyPath(file.path)).toFile().bytes == project.resolve(file.path).toFile().bytes &&
                    state.isAsGenerated(project, file.path)
        }

        and: 'no copy is a file Git reads'
        state.baseline.files*.path.contains('.gitignore')
        project.resolve('.grails/baseline/files/.gitignore.baseline').toFile().file
        !project.resolve('.grails/baseline/files/.gitignore').toFile().exists()

        and: 'Git is told to keep the bytes of the state, which the digests are of'
        project.resolve('.grails/.gitattributes').toFile().getText('UTF-8') == '* -text\n'
        project.resolve('.grails/.gitignore').toFile().getText('UTF-8') == '!/project.yml\n!/forge.lock.yml\n!/.gitattributes\n!/.gitignore\n!/baseline/\n!/baseline/**\n' +
                '/cache/\n/history\n/run/\n/transactions/\n'
        stateFiles(project) - state.baseline.files.collect { '.grails/baseline/files/' + BaselineIndex.copyPath(it.path) } ==
                ['.grails/.gitattributes', '.grails/.gitignore', '.grails/baseline/index.yml', '.grails/forge.lock.yml', '.grails/project.yml']

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
        state.lock.featureNames == appliedFeatures(type, [], options, OperatingSystem.MACOS_ARCH64)
        state.baseline.files*.path == applicationFiles(project)

        where:
        type                       | gorm                | servlet              | reloading                   | jdk
        ApplicationType.WEB        | GormImpl.HIBERNATE7 | ServletImpl.JETTY    | DevelopmentReloading.NONE   | JdkVersion.JDK_25
        ApplicationType.REST_API   | GormImpl.MONGODB    | ServletImpl.UNDERTOW | DevelopmentReloading.JREBEL | JdkVersion.JDK_21
        ApplicationType.PLUGIN     | GormImpl.HIBERNATE5 | ServletImpl.NONE     | DevelopmentReloading.NONE   | JdkVersion.JDK_21
        ApplicationType.WEB_PLUGIN | GormImpl.NEO4J      | ServletImpl.TOMCAT   | DevelopmentReloading.DEVTOOLS | JdkVersion.JDK_21
        ApplicationType.REST_API_PLUGIN | GormImpl.HIBERNATE7 | ServletImpl.JETTY | DevelopmentReloading.NONE     | JdkVersion.JDK_25
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

        and: 'it is locked by the digest of its content, which is that of the pack wherever it is'
        locked.contentSha256 ==~ /[0-9a-f]{64}/
        locked.contentSha256 == pack.contentSha256
        target.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8').contains("- name: acme-audit\n  source: pack\n  contentSha256: ${pack.contentSha256}\n")
    }

    void "the digest of the content of a pack is that of its descriptor and the files it names, and of nothing else"() {
        given:
        Pack pack = pack('acme-audit')
        Path copy = Files.createDirectories(directory.resolve('elsewhere/acme-audit'))
        Path original = directory.resolve('packs/acme-audit')
        Files.copy(original.resolve('feature.yml'), copy.resolve('feature.yml'))
        Files.createDirectories(copy.resolve('templates'))
        Files.copy(original.resolve('templates/audit.txt'), copy.resolve('templates/audit.txt'))
        // a file the descriptor does not name is no part of the pack
        Files.writeString(copy.resolve('templates/notes.txt'), 'not of the pack')

        expect: 'a copy elsewhere is the same pack'
        new PackLoader().fromDirectory(copy).contentSha256 == pack.contentSha256

        when: 'a line ending of a template is another'
        Files.writeString(copy.resolve('templates/audit.txt'), 'kept as it is\r\n')

        then:
        new PackLoader().fromDirectory(copy).contentSha256 != pack.contentSha256

        when: 'the descriptor says the same in other bytes'
        Files.copy(original.resolve('templates/audit.txt'), copy.resolve('templates/audit.txt'), StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(copy.resolve('feature.yml'), Files.readString(original.resolve('feature.yml')) + '\n')

        then:
        new PackLoader().fromDirectory(copy).contentSha256 != pack.contentSha256
    }

    @Unroll
    void "a lock with the digest of the content of a pack #what is refused"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lock, sealed(lock.toFile().getText('UTF-8').replace('features:\n', 'features:\n- name: acme-audit\n' + entry)))

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message.contains(message)

        where:
        what                           | entry                                                                                                        || message
        'of the framework'             | "  source: framework\n  contentSha256: ${'ab' * 32}\n"                                                     || 'contentSha256'
        'and the jar of a repository'  | "  source: pack\n  contentSha256: ${'ab' * 32}\n  coordinates: com.acme:audit\n  version: 1.0\n  sha256: ${'ab' * 32}\n" || 'contentSha256'
        'that is no digest'            | "  source: pack\n  contentSha256: abc\n"                                                                  || 'contentSha256'
    }

    void "a lock can say which artifact the feature of a feature pack came from"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lock, sealed(lock.toFile().getText('UTF-8').replace('features:\n', 'features:\n- name: acme-audit\n  source: pack\n' +
                "  origin: HTTPS://repo.acme.example/maven/\n  coordinates: com.acme:acme-audit-grails\n  version: 2.3.0\n  sha256: ${'ab' * 32}\n")))

        when:
        LockedFeature locked = ProjectState.read(project).lock.features.first()

        then:
        locked.name == 'acme-audit'
        locked.source == LockedFeature.PACK
        locked.origin == 'HTTPS://repo.acme.example/maven/'
        locked.coordinates == 'com.acme:acme-audit-grails'
        locked.version == '2.3.0'
        locked.sha256 == 'ab' * 32
    }

    void "a lock says which catalog gave the coordinates of a feature pack, and a lock without one is read as before"() {
        given:
        String digest = 'cd' * 32
        String catalogDigest = 'ef' * 32
        Pack named = pack('acme-audit').withArtifact(new PackArtifact('acme-audit', 'com.acme:acme-audit-grails:2.3.0',
                'https://repo.acme.example/maven/', 'acme-audit-grails-2.3.0.jar', digest, 'sha256', true)
                .withCatalog('https://catalog.acme.example/index.json', catalogDigest))
        Pack ofAFile = pack('acme-base', 'grails-app/conf/base.txt').withArtifact(new PackArtifact('acme-base', 'com.acme:acme-base:1.0',
                'https://repo.acme.example/maven/', 'acme-base-1.0.jar', digest, null, true)
                .withCatalog('file:///home/zzsomeone/catalog.json', catalogDigest))
        Pack given = pack('acme-given', 'grails-app/conf/given.txt').withArtifact(new PackArtifact('acme-given', 'com.acme:acme-given:1.0',
                'https://repo.acme.example/maven/', 'acme-given-1.0.jar', digest, null, false))
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('com.acme.shop')
        Path target = Files.createDirectory(directory.resolve('app'))

        when:
        generator.generate(ApplicationType.WEB, project, new FileSystemOutputHandler(target.toFile(), ConsoleOutput.NOOP),
                generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                        ['acme-audit', 'acme-base', 'acme-given'], ConsoleOutput.NOOP, [named, ofAFile, given]))
        ProjectState state = ProjectState.read(target)
        state.verifyBaseline(target)

        then: 'the catalog of an https address is named, with the digest of what was read of it'
        with(state.lock.features.find { it.name == 'acme-audit' }) {
            coordinates == 'com.acme:acme-audit-grails'
            version == '2.3.0'
            sha256 == digest
            catalog == 'https://catalog.acme.example/index.json'
            catalogSha256 == catalogDigest
        }
        target.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8').contains(
                "  sha256: ${digest}\n  catalog: https://catalog.acme.example/index.json\n  catalogSha256: ${catalogDigest}\n")

        and: 'of a catalog that is a file of this machine only the digest is said'
        with(state.lock.features.find { it.name == 'acme-base' }) {
            catalog == null
            catalogSha256 == catalogDigest
        }
        !target.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8').contains('zzsomeone')

        and: 'a feature whose coordinates were given has neither'
        with(state.lock.features.find { it.name == 'acme-given' }) {
            catalog == null
            catalogSha256 == null
        }

        and: 'the manifest says which plugin and version the name was'
        with(state.manifest.requestedFeatures.find { it.name == 'acme-audit' }) {
            coordinates == 'com.acme:acme-audit-grails'
            version == '2.3.0'
        }
    }

    @Unroll
    void "a lock whose feature names a catalog #what is refused"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lock, sealed(lock.toFile().getText('UTF-8').replace('features:\n', 'features:\n- name: acme-audit\n' + entry)))

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message.contains(message)

        where:
        what                             | entry                                                                                                || message
        'without its digest'             | "  source: pack\n  coordinates: com.acme:audit\n  version: 1.0\n  sha256: ${'ab' * 32}\n  catalog: https://c.example/i.json\n" || 'catalogSha256'
        'without the artifact it gave'   | "  source: pack\n  catalogSha256: ${'ab' * 32}\n"                                                 || 'catalogSha256'
        'with a password in its address' | "  source: pack\n  coordinates: com.acme:audit\n  version: 1.0\n  sha256: ${'ab' * 32}\n  catalog: https://u:p@c.example/i.json\n  catalogSha256: ${'ab' * 32}\n" || 'catalog'
        'that is a file'                 | "  source: pack\n  coordinates: com.acme:audit\n  version: 1.0\n  sha256: ${'ab' * 32}\n  catalog: file:///home/me/i.json\n  catalogSha256: ${'ab' * 32}\n" || 'catalog'
        'with a digest that is none'     | "  source: pack\n  coordinates: com.acme:audit\n  version: 1.0\n  sha256: ${'ab' * 32}\n  catalogSha256: abc\n" || 'catalogSha256'
        'and is of the framework'        | "  source: framework\n  catalogSha256: ${'ab' * 32}\n"                                            || 'catalogSha256'
    }

    @Unroll
    void "a lock whose feature of a feature pack came from an address #what is refused, and the address is not repeated"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lock, sealed(lock.toFile().getText('UTF-8').replace('features:\n', 'features:\n- name: acme-audit\n  source: pack\n' +
                "  origin: ${origin}\n  coordinates: com.acme:acme-audit-grails\n  version: 2.3.0\n  sha256: ${'ab' * 32}\n")))

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == '.grails/forge.lock.yml: features[0].origin must be the https address of a repository, without a name, a password, a query or a fragment'

        when: 'what shows the record does not read it either'
        ProjectState.readSelection(project)

        then:
        ProjectStateException selection = thrown()
        selection.message == e.message

        where:
        what                         | origin
        'that is not https'          | 'http://repo.acme.example/maven/'
        'with a name and a password' | 'https://zzsomeone:zzsecret@repo.acme.example/maven/'
        'with a query'               | 'https://repo.acme.example/maven/?token=zzsecret'
        'with a fragment'            | 'https://repo.acme.example/maven/#zzsecret'
        'of a file of the machine'   | 'file:///home/zzsomeone/.m2/repository'
        'that is a directory'        | '/home/zzsomeone/repository'
        'without a host'             | 'https:///maven'
    }

    void "the parameters of a manifest and of a lock are read as they are written, each a name with one line of text, and in their order"() {
        given: 'this version writes none: a manifest and a lock with some, as a tool that wrote them would have them'
        Path project = generate('shop', [])
        Path manifest = project.resolve('.grails/project.yml')
        Path lock = project.resolve('.grails/forge.lock.yml')

        expect:
        manifest.toFile().getText('UTF-8').endsWith('parameters: {}\n')
        lock.toFile().getText('UTF-8').contains('\nparameters: {}\n')
        ProjectState.read(project).manifest.parameters == [:]
        ProjectState.read(project).lock.parameters == [:]

        when:
        Files.writeString(manifest, manifest.toFile().getText('UTF-8').replace('parameters: {}\n', "parameters:\n  region: eu\n  tier: '2'\n  a.b: c d\n"))
        rebind(project)
        Files.writeString(lock, sealed(lock.toFile().getText('UTF-8').replace('\nparameters: {}\n', '\nparameters:\n  seed: 0a1b\n')))
        ProjectState state = ProjectState.read(project)

        then: 'a number is the text it was written as, and neither file is held to the parameters of the other'
        state.manifest.parameters == [region: 'eu', tier: '2', 'a.b': 'c d']
        state.manifest.parameters.keySet().toList() == ['region', 'tier', 'a.b']
        state.lock.parameters == [seed: '0a1b']
        ProjectState.readSelection(project).manifest.parameters == state.manifest.parameters

        when:
        state.manifest.parameters.put('more', 'x')

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll
    void "parameters are refused when #what"() {
        given:
        Path project = generate('shop', [])
        Path manifest = project.resolve('.grails/project.yml')
        Path lock = project.resolve('.grails/forge.lock.yml')
        if (file == '.grails/project.yml') {
            Files.writeString(manifest, manifest.toFile().getText('UTF-8').replace('parameters: {}\n', 'parameters:' + parameters))
            rebind(project)
        } else {
            Files.writeString(lock, sealed(lock.toFile().getText('UTF-8').replace('\nparameters: {}\n', '\nparameters:' + parameters)))
        }

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == "${file}: ${message}"

        where:
        what                                     | file                     | parameters                                                    || message
        'a value of the manifest is a list'      | '.grails/project.yml'    | '\n  region: [eu, us]\n'                                      || 'parameters.region must be one line of at most 255 characters, none of them a control character or one that is not shown'
        'a value of the manifest is a mapping'   | '.grails/project.yml'    | '\n  region:\n    name: eu\n'                                 || 'parameters.region must be one line of at most 255 characters, none of them a control character or one that is not shown'
        'a value of the manifest is empty'       | '.grails/project.yml'    | '\n  region:\n'                                               || 'parameters.region must be one line of at most 255 characters, none of them a control character or one that is not shown'
        'a value of the manifest has two lines'  | '.grails/project.yml'    | '\n  region: "eu\\nus"\n'                                     || 'parameters.region must be one line of at most 255 characters, none of them a control character or one that is not shown'
        'a name of the manifest is markup'       | '.grails/project.yml'    | "\n  '@|red x|@': eu\n"                                        || 'parameters has a key that is not one line of text'
        'the manifest has no mapping of them'    | '.grails/project.yml'    | ' none\n'                                                     || 'parameters must be a mapping'
        'the manifest has more than it can have' | '.grails/project.yml'    | '\n' + (1..201).collect { "  p${it}: v\n" }.join('')          || 'parameters has 201 entries, and can have 200'
        'a value of the lock is a list'          | '.grails/forge.lock.yml' | '\n  seed: [1]\n'                                             || 'parameters.seed must be one line of at most 255 characters, none of them a control character or one that is not shown'
        'the lock has more than it can have'     | '.grails/forge.lock.yml' | '\n' + (1..201).collect { "  p${it}: v\n" }.join('')          || 'parameters has 201 entries, and can have 200'
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

    void "a feature cannot write the record of an earlier version (#path)"() {
        given:
        Pack pack = pack('acme-audit', path)
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('com.acme.shop')
        MapOutputHandler output = new MapOutputHandler()

        when:
        generator.generate(ApplicationType.WEB, project, output, generator.createGeneratorContext(ApplicationType.WEB, project,
                new Options(), OperatingSystem.LINUX, ['acme-audit'], ConsoleOutput.NOOP, [pack]))

        then:
        IllegalArgumentException e = thrown()
        e.message == "The features write the file ${path}, which the command line reads as the record of a project of an earlier version"
        output.project.isEmpty()

        where:
        path << ['grails-forge-cli.yml', 'Grails-Forge-CLI.yml', 'grails-cli.yml']
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
        !state.isAsGenerated(project, 'build.gradle')
        state.isAsGenerated(project, 'gradlew')
    }

    void "an application is not generated over the state of another"() {
        given:
        Path project = generate('shop', [])
        Map<String, String> before = stateFiles(project).collectEntries { [(it): sha256(project.resolve(it).toFile().bytes)] }

        when:
        generateInto(project, 'shop', ['grails-quartz'])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Cannot create the project because the directory already has the state of a generated project (.grails/project.yml); ' +
                'to generate the project anew, remove the directory .grails first, which discards the record of how it was generated'
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

    void "the difference a feature makes is of the application, and can be applied to a project without its state"() {
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
        diff.contains('+++ build.gradle')
        !diff.readLines().any { it.startsWith('+++ .grails') || it.startsWith('--- .grails') }

        when: 'the difference is applied to a project that was generated without the feature'
        Path project = generate('shop', [])
        List<String> changed = apply(project, diff.toString())
        ProjectState state = ProjectState.read(project)
        state.verifyBaseline(project)

        then: 'the state of the project reads, and has the files that were changed as changed'
        changed.contains('build.gradle')
        project.resolve('build.gradle').toFile().getText('UTF-8').contains('grails-quartz')
        !state.isAsGenerated(project, 'build.gradle')
        state.isAsGenerated(project, 'gradlew')
        !state.lock.featureNames.contains('grails-quartz')
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
        partial.message == 'The state of the project is not complete: .grails/forge.lock.yml is missing; the baseline is there, as it is when the file was ' +
                'not committed with the project: see whether a rule of a .gitignore, also a global one, leaves it out, and commit it from where the project was generated'

        when:
        Files.delete(project.resolve('.grails/baseline/index.yml'))
        ProjectState.read(project)

        then:
        ProjectStateException more = thrown()
        more.message == 'The state of the project is not complete: .grails/forge.lock.yml and .grails/baseline/index.yml are missing; restore them from version control'

        when: 'the manifest and the lock are missing and the baseline is all there, as in a clone of a project they were not committed with'
        Path cloned = generate('shop', [], ApplicationType.WEB, 'cloned')
        Files.delete(cloned.resolve('.grails/project.yml'))
        Files.delete(cloned.resolve('.grails/forge.lock.yml'))
        ProjectState.read(cloned)

        then:
        ProjectStateException ignored = thrown()
        ignored.message == 'The state of the project is not complete: .grails/project.yml and .grails/forge.lock.yml are missing; the baseline is there, as it is when ' +
                'the files were not committed with the project: see whether a rule of a .gitignore, also a global one, leaves them out, and commit them from where the project was generated'
        !ProjectState.exists(cloned)

        when: 'the manifest, which is written last, is missing, and so is the index that is written before it'
        Path unfinished = generate('shop', [], ApplicationType.WEB, 'unfinished')
        Files.delete(unfinished.resolve('.grails/project.yml'))
        Files.delete(unfinished.resolve('.grails/baseline/index.yml'))
        ProjectState.read(unfinished)

        then:
        ProjectStateException interrupted = thrown()
        interrupted.message == 'The state of the project is not complete: .grails/project.yml and .grails/baseline/index.yml are missing; the project was not generated to the end, ' +
                'and creating it again in this directory with --inplace completes it'
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
        'the manifest has a key this version does not read' | '.grails/project.yml' | { it + 'profile: acme\n' } |
                '.grails/project.yml: profile is not a key this version of Grails reads'
        'the manifest names a preset it did not ask for' | '.grails/project.yml' | { it + 'preset: acme\n' } |
                '.grails/project.yml: preset is acme, which is not among the requestedFeatures'
        'the manifest is nested deeper than it is written' | '.grails/project.yml' | { it.replace('parameters: {}', 'parameters: {a: {b: {c: {d: {e: {f: {g: h}}}}}}}') } |
                '.grails/project.yml has mappings and lists more than 6 deep'
        'the manifest names no kind of application there is' | '.grails/project.yml' | { it.replace('applicationType: web', 'applicationType: desktop') } |
                ".grails/project.yml: applicationType must be one of web, rest-api, web-plugin, plugin, rest-api-plugin, but is 'desktop'"
        'the manifest has an option there is not' | '.grails/project.yml' | { it.replace('servlet: tomcat', 'servlet: resin') } |
                ".grails/project.yml: options.servlet must be one of tomcat, jetty, undertow, none, but is 'resin'"
        'the manifest has a version of Java there is not' | '.grails/project.yml' | { it.replace('jdk: 21', 'jdk: 8') } |
                '.grails/project.yml: options.jdk is 8, a version of Java this version of Grails does not generate for'
        'the manifest has a package that is none' | '.grails/project.yml' | { it.replace('defaultPackage: com.acme', 'defaultPackage: ../../etc') } |
                '.grails/project.yml: defaultPackage is not a package an application can be created with'
        'the manifest has markup of the command line' | '.grails/project.yml' | { it.replace('name: shop', "name: '@|red x|@'") } |
                '.grails/project.yml: name must be one line of at most 255 characters, none of them a control character or one that is not shown'
        'the manifest has a state of generation there is not' | '.grails/project.yml' | { it.replace('generationState: tracked', 'generationState: maybe') } |
                ".grails/project.yml: generationState must be one of tracked, untracked, adopted, but is 'maybe'"
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
        Files.writeString(project.resolve('.grails/project.yml'), '#' + ('x' * StateLimits.MAX_MANIFEST) + '\n')

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == ".grails/project.yml is larger than it can be: it has ${StateLimits.MAX_MANIFEST + 2} bytes, and can have ${StateLimits.MAX_MANIFEST}"
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

    void "a change to the lock alone is seen, by the digest the lock ends with"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        String written = lock.toFile().getText('UTF-8')

        expect:
        written.endsWith("seal:\n  sha256: ${sha256(written.substring(0, written.lastIndexOf('seal:\n')).getBytes('UTF-8'))}\n")

        when: 'a feature is added to the lock by hand, which contradicts neither of the other files'
        Files.writeString(lock, written.replace('features:\n', 'features:\n- name: grails-quartz\n  source: framework\n'))
        ProjectState.read(project)

        then:
        ProjectStateException feature = thrown()
        feature.message == '.grails/forge.lock.yml was changed after it was written: it does not have the digest it ends with'

        when: 'the version of the generator is changed'
        Files.writeString(lock, written.replace("name: grails-forge\n  version: ${VersionInfo.version}\n", 'name: grails-forge\n  version: 1.2.3\n'))
        ProjectState.readSelection(project)

        then:
        ProjectStateException version = thrown()
        version.message == '.grails/forge.lock.yml was changed after it was written: it does not have the digest it ends with'

        when: 'something comes after the seal'
        Files.writeString(lock, written + 'lineEndings: preserve\n')
        ProjectState.read(project)

        then:
        ProjectStateException after = thrown()
        after.message.startsWith('.grails/forge.lock.yml is not YAML that can be read: ')

        when:
        Files.writeString(lock, written)

        then:
        ProjectState.read(project).lock.generatorVersion == VersionInfo.version
    }

    @Unroll
    void "a lock that is sealed and says other than the manifest is refused: #what"() {
        given:
        Path project = generate('shop', ['grails-quartz'])
        Path manifest = project.resolve('.grails/project.yml')
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(manifest, inManifest(manifest.toFile().getText('UTF-8')))
        rebind(project)
        Files.writeString(lock, sealed(inLock(lock.toFile().getText('UTF-8'))))

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == '.grails/forge.lock.yml does not belong to .grails/project.yml: ' + message

        when:
        ProjectState.readSelection(project)

        then:
        ProjectStateException selection = thrown()
        selection.message == e.message

        where:
        what | inManifest | inLock | message
        'its options are others' | { it } | { it.replace('data: hibernate5', 'data: mongodb') } | 'the options of the two are not the same'
        'it is for another version of Java' | { it } | { it.replace('jdk: 21', 'jdk: 25') } | 'the options of the two are not the same'
        'it has not a feature that was asked for' | { it } | { it.replace('- name: grails-quartz\n  source: bundled-pack\n', '') } |
                'the feature grails-quartz was asked for and is not among the features of the lock'
        'it has a feature that was left out' | { it.replace('excludedFeatures: []', 'excludedFeatures:\n- name: spock') } | { it } |
                'the feature spock was left out and is among the features of the lock'
        'its feature is of another artifact than was asked for' |
                { it.replace('- name: grails-quartz\n', '- name: grails-quartz\n  coordinates: com.acme:quartz\n  version: 1.0\n') } | { it } |
                'the feature grails-quartz is not of the artifact that was asked for'
        'it is for another kind of application' | { it } | { it.replace('applicationType: web', 'applicationType: plugin') } |
                'one of them was changed without the other'
    }

    void "what the application is and has is read without the index of the baseline"() {
        given:
        Path project = generate('shop', ['grails-quartz'])
        Path index = project.resolve('.grails/baseline/index.yml')

        when: 'the index is not what the lock was written with, which reading everything refuses'
        Files.writeString(index, 'format: 1\nfiles: []\n')
        ProjectState selection = ProjectState.readSelection(project)

        then:
        selection.manifest.name == 'shop'
        selection.lock.featureNames.contains('grails-quartz')

        when:
        selection.baseline

        then:
        thrown(IllegalStateException)

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == '.grails/baseline/index.yml is not the baseline .grails/forge.lock.yml was written with'

        when: 'the index is not there'
        Files.delete(index)
        ProjectState.readSelection(project)

        then:
        ProjectStateException missing = thrown()
        missing.message == 'The state of the project is not complete: .grails/baseline/index.yml is missing; restore it from version control'
    }

    @Unroll
    void "a file of the state that is not as Forge writes one is refused before it is parsed: #what"() {
        given:
        Path project = generate('shop', [])
        Files.writeString(project.resolve(file), content())

        when:
        long start = System.nanoTime()
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == message
        System.nanoTime() - start < 30_000_000_000L

        where:
        what | file | content | message
        'an index of empty entries' | '.grails/baseline/index.yml' | { 'format: 1\nfiles:\n' + ('- {}\n' * 1_600_000) } |
                ".grails/baseline/index.yml has more than ${StateLimits.INDEX_LINES} lines"
        'an index that is one comment' | '.grails/baseline/index.yml' | { 'format: 1\nfiles: []\n#' + ('c' * (StateLimits.MAX_INDEX - 40)) + '\n' } |
                ".grails/baseline/index.yml has a line of more than ${StateLimits.MAX_LINE} characters"
        'an index that is one value' | '.grails/baseline/index.yml' | { 'format: 1\nfiles: []\nx: "' + ('c' * (StateLimits.MAX_INDEX - 40)) + '"\n' } |
                ".grails/baseline/index.yml has a line of more than ${StateLimits.MAX_LINE} characters"
        'an index that is spaces' | '.grails/baseline/index.yml' | { 'format: 1\nfiles: []\n' + (' ' * (StateLimits.MAX_INDEX - 40)) + '\n' } |
                ".grails/baseline/index.yml has a line of more than ${StateLimits.MAX_LINE} characters"
        'an index that is empty lines' | '.grails/baseline/index.yml' | { 'format: 1\nfiles: []\n' + ('\n' * (StateLimits.MAX_INDEX - 40)) } |
                ".grails/baseline/index.yml has more than ${StateLimits.INDEX_LINES} lines"
        'an index of lists in few lines' | '.grails/baseline/index.yml' | { 'format: 1\nfiles:\n' + (('- [' + ('[],' * 1300) + '[]]\n') * 2000) } |
                '.grails/baseline/index.yml has more mappings, lists and values than the state of a project has'
        'an index that is one value of many lines' | '.grails/baseline/index.yml' | { 'format: 1\nfiles: []\nx: "' + (('c' * 100 + '\n ') * 78000) + '"\n' } |
                ".grails/baseline/index.yml has a value of more than ${StateLimits.MAX_LINE} characters"
        'a manifest of empty entries' | '.grails/project.yml' | { 'format: 1\nrequestedFeatures:\n' + ('- {}\n' * 50_000) } |
                ".grails/project.yml has more than ${StateLimits.MANIFEST_LINES} lines"
        'a lock that is one comment' | '.grails/forge.lock.yml' | { 'format: 1\n#' + ('c' * (StateLimits.MAX_LOCK - 40)) + '\n' } |
                ".grails/forge.lock.yml has a line of more than ${StateLimits.MAX_LINE} characters"
    }

    void "the lock says which artifact a pack was taken from, and the manifest that it was asked for by it"() {
        given:
        String digest = 'cd' * 32
        String repository = 'HTTPS://Repo.Acme.Example/maven/' + ('m' * (StateLimits.MAX_ADDRESS - 33)) + '/'
        Pack audit = pack('acme-audit').withArtifact(new PackArtifact('acme-audit', 'com.acme:acme-audit-grails:2.3.0',
                repository, 'acme-audit-grails-2.3.0.jar', digest, 'sha256', false))
        Pack base = pack('acme-base', 'grails-app/conf/base.txt').withArtifact(new PackArtifact('acme-base', "${'g' * 200}:${'a' * 200}:${'1' * 200}",
                'file:/home/zzsomeone/repository/', 'acme-base.jar', digest, null, false))
        Pack secret = pack('acme-secret', 'grails-app/conf/secret.txt').withArtifact(new PackArtifact('acme-secret', 'com.acme:acme-secret:1.0',
                'https://zzsomeone:zzsecret@repo.acme.example/maven/', 'acme-secret-1.0.jar', digest, null, false))
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('com.acme.shop')
        Path target = Files.createDirectory(directory.resolve('app'))

        when:
        generator.generate(ApplicationType.WEB, project, new FileSystemOutputHandler(target.toFile(), ConsoleOutput.NOOP),
                generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                        ['acme-audit', 'acme-base', 'acme-secret'], ConsoleOutput.NOOP, [audit, base, secret]))
        ProjectState state = ProjectState.read(target)
        state.verifyBaseline(target)

        then: 'an https address is said, in whatever case it is written and as long as an address can be'
        repository.length() == StateLimits.MAX_ADDRESS
        with(state.lock.features.find { it.name == 'acme-audit' }) {
            origin == repository
            coordinates == 'com.acme:acme-audit-grails'
            version == '2.3.0'
            sha256 == digest
        }
        with(state.manifest.requestedFeatures.find { it.name == 'acme-audit' }) {
            coordinates == 'com.acme:acme-audit-grails'
            version == '2.3.0'
        }

        and: 'coordinates and a version as long as a repository lets them be are read back'
        with(state.lock.features.find { it.name == 'acme-base' }) {
            origin == null
            coordinates == "${'g' * 200}:${'a' * 200}"
            version == '1' * 200
        }
        state.manifest.requestedFeatures.find { it.name == 'acme-base' }.version == '1' * 200

        and: 'a directory of this machine and an address with a password are not said anywhere'
        state.lock.features.find { it.name == 'acme-secret' }.origin == null
        files(target).findAll { String path ->
            String content = new String(target.resolve(path).toFile().bytes, 'ISO-8859-1')
            content.contains('zzsomeone') || content.contains('zzsecret')
        }.isEmpty()
    }

    void "a state that could not be read back is not written, and nothing of the application is"() {
        given: 'an address longer than the state takes is left out, but a version longer than it takes cannot be'
        Pack pack = pack('acme-audit').withArtifact(new PackArtifact('acme-audit', "com.acme:acme-audit-grails:${'1' * 201}",
                'https://repo.acme.example/' + ('m' * StateLimits.MAX_ADDRESS), 'acme-audit-grails.jar', 'ab' * 32, null, false))
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('com.acme.shop')
        Path target = Files.createDirectory(directory.resolve('app'))

        when:
        generator.generate(ApplicationType.WEB, project, new FileSystemOutputHandler(target.toFile(), ConsoleOutput.NOOP),
                generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                        ['acme-audit'], ConsoleOutput.NOOP, [pack]))

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith('Cannot create the project because the record of how it is generated could not be read again: .grails/project.yml: requestedFeatures[0].version must be a version, but is ')
        target.toFile().list().length == 0
    }

    void "a file with a long path and a file Git would read are in the baseline as they are generated"() {
        given:
        String deep = (1..3).collect { 'd' * 200 }.join('/') + '/audit.txt'
        Pack attributes = pack('acme-audit', "'conf/{name}/.gitattributes'")
        Pack other = pack('acme-deep', deep)
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('com.acme.shop')
        Path target = Files.createDirectory(directory.resolve('app'))

        when:
        generator.generate(ApplicationType.WEB, project, new FileSystemOutputHandler(target.toFile(), ConsoleOutput.NOOP),
                generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                        ['acme-audit', 'acme-deep'], ConsoleOutput.NOOP, [attributes, other]))
        ProjectState state = ProjectState.read(target)
        state.verifyBaseline(target)

        then:
        state.baseline.files*.path.containsAll(['conf/shop/.gitattributes', deep])
        target.resolve('.grails/baseline/files/conf/shop/.gitattributes.baseline').toFile().getText('UTF-8') == 'kept as it is\n'
        !target.resolve('.grails/baseline/files/conf/shop/.gitattributes').toFile().exists()
        state.isAsGenerated(target, 'conf/shop/.gitattributes')
        state.isAsGenerated(target, deep)
        BaselineIndex.copyPath('a/.gitignore.baseline/.GitIgnore') == 'a/.gitignore.baseline.baseline/.GitIgnore.baseline'
        BaselineIndex.copyPath('a/.gitignored/x.gitignore') == 'a/.gitignored/x.gitignore'
    }

    void "the repositories that were given in place of those of Grails are in the lock, without what is of the machine"() {
        given:
        ContextFactory contextFactory = beanContext.getBean(ContextFactory)
        String before = contextFactory.repositoryOverride
        Path plain = generate('shop', [], ApplicationType.WEB, 'plain')

        when:
        contextFactory.repositoryOverride = 'https://repo.acme.example/maven; /home/someone/repository ;mavenLocal();' +
                'https://someone:password@private.acme.example/maven;file:///home/someone/other;mavenCentral()'
        Path given = generate('shop', [], ApplicationType.WEB, 'given')
        ProjectState state = ProjectState.read(given)
        state.verifyBaseline(given)
        String lock = given.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8')

        then:
        state.lock.repositories*.kind == [LockedRepository.REMOTE, LockedRepository.LOCAL, LockedRepository.MAVEN_LOCAL, LockedRepository.REMOTE,
                                          LockedRepository.LOCAL, LockedRepository.MAVEN_CENTRAL]
        state.lock.repositories*.url == ['https://repo.acme.example/maven', null, null, null, null, null]
        state.lock.repositoriesNotNamed == 0
        lock.contains('repositories:\n- kind: remote\n  url: https://repo.acme.example/maven\n- kind: local\n- kind: maven-local\n- kind: remote\n- kind: local\n- kind: maven-central\n')
        !lock.contains('someone')
        !given.resolve('.grails/project.yml').toFile().getText('UTF-8').contains('someone')
        !given.resolve('.grails/baseline/index.yml').toFile().getText('UTF-8').contains('someone')

        and: 'the build file has them, and so has its copy in the baseline, which is the file as it was generated'
        given.resolve('build.gradle').toFile().getText('UTF-8').contains('/home/someone/repository')
        given.resolve('.grails/baseline/files/build.gradle').toFile().bytes == given.resolve('build.gradle').toFile().bytes

        and: 'a lock without them is that of the repositories of Grails'
        ProjectState.read(plain).lock.repositories.empty
        !plain.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8').contains('repositories')
        plain.resolve('.grails/forge.lock.yml').toFile().bytes != given.resolve('.grails/forge.lock.yml').toFile().bytes

        cleanup:
        contextFactory.repositoryOverride = before
    }

    void "the lock has the address of a repository without what a token is given in, and counts the repositories it does not name"() {
        given:
        ContextFactory contextFactory = beanContext.getBean(ContextFactory)
        String before = contextFactory.repositoryOverride

        when: 'an address with a query and a fragment, and more repositories than a lock names'
        contextFactory.repositoryOverride = 'https://Repo.Acme.Example:8443/m%20aven/?token=hunter2#frag;' + (1..60).collect { "https://repo${it}.acme.example/m" }.join(';')
        Path project = generate('shop', [])
        ProjectState state = ProjectState.read(project)
        String lock = project.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8')

        then: 'the scheme, the host, the port and the path are said, and nothing else'
        state.lock.repositories.size() == StateLimits.MAX_REPOSITORIES
        state.lock.repositories[0].url == 'https://Repo.Acme.Example:8443/m%20aven/'
        state.lock.repositories[1].url == 'https://repo1.acme.example/m'
        state.lock.repositoriesNotNamed == 11
        lock.contains("repositoriesNotNamed: 11\n")
        !lock.contains('hunter2')
        !lock.contains('frag')

        and: 'the build file has every one of them, as it was given'
        project.resolve('build.gradle').toFile().getText('UTF-8').contains("url = 'https://Repo.Acme.Example:8443/m%20aven/?token=hunter2#frag'")
        project.resolve('build.gradle').toFile().getText('UTF-8').contains("url = 'https://repo60.acme.example/m'")

        when: 'a lock that names an address with a query'
        Path lockFile = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lockFile, sealed(lock.replace('url: https://repo1.acme.example/m\n', 'url: https://repo1.acme.example/m?token=hunter2\n')))
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == '.grails/forge.lock.yml: repositories[1].url must be the https address of a repository, without a name, a password, a query or a fragment, ' +
                'and only a remote repository has one'

        cleanup:
        contextFactory.repositoryOverride = before
    }

    @Unroll
    void "a repository that would be code of the build file is refused, without being repeated: #what"() {
        given:
        ContextFactory contextFactory = beanContext.getBean(ContextFactory)
        String before = contextFactory.repositoryOverride
        Path project = Files.createDirectory(directory.resolve('app'))

        when:
        contextFactory.repositoryOverride = 'https://repo.acme.example/m;' + entry
        generateInto(project, 'shop', [])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Entry 2 of 2 of GRAILS_REPO_URL has a character the address or the path of a repository cannot have here: a control character, a quote or a $'
        project.toFile().list().length == 0

        cleanup:
        contextFactory.repositoryOverride = before

        where:
        what              | entry
        'a line break'    | 'https://repo.example.com/m\n}\nprintln "PWNED"'
        'a single quote'  | "https://repo.example.com/m'+System.exit(1)+'"
        'a double quote'  | '/home/me/"repo"'
        'a dollar'        | 'https://repo.example.com/${System.exit(1)}'
        'an escape'       | "/home/me/repo\u001b[31m"
        'a secret in it'  | "https://someone:hunter2'@repo.example.com/m"
    }

    void "a path with a backslash or a space is written into the build file as the text it is"() {
        given:
        ContextFactory contextFactory = beanContext.getBean(ContextFactory)
        String before = contextFactory.repositoryOverride

        when:
        contextFactory.repositoryOverride = 'C:\\Users\\me\\my repo\\releases'
        String build = generate('shop', []).resolve('build.gradle').toFile().getText('UTF-8')

        then: 'the backslashes are escaped, so that the string of the build file is the path'
        build.contains("url = 'C:\\\\Users\\\\me\\\\my repo\\\\releases'")

        cleanup:
        contextFactory.repositoryOverride = before
    }

    void "a file where the project needs a directory, and a directory that cannot be written to, are seen before anything is written"() {
        given:
        Path project = Files.createDirectory(directory.resolve('app'))
        Files.writeString(project.resolve('grails-app'), 'a file')

        when:
        generateInto(project, 'shop', [])

        then:
        IllegalArgumentException file = thrown()
        file.message == 'Cannot create the project because grails-app in the directory is a file, and the project has files in a directory of that name; ' +
                'remove or rename it and create the project again'
        !file.message.contains(directory.toString())
        project.toFile().list() as List == ['grails-app']

        when: 'a directory where the project has a file'
        Files.delete(project.resolve('grails-app'))
        Files.createDirectory(project.resolve('build.gradle'))
        generateInto(project, 'shop', [])

        then:
        IllegalArgumentException dir = thrown()
        dir.message == 'Cannot create the project because build.gradle in the directory is a directory, and the project has a file of that name; ' +
                'remove or rename it and create the project again'
        project.toFile().list() as List == ['build.gradle']
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "a directory that cannot be written to is seen before anything is written"() {
        given:
        Path project = Files.createDirectory(directory.resolve('app'))
        Path state = Files.createDirectory(project.resolve('.grails'))
        state.toFile().setWritable(false)

        when:
        generateInto(project, 'shop', [])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Cannot create the project because .grails in the directory cannot be written to'
        project.toFile().list() as List == ['.grails']

        when:
        state.toFile().setWritable(true)
        Files.delete(state)
        project.toFile().setWritable(false)
        generateInto(project, 'shop', [])

        then:
        IllegalArgumentException whole = thrown()
        whole.message == 'Cannot create the project because the directory cannot be written to'

        cleanup:
        state.toFile().setWritable(true)
        project.toFile().setWritable(true)
    }

    void "a file that cannot be written is said by its path in the project, with what was written before it"() {
        given: 'an output that fails at its twelfth file, as a disk does that is full'
        List<String> written = []
        OutputHandler failing = new OutputHandler() {
            @Override
            boolean exists(String path) { false }
            @Override
            void write(String path, Template contents) throws IOException {
                if (written.size() == 11) {
                    throw new java.nio.file.FileSystemException('/home/zzsomeone/projects/shop/' + path, null, 'No space left on device')
                }
                written << path
            }
            @Override
            String getOutputLocation() { null }
            @Override
            void close() { }
        }

        when:
        beanContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('com.acme.shop'), new Options(), OperatingSystem.LINUX,
                [], failing, ConsoleOutput.NOOP)

        then:
        OutputFailedException e = thrown()
        e.message.startsWith('Cannot write ')
        e.message.contains(': the file system says "No space left on device". The project is not complete: 11 files were written before it (')
        e.message.endsWith(' and 1 more)')
        written.take(10).every { e.message.contains(it) }
        !e.message.contains('zzsomeone')
        e.message.readLines().size() == 1
    }

    @Unroll
    void "the state does not depend on the operating system an application is generated for: #operatingSystem"() {
        given:
        Path linux = generate('shop', ['grails-quartz'], ApplicationType.WEB, 'linux', new Options(), OperatingSystem.LINUX)
        Path other = generate('shop', ['grails-quartz'], ApplicationType.WEB, 'other',
                new Options(DevelopmentReloading.DEFAULT_OPTION, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION, operatingSystem),
                operatingSystem)

        expect:
        files(linux) == files(other)
        files(linux).every { String path -> linux.resolve(path).toFile().bytes == other.resolve(path).toFile().bytes }

        where:
        operatingSystem << [OperatingSystem.WINDOWS, OperatingSystem.MACOS_ARCH64, OperatingSystem.MACOS, OperatingSystem.SOLARIS, null]
    }

    @Unroll
    void "a #type application has a .gitattributes that keeps its files as generated on every clone"() {
        given:
        Path project = generate('shop', [], type)
        ProjectState state = ProjectState.read(project)

        expect: 'the file, and its copy in the baseline under a name Git does not read'
        project.resolve('.gitattributes').toFile().getText('UTF-8') ==
                '# Keeps the scripts of the wrappers with the line endings they need, whatever Git is set to do with line endings\n' +
                '*.bat text eol=crlf\ngradlew text eol=lf\ngrailsw text eol=lf\n*.jar binary\n'
        project.resolve('.grails/baseline/files/.gitattributes.baseline').toFile().bytes == project.resolve('.gitattributes').toFile().bytes
        !project.resolve('.grails/baseline/files/.gitattributes').toFile().exists()

        and: 'a rule for every file that is binary to the generator and is text: with it Git gives every clone the bytes that were generated'
        state.baseline.files.findAll { it.binary && StateYaml.isText(project.resolve(it.path).toFile().bytes) }*.path.every { String path ->
            String content = new String(project.resolve(path).toFile().bytes, 'ISO-8859-1')
            // eol=crlf: stored with line feeds and checked out with CR LF, which gives the file back when it has no other line ending
            path.endsWith('.bat') ? content.replace('\r\n', '\n').replace('\n', '\r\n') == content && !content.replace('\r\n', '').contains('\r') :
                    // eol=lf: checked out as it is stored
                    path in ['gradlew', 'grailsw'] && !content.contains('\r')
        }

        and: 'every other binary file is one Git sees to be binary, and every text file has line feeds only'
        state.baseline.files.findAll { it.binary && !StateYaml.isText(project.resolve(it.path).toFile().bytes) }*.path.every { String path ->
            path.endsWith('.jar') || (project.resolve(path).toFile().bytes as List).contains((byte) 0)
        }
        state.baseline.files.findAll { !it.binary }.every { !new String(project.resolve(it.path).toFile().bytes, 'ISO-8859-1').contains('\r') }

        when: 'a clone has its text files with the line endings of Windows, which is all Git does to them'
        state.baseline.files.findAll { !it.binary }.each {
            File file = project.resolve(it.path).toFile()
            file.bytes = new String(file.bytes, 'ISO-8859-1').replace('\n', '\r\n').getBytes('ISO-8859-1')
        }

        then:
        state.baseline.files.every { state.isAsGenerated(project, it.path) }

        where:
        type << ApplicationType.values().toList()
    }

    void "a file is as generated whatever its line endings are, unless it is binary"() {
        given:
        Path project = generate('shop', [])
        ProjectState state = ProjectState.read(project)
        File text = project.resolve('build.gradle').toFile()
        File binary = project.resolve('gradlew.bat').toFile()

        expect: 'the script for Windows has the line endings of Windows and is binary to its template'
        new String(binary.bytes, 'ISO-8859-1').contains('\r\n')
        state.baseline.find('gradlew.bat').binary
        !state.baseline.find('build.gradle').binary

        when: 'a clone has the text with the line endings of Windows'
        text.setText(text.getText('UTF-8').replace('\n', '\r\n'), 'UTF-8')

        then:
        state.isAsGenerated(project, 'build.gradle')

        when: 'the text is changed'
        text.append('// mine\r\n', 'UTF-8')

        then:
        !state.isAsGenerated(project, 'build.gradle')

        when: 'a commit has turned the line endings of the binary file into those of Unix'
        binary.bytes = new String(binary.bytes, 'ISO-8859-1').replace('\r\n', '\n').getBytes('ISO-8859-1')

        then:
        !state.isAsGenerated(project, 'gradlew.bat')

        when: 'a file is gone'
        text.delete()

        then:
        !state.isAsGenerated(project, 'build.gradle')

        when:
        state.isAsGenerated(project, 'grails-forge-cli.yml')

        then:
        ProjectStateException e = thrown()
        e.message == 'grails-forge-cli.yml is not a file the baseline of the project has'
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "a file of the project that cannot be read is said to be one, by its path, and not taken for a baseline that cannot be read"() {
        given:
        Path project = generate('shop', [])
        ProjectState state = ProjectState.read(project)
        Path build = project.resolve('build.gradle')
        Files.setPosixFilePermissions(build, PosixFilePermissions.fromString('---------'))

        when:
        state.isAsGenerated(project, 'build.gradle')

        then:
        AccessDeniedException e = thrown()
        Path.of(e.file) == build

        when: 'its copy in the baseline is what cannot be read'
        Files.setPosixFilePermissions(build, PosixFilePermissions.fromString('rw-r--r--'))
        Files.setPosixFilePermissions(project.resolve('.grails/baseline/files/build.gradle'), PosixFilePermissions.fromString('---------'))
        state.isAsGenerated(project, 'build.gradle')

        then:
        ProjectStateException baseline = thrown()
        baseline.message == 'The baseline of the project cannot be read: AccessDeniedException'

        cleanup:
        Files.setPosixFilePermissions(build, PosixFilePermissions.fromString('rw-r--r--'))
        Files.setPosixFilePermissions(project.resolve('.grails/baseline/files/build.gradle'), PosixFilePermissions.fromString('rw-r--r--'))
    }

    void "the files that were kept and are not as generated are named, and no others"() {
        given:
        Path project = Files.createDirectory(directory.resolve('app'))
        Files.writeString(project.resolve('build.gradle'), '// mine\n')
        Files.writeString(project.resolve('gradlew'), '#!/bin/sh\n')
        Path same = generate('shop', [], ApplicationType.WEB, 'same')
        Files.createDirectories(project.resolve('gradle/wrapper'))
        Files.copy(same.resolve('gradle/wrapper/gradle-wrapper.properties'), project.resolve('gradle/wrapper/gradle-wrapper.properties'))
        List<String> warnings = []

        when:
        beanContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('com.acme.shop'), new Options(), OperatingSystem.LINUX,
                [], new FileSystemOutputHandler(project.toFile(), recording(warnings)), ConsoleOutput.NOOP)

        then:
        warnings == ['2 files that the directory already had were kept and are not as generated: build.gradle, gradlew',
                     'The record of the project in .grails has them as they would have been generated, so they count as changed since then']
        ProjectState.read(project).manifest.tracked
    }

    void "more files that were kept than can be named are counted"() {
        given:
        Path project = Files.createDirectory(directory.resolve('app'))
        List<String> generated = applicationFiles(generate('shop', [], ApplicationType.WEB, 'same')).findAll { !it.contains('/') || it.startsWith('grails-app/conf/') || it.startsWith('grails-app/i18n/') }
        generated.each { String path ->
            Files.createDirectories(project.resolve(path).parent)
            Files.writeString(project.resolve(path), 'mine\n')
        }
        List<String> warnings = []

        when:
        beanContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('com.acme.shop'), new Options(), OperatingSystem.LINUX,
                [], new FileSystemOutputHandler(project.toFile(), recording(warnings)), ConsoleOutput.NOOP)

        then:
        generated.size() > FileSystemOutputHandler.MAX_KEPT_NAMED
        warnings[0].startsWith("${generated.size()} files that the directory already had were kept and are not as generated: ")
        warnings[0].endsWith(" and ${generated.size() - FileSystemOutputHandler.MAX_KEPT_NAMED} more")
        warnings[0].count(', ') == FileSystemOutputHandler.MAX_KEPT_NAMED - 1
    }

    void "a generation that did not come to its end is replaced by the next one"() {
        given: 'the manifest is written last: without it the state was not written to the end'
        Path project = generate('shop', [])
        Files.delete(project.resolve('.grails/project.yml'))
        Files.writeString(project.resolve('.grails/baseline/files/stale.txt'), 'of an earlier attempt\n')
        Files.createDirectories(project.resolve('.grails/baseline/files/stale'))
        Files.writeString(project.resolve('.grails/baseline/files/stale/more.txt'), 'of an earlier attempt\n')
        Files.writeString(project.resolve('.grails/cache'), 'not of the baseline\n')
        List<String> warnings = []

        when:
        beanContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('com.acme.shop'), new Options(), OperatingSystem.LINUX,
                ['grails-quartz'], new FileSystemOutputHandler(project.toFile(), recording(warnings)), ConsoleOutput.NOOP)
        ProjectState state = ProjectState.read(project)
        state.verifyBaseline(project)

        then: 'the state is that of this generation, and the files of the first that are others are said to be'
        state.lock.featureNames.contains('grails-quartz')
        !project.resolve('.grails/baseline/files/stale.txt').toFile().exists()
        !project.resolve('.grails/baseline/files/stale').toFile().exists()
        project.resolve('.grails/cache').toFile().getText('UTF-8') == 'not of the baseline\n'
        warnings[0].contains('were kept and are not as generated: ')
        warnings[0].contains('build.gradle')
        !warnings[0].contains('gradlew')
    }

    @IgnoreIf({ os.windows })
    void "nothing is written where the directory of the state is a file or a link: #what"() {
        given:
        Path project = Files.createDirectory(directory.resolve('app'))
        Path elsewhere = Files.createDirectory(directory.resolve('elsewhere'))
        place(project, elsewhere)

        when:
        generateInto(project, 'shop', [])

        then:
        IllegalArgumentException e = thrown()
        e.message == message
        !e.message.contains(directory.toString())
        elsewhere.toFile().list().length == 0
        !project.resolve('build.gradle').toFile().exists()

        where:
        what | place | message
        'a file' | { Path app, Path other -> Files.writeString(app.resolve('.grails'), 'x') } |
                'Cannot create the project because .grails in the directory is a file or a link and not a directory; remove it and create the project again'
        'a link to a directory' | { Path app, Path other -> Files.createSymbolicLink(app.resolve('.grails'), other) } |
                'Cannot create the project because .grails in the directory is a file or a link and not a directory; remove it and create the project again'
        'a link in place of the baseline' | { Path app, Path other -> Files.createDirectory(app.resolve('.grails')); Files.createSymbolicLink(app.resolve('.grails/baseline'), other) } |
                'Cannot create the project because .grails/baseline in the directory is a file or a link and not a directory; remove it and create the project again'
        'a directory in place of the lock' | { Path app, Path other -> Files.createDirectories(app.resolve('.grails/forge.lock.yml')) } |
                'Cannot create the project because .grails/forge.lock.yml in the directory is a directory or a link and not a file; remove it and create the project again'
    }

    @Unroll
    void "a manifest is held to the name and the package an application can be created with: #name in #defaultPackage"() {
        given:
        Path project = generate('shop', [])
        Path manifest = project.resolve('.grails/project.yml')
        Files.writeString(manifest, manifest.toFile().getText('UTF-8').replace('name: shop', "name: '${name}'").replace('defaultPackage: com.acme', "defaultPackage: '${defaultPackage}'"))
        rebind(project)

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == ".grails/project.yml: ${message}"

        where:
        name               | defaultPackage || message
        '../../../etc/x'   | 'com.acme'     || 'name is not a name an application can be created with'
        '/etc/passwd'      | 'com.acme'     || 'name is not a name an application can be created with'
        'a/b'              | 'com.acme'     || 'name is not a name an application can be created with'
        'aux'              | 'com.acme'     || 'name is not a name an application can be created with'
        'shop.'            | 'com.acme'     || 'name is not a name an application can be created with'
        'shop.api'         | 'com.acme'     || 'defaultPackage is not a package an application can be created with'
        'shop'             | 'com.acme.'    || 'defaultPackage is not a package an application can be created with'
        'shop'             | '.com.acme'    || 'defaultPackage is not a package an application can be created with'
        'shop'             | 'com..acme'    || 'defaultPackage is not a package an application can be created with'
        'shop'             | 'com.aux'      || 'defaultPackage is not a package an application can be created with'
        'shop'             | 'com/acme'     || 'defaultPackage is not a package an application can be created with'
        'shop'             | 'com.1acme'    || 'defaultPackage is not a package an application can be created with'
        "sh\u200Bop"       | 'com.acme'     || 'name must be one line of at most 255 characters, none of them a control character or one that is not shown'
    }

    void "what a file of the state says is not shown with characters that are not seen"() {
        given:
        Path project = generate('shop', [])
        Files.writeString(project.resolve('.grails/project.yml'), project.resolve('.grails/project.yml').toFile().getText('UTF-8') + "\"a\u200B\uFEFF\u00AD\u202Eb\": c\n")

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == '.grails/project.yml: a????b is not a key this version of Grails reads'
    }

    void "an application that is a project of another build has its own files only, and its state says so"() {
        given:
        Path alone = generate('shop', [], ApplicationType.WEB, 'alone')
        Path project = generate('shop', [], ApplicationType.WEB, 'part', new Options().withSubproject(true))

        when:
        ProjectState state = ProjectState.read(project)
        state.verifyBaseline(project)
        Set<String> files = state.baseline.files*.path as Set<String>
        Set<String> ofAlone = ProjectState.read(alone).baseline.files*.path as Set<String>

        then: 'what is of the root of a build is left out, and nothing else'
        ofAlone - files == ['.gitattributes', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties', 'gradlew', 'gradlew.bat',
                            'grails-wrapper.jar', 'grailsw', 'grailsw.bat'] as Set<String>
        files - ofAlone == [] as Set<String>
        (ofAlone - files).every { String path -> ProjectState.isOfBuildRoot(path) && !Files.exists(project.resolve(path)) }
        !Files.exists(project.resolve('gradle'))
        files.containsAll(['build.gradle', 'gradle.properties', '.gitignore'])

        and: 'the properties are those of the project, without the ones Gradle reads at the root of a build only'
        project.resolve('gradle.properties').toFile().readLines().findAll { String line -> line.startsWith('org.gradle.') } == []
        project.resolve('gradle.properties').toFile().text.contains("grailsVersion=${VersionInfo.grailsVersion}")
        alone.resolve('gradle.properties').toFile().text.contains('org.gradle.daemon=true')

        and: 'the manifest and the lock say which kind of application it is, and those of an application of its own do not'
        state.manifest.options.subproject
        state.lock.options == state.manifest.options
        project.resolve('.grails/project.yml').toFile().getText('UTF-8').contains('  reloading: devtools\n  layout: subproject\ngenerationState: tracked\n')
        project.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8').contains('  layout: subproject\n')
        !ProjectState.read(alone).manifest.options.subproject
        !alone.resolve('.grails/project.yml').toFile().getText('UTF-8').contains('layout')
        !alone.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8').contains('layout')

        when: 'the settings of a build are asked for with it'
        generate('shop', ['gradle-settings-file'], ApplicationType.WEB, 'both', new Options().withSubproject(true))

        then:
        FeatureSelectionException refused = thrown()
        refused.message.contains('gradle-settings-file')
        directory.resolve('both').toFile().list().length == 0
    }

    @Unroll
    void "#path is of the root of a build: #ofRoot"() {
        expect:
        ProjectState.isOfBuildRoot(path) == ofRoot

        where:
        path                                       | ofRoot
        'gradlew'                                  | true
        'GRADLEW.BAT'                              | true
        './gradle//wrapper/gradle-wrapper.jar'     | true
        'gradle/wrapper/gradle-wrapper.properties' | true
        'settings.gradle'                          | true
        'settings.gradle.kts'                      | true
        'grailsw'                                  | true
        'grailsw.bat'                              | true
        'grails-wrapper.jar'                       | true
        '.gitattributes'                           | true
        '.gitignore'                               | false
        'build.gradle'                             | false
        'gradle.properties'                        | false
        'src/main/gradlew'                         | false
        'grails-app/conf/settings.gradle'          | false
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

    // the files the baseline has: those of the application
    private static List<String> applicationFiles(Path project) {
        return files(project).findAll { !ProjectState.isStatePath(it) }
    }

    // a lock that was changed, with the digest of what it now is at its end, as a tool that wrote it would have it
    private static String sealed(String lock) {
        String body = lock.substring(0, lock.lastIndexOf('seal:\n'))
        return body + 'seal:\n  sha256: ' + sha256(body.getBytes('UTF-8')) + '\n'
    }

    // the manifest was changed: the lock is given its digest, and its own
    private static void rebind(Path project) {
        Path lock = project.resolve('.grails/forge.lock.yml')
        String text = lock.toFile().getText('UTF-8').replaceFirst('manifest:\n  sha256: [0-9a-f]{64}',
                'manifest:\n  sha256: ' + sha256(project.resolve('.grails/project.yml').toFile().bytes))
        Files.writeString(lock, sealed(text))
    }

    // applies what FeatureDiffer wrote, a unified difference for each file, as patch does
    private static List<String> apply(Path project, String diff) {
        List<String> changed = []
        List<List<String>> files = []
        diff.readLines().each { String line ->
            if (line.startsWith('--- ')) {
                files << []
            }
            if (files) {
                files.last() << line
            }
        }
        files.each { List<String> lines ->
            String path = lines[0].substring(4)
            File file = project.resolve(path).toFile()
            List<String> before = file.file ? file.getText('UTF-8').split('\n') as List<String> : []
            List<String> after = UnifiedDiffUtils.parseUnifiedDiff(lines.reverse().dropWhile { it == '' }.reverse()).applyTo(before)
            file.parentFile.mkdirs()
            file.setText(after.join('\n') + '\n', 'UTF-8')
            changed << path
        }
        return changed
    }

    private ConsoleOutput recording(List<String> warnings) {
        return new ConsoleOutput() {
            @Override
            void out(String message) { }
            @Override
            void err(String message) { }
            @Override
            void warning(String message) { warnings << message }
            @Override
            boolean showStacktrace() { false }
            @Override
            boolean verbose() { false }
        }
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

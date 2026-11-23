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
package org.grails.forge.cli.command

import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.feature.FeatureSelectionException
import spock.lang.Unroll

/**
 * An application made from a preset with {@code --preset}: the preset given as a directory, a jar, the
 * coordinates of its artifact or its name in a feature catalog, its options as defaults the command
 * line overrides, the type of application it is for, and the record of the application, which
 * {@code project show} reads. Every repository and catalog here is a directory or a file.
 */
class CreatePresetSpec extends CommandSpec {

    static final String RANGE = '[9.0.0,10.0.0)'

    static final String COORDINATES = 'com.acme:acme-preset:1.0.0'

    static final String PRESET = """\
        format: 1
        kind: preset
        name: acme-service
        templateApi: 1
        compatibility:
          grails: '${RANGE}'
          forge: '${RANGE}'
        title: Acme Service
        description: How Acme makes a service
        applicationType: rest-api
        options: { data: mongodb, jdk: 25 }
        features: [grails-quartz]
        dependencies:
          - scope: implementation
            coordinates: com.acme:acme-logging
            version: 1.2.0
        configuration:
          acme.service.team: payments
        files:
          - from: TEAM.md.gsp
            to: TEAM.md
        """.stripIndent()

    static final String MONGODB = 'implementation "org.apache.grails:grails-data-mongodb"'

    static final String TEMPLATE = '# ${project.naturalName}, a ${applicationType} of the payments team, with ${options.data}\n'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File repository
    String address
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        work = Files.createTempDirectory('grailsforgepresets').toFile()
        repository = Files.createTempDirectory('grailsforgerepository').toFile()
        address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        work.deleteDir()
        repository.deleteDir()
        home.deleteDir()
    }

    void "an application is made from a preset in a directory: its type, its options, its features and what it adds"() {
        given:
        File preset = directory('acme-service', PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath)

        then:
        exit == 0
        err.toString().isEmpty()
        printed.contains("| Preset acme-service from ${preset.absolutePath}")
        printed.contains('    dependencies: implementation com.acme:acme-logging:1.2.0')
        printed.contains('Application created')

        and: 'the options of the preset, the features it asks for and what it adds'
        String build = new File(dir, 'shop/build.gradle').text
        build.contains(MONGODB)
        build.contains('org.apache.grails:grails-quartz')
        build.contains('implementation "com.acme:acme-logging:1.2.0"')
        new File(dir, 'shop/grails-app/conf/application.yml').text.contains('team: payments')
        new File(dir, 'shop/TEAM.md').text == '# Shop, a rest-api of the payments team, with mongodb\n'

        and: 'the record says the application was made from it, which is the feature of a pack'
        String manifest = new File(dir, 'shop/.grails/project.yml').text
        manifest.contains('applicationType: rest-api\n')
        manifest.contains('preset: acme-service\n')
        manifest.contains('requestedFeatures:\n- name: acme-service\n')
        manifest.contains('  data: mongodb\n')
        manifest.contains('  jdk: 25\n')
        new File(dir, 'shop/.grails/forge.lock.yml').text.contains('- name: acme-service\n  source: pack\n')
    }

    void "an option given on the command line is used in place of the one of the preset"() {
        given:
        File preset = directory('acme-service', PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath, '--data', 'hibernate7', '--jdk', '21')

        then:
        exit == 0
        String build = new File(dir, 'shop/build.gradle').text
        !build.contains(MONGODB)
        build.contains('org.apache.grails:grails-data-hibernate7"')
        new File(dir, 'shop/.grails/project.yml').text.contains('  jdk: 21\n')
        new File(dir, 'shop/TEAM.md').text.endsWith('with hibernate7\n')
        new File(dir, 'shop/.grails/project.yml').text.contains('  data: hibernate7\n')
    }

    void "a Grails Data implementation of the preset is kept as one of the command line is, and the command line replaces it"() {
        given:
        File preset = directory('acme-service', PRESET.replace('data: mongodb', 'data: hibernate7'), ['TEAM.md.gsp': TEMPLATE])

        when: 'the Grails Data implementation of the preset is Hibernate 7, and a feature asks for Hibernate 5'
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath, '--features', 'gorm-hibernate5')

        then: 'Hibernate 7 does not give way: only the default of Grails does'
        exit == 2
        err.toString().contains('Only one Grails Data for Hibernate implementation can be selected: gorm-hibernate5 or gorm-hibernate7')
        !new File(dir, 'shop').exists()

        when: 'the option is given on the command line'
        int given = create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath, '--features', 'gorm-hibernate5', '--data', 'hibernate5')

        then:
        assert given == 0, err.toString()
        new File(dir, 'shop/build.gradle').text.contains('org.apache.grails:grails-data-hibernate5"')
        !new File(dir, 'shop/build.gradle').text.contains('org.apache.grails:grails-data-hibernate7"')
    }

    void "a feature the preset asks for cannot be left out"() {
        given:
        File preset = directory('acme-service', PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath, '--exclude-features', 'grails-quartz')

        then:
        exit == 2
        err.toString().contains('grails-quartz')
        !new File(dir, 'shop').exists()
    }

    void "a servlet container of the preset gives way to a feature that requires another, as the default of Grails does"() {
        given:
        File preset = directory('acme-service', PRESET.replace('options: { data: mongodb, jdk: 25 }', 'options: { data: mongodb, servlet: undertow, jdk: 25 }'),
                ['TEAM.md.gsp': TEMPLATE])
        File onJetty = directory('acme-jetty', """\
            format: 1
            kind: feature
            templateApi: 1
            name: acme-jetty
            compatibility:
              grails: '${RANGE}'
              forge: '${RANGE}'
            requires: [spring-boot-starter-jetty]
            """.stripIndent(), [:])

        when:
        int alone = create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath)

        then:
        alone == 0
        new File(dir, 'shop/build.gradle').text.contains('grails-undertow')
        new File(dir, 'shop/.grails/project.yml').text.contains('  servlet: undertow\n')

        when:
        File elsewhere = other()
        int jetty = create(elsewhere, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath, '--features', 'acme-jetty',
                '--feature-pack-dir', onJetty.absolutePath)

        then:
        assert jetty == 0, err.toString()
        new File(elsewhere, 'shop/build.gradle').text.contains('spring-boot-starter-jetty')
        !new File(elsewhere, 'shop/build.gradle').text.contains('grails-undertow')
        new File(elsewhere, 'shop/.grails/project.yml').text.contains('  servlet: jetty\n')

        when: 'the option on the command line is a choice, which does not give way'
        int chosen = create(other(), 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath, '--features', 'acme-jetty',
                '--feature-pack-dir', onJetty.absolutePath, '--servlet', 'undertow')

        then:
        chosen == 2
        err.toString().contains('There can only be one of the following features selected: [grails-undertow, spring-boot-starter-jetty]')
    }

    void "a feature given on the command line is added to those of the preset"() {
        given:
        File preset = directory('acme-service', PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath, '--features', 'cache')

        then:
        exit == 0
        String build = new File(dir, 'shop/build.gradle').text
        build.contains('org.apache.grails:grails-quartz')
        build.contains('org.apache.grails:grails-cache')
        new File(dir, 'shop/.grails/project.yml').text.contains('requestedFeatures:\n- name: acme-service\n- name: cache\n')
    }

    void "a preset of another type of application is refused by #command, which names the command that takes it"() {
        given:
        File preset = directory('acme-service', PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, command, 'com.example.shop', '--preset', preset.absolutePath)

        then:
        exit == 2
        err.toString().contains("The preset acme-service is for an application of the type rest-api, and ${command} creates one of the type ${type}: " +
                "create the application with create-restapi --preset ${FeatureSelectionException.shown(preset.absolutePath)}")
        !new File(dir, 'shop').exists()

        where:
        command          | type
        'create-app'     | 'web'
        'create-plugin'  | 'plugin'
    }

    void "a preset is taken by the coordinates of its artifact, with its digest in the record, and project show gives the command that makes it again"() {
        given:
        File jar = publish(COORDINATES, PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 0
        printed.contains("| Preset acme-service from ${COORDINATES} (${address}), sha256 ${sha256(jar)}")

        and: 'a preset is no plugin of the application: no word that it does not declare one'
        !printed.contains('Warning')
        !new File(dir, 'shop/build.gradle').text.contains('acme-preset')

        and: 'the record has the artifact and its digest as it has them for a feature pack'
        new File(dir, 'shop/.grails/project.yml').text.contains('preset: acme-service\nrequestedFeatures:\n- name: acme-service\n  coordinates: com.acme:acme-preset\n  version: 1.0.0\n')
        new File(dir, 'shop/.grails/forge.lock.yml').text.contains("- name: acme-service\n  source: pack\n  coordinates: com.acme:acme-preset\n  version: 1.0.0\n  sha256: ${sha256(jar)}\n")

        when:
        int shown = run('project', 'show')
        List<String> lines = printed.readLines()

        then: 'project show names the preset, and the command has it with --preset and its SHA-256'
        shown == 0
        lines.any { String line -> line.trim() == "preset        acme-service (${COORDINATES})" }
        lines.any { String line -> line.trim().startsWith('--preset') && line.contains(COORDINATES) && line.contains('(differs from the current default: (none))') }
        lines.last() == "grails-forge-cli create-restapi com.example.shop --jdk 25 --data mongodb --preset ${COORDINATES} " +
                "--feature-pack-sha256 ${COORDINATES}=${sha256(jar)}"

        when: 'the JSON document'
        run('project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(printed)

        then:
        document.preset == [name: 'acme-service', coordinates: 'com.acme:acme-preset', version: '1.0.0']
        document.effectiveOptions.find { it.name == 'preset' } == [name: 'preset', option: '--preset', value: [COORDINATES], default: [], status: 'differs']

        when: 'that command is run in another directory, with the repository the record does not name'
        File other = Files.createTempDirectory('grailsforgeother').toFile()
        int again = create(other, (lines.last().split(' ').drop(1) + ['--feature-pack-repository', address]) as String[])

        then: 'the application is the same, its record among its files'
        again == 0
        tree(new File(other, 'shop')) == tree(new File(dir, 'shop'))

        cleanup:
        other?.deleteDir()
    }

    void "a preset whose artifact does not have the SHA-256 that was asked for is refused before it is opened"() {
        given:
        publish(COORDINATES, PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', COORDINATES, '--feature-pack-repository', address,
                '--feature-pack-sha256', "${COORDINATES}=${'0' * 64}")

        then:
        exit == 1
        err.toString().contains(COORDINATES)
        err.toString().contains('0' * 64)
        !new File(dir, 'shop').exists()
    }

    void "a preset is taken from a jar by its path"() {
        given:
        File jar = publish(COORDINATES, PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', jar.absolutePath)

        then:
        exit == 0
        printed.contains("| Preset acme-service from ${jar.absolutePath}")
        new File(dir, 'shop/TEAM.md').exists()

        when: 'the record does not say where a preset of a jar was'
        run('project', 'show')

        then:
        printed.contains('The application was made from the preset acme-service, given as a directory or a jar, which the record does not name: ' +
                'give it to the command with --preset')
        !printed.readLines().last().contains('--preset')
        !printed.contains('give it to the command with --feature-pack-dir')
    }

    void "a preset is taken by its name from a feature catalog, with the SHA-256 the catalog records"() {
        given:
        File jar = publish(COORDINATES, PRESET, ['TEAM.md.gsp': TEMPLATE])
        File catalog = catalog([[coords: 'com.acme:acme-preset', versions: [
                [version: '1.0.0', pack: [format: 1, kind: 'preset', name: 'acme-service', templateApi: 1, applicationTypes: ['rest-api'],
                                          compatibility: [grails: RANGE, forge: RANGE], sha256: sha256(jar)]]]]])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', 'acme-service', '--feature-catalog', catalog.toURI().toString(),
                '--feature-pack-repository', address)

        then:
        exit == 0
        printed.contains("| The preset acme-service is taken as ${COORDINATES}, which the feature catalog ${catalog.toPath().toUri()}")
        printed.contains("| Preset acme-service from ${COORDINATES} (${address}), sha256 ${sha256(jar)}")
        new File(dir, 'shop/.grails/project.yml').text.contains('preset: acme-service\n')

        when: 'a name no catalog has a preset of'
        int none = create(other(), 'create-restapi', 'com.example.shop', '--preset', 'acme-batch', '--feature-catalog', catalog.toURI().toString(),
                '--feature-pack-repository', address)

        then:
        none == 2
        err.toString().contains('There is no preset acme-batch in the feature catalog')
    }

    void "a feature of a catalog is not a preset, and a preset of a catalog is not a feature"() {
        given:
        File jar = publish(COORDINATES, PRESET, ['TEAM.md.gsp': TEMPLATE])
        File catalog = catalog([[coords: 'com.acme:acme-preset', versions: [
                [version: '1.0.0', pack: [format: 1, kind: 'preset', name: 'acme-service', templateApi: 1, compatibility: [grails: RANGE, forge: RANGE],
                                          sha256: sha256(jar)]]]]])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--features', 'acme-service', '--feature-catalog', catalog.toURI().toString(),
                '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().contains('acme-service')
        !new File(dir, 'shop').exists()
    }

    void "the name of a preset needs a catalog"() {
        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', 'acme-service')

        then:
        exit == 2
        err.toString().contains('--preset acme-service is the name of a preset, which is looked up in a feature catalog, and no catalog is read')
    }

    @Unroll
    void "--preset #given is refused: #what"() {
        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', given)

        then:
        exit == 2
        err.toString().contains(message)
        !new File(dir, 'shop').exists()

        where:
        what                         | given                    || message
        'it is none of the forms'    | 'Acme Service'           || '--preset Acme Service is none of the forms a preset is given in'
        'a path to nothing'          | '/no/such/preset'        || '--preset /no/such/preset is a path, and there is no directory or file there'
        'not the coordinates of one' | 'com.acme:acme-preset'   || 'is not the coordinates of a plugin: they are group:artifact:version'
        'a version range'            | 'com.acme:acme:[1,2)'    || 'is not the coordinates of a plugin'
    }

    void "a feature pack is not a preset, and a preset is not a feature pack"() {
        given:
        File feature = directory('acme-audit', CreateAppFeaturePackSpec.DESCRIPTOR, ['audit.txt': 'kept as it is'])
        File preset = directory('acme-service', PRESET, ['TEAM.md.gsp': TEMPLATE])
        publish(COORDINATES, PRESET, ['TEAM.md.gsp': TEMPLATE])

        when: 'a feature pack is given as the preset'
        int asPreset = create(dir, 'create-restapi', 'com.example.shop', '--preset', feature.absolutePath)

        then:
        asPreset == 2
        err.toString().contains("--preset ${FeatureSelectionException.shown(feature.absolutePath)} has the feature pack acme-audit, which is not a preset (its kind is feature)")

        when: 'a preset is given as a feature pack, in a directory or by its coordinates'
        int asDirectory = create(dir, 'create-restapi', 'com.example.shop', '--feature-pack-dir', preset.absolutePath)
        String ofDirectory = err.toString()
        int asCoordinates = create(dir, 'create-restapi', 'com.example.shop', '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        asDirectory == 2
        ofDirectory.contains("${FeatureSelectionException.shown(preset.absolutePath)} has the preset acme-service, which is not a feature: an application is made from a preset with --preset")
        asCoordinates == 2
        err.toString().contains("${COORDINATES} has the preset acme-service, which is not a feature")
        !new File(dir, 'shop').exists()
    }

    void "an application is made from one preset"() {
        given:
        File preset = directory('acme-service', PRESET, ['TEAM.md.gsp': TEMPLATE])

        when:
        int exit = create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath, '--preset', preset.absolutePath)

        then:
        exit == 2
        err.toString().contains('--preset')
        !new File(dir, 'shop').exists()
    }

    void "a feature is not added to an application made from a preset, and it is not generated again, as for one with a feature pack"() {
        given:
        File preset = directory('acme-service', PRESET, ['TEAM.md.gsp': TEMPLATE])
        create(dir, 'create-restapi', 'com.example.shop', '--preset', preset.absolutePath)
        Map<String, String> before = tree(new File(dir, 'shop'))

        when:
        int exit = run('feature', 'add', 'cache')

        then:
        exit == 1
        err.toString().contains('The application has the feature acme-service of a feature pack that is not part of Grails; a feature cannot yet be added to such an application')
        tree(new File(dir, 'shop')) == before

        when: 'nor is it generated again'
        int regenerated = run('project', 'regenerate', '--output', other().absolutePath)

        then:
        regenerated == 1
        err.toString().contains('The application has the feature acme-service of a feature pack that is not part of Grails')
        tree(new File(dir, 'shop')) == before
    }

    void "the option is in the help of the create commands"() {
        when:
        ForgeCli.execute(beanContext, ['create-restapi', '--help'] as String[])

        then:
        out.toString().contains('--preset=PRESET')
    }

    private String getPrinted() {
        return out.toString()
    }

    private File other() {
        File other = new File(work, "other${System.nanoTime()}")
        other.mkdirs()
        return other
    }

    private int create(File directory, String... arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        out.reset()
        err.reset()
        try {
            return ForgeCli.execute(beanContext, arguments)
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private int run(String... arguments) {
        return create(new File(dir, 'shop'), arguments)
    }

    private File directory(String name, String descriptor, Map<String, String> templates) {
        File pack = new File(work, name)
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText(descriptor, 'UTF-8')
        templates.each { String path, String text -> new File(pack, "templates/${path}").setText(text, 'UTF-8') }
        return pack
    }

    private File publish(String coordinates, String descriptor, Map<String, String> templates) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
        Map<String, String> entries = ['META-INF/grails-forge/feature.yml': descriptor] +
                templates.collectEntries { String path, String text -> ["META-INF/grails-forge/templates/${path}".toString(), text] }
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            entries.each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return jar
    }

    private File catalog(List plugins) {
        File catalog = new File(work, 'catalog.json')
        catalog.setText(JsonOutput.toJson(plugins), 'UTF-8')
        return catalog
    }

    // every file of an application by its path, with its content
    private static Map<String, String> tree(File root) {
        Map<String, String> files = new TreeMap<>()
        root.eachFileRecurse(groovy.io.FileType.FILES) { File file ->
            files.put(root.toPath().relativize(file.toPath()).join('/'), sha256(file))
        }
        return files
    }

    private static String sha256(File file) {
        return MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString()
    }
}

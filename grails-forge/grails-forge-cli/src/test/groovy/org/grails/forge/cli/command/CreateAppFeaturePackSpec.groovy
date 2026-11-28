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
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec

class CreateAppFeaturePackSpec extends CommandSpec implements CommandFixture {

    static final String DESCRIPTOR = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
            'title: Acme Audit Trail\ndescription: Adds the Acme audit plugin\n' +
            'dependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-audit-grails\n    version: 2.3.0\n' +
            "files:\n  - from: audit.txt\n    to: 'grails-app/conf/{packagePath}/audit.txt'\n"

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File packs

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        packs = Files.createTempDirectory('grailsforgepacks').toFile()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        packs.deleteDir()
    }

    void "an application is created with the feature of a pack in a directory"() {
        given:
        File pack = pack('acme', DESCRIPTOR)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', 'acme-audit',
                '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${pack.absolutePath}")
        out.toString().contains('Application created')

        and: 'what the pack adds is said before the application is written'
        out.toString().contains('    dependencies: implementation com.acme:acme-audit-grails:2.3.0')
        out.toString().contains('    files it writes outside the sources: grails-app/conf/{packagePath}/audit.txt')

        and:
        new File(dir, 'shop/build.gradle').text.contains('implementation "com.acme:acme-audit-grails:2.3.0"')
        new File(dir, 'shop/grails-app/conf/com/example/audit.txt').text == 'kept as it is'
        new File(dir, 'shop/.grails/forge.lock.yml').text.contains('- name: acme-audit\n  source: pack\n')
    }

    void "an application is created with the feature of a pack in the jar of a plugin"() {
        given:
        File jar = new File(packs, 'acme-plugin.jar')
        new java.util.zip.ZipOutputStream(new FileOutputStream(jar)).withCloseable { zip ->
            ['META-INF/grails-forge/feature.yml': DESCRIPTOR, 'META-INF/grails-forge/templates/audit.txt': 'kept as it is'].each { String path, String text ->
                zip.putNextEntry(new java.util.zip.ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', 'acme-audit',
                '--feature-pack-jar', jar.absolutePath)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${jar.absolutePath}")
        new File(dir, 'shop/build.gradle').text.contains('implementation "com.acme:acme-audit-grails:2.3.0"')
        new File(dir, 'shop/grails-app/conf/com/example/audit.txt').text == 'kept as it is'

        when: 'a file that is no jar is a mistake in the command line'
        int refused = CliRunner.run(CreateAppCommand, beanContext, 'other', '--feature-pack-jar', new File(packs, 'none.jar').absolutePath)

        then:
        refused == 2
        err.toString().contains('It is not a file')
        !new File(dir, 'other').exists()
    }

    void "a pack that is given and not selected adds nothing"() {
        given:
        File pack = pack('acme', DESCRIPTOR)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 0
        !new File(dir, 'shop/build.gradle').text.contains('acme')

        and: 'and is not named as one the application is made with'
        !out.toString().contains('Feature pack')
    }

    void "one directory given twice is a mistake in the command line"() {
        given:
        File pack = pack('acme', DESCRIPTOR)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'acme-audit',
                '--feature-pack-dir', pack.absolutePath, '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 2
        err.toString().contains('both have the name acme-audit')
        !new File(dir, 'shop').exists()
    }

    void "a pack that another selected pack requires is named too"() {
        given:
        File first = pack('one', DESCRIPTOR + 'requires: [acme-base]\n')
        File second = pack('two', DESCRIPTOR.replace('name: acme-audit', 'name: acme-base').replace('acme-audit-grails', 'acme-base').replace('audit.txt', 'base.txt'))

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'acme-audit',
                '--feature-pack-dir', first.absolutePath, '--feature-pack-dir', second.absolutePath)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${first.absolutePath}")
        out.toString().contains("Feature pack acme-base from ${second.absolutePath}")
    }

    void "what a descriptor says cannot be markup of the command line"() {
        given:
        File pack = pack('acme', DESCRIPTOR.replace('description: Adds the Acme audit plugin', "description: 'Harmless @|bold,red (+)|@ text'"))

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, '--list-features', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 2
        err.toString().contains("description: has '@|' in it, which the command line reads as the start of markup")
    }

    void "the option for a directory is in the help of the command, and the one for a jar is not yet"() {
        when:
        CliRunner.run(CreateAppCommand, beanContext, '--help')

        then:
        out.toString().contains('--feature-pack-dir')
        !out.toString().contains('feature-pack-jar')
        out.toString().contains('--features')
    }

    void "the feature of a pack is not there without its directory"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'acme-audit')

        then:
        exit != 0
        err.toString().contains('The requested feature does not exist: acme-audit')
        !new File(dir, 'shop').exists()
    }

    void "the packs of several directories can be used together"() {
        given:
        File first = pack('one', DESCRIPTOR)
        File second = pack('two', DESCRIPTOR.replace('name: acme-audit', 'name: acme-base').replace('acme-audit-grails', 'acme-base').replace('audit.txt', 'base.txt'))

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'acme-audit,acme-base',
                '--feature-pack-dir', first.absolutePath, '--feature-pack-dir', second.absolutePath)

        then:
        exit == 0
        new File(dir, 'shop/build.gradle').text.contains('"com.acme:acme-audit-grails:2.3.0"')
        new File(dir, 'shop/build.gradle').text.contains('"com.acme:acme-base:2.3.0"')
    }

    void "the features that are listed include those of the packs"() {
        given:
        File pack = pack('acme', DESCRIPTOR)

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, '--list-features', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 0
        out.toString() =~ /acme-audit\s+Adds the Acme audit plugin/
        out.toString().contains('gsp')
    }

    void "a directory that is not a pack is a mistake in the command line, and nothing is created"() {
        given:
        File notAPack = new File(packs, 'empty')
        notAPack.mkdirs()

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--feature-pack-dir', notAPack.absolutePath)

        then:
        exit == 2
        err.toString().contains('There is no feature.yml in the directory, or it is not a file')
        !new File(dir, 'shop').exists()
    }

    void "a pack whose descriptor is wrong is reported with what is wrong, and nothing is created"() {
        given:
        File pack = pack('acme', DESCRIPTOR.replace('format: 1', 'format: 2').replace('version: 2.3.0', "version: '2.3.0\"'"))

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 2
        err.toString().contains('format: must be the number 1')
        err.toString().contains('dependencies[0].version: must be one version')
        !new File(dir, 'shop').exists()
    }

    void "a pack that the application cannot take is refused before anything is written"() {
        given:
        File pack = pack('acme', DESCRIPTOR + "build:\n  properties:\n    grailsVersion: '0.0.1'\n")

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'shop', '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath)

        then:
        exit != 0
        err.toString().contains('The feature acme-audit sets the build property grailsVersion')

        and: 'what it adds was said before it was refused'
        out.toString().contains('    build properties: grailsVersion=0.0.1')
        !new File(dir, 'shop/build.gradle').exists()
    }

    void "the build of an application made with a pack is one Gradle can run"() {
        given:
        File pack = pack('acme', 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-text\n' +
                "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
                'dependencies:\n' +
                '  - scope: implementation\n    coordinates: org.apache.commons:commons-lang3\n' +
                "    excludes: ['org.slf4j:slf4j-simple', commons-logging]\n" +
                '  - scope: testRuntimeOnly\n    coordinates: org.apache.commons:commons-lang3\n' +
                'build:\n  properties:\n    acme.text.enabled: true\n' +
                "  extensions:\n    tasks.bootRun: systemProperty 'acme.text.console', 'true'\n" +
                "    assets: minifyJs = false\n" +
                'configuration:\n  acme.text.enabled: true\n  environments:\n    production:\n      acme.text.cache: 30s\n' +
                "files:\n  - from: audit.txt\n    to: 'grails-app/conf/{packagePath}/text.txt'\n" +
                "  - from: Text.groovy.gsp\n    to: 'src/main/groovy/{packagePath}/{className}Text.groovy'\n")
        new File(pack, 'templates/Text.groovy.gsp').setText('package ${project.packageName}\n\nimport org.apache.commons.lang3.StringUtils\n\n' +
                'class ${project.className}Text {\n<% if (features.contains("gsp")) { %>\n    static final boolean VIEWS = true\n<% } %>\n' +
                '    static String shout(String text) {\n        StringUtils.upperCase(text)\n    }\n}\n', 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--inplace', '--features', 'acme-text',
                '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 0
        new File(dir, 'build.gradle').text.contains(
                'implementation("org.apache.commons:commons-lang3") { exclude group: "org.slf4j", module: "slf4j-simple"; exclude group: "commons-logging" }')
        new File(dir, 'src/main/groovy/com/example/ShopText.groovy').text.contains('class ShopText {\n    static final boolean VIEWS = true\n')

        and: 'what of the pack is code is said to be'
        out.toString().contains('    templates, which are code that runs when the application is generated: Text.groovy.gsp')
        out.toString().contains('    build file blocks, which are code of the build: tasks.bootRun, assets')
        out.toString().contains('    build properties: acme.text.enabled=true')
        out.toString().contains('    configuration: 2 settings')

        when: 'Gradle configures the build, resolves what it declares and compiles the application'
        System.setOut(originalOut)
        System.setErr(originalErr)
        executeGradleCommand('classes dependencies --configuration runtimeClasspath --console=plain')

        then:
        testOutputContains('org.apache.commons:commons-lang3')
        testOutputContains('BUILD SUCCESSFUL')

        and: 'the class the template of the pack wrote is compiled'
        new File(dir, 'build/classes/groovy/main/com/example/ShopText.class').exists()
    }

    private File pack(String directory, String descriptor) {
        File pack = new File(packs, directory)
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText(descriptor, 'UTF-8')
        new File(pack, 'templates/audit.txt').setText('kept as it is', 'UTF-8')
        new File(pack, 'templates/base.txt').setText('kept as it is', 'UTF-8')
        return pack
    }
}

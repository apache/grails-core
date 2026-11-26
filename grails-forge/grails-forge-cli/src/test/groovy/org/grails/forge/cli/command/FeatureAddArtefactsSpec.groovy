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
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.console.ProjectTools
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import spock.lang.Unroll

/**
 * A feature pack that gives the command line commands which create classes, added to an application
 * that is there: the templates are written into the application, its record names the commands, and
 * the commands are there, as in an application that was created with the pack.
 */
class FeatureAddArtefactsSpec extends CommandSpec implements CommandFixture {

    static final String IN_PROJECT = PackArtefactCommandsSpec.IN_PROJECT

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File home
    String previousHome
    File app

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        work = Files.createTempDirectory('grailsforgeaddartefacts').toFile()
        home = new File(work, 'home')
        home.mkdirs()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        app = new File(work, 'app')
        app.mkdirs()
    }

    void cleanup() {
        ProjectTools.setListing(null)
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        work.deleteDir()
    }

    @Unroll
    void "the pack of a #from that is added writes its templates, records its commands, and the commands create classes"() {
        given:
        assert run(app, 'create-app', 'com.example.shop', '--inplace') == 0
        List<String> pack = from == 'directory' ? ['acme-audit', '--feature-pack-dir', directoryPack().absolutePath] :
                [PackArtefactCommandsSpec.PLUGIN, '--feature-pack-repository', repository().toPath().toAbsolutePath().normalize().toUri().toString()]
        List<String> again = pack.drop(1)

        when:
        int exit = run(app, (['feature', 'add'] + pack) as String[])

        then: 'the templates are files of the application and of its record, and the lock lists the commands'
        assert exit == 0, "${out}\n${err}"
        PackArtefactCommandsSpec.TEMPLATES.every { String path, String text -> new File(app, IN_PROJECT + path).getText('UTF-8') == text }
        PackArtefactCommandsSpec.TEMPLATES.every { String path, String text -> new File(app, '.grails/baseline/files/' + IN_PROJECT + path).getText('UTF-8') == text }
        new File(app, '.grails/forge.lock.yml').text.contains('  artefacts:\n  - name: audit-listener\n')
        out.toString().contains('create-audit-listener, create-audit-report, create-domain-check')

        and: 'the application is what its record generates'
        run(app, (['project', 'regenerate', '--diff'] + again) as String[]) == 0

        when: 'a command of the feature is run, on a command line that has no project generator'
        ForgeContext context = new ForgeContext()
        exit = inDirectory(app) { ForgeCli.execute(context, 'create-audit-listener', 'order') }

        then:
        exit == 0
        !context.isStarted()
        new File(app, 'grails-app/services/com/example/OrderAuditListener.groovy').text.startsWith('package com.example\n\nclass OrderAuditListener {')
        new File(app, 'src/test/groovy/com/example/OrderAuditListenerSpec.groovy').isFile()

        when: 'the feature is shown'
        exit = run(app, (['feature', 'show', 'acme-audit', '--json'] + again) as String[])
        Map document = new JsonSlurper().parseText(out.toString().readLines().last()) as Map

        then: 'with the commands it gives'
        exit == 0
        ((document.feature as Map).commands as List<Map>)*.name == ['create-audit-listener', 'create-audit-report', 'create-domain-check']
        ((document.feature as Map).commands as List<Map>).every { Map command -> command.containsKey('description') }

        when:
        exit = run(app, (['feature', 'show', 'acme-audit'] + again) as String[])

        then:
        exit == 0
        out.toString().contains('* Commands it gives the command line of an application that has it:')
        out.toString().readLines().any { String line -> line.startsWith('    create-audit-report') }

        where:
        from << ['directory', 'jar']
    }

    void "a second pack that gives a command the application has of another is refused, and nothing is written"() {
        given: 'an application with the pack, and another pack that gives one of its commands'
        File first = directoryPack()
        assert run(app, 'create-app', 'com.example.shop', '--inplace') == 0
        assert run(app, 'feature', 'add', 'acme-audit', '--feature-pack-dir', first.absolutePath) == 0
        File second = new File(work, 'other')
        new File(second, 'templates/artefacts').mkdirs()
        new File(second, 'feature.yml').setText('format: 1\nkind: feature\ntemplateApi: 1\nname: other-audit\n' +
                "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
                'artefacts:\n  - name: audit-report\n    description: Another report\n    template: artefacts/Other.groovy\n' +
                "    to: 'src/main/groovy/{packagePath}/{className}Other.groovy'\n", 'UTF-8')
        new File(second, 'templates/artefacts/Other.groovy').setText(PackArtefactCommandsSpec.REPORT, 'UTF-8')
        Map<String, String> before = tree(app)

        when:
        int exit = run(app, 'feature', 'add', 'other-audit', '--feature-pack-dir', first.absolutePath, '--feature-pack-dir', second.absolutePath)

        then:
        exit != 0
        err.toString().contains('audit-report')
        tree(app) == before
    }

    void "the record project migrate writes anew keeps the commands of a pack that was added to an application of Grails 8"() {
        given:
        File pack = directoryPack()
        EarlierApplication.copy(EarlierApplication.GRAILS_8_FORGE, app)
        EarlierApplication.upgrade(app)
        assert run(app, 'project', 'migrate') == 0
        assert run(app, 'feature', 'add', 'acme-audit', '--feature-pack-dir', pack.absolutePath) == 0

        when:
        int exit = run(app, 'project', 'migrate', '--force', '--feature-pack-dir', pack.absolutePath)
        ProjectState state = ProjectState.read(app.toPath())

        then:
        assert exit == 0, "${out}\n${err}"
        state.manifest.generationState == ProjectManifest.ADOPTED
        state.lock.features.find { it.name == 'acme-audit' }.artefacts*.name == ['audit-listener', 'audit-report', 'domain-check']
        state.verifyBaseline(app.toPath())

        when:
        exit = inDirectory(app) { ForgeCli.execute(new ForgeContext(), 'create-audit-report', 'order') }

        then:
        exit == 0
    }

    private int run(File directory, String... arguments) {
        return inDirectory(directory) { ForgeCli.execute(beanContext, arguments) }
    }

    private int inDirectory(File directory, Closure<Integer> run) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            out.reset()
            err.reset()
            return run.call()
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private File directoryPack() {
        File directory = new File(work, 'pack')
        new File(directory, 'templates/artefacts').mkdirs()
        new File(directory, 'feature.yml').setText(PackArtefactCommandsSpec.DESCRIPTOR, 'UTF-8')
        PackArtefactCommandsSpec.TEMPLATES.each { String path, String text -> new File(directory, 'templates/' + path).setText(text, 'UTF-8') }
        return directory
    }

    private File repository() {
        File repository = new File(work, 'repository')
        File directory = new File(repository, 'com/acme/acme-audit-grails/2.3.0')
        directory.mkdirs()
        new ZipOutputStream(new FileOutputStream(new File(directory, 'acme-audit-grails-2.3.0.jar'))).withCloseable { ZipOutputStream zip ->
            (['META-INF/grails-forge/feature.yml': PackArtefactCommandsSpec.DESCRIPTOR] + PackArtefactCommandsSpec.TEMPLATES.collectEntries { String path, String text ->
                ['META-INF/grails-forge/templates/' + path, text]
            }).each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return repository
    }

    private static Map<String, String> tree(File project) {
        Map<String, String> entries = new TreeMap<>()
        Files.walk(project.toPath()).withCloseable { stream ->
            stream.each { Path path ->
                String name = project.toPath().relativize(path).join('/')
                if (!name.startsWith('.grails/transactions') && !name.startsWith('.grails/cache') && Files.isRegularFile(path)) {
                    entries.put(name, MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString())
                }
            }
        }
        return entries
    }
}

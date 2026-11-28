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
import java.util.concurrent.TimeUnit
import groovy.json.JsonSlurper
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.SpecWaits
import org.grails.forge.util.VersionInfo
import spock.lang.Requires

/**
 * What becomes of the record of a project under {@code .grails} while the project is worked on: a
 * merge of two branches that both added a feature, a project that was moved to another version of
 * Grails by hand, whose record {@code project migrate} moves to that version, a generated dependency
 * that was removed by hand, and a record that another build of the same version wrote. Each is walked
 * through with the commands a user runs.
 */
class ProjectStateLifecycleSpec extends CommandSpec {

    private static final List<String> RECORD = ['.grails/project.yml', '.grails/forge.lock.yml', '.grails/baseline/index.yml']

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    File home
    String previousHome

    /** The application, created in the directory of the spec */
    File project

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        project = new File(dir, 'shop')
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        home.deleteDir()
    }

    @Requires({ ProjectStateLifecycleSpec.hasGit() })
    void "after a merge of two branches that each added a feature, the record of one side and feature add for the features of the other give the record of both"() {
        given: 'an application in a repository, and two branches of it that each add a feature'
        create(dir, 'create-app', 'com.example.shop')
        git('init', '-q')
        commitAll('Create the application')
        git('checkout', '-q', '-b', 'quartz')
        assert run('feature', 'add', 'grails-quartz') == 0
        commitAll('Add grails-quartz')
        git('checkout', '-q', 'main')
        git('checkout', '-q', '-b', 'cache')
        assert run('feature', 'add', 'cache') == 0
        new File(project, 'src/main/groovy/com/example').mkdirs()
        new File(project, 'src/main/groovy/com/example/Prices.groovy').text = 'package com.example\n\nclass Prices {\n}\n'
        commitAll('Add cache, and a class')
        git('checkout', '-q', 'quartz')
        out.reset()
        err.reset()

        when: 'the one is merged into the other'
        int merged = gitExit('merge', '-q', 'cache')

        then: 'the files of the record conflict, as the lock always does when both branches changed it, and so does a file both features changed'
        merged != 0
        conflicts() == ['.grails/baseline/files/README.md', '.grails/baseline/index.yml', '.grails/forge.lock.yml', '.grails/project.yml', 'README.md']

        when: 'a command reads the record as the merge left it'
        int shown = run('project', 'show')

        then: 'it says what is wrong with the record and how to have one again, and nothing of a digest to change'
        shown == 1
        err.toString().contains('.grails/project.yml has the markers of a merge conflict in it (line ')
        err.toString().contains('take them as one side of the merge committed them, as git restore --source=HEAD --staged --worktree -- .grails does for this branch, ' +
                'resolve the conflicts of the other files, and then add the features the other side added with feature add')

        when: 'the record of this branch is taken, a file whose conflict is between the lines of the two features is taken as this branch has it, ' +
                'and the features the other branch asked for are added'
        err.reset()
        git('restore', '--source=HEAD', '--staged', '--worktree', '--', '.grails')
        git('checkout', '--ours', '--', 'README.md')
        git('add', 'README.md')
        List<String> theirs = requested(git('show', 'MERGE_HEAD:.grails/project.yml'))
        out.reset()
        List<String> lacking = theirs - ProjectState.read(project.toPath()).manifest.requestedFeatures*.name
        int added = run((['feature', 'add'] + lacking) as String[])

        then: 'what the merge brought is kept, and what the other feature writes where this branch had its own lines is written'
        theirs == ['cache']
        added == 0
        out.toString().contains('    update   README.md')
        out.toString().contains('    keep     build.gradle (it is what the feature makes it already)')
        out.toString().contains('    keep     grails-app/conf/application.yml (it is what the feature makes it already)')

        and: 'the record is that of an application created with both features, and the project is that application but for the class of the other branch'
        File both = new File(dir, 'both')
        both.mkdirs()
        create(both, 'create-app', 'com.example.shop', '--features', 'cache,grails-quartz') == 0
        RECORD.every { new File(project, it).bytes == new File(both, "shop/${it}").bytes }
        files(project, '.grails/baseline') == files(new File(both, 'shop'), '.grails/baseline')
        regenerate() == 1
        out.toString().readLines().first() == 'added    src/main/groovy/com/example/Prices.groovy'

        when: 'the merge is committed'
        commitAll('Merge cache')

        then: 'nothing is left to commit, and the whole record reads'
        git('status', '--porcelain').isEmpty()
        ProjectState.read(project.toPath()).verifyBaseline(project.toPath())
    }

    @Requires({ ProjectStateLifecycleSpec.hasGit() })
    void "a merge in which one branch changed the record and the other did not gives a record that reads, with nothing to do"() {
        given:
        create(dir, 'create-app', 'com.example.shop')
        git('init', '-q')
        commitAll('Create the application')
        git('checkout', '-q', '-b', 'quartz')
        assert run('feature', 'add', 'grails-quartz') == 0
        commitAll('Add grails-quartz')
        git('checkout', '-q', 'main')
        File controller = new File(project, 'grails-app/controllers/com/example/ShopController.groovy')
        controller.parentFile.mkdirs()
        controller.text = 'package com.example\n\nclass ShopController {\n}\n'
        commitAll('Add a controller')
        out.reset()

        when:
        int merged = gitExit('merge', '-q', 'quartz')

        then:
        merged == 0
        ProjectState.read(project.toPath()).manifest.requestedFeatures*.name == ['grails-quartz']
        regenerate() == 1
        out.toString().readLines().first() == 'added    grails-app/controllers/com/example/ShopController.groovy'
    }

    void "a project that was moved to another version of Grails by hand keeps the record of the version that generated it until project migrate moves it, and each command says what it does with it"() {
        given: 'an application whose record an earlier version wrote, and whose build was moved to this version by hand'
        create(dir, 'create-app', 'com.example.shop', '--features', 'grails-quartz')
        writtenBy('9.0.0-M1', 'ab' * 32)
        Map<String, String> record = recordOf(project)

        when: 'project show'
        int shown = run('project', 'show')
        List<String> lines = out.toString().readLines()

        then: 'shows how the application was generated, says that the project is on another version now, and names the command that moves the record'
        shown == 0
        lines.contains('    generated by  grails-forge 9.0.0-M1')
        lines.contains("    build         generator sha256 ${'ab' * 6}".toString())
        lines.contains(("| The project is on Grails ${VersionInfo.grailsVersion} (gradle.properties), and the record is of Grails 9.0.0-M1: it says how the " +
                'application was generated, and was not changed when the project moved to another version. grails-forge-cli project migrate moves it to this ' +
                'version of the command line, after which project regenerate and feature add run with it').toString())
        lines[-1] == 'grails-forge-cli create-app com.example.shop --features grails-quartz'

        when: 'project regenerate'
        out.reset()
        int regenerated = run('project', 'regenerate', '--diff')

        then: 'is refused, with the command that moves the record'
        regenerated == 1
        err.toString().contains("The application was generated by grails-forge 9.0.0-M1 for Grails 9.0.0-M1, and this is grails-forge ${VersionInfo.version}")
        err.toString().contains('its record is moved to this version first. Nothing was changed. grails-forge-cli project migrate moves the record to this version, ' +
                'from the choices it has, and changes no file of the application outside .grails')

        when: 'feature add'
        err.reset()
        int added = run('feature', 'add', 'cache')

        then: 'is refused in the same words, and changes nothing'
        added == 1
        err.toString().contains("The application was generated by grails-forge 9.0.0-M1 for Grails 9.0.0-M1, and this is grails-forge ${VersionInfo.version}")
        err.toString().contains('grails-forge-cli project migrate moves the record to this version')
        recordOf(project) == record

        when: 'the commands that create classes'
        err.reset()
        out.reset()
        int controller = run('create-controller', 'Book')
        int job = run('create-job', 'Nightly')

        then: 'work from the record, which has the package and the features'
        controller == 0
        job == 0
        new File(project, 'grails-app/controllers/com/example/BookController.groovy').file
        new File(project, 'grails-app/jobs/com/example/NightlyJob.groovy').file

        when: 'project migrate'
        err.reset()
        out.reset()
        Map<String, String> application = files(project, '').findAll { !it.key.startsWith('.grails/') }
        int migrated = run('project', 'migrate')

        then: 'moves the record to this version, from the choices it has, and changes no file of the application'
        migrated == 0
        err.toString().isEmpty()
        out.toString().contains('| The record was moved to this version')
        files(project, '').findAll { !it.key.startsWith('.grails/') } == application

        and: 'the record is that of the application created with the same choices by this version'
        File again = new File(dir, 'again')
        again.mkdirs()
        create(again, 'create-app', 'com.example.shop', '--features', 'grails-quartz') == 0
        RECORD.every { new File(project, it).bytes == new File(again, "shop/${it}").bytes }

        when: 'the commands that generate the application again'
        out.reset()
        int compared = regenerate()
        int addedNow = run('feature', 'add', 'cache')

        then: 'run, as with an application this version generated: the classes that were created are files of yours'
        compared == 1
        out.toString().contains('added    grails-app/controllers/com/example/BookController.groovy')
        addedNow == 0
        new File(project, 'build.gradle').text.contains('implementation "org.apache.grails:grails-cache"')
    }

    void "a generated dependency that was removed by hand stays in the record, which says how the application was generated"() {
        given:
        create(dir, 'create-app', 'com.example.shop', '--features', 'grails-quartz')
        File build = new File(project, 'build.gradle')
        String generated = build.text
        String dependency = '    implementation "org.apache.grails:grails-quartz"\n'
        build.text = generated.replace(dependency, '')
        Map<String, String> record = recordOf(project)

        when:
        int shown = run('project', 'show')

        then: 'the feature is one the application was generated with'
        shown == 0
        out.toString().readLines().contains('    asked for     grails-quartz')

        when:
        out.reset()
        int regenerated = regenerate()

        then: 'the build file is one that was changed'
        regenerated == 1
        out.toString().readLines() == ['changed  build.gradle', '| 1 file is not as the application was generated: 1 changed']

        when: 'the feature is added again'
        out.reset()
        int again = run('feature', 'add', 'grails-quartz')

        then: 'nothing is changed: the application has it, by its record'
        again == 0
        out.toString().contains('The application already has the feature grails-quartz')
        build.text == generated.replace(dependency, '')
        recordOf(project) == record

        when: 'another feature is added'
        out.reset()
        int other = run('feature', 'add', 'cache')

        then: 'the dependency stays removed, as any change of the project stays'
        other == 0
        out.toString().contains('    merge    build.gradle (your changes are kept)')
        !build.text.contains(dependency)
        build.text.contains('    implementation "org.apache.grails:grails-cache"\n')

        when: 'the line is taken back from the copy the baseline has of the file'
        String copy = new File(project, '.grails/baseline/files/build.gradle').text
        build.text = copy
        out.reset()

        then:
        copy.contains(dependency)
        regenerate() == 0
    }

    void "a record that another build of the same version wrote is used where the two builds generate the same files, and names the build that wrote it"() {
        given:
        create(dir, 'create-app', 'com.example.shop')
        writtenBy(VersionInfo.version, 'ab' * 32)

        when:
        int shown = run('project', 'show')
        List<String> lines = out.toString().readLines()

        then: 'the build is shown, and that it is another one'
        shown == 0
        lines.contains("    build         generator sha256 ${'ab' * 6}  (another build than this one, which is ${VersionInfo.generatorSha256.take(12)})".toString())
        lines.contains(("| The record was written by another build of version ${VersionInfo.version} of the command line than this one (the generator sha256 " +
                "${'ab' * 32} there, ${VersionInfo.generatorSha256} here): the command gives the same application where the two builds generate the same files, " +
                'and project regenerate --diff says whether they do; grails-forge-cli project migrate moves the record to this version, from the choices it has, ' +
                'and changes no file of the application outside .grails').toString())

        when:
        out.reset()
        run('project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        document.generator == [name: 'grails-forge', version: VersionInfo.version, sha256: 'ab' * 32]

        when: 'this build generates the same files'
        out.reset()

        then: 'the project is generated again: the files are those of the record, and so is the record but for the build that wrote it'
        regenerate() == 0
        run('project', 'regenerate', '--output', new File(dir, 'again').absolutePath) == 0
        out.toString().contains('and its record under .grails is that of the project')
        ProjectState.read(new File(dir, 'again').toPath()).lock.generatorSha256 == VersionInfo.generatorSha256

        when: 'a feature is added'
        int added = run('feature', 'add', 'cache')

        then: 'it is, and the record now names this build, which wrote it'
        added == 0
        ProjectState.read(project.toPath()).lock.generatorSha256 == VersionInfo.generatorSha256
    }

    void "a record that another build of the same version wrote, which generates other files, is refused with both builds named"() {
        given: 'a record whose build wrote another README.md than this one'
        create(dir, 'create-app', 'com.example.shop')
        File readme = new File(project, 'README.md')
        String generated = readme.text
        String other = generated + '\nWritten by another build\n'
        readme.text = other
        new File(project, '.grails/baseline/files/README.md').text = other
        File index = new File(project, '.grails/baseline/index.yml')
        assert index.text.count(sha(generated.bytes)) == 1
        index.text = index.text.replace(sha(generated.bytes), sha(other.bytes))
        writtenBy(VersionInfo.version, 'ab' * 32)

        expect: 'the record reads, and is what that build wrote'
        ProjectState.read(project.toPath()).verifyBaseline(project.toPath())

        when:
        int regenerated = regenerate()

        then:
        regenerated == 1
        err.toString().contains('The application cannot be generated again as it was: 1 file is not what the baseline of the project has (README.md)')
        err.toString().contains("The record was written by another build of grails-forge ${VersionInfo.version} than this one (the generator sha256 ${'ab' * 32} there, " +
                "${VersionInfo.generatorSha256} here), which does not generate the same files: use the build that generated the application, or move the record to " +
                'this build. grails-forge-cli project migrate moves the record to this version')

        when:
        err.reset()
        int added = run('feature', 'add', 'cache')

        then:
        added == 1
        err.toString().contains("The record was written by another build of grails-forge ${VersionInfo.version} than this one")
    }

    /**
     * Makes the record one that another version or build wrote: the version of Grails in the manifest
     * and the lock, the version and the identity of the generator in the lock, with the digests that
     * bind them.
     */
    private void writtenBy(String version, String generatorSha256) {
        File manifest = new File(project, '.grails/project.yml')
        File lock = new File(project, '.grails/forge.lock.yml')
        File index = new File(project, '.grails/baseline/index.yml')
        boolean grails = version != VersionInfo.version
        if (grails) {
            manifest.text = manifest.text.replace("grailsVersion: ${VersionInfo.grailsVersion}\n", "grailsVersion: ${version}\n")
        }
        String body = lock.text.replace("  version: ${VersionInfo.version}\n  sha256: ${VersionInfo.generatorSha256}\n", "  version: ${version}\n  sha256: ${generatorSha256}\n")
        if (grails) {
            body = body.replace("\ngrailsVersion: ${VersionInfo.grailsVersion}\n", "\ngrailsVersion: ${version}\n")
        }
        body = body.replaceFirst('manifest:\n  sha256: [0-9a-f]{64}', "manifest:\n  sha256: ${sha(manifest.bytes)}")
                .replaceFirst('baselineIndex:\n  sha256: [0-9a-f]{64}', "baselineIndex:\n  sha256: ${sha(index.bytes)}")
        body = body.substring(0, body.lastIndexOf('seal:\n'))
        lock.text = body + "seal:\n  sha256: ${sha(body.bytes)}\n"
        assert ProjectState.read(project.toPath()).lock.generatorSha256 == generatorSha256
    }

    private int regenerate() {
        return run('project', 'regenerate', '--diff')
    }

    private static Map<String, String> recordOf(File project) {
        return RECORD.collectEntries { [(it): sha(new File(project, it).bytes)] }
    }

    // every file under a directory of a project, with the digest of its content
    private static Map<String, String> files(File project, String under) {
        Map<String, String> files = new TreeMap<>()
        Path root = project.toPath()
        Files.walk(root.resolve(under)).withCloseable { stream ->
            stream.filter { Files.isRegularFile(it) }.each { Path path -> files.put(root.relativize(path).join('/'), sha(Files.readAllBytes(path))) }
        }
        return files
    }

    // the features a manifest asks for
    private static List<String> requested(String manifest) {
        String section = manifest.substring(manifest.indexOf('requestedFeatures:\n'), manifest.indexOf('excludedFeatures:'))
        return section.readLines().findAll { it.startsWith('- name: ') }.collect { it.substring('- name: '.length()) }
    }

    private List<String> conflicts() {
        return git('diff', '--name-only', '--diff-filter=U').readLines().sort()
    }

    private void commitAll(String message) {
        git('add', '-A')
        git('commit', '-q', '-m', message)
    }

    private String git(String... arguments) {
        Process process = gitProcess(arguments)
        String text = process.inputStream.getText('UTF-8')
        assert process.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS) && process.exitValue() == 0: "git ${arguments.join(' ')}: ${text}"
        return text
    }

    private int gitExit(String... arguments) {
        Process process = gitProcess(arguments)
        process.inputStream.getText('UTF-8')
        assert process.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
        return process.exitValue()
    }

    // Git with none of the settings of the machine: a branch named main, no signing, the bytes of the files as they are
    private Process gitProcess(String... arguments) {
        List<String> command = ['git', '-c', 'user.name=Shop', '-c', 'user.email=shop@example.org', '-c', 'commit.gpgsign=false', '-c', 'core.autocrlf=false',
                                '-c', 'init.defaultBranch=main', '-c', 'merge.conflictStyle=merge'] + arguments.toList()
        ProcessBuilder builder = new ProcessBuilder(command).directory(project).redirectErrorStream(true)
        builder.environment().put('GIT_CONFIG_NOSYSTEM', '1')
        builder.environment().put('GIT_CONFIG_GLOBAL', new File(home, '.gitconfig').absolutePath)
        builder.environment().put('HOME', home.absolutePath)
        return builder.start()
    }

    static boolean hasGit() {
        try {
            Process process = new ProcessBuilder('git', '--version').redirectErrorStream(true).start()
            process.inputStream.getText('UTF-8')
            return process.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS) && process.exitValue() == 0
        } catch (IOException ignored) {
            return false
        }
    }

    // a create command, in the directory it is given
    private int create(File directory, String... arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            return ForgeCli.execute(beanContext, arguments)
        } finally {
            System.setProperty('user.dir', current)
            out.reset()
        }
    }

    // a command in the application
    private int run(String... arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', project.absolutePath)
        try {
            return ForgeCli.execute(beanContext, arguments)
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }
}

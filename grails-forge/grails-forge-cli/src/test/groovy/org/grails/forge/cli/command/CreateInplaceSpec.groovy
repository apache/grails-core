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

import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

/**
 * {@code --inplace} of the commands that create an application: the application is created in the
 * current directory and, when no name is given, named after that directory, as the Grails shell named it.
 */
@Issue('https://github.com/apache/grails-core/issues/14872')
class CreateInplaceSpec extends CommandSpec {

    static final String PRESET = '''\
        format: 1
        kind: preset
        name: acme-service
        templateApi: 1
        compatibility:
          grails: '[9.0.0,10.0.0)'
          forge: '[9.0.0,10.0.0)'
        title: Acme Service
        description: How Acme makes a service
        applicationType: rest-api
        options: { data: mongodb }
        files:
          - from: TEAM.md.gsp
            to: TEAM.md
        '''.stripIndent()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    File home
    String previousHome
    File work

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        // where a change of a project and the copies of catalogs are kept
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        work = dir.canonicalFile
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        home.deleteDir()
    }

    @Unroll
    void "create-app --inplace without a name creates the application in the directory #directory, named #name in the package #packageName"() {
        given:
        File app = newDirectory(directory)

        when:
        int exit = run(app, 'create-app', '--inplace')
        ProjectState state = ProjectState.read(app.toPath())
        state.verifyBaseline(app.toPath())

        then: 'the application is the current directory, and no directory is made in it'
        exit == 0
        said().contains("| Application created at ${app.path}")
        new File(app, "grails-app/init/${packageName.replace('.', '/')}/Application.groovy").getText('UTF-8').contains("package ${packageName}")
        new File(app, 'build.gradle').file
        !new File(app, name).exists()

        and: 'its record has the name of the directory, and the package a name without one is given'
        state.manifest.name == name
        state.manifest.defaultPackage == packageName
        state.manifest.applicationType == ApplicationType.WEB

        and: 'which is the package the same name given on the command line gets'
        File other = newDirectory('other')
        run(other, 'create-app', name) == 0
        ProjectState.read(new File(other, name).toPath()).manifest.defaultPackage == packageName

        where:
        directory | name     | packageName
        'shop'    | 'shop'   | 'shop'
        'my-app'  | 'my-app' | 'my.app'
        'MyApp'   | 'MyApp'  | 'MyApp'
        'my_app'  | 'my_app' | 'my_app'
    }

    @Unroll
    void "#command --inplace without a name creates #type in the current directory, named after it"() {
        given:
        File app = newDirectory('my-shop')

        when:
        int exit = run(app, command, '--inplace')
        ProjectState state = ProjectState.read(app.toPath())

        then:
        exit == 0
        state.manifest.applicationType == type
        state.manifest.name == 'my-shop'
        state.manifest.defaultPackage == 'my.shop'
        new File(app, 'grails-app/init/my/shop/Application.groovy').file
        new File(app, 'src/main/groovy/my/shop/MyShopGrailsPlugin.groovy').file == plugin
        !plugin || new File(app, 'src/main/groovy/my/shop/MyShopGrailsPlugin.groovy').getText('UTF-8').contains('class MyShopGrailsPlugin extends Plugin')
        !new File(app, 'my-shop').exists()

        where:
        command                  | type                            | plugin
        'create-app'             | ApplicationType.WEB             | false
        'create-webapp'          | ApplicationType.WEB             | false
        'create-restapi'         | ApplicationType.REST_API        | false
        'create-plugin'          | ApplicationType.PLUGIN          | true
        'create-web-plugin'      | ApplicationType.WEB_PLUGIN      | true
        'create-rest-api-plugin' | ApplicationType.REST_API_PLUGIN | true
    }

    void "--inplace with a name creates the application of that name in the current directory, whatever the directory is called"() {
        given:
        File app = newDirectory('bookstore.demo')

        when:
        int exit = run(app, 'create-app', '--inplace', 'com.example.shop')
        ProjectState state = ProjectState.read(app.toPath())

        then:
        exit == 0
        state.manifest.name == 'shop'
        state.manifest.defaultPackage == 'com.example'
        new File(app, 'grails-app/init/com/example/Application.groovy').file
        !new File(app, 'shop').exists()
    }

    @Unroll
    void "a directory whose name an application cannot have is refused by --inplace without a name, which says to give the name: #what"() {
        given:
        File app = newDirectory(directory)

        when:
        int exit = run(app, 'create-app', '--inplace')

        then: 'a mistake in the command line, which names the directory, says why and how to give the name, and writes nothing'
        exit == 2
        said().contains("With --inplace and no name the application is named after the current directory, ${app.path}, " +
                "and its name is not one an application can have: ${why}. Give the name: grails-forge-cli create-app --inplace NAME")
        said().contains('Usage:')
        app.list().length == 0

        where:
        what                     | directory        | why
        'a space'                | 'my shop'        | "it has a character that the name of a class cannot have: only letters, digits, '_' and '-' between words"
        'another character'      | 'shop+app'       | "it has a character that the name of a class cannot have: only letters, digits, '_' and '-' between words"
        'a dot'                  | 'bookstore.demo' | 'it has a dot, which in the name of an application ends its package'
        'no package to be made'  | '1shop'          | 'Cannot create a valid package name for [1shop]. Please specify a name that is also a valid Java package'
        'a name kept by Grails'  | 'micronaut'      | '"micronaut" is not a valid app name'
    }

    void "the root of a file system has no name, so --inplace without a name is refused there"() {
        given:
        File root = work.toPath().getRoot().toFile()

        when: 'with a version of Java there is not, which is refused before anything is written: should the name be taken, nothing is written into the root all the same'
        int exit = run(root, 'create-app', '--inplace', '--jdk', '1')

        then:
        exit == 2
        said().contains("With --inplace and no name the application is named after the current directory, ${root.path}, " +
                'and its name is not one an application can have: the root of a file system has no name. Give the name: grails-forge-cli create-app --inplace NAME')
    }

    @IgnoreIf({ os.windows })
    void "the directory --inplace without a name names the application after is the one a symbolic link leads to"() {
        given:
        File real = newDirectory('real-shop')
        File link = Files.createSymbolicLink(new File(work, 'linked-shop').toPath(), real.toPath()).toFile()

        when:
        int exit = run(link, 'create-app', '--inplace')

        then:
        exit == 0
        ProjectState.read(real.toPath()).manifest.name == 'real-shop'
        ProjectState.read(real.toPath()).manifest.defaultPackage == 'real.shop'
        new File(real, 'grails-app/init/real/shop/Application.groovy').file
    }

    void "without --inplace and without a name, the command says to give one or to use --inplace"() {
        when:
        int exit = run(work, 'create-app')

        then:
        exit == 2
        said().contains('Specify an application name or use --inplace to create an application in the current directory')
        work.list().length == 0
    }

    void "--list-features and --help with --inplace create nothing, also in a directory whose name an application cannot have"() {
        given:
        File app = newDirectory('my shop')

        when:
        int listed = run(app, 'create-app', '--inplace', '--list-features')

        then:
        listed == 0
        said().contains('asciidoctor')
        app.list().length == 0

        when:
        int helped = run(app, 'create-app', '--inplace', '--help')

        then: 'which says what the option does for an application'
        helped == 0
        said().contains('-i, --inplace Create the application in the current directory, and not in a new directory named after it. ' +
                'Without NAME, the application is named after the current directory.')
        app.list().length == 0
    }

    void "a directory that has files keeps them, and create commands have no --force to replace them"() {
        given:
        File app = newDirectory('shop')
        new File(app, 'build.gradle').setText('// mine\n', 'UTF-8')
        new File(app, 'notes.txt').setText('mine\n', 'UTF-8')

        when:
        int exit = run(app, 'create-app', '--inplace')
        ProjectState state = ProjectState.read(app.toPath())

        then: 'the application is created around them, named after the directory, and the command names the kept file that is not as generated'
        exit == 0
        state.manifest.name == 'shop'
        new File(app, 'build.gradle').getText('UTF-8') == '// mine\n'
        new File(app, 'notes.txt').getText('UTF-8') == 'mine\n'
        said().contains('1 file that the directory already had was kept and is not as generated: build.gradle')
        new File(app, 'grails-app/init/shop/Application.groovy').file

        when:
        byte[] lock = new File(app, '.grails/forge.lock.yml').bytes
        int forced = run(app, 'create-app', '--inplace', '--force')

        then: '--force is no option of a create command'
        forced == 2
        said().contains('create-app has no option --force')
        new File(app, 'build.gradle').getText('UTF-8') == '// mine\n'
        new File(app, '.grails/forge.lock.yml').bytes == lock

        when:
        int again = run(app, 'create-app', '--inplace')

        then: 'and the directory now has the record of an application, which is not generated over'
        again == 1
        said().contains('Cannot create the project because the directory already has the state of a generated project (.grails/project.yml)')
        new File(app, '.grails/forge.lock.yml').bytes == lock
    }

    void "create-app --subproject --inplace without a name makes the current directory the project, named after it"() {
        given:
        new File(work, 'settings.gradle').text = "rootProject.name = 'acme'\n"
        File shop = newDirectory('services/shop')

        when:
        int exit = run(shop, 'create-app', '--subproject', '--inplace')

        then:
        exit == 0
        out.toString().readLines().contains("    include 'services:shop'")
        ProjectState.read(shop.toPath()).manifest.name == 'shop'
        ProjectState.read(shop.toPath()).manifest.options.subproject
        new File(shop, 'build.gradle').file
        !new File(shop, 'gradlew').exists()
    }

    void "create-restapi --inplace --preset without a name names the application after the directory, with the options and files of the preset"() {
        given:
        File preset = new File(home, 'acme-service')
        new File(preset, 'templates').mkdirs()
        new File(preset, 'feature.yml').setText(PRESET, 'UTF-8')
        new File(preset, 'templates/TEAM.md.gsp').setText('# ${project.naturalName} in ${project.packageName}\n', 'UTF-8')
        File app = newDirectory('payments')

        when:
        int exit = run(app, 'create-restapi', '--inplace', '--preset', preset.absolutePath)
        ProjectState state = ProjectState.read(app.toPath())

        then:
        exit == 0
        state.manifest.name == 'payments'
        state.manifest.defaultPackage == 'payments'
        state.manifest.preset == 'acme-service'
        new File(app, 'TEAM.md').getText('UTF-8') == '# Payments in payments\n'
        new File(app, 'build.gradle').getText('UTF-8').contains('implementation "org.apache.grails:grails-data-mongodb"')
    }

    void "project show of an application created with --inplace and no name prints a command that creates the same application again"() {
        given:
        File app = newDirectory('in-place/my-shop')
        assert run(app, 'create-app', '--inplace', '--servlet', 'jetty', '--features', 'grails-quartz') == 0

        when:
        int shown = run(app, 'project', 'show')
        String line = out.toString().readLines().last()

        then: 'the name with its package, which recreates the application wherever it is run'
        shown == 0
        line == 'grails-forge-cli create-app my.shop.my-shop --servlet jetty --features grails-quartz'

        when:
        File elsewhere = newDirectory('elsewhere')
        int recreated = run(elsewhere, line.split(' ').drop(1) as String[])

        then: 'the same files, the record among them'
        recreated == 0
        filesOf(new File(elsewhere, 'my-shop')) == filesOf(app)

        when: 'and --inplace without a name, in another directory of that name'
        File again = newDirectory('again/my-shop')
        int inPlace = run(again, 'create-app', '--inplace', '--servlet', 'jetty', '--features', 'grails-quartz')

        then:
        inPlace == 0
        filesOf(again) == filesOf(app)
    }

    private File newDirectory(String path) {
        File directory = new File(work, path)
        assert directory.mkdirs()
        return directory
    }

    private int run(File directory, String... arguments) {
        System.setProperty('user.dir', directory.absolutePath)
        out.reset()
        return ForgeCli.execute(beanContext, arguments)
    }

    // without the line breaks a narrow usage width could put into a message
    private String said() {
        return out.toString().replaceAll(/\s+/, ' ')
    }

    private static Map<String, String> filesOf(File directory) {
        Map<String, String> files = new TreeMap<>()
        Path base = directory.toPath()
        Files.walk(base).withCloseable { stream ->
            stream.filter { Path path -> Files.isRegularFile(path) }.each { Path path ->
                String name = base.relativize(path).join('/')
                if (!name.startsWith('.grails/transactions')) {
                    files.put(name, Files.readAllBytes(path).encodeHex().toString())
                }
            }
        }
        return files
    }
}

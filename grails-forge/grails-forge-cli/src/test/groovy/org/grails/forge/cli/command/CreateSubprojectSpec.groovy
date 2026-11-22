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
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Unroll

/**
 * Creating an application as a project of a Gradle build there is already, and what the commands of
 * that application write then. Nothing here runs Gradle.
 */
class CreateSubprojectSpec extends CommandSpec {

    static final List<String> OF_BUILD_ROOT = ['gradlew', 'gradlew.bat', 'gradle', 'settings.gradle', 'settings.gradle.kts', 'grailsw', 'grailsw.bat',
                                               'grails-wrapper.jar', '.gitattributes']

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    File home
    String previousHome
    File root

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        // the journal of a change is kept in the home directory of whoever runs the command
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        root = dir.canonicalFile
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        home.deleteDir()
    }

    @Unroll
    void "create-app --subproject below a build with #settings writes the files of the application only and says #line"() {
        given:
        new File(root, settings).text = "rootProject.name = 'acme'\n"
        File apps = new File(root, 'apps')
        apps.mkdirs()
        Map<String, byte[]> before = filesOf(root)

        when:
        int exit = run(apps, 'create-app', '--subproject', 'example.shop')
        File shop = new File(apps, 'shop')

        then:
        exit == 0
        said().contains("| Application created at ${shop.path}")
        said().contains("Add this line to ${new File(root, settings).path}:")
        out.toString().readLines().contains('    ' + line)
        said().contains('Its tasks are then those of the project :apps:shop, run with the Gradle wrapper of the build')

        and: 'nothing that was there is changed: the settings are the user\'s to change'
        before.every { String path, byte[] content -> new File(root, path).bytes == content }

        and: 'the application has its own files, and none of the root of a build'
        OF_BUILD_ROOT.every { String name -> !new File(shop, name).exists() }
        new File(shop, 'build.gradle').file
        new File(shop, '.gitignore').file
        new File(shop, 'grails-app/conf/application.yml').file
        new File(shop, 'gradle.properties').readLines().any { String property -> property.startsWith('grailsVersion=') }
        !new File(shop, 'gradle.properties').text.contains('org.gradle.')

        and: 'the state says so, reads, and has a baseline of those files'
        new File(shop, '.grails/project.yml').text.contains('\n  layout: subproject\n')
        ProjectState.read(shop.toPath()).manifest.options.subproject
        ProjectState.read(shop.toPath()).verifyBaseline(shop.toPath()) == null
        ProjectState.read(shop.toPath()).baseline.files*.path.every { String path -> !ProjectState.isOfBuildRoot(path) }

        where:
        settings              | line
        'settings.gradle'     | "include 'apps:shop'"
        'settings.gradle.kts' | 'include("apps:shop")'
    }

    void "create-app --subproject in the root of a build includes the application by its name"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"

        expect:
        run(root, 'create-app', '--subproject', 'example.shop') == 0
        out.toString().readLines().contains("    include 'shop'")
        !new File(root, 'shop/gradlew').exists()
    }

    void "create-app --subproject says so when the directories give no path of a project"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"
        File apps = new File(root, 'our apps')
        apps.mkdirs()

        expect:
        run(apps, 'create-app', '--subproject', 'example.shop') == 0
        said().contains("Include its directory in ${new File(root, 'settings.gradle').path}, under a name Gradle takes for a project")
        !said().contains('include \'')
        !new File(apps, 'shop/gradlew').exists()
    }

    void "create-app --subproject is refused where there is no build above, and writes nothing"() {
        when:
        int exit = run(root, 'create-app', '--subproject', 'example.shop')

        then:
        exit == 2
        said().contains('--subproject creates the application as a project of an existing Gradle build, and there is no settings.gradle or settings.gradle.kts in a directory above')
        root.list().length == 0
    }

    void "create-app --subproject --inplace is refused in the root of a build, and writes nothing"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"

        when:
        int exit = run(root, 'create-app', '--subproject', '--inplace', 'example.shop')

        then:
        exit == 2
        said().contains('has the settings of a build itself: it is the root of one')
        root.list() as List<String> == ['settings.gradle']
    }

    void "create-app --subproject --inplace makes the current directory the project"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"
        File shop = new File(root, 'services/shop')
        shop.mkdirs()

        expect:
        run(shop, 'create-app', '--subproject', '--inplace', 'example.shop') == 0
        out.toString().readLines().contains("    include 'services:shop'")
        new File(shop, 'build.gradle').file
        !new File(shop, 'gradlew').exists()
    }

    void "the settings of a build are not a feature of an application that is created with --subproject"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"

        when:
        int exit = run(root, 'create-app', '--subproject', '--features', 'gradle-settings-file', 'example.shop')

        then:
        exit == 2
        said().contains('The feature gradle-settings-file writes the settings of a Gradle build')
        !new File(root, 'shop/build.gradle').exists()
        !new File(root, 'shop/.grails').exists()
    }

    void "an application that is created inside a build without --subproject is whole, and the command says what Gradle makes of it"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"

        when:
        int exit = run(root, 'create-app', 'example.shop')

        then:
        exit == 0
        new File(root, 'shop/gradlew').file
        !new File(root, 'shop/.grails/project.yml').text.contains('layout')
        said().contains("| Warning ${new File(root, 'shop').path} is inside the Gradle build of ${root.path} and has no settings of its own")
        said().contains('is created with --subproject')

        when: 'it has settings of its own'
        out.reset()
        exit = run(root, 'create-app', '--features', 'gradle-settings-file', 'example.admin')

        then:
        exit == 0
        new File(root, 'admin/settings.gradle').file
        !said().contains('| Warning')
    }

    void "an application outside a build is created as before, with no word of builds"() {
        when:
        int exit = run(root, 'create-app', 'example.shop')

        then:
        exit == 0
        new File(root, 'shop/gradlew').file
        !said().contains('Warning')
        !said().contains('include')
    }

    void "feature add in an application that is a project of a build keeps it one"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"
        File apps = new File(root, 'apps')
        File with = new File(root, 'with')
        apps.mkdirs()
        with.mkdirs()
        assert run(apps, 'create-app', '--subproject', 'example.shop') == 0
        assert run(with, 'create-app', '--subproject', '--features', 'grails-quartz', 'example.shop') == 0
        File shop = new File(apps, 'shop')

        when: 'from a directory below the project'
        out.reset()
        int exit = run(new File(shop, 'grails-app/conf'), 'feature', 'add', 'grails-quartz')

        then:
        exit == 0
        said().contains('Feature grails-quartz added')
        new File(shop, 'build.gradle').text.contains('grails-quartz')

        and: 'it is the application that is created with the feature, file for file, the state too'
        filesOf(shop).keySet() == filesOf(new File(with, 'shop')).keySet()
        filesOf(shop).every { String path, byte[] content -> path.startsWith('.grails/transactions') || new File(with, "shop/${path}").bytes == content }
        OF_BUILD_ROOT.every { String name -> !new File(shop, name).exists() }
        ProjectState.read(shop.toPath()).manifest.options.subproject
        ProjectState.read(shop.toPath()).verifyBaseline(shop.toPath()) == null

        when: 'the kind of application the state names is not one there is'
        File manifest = new File(shop, '.grails/project.yml')
        manifest.text = manifest.text.replace('layout: subproject', 'layout: nested')
        out.reset()
        exit = run(shop, 'feature', 'add', 'grails-web-console')

        then:
        exit == 1
        said().contains('layout must be one of subproject')
    }

    void "project regenerate writes such an application again with its own files only"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"
        File apps = new File(root, 'apps')
        apps.mkdirs()
        assert run(apps, 'create-app', '--subproject', 'example.shop') == 0
        File shop = new File(apps, 'shop')
        File again = new File(root, 'again')

        when:
        out.reset()
        int exit = run(new File(shop, 'grails-app'), 'project', 'regenerate', '--output', again.path)

        then:
        exit == 0
        filesOf(again) == filesOf(shop)
        OF_BUILD_ROOT.every { String name -> !new File(again, name).exists() }

        expect: 'and the application is as it was generated'
        run(shop, 'project', 'regenerate', '--diff') == 0

        when: 'the record is shown'
        out.reset()
        exit = run(shop, 'project', 'show')

        then: 'the command that creates it again has the option, which is not the default'
        exit == 0
        said().contains(' --subproject yes (differs from the current default: no) ')
        out.toString().readLines().last() == 'grails-forge-cli create-app example.shop --subproject'
    }

    void "the commands that write classes write into the project of the build they are run below"() {
        given:
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n"
        File apps = new File(root, 'apps/web')
        apps.mkdirs()
        assert run(apps, 'create-app', '--subproject', 'example.shop') == 0
        File shop = new File(apps, 'shop')

        when:
        out.reset()
        int exit = run(new File(shop, 'grails-app/conf'), 'create-controller', 'Book')

        then:
        exit == 0
        new File(shop, 'grails-app/controllers/example/BookController.groovy').file
        new File(shop, 'src/test/groovy/example/BookControllerSpec.groovy').file
        !new File(root, 'grails-app').exists()

        when:
        out.reset()
        exit = run(new File(shop, 'src'), 'help', '--json')

        then:
        exit == 0
        // as JSON writes it: the backslash of a path of Windows is written twice
        out.toString().contains("\"projectDirectory\":\"${shop.path.replace('\\', '\\\\')}\"")
    }

    private int run(File directory, String... arguments) {
        System.setProperty('user.dir', directory.absolutePath)
        return ForgeCli.execute(beanContext, arguments)
    }

    // without the line breaks a narrow usage width could put into a message
    private String said() {
        return out.toString().replaceAll(/\s+/, ' ')
    }

    private static Map<String, byte[]> filesOf(File directory) {
        Map<String, byte[]> files = new TreeMap<>()
        Path base = directory.toPath()
        Files.walk(base).withCloseable { stream ->
            stream.filter { Path path -> Files.isRegularFile(path) }.each { Path path ->
                String name = base.relativize(path).join('/')
                if (!name.startsWith('.grails/transactions')) {
                    files.put(name, Files.readAllBytes(path))
                }
            }
        }
        return files
    }
}

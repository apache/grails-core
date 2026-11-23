/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.urlmappings

import java.nio.charset.StandardCharsets

import spock.lang.Specification
import spock.lang.TempDir

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class GenerateUrlMappingsIndexTaskSpec extends Specification {

    @TempDir
    File projectDir

    void 'one project writes one index covering every UrlMappings source'() {
        given:
        String books = '''\
class UrlMappings {
    static mappings = {
        "/books"(controller: "book", action: "list")
        '/book/$id'(controller: "book", action: "show")
        get "/admin"(controller: "admin", action: "index")
        group "/api", {
            "/status"(controller: "status", action: "health")
        }
    }
}
'''
        String reports = '''\
class ReportUrlMappings {
    static mappings = {
        "/reports"(controller: "report", action: "index")
    }
}
'''

        when:
        Properties index = propertiesOf(task(GenerateUrlMappingsIndexTask.TYPE_APPLICATION, 'my-app', books, reports))

        then:
        index.getProperty('format.version') == GenerateUrlMappingsIndexTask.FORMAT_VERSION
        index.getProperty('artifact.type') == 'application'
        index.getProperty('artifact.name') == 'my-app'
        index.getProperty('mapping.count') == '5'
        patterns(index) == ['/admin', '/api/status', '/book/$id', '/books', '/reports']
        index.getProperty('mapping.0.method') == 'GET'
        index.getProperty('mapping.0.controller') == 'admin'
        index.getProperty('mapping.3.controller') == 'book'
        index.getProperty('mapping.3.action') == 'list'
    }

    void 'a plugin index names that plugin'() {
        when:
        Properties index = propertiesOf(task(GenerateUrlMappingsIndexTask.TYPE_PLUGIN, 'spring-security-core', '''\
class UrlMappings {
    static mappings = {
        "/login"(controller: "login", action: "auth")
    }
}
'''))

        then:
        index.getProperty('artifact.type') == 'plugin'
        index.getProperty('artifact.name') == 'spring-security-core'
        index.getProperty('mapping.0.pattern') == '/login'
        index.getProperty('mapping.0.controller') == 'login'
        index.getProperty('mapping.0.action') == 'auth'
    }

    void 'non-ASCII patterns are written so Properties.load can read them'() {
        when:
        Properties index = propertiesOf(task(GenerateUrlMappingsIndexTask.TYPE_APPLICATION, 'my-app', '''\
class UrlMappings {
    static mappings = {
        "/b\u00fccher"(controller: "book", action: "list")
    }
}
'''))

        then:
        index.getProperty('mapping.0.pattern') == '/b\u00fccher'
        index.getProperty('mapping.0.controller') == 'book'
    }

    void 'a mapping whose controller is not a constant is left out of the index'() {
        when:
        Properties index = propertiesOf(task(GenerateUrlMappingsIndexTask.TYPE_APPLICATION, 'my-app', '''\
class UrlMappings {
    static mappings = {
        "/books"(controller: name, action: "list")
    }
}
'''))

        then:
        index.getProperty('mapping.count') == '0'
        index.getProperty('artifact.type') == 'application'
    }

    void 'an application and a plugin each package an index when nothing is indexable'() {
        when:
        Properties application = propertiesOf(emptyTask(GenerateUrlMappingsIndexTask.TYPE_APPLICATION, 'my-app', 'app-out'))
        Properties plugin = propertiesOf(emptyTask(GenerateUrlMappingsIndexTask.TYPE_PLUGIN, 'spring-security-core', 'plugin-out'))

        then:
        application.getProperty('format.version') == GenerateUrlMappingsIndexTask.FORMAT_VERSION
        application.getProperty('artifact.type') == 'application'
        application.getProperty('artifact.name') == 'my-app'
        application.getProperty('mapping.count') == '0'
        plugin.getProperty('artifact.type') == 'plugin'
        plugin.getProperty('artifact.name') == 'spring-security-core'
        plugin.getProperty('mapping.count') == '0'
    }

    private GenerateUrlMappingsIndexTask task(String type, String name, String... sources) {
        File controllers = new File(projectDir, 'grails-app/controllers')
        controllers.mkdirs()
        sources.eachWithIndex { String source, int index ->
            new File(controllers, "Mappings${index}UrlMappings.groovy").setText(source, 'UTF-8')
        }
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.tasks.register('generateUrlMappingsIndex', GenerateUrlMappingsIndexTask) { GenerateUrlMappingsIndexTask registered ->
            registered.sources.from(project.fileTree(controllers).matching { include '**/*UrlMappings.groovy' })
            registered.artifactType.set(type)
            registered.artifactName.set(name)
            registered.outputDirectory.set(new File(projectDir, 'out'))
        }
        project.tasks.named('generateUrlMappingsIndex', GenerateUrlMappingsIndexTask).get()
    }

    private GenerateUrlMappingsIndexTask emptyTask(String type, String name, String output) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.tasks.register('generate-' + output, GenerateUrlMappingsIndexTask) { GenerateUrlMappingsIndexTask registered ->
            registered.sources.from(project.files())
            registered.artifactType.set(type)
            registered.artifactName.set(name)
            registered.outputDirectory.set(new File(projectDir, output))
        }
        project.tasks.named('generate-' + output, GenerateUrlMappingsIndexTask).get()
    }

    private Properties propertiesOf(GenerateUrlMappingsIndexTask generated) {
        generated.generate()
        File file = new File(generated.outputDirectory.get().asFile, GenerateUrlMappingsIndexTask.INDEX_PATH)
        Properties properties = new Properties()
        file.withInputStream { input ->
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8))
        }
        properties
    }

    private static List<String> patterns(Properties index) {
        int count = Integer.parseInt(index.getProperty('mapping.count'))
        (0..<count).collect { int i -> index.getProperty("mapping.${i}.pattern") }
    }
}

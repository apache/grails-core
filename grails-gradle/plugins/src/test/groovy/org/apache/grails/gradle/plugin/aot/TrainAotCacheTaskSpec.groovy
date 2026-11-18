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
package org.apache.grails.gradle.plugin.aot

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Covers the run that writes down what starting the application needs.
 *
 * <p>The cache is written as the training JVM exits, so a run that never started, or one that was
 * killed, leaves nothing behind -- and a build that carried on regardless would ship an application
 * with no cache and no indication that the speed it was built for is absent.</p>
 */
class TrainAotCacheTaskSpec extends Specification {

    @TempDir
    File temporaryFolder

    Project project = ProjectBuilder.builder().build()

    /** Where the fixture writes the paths it was asked for, so the spec can read them back. */
    private File requestedFile

    List<String> getRequested() {
        requestedFile?.isFile() ? requestedFile.readLines().findAll { it } : []
    }

    private static Properties describedBy(TrainAotCacheTask task) {
        Properties properties = new Properties()
        task.metadataFile.get().asFile.withInputStream { properties.load(it) }
        properties
    }

    private static String sha256Of(File file) {
        java.security.MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString()
    }

    /**
     * A task whose training run really starts, serves and stops.
     *
     * <p>Launched through a script standing in for the JVM: the run is given
     * {@code -XX:AOTCacheOutput}, which a JDK before 25 refuses outright, and a test of this task
     * should not also be a test of which JDK the build is running on.</p>
     */
    private TrainAotCacheTask servingTask(List<String> paths, boolean announcesItself = true) {
        File application = new File(temporaryFolder, 'serving')
        application.mkdirs()
        new File(application, 'served.jar').text = 'stands in for the archive, and is what is digested'
        requestedFile = new File(temporaryFolder, 'requested.txt')
        requestedFile.text = ''

        File script = new File(temporaryFolder, 'fake-jvm.sh')
        script.text = """#!/bin/sh
cache=""
port=""
for arg in "\$@"; do
  case "\$arg" in
    -XX:AOTCacheOutput=*) cache="\${arg#-XX:AOTCacheOutput=}" ;;
    --server.port=*) port="\${arg#--server.port=}" ;;
  esac
done
exec '${new File(System.getProperty('java.home'), 'bin/java').absolutePath}' \\
  -cp '${System.getProperty('java.class.path')}' \\
  ${TrainingRunFixture.name} "\$cache" "\$port" '${requestedFile.absolutePath}' ${announcesItself ? 'loud' : 'quiet'}
"""
        script.setExecutable(true)

        TrainAotCacheTask task = project.tasks.create('trainServing', TrainAotCacheTask)
        task.applicationDirectory.set(application)
        task.archiveFileName.set('served.jar')
        task.cacheFile.set(new File(application, 'demo.aot'))
        task.metadataFile.set(new File(application, 'aot-cache.properties'))
        task.javaExecutable.set(script.absolutePath)
        task.javaVersion.set('25.0.1+9')
        task.javaVendor.set('A Vendor')
        task.jvmArguments.set(['-Dspring.aot.enabled=true'])
        task.paths.set(paths)
        task.port.set(18098)
        task.startTimeoutSeconds.set(60)
        task
    }

    private TrainAotCacheTask task(String archiveName, List<String> arguments) {
        File application = new File(temporaryFolder, 'application')
        application.mkdirs()
        new File(application, archiveName).text = 'not a real archive'
        TrainAotCacheTask task = project.tasks.create('trainAotCache', TrainAotCacheTask)
        task.applicationDirectory.set(application)
        task.archiveFileName.set(archiveName)
        task.cacheFile.set(new File(application, 'demo.aot'))
        task.metadataFile.set(new File(application, 'aot-cache.properties'))
        task.javaExecutable.set(new File(System.getProperty('java.home'), 'bin/java').absolutePath)
        task.javaVersion.set('25.0.1+9')
        task.javaVendor.set('A Vendor')
        task.jvmArguments.set(arguments)
        task.paths.set([])
        task.port.set(18099)
        task.startTimeoutSeconds.set(5)
        task
    }

    void 'a variable the daemon kept but emptied is not handed to the run'() {
        given: 'a run that writes down the environment it was given, and ends'
            File application = new File(temporaryFolder, 'environment')
            application.mkdirs()
            new File(application, 'reports.jar').text = 'stands in for the archive'
            File seen = new File(temporaryFolder, 'seen-environment.txt')
            File script = new File(temporaryFolder, 'reporting-jvm.sh')
            script.text = """#!/bin/sh
env > '${seen.absolutePath}'
exit 1
"""
            script.setExecutable(true)
            TrainAotCacheTask task = project.tasks.create('trainEnvironment', TrainAotCacheTask)
            task.applicationDirectory.set(application)
            task.archiveFileName.set('reports.jar')
            task.cacheFile.set(new File(application, 'demo.aot'))
            task.metadataFile.set(new File(application, 'aot-cache.properties'))
            task.javaExecutable.set(script.absolutePath)
            task.javaVersion.set('25.0.1+9')
            task.javaVendor.set('A Vendor')
            task.jvmArguments.set([])
            task.paths.set([])
            task.port.set(18096)
            task.startTimeoutSeconds.set(5)

        and: 'which this build gives the test worker, because an environment cannot be written'
            assert System.getenv().containsKey('GRAILS_TRAINING_LEFTOVER')
            assert !System.getenv('GRAILS_TRAINING_LEFTOVER')

        when:
            task.train()

        then:
            thrown(GradleException)

        and: 'Spring Boot binds an environment variable over the application configuration, so an ' +
                'empty one left behind by a daemon replaces a configured value with nothing'
            !seen.readLines().any { String line -> line.startsWith('GRAILS_TRAINING_LEFTOVER=') }

        and: 'while the environment the build was actually given still arrives'
            seen.readLines().any { String line -> line.startsWith('PATH=') }
    }

    void 'the run is given the archive by its full path, and still runs in its directory'() {
        given: 'a run that writes down where it was put and what it was given, and ends'
            File application = new File(temporaryFolder, 'named')
            application.mkdirs()
            new File(application, 'named.jar').text = 'stands in for the archive'
            File arguments = new File(temporaryFolder, 'seen-arguments.txt')
            File directory = new File(temporaryFolder, 'seen-directory.txt')
            File script = new File(temporaryFolder, 'recording-jvm.sh')
            script.text = """#!/bin/sh
pwd -P > '${directory.absolutePath}'
for argument in "\$@"; do echo "\$argument" >> '${arguments.absolutePath}'; done
exit 1
"""
            script.setExecutable(true)
            TrainAotCacheTask task = project.tasks.create('trainNamed', TrainAotCacheTask)
            task.applicationDirectory.set(application)
            task.archiveFileName.set('named.jar')
            task.cacheFile.set(new File(application, 'demo.aot'))
            task.metadataFile.set(new File(application, 'aot-cache.properties'))
            task.javaExecutable.set(script.absolutePath)
            task.javaVersion.set('25.0.1+9')
            task.javaVendor.set('A Vendor')
            task.jvmArguments.set([])
            task.paths.set([])
            task.port.set(18095)
            task.startTimeoutSeconds.set(5)

        when:
            task.train()

        then:
            thrown(GradleException)

        and: 'the cache records the classpath it was trained against, and an entry that is a bare ' +
                'name is resolved against the working directory of whatever starts the application ' +
                'later -- so a cache trained by name is refused for java -jar /opt/app/app.jar'
            List<String> given = arguments.readLines()
            int jar = given.indexOf('-jar')
            jar >= 0
            given[jar + 1] == new File(application, 'named.jar').absolutePath

        and: 'while the run itself is still made in the application directory, which is where the ' +
                'archive expects to find what sits beside it'
            directory.text.trim() == application.canonicalPath
    }

    void 'a run that never starts serving fails the build'() {
        given: 'an archive that is not one, so the run ends immediately'
            TrainAotCacheTask task = task('not-an-archive.jar', [])

        when:
            task.train()

        then: 'rather than leaving a build to carry on and ship an application with no cache'
            GradleException e = thrown()
            e.message.contains('ended before it started serving')
    }

    void 'what the run printed is where the failure says it is'() {
        given:
            TrainAotCacheTask task = task('not-an-archive.jar', [])

        when:
            task.train()

        then:
            GradleException e = thrown()
            new File(e.message.replaceAll(/(?s).*is in /, '').trim()).isFile()
    }

    void 'a run that could not start is reported with what it said was wrong'() {
        given: 'a run that prints why it could not start, the way Spring Boot does, and ends'
            File application = new File(temporaryFolder, 'failing')
            application.mkdirs()
            new File(application, 'failing.jar').text = 'stands in for the archive'
            File script = new File(temporaryFolder, 'failing-jvm.sh')
            script.text = '''#!/bin/sh
echo "***************************"
echo "APPLICATION FAILED TO START"
echo "***************************"
echo ""
echo "Description:"
echo ""
echo "Invalid value for configuration property grails.mongodb.url, originating from System Environment Property GRAILS_MONGODB_URL"
exit 1
'''
            script.setExecutable(true)
            TrainAotCacheTask task = project.tasks.create('trainFailing', TrainAotCacheTask)
            task.applicationDirectory.set(application)
            task.archiveFileName.set('failing.jar')
            task.cacheFile.set(new File(application, 'demo.aot'))
            task.metadataFile.set(new File(application, 'aot-cache.properties'))
            task.javaExecutable.set(script.absolutePath)
            task.javaVersion.set('25.0.1+9')
            task.javaVendor.set('A Vendor')
            task.jvmArguments.set([])
            task.paths.set([])
            task.port.set(18097)
            task.startTimeoutSeconds.set(5)

        when:
            task.train()

        then: 'the reason is in the failure, rather than only a file to go and read'
            GradleException e = thrown()
            e.message.contains('ended before it started serving')
            e.message.contains('grails.mongodb.url')
            e.message.contains('GRAILS_MONGODB_URL')

        and: 'without the box it was printed in'
            !e.message.contains('****')
    }

    void 'a run that starts is exercised and described'() {
        given: 'a run that says it started, serves what it is asked for, and stops when asked'
            TrainAotCacheTask task = servingTask(['/', '/login', '/missing'])

        when:
            task.train()

        then: 'the cache the run left behind is described by what it was made from'
            Properties described = describedBy(task)
            described.'application.archive' == 'served.jar'
            described.'application.sha256' == sha256Of(new File(task.applicationDirectory.get().asFile, 'served.jar'))
            described.'training.paths' == '/ /login /missing'
            described.'training.arguments' == '-Dspring.aot.enabled=true'
            described.'java.runtime.version' == '25.0.1+9'
            described.'java.vendor' == 'A Vendor'

        and: 'every path was asked for, including the one that answered with an error'
            requested == ['/', '/login', '/missing']
    }

    void 'a path that is not a URI is skipped rather than failing the build'() {
        given: 'a leading slash left off, which makes a URI with no valid authority'
            TrainAotCacheTask task = servingTask(['login', '/'])

        when:
            task.train()

        then: 'a typo in a list that exists to make the next start quicker does not fail a build'
            noExceptionThrown()

        and: 'the paths that are URIs were still asked for'
            requested == ['/']
    }

    void 'a run that says nothing is found by its port'() {
        given: 'an application that has turned off the log line Spring Boot starts with'
            TrainAotCacheTask task = servingTask(['/'], false)

        when:
            task.train()

        then: 'a message an application may reword or silence is not the only way to know it is up'
            noExceptionThrown()
            requested == ['/']
    }

    void 'a run is refused where it could not be asked to stop'() {
        given: 'Windows, where a child process can only be killed, and a killed run writes no cache'
            String os = System.getProperty('os.name')
            System.setProperty('os.name', 'Windows 11')
            TrainAotCacheTask task = task('not-an-archive.jar', [])

        when:
            task.train()

        then: 'said before the run rather than after it, as "no cache was written"'
            GradleException e = thrown()
            e.message.contains('can only be killed')

        cleanup:
            System.setProperty('os.name', os)
    }

    void 'a run is refused on a JDK that has no cache to write'() {
        given: 'the toolchain the training would run on, which is not the one running the build'
            TrainAotCacheTask task = task('not-an-archive.jar', [])
            task.javaVersion.set('21.0.12+10')

        when:
            task.train()

        then: 'said as the wrong JDK, rather than as an application that ended before it served'
            GradleException e = thrown()
            e.message.contains('Java 25 or later')
            e.message.contains('21.0.12+10')
    }

    void 'a JDK that can write one is not refused'() {
        given:
            TrainAotCacheTask task = task('not-an-archive.jar', [])
            task.javaVersion.set(version)

        when:
            task.train()

        then: 'it gets as far as running, which is where this spec stops caring'
            GradleException e = thrown()
            !e.message.contains('Java 25 or later')

        where:
            version << ['25.0.1+9', '26', '31.0.2+7']
    }

    void 'a version that cannot be read is left to the run to answer for'() {
        given: 'refusing over an unreadable version would fail a build on a capable JDK'
            TrainAotCacheTask task = task('not-an-archive.jar', [])
            task.javaVersion.set('a vendor string nobody parsed')

        when:
            task.train()

        then:
            GradleException e = thrown()
            !e.message.contains('Java 25 or later')
    }
}

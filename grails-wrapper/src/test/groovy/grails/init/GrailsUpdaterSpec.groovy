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
package grails.init

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

import io.github.cjstehno.ersatz.GroovyErsatzServer
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import uk.org.webcompere.systemstubs.SystemStubs

class GrailsUpdaterSpec extends Specification {

    private static final String REMOTE_BASE_URL = 'https://localhost/releases/maven-metadata.xml'

    @TempDir
    File dir

    @AutoCleanup
    GroovyErsatzServer server = new GroovyErsatzServer({})

    @Unroll
    def "remote wrapper URL must use HTTPS for #url"() {
        when:
        GrailsUpdater.createSecureRemoteUrl(url)

        then:
        def e = thrown(IOException)
        e.message == "Grails wrapper remote repository URLs must use HTTPS: ${url}"

        where:
        url << [
                'http://localhost/maven-metadata.xml',
                'file:/tmp/repo/maven-metadata.xml',
        ]
    }

    def "remote wrapper URL accepts HTTPS"() {
        expect:
        GrailsUpdater.createSecureRemoteUrl(REMOTE_BASE_URL).toString() == REMOTE_BASE_URL
    }

    def "remote wrapper redirect must stay on HTTPS"() {
        when:
        GrailsUpdater.resolveSecureRedirectUrl(new URI(REMOTE_BASE_URL), 'http://localhost/releases/maven-metadata.xml')

        then:
        def e = thrown(IOException)
        e.message == 'Grails wrapper remote repository URLs must use HTTPS: http://localhost/releases/maven-metadata.xml'
    }

    def "remote wrapper rejects HTTP redirect responses"() {
        given:
        server.expectations {
            GET('/start') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', 'http://localhost/insecure')
                }
            }
        }

        when:
        GrailsUpdater.createHttpURLConnection('https://localhost/start') { URL requestedUrl ->
            openErsatzConnection(requestedUrl)
        }

        then:
        def e = thrown(IOException)
        e.message == 'Grails wrapper remote repository URLs must use HTTPS: http://localhost/insecure'
        server.verify()
    }

    def "remote wrapper redirect accepts relative HTTPS target"() {
        expect:
        GrailsUpdater.resolveSecureRedirectUrl(new URI(REMOTE_BASE_URL), '../snapshots/maven-metadata.xml') == new URI('https://localhost/snapshots/maven-metadata.xml')
    }

    def "remote wrapper redirect requires location header"() {
        when:
        GrailsUpdater.resolveSecureRedirectUrl(new URI(REMOTE_BASE_URL), null)

        then:
        def e = thrown(IOException)
        e.message == 'Redirect response is missing a Location header for Grails wrapper remote resource: https://localhost/releases/maven-metadata.xml'
    }

    def "remote wrapper rejects redirect responses without a location header"() {
        given:
        server.expectations {
            GET('/start') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                }
            }
        }

        when:
        GrailsUpdater.createHttpURLConnection('https://localhost/start') { URL requestedUrl ->
            openErsatzConnection(requestedUrl)
        }

        then:
        def e = thrown(IOException)
        e.message == 'Redirect response is missing a Location header for Grails wrapper remote resource: https://localhost/start'
        server.verify()
    }

    def "remote wrapper redirect rejects malformed location header"() {
        when:
        GrailsUpdater.resolveSecureRedirectUrl(new URI(REMOTE_BASE_URL), 'http://[bad')

        then: 'what stands where the host is, and is none, is not shown: it cannot be told from a user and a password'
        def e = thrown(IOException)
        e.message == 'Invalid redirect Location header for Grails wrapper remote resource: http://***'
    }

    def "remote wrapper accepts final response after maximum redirects"() {
        given:
        server.expectations {
            GET('/start') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-1')
                }
            }
            GET('/redirect-1') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-2')
                }
            }
            GET('/redirect-2') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-3')
                }
            }
            GET('/redirect-3') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-4')
                }
            }
            GET('/redirect-4') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/final')
                }
            }
            GET('/final') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_OK)
                    body('ok')
                }
            }
        }

        when:
        HttpURLConnection connection = GrailsUpdater.createHttpURLConnection('https://localhost/start') { URL requestedUrl ->
            openErsatzConnection(requestedUrl)
        }

        then:
        connection.responseCode == HttpURLConnection.HTTP_OK
        connection.inputStream.text == 'ok'

        and: 'a repository that stops answering does not hold the wrapper for ever'
        connection.connectTimeout == 30000
        connection.readTimeout == 60000
        server.verify()
    }

    def "remote wrapper rejects redirect after maximum redirects"() {
        given:
        server.expectations {
            GET('/start') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-1')
                }
            }
            GET('/redirect-1') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-2')
                }
            }
            GET('/redirect-2') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-3')
                }
            }
            GET('/redirect-3') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-4')
                }
            }
            GET('/redirect-4') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/redirect-5')
                }
            }
            GET('/redirect-5') {
                called(1)
                responder {
                    code(HttpURLConnection.HTTP_MOVED_TEMP)
                    header('Location', '/too-many')
                }
            }
        }

        when:
        GrailsUpdater.createHttpURLConnection('https://localhost/start') { URL requestedUrl ->
            openErsatzConnection(requestedUrl)
        }

        then:
        def e = thrown(IOException)
        e.message == 'Too many redirects while downloading Grails wrapper remote resource: https://localhost/start'
        server.verify()
    }

    private HttpURLConnection openErsatzConnection(URL requestedUrl) {
        new URL(server.httpUrl(requestedUrl.path).toString()).openConnection() as HttpURLConnection
    }

    def "a snapshot is taken from a local repository that a build published to"() {
        given: 'a directory in the layout of a remote repository: maven-metadata.xml and a jar named by its timestamp'
        File repository = new File(dir, 'repository')
        File versionDirectory = new File(repository, 'org/apache/grails/grails-cli/9.0.0-SNAPSHOT')
        versionDirectory.mkdirs()
        new File(versionDirectory, 'maven-metadata.xml').text = """\
            <metadata>
              <groupId>org.apache.grails</groupId>
              <artifactId>grails-cli</artifactId>
              <version>9.0.0-SNAPSHOT</version>
              <versioning>
                <snapshot><timestamp>20261120.101112</timestamp><buildNumber>3</buildNumber></snapshot>
                <snapshotVersions>
                  <snapshotVersion>
                    <classifier>all</classifier>
                    <extension>jar</extension>
                    <value>9.0.0-20261120.101112-3</value>
                  </snapshotVersion>
                </snapshotVersions>
              </versioning>
            </metadata>
            """.stripIndent()
        new File(versionDirectory, 'grails-cli-9.0.0-20261120.101112-3-all.jar').bytes = CliJars.jar('the command line')
        File home = new File(dir, 'home')
        home.mkdirs()
        System.setProperty('grails.repo.url', repository.absolutePath)

        when:
        boolean updated = false
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.absolutePath).execute {
            updated = new GrailsUpdater(new LinkedHashSet<>([GrailsReleaseType.SNAPSHOT]), new GrailsVersion('9.0.0-SNAPSHOT')).update()
        }

        then: 'the jar of that repository is the one in the wrapper home, and no other repository was asked'
        updated
        CliJars.marker(new File(home, 'wrapper/9.0.0-SNAPSHOT/grails-cli-9.0.0-SNAPSHOT-all.jar')) == 'the command line'

        cleanup:
        System.clearProperty('grails.repo.url')
    }

    def "an overridden repository without the snapshot is named before the next repository is tried"() {
        given: 'two repositories of the override: the first has nothing, the second has the snapshot'
        File empty = new File(dir, 'empty')
        empty.mkdirs()
        File repository = new File(dir, 'repository')
        File versionDirectory = new File(repository, 'org/apache/grails/grails-cli/9.0.0-SNAPSHOT')
        versionDirectory.mkdirs()
        new File(versionDirectory, 'maven-metadata-local.xml').text = """\
            <metadata>
              <versioning>
                <snapshotVersions>
                  <snapshotVersion>
                    <classifier>all</classifier>
                    <extension>jar</extension>
                    <value>9.0.0-SNAPSHOT</value>
                  </snapshotVersion>
                </snapshotVersions>
              </versioning>
            </metadata>
            """.stripIndent()
        new File(versionDirectory, 'grails-cli-9.0.0-SNAPSHOT-all.jar').bytes = CliJars.jar('the command line')
        File home = new File(dir, 'home')
        home.mkdirs()
        System.setProperty('grails.repo.url', "${empty.absolutePath};${repository.absolutePath}")

        when:
        boolean updated = false
        String output = SystemStubs.tapSystemOut {
            SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.absolutePath).execute {
                updated = new GrailsUpdater(new LinkedHashSet<>([GrailsReleaseType.SNAPSHOT]), new GrailsVersion('9.0.0-SNAPSHOT')).update()
            }
        }

        then:
        updated
        output.contains("...The Grails CLI was not found in the overridden repository [${new File(empty, 'org/apache/grails/grails-cli').path}]: ")
        output.contains('...Trying the next repository.')
        !output.contains("overridden repository [${new File(repository, 'org/apache/grails/grails-cli').path}]")

        cleanup:
        System.clearProperty('grails.repo.url')
    }
}

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
import spock.lang.Unroll

class GrailsUpdaterSpec extends Specification {

    private static final String REMOTE_BASE_URL = 'https://localhost/releases/maven-metadata.xml'

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

        then:
        def e = thrown(IOException)
        e.message == 'Invalid redirect Location header for Grails wrapper remote resource: http://[bad'
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
}

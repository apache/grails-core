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
package org.grails.web.mapping

import java.nio.charset.StandardCharsets

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.Action
import grails.web.mapping.UrlMappingInfo
import org.grails.web.mapping.mvc.GrailsControllerUrlMappings
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

class UrlMappingsIndexSpec extends Specification {

    void 'every classpath root contributes an index'() {
        when:
        UrlMappingsIndex index = UrlMappingsIndex.load(loaderFor([
                memoryUrl(indexText('plugin-sample', 'plugin', '/tools', 'tool', 'index')),
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list'))
        ]))

        then:
        index.entries*.pattern as Set == ['/tools', '/books'] as Set
        index.entries*.artifactName as Set == ['plugin-sample', 'my-app'] as Set
        index.entries*.artifactType as Set == ['plugin', 'application'] as Set
    }

    void 'the first classpath match does not hide a later index'() {
        given:
        URL first = memoryUrl(indexText('first', 'application', '/first', 'first', 'show'))
        URL second = memoryUrl(indexText('second', 'plugin', '/second', 'second', 'index'))
        ClassLoader loader = new ClassLoader(null) {
            @Override
            InputStream getResourceAsStream(String name) {
                name == UrlMappingsIndex.LOCATION ? first.openStream() : null
            }

            @Override
            Enumeration<URL> getResources(String name) {
                name == UrlMappingsIndex.LOCATION ? Collections.enumeration([first, second]) : Collections.emptyEnumeration()
            }
        }

        expect:
        UrlMappingsIndex.load(loader).entries*.pattern as Set == ['/first', '/second'] as Set
    }

    void 'a failing index is ignored when a later classpath root is readable'() {
        when:
        UrlMappingsIndex malformed = UrlMappingsIndex.load(loaderFor([
                memoryUrl('format.version=\\uZZZZ\n'.getBytes(StandardCharsets.UTF_8)),
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list'))
        ]))
        UrlMappingsIndex unreadable = UrlMappingsIndex.load(loaderFor([
                unreadableUrl(),
                memoryUrl(indexText('my-app', 'application', '/tools', 'tool', 'index'))
        ]))

        then:
        malformed.entries*.pattern == ['/books']
        unreadable.entries*.pattern == ['/tools']
    }

    void 'an explicit class loader is used even when the thread context loader has an index'() {
        given:
        Thread current = Thread.currentThread()
        ClassLoader original = current.contextClassLoader
        current.contextClassLoader = loaderFor([
                memoryUrl(indexText('tccl', 'application', '/from-tccl', 'tccl', 'show'))
        ])
        ClassLoader explicit = loaderFor([
                memoryUrl(indexText('explicit', 'application', '/from-explicit', 'explicit', 'index'))
        ])

        when:
        UrlMappingsIndex index = UrlMappingsIndex.load(explicit)

        then:
        index.entries*.pattern == ['/from-explicit']

        cleanup:
        current.contextClassLoader = original
    }

    void 'a programming error from the class loader is not treated as a missing index'() {
        when:
        UrlMappingsIndex.load(new ClassLoader(null) {
            @Override
            Enumeration<URL> getResources(String name) {
                throw new NullPointerException('bug')
            }
        })

        then:
        thrown NullPointerException
    }

    void 'index files are read as UTF-8'() {
        given:
        String pattern = '/b\u00fccher'
        byte[] body = """format.version=1
artifact.type=application
artifact.name=my-app
mapping.count=1
mapping.0.pattern=${pattern}
mapping.0.controller=book
mapping.0.action=list
""".getBytes(StandardCharsets.UTF_8)

        expect:
        UrlMappingsIndex.load(loaderFor([memoryUrl(body)])).entries*.pattern == [pattern]
    }

    void 'building a holder does not read an index until that index is used'() {
        given:
        boolean[] seen = [false]
        ClassLoader loader = new ClassLoader(null) {
            @Override
            Enumeration<URL> getResources(String name) {
                seen[0] = true
                return Collections.emptyEnumeration()
            }
        }
        Thread current = Thread.currentThread()
        ClassLoader original = current.contextClassLoader
        current.contextClassLoader = loader
        DefaultUrlMappingsHolder holder = new DefaultUrlMappingsHolder(booksMapping(), null, true)

        when:
        holder.initialize()
        UrlMappingInfo info = holder.match('/books')

        then:
        !seen[0]
        !holder.matchedFromIndex('/books')
        info.controllerName == 'book'
        info.actionName == 'list'

        cleanup:
        current.contextClassLoader = original
    }

    void 'runtime matching stays authoritative when an index names a different controller'() {
        given:
        DefaultUrlMappingsHolder holder = holder(booksMapping(), loaderFor([
                memoryUrl(indexText('my-app', 'application', '/books', 'other', 'show'))
        ]))

        when:
        UrlMappingInfo info = holder.match('/books')

        then:
        holder.urlMappingsIndex.entries*.controller == ['other']
        !holder.matchedFromIndex('/books')
        info.controllerName == 'book'
        info.actionName == 'list'
    }

    void 'an index entry is used when it describes the runtime mapping'() {
        given:
        DefaultUrlMappingsHolder holder = holder(booksMapping(), loaderFor([
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list'))
        ]))

        when:
        UrlMappingInfo info = holder.match('/books')

        then:
        holder.matchedFromIndex('/books')
        info.controllerName == 'book'
        info.actionName == 'list'
    }

    void 'matches use the index from every classpath root'() {
        given:
        List mappings = new DefaultUrlMappingEvaluator(null).evaluateMappings {
            '/books'(controller: 'book', action: 'list')
            '/tools'(controller: 'tool', action: 'index')
        }
        DefaultUrlMappingsHolder holder = holder(mappings, loaderFor([
                memoryUrl(indexText('plugin-sample', 'plugin', '/tools', 'tool', 'index')),
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list'))
        ]))

        when:
        UrlMappingInfo books = holder.match('/books')
        UrlMappingInfo tools = holder.match('/tools')

        then:
        holder.matchedFromIndex('/books')
        holder.matchedFromIndex('/tools')
        books.controllerName == 'book'
        books.actionName == 'list'
        tools.controllerName == 'tool'
        tools.actionName == 'index'
    }

    void 'an entry that omits the action or names another namespace or plugin is not the runtime mapping'() {
        given:
        DefaultUrlMappingsHolder missingAction = holder(booksMapping(), loaderFor([
                memoryUrl('''format.version=1
artifact.type=application
artifact.name=my-app
mapping.count=1
mapping.0.pattern=/books
mapping.0.controller=book
''')
        ]))
        DefaultUrlMappingsHolder otherNamespace = holder(namespacedMapping(), loaderFor([
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list') + 'mapping.0.namespace=other\n')
        ]))
        DefaultUrlMappingsHolder otherPlugin = holder(namespacedMapping(), loaderFor([
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list') +
                        'mapping.0.namespace=api\nmapping.0.plugin=other\n')
        ]))
        DefaultUrlMappingsHolder identified = holder(namespacedMapping(), loaderFor([
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list') +
                        'mapping.0.namespace=api\nmapping.0.plugin=books\n')
        ]))

        expect:
        missingAction.match('/books').actionName == 'list'
        !missingAction.matchedFromIndex('/books')
        otherNamespace.match('/books').controllerName == 'book'
        !otherNamespace.matchedFromIndex('/books')
        otherPlugin.match('/books').controllerName == 'book'
        !otherPlugin.matchedFromIndex('/books')
        identified.match('/books').actionName == 'list'
        identified.matchedFromIndex('/books')
    }

    void 'an index entry with no action does not identify a closure action'() {
        given:
        DefaultUrlMappingsHolder closureAction = holder(new DefaultUrlMappingEvaluator(null).evaluateMappings {
            '/books'(controller: 'book', action: { 'show' })
        }, loaderFor([
                memoryUrl('''format.version=1
artifact.type=application
artifact.name=my-app
mapping.count=1
mapping.0.pattern=/books
mapping.0.controller=book
''')
        ]))

        when:
        UrlMappingInfo info = closureAction.match('/books')

        then:
        info != null
        info.controllerName == 'book'
        !closureAction.matchedFromIndex('/books')
    }

    void 'an access denial while enumerating indexes does not drop a later index'() {
        when:
        URL later = memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list'))
        UrlMappingsIndex continued = UrlMappingsIndex.load(new ClassLoader(null) {
            @Override
            Enumeration<URL> getResources(String name) {
                if (name != UrlMappingsIndex.LOCATION) {
                    return Collections.emptyEnumeration()
                }
                return new Enumeration<URL>() {
                    private int cursor

                    @Override
                    boolean hasMoreElements() {
                        return cursor < 2
                    }

                    @Override
                    URL nextElement() {
                        if (cursor++ == 0) {
                            throw new SecurityException('denied')
                        }
                        return later
                    }
                }
            }
        })
        UrlMappingsIndex gapped = UrlMappingsIndex.load(loaderFor([
                memoryUrl('''format.version=1
artifact.type=application
artifact.name=bad
mapping.count=1
mapping.5.pattern=/skipped
mapping.5.controller=skipped
mapping.5.action=show
'''),
                memoryUrl(indexText('my-app', 'application', '/tools', 'tool', 'index'))
        ]))

        then:
        continued.entries*.pattern == ['/books']
        gapped.entries*.pattern == ['/tools']
    }

    void 'dispatch considers the indexed runtime mapping ahead of the others'() {
        given:
        DefaultGrailsApplication application = new DefaultGrailsApplication(BookController)
        application.initialise()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.requestURI = '/books'
        webRequest.request.method = 'GET'
        DefaultUrlMappingsHolder plain = new DefaultUrlMappingsHolder(duplicateBooks())
        String withoutIndex = new UrlMappingsHandlerMapping(new GrailsControllerUrlMappings(application, plain))
                .getHandler(webRequest.request).handler.actionName
        DefaultUrlMappingsHolder chosen = holder(duplicateBooks(), loaderFor([
                memoryUrl(indexText('my-app', 'application', '/books', 'book', withoutIndex))
        ]))
        List slashFirst = new DefaultUrlMappingEvaluator(null).evaluateMappings {
            '/books/'(controller: 'book', action: 'show')
            '/books'(controller: 'book', action: 'list')
        }
        DefaultUrlMappingsHolder slashPlain = new DefaultUrlMappingsHolder(new ArrayList(slashFirst))
        String slashWithout = new UrlMappingsHandlerMapping(new GrailsControllerUrlMappings(application, slashPlain))
                .getHandler(webRequest.request).handler.actionName
        DefaultUrlMappingsHolder slashIndexed = holder(new ArrayList(slashFirst), loaderFor([
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list'))
        ]))
        DefaultUrlMappingsHolder posted = holder(methodFilteredBooks(), loaderFor([
                memoryUrl(indexText('my-app', 'application', '/books', 'book', 'list') + 'mapping.0.method=GET\n')
        ]))

        when:
        def chosenChain = new UrlMappingsHandlerMapping(new GrailsControllerUrlMappings(application, chosen))
                .getHandler(webRequest.request)

        then:
        chosenChain.handler.actionName == withoutIndex
        chosen.matchedFromIndex('/books')

        when:
        def slashChain = new UrlMappingsHandlerMapping(new GrailsControllerUrlMappings(application, slashIndexed))
                .getHandler(webRequest.request)

        then:
        slashChain.handler.actionName == slashWithout

        when:
        webRequest.request.method = 'POST'
        def postChain = new UrlMappingsHandlerMapping(new GrailsControllerUrlMappings(application, posted))
                .getHandler(webRequest.request)

        then:
        postChain.handler.actionName == 'show'
        !posted.matchedFromIndex('/books')

        cleanup:
        RequestContextHolder.resetRequestAttributes()
    }

    void 'the holder uses the class loader it was given'() {
        given:
        Thread current = Thread.currentThread()
        ClassLoader original = current.contextClassLoader
        current.contextClassLoader = loaderFor([
                memoryUrl(indexText('tccl', 'plugin', '/tools', 'tool', 'index'))
        ])
        List mappings = new DefaultUrlMappingEvaluator(null).evaluateMappings {
            '/books'(controller: 'book', action: 'list')
            '/tools'(controller: 'tool', action: 'index')
        }
        DefaultUrlMappingsHolder holder = holder(mappings, loaderFor([
                memoryUrl(indexText('explicit', 'application', '/books', 'book', 'list'))
        ]))

        when:
        holder.match('/books')
        holder.match('/tools')

        then:
        holder.matchedFromIndex('/books')
        !holder.matchedFromIndex('/tools')
        holder.match('/books').controllerName == 'book'
        holder.match('/tools').controllerName == 'tool'

        cleanup:
        current.contextClassLoader = original
    }

    private static List booksMapping() {
        new DefaultUrlMappingEvaluator(null).evaluateMappings {
            '/books'(controller: 'book', action: 'list')
        }
    }

    private static List namespacedMapping() {
        new DefaultUrlMappingEvaluator(null).evaluateMappings {
            '/books'(controller: 'book', action: 'list', namespace: 'api', plugin: 'books')
        }
    }

    private static List duplicateBooks() {
        new DefaultUrlMappingEvaluator(null).evaluateMappings {
            '/books'(controller: 'book', action: 'list')
            '/books'(controller: 'book', action: 'show')
        }
    }

    private static List methodFilteredBooks() {
        new DefaultUrlMappingEvaluator(null).evaluateMappings {
            '/books'(controller: 'book', action: 'show')
            '/books'(controller: 'book', action: 'list', method: 'GET')
        }
    }

    private static DefaultUrlMappingsHolder holder(List mappings, ClassLoader loader) {
        DefaultUrlMappingsHolder created = new DefaultUrlMappingsHolder(mappings, null, true)
        created.indexClassLoader = loader
        created.initialize()
        created
    }

    private static String indexText(String artifactName, String artifactType, String pattern, String controller, String action) {
        """format.version=1
artifact.type=${artifactType}
artifact.name=${artifactName}
mapping.count=1
mapping.0.pattern=${pattern}
mapping.0.controller=${controller}
mapping.0.action=${action}
"""
    }

    private static ClassLoader loaderFor(List<URL> urls) {
        new ClassLoader(null) {
            @Override
            Enumeration<URL> getResources(String name) {
                name == UrlMappingsIndex.LOCATION ? Collections.enumeration(urls) : Collections.emptyEnumeration()
            }
        }
    }

    private static URL memoryUrl(String text) {
        memoryUrl(text.getBytes(StandardCharsets.UTF_8))
    }

    private static URL memoryUrl(byte[] body) {
        streamUrl('memory:' + System.nanoTime(), { new ByteArrayInputStream(body) })
    }

    private static URL unreadableUrl() {
        streamUrl('memory:unreadable-' + System.nanoTime(), { throw new IOException('unreadable index') })
    }

    private static URL streamUrl(String spec, Closure<InputStream> stream) {
        new URL(null, spec, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                new URLConnection(url) {
                    @Override
                    void connect() {
                    }

                    @Override
                    InputStream getInputStream() {
                        stream.call()
                    }
                }
            }
        })
    }
}

@Artefact('Controller')
class BookController {

    @Action
    def list() {
    }

    @Action
    def show() {
    }

    @Action
    def other() {
    }
}

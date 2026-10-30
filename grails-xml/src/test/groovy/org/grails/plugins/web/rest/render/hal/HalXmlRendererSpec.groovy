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
package org.grails.plugins.web.rest.render.hal

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.persistence.Entity
import grails.rest.Link
import grails.rest.render.hal.HalXmlCollectionRenderer
import grails.rest.render.hal.HalXmlRenderer
import grails.spring.BeanBuilder
import grails.util.GrailsWebMockUtil
import grails.util.GrailsWebUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.LinkGenerator
import grails.web.mime.MimeType
import org.grails.config.PropertySourcesConfig
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.support.MockApplicationContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.mime.DefaultMimeUtility
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.util.WebUtils
import spock.lang.Specification

class HalXmlRendererSpec extends Specification {

    void setup() {
        ShutdownOperations.runOperations()
        def initializer = new XmlConvertersConfigurationInitializer()
        initializer.grailsApplication = new DefaultGrailsApplication()
        initializer.initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
        RequestContextHolder.resetRequestAttributes()
        ShutdownOperations.runOperations()
    }

    void 'Test rendering a domain object with an embedded resource as HAL XML'() {
        given:
        def renderer = configureRenderer(new HalXmlRenderer(Product))
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse
        def product = new Product(name: 'MacBook & accessories', numberInStock: 10,
                category: new Category(name: 'Laptops'))

        when:
        renderer.render(product, new ServletRenderContext(webRequest))

        then:
        response.contentType == GrailsWebUtil.getContentType(HalXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        response.contentAsString.startsWith('<?xml version="1.0" encoding="UTF-8"?><resource href="http://localhost/products" hreflang="en" title="Products">')
        response.contentAsString.contains('<name>MacBook &amp; accessories</name>')
        response.contentAsString.contains('<numberInStock>10</numberInStock>')
        response.contentAsString.contains('<resource href="http://localhost/category/index" hreflang="en"><name>Laptops</name></resource>')
        response.contentAsString.endsWith('</resource></resource>')
    }

    void 'Test rendering a domain association link as HAL XML'() {
        given:
        def renderer = configureRenderer(new HalXmlRenderer(Member))
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse
        def member = new Member(name: 'One', team: new Team(name: 'Test Team'))

        when:
        renderer.render(member, new ServletRenderContext(webRequest))

        then:
        response.contentType == GrailsWebUtil.getContentType(HalXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        response.contentAsString.contains('<resource href="http://localhost/members" hreflang="en">')
        response.contentAsString.contains('<link rel="team" href="http://localhost/teams" hreflang="en" title="Teams" />')
        response.contentAsString.contains('<name>One</name>')
        response.contentAsString.contains('<resource href="http://localhost/teams" hreflang="en" title="Teams">')
        response.contentAsString.contains('<name>Test Team</name>')
    }

    void 'Test rendering a collection of domain objects as HAL XML'() {
        given:
        def renderer = configureRenderer(new HalXmlCollectionRenderer(Product))
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse
        def products = [
            new Product(name: 'MacBook', numberInStock: 10),
            new Product(name: 'iMac', numberInStock: 42)
        ]

        when:
        renderer.render(products, new ServletRenderContext(webRequest))

        then:
        renderer.componentType == Product
        response.contentType == GrailsWebUtil.getContentType(HalXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        response.contentAsString.count('<resource href="http://localhost/products" hreflang="en" title="Products">') == 3
        response.contentAsString.contains('<name>MacBook</name>')
        response.contentAsString.contains('<numberInStock>10</numberInStock>')
        response.contentAsString.contains('<name>iMac</name>')
        response.contentAsString.contains('<numberInStock>42</numberInStock>')
        response.contentAsString.endsWith('</resource></resource>')
    }

    void 'Test rendering an empty HAL XML collection'() {
        given:
        def renderer = configureRenderer(new HalXmlCollectionRenderer(Product))
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse

        when:
        renderer.render([], new ServletRenderContext(webRequest))

        then:
        response.contentType == GrailsWebUtil.getContentType(HalXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        response.contentAsString == '<?xml version="1.0" encoding="UTF-8"?><resource href="http://localhost/products" hreflang="en" title="Products" />'
    }

    void 'Test HAL XML filters POGO properties with #filter'() {
        given:
        def renderer = configureRenderer(new HalXmlRenderer(SimpleProduct))
        renderer.includes = includes
        renderer.excludes = excludes
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse
        def product = new SimpleProduct(name: 'MacBook', numberInStock: 10)

        when:
        renderer.render(product, new ServletRenderContext(webRequest, arguments))

        then:
        response.contentType == GrailsWebUtil.getContentType(HalXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        response.contentAsString.contains('<resource href="http://localhost/products" hreflang="en" title="Products">')
        response.contentAsString.contains('<name>MacBook</name>')
        !response.contentAsString.contains('<numberInStock>')
        !response.contentAsString.contains('<class>')
        !response.contentAsString.contains('<metaClass>')
        response.contentAsString.endsWith('</resource>')

        where:
        filter              | includes | excludes                      | arguments
        'renderer includes' | ['name'] | []                            | [:]
        'renderer excludes' | null     | ['numberInStock', 'category'] | [:]
        'context includes'  | null     | []                            | [includes: ['name']]
        'context excludes'  | null     | []                            | [excludes: ['numberInStock', 'category']]
    }

    void 'Test HAL XML writes the extra links declared by the rendered object'() {
        given:
        def renderer = configureRenderer(new HalXmlRenderer(LinkedGadget))
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse

        when:
        renderer.render(new LinkedGadget(name: 'Gadget'), new ServletRenderContext(webRequest))

        then:
        response.contentAsString.contains('<resource href="http://localhost/products" hreflang="en" title="Products">')
        response.contentAsString.contains('<link rel="docs" href="http://example.org/docs" hreflang="en" templated="true" deprecated="true" />')
        response.contentAsString.contains('<name>Gadget</name>')
    }

    void 'Test HAL XML supports relative links pretty printing and a custom mime type'() {
        given:
        def pretty = configureRenderer(new HalXmlRenderer(Product, MimeType.XML))
        pretty.absoluteLinks = false
        pretty.prettyPrint = true
        def compact = configureRenderer(new HalXmlRenderer(Product))
        compact.absoluteLinks = false
        def product = new Product(name: 'MacBook', numberInStock: 10)

        when:
        def prettyRequest = boundMimeTypeRequest()
        pretty.render(product, new ServletRenderContext(prettyRequest))
        String prettyXml = (prettyRequest.response as MockHttpServletResponse).contentAsString
        def compactRequest = boundMimeTypeRequest()
        compact.render(product, new ServletRenderContext(compactRequest))
        String compactXml = (compactRequest.response as MockHttpServletResponse).contentAsString

        then:
        pretty.mimeTypes.toList() == [MimeType.XML]
        compactXml.contains('<resource href="/products" hreflang="en" title="Products">')
        !compactXml.contains('http://localhost')
        prettyXml != compactXml
        prettyXml.replaceAll(/\s*\R\s*/, '') == compactXml
    }

    void 'HAL XML rendering warns once per application that it is scheduled for removal in Grails 11'() {
        given:
        def renderer = configureRenderer(new HalXmlRenderer(Product))

        when:
        String log = stderrWhile {
            2.times {
                renderer.render(new Product(name: 'MacBook', numberInStock: 1, category: new Category(name: 'Laptops')),
                        new ServletRenderContext(boundMimeTypeRequest()))
            }
        }

        then:
        log.readLines().count { it.contains('HAL XML rendering is scheduled for removal in Grails 11.') } == 1
    }

    private static String stderrWhile(Closure<?> work) {
        PrintStream original = System.err
        def buffer = new ByteArrayOutputStream()
        System.setErr(new PrintStream(buffer, true))
        try {
            work.call()
        }
        finally {
            System.setErr(original)
        }
        buffer.toString()
    }

    private HalXmlRenderer configureRenderer(HalXmlRenderer renderer) {
        def mappingContext = new KeyValueMappingContext('')
        mappingContext.addPersistentEntity(Product)
        mappingContext.addPersistentEntity(Category)
        mappingContext.addPersistentEntity(Team)
        mappingContext.addPersistentEntity(Member)
        renderer.mappingContext = mappingContext
        def messages = new StaticMessageSource()
        messages.addMessage('resource.product.href.title', Locale.ENGLISH, 'Products')
        messages.addMessage('resource.products.href.title', Locale.ENGLISH, 'Products')
        messages.addMessage('resource.team.href.title', Locale.ENGLISH, 'Teams')
        renderer.messageSource = messages
        renderer.linkGenerator = getLinkGenerator()
        renderer.prettyPrint = false
        renderer
    }

    private LinkGenerator getLinkGenerator() {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def mappings = evaluator.evaluateMappings {
            '/products'(resources: 'product')
            '/members'(resources: 'member')
            '/teams'(resources: 'team')
        }
        def generator = new DefaultLinkGenerator('http://localhost', null)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = new DefaultUrlMappingsHolder(mappings)
        generator
    }

    private GrailsWebRequest boundMimeTypeRequest() {
        def servletContext = new MockServletContext()
        def ctx = new GenericWebApplicationContext(servletContext)
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ctx)
        def application = new DefaultGrailsApplication()
        application.config = new PropertySourcesConfig([
            'grails.mime.use.accept.header': true,
            'grails.mime.types': [hal: ['application/hal+json', 'application/hal+xml']]
        ])
        def mainContext = new GenericApplicationContext()
        mainContext.refresh()
        application.setApplicationContext(mainContext)
        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        def mimeConfiguration = bb.createApplicationContext().getBean(MimeTypesConfiguration)
        ctx.beanFactory.registerSingleton('mimeUtility', new DefaultMimeUtility(mimeConfiguration.mimeTypes()))
        ctx.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        ctx.refresh()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(ctx)
        (webRequest.request as MockHttpServletRequest).addHeader('ACCEPT', 'application/hal+xml')
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, '/products')
        webRequest
    }
}

class LinkedGadget {

    String name

    Collection<Link> links() {
        def docs = new Link('docs', 'http://example.org/docs')
        docs.templated = true
        docs.deprecated = true
        [docs]
    }
}

@Entity
class Product {

    String name
    Integer numberInStock
    Category category

    @SuppressWarnings('unused')
    static embedded = ['category']
}

@Entity
class Category {

    String name

    @Override
    String toString() {
        name
    }

    // The renderer checks the objects it has written already, which needs equality by value
    @Override
    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }
        name == ((Category) o).name
    }

    @Override
    int hashCode() {
        return name.hashCode()
    }
}

@Entity
class Team {

    String name

    @SuppressWarnings('unused')
    static hasMany = [members: Member]
}

@Entity
class Member {

    String name

    @SuppressWarnings('unused')
    static belongsTo = [team: Team]
}

class SimpleProduct {
    String name
    Integer numberInStock
    SimpleCategory category
}

class SimpleCategory {
    String name
}

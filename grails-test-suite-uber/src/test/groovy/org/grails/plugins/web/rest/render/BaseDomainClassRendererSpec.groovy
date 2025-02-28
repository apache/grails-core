package org.grails.plugins.web.rest.render

import grails.config.Config
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.persistence.Entity
import grails.rest.Linkable
import grails.rest.Resource
import grails.rest.render.Renderer
import grails.spring.BeanBuilder
import grails.util.GrailsWebMockUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingsHolder
import grails.web.mime.MimeType
import org.grails.config.PropertySourcesConfig
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.mime.DefaultMimeUtility
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Specification

abstract class BaseDomainClassRendererSpec extends Specification {

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    protected abstract Renderer getRenderer()

    protected GrailsWebRequest setupRequest(String acceptMimeType) {
        def webRequest = boundMimeTypeRequest()
        (webRequest.request as MockHttpServletRequest).addHeader('ACCEPT', acceptMimeType)
        return webRequest
    }

    protected MockHttpServletResponse setupResponse(GrailsWebRequest webRequest) {
        return webRequest.response as MockHttpServletResponse
    }

    protected GrailsWebRequest boundMimeTypeRequest() {
        def servletContext = new MockServletContext()
        def ctx = new GenericWebApplicationContext(servletContext)
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ctx)
        def application = new DefaultGrailsApplication()
        application.config = testConfig
        ctx.beanFactory.registerSingleton(MimeType.BEAN_NAME, new DefaultMimeUtility(buildMimeTypes(application)))
        ctx.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        ctx.refresh()
        GrailsWebMockUtil.bindMockWebRequest(ctx)
    }

    String applicationConfigText = '''
        grails.mime.file.extensions = true // enables the parsing of file extensions from URLs into the request format
        grails.mime.use.accept.header = true
        grails.mime.types = [
            all: '*/*',
            html: ['text/html','application/xhtml+xml'],
            xml: ['text/xml', 'application/xml'],
            text: 'text/plain',
            js: 'text/javascript',
            rss: 'application/rss+xml',
            atom: 'application/atom+xml',
            css: 'text/css',
            csv: 'text/csv',
            json: ['application/json','text/json'],
            form: 'application/x-www-form-urlencoded',
            hal: ['application/hal+json','application/hal+xml'],
            multipartForm: 'multipart/form-data'
        ]
    '''.stripIndent()

    protected Config getTestConfig() {
        def config = new ConfigSlurper().parse(String.valueOf(applicationConfigText))
        def propertySources = new MutablePropertySources()
        propertySources.addLast(new MapPropertySource('grails', config))
        return new PropertySourcesConfig(propertySources)
    }

    private MimeType[] buildMimeTypes(application) {
        final def mainContext = new GenericApplicationContext()
        mainContext.refresh()
        application.setApplicationContext(mainContext)

        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        final ApplicationContext context = bb.createApplicationContext()
        final MimeTypesConfiguration mimeTypesConfiguration = context.getBean(MimeTypesConfiguration)
        mimeTypesConfiguration.mimeTypes()
    }

    protected static String toCompactXml(String xml) {
        xml.strip().replaceAll(/\n/, '').replaceAll(/>\s*</, '><')
    }

    protected MappingContext getMappingContext() {
        def context = new KeyValueMappingContext('')
        context.addPersistentEntity(Book)
        context.addPersistentEntity(Author)
        return context
    }

    protected LinkGenerator getLinkGenerator(Closure mappings) {
        def generator = new DefaultLinkGenerator('http://localhost', null)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = getUrlMappingsHolder(mappings)
        return generator
    }

    protected UrlMappingsHolder getUrlMappingsHolder(Closure mappings) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def allMappings = evaluator.evaluateMappings mappings
        return new DefaultUrlMappingsHolder(allMappings)
    }
}

@Linkable
class Product {
    String name
    Category category
}

class Category {
    String name
}

@Entity
@Resource
@SuppressWarnings('unused')
class Book {
    Date dateCreated
    Date lastUpdated
    String title
    Author author
    List authors
    static hasMany = [authors: Author]

    static create(Long id, String title, Author author) {
        def book = new Book(title: title, author: author)
        book.id = id
        book.authors = []
        return book
    }
}

@Entity
@Resource
@SuppressWarnings('unused')
class Author {
    Date dateCreated
    Date lastUpdated
    String name

    static create(Long id, String name) {
        def author = new Author(name: name)
        author.id = id
        return author
    }
}

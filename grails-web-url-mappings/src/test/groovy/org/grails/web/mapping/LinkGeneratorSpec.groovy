package org.grails.web.mapping

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.util.GrailsWebMockUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMappingsHolder

import org.grails.plugins.CoreGrailsPlugin
import org.grails.web.util.WebUtils
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Issue
import spock.lang.Specification

 /**
 * Tests for the {@link org.grails.web.mapping.DefaultLinkGenerator} class
 */
class LinkGeneratorSpec extends Specification {

    def baseUrl = "http://myserver.com/foo"
    def context = "/bar"
    def resourcePath = ''
    def someAbsoluteUrl = "http://www.grails.org/"
    def resource = null
    def linkParams = [:]
    def pluginManager

    def mainCssResource = [dir:'css', file:'main.css']

    def setup() {
        WebUtils.clearGrailsWebRequest()
    }


    def "relative links contain the context with resource path"() {
        when:
        resource = mainCssResource

        then:
        link == "$context/$resource.dir/$resource.file"

        when:
        resourcePath = '/foo'
        resource = mainCssResource

        then:
        link == "$context$resourcePath/$resource.dir/$resource.file"
    }

    def "Test absolute link"() {
        when:
            linkParams.uri = someAbsoluteUrl
            linkParams.absolute = true
        then:
            link == someAbsoluteUrl

        when:
            linkParams.uri = someAbsoluteUrl

        then:
            link == someAbsoluteUrl
    }

    def "Test create link with root URI"() {
        when:
            linkParams.uri = '/'

        then:
            link == '/bar/'

        when:
            linkParams.uri = ''

        then:
            link == '/bar'
    }

    def "Test create relative link with custom context"() {
        when: "No custom context path specified"
            linkParams.controller = 'one'
            linkParams.action = 'two'

        then: "The default is used"
            link == '/bar/one/two'

        when: "A custom context path is specified"
            linkParams.contextPath = '/different'
            linkParams.controller = 'one'
            linkParams.action = 'two'

        then: "The custom context path is used"
            link == '/different/one/two'

       when: "A blank context path is specified"
            linkParams.contextPath = ''
            linkParams.controller = 'one'
            linkParams.action = 'two'

        then: "No context path is used"
            link == '/one/two'
    }

    def "absolute links contains the base url and context when cached"() {
        when:
            resource = mainCssResource + [absolute:true]

        then:
            cachedLink == "$baseUrl/$resource.dir/$resource.file"
            cachedLink == "$baseUrl/$resource.dir/$resource.file"
    }

    def "absolute links contains the base url and context"() {
        when:
            resource = mainCssResource + [absolute:true]

        then:
            link == "$baseUrl/$resource.dir/$resource.file"
    }

    def "relative links contain the context"() {
        when:
            resource = mainCssResource

        then:
            link == "$context/$resource.dir/$resource.file"
    }

    def "default to absolute links when no context path is specified"() {
        given:
            context = null

        when:
            resource = mainCssResource

        then:
            link == "$baseUrl/$resource.dir/$resource.file"
    }

    def "plugin paths are resolved with the plugin attribute"() {
        given:
            plugins = [CoreGrailsPlugin]

        and:
            def pluginName = "core"
            def pluginVersion = pluginManager.getGrailsPlugin(pluginName).version

        when:
            resource = mainCssResource + [plugin: pluginName]

        then:
            link == "$context/plugins/$pluginName-$pluginVersion/$resource.dir/$resource.file"
    }

    def "link contains given explicit context path"() {
        given:
            def customContextPath = "/test"

        when:
            resource = mainCssResource + [contextPath: customContextPath]

        then:
            link == "$customContextPath/$resource.dir/$resource.file"
    }

    def "link has no context path if blank context supplied"() {
        given:
            def customContextPath = ""

        when:
            resource = mainCssResource + [contextPath: customContextPath]

        then:
            link == "/$resource.dir/$resource.file"
    }

    def "test absolute links created from request scheme"() {

        given:
            final webRequest = GrailsWebMockUtil.bindMockWebRequest()
            MockHttpServletRequest request = webRequest.currentRequest

        when:
            baseUrl = null
            resource = mainCssResource + [absolute:true]
            webRequest.baseUrl = null
        then:
            link == "http://localhost/$resource.dir/$resource.file"

        when:
            request.serverPort = 8081
            webRequest.baseUrl = null
        then:
            link == "http://localhost:8081/$resource.dir/$resource.file"

        when:
            request.contextPath = "/blah"
            request.serverPort = 8081
            webRequest.baseUrl = null
        then:
            link == "http://localhost:8081/blah/$resource.dir/$resource.file"
    }

    def "caching should take request Host header, scheme and port in to account"() {

        given:
            final webRequest = GrailsWebMockUtil.bindMockWebRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            baseUrl = null
            def cachingGenerator = getGenerator(true)

        when:
            resource = mainCssResource + [absolute:true]
            def cachedlink = cachingGenerator.resource(resource)

        then:
            cachedlink == "http://localhost/$resource.dir/$resource.file"

        when:
            request.serverName = "some.other.host"
            request.scheme = "https"
            request.serverPort = 443
            webRequest.baseUrl = null
            cachedlink = cachingGenerator.resource(resource)
        then:
            cachedlink == "https://some.other.host/$resource.dir/$resource.file"

        when:
            request.serverName = "localhost"
            request.scheme = "http"
            request.serverPort = 8081
            webRequest.baseUrl = null
            cachedlink = cachingGenerator.resource(resource)
        then:
            cachedlink == "http://localhost:8081/$resource.dir/$resource.file"

        when:
            request.contextPath = "/blah"
            request.serverPort = 8081
            webRequest.baseUrl = null
            cachedlink = cachingGenerator.resource(resource)
        then:
            cachedlink == "http://localhost:8081/blah/$resource.dir/$resource.file"
    }
        
    
    def "caching should ignore request.baseUrl when base is provided for absolute links"() {

        given:
            final webRequest = GrailsWebMockUtil.bindMockWebRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            baseUrl = null
            def cachingGenerator = getGenerator(true)

        when:
            def cacheKey = cachingGenerator.makeKey(CachingLinkGenerator.RESOURCE_PREFIX, [:]);
        then:
            cacheKey == "resource[:][]"

        when:
            cacheKey = cachingGenerator.makeKey(CachingLinkGenerator.RESOURCE_PREFIX, [absolute:true]);
        then:
            cacheKey == "resourcehttp://localhost[absolute:true]"
        when:
            cacheKey = cachingGenerator.makeKey(CachingLinkGenerator.RESOURCE_PREFIX, [absolute:true, base: "http://some.other.host"]);
        then:
            cacheKey == "resourcehttp://some.other.host[absolute:true, base:http://some.other.host]"
    }
    
    @Issue('GRAILS-10883')
    def 'cache key should use identity of resource value'() {
        given:
            final webRequest = GrailsWebMockUtil.bindMockWebRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            baseUrl = null
            def cachingGenerator = getGenerator(true)
            def w1 = new Widget(id: 1, name: 'Some Widget')
            def w2 = new Widget(id: 2, name: 'Some Widget')

        when:
            def cacheKey = cachingGenerator.makeKey('somePrefix', [resource:w1]);
        then:
            cacheKey == "somePrefix[resource:org.grails.web.mapping.Widget->1]"
        when:
            cacheKey = cachingGenerator.makeKey('somePrefix', [resource:w2]);
        then:
            cacheKey == "somePrefix[resource:org.grails.web.mapping.Widget->2]"
    }

    @Issue('https://github.com/apache/grails-core/issues/13627')
    def 'resource links should use ident and allow controller override'() {
        given:
        final webRequest = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest request = webRequest.currentRequest
        linkParams.method = 'GET'

        when: 'a resource is specified, ident() is used for id'
        linkParams.resource = new Widget(id: 1, name: 'Some Widget')

        then:
        link == "/bar/widget/show/1"

        then:
        linkParams.resource.identCalled

        when: "A controller is specified"
        linkParams.controller = 'widgetAdmin'

        then:
        link == "/bar/widgetAdmin/show/1"
    }

    def 'link should take into affect namespace'() {
        given:
        final webRequest = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest request = webRequest.currentRequest
        linkParams.contextPath = ''

        when: "A namespace is specified"
        linkParams.namespace = 'fooBar'
        linkParams.controller = 'one'
        linkParams.action = 'two'

        then: "it exists in the url"
        link == '/fooBar/one/two'

        when: "The namespace is in the request params"
        webRequest.setControllerNamespace("fooBarReq")
        webRequest.setControllerName('one')
        linkParams.remove('namespace')
        linkParams.controller = 'one'
        linkParams.action = 'two'

        then: "it exists in the url"
        link == '/fooBarReq/one/two'

        when: "The namespace is in the request params and the current controller is different"
        webRequest.setControllerNamespace("fooBarReq")
        webRequest.setControllerName("abc")
        linkParams.controller = 'one'
        linkParams.action = 'two'

        then: "it is not included in the URL"
        link == '/one/two'

        when: "Params and the request attribute exist"
        webRequest.setControllerNamespace("fooBarReq")
        linkParams.namespace = 'fooBarParam'
        linkParams.controller = 'one'
        linkParams.action = 'two'

        then: "params wins"
        link == '/fooBarParam/one/two'
    }

    
    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    protected getGenerator(boolean cache=false) {
        def generator = cache ? new CachingLinkGenerator(baseUrl, context) : new DefaultLinkGenerator(baseUrl, context)
        final callable = { String controller, String action, String namespace, String pluginName, String httpMethod, Map params ->
            [createRelativeURL: { String c, String a, String n, String p, Map parameterValues, String encoding, String fragment ->

                "${namespace ? '/' + namespace : ''}/$controller/$action${parameterValues.id? '/'+parameterValues.id:''}".toString()
            }] as UrlCreator
        }
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        def urlMappingsHolder = [getReverseMapping: callable,getReverseMappingNoDefault: callable] as UrlMappingsHolder

        if(resourcePath != null) {
            generator.resourcePath = resourcePath
        }
        generator.urlMappingsHolder = urlMappingsHolder
        if (pluginManager) {
            generator.pluginManager = pluginManager
        }
        generator
    }

    protected getLink() {
        if (resource != null) {
            getGenerator().resource(resource)
        }
        else {
            getGenerator().link(linkParams)
        }
    }

    protected getCachedLink() {
        if (resource != null) {
            getGenerator(true).resource(resource)
        }
        else {
            getGenerator(true).link(linkParams)
        }
    }

    protected setPlugins(List<Class> pluginClasses) {
        pluginManager = new DefaultGrailsPluginManager(pluginClasses as Class[], new DefaultGrailsApplication())
        pluginManager.loadPlugins()
    }
}

@Artefact('Domain')
class Widget {
    Long id
    String name
    boolean identCalled = false
    
    Long ident() {
        identCalled = true
        id
    }
    
    String toString() {
        name
    }
}

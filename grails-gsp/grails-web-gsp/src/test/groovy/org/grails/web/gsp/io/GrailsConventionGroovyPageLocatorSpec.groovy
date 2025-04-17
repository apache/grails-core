package org.grails.web.gsp.io

import grails.core.DefaultGrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.metadata.GrailsPlugin
import grails.util.GrailsUtil
import grails.util.GrailsWebMockUtil
import groovy.xml.XmlSlurper
import org.grails.core.io.SimpleMapResourceLoader
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.gsp.io.GroovyPageCompiledScriptSource
import org.grails.gsp.io.GroovyPageResourceScriptSource
import org.grails.plugins.BinaryGrailsPlugin
import org.grails.plugins.BinaryGrailsPluginDescriptor
import org.grails.plugins.CoreGrailsPlugin
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

class GrailsConventionGroovyPageLocatorSpec extends Specification {

    SimpleMapResourceLoader resourceLoader = new SimpleMapResourceLoader()

    void "Test find view from binary plugin"() {
        when:"Finding a view located in a binary plugin"
            def source = pageLocator.findView(new TestController(), "binaryView")

        then:"The view is found"
            source instanceof GroovyPageCompiledScriptSource
            source != null
            !source.isPublic()
    }
    void "Test find template with controller instance and view name"() {
        given: "a simple resource loader with a path to the view"
            resourceLoader.resources["/grails-app/views/test/_bar.gsp"] = new ByteArrayResource("contents".bytes) {
                @Override
                URL getURL() {
                    return new URL("file://myapp/grails-app/views/test/_bar.gsp")
                }
            }
        when: "The controller and template name is specified"
            def source = pageLocator.findTemplate(new TestController(), "bar")
        then: "the script source is found"
            source != null
            source.URI == '/test/_bar.gsp'
            source instanceof GroovyPageResourceScriptSource
            !source.isPublic()

        when:"A non-existent template is queried"
            source = pageLocator.findTemplate(new TestController(), "notThere")
        then:"source is null"
            source == null
    }

    void "Test find view with controller instance and view name"() {
        given: "a simple resource loader with a path to the view"
            resourceLoader.resources["/grails-app/views/test/bar.gsp"] = new ByteArrayResource("contents".bytes)
        when: "The controller and view name is specified"
            def source = pageLocator.findView(new TestController(), "bar")
        then: "the script source is found"
            source != null
            source.URI == '/test/bar.gsp'

        when:"A non-existent view is queried"
            source = pageLocator.findView(new TestController(), "notThere")
        then:"source is null"
            source == null
    }

    void "Test find view with controller instance, view name and specified response format"() {
        setup:
            def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when: "The controller and view name is specified as well as a response format of xml"
            resourceLoader.resources["/grails-app/views/test/bar.xml.gsp"] = new ByteArrayResource("contents".bytes)
            resourceLoader.resources["/grails-app/views/test/bar.gsp"] = new ByteArrayResource("contents".bytes)

            webRequest.request.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT, "xml")
            def source = pageLocator.findView(new TestController(), "bar")

        then: "the script source for the xml view is found"
            source != null
            source.URI == '/test/bar.xml.gsp'

        when: "no response format is specified"
            webRequest.request.removeAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT)
            source = pageLocator.findView(new TestController(), "bar")

        then: "the default view is found"
            source != null
            source.URI == '/test/bar.gsp'

        when:"A non-existent view is queried"
            source = pageLocator.findView(new TestController(), "notThere")
        then:"source is null"
            source == null

        cleanup:
            RequestContextHolder.resetRequestAttributes()
    }

    void "Test find view with controller instance and view name from plugin"() {
        given: "a valid path to a plugin view"
            resourceLoader.resources["/grails-app/views/plugins/core-${GrailsUtil.grailsVersion}/grails-app/views/bar.gsp"] = new ByteArrayResource("contents".bytes)
        when: "The controller from a plugin and view name is specified"
            def source = pageLocator.findView(new PluginController(), "bar")
        then: "the script source is found"
            source != null
            source.URI == "/plugins/core-${GrailsUtil.grailsVersion}/grails-app/views/bar.gsp"

        when:"A non-existent view is queried"
            source = pageLocator.findView(new PluginController(), "notThere")
        then:"source is null"
            source == null
    }

    void "Test find view with controller name and view name"() {
        given: "a simple resource loader with a path to the view"
            resourceLoader.resources["/grails-app/views/foo/bar.gsp"] = new ByteArrayResource("contents".bytes)
        when: "The controller and view name is specified"
            def source = pageLocator.findView("foo", "bar")
        then: "the script source is found"
            source != null
            source.URI == '/foo/bar.gsp'

        when:"A non-existent view is queried"
            source = pageLocator.findView("not", "there")
        then:"source is null"
            source == null
    }

    void "Test find view by path"() {
        given: "a simple resource loader with a path to the view"
            resourceLoader.resources["/grails-app/views/foo/bar.gsp"] = new ByteArrayResource("contents".bytes) {
                @Override
                URL getURL() {
                    return new URL("file://myapp/grails-app/views/foo/bar.gsp")
                }
            }
        when: "The controller and view name is specified"
            def source = pageLocator.findViewByPath("/foo/bar")
        then: "the script source is found"
            source != null
            source.URI == '/foo/bar.gsp'
            !source.isPublic()

        when:"A non-existent view is queried"
            source = pageLocator.findViewByPath("/not/there")

        then:"source is null"
            source == null
    }

    void "Test find public view by path"() {
        given: "a simple resource loader with a path to the view"
            resourceLoader.resources["/foo/bar.gsp"] = new ByteArrayResource("contents".bytes)
        when: "The controller and view name is specified"
            def source = pageLocator.findViewByPath("/foo/bar")
        then: "the script source is found"
            source != null
            source.URI == '/foo/bar.gsp'
            source.isPublic()

        when:"A non-existent view is queried"
            source = pageLocator.findViewByPath("/not/there")

        then:"source is null"
            source == null
    }

    void "Test find template by controller and template name"() {
        given: "a simple resource loader with a path to the view"
            resourceLoader.resources["/grails-app/views/foo/_bar.gsp"] = new ByteArrayResource("contents".bytes)
        when: "The controller and view name is specified"
            def source = pageLocator.findTemplate("foo", "bar")
        then: "the script source is found"
            source != null
            source.URI == '/foo/_bar.gsp'

        when:"A non-existent view is queried"
            source = pageLocator.findTemplate("not", "there")
        then:"source is null"
            source == null
    }

    void "Test find template by path"() {
        given: "a simple resource loader with a path to the view"
            resourceLoader.resources["/grails-app/views/foo/_bar.gsp"] = new ByteArrayResource("contents".bytes)
        when: "The controller and view name is specified"
            def source = pageLocator.findTemplateByPath("/foo/bar")
        then: "the script source is found"
            source != null
            source.URI == '/foo/_bar.gsp'

        when:"A non-existent view is queried"
            source = pageLocator.findTemplateByPath("/not/there")
        then:"source is null"
            source == null
    }

    GrailsConventionGroovyPageLocator getPageLocator() {
        GrailsConventionGroovyPageLocator locator = new GrailsConventionGroovyPageLocator()
        def str = '''
<plugin name='testBinary'>
  <class>TestBinaryGrailsPlugin</class>
  <resources>
         <resource>org.grails.web.gsp.io.TestBinaryResource</resource>
  </resources>
</plugin>
'''

        def xml = new XmlSlurper().parseText(str)

        def resource = new MockBinaryPluginResource(str.bytes)
        def descriptor = new BinaryGrailsPluginDescriptor(resource, ['org.grails.web.gsp.io.TestBinaryResource'])
        resource.relativesResources['static/css/main.css'] = new ByteArrayResource(''.bytes)
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())
        GroovyPageParser gpp = new GroovyPageParser("binaryView","/test/binaryView.gsp","/test/binaryView.gsp",new ByteArrayInputStream("hello world".bytes), "UTF-8", "HTML", null)
        gpp.packageName = "foo.bar"
        gpp.className = "test_binary_view"
        gpp.lastModified = System.currentTimeMillis()
        final sw = new StringWriter()
        gpp.generateGsp(sw)

        binaryPlugin.@precompiledViewMap["/WEB-INF/grails-app/views/test/binaryView.gsp"] = new GroovyClassLoader().parseClass(sw.toString())

        def pluginManager = new DefaultGrailsPluginManager([CoreGrailsPlugin] as Class[], new DefaultGrailsApplication())

        pluginManager.loadPlugins()
        pluginManager.@pluginList << binaryPlugin
        locator.pluginManager = pluginManager

        locator.addResourceLoader(resourceLoader)
        return locator
    }
}
class TestBinaryGrailsPlugin {
    def version = 1.0
}
class TestBinaryResource {}

class TestController {}

@GrailsPlugin(name="core", version="0.1")
class PluginController {}

class MockBinaryPluginResource extends ByteArrayResource {

    Map<String, Resource> relativesResources = [:]

    MockBinaryPluginResource(byte[] byteArray) {
        super(byteArray)
    }

    @Override
    Resource createRelative(String relativePath) {
        return relativesResources[relativePath]
    }
}

package grails.plugin.json.view.test

import grails.config.Config
import grails.plugin.json.view.api.JsonView
import grails.util.TypeConvertingMap
import grails.views.api.HttpView
import grails.views.api.http.Request
import grails.views.mvc.http.DelegatingParameters
import groovy.transform.CompileStatic
import org.springframework.http.HttpMethod

/**
 * Allows configuring the JSON view rendering for request state
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
class TestRequestConfigurer implements Request {

    final JsonView jsonView

    String contextPath
    String method = "GET"
    String uri
    String contentType = 'application/json'
    String characterEncoding = 'UTF-8'
    Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>().withDefault { String name ->
        return [] as List<String>
    }
    Map<String, Object> attributes = new LinkedHashMap<String, Object>()

    TestRequestConfigurer(JsonView jsonView) {
        this.jsonView = jsonView
        if(jsonView instanceof HttpView) {
            ((HttpView)jsonView).setRequest(this)
        }
    }

    void setActionName(String actionName) {
        jsonView.setActionName(actionName)
    }

    void setControllerName(String controllerName) {
        jsonView.setControllerName(controllerName)
    }

    void setControllerNamespace(String controllerNamespace) {
        jsonView.setControllerNamespace(controllerNamespace)
    }

    void setConfig(Config config) {
        jsonView.setConfig(config)
    }


    TestRequestConfigurer actionName(String actionName) {
        this.actionName = actionName
        return this
    }

    TestRequestConfigurer controllerName(String controllerName) {
        this.controllerName = controllerName
        return this
    }

    TestRequestConfigurer controllerNamespace(String controllerNamespace) {
        this.controllerNamespace = controllerNamespace
        return this
    }

    TestRequestConfigurer config(Config config) {
        this.config = config
        return this
    }

    TestRequestConfigurer method(String method) {
        this.method = method
        return this
    }

    TestRequestConfigurer header(String name, String value) {
        this.headers.get(name).add(value)
        return this
    }

    TestRequestConfigurer attribute(String name, Object value) {
        this.attributes.put(name, value)
        return this
    }

    TestRequestConfigurer params(Map parameters) {
        this.jsonView.setParams( new DelegatingParameters(new TypeConvertingMap(parameters)))
        return this
    }

    TestRequestConfigurer method(HttpMethod method) {
        this.method = method.toString()
        return this
    }

    @Override
    Collection<String> getHeaderNames() {
        return headers.keySet()
    }

    @Override
    String getHeader(String name) {
        def headerValues = headers.get(name)
        if(headerValues) {
            return headerValues.get(0)
        }
        return null
    }

    @Override
    Collection<String> getHeaders(String name) {
        headers.get(name)
    }

    @Override
    Object getAttribute(String name) {
        attributes.get(name)
    }

    @Override
    Collection<String> getAttributeNames() {
        attributes.keySet()
    }
}

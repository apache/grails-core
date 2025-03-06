package grails.plugin.json.view.test

import grails.core.GrailsApplication
import grails.plugin.json.view.JsonViewConfiguration
import grails.plugin.json.view.JsonViewTemplateEngine
import grails.plugin.json.view.api.JsonView
import grails.plugin.json.view.api.jsonapi.DefaultJsonApiIdRenderer
import grails.plugin.json.view.api.jsonapi.JsonApiIdRenderStrategy
import grails.util.GrailsMessageSourceUtils
import grails.views.api.HttpView
import grails.views.api.http.Response
import grails.web.mapping.LinkGenerator
import grails.web.mime.MimeUtility
import groovy.json.JsonSlurper
import groovy.text.Template
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.HttpStatus

/**
 * A trait that test classes can implement to add support for easily testing JSON views
 *
 * @author Graeme Rocher
 * @since 1.1.0
 */

@CompileStatic
trait JsonViewTest {

    MessageSource messageSource = new StaticMessageSource()

    @Autowired(required = false)
    setMessageSource(List<MessageSource> messageSources) {
        setMessageSource(GrailsMessageSourceUtils.findPreferredMessageSource(messageSources))
    }

    void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    @Autowired(required = false)
    MappingContext mappingContext = {
        def ctx = new KeyValueMappingContext("test")
        ctx.setCanInitializeEntities(true)
        return ctx
    }()

    @Autowired(required = false)
    MimeUtility mimeUtility

    @Autowired(required = false)
    LinkGenerator linkGenerator = new TestLinkGenerator(mappingContext)

    @Autowired(required = false)
    JsonViewConfiguration viewConfiguration = new JsonViewConfiguration()

    @Autowired(required = false)
    void setGrailsApplication(GrailsApplication grailsApplication) {
        viewConfiguration.setGrailsApplication(grailsApplication)
    }

    @Autowired(required = false)
    JsonApiIdRenderStrategy jsonApiIdRenderStrategy = new DefaultJsonApiIdRenderer()

    @Lazy JsonViewTemplateEngine templateEngine = {

        def templateEngine = new JsonViewTemplateEngine(viewConfiguration, Thread.currentThread().contextClassLoader)
        if(messageSource != null) {
            templateEngine.setMessageSource(messageSource)
        }
        templateEngine.setLinkGenerator(linkGenerator)
        if(mimeUtility != null) {
            templateEngine.setMimeUtility(mimeUtility)
        }
        if(mappingContext != null) {
            templateEngine.setMappingContext(mappingContext)
        }
        templateEngine.setJsonApiIdRenderStrategy(jsonApiIdRenderStrategy)
        return templateEngine
    }()

    /**
     * Render a template for the given source
     *
     * @param source The raw source of the template
     * @param model The model
     *
     * @return The result
     */
    JsonRenderResult render(String source) {
        render(source, Collections.emptyMap(), null)
    }

    /**
     * Render a template for the given source
     *
     * @param source The raw source of the template
     * @param model The model
     *
     * @return The result
     */
    JsonRenderResult render(String source, Map model) {
        render(source, model, null)
    }

    /**
     * Render a template for the given source
     *
     * @param source The raw source of the template
     * @param model The model
     * @param configurer The configurer
     *
     * @return The result
     */
    JsonRenderResult render(String source, Map model, @DelegatesTo(TestRequestConfigurer) Closure configurer) {
        def template = templateEngine.createTemplate(source)
        return produceResult(template, model, configurer)
    }

    /**
     * Render a template for the given source
     *
     * @param source The raw source of the template
     * @param model The model
     * @param configurer The configurer
     *
     * @return The result
     */
    JsonRenderResult render(String source, @DelegatesTo(TestRequestConfigurer) Closure configurer) {
        def template = templateEngine.createTemplate(source)
        return produceResult(template, Collections.emptyMap(), configurer)
    }
    /**
     * Render one of the GSON views in the grails-app/views directory for the given arguments
     *
     * @param arguments The named arguments: 'template', 'view' and 'model'
     *
     * @return The render result
     */
    JsonRenderResult render(Map arguments) {
        render arguments, null
    }

    /**
     * Render one of the GSON views in the grails-app/views directory for the given arguments
     *
     * @param arguments The named arguments: 'template', 'view' and 'model'
     * @param configurer The configurer for modifying the request state of the view
     *
     * @return The render result
     */
    JsonRenderResult render(Map arguments, @DelegatesTo(TestRequestConfigurer) Closure configurer) {

        String viewUri
        if( arguments.template ) {
            viewUri = templateEngine
                            .viewUriResolver
                            .resolveTemplateUri(null, arguments.template.toString())

        }
        else if( arguments.view ) {
            viewUri = arguments.view.toString()
        }
        else {
            throw new IllegalArgumentException("Either a 'view' or 'template' argument is required!")

        }
        def template = templateEngine.resolveTemplate(viewUri)

        if(template == null) {
            throw new IllegalArgumentException("No view or template found for URI $viewUri")
        }

        def model = arguments.model instanceof Map ? (Map)arguments.model : [:]
        return produceResult(template, model, configurer)
    }

    private JsonRenderResult produceResult(Template template, Map model, Closure configurer) {
        JsonView writable = (JsonView) template.make(model)
        if (configurer != null) {
            def rc = new TestRequestConfigurer(writable)
            configurer.delegate = rc
            configurer.call()
        }

        def result = new JsonRenderResult()
        if (writable instanceof HttpView) {
            def httpView = (HttpView) writable
            httpView.setResponse(new TestHttpResponse(result))
        }

        def sw = new StringWriter()
        writable.writeTo(sw)
        def str = sw.toString()
        result.jsonText = str
        result.json = new JsonSlurper().parseText(str)
        return result
    }

    static class TestHttpResponse implements Response {
        final JsonRenderResult result

        TestHttpResponse(JsonRenderResult result) {
            this.result = result
        }

        @Override
        void header(String name, String value) {
            result.headers[name] = value
        }

        @Override
        void header(Map<String, String> nameAndValue) {
            result.headers.putAll(nameAndValue)
        }

        @Override
        void headers(Map<String, String> namesAndValues) {
            result.headers.putAll(namesAndValues)
        }

        @Override
        void contentType(String contentType) {
            result.contentType = contentType
        }

        @Override
        void encoding(String encoding) {
            // ignore
        }

        @Override
        void status(int status) {
            result.status = HttpStatus.valueOf(status)
        }

        @Override
        void status(int status, String message) {
            result.status = HttpStatus.valueOf(status)
            result.message = message
        }

        @Override
        void status(HttpStatus status) {
            result.status = status
            result.message = status.getReasonPhrase()
        }

        @Override
        void status(HttpStatus status, String message) {
            result.status = status
            result.message = message
        }
    }
}
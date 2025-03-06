package grails.plugin.json.view.mvc

import grails.core.support.proxy.ProxyHandler
import grails.plugin.json.renderer.ErrorsJsonViewRenderer
import grails.plugin.json.renderer.JsonViewJsonRenderer
import grails.plugin.json.view.JsonViewTemplateEngine
import grails.plugin.json.view.JsonViewConfiguration
import grails.plugin.json.view.JsonViewWritableScript
import grails.rest.render.RendererRegistry
import grails.views.mvc.SmartViewResolver
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.Errors

import jakarta.annotation.PostConstruct
/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class JsonViewResolver extends SmartViewResolver {

    public static final String JSON_VIEW_SUFFIX = ".${JsonViewWritableScript.EXTENSION}"

    @Autowired(required = false)
    ProxyHandler proxyHandler

    @Autowired(required = false)
    RendererRegistry rendererRegistry

    JsonViewConfiguration viewConfiguration

    JsonViewResolver(JsonViewConfiguration configuration = new JsonViewConfiguration(), ClassLoader classLoader = Thread.currentThread().contextClassLoader) {
        this(new JsonViewTemplateEngine(configuration, classLoader))
    }

    JsonViewResolver(JsonViewTemplateEngine templateEngine) {
        this(templateEngine, JSON_VIEW_SUFFIX, MimeType.JSON.name)
    }

    JsonViewResolver(JsonViewTemplateEngine templateEngine, String suffix, String contentType) {
        super(templateEngine, suffix, contentType)
        viewConfiguration = (JsonViewConfiguration)templateEngine.viewConfiguration
    }

    @PostConstruct
    void initialize() {
        if(rendererRegistry != null) {
            def errorsRenderer = new ErrorsJsonViewRenderer((Class)Errors)
            errorsRenderer.setJsonViewResolver(this)
            rendererRegistry.addRenderer(errorsRenderer)
            def defaultJsonRenderer = rendererRegistry.findRenderer(MimeType.JSON, Object.class)
            viewConfiguration.mimeTypes.each { String mimeTypeString ->
                MimeType mimeType = new MimeType(mimeTypeString, "json")
                rendererRegistry.addDefaultRenderer(
                    new JsonViewJsonRenderer<Object>(Object.class, mimeType, this , proxyHandler, rendererRegistry, defaultJsonRenderer)
                )
            }

        }
    }
}

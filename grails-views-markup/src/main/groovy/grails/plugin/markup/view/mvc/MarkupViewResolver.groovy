package grails.plugin.markup.view.mvc

import grails.core.support.proxy.ProxyHandler
import grails.plugin.markup.view.MarkupViewConfiguration
import grails.plugin.markup.view.MarkupViewTemplate
import grails.plugin.markup.view.MarkupViewTemplateEngine
import grails.plugin.markup.view.renderer.MarkupViewXmlRenderer
import grails.rest.render.RendererRegistry
import grails.views.mvc.SmartViewResolver
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import jakarta.annotation.PostConstruct

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class MarkupViewResolver extends SmartViewResolver {

    public static final String MARKUP_VIEW_SUFFIX = ".${MarkupViewTemplate.EXTENSION}"


    @Autowired(required = false)
    ProxyHandler proxyHandler

    @Autowired(required = false)
    RendererRegistry rendererRegistry

    MarkupViewConfiguration viewConfiguration

    MarkupViewResolver(MarkupViewConfiguration configuration) {
        this(new MarkupViewTemplateEngine(configuration), ".$configuration.extension", MimeType.XML.name)
    }

    MarkupViewResolver(MarkupViewTemplateEngine templateEngine) {
        this(templateEngine, MARKUP_VIEW_SUFFIX, MimeType.XML.name)
    }

    MarkupViewResolver(MarkupViewTemplateEngine templateEngine, String suffix, String contentType) {
        super(templateEngine, suffix, contentType)
        viewConfiguration = (MarkupViewConfiguration)templateEngine.viewConfiguration
    }

    @PostConstruct
    void initialize() {
        if(rendererRegistry != null) {
            def defaultXmlRenderer = rendererRegistry.findRenderer(MimeType.XML, Object.class)
            viewConfiguration.mimeTypes.each { String mimeTypeString ->
                MimeType mimeType = new MimeType(mimeTypeString, "xml")
                rendererRegistry.addDefaultRenderer(
                    new MarkupViewXmlRenderer<Object>(Object.class, mimeType, this , proxyHandler, rendererRegistry, defaultXmlRenderer)
                )
            }
        }
    }
}

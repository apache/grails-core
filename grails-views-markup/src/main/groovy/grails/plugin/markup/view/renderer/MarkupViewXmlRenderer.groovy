package grails.plugin.markup.view.renderer

import grails.core.support.proxy.ProxyHandler
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.views.mvc.SmartViewResolver
import grails.views.mvc.renderer.DefaultViewRenderer
import grails.web.mime.MimeType

/**
 * Integration with the Grails renderer framework
 *
 * @author Graeme Rocher
 * @since 1.0
 *
 */
class MarkupViewXmlRenderer<T> extends DefaultViewRenderer<T> {
    MarkupViewXmlRenderer(Class<T> targetType, SmartViewResolver viewResolver, ProxyHandler proxyHandler, RendererRegistry rendererRegistry, Renderer defaultRenderer) {
        super(targetType, MimeType.XML, viewResolver, proxyHandler, rendererRegistry, defaultRenderer)
    }

    MarkupViewXmlRenderer(Class<T> targetType, MimeType mimeType, SmartViewResolver viewResolver, ProxyHandler proxyHandler, RendererRegistry rendererRegistry, Renderer defaultRenderer) {
        super(targetType, mimeType, viewResolver, proxyHandler, rendererRegistry, defaultRenderer)
    }
}

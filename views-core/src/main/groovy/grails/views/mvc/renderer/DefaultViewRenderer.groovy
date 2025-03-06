package grails.views.mvc.renderer

import grails.core.support.proxy.ProxyHandler
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.views.mvc.SmartViewResolver
import grails.views.resolve.TemplateResolverUtils
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.AbstractUrlBasedView

/**
 * A renderer implementation that looks up a view from the ViewResolver
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@InheritConstructors
@CompileStatic
abstract class DefaultViewRenderer<T> extends DefaultHtmlRenderer<T> {
    public static final String MODEL_OBJECT = 'object'
    final SmartViewResolver viewResolver

    final ProxyHandler proxyHandler

    final RendererRegistry rendererRegistry

    final Renderer defaultRenderer


    DefaultViewRenderer(Class<T> targetType, MimeType mimeType, SmartViewResolver viewResolver, ProxyHandler proxyHandler, RendererRegistry rendererRegistry, Renderer defaultRenderer) {
        super(targetType,mimeType)
        this.viewResolver = viewResolver
        this.proxyHandler = proxyHandler
        this.rendererRegistry = rendererRegistry
        this.defaultRenderer = defaultRenderer
    }


    @Override
    void render(T object, RenderContext context) {
        def arguments = context.arguments
        def ct = arguments?.contentType

        if(ct) {
            context.setContentType(ct.toString())
        }
        else {
            final mimeType = context.acceptMimeType ?: mimeTypes[0]
            if (!mimeType.equals(MimeType.ALL)) {
                context.setContentType(mimeType.name)
            }
        }

        String viewName
        if (arguments?.view) {
            viewName = arguments.view.toString()
        }
        else {
            viewName = context.actionName
        }

        String viewUri
        if (viewName?.startsWith('/')) {
            viewUri = viewName
        } else {
           viewUri = "/${context.controllerName}/${viewName}"
        }

        def webRequest = ((ServletRenderContext) context).getWebRequest()
        def request = webRequest.currentRequest
        def response = webRequest.currentResponse

        AbstractUrlBasedView view
        String namespace = webRequest.controllerNamespace
        if (namespace) {
            view = (AbstractUrlBasedView)viewResolver.resolveView("/${namespace}${viewUri}", request, response)
        }
        
        if (view == null) {
            view = (AbstractUrlBasedView)viewResolver.resolveView(viewUri, request, response)
        }

        if(view == null) {
            if(proxyHandler != null) {
                object = (T)proxyHandler.unwrapIfProxy(object)
            }

            def cls = object.getClass()
            // Try resolve template. Example /book/_book
            view = (AbstractUrlBasedView)viewResolver.resolveView(cls, request, response)
        }

        if(view != null) {
            Map<String, ?> model
            if(object instanceof Map) {
                def map = (Map) object
                model = map
                if(view == viewResolver.objectView) {
                    // avoid stack overflow by making a copy of the map
                    model.put(MODEL_OBJECT, new LinkedHashMap(map))
                }
            } else {
                model = [(resolveModelVariableName(object)): object]
                if(view == viewResolver.objectView) {
                    model.put(MODEL_OBJECT, object)
                }
            }
            if (arguments?.model) {
                model.putAll((Map) arguments.model)
            }
            context.setModel(model)
            ModelAndView modelAndView = (ModelAndView) request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
            modelAndView.setView(view)
        }
        else {
            defaultRenderer.render(object, context)
        }
    }

    static String templateNameForClass(Class<?> cls) {
        TemplateResolverUtils.shortTemplateNameForClass(cls)
    }
}

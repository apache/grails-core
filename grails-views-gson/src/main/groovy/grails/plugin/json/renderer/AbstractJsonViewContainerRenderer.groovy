package grails.plugin.json.renderer

import grails.plugin.json.view.mvc.JsonViewResolver
import grails.rest.render.ContainerRenderer
import grails.rest.render.RenderContext
import grails.util.GrailsNameUtils
import grails.views.Views
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.json.DefaultJsonRenderer
import org.springframework.beans.factory.annotation.Autowired

/**
 * A container renderer that looks up JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@InheritConstructors
abstract class AbstractJsonViewContainerRenderer<C,T> extends DefaultJsonRenderer<T> {

    @Autowired
    JsonViewResolver jsonViewResolver

    @Override
    void render(T object, RenderContext context) {
        if(jsonViewResolver != null) {
            String viewUri = "/${context.controllerName}/_${GrailsNameUtils.getPropertyName(targetType)}"
            def webRequest = ((ServletRenderContext) context).getWebRequest()
            if (webRequest.controllerNamespace) {
                viewUri = "/${webRequest.controllerNamespace}" + viewUri
            }
            def view = jsonViewResolver.resolveView(viewUri, context.locale)
            if(view == null) {
                view = jsonViewResolver.resolveView(targetType, context.locale)
            }

            if(view != null) {
                Map<String, Object> model = (Map<String, Object>) [(resolveModelName()): object]
                def contextArguments = context.getArguments()
                def contextModel = contextArguments?.get(Views.MODEL)
                if(contextModel instanceof Map) {
                    model.putAll((Map)contextModel)
                }

                def request = webRequest.currentRequest
                def response = webRequest.currentResponse
                view.render(model, request, response)
            }
            else {
                super.render(object, context)
            }
        }
        else {
            super.render(object, context)
        }
    }

    protected String resolveModelName() {
        GrailsNameUtils.getPropertyName(targetType)
    }
}

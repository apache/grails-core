/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package grails.views.mvc.renderer

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.AbstractUrlBasedView

import grails.core.support.proxy.ProxyHandler
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.views.mvc.SmartViewResolver
import grails.views.resolve.TemplateResolverUtils
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.ProblemDetailResponse
import org.grails.plugins.web.rest.render.SelectiveRenderer
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.web.util.GrailsApplicationAttributes

/**
 * A renderer implementation that looks up a view from the ViewResolver
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@InheritConstructors
@CompileStatic
abstract class DefaultViewRenderer<T> extends DefaultHtmlRenderer<T> implements SelectiveRenderer {

    public static final String MODEL_OBJECT = 'object'
    final SmartViewResolver viewResolver

    final ProxyHandler proxyHandler

    final RendererRegistry rendererRegistry

    final Renderer<?> defaultRenderer

    DefaultViewRenderer(Class<T> targetType, MimeType mimeType, SmartViewResolver viewResolver, ProxyHandler proxyHandler, RendererRegistry rendererRegistry, Renderer<?> defaultRenderer) {
        super(targetType, mimeType)
        this.viewResolver = viewResolver
        this.proxyHandler = proxyHandler
        this.rendererRegistry = rendererRegistry
        this.defaultRenderer = defaultRenderer
    }

    @Override
    void render(Object object, RenderContext context) {
        def arguments = context.arguments
        // A problem carries its own content type, which a view would replace: only the view that respond names renders
        // it, answering with the status the problem written in JSON would have
        boolean problem = object instanceof ProblemDetail
        if (problem && !arguments?.view) {
            renderDefault(object, context)
            return
        }
        def ct = arguments?.contentType

        if (ct) {
            context.setContentType(ct.toString())
        }
        else {
            MimeType mimeType = context.acceptMimeType ?: mimeTypes[0]
            if (problem && !(mimeType in mimeTypes)) {
                // respond writes a problem as JSON for a client that accepts no format a renderer writes, in this
                // renderer's format
                mimeType = mimeTypes[0]
            }
            if (!mimeType.equals(MimeType.ALL)) {
                context.setContentType(mimeType.name)
            }
        }

        AbstractUrlBasedView view = findNamedView(context)
        if (view == null && !problem) {
            if (proxyHandler != null) {
                object = (T) proxyHandler.unwrapIfProxy(object)
            }
            view = findTemplateView(object, context)
        }

        def request = ((ServletRenderContext) context).webRequest.request
        if (view != null) {
            if (problem) {
                ProblemDetailResponse.prepare((ProblemDetail) object, context)
            }
            Map<String, Object> model
            if (object instanceof Map) {
                def map = (Map) object
                model = map
                if (view == viewResolver.objectView) {
                    // avoid stack overflow by making a copy of the map
                    model.put(MODEL_OBJECT, new LinkedHashMap(map))
                }
            } else {
                model = [(resolveModelVariableName(object)): object]
                if (view == viewResolver.objectView) {
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
            renderDefault(object, context)
        }
    }

    /**
     * Whether the renderer renders the object: with a view, the one {@code respond} names or the action's, or the template
     * of the object's class, or with the renderer the format had before views were added. A problem renders only with the
     * view {@code respond} names. {@code respond} passes over the renderer for a value it cannot render, and tries the
     * client's next type.
     */
    @Override
    boolean canRender(Object object, RenderContext context) {
        if (defaultRenderer != null) {
            return true
        }
        if (object instanceof ProblemDetail) {
            return context.arguments?.view && findNamedView(context) != null
        }
        if (findNamedView(context) != null) {
            return true
        }
        findTemplateView(proxyHandler != null ? proxyHandler.unwrapIfProxy(object) : object, context) != null
    }

    /** The view {@code respond} names, or else the action's, in the controller's namespace first. */
    private AbstractUrlBasedView findNamedView(RenderContext context) {
        def arguments = context.arguments
        String viewName = arguments?.view ? arguments.view.toString() : context.actionName
        String viewUri = viewName?.startsWith('/') ? viewName : "/${context.controllerName}/${viewName}"

        def webRequest = ((ServletRenderContext) context).getWebRequest()
        def request = webRequest.request
        def response = webRequest.currentResponse

        AbstractUrlBasedView view = null
        String namespace = webRequest.controllerNamespace
        if (namespace) {
            view = (AbstractUrlBasedView) viewResolver.resolveView("/${namespace}${viewUri}", request, response)
        }
        if (view == null) {
            view = (AbstractUrlBasedView) viewResolver.resolveView(viewUri, request, response)
        }
        view
    }

    /** The template of the object's class, such as {@code /book/_book}. */
    private AbstractUrlBasedView findTemplateView(Object object, RenderContext context) {
        def webRequest = ((ServletRenderContext) context).getWebRequest()
        (AbstractUrlBasedView) viewResolver.resolveView(object.getClass(), webRequest.request, webRequest.currentResponse)
    }

    /**
     * Without a view, the object is rendered by the renderer the format had before views were added. A format nothing
     * else renders, such as XML without the {@code grails-xml} module, is not acceptable: the error is sent, as
     * {@code respond} sends it when it finds no renderer, so that the application's error page applies. A problem is
     * written as {@code application/problem+json} instead, as {@code respond} writes one in a format nothing writes.
     * {@code respond} does not choose the renderer for such a value, which {@link #canRender} says it cannot render, so
     * this applies when the renderer is called directly.
     */
    private void renderDefault(Object object, RenderContext context) {
        Renderer<?> problemRenderer = defaultRenderer == null && object instanceof ProblemDetail ?
                rendererRegistry?.findRenderer(MimeType.JSON, object) : null
        if (defaultRenderer != null) {
            defaultRenderer.render(object, context)
        }
        else if (problemRenderer != null) {
            problemRenderer.render(object, context)
        }
        else if (context instanceof ServletRenderContext) {
            ((ServletRenderContext) context).webRequest.currentResponse.sendError(HttpStatus.NOT_ACCEPTABLE.value())
        }
        else {
            context.setStatus(HttpStatus.NOT_ACCEPTABLE)
        }
    }

    static String templateNameForClass(Class<?> cls) {
        TemplateResolverUtils.shortTemplateNameForClass(cls)
    }
}

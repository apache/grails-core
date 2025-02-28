/*
 * Copyright 2014-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.artefact.controller.support

import grails.io.IOUtils
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.util.GrailsStringUtils
import grails.util.GrailsWebUtil
import grails.web.api.WebAttributes
import grails.web.http.HttpHeaders
import grails.web.mime.MimeType
import grails.web.mime.MimeUtility
import groovy.json.StreamingJsonBuilder
import groovy.transform.CompileStatic
import groovy.transform.Generated
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.StreamingMarkupBuilder
import org.grails.gsp.GroovyPageTemplate
import org.grails.io.support.SpringIOUtils
import org.grails.web.json.JSONElement
import org.grails.web.servlet.mvc.ActionResultTransformer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException
import org.grails.web.servlet.view.CompositeViewResolver
import org.grails.web.servlet.view.GroovyPageView
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.View

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import static org.grails.plugins.web.controllers.metaclass.RenderDynamicMethod.*
/**
 *
 * A trait that adds behavior to allow rendering of objects to the response
 * 
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 3.0
 */
@CompileStatic
trait ResponseRenderer extends WebAttributes {

    private Collection<ActionResultTransformer> actionResultTransformers = []


    private MimeUtility mimeUtility
    private GrailsPluginManager pluginManager

    @Generated
    @Autowired(required = false)
    @Qualifier("grailsMimeUtility")
    void setMimeUtility(MimeUtility mimeUtility) {
        this.mimeUtility = mimeUtility
    }

    @Generated
    @Autowired(required = false)
    void setActionResultTransformers(ActionResultTransformer[] actionResultTransformers) {
        this.actionResultTransformers = actionResultTransformers.toList()
    }

    /**
     * Render the given object to the response
     *
     * @param object The object to render
     */
    @Generated
    void render(object) {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()
        HttpServletResponse response = webRequest.currentResponse
        webRequest.renderView = false
        applyContentType response, null, object

        try {
            response.writer.write object.inspect()
        }
        catch (IOException e) {
            throw new ControllerExecutionException("I/O error obtaining response writer: " + e.getMessage(), e)
        }
    }

    /**
     * Use the given closure to render markup to the response. The markup is assumed to be HTML. Use {@link ResponseRenderer#render(java.util.Map, groovy.lang.Closure)} to change the content type.
     *
     * @param closure The markup to render
     */
    @Generated
    void render(@DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure closure) {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()
        HttpServletResponse response = webRequest.currentResponse

        setContentType response, TEXT_HTML, DEFAULT_ENCODING, true

        renderMarkupInternal webRequest, closure, response
    }

    /**
     * Render the given closure, configured by the named argument map, to the response
     *
     * @param argMap The name arguments
     * @param closure The closure to render
     */
    @Generated
    void render(Map argMap, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure closure) {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()
        HttpServletResponse response = webRequest.currentResponse
        String layoutArg = argMap[ARGUMENT_LAYOUT]?.toString() ?: null

        applyContentType response, argMap, closure
        handleStatusArgument argMap, webRequest, response

        if (BUILDER_TYPE_JSON.equals(argMap.get(ARGUMENT_BUILDER)) || isJSONResponse(response)) {
            renderJsonInternal(response, closure)
            webRequest.renderView = false
        }
        else {
            renderMarkupInternal webRequest, closure, response
        }
        setLayout webRequest.currentRequest, layoutArg
    }

    private void renderJsonInternal(HttpServletResponse response, @DelegatesTo(value = StreamingJsonBuilder.StreamingJsonDelegate.class, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        response.setContentType(GrailsWebUtil.getContentType(MimeType.JSON.getName(), response.getCharacterEncoding() ?: "UTF-8"))
        def jsonBuilder = new StreamingJsonBuilder(response.writer)
        jsonBuilder.call callable
    }

    /**
     * Render the given CharSequence to the response with the give named arguments
     *
     * @param argMap The named arguments
     * @param body The text to render
     */
    @Generated
    void render(Map argMap, CharSequence body) {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()
        HttpServletResponse response = webRequest.currentResponse
        String layoutArg = argMap[ARGUMENT_LAYOUT]?.toString() ?: null

        applyContentType response, argMap, body
        handleStatusArgument argMap, webRequest, response
        render body
        setLayout webRequest.currentRequest, layoutArg
    }

    /**
     * Renders text to the response for the given CharSequence
     *
     * @param txt The text to render
     */
    @Generated
    void render(CharSequence txt) {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()
        HttpServletResponse response = webRequest.currentResponse
        applyContentType response, null, txt
        try {
            PrintWriter writer = response.getWriter()
            if (writer instanceof PrintWriter) {
                ((PrintWriter)writer).print txt
            }
            else {
                writer.write txt.toString()
            }
            writer.flush()
            webRequest.renderView = false
        }
        catch (IOException e) {
            throw new ControllerExecutionException(e.message, e)
        }
    }

    /**
     * Render the given writable to the response using the named arguments to configure the response
     *
     * @param argMap The named arguments
     * @param writable The writable
     */
    @Generated
    void render(Map argMap, Writable writable) {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()
        HttpServletResponse response = webRequest.currentResponse
        String layoutArg = argMap[ARGUMENT_LAYOUT]?.toString() ?: null

        handleStatusArgument argMap, webRequest, response
        applyContentType response, argMap, writable
        renderWritable writable, response
        setLayout webRequest.currentRequest, layoutArg
        webRequest.renderView = false
    }

    /**
     * Render a response for the given named arguments
     *
     * @param argMap The named argument map
     */
    @Generated
    void render(Map argMap) {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()
        HttpServletResponse response = webRequest.currentResponse
        String layoutArg = argMap[ARGUMENT_LAYOUT]?.toString() ?: null
        boolean statusSet = handleStatusArgument(argMap, webRequest, response)


        def applicationAttributes = webRequest.attributes
        if (argMap.containsKey(ARGUMENT_TEXT)) {
            def textArg = argMap[ARGUMENT_TEXT]
            applyContentType response, argMap, textArg
            if (textArg instanceof Writable) {
                renderWritable((Writable)textArg, response)
                webRequest.renderView = false
            } else {
                CharSequence text = (textArg instanceof CharSequence) ? ((CharSequence)textArg) : textArg.toString()
                render text
            }
            setLayout webRequest.currentRequest, layoutArg
        }
        else if (argMap.containsKey(ARGUMENT_VIEW)) {
            String viewName = argMap[ARGUMENT_VIEW].toString()
            String viewUri = applicationAttributes.getNoSuffixViewURI((GroovyObject)this, viewName)
            String contextPath = getContextPath(webRequest, argMap)
            if(contextPath) {
                viewUri = contextPath + viewUri
            }
            Object modelObject = argMap[ARGUMENT_MODEL]
            if (modelObject) {
                Collection<ActionResultTransformer> resultTransformers = actionResultTransformers
                for (ActionResultTransformer resultTransformer : resultTransformers) {
                    modelObject = resultTransformer.transformActionResult webRequest,viewUri, modelObject
                }
            }

            applyContentType webRequest.currentResponse, argMap, null, false

            Map model
            if (modelObject instanceof Map) {
                model = (Map) modelObject
            }
            else {
                model = [:]
            }

            ((GroovyObject)this).setProperty "modelAndView", new ModelAndView(viewUri, model)
            setLayout webRequest.currentRequest, layoutArg
        }
        else if (argMap.containsKey(ARGUMENT_TEMPLATE)) {
            applyContentType response, argMap, null, false
            webRequest.renderView = false
            boolean hasModel = argMap.containsKey(ARGUMENT_MODEL)
            def modelObject
            if(hasModel) {
                modelObject = argMap[ARGUMENT_MODEL]
            }
            String templateName = argMap[ARGUMENT_TEMPLATE].toString()
            String var
            if (argMap.containsKey(ARGUMENT_VAR)) {
                var = String.valueOf( argMap[ARGUMENT_VAR] )
            }

            // get the template uri
            String templateUri = applicationAttributes.getTemplateURI((GroovyObject)this, templateName, false)

            // retrieve view resolver
            def applicationContext = applicationAttributes.getApplicationContext()
            def viewResolver = applicationContext.getBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver)
            try {

                View view = viewResolver.resolveView(templateUri, webRequest.locale)
                if(view instanceof GroovyPageView) {
                    ((GroovyPageTemplate)((GroovyPageView)view).template).allowSettingContentType = true
                }
                if (view == null) {
                    throw new ControllerExecutionException("Unable to load template for uri [$templateUri]. Template not found.")
                }


                boolean renderWithLayout = (layoutArg || webRequest.getCurrentRequest().getAttribute(WebUtils.LAYOUT_ATTRIBUTE))
                // if automatic decoration occurred unwrap, since this is a partial

                if (renderWithLayout) {
                    setLayout webRequest.currentRequest, layoutArg
                }


                Map binding = [:]

                if (argMap.containsKey(ARGUMENT_BEAN)) {
                    Object bean = argMap[ARGUMENT_BEAN]
                    if (hasModel) {
                        if (modelObject instanceof Map) {
                            setTemplateModel webRequest, binding, (Map) modelObject
                        }
                    }
                    if (GrailsStringUtils.isBlank(var)) {
                        binding.put DEFAULT_ARGUMENT, bean
                    }
                    else {
                        binding.put var, bean
                    }
                    renderViewForTemplate webRequest, view, binding
                }
                else if (argMap.containsKey(ARGUMENT_COLLECTION)) {
                    Object colObject = argMap[ARGUMENT_COLLECTION]
                    if (hasModel) {
                        if (modelObject instanceof Map) {
                            setTemplateModel webRequest, binding, (Map)modelObject
                        }
                    }
                    renderTemplateForCollection webRequest, view, binding, colObject, var
                }
                else if (hasModel) {
                    if (modelObject instanceof Map) {
                        setTemplateModel webRequest, binding, (Map)modelObject
                    }
                    renderViewForTemplate webRequest, view, binding
                }
                else {
                    renderViewForTemplate webRequest, view, binding
                }
            }
            catch (GroovyRuntimeException gre) {
                throw new ControllerExecutionException("Error rendering template [$templateName]: ${gre.message}", gre)
            }
            catch (IOException ioex) {
                throw new ControllerExecutionException("I/O error executing render method for arguments [$argMap]: ${ioex.message}" , ioex)
            }
        }
        else if (argMap.containsKey(ARGUMENT_FILE)) {
            webRequest.renderView = false

            def o = argMap[ARGUMENT_FILE]
            def fnO = argMap[ARGUMENT_FILE_NAME]
            String fileName = fnO ? fnO.toString() : ((o instanceof File) ? ((File)o).name : null )
            if (o) {
                boolean hasContentType = applyContentType(response, argMap, null, false)
                if (fileName) {
                    if(!hasContentType) {
                        hasContentType = detectContentTypeFromFileName(webRequest, response, argMap, fileName)
                    }
                    if (fnO) {
                        response.setHeader HttpHeaders.CONTENT_DISPOSITION, "$DISPOSITION_HEADER_PREFIX\"$fileName\""
                    }
                }
                if (!hasContentType) {
                    throw new ControllerExecutionException(
                            "Argument [file] of render method specified without valid [contentType] argument")
                }

                InputStream input
                try {
                    if (o instanceof File) {
                        input = IOUtils.openStream(o)
                    }
                    else if (o instanceof InputStream) {
                        input = (InputStream)o
                    }
                    else if (o instanceof byte[]) {
                        input = new ByteArrayInputStream((byte[])o)
                    }
                    else {
                        input = IOUtils.openStream(new File(o.toString()))
                    }
                    SpringIOUtils.copy input, response.getOutputStream()
                } catch (IOException e) {
                    throw new ControllerExecutionException(
                            "I/O error copying file to response: ${e.message}", e)
                }
                finally {
                    if (input) {
                        try {
                            ((InputStream)input).close()
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }
        else if( !statusSet ) {
            webRequest.renderView = false
            if(argMap instanceof JSONElement) {
                response.contentType = GrailsWebUtil.getContentType(MimeType.JSON.name, DEFAULT_ENCODING)
                renderWritable( (JSONElement)argMap, response )
            }
            else {
                applyContentType response, argMap, argMap
                try {
                    response.writer.write argMap.inspect()
                }
                catch (IOException e) {
                    throw new ControllerExecutionException("I/O error obtaining response writer: ${e.message}", e)
                }
            }
        }
        else {
            // reached here so only the status was set, just send it back
            String message = argMap?.message?.toString()
            int statusCode = response.status
            if( message ) {
                response.sendError(statusCode, message  )
            }
            else {
                // if the status code is an error trigger the container
                // forwarding logic
                if(statusCode >= 300) {
                    response.sendError(statusCode)
                }
                else {
                    // otherwise just ensure the status is propagated to the client
                    response.setStatus(statusCode)
                    response.flushBuffer()
                }
            }
        }
    }



    private boolean handleStatusArgument(Map argMap, GrailsWebRequest webRequest, HttpServletResponse response) {
        boolean statusSet
        if (argMap.containsKey(ARGUMENT_STATUS)) {
            def statusObj = argMap.get(ARGUMENT_STATUS)
            if (statusObj != null) {
                if (statusObj instanceof HttpStatus) {
                    response.status = ((HttpStatus)statusObj).value()
                    statusSet = true
                } else {

                    try {
                        final int statusCode = statusObj instanceof Number ? ((Number) statusObj).intValue() : Integer.parseInt(statusObj.toString())
                        response.status = statusCode
                        statusSet = true
                    }
                    catch (NumberFormatException e) {
                        throw new ControllerExecutionException(
                                "Argument [status] of method [render] must be a valid integer.")
                    }
                }
            }
        }
        if(statusSet) {
            webRequest.renderView = false
        }
        return statusSet
    }

    private void renderMarkupInternal(GrailsWebRequest webRequest, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure closure, HttpServletResponse response) {
        StreamingMarkupBuilder b = new StreamingMarkupBuilder()
        b.encoding = response.characterEncoding

        Writable markup = (Writable) b.bind(closure)
        renderWritable markup, response

        webRequest.setRenderView false
    }

    private boolean isJSONResponse(HttpServletResponse response) {
        String contentType = response.getContentType()
        return contentType != null && (contentType.indexOf("application/json") > -1 ||
                contentType.indexOf("text/json") > -1)
    }

    private void renderWritable(Writable writable, HttpServletResponse response) {
        try {
            PrintWriter writer = response.getWriter()
            writable.writeTo writer
            writer.flush()
        }
        catch (IOException e) {
            throw new ControllerExecutionException(e.getMessage(), e)
        }
    }

    private boolean applyContentType(HttpServletResponse response, Map argMap, Object renderArgument) {
        applyContentType response, argMap, renderArgument, true
    }

    private boolean applyContentType(HttpServletResponse response, Map argMap, Object renderArgument, boolean useDefault) {
        boolean contentTypeIsDefault = true
        String contentType = resolveContentTypeBySourceType(renderArgument, useDefault ? TEXT_HTML : null)
        String encoding = DEFAULT_ENCODING
        if (argMap != null) {
            if(argMap.containsKey(ARGUMENT_CONTENT_TYPE)) {
                contentType = argMap.get(ARGUMENT_CONTENT_TYPE).toString()
                contentTypeIsDefault = false
            }
            if(argMap.containsKey(ARGUMENT_ENCODING)) {
                encoding = argMap.get(ARGUMENT_ENCODING).toString()
                contentTypeIsDefault = false
            }
        }
        if(contentType != null) {
            setContentType response, contentType, encoding, contentTypeIsDefault
            return true
        }
        false
    }

    private void setContentType(HttpServletResponse response, String contentType, String encoding) {
        setContentType response, contentType, encoding, false
    }

    private void setContentType(HttpServletResponse response, String contentType, String encoding, boolean contentTypeIsDefault) {
        if (!contentTypeIsDefault || response.getContentType()==null) {
            response.setContentType GrailsWebUtil.getContentType(contentType, encoding)
        }
    }

    private String resolveContentTypeBySourceType(final Object renderArgument, String defaultEncoding) {
        renderArgument instanceof GPathResult ? APPLICATION_XML : defaultEncoding
    }

    private void setLayout(HttpServletRequest request, String layout) {
        if (layout != null && request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE) == null) {
            request.setAttribute WebUtils.LAYOUT_ATTRIBUTE, layout
        }
    }

    private String getContextPath(GrailsWebRequest webRequest, Map argMap) {
        def cp = argMap.get(ARGUMENT_CONTEXTPATH)
        String contextPath = (cp != null ? cp.toString() : "")

        Object pluginName = argMap.get(ARGUMENT_PLUGIN)
        if (pluginName != null) {

            GrailsPlugin plugin = getPluginManager(webRequest).getGrailsPlugin(pluginName.toString())
            if (plugin != null && !plugin.isBasePlugin()) contextPath = plugin.getPluginPath()
        }
        contextPath
    }

    private GrailsPluginManager getPluginManager(GrailsWebRequest webRequest) {
        if(pluginManager == null) {
            pluginManager = webRequest.getApplicationContext().getBean(GrailsPluginManager)
        }
        pluginManager
    }

    private void setTemplateModel(GrailsWebRequest webRequest, Map binding, Map modelObject) {
        Map modelMap = modelObject
        webRequest.setAttribute GrailsApplicationAttributes.TEMPLATE_MODEL, modelMap, RequestAttributes.SCOPE_REQUEST
        binding.putAll modelMap
    }

    private void renderTemplateForCollection(GrailsWebRequest webRequest, View view, Map binding, Object colObject, String var) throws IOException {
        if (colObject instanceof Iterable) {
            Iterable c = (Iterable) colObject
            for (Object o : c) {
                if (GrailsStringUtils.isBlank(var)) {
                    binding.put DEFAULT_ARGUMENT, o
                }
                else {
                    binding.put var, o
                }
                renderViewForTemplate webRequest, view, binding
            }
        }
        else {
            if (GrailsStringUtils.isBlank(var)) {
                binding.put DEFAULT_ARGUMENT, colObject
            }
            else {
                binding.put var, colObject
            }

            renderViewForTemplate webRequest, view, binding
        }
    }
    private void renderViewForTemplate(GrailsWebRequest webRequest, View view, Map binding) {
        try {
            view.render binding, webRequest.getCurrentRequest(), webRequest.getResponse()
        }
        catch (Exception e) {
            throw new ControllerExecutionException(e.getMessage(), e)
        }
    }

    private boolean detectContentTypeFromFileName(GrailsWebRequest webRequest, HttpServletResponse response, Map argMap, String fileName) {
        if (mimeUtility) {
            MimeType mimeType = mimeUtility.getMimeTypeForExtension(GrailsStringUtils.getFilenameExtension(fileName))
            if (mimeType) {
                String contentType = mimeType.name
                def encodingObj = argMap.get(ARGUMENT_ENCODING)
                String encoding = encodingObj ? encodingObj.toString() : DEFAULT_ENCODING
                setContentType response, contentType, encoding
                return true
            }
        }
        return false
    }

}

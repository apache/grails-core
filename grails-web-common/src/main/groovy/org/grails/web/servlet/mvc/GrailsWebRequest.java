/*
 * Copyright 2004-2024 the original author or authors.
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
package org.grails.web.servlet.mvc;

import grails.util.Holders;
import grails.validation.DeferredBindingActions;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import grails.web.servlet.mvc.GrailsHttpSession;
import grails.web.servlet.mvc.GrailsParameterMap;
import grails.core.GrailsApplication;
import grails.core.GrailsControllerClass;
import org.grails.core.artefact.ControllerArtefactHandler;
import org.grails.core.io.support.GrailsFactoriesLoader;
import org.grails.encoder.CodecLookupHelper;
import org.grails.encoder.DefaultEncodingStateRegistry;
import org.grails.encoder.Encoder;
import org.grails.encoder.EncodingStateRegistry;
import org.grails.encoder.EncodingStateRegistryLookup;
import org.grails.encoder.EncodingStateRegistryLookupHolder;
import org.grails.web.beans.PropertyEditorRegistryUtils;
import org.grails.web.pages.FilteringCodecsByContentTypeSettings;
import grails.web.mvc.FlashScope;
import org.grails.web.util.GrailsApplicationAttributes;
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.handler.DispatcherServletWebRequest;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * Encapsulates a Grails request. An instance of this class is bound to the current thread using
 * Spring's RequestContextHolder which can later be retrieved using:
 *
 * def webRequest = RequestContextHolder.currentRequestAttributes()
 *
 * @author Graeme Rocher
 * @since 3.0
 */
public class GrailsWebRequest extends DispatcherServletWebRequest  {

    private static final String REDIRECT_CALLED = GrailsApplicationAttributes.REDIRECT_ISSUED;

    private static final Class<? extends GrailsApplicationAttributes> grailsApplicationAttributesClass = GrailsFactoriesLoader.loadFactoryClasses(GrailsApplicationAttributes.class, GrailsWebRequest.class.getClassLoader()).get(0);
    private static final Constructor<? extends GrailsApplicationAttributes> grailsApplicationAttributesConstructor = ClassUtils.getConstructorIfAvailable(grailsApplicationAttributesClass, ServletContext.class);
    private GrailsApplicationAttributes attributes;
    private GrailsParameterMap params;
    private GrailsParameterMap originalParams;
    private GrailsHttpSession session;
    private boolean renderView = true;
    private boolean skipFilteringCodec = false;
    private Encoder filteringEncoder;
    public static final String ID_PARAMETER = "id";
    private final List<ParameterCreationListener> parameterCreationListeners = new ArrayList<ParameterCreationListener>();
    private final UrlPathHelper urlHelper = new UrlPathHelper();
    private ApplicationContext applicationContext;
    private String baseUrl;
    private HttpServletResponse wrappedResponse;

    private EncodingStateRegistry encodingStateRegistry;
    private HttpServletRequest multipartRequest;

    public GrailsWebRequest(HttpServletRequest request, HttpServletResponse response, GrailsApplicationAttributes attributes) {
        super(request, response);
        this.attributes = attributes;
        this.applicationContext = attributes.getApplicationContext();
        inheritEncodingStateRegistry();
    }

    public GrailsWebRequest(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext) {
        super(request, response);
        try {
            attributes = grailsApplicationAttributesConstructor.newInstance(servletContext);
            this.applicationContext = attributes.getApplicationContext();
        }
        catch (Exception e) {
            ReflectionUtils.rethrowRuntimeException(e);
        }
        inheritEncodingStateRegistry();
    }

    public GrailsWebRequest(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext, ApplicationContext applicationContext) {
        this(request, response, servletContext);
        this.applicationContext = applicationContext;
    }


    /**
     * Holds a reference to the {@link org.springframework.web.multipart.MultipartRequest}
     *
     * @param multipartRequest The multipart request
     */
    public void setMultipartRequest(HttpServletRequest multipartRequest) {
        this.multipartRequest = multipartRequest;
        this.originalParams = null; // originalParams will need to be re-initialized. See https://github.com/grails/grails-core/issues/13837
    }

    private void inheritEncodingStateRegistry() {
        GrailsWebRequest parentRequest = GrailsWebRequest.lookup(getRequest());
        if(parentRequest != null) {
            this.encodingStateRegistry = parentRequest.getEncodingStateRegistry();
        }
    }

    /**
     * Overriden to return the GrailsParameterMap instance,
     *
     * @return An instance of GrailsParameterMap
     */
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map getParameterMap() {
        if (params == null) {
            resetParams();
        }
        return params;
    }

    @Override
    public void requestCompleted() {
        super.requestCompleted();
        DeferredBindingActions.clear();
    }

    /**
     * @return the out
     */
    public Writer getOut() {
        Writer out = attributes.getOut(getCurrentRequest());
        if (out == null) {
            try {
                return getCurrentResponse().getWriter();
            } catch (IOException e) {
                throw new ControllerExecutionException("Error retrieving response writer: " + e.getMessage(), e);
            }
        }
        return out;
    }

    /**
     * Whether the web request is still active
     * @return true if it is
     */
    public boolean isActive() {
        return super.isRequestActive();
    }

    /**
     * @param out the out to set
     */
    public void setOut(Writer out) {
        attributes.setOut(getCurrentRequest(), out);
    }

    /**
     * @return The ServletContext instance
     */
    public ServletContext getServletContext() {
        return attributes.getServletContext();
    }

    /**
     * Returns the context path of the request.
     * @return the path
     */
    @Override
    public String getContextPath() {
        final HttpServletRequest request = getCurrentRequest();
        String appUri = (String) request.getAttribute(GrailsApplicationAttributes.APP_URI_ATTRIBUTE);
        if (appUri == null) {
            appUri = urlHelper.getContextPath(request);
        }
        return appUri;
    }

    /**
     * @return The FlashScope instance for the current request
     */
    public FlashScope getFlashScope() {
        return attributes.getFlashScope(getRequest());
    }

    /**
     * @return The currently executing request
     */
    public HttpServletRequest getCurrentRequest() {
        if(multipartRequest != null) {
            return multipartRequest;
        }
        else {
            return getRequest();
        }
    }

    public HttpServletResponse getCurrentResponse() {
        if(wrappedResponse != null) {
            return wrappedResponse;
        } else {
            return getResponse();
        }
    }

    public HttpServletResponse getWrappedResponse() {
        return wrappedResponse;
    }

    public void setWrappedResponse(HttpServletResponse wrappedResponse) {
        this.wrappedResponse = wrappedResponse;
    }

    /**
     * @return The Grails params object
     */
    public GrailsParameterMap getParams() {
        if (params == null) {
            resetParams();
        }
        return params;
    }

    /**
     * @return The Grails params object
     */
    public GrailsParameterMap getOriginalParams() {
        if (originalParams == null) {
            originalParams = new GrailsParameterMap(getCurrentRequest());
        }
        return originalParams;
    }

    /**
     * Reset params by re-reading and initializing parameters from request
     */
    public void resetParams() {
        params = (GrailsParameterMap)getOriginalParams().clone();
    }

    @SuppressWarnings("rawtypes")
    public void addParametersFrom(Map previousParams) {
        if (previousParams instanceof GrailsParameterMap) {
            getParams().addParametersFrom((GrailsParameterMap)previousParams);
        } else {
            for (Object key : previousParams.keySet()) {
                String name = String.valueOf(key);
                getParams().put(name, previousParams.get(key));
            }
        }
    }

    /**
     * Informs any parameter creation listeners.
     */
    public void informParameterCreationListeners() {
        for (ParameterCreationListener parameterCreationListener : parameterCreationListeners) {
            parameterCreationListener.paramsCreated(getParams());
        }
    }

    /**
     * @return The Grails session object
     */
    public GrailsHttpSession getSession() {
        if (session == null) {
            session = new GrailsHttpSession(getCurrentRequest());
        }

        return session;
    }

    /**
     * @return The GrailsApplicationAttributes instance
     */
    public GrailsApplicationAttributes getAttributes() {
        return attributes;
    }

    public void setActionName(String actionName) {
        getCurrentRequest().setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, actionName);
    }

    public void setControllerName(String controllerName) {
        getCurrentRequest().setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, controllerName);
    }

    public void setControllerNamespace(String controllerNamespace) {
        getCurrentRequest().setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, controllerNamespace);
    }

    /**
     * @return the actionName
     */
    public String getActionName() {
        return (String)getCurrentRequest().getAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE);
    }

    /**
     * @return the controllerName
     */
    public String getControllerName() {
        return (String)getCurrentRequest().getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE);
    }

    /**
     * @return the controllerClass
     */
    public GrailsControllerClass getControllerClass() {
        HttpServletRequest currentRequest = getCurrentRequest();
        GrailsControllerClass controllerClass = (GrailsControllerClass) currentRequest.getAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS);
        if(controllerClass == null) {
            Object controllerNameObject = currentRequest.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE);
            if(controllerNameObject != null) {
                controllerClass = (GrailsControllerClass)getAttributes()
                                                            .getGrailsApplication()
                                                            .getArtefactByLogicalPropertyName(ControllerArtefactHandler.TYPE, controllerNameObject.toString());
                if(controllerClass != null) {
                    currentRequest.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, controllerClass);
                }
            }
        }
        return controllerClass;
    }

    /**
    * @return the controllerNamespace
    */
    public String getControllerNamespace() {
        return (String)getCurrentRequest().getAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE);
    }

    public void setRenderView(boolean renderView) {
        this.renderView = renderView;
    }

    /**
     * @return true if the view for this GrailsWebRequest should be rendered
     */
    public boolean isRenderView() {
        final HttpServletRequest currentRequest = getCurrentRequest();
        HttpServletResponse currentResponse = getCurrentResponse();
        return renderView &&
                !currentResponse.isCommitted() &&
                currentResponse.getStatus() < 300 &&
                currentRequest.getAttribute(REDIRECT_CALLED) == null;
    }

    public String getId() {
        Object id = getParams().get(ID_PARAMETER);
        return id == null ? null : id.toString();
    }

    /**
     * Returns true if the current executing request is a flow request
     *
     * @return true if it is a flow request
     */
    public boolean isFlowRequest() {
        GrailsApplication application = getAttributes().getGrailsApplication();
        Object controllerClassObject = getControllerClass();
        GrailsControllerClass controllerClass = null;
        if(controllerClassObject instanceof GrailsControllerClass) {
            controllerClass = (GrailsControllerClass) controllerClassObject;
        }

        if (controllerClass == null) return false;

        String actionName = getActionName();
        if (actionName == null) actionName = controllerClass.getDefaultAction();
        if (actionName == null) return false;

        return false;
    }

    public void addParameterListener(ParameterCreationListener creationListener) {
        parameterCreationListeners.add(creationListener);
    }

    /**
     * Obtains the ApplicationContext object.
     *
     * @return The ApplicationContext
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext == null ? getAttributes().getApplicationContext() : applicationContext;
    }

    /**
     * Obtains the PropertyEditorRegistry instance.
     * @return The PropertyEditorRegistry
     */
    public PropertyEditorRegistry getPropertyEditorRegistry() {
        final HttpServletRequest servletRequest = getCurrentRequest();
        PropertyEditorRegistry registry = (PropertyEditorRegistry) servletRequest.getAttribute(GrailsApplicationAttributes.PROPERTY_REGISTRY);
        if (registry == null) {
            registry = new PropertyEditorRegistrySupport();
            PropertyEditorRegistryUtils.registerCustomEditors(this, registry, RequestContextUtils.getLocale(servletRequest));
            servletRequest.setAttribute(GrailsApplicationAttributes.PROPERTY_REGISTRY, registry);
        }
        return registry;
    }

    /**
     * Looks up the GrailsWebRequest from the current request.
     * @param request The current request
     * @return The GrailsWebRequest
     */
    public static @Nullable GrailsWebRequest lookup(HttpServletRequest request) {
        GrailsWebRequest webRequest = (GrailsWebRequest) request.getAttribute(GrailsApplicationAttributes.WEB_REQUEST);
        return webRequest == null ? lookup() : webRequest;
    }

    /**
     * Looks up the current Grails WebRequest instance
     * @return The GrailsWebRequest instance
     */
    public static @Nullable GrailsWebRequest lookup() {
        GrailsWebRequest webRequest = null;
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof GrailsWebRequest) {
            webRequest = (GrailsWebRequest) requestAttributes;
        }
        return webRequest;
    }

    /**
     * Sets the id of the request.
     * @param id The id
     */
    public void setId(Object id) {
        getParams().put(GrailsWebRequest.ID_PARAMETER, id);
    }

    public String getBaseUrl() {
        if (baseUrl == null) {
            HttpServletRequest request=getCurrentRequest();
            String scheme =request.getScheme();
            String forwardedScheme = request.getHeader("X-Forwarded-Proto");
            StringBuilder sb=new StringBuilder();
            sb.append(scheme).append("://").append(request.getServerName());

            int port = request.getServerPort();
            String forwardedPort = request.getHeader("X-Forwarded-Port");

            //ignore port append if the request was forwarded from a VIP as actual source port is now not known
            if (forwardedScheme == null && (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443))) {
                sb.append(":").append(port);
            } else if (forwardedPort != null &&
                    ("http".equals(forwardedScheme) && !"80".equals(forwardedPort)) ||
                    ("https".equals(forwardedScheme) && !"443".equals(forwardedPort))) {
                sb.append(":").append(forwardedPort);
            }

            String contextPath = request.getContextPath();
            if (contextPath != null) {
                sb.append(contextPath);
            }
            baseUrl = sb.toString();
        }
        return baseUrl;
    }

    public EncodingStateRegistry getEncodingStateRegistry() {
        if (encodingStateRegistry == null) {
            encodingStateRegistry = new DefaultEncodingStateRegistry();
        }
        return encodingStateRegistry;
    }

    private static final class DefaultEncodingStateRegistryLookup implements EncodingStateRegistryLookup {
        public EncodingStateRegistry lookup() {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup();
            return webRequest == null ? null : webRequest.getEncodingStateRegistry();
        }
    }

    static {
        EncodingStateRegistryLookupHolder.setEncodingStateRegistryLookup(new DefaultEncodingStateRegistryLookup());
    }

    /**
     * @return true if grails.views.filteringCodecForMimeType settings should be ignored for this request
     */
    public boolean isSkipFilteringCodec() {
        return skipFilteringCodec;
    }

    public void setSkipFilteringCodec(boolean skipCodec) {
        this.skipFilteringCodec = skipCodec;
    }

    public String getFilteringCodec() {
        return filteringEncoder != null ? filteringEncoder.getCodecIdentifier().getCodecName() : null;
    }
    public void setFilteringCodec(String codecName) {
        filteringEncoder=codecName != null ? CodecLookupHelper.lookupEncoder(attributes.getGrailsApplication(), codecName) : null;
    }

    public Encoder lookupFilteringEncoder() {
        if (filteringEncoder == null && applicationContext != null && applicationContext.containsBean(FilteringCodecsByContentTypeSettings.BEAN_NAME)) {
            filteringEncoder = applicationContext.getBean(FilteringCodecsByContentTypeSettings.BEAN_NAME, FilteringCodecsByContentTypeSettings.class).getEncoderForContentType(getResponse().getContentType());
        }
        return filteringEncoder;
    }

    public Encoder getFilteringEncoder() {
        return filteringEncoder;
    }

    public void setFilteringEncoder(Encoder filteringEncoder) {
        this.filteringEncoder = filteringEncoder;
    }
}

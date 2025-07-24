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
package grails.artefact

import java.lang.reflect.Method

import groovy.transform.CompileStatic
import groovy.transform.Generated
import org.codehaus.groovy.runtime.InvokerHelper

import jakarta.servlet.ServletRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpMethod
import org.springframework.validation.BindingResult
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
import org.springframework.web.context.ContextLoader
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView

import grails.artefact.controller.support.RequestForwarder
import grails.artefact.controller.support.ResponseRedirector
import grails.artefact.controller.support.ResponseRenderer
import grails.core.GrailsControllerClass
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.util.GrailsClassUtils
import grails.util.GrailsMetaClassUtils
import grails.web.api.ServletAttributes
import grails.web.api.WebAttributes
import grails.web.databinding.DataBinder
import grails.web.databinding.DataBindingUtils
import org.grails.compiler.web.ControllerActionTransformer
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.plugins.web.api.MimeTypesApiSupport
import org.grails.plugins.web.controllers.ControllerExceptionHandlerMetaData
import org.grails.plugins.web.servlet.mvc.InvalidResponseHandler
import org.grails.plugins.web.servlet.mvc.ValidResponseHandler
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.SynchronizerTokensHolder
import org.grails.web.servlet.mvc.TokenResponseHandler
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Classes that implement the {@link Controller} trait are automatically treated as web controllers in a Grails application
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 3.0
 *
 */
@CompileStatic
trait Controller implements ResponseRenderer, ResponseRedirector, RequestForwarder, DataBinder, WebAttributes, ServletAttributes {

    private MimeTypesApiSupport mimeTypesSupport = new MimeTypesApiSupport()


    /**
     * <p>The withFormat method is used to allow controllers to handle different types of
     * request formats such as HTML, XML and so on. Example usage:</p>
     *
     * <pre>
     * <code>
     *    withFormat {
     *        html { render "html" }
     *        xml { render "xml}
     *    }
     * </code>
     * </pre>
     *
     * @param callable
     * @return  The result of the closure execution selected
     */
    @Generated
    def withFormat(Closure callable) {
        HttpServletResponse response = GrailsWebRequest.lookup().currentResponse
        mimeTypesSupport.withFormat((HttpServletResponse)response, callable)
    }

    /**
     * Sets a response header for the given name and value
     *
     * @param headerName The header name
     * @param headerValue The header value
     */
    @Generated
    void header(String headerName, headerValue) {
        if (headerValue != null) {
            final HttpServletResponse response = getResponse()
            response?.setHeader headerName, headerValue.toString()
        }
    }

    /**
     * Binds data for the given type to the given collection from the request
     *
     * @param targetType The target type
     * @param collectionToPopulate The collection to populate
     * @param request The request
     */
    @Generated
    void bindData(Class targetType, Collection collectionToPopulate, ServletRequest request) {
        DataBindingUtils.bindToCollection targetType, collectionToPopulate, request
    }

    /**
     * Return true if there are an errors
     * @return true if there are errors
     */
    @Generated
    boolean hasErrors() {
        getErrors()?.hasErrors()
    }

    /**
     * Sets the errors instance of the current controller
     *
     * @param errors The error instance
     */
    @Generated
    void setErrors(Errors errors) {
        def webRequest = currentRequestAttributes()
        setErrorsInternal(webRequest, errors)
    }


    /**
     * Obtains the errors instance for the current controller
     *
     * @return The Errors instance
     */
    @Generated
    Errors getErrors() {
        def webRequest = currentRequestAttributes()
        getErrorsInternal(webRequest)
    }


    /**
     * Obtains the ModelAndView for the currently executing controller
     *
     * @return The ModelAndView
     */
    @Generated
    ModelAndView getModelAndView() {
        (ModelAndView)currentRequestAttributes().getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, 0)
    }

    /**
     * Sets the ModelAndView of the current controller
     *
     * @param mav The ModelAndView
     */
    @Generated
    void setModelAndView(ModelAndView mav) {
        currentRequestAttributes().setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, mav, 0)
    }

    /**
     * Returns the URI of the currently executing action
     *
     * @return The action URI
     */
    @Generated
    String getActionUri() {
        "/${getControllerName()}/${getActionName()}"
    }

    /**
     * Returns the URI of the currently executing controller
     * @return The controller URI
     */
    @Generated
    String getControllerUri() {
        "/${getControllerName()}"
    }

    /**
     * Obtains a URI of a template by name
     *
     * @param name The name of the template
     * @return The template URI
     */
    @Generated
    String getTemplateUri(String name) {
        getGrailsAttributes().getTemplateUri(name, getRequest())
    }

    /**
     * Obtains a URI of a view by name
     *
     * @param name The name of the view
     * @return The template URI
     */
    @Generated
    String getViewUri(String name) {
        getGrailsAttributes().getViewUri(name, getRequest())
    }

    /**
     * Redirects for the given arguments.
     *
     * @param argMap The arguments
     * @return null
     */
    @Generated
    void redirect(Map argMap) {

        if (argMap.isEmpty()) {
            throw new IllegalArgumentException("Invalid arguments to method 'redirect': $argMap")
        }

        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()

        if(this instanceof GroovyObject) {
            GroovyObject controller = (GroovyObject)this

            // if there are errors add it to the list of errors
            Errors controllerErrors = getErrorsInternal(webRequest)
            Errors errors = (Errors)argMap.get(GormProperties.ERRORS)
            if (controllerErrors != null && errors != null) {
                controllerErrors.addAllErrors errors
            }
            else {
                setErrorsInternal webRequest, errors
            }
            def action = argMap.get(GrailsControllerClass.ACTION)
            if (action != null) {
                argMap.put(GrailsControllerClass.ACTION, action.toString())
            }
            if (!argMap.containsKey(GrailsControllerClass.NAMESPACE_PROPERTY)) {
                // this could be made more efficient if we had a reference to the GrailsControllerClass object, which
                // has the namespace property accessible without needing reflection
                argMap.put GrailsControllerClass.NAMESPACE_PROPERTY, GrailsClassUtils.getStaticFieldValue(controller.getClass(), GrailsControllerClass.NAMESPACE_PROPERTY)
            }
        }

        super.redirect(argMap)
    }
    /**
     * Used the synchronizer token pattern to avoid duplicate form submissions
     *
     * @param callable The closure to execute
     * @return The result of the closure execution
     */
    @Generated
    TokenResponseHandler withForm(Closure callable) {
        withForm getWebRequest(), callable
    }

    /**
     * <p>Main entry point, this method will check the request for the necessary TOKEN and if it is valid
     *     will call the passed closure.
     *
     * <p>For an invalid response an InvalidResponseHandler is returned which will invoke the closure passed
     * to the handleInvalid method. The idea here is to allow code like:
     *
     * <pre><code>
     * withForm {
     *   // handle valid form submission
     * }.invalidToken {
     *    // handle invalid form submission
     * }
     * </code></pre>
     */
    @Generated
    TokenResponseHandler withForm(GrailsWebRequest webRequest, Closure callable) {
        TokenResponseHandler handler
        if (isTokenValid(webRequest)) {
            resetToken(webRequest)
            handler = new ValidResponseHandler(callable?.call())
        }
        else {
            handler = new InvalidResponseHandler()
        }

        webRequest.request.setAttribute(TokenResponseHandler.KEY, handler)
        return handler
    }

    /**
     * Checks whether the token in th request is valid.
     *
     * @param request The servlet request
     */
    private synchronized boolean isTokenValid(GrailsWebRequest webRequest) {
        final request = webRequest.getCurrentRequest()
        SynchronizerTokensHolder tokensHolderInSession = (SynchronizerTokensHolder)request.getSession(false)?.getAttribute(SynchronizerTokensHolder.HOLDER)
        if (!tokensHolderInSession) return false

        String tokenInRequest = webRequest.params[SynchronizerTokensHolder.TOKEN_KEY]
        if (!tokenInRequest) return false

        String urlInRequest = webRequest.params[SynchronizerTokensHolder.TOKEN_URI]
        if (!urlInRequest) return false

        try {
            return tokensHolderInSession.isValid(urlInRequest, tokenInRequest)
        }
        catch (IllegalArgumentException) {
            return false
        }
    }

    /**
     * Resets the token in the request
     */
    private synchronized resetToken(GrailsWebRequest webRequest) {
        final request = webRequest.getCurrentRequest()
        SynchronizerTokensHolder tokensHolderInSession = (SynchronizerTokensHolder)request.getSession(false)?.getAttribute(SynchronizerTokensHolder.HOLDER)
        String urlInRequest = webRequest.params[SynchronizerTokensHolder.TOKEN_URI]
        String tokenInRequest = webRequest.params[SynchronizerTokensHolder.TOKEN_KEY]

        if (urlInRequest && tokenInRequest) {
            tokensHolderInSession.resetToken(urlInRequest, tokenInRequest)
        }
        if (tokensHolderInSession.isEmpty()) request.getSession(false)?.removeAttribute(SynchronizerTokensHolder.HOLDER)
    }

    @Generated
    public static ApplicationContext getStaticApplicationContext() {
         RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes()
         if (!(requestAttributes instanceof GrailsWebRequest)) {
             return ContextLoader.getCurrentWebApplicationContext()
         }
         ((GrailsWebRequest)requestAttributes).getApplicationContext()
    }


    /**
     * Initializes a command object.
     *
     * If type is a domain class and the request body or parameters include an id, the id is used to retrieve
     * the command object instance from the database, otherwise the no-arg constructor on type is invoke.  If
     * an attempt is made to retrieve the command object instance from the database and no corresponding
     * record is found, null is returned.
     *
     * The command object is then subjected to data binding and dependency injection before being returned.
     *
     *
     * @param type The type of the command object
     * @return the initialized command object or null if the command object is a domain class, the body or
     * parameters included an id and no corresponding record was found in the database.
     */
    @Generated
    def initializeCommandObject(final Class type, final String commandObjectParameterName) throws Exception {
        final HttpServletRequest request = getRequest()
        def commandObjectInstance = null
        try {
            final DataBindingSource dataBindingSource = DataBindingUtils
                    .createDataBindingSource(
                    getGrailsApplication(), type,
                    request)
            final DataBindingSource commandObjectBindingSource = getCommandObjectBindingSourceForPrefix(
                    commandObjectParameterName, dataBindingSource)
            def entityIdentifierValue = null
            final boolean isDomainClass
            if(GroovyObject.isAssignableFrom(type)) {
                isDomainClass = DomainClass.isAssignableFrom(type)
            } else {
                isDomainClass = DomainClassArtefactHandler
                        .isDomainClass(type)
            }
            if (isDomainClass) {
                entityIdentifierValue = commandObjectBindingSource
                        .getIdentifierValue()
                if (entityIdentifierValue == null) {
                    final GrailsWebRequest webRequest = GrailsWebRequest
                            .lookup(request)
                    entityIdentifierValue = webRequest?.getParams().getIdentifier()
                }
            }
            if (entityIdentifierValue instanceof String) {
                entityIdentifierValue = ((String) entityIdentifierValue).trim()
                if ("".equals(entityIdentifierValue)
                        || "null".equals(entityIdentifierValue)) {
                    entityIdentifierValue = null
                }
            }

            final HttpMethod requestMethod = HttpMethod.valueOf(request.getMethod())

            if (entityIdentifierValue != null) {
                try {
                    commandObjectInstance = InvokerHelper.invokeStaticMethod(type, "get", entityIdentifierValue)
                } catch (Exception e) {
                    final Errors errors = getErrors()
                    if (errors != null) {
                        errors.reject(getClass().getName()
                                + ".commandObject."
                                + commandObjectParameterName + ".error",
                                e.getMessage())
                    }
                }
            } else if (requestMethod == HttpMethod.POST || !isDomainClass) {
                commandObjectInstance = type.getDeclaredConstructor().newInstance()
            }

            if (commandObjectInstance != null
                    && commandObjectBindingSource != null) {
                boolean shouldDoDataBinding

                if (entityIdentifierValue != null) {
                    switch (requestMethod) {
                        case HttpMethod.PATCH:
                        case HttpMethod.POST:
                        case HttpMethod.PUT:
                            shouldDoDataBinding = true
                            break
                        default:
                            shouldDoDataBinding = false
                    }
                } else {
                    shouldDoDataBinding = true
                }

                if (shouldDoDataBinding) {
                    bindData(commandObjectInstance, commandObjectBindingSource, Collections.EMPTY_MAP, null)
                }
            }
        } catch (Exception e) {
            final exceptionHandlerMethodFor = getExceptionHandlerMethodFor(e.getClass())
            if(exceptionHandlerMethodFor != null) {
                throw e
            }
            commandObjectInstance = type.getDeclaredConstructor().newInstance()
            final o = GrailsMetaClassUtils.invokeMethodIfExists(commandObjectInstance, "getErrors")
            if(o instanceof BindingResult) {
                final BindingResult errors = (BindingResult)o
                String msg = "Error occurred initializing command object [" + commandObjectParameterName + "]. " + e.getMessage()
                ObjectError error = new ObjectError(commandObjectParameterName, msg)
                errors.addError(error)
            }
        }

        if(commandObjectInstance != null) {
            final ApplicationContext applicationContext = getApplicationContext()
            final AutowireCapableBeanFactory autowireCapableBeanFactory = applicationContext.getAutowireCapableBeanFactory()
            autowireCapableBeanFactory.autowireBeanProperties(commandObjectInstance, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
        }

        commandObjectInstance
    }

    /**
     * Return a DataBindingSource for a command object which has a parameter name matching the specified prefix.
     * If params include something like widget.name=Thing and prefix is widget then the returned binding source
     * will include name=thing, not widget.name=Thing.
     *
     * @param prefix The parameter name for the command object
     * @param params The original binding source associated with the request
     * @return The binding source suitable for binding to a command object with a parameter name matching the specified prefix.
     */
    private DataBindingSource getCommandObjectBindingSourceForPrefix(String prefix, DataBindingSource params) {
        DataBindingSource commandParams = params
        if (params != null && prefix != null) {
            def innerValue = params[prefix]
            if(innerValue instanceof DataBindingSource) {
                commandParams = (DataBindingSource)innerValue
            } else if(innerValue instanceof Map) {
                commandParams = new SimpleMapDataBindingSource(innerValue)
            }
        }
        commandParams
    }

    @Generated
    @SuppressWarnings("unchecked")
    Method getExceptionHandlerMethodFor(final Class<? extends Exception> exceptionType) throws Exception {
        if(!Exception.class.isAssignableFrom(exceptionType)) {
            throw new IllegalArgumentException("exceptionType [${exceptionType.getName()}] argument must be Exception or a subclass of Exception")
        }

        Method handlerMethod
        final List<ControllerExceptionHandlerMetaData> exceptionHandlerMetaDataInstances = (List<ControllerExceptionHandlerMetaData>)GrailsClassUtils.getStaticFieldValue(this.getClass(), ControllerActionTransformer.EXCEPTION_HANDLER_META_DATA_FIELD_NAME)
        if(exceptionHandlerMetaDataInstances) {

            // find all of the handler methods which could accept this exception type
            final List<ControllerExceptionHandlerMetaData> matches = (List<ControllerExceptionHandlerMetaData>)exceptionHandlerMetaDataInstances.findAll { ControllerExceptionHandlerMetaData cemd ->
                cemd.exceptionType.isAssignableFrom(exceptionType)
            }

            if(matches.size() > 0) {
                ControllerExceptionHandlerMetaData theOne = matches.get(0)

                // if there are more than 1, find the one that is farthest down the inheritance hierarchy
                for(int i = 1; i < matches.size(); i++) {
                    final ControllerExceptionHandlerMetaData nextMatch = matches.get(i)
                    if(theOne.getExceptionType().isAssignableFrom(nextMatch.getExceptionType())) {
                        theOne = nextMatch
                    }
                }
                handlerMethod = this.getClass().getMethod(theOne.getMethodName(), theOne.getExceptionType())
            }
        }

        handlerMethod
    }



    private Errors getErrorsInternal(GrailsWebRequest webRequest) {
        (Errors) webRequest.getAttribute(GrailsApplicationAttributes.ERRORS, 0)
    }

    private setErrorsInternal(GrailsWebRequest webRequest, Errors errors) {
        webRequest.setAttribute(GrailsApplicationAttributes.ERRORS, errors, 0)
    }

}

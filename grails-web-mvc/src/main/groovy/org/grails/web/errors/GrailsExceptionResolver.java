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
package org.grails.web.errors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.micrometer.observation.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;

import grails.config.Config;
import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import grails.util.Environment;
import grails.web.mapping.UrlMappingInfo;
import grails.web.mapping.UrlMappingsHolder;
import grails.web.mapping.exceptions.UrlMappingException;
import grails.web.mvc.GrailsResponseMutator;
import org.apache.grails.core.GrailsBootstrapRegistryInitializer;
import org.grails.core.exceptions.GrailsRuntimeException;
import org.grails.exceptions.ExceptionUtils;
import org.grails.exceptions.reporting.DefaultStackTraceFilterer;
import org.grails.exceptions.reporting.StackTraceFilterer;
import org.grails.web.mapping.DefaultUrlMappingInfo;
import org.grails.web.mapping.UrlMappingUtils;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.servlet.mvc.exceptions.GrailsMVCException;
import org.grails.web.util.GrailsApplicationAttributes;
import org.grails.web.util.WebUtils;

/**
 * Wraps any runtime exceptions with a GrailsWrappedException instance.
 *
 * @author Graeme Rocher
 */
public class GrailsExceptionResolver extends SimpleMappingExceptionResolver implements ServletContextAware, GrailsApplicationAware {

    public static final String EXCEPTION_ATTRIBUTE = WebUtils.EXCEPTION_ATTRIBUTE;

    /** Marks a request that is currently inside a forward to a status-code controller mapping. */
    private static final String ERROR_HANDLER_FORWARD_IN_PROGRESS_ATTRIBUTE =
            "org.grails.web.errors.ERROR_HANDLER_FORWARD_IN_PROGRESS";

    /**
     * Where {@link ServerHttpObservationFilter} keeps the observation of a request. The filter only offers its context,
     * which cannot tell the handlers of an error, and a registry cannot be relied on to find it, because a filter can be
     * built with a registry the application does not know. A public way to get it is requested in
     * <a href="https://github.com/spring-projects/spring-framework/issues/37658">spring-framework#37658</a>.
     */
    private static final String SERVER_OBSERVATION_ATTRIBUTE =
            ServerHttpObservationFilter.class.getName() + ".observation";

    /** Where a dispatch holds the exception left on its request until it returns. */
    private static final String FAILURE_LEFT_ON_REQUEST_ATTRIBUTE =
            GrailsExceptionResolver.class.getName() + ".FAILURE_LEFT_ON_REQUEST";

    protected static final Logger LOG = LoggerFactory.getLogger(GrailsExceptionResolver.class);
    protected static final String LINE_SEPARATOR = System.getProperty("line.separator");

    protected ServletContext servletContext;
    protected GrailsApplication grailsApplication;
    protected StackTraceFilterer stackFilterer;
    protected AuditorAwareLookup auditorAwareLookup;
    private volatile boolean logFlagsResolved;
    private boolean logFullStackTrace;
    private boolean logAuditor;
    private boolean logRemoteAddr;

    /* (non-Javadoc)
     * @see org.springframework.web.servlet.handler.SimpleMappingExceptionResolver#resolveException(jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, java.lang.Object, java.lang.Exception)
     */
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Exception ex) {
        // don't reuse cached controller attribute
        request.removeAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS_AVAILABLE);
        DispatcherType dispatchedAs = request.getDispatcherType();

        ex = findWrappedException(ex);

        if (!(ex instanceof AsyncRequestTimeoutException)) {
            logFullStackTraceIfEnabled(ex);
            filterStackTrace(ex);
        }

        ModelAndView mv = super.resolveException(request, response, handler, ex);

        setStatus(request, response, mv, ex);

        if (ex instanceof AsyncRequestTimeoutException) {
            LOG.debug("Async request timed out: {}", request.getRequestURI());
        }
        else {
            logStackTrace(ex, request);
        }

        UrlMappingsHolder urlMappings = lookupUrlMappings();
        if (urlMappings != null) {
            mv = resolveViewOrForward(ex, urlMappings, request, response, mv);
        }

        recordServerError(request, reportedFailure(ex), dispatchedAs);

        return mv;
    }

    /**
     * Holds the exception this resolver leaves on the request until the dispatch has returned, because only then does
     * the request read as the filter that observes it will read it. Inside a forward it does not yet: Tomcat and Jetty
     * restore a request when a forward returns, and Undertow leaves it reading as the forward.
     * {@link org.grails.web.servlet.mvc.GrailsDispatcherServlet} calls this when a dispatch enters it, and runs what
     * this returns when the dispatch returns.
     *
     * @param request the request of the dispatch
     * @return what records the exception left on the request, unless the filter will; it does nothing for a forward or
     * include within a dispatch that already holds it
     */
    public static Runnable holdFailureLeftOnRequest(HttpServletRequest request) {
        if (request.getAttribute(FAILURE_LEFT_ON_REQUEST_ATTRIBUTE) != null) {
            return () -> { };
        }
        FailureLeftOnRequest held = new FailureLeftOnRequest(request.getDispatcherType());
        request.setAttribute(FAILURE_LEFT_ON_REQUEST_ATTRIBUTE, held);
        return () -> {
            request.removeAttribute(FAILURE_LEFT_ON_REQUEST_ATTRIBUTE);
            if (held.failure != null) {
                recordUnlessRecordedFromRequest(request, held.failure, held.dispatchedAs);
            }
        };
    }

    /**
     * Every exception this resolves is a server error, and resolving it hides it from the observation of the request,
     * whose metrics would otherwise say there was no error and whose trace would not be marked as failed. Only
     * {@link Observation#error(Throwable)} tells the observation's handlers, one of which marks the span.
     * <p>
     * An exception left in {@link WebUtils#ERROR_EXCEPTION_ATTRIBUTE} is not recorded here when it is recorded from there
     * once the dispatch returns, so that the handlers hear of it once. A dispatch that holds it decides when it returns.
     * <p>
     * A failure inside an include is not recorded: its error page is rendered inside the page that included it, which
     * goes on to answer the request.
     *
     * @param dispatchedAs how the request was dispatched before an error handler was forwarded to
     */
    private static void recordServerError(HttpServletRequest request, Throwable ex, DispatcherType dispatchedAs) {
        if (dispatchedAs == DispatcherType.INCLUDE) {
            return;
        }
        if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) == ex &&
                request.getAttribute(FAILURE_LEFT_ON_REQUEST_ATTRIBUTE) instanceof FailureLeftOnRequest held) {
            held.failure = ex;
        }
        else {
            recordUnlessRecordedFromRequest(request, ex, dispatchedAs);
        }
    }

    private static void recordUnlessRecordedFromRequest(HttpServletRequest request, Throwable ex,
            DispatcherType dispatchedAs) {
        if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) == ex && recordedFromRequest(request, dispatchedAs)) {
            return;
        }
        if (request.getAttribute(SERVER_OBSERVATION_ATTRIBUTE) instanceof Observation observation) {
            observation.error(ex);
        }
        else {
            ServerHttpObservationFilter.findObservationContext(request).ifPresent(context -> context.setError(ex));
        }
    }

    /**
     * Whether the exception left in {@link WebUtils#ERROR_EXCEPTION_ATTRIBUTE} is recorded from there once the dispatch
     * returns, judged from how the request reads now.
     * <ul>
     * <li>The filter that observes the request records it at the end of a plain dispatch. It does not look at the end of
     * an asynchronous dispatch, which {@link WebUtils#isAsync} tells even inside a forward.</li>
     * <li>A dispatch that has started asynchronous processing leaves stopping the observation to a listener of the filter.
     * Tomcat reports the exception to that listener when the dispatch returns. Jetty does not, but the request already
     * reads as asynchronous there, so the exception is recorded here.</li>
     * <li>Undertow leaves a request that was forwarded reading as a forward, so the filter takes it for a plain dispatch
     * and records the exception however the request was dispatched. A request that reads as a forward although the
     * dispatch did not begin as one was left that way.</li>
     * </ul>
     *
     * @param dispatchedAs how the dispatch began
     */
    private static boolean recordedFromRequest(HttpServletRequest request, DispatcherType dispatchedAs) {
        boolean forwardLeftOnRequest = dispatchedAs != DispatcherType.FORWARD &&
                request.getDispatcherType() == DispatcherType.FORWARD;
        return forwardLeftOnRequest || !WebUtils.isAsync(request);
    }

    /**
     * A forward to an error handler copies its model to the request, which leaves a wrapper in
     * {@link WebUtils#ERROR_EXCEPTION_ATTRIBUTE}. The attribute keeps the exception itself for whatever reads it once the
     * chain has returned, which includes the filter that observes the request.
     */
    private static void keepFailureOnRequest(HttpServletRequest request, Exception ex) {
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, reportedFailure(ex));
    }

    /**
     * What the action threw, as the observation of the request reports it. {@code DispatcherServlet} wraps an
     * {@link Error}, such as a failed {@code assert}, in a {@link ServletException}, which
     * {@link ServerHttpObservationFilter} takes off an exception that reaches it, and
     * {@link #findWrappedException(Exception)} leaves a Groovy wrapper around an {@code Error}, since it resolves
     * exceptions only.
     */
    private static Throwable reportedFailure(Exception resolved) {
        Throwable failure = resolved;
        if (failure instanceof ServletException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        while ((failure instanceof InvokerInvocationException || failure instanceof GrailsMVCException) &&
                failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    public String getRequestLogMessage(Throwable e, HttpServletRequest request) {
        Throwable cause = getRootCause(e);
        String exceptionName = cause.getClass().getSimpleName();
        return getRequestLogMessage(exceptionName, request, cause.getMessage());
    }

    public String getRequestLogMessage(HttpServletRequest request) {
        return getRequestLogMessage("Exception", request, null);
    }

    public void setServletContext(ServletContext servletContext) {

        this.servletContext = servletContext;
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
        createStackFilterer();
        this.auditorAwareLookup = new AuditorAwareLookup(grailsApplication.getMainContext());
    }

    /**
     * Obtains the root cause of the given exception
     * @param ex The exception
     * @return The root cause
     */
    public static Throwable getRootCause(Throwable ex) {
        return ExceptionUtils.getRootCause(ex);
    }

    public static int extractLineNumber(CompilationFailedException e) {
        return ExceptionUtils.extractLineNumber(e);
    }

    public static RuntimeException getFirstRuntimeException(Throwable e) {
        return ExceptionUtils.getFirstRuntimeException(e);
    }

    protected void filterStackTrace(Exception e) {
        stackFilterer.filter(e, true);
    }

    protected void setStatus(HttpServletRequest request, HttpServletResponse response, ModelAndView mv, Exception e) {
        int status = exceptionStatus(e);
        response.setStatus(status);
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, status);
        final GrailsWrappedRuntimeException gwre = new GrailsWrappedRuntimeException(servletContext, e);
        mv.addObject(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, gwre);
        mv.addObject(WebUtils.EXCEPTION_ATTRIBUTE, gwre);
    }

    protected UrlMappingsHolder lookupUrlMappings() {
        try {
            return UrlMappingUtils.lookupUrlMappings(servletContext);
        }
        catch (Exception ignored) {
            // ignore, no app ctx in this case.
            return null;
        }
    }

    Map extractRequestParamsWithUrlMappingHolder(UrlMappingsHolder urlMappings, HttpServletRequest request) {
        Map params = new HashMap();
        try {
            UrlMappingInfo requestInfo = urlMappings.match(request.getRequestURI());
            if (requestInfo != null) {
                params.putAll(UrlMappingUtils.findAllParamsNotInUrlMappingKeywords(requestInfo.getParameters()));
            }
        } catch (UrlMappingException ulrMappingException) {
            logger.debug("Could not find urlMapping which matches: " + request.getRequestURI());
        }
        return params;
    }

    protected ModelAndView resolveViewOrForward(Exception ex, UrlMappingsHolder urlMappings, HttpServletRequest request,
            HttpServletResponse response, ModelAndView mv) {

        UrlMappingInfo info;
        boolean mapsToView;
        boolean mapsToController;
        try {
            info = matchStatusCode(ex, urlMappings);
            if (info != null) {
                Map params = extractRequestParamsWithUrlMappingHolder(urlMappings, request);
                if (params != null && !params.isEmpty()) {
                    Map infoParams = info.getParameters();
                    if (infoParams != null) {
                        params.putAll(info.getParameters());
                    }
                    info = new DefaultUrlMappingInfo(info, params, grailsApplication);
                }
            }
            mapsToView = info != null && info.getViewName() != null;
            mapsToController = !mapsToView && info != null && info.getControllerName() != null;
        }
        catch (RuntimeException e) {
            // An error handler that cannot be resolved for this request - a mapping that computes its controller
            // from request state Grails did not set up, for example - must not replace the exception being
            // resolved, so the default error view renders that exception instead
            LOG.error("Unable to resolve the error handler mapped for [{}]: {}", request.getRequestURI(), e.getMessage(), e);
            return mv;
        }

        try {
            if (mapsToView) {
                resolveView(request, info, mv);
            }
            else if (mapsToController) {
                if (isErrorHandlerForwardInProgress(request)) {
                    LOG.error("The error handler for this request failed as well; not forwarding to it again");
                    return mv;
                }
                String uri = determineUri(request);
                if (GrailsWebRequest.lookup(request) == null) {
                    LOG.warn("Not forwarding [{}] to the error handler it maps to, because the request has no " +
                            "GrailsWebRequest to dispatch it with; rendering the default error view instead", uri);
                    return mv;
                }
                if (!response.isCommitted()) {
                    if (response instanceof GrailsResponseMutator) {
                        // prevent further mutation of the request since an error page needs rendered instead
                        ((GrailsResponseMutator) response).deactivateResponseMutator();
                    }
                    request.setAttribute(ERROR_HANDLER_FORWARD_IN_PROGRESS_ATTRIBUTE, Boolean.TRUE);
                    try {
                        if (WebUtils.isInclude(request)) {
                            includeRequest(info, request, response, mv, uri);
                        }
                        else {
                            forwardRequest(info, request, response, mv, uri);
                        }
                    }
                    finally {
                        request.removeAttribute(ERROR_HANDLER_FORWARD_IN_PROGRESS_ATTRIBUTE);
                        keepFailureOnRequest(request, ex);
                    }
                    // return an empty ModelAndView since the error handler has been processed
                    return new ModelAndView();
                }
            }
            return mv;
        }
        catch (Exception e) {
            LOG.error("Unable to render errors view: {}", e.getMessage(), e);
            throw new GrailsRuntimeException(e);
        }
    }

    /**
     * Whether a forward to a status-code controller mapping is already running for this request.
     * <p>
     * The forward re-enters the {@code DispatcherServlet} and resolves the same status-code mapping again,
     * since {@link WebUtils#ERROR_STATUS_CODE_ATTRIBUTE} is set. An error handler failing for a reason that
     * belongs to the request rather than the moment - an unparseable multipart body, a missing collaborator
     * - therefore fails again inside that forward and resolves back into this method, recursing until
     * {@code StackOverflowError}.
     * <p>
     * So the handler is not forwarded to from inside itself; the exception goes back to the
     * {@code DispatcherServlet}, which reports it once through the container. The flag is cleared when the
     * forward returns, leaving a later error on the same request free to use the handler again.
     *
     * @param request The request
     * @return True when this request is inside an error handler forward
     */
    protected boolean isErrorHandlerForwardInProgress(HttpServletRequest request) {
        return request.getAttribute(ERROR_HANDLER_FORWARD_IN_PROGRESS_ATTRIBUTE) != null;
    }

    protected void forwardRequest(UrlMappingInfo info, HttpServletRequest request, HttpServletResponse response,
            ModelAndView mv, String uri) throws ServletException, IOException {
        info.configure(GrailsWebRequest.lookup(request));
        String forwardUrl = UrlMappingUtils.forwardRequestForUrlMappingInfo(
                request, response, info, mv.getModel(), true);
        LOG.debug("Matched URI [{}] to URL mapping [{}], forwarding to [{}] with response [{}]",
                uri, info, forwardUrl, response.getClass());
    }

    /**
     * Answers an exception inside an include with its error handler, included where the failing action would have
     * written, as Spring MVC includes a view rather than forwarding to it there. A forward ends the response it is
     * given, which inside an include belongs to the page that included the action, so Tomcat answered that page with
     * nothing.
     *
     * @since 8.0.1
     */
    protected void includeRequest(UrlMappingInfo info, HttpServletRequest request, HttpServletResponse response,
            ModelAndView mv, String uri) throws ServletException, IOException {
        info.configure(GrailsWebRequest.lookup(request));
        String includeUrl = UrlMappingUtils.includeRequestForUrlMappingInfo(
                request, response, info, mv.getModel(), true);
        LOG.debug("Matched URI [{}] to URL mapping [{}], including [{}] with response [{}]",
                uri, info, includeUrl, response.getClass());
    }

    protected String determineUri(HttpServletRequest request) {
        String uri = (String) request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE);
        if (uri == null) {
            uri = request.getRequestURI();
        }
        return uri;
    }

    protected void resolveView(HttpServletRequest request, UrlMappingInfo info, ModelAndView mv) throws Exception {
        ViewResolver viewResolver = WebUtils.lookupViewResolver(servletContext);
        View v = UrlMappingUtils.resolveView(request, info, info.getViewName(), viewResolver);
        if (v != null) {
            mv.setView(v);
        }
    }

    protected UrlMappingInfo matchStatusCode(Exception ex, UrlMappingsHolder urlMappings) {
        int status = exceptionStatus(ex);
        UrlMappingInfo info = urlMappings.matchStatusCode(status, ex);
        if (info == null) {
            info = urlMappings.matchStatusCode(status,
                    getRootCause(ex));
        }
        if (info == null) {
            info = urlMappings.matchStatusCode(status);
        }
        return info;
    }

    private static int exceptionStatus(Exception ex) {
        if (ex instanceof AsyncRequestTimeoutException timeout) {
            return timeout.getStatusCode().value();
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    protected void logStackTrace(Exception e, HttpServletRequest request) {
        LOG.error(getRequestLogMessage(e, request), e);
    }

    /**
     * When the {@code grails.exceptionresolver.logFullStackTrace} property is enabled,
     * emits the unfiltered stack trace to the dedicated {@code StackTrace} logger.
     * Must be invoked <em>before</em> {@link #filterStackTrace(Exception)} — once the
     * filterer calls {@code setStackTrace(clean)}, the original frames are gone and
     * this method can only log the already-trimmed trace.
     */
    protected void logFullStackTraceIfEnabled(Exception e) {
        if (shouldLogFullStackTrace()) {
            DefaultStackTraceFilterer.STACK_LOG.error(StackTraceFilterer.FULL_STACK_TRACE_MESSAGE, e);
        }
    }

    protected boolean shouldLogFullStackTrace() {
        resolveLogFlags();
        return logFullStackTrace;
    }

    protected boolean shouldLogAuditor() {
        resolveLogFlags();
        return logAuditor;
    }

    protected boolean shouldLogRemoteAddr() {
        resolveLogFlags();
        return logRemoteAddr;
    }

    /**
     * Resolves the three {@code shouldLog*} flags from the application config once, on first use.
     * Config values do not change at runtime, so each {@link Config#getProperty} lookup is paid once
     * rather than per resolved exception. Subclasses that override the {@code shouldLog*} predicates
     * never reach this method.
     */
    private void resolveLogFlags() {
        if (logFlagsResolved) {
            return;
        }
        synchronized (this) {
            if (logFlagsResolved) {
                return;
            }
            Config config = grailsApplication != null ? grailsApplication.getConfig() : null;
            if (config != null) {
                logFullStackTrace = config.getProperty(Settings.SETTING_LOG_FULL_STACKTRACE, Boolean.class, false);
                logAuditor = config.getProperty(Settings.SETTING_LOG_AUDITOR, Boolean.class, false);
                logRemoteAddr = config.getProperty(Settings.SETTING_LOG_REMOTE_ADDR, Boolean.class, false);
            }
            logFlagsResolved = true;
        }
    }

    /**
     * Resolves the client address to include in the exception log headline. The default
     * returns {@link HttpServletRequest#getRemoteAddr()} — the container's view of the
     * TCP peer, which reflects forwarded-header handling only when the servlet container
     * is configured to trust a proxy chain (for example Spring Boot's
     * {@code server.forward-headers-strategy}). Subclasses can override this to apply a
     * different resolution strategy; the returned value (null or empty to omit) is
     * appended verbatim as {@code ip: <value>}.
     */
    protected String resolveRemoteAddr(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /**
     * Appends optional per-request context (remote address, current auditor) to the log
     * headline as a single parenthesised clause — e.g. {@code (ip: 1.2.3.4, user: alice)}.
     * Emits nothing when no tokens apply, so the baseline headline format is unchanged.
     */
    protected void appendRequestContext(StringBuilder sb, HttpServletRequest request) {
        List<String> tokens = new ArrayList<>(2);
        if (shouldLogRemoteAddr()) {
            String remoteAddr = resolveRemoteAddr(request);
            if (remoteAddr != null && !remoteAddr.isEmpty()) {
                tokens.add("ip: " + remoteAddr);
            }
        }
        if (shouldLogAuditor() && auditorAwareLookup != null) {
            auditorAwareLookup.getCurrentAuditor().ifPresent(auditor ->
                tokens.add("user: " + auditor)
            );
        }
        if (!tokens.isEmpty()) {
            sb.append(" (").append(String.join(", ", tokens)).append(")");
        }
    }

    /**
     * The exception an action threw, out of what Groovy and Grails wrap it in. Only those come off: what that exception
     * wraps itself is its own cause, and an exception mapping that names it has to find it. A mapping that names a cause
     * finds it through {@link #matchStatusCode(Exception, UrlMappingsHolder)}.
     */
    protected Exception findWrappedException(Exception e) {
        Throwable found = e;
        while ((found instanceof InvokerInvocationException || found instanceof GrailsMVCException) &&
                found.getCause() != null) {
            found = found.getCause();
        }
        return found instanceof Exception exception ? exception : e;
    }

    protected String getRequestLogMessage(String exceptionName, HttpServletRequest request, String message) {
        StringBuilder sb = new StringBuilder();

        sb.append(exceptionName)
          .append(" occurred when processing request: ")
          .append("[").append(request.getMethod().toUpperCase()).append("] ");

        if (request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE) != null) {
            sb.append(request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE));
        } else {
            sb.append(request.getRequestURI());
        }

        appendRequestContext(sb, request);

        Config config = grailsApplication != null ? grailsApplication.getConfig() : null;
        final boolean shouldLogRequestParameters = config != null ? config.getProperty(Settings.SETTING_LOG_REQUEST_PARAMETERS, Boolean.class, Environment.getCurrent() == Environment.DEVELOPMENT) : false;

        if (shouldLogRequestParameters) {
            // The exception being logged may be the container refusing to parse a multipart body, in which
            // case every parameter read on this request fails too - see WebUtils.readParameterNames.
            Enumeration<String> params = WebUtils.readParameterNames(request);

            if (params.hasMoreElements()) {
                String param;
                String values[];
                int i;

                sb.append(" - parameters:");
                @SuppressWarnings("unchecked")
                List<String> blackList = (config.getProperty(Settings.SETTING_EXCEPTION_RESOLVER_PARAM_EXCLUDES, List.class, Collections.emptyList()));

                if (blackList == null) {
                    blackList = Collections.emptyList();
                }
                while (params.hasMoreElements()) {
                    param = params.nextElement();
                    values = request.getParameterValues(param);

                    if (values != null) {
                        for (i = 0; i < values.length; i++) {
                            sb.append(LINE_SEPARATOR).append(param).append(": ");

                            if (isExcludedRequestParameter(param, blackList)) {
                                sb.append("***");
                            } else {
                                sb.append(values[i]);
                            }
                        }
                    }
                }
            }
        }

        sb.append(LINE_SEPARATOR);
        if (message != null) {
            sb.append(message).append(". ");
        }
        sb.append("Stacktrace follows:");

        return sb.toString();
    }

    protected boolean isExcludedRequestParameter(String parameterName, List<String> excludedParameterNames) {
        if (parameterName == null || excludedParameterNames == null) {
            return false;
        }
        for (String excludedParameterName : excludedParameterNames) {
            if (excludedParameterName != null && parameterName.equalsIgnoreCase(excludedParameterName)) {
                return true;
            }
        }
        return false;
    }

    protected void createStackFilterer() {
        StackTraceFilterer promoted = resolvePromotedStackTraceFilterer();
        if (promoted != null) {
            stackFilterer = promoted;
            return;
        }
        try {
            Class filtererClass = grailsApplication.getConfig().getProperty(Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS, Class.class, DefaultStackTraceFilterer.class);
            stackFilterer = BeanUtils.instantiateClass(filtererClass, StackTraceFilterer.class);
        }
        catch (Throwable t) {
            logger.error("Problem instantiating StackTracePrinter class, using default: " + t.getMessage());
            stackFilterer = new DefaultStackTraceFilterer();
        }
        applyLogFullStackTraceOnFilter();
    }

    /**
     * Looks up the {@link StackTraceFilterer} that
     * {@link GrailsBootstrapRegistryInitializer} promoted to the {@code ApplicationContext} during
     * bootstrap, so this resolver reuses that instance instead of instantiating a second copy from
     * config. Returns {@code null} when the bean cannot be obtained — no such bean (e.g. a
     * {@code GrailsApplication} wired up outside the normal Spring Boot bootstrap sequence), or a
     * bean of an incompatible type registered under the same name — in which case
     * {@link #createStackFilterer()} falls back to its own construction. A filterer
     * misconfiguration must never fail the context, so no {@code BeansException} escapes here.
     *
     * <p>Note that an application registering its own bean definition named
     * {@link GrailsBootstrapRegistryInitializer#STACK_TRACE_FILTERER_BEAN_NAME} replaces the
     * promoted singleton: this resolver then uses the application's bean, while the static filterer
     * already installed in {@code GrailsUtil} at bootstrap remains the config-resolved one. Replace
     * the bean only when that split is intended; otherwise configure
     * {@code grails.logging.stackTraceFiltererClass} so both consume the same instance.
     */
    protected StackTraceFilterer resolvePromotedStackTraceFilterer() {
        ApplicationContext context = grailsApplication.getMainContext();
        if (context == null) {
            return null;
        }
        try {
            return context.getBean(GrailsBootstrapRegistryInitializer.STACK_TRACE_FILTERER_BEAN_NAME,
                    StackTraceFilterer.class);
        }
        catch (BeansException e) {
            return null;
        }
    }

    /**
     * Propagates {@code grails.exceptionresolver.logFullStackTraceOnFilter} to the
     * filterer instance when it is a {@link DefaultStackTraceFilterer} (or subclass
     * thereof). Custom {@link StackTraceFilterer} implementations that do not extend
     * the default are responsible for their own logging policy.
     */
    protected void applyLogFullStackTraceOnFilter() {
        if (stackFilterer instanceof DefaultStackTraceFilterer) {
            Config config = grailsApplication != null ? grailsApplication.getConfig() : null;
            boolean logOnFilter = config == null ?
                true :
                config.getProperty(Settings.SETTING_LOG_FULL_STACKTRACE_ON_FILTER, Boolean.class, true);
            ((DefaultStackTraceFilterer) stackFilterer).setLogFullStackTraceOnFilter(logOnFilter);
        }
    }

    /** The exception left on the request of a dispatch, held until the dispatch returns. */
    private static final class FailureLeftOnRequest {

        private final DispatcherType dispatchedAs;
        private Throwable failure;

        private FailureLeftOnRequest(DispatcherType dispatchedAs) {
            this.dispatchedAs = dispatchedAs;
        }
    }
}

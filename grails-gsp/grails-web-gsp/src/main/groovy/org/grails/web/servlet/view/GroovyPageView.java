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
package org.grails.web.servlet.view;

import grails.util.Environment;
import grails.util.GrailsUtil;
import groovy.text.Template;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.gsp.GroovyPageWritable;
import org.grails.web.pages.GSPResponseWriter;
import org.grails.gsp.GroovyPageTemplate;
import org.grails.gsp.GroovyPagesTemplateEngine;
import org.grails.gsp.GroovyPagesException;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.springframework.core.io.Resource;
import org.springframework.scripting.ScriptSource;

import static org.grails.web.pages.GroovyPagesServlet.RENDERING_VIEW_ATTRIBUTE;

/**
 * A Spring View that renders Groovy Server Pages to the response. It requires an instance
 * of GroovyPagesTemplateEngine to be set and will render to view returned by the getUrl()
 * method of AbstractUrlBasedView
 *
 * This view also requires an instance of GrailsWebRequest to be bound to the currently
 * executing Thread using Spring's RequestContextHolder. This can be done with by adding
 * the GrailsWebRequestFilter.
 *
 * @see #getUrl()
 * @see org.grails.gsp.GroovyPagesTemplateEngine
 * @see org.springframework.web.context.request.RequestContextHolder
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class GroovyPageView extends AbstractGrailsView {
    private static final Log LOG = LogFactory.getLog(GroovyPageView.class);
    protected GroovyPagesTemplateEngine templateEngine;
    private long createTimestamp = System.currentTimeMillis();
    private static final long LASTMODIFIED_CHECK_INTERVAL =  Long.getLong("grails.gsp.reload.interval", 5000).longValue();
    private ScriptSource scriptSource;
    protected GroovyPageTemplate template;
    public static final String EXCEPTION_MODEL_KEY = "exception";
    private static boolean developmentMode = Environment.isDevelopmentMode();
    
    @Override
    protected void renderTemplate(Map<String, Object> model, GrailsWebRequest webRequest, HttpServletRequest request,
            HttpServletResponse response) {
        request.setAttribute(RENDERING_VIEW_ATTRIBUTE, Boolean.TRUE);
        GSPResponseWriter out = null;
        try {
            out = createResponseWriter(webRequest, response);
            final GroovyPageWritable writable = template.make(model);
            writable.setShowSource( developmentMode  && request.getParameter("showSource") != null);
            writable.writeTo(out);
        }
        catch (Exception e) {
            out.setError();
            handleException(e, templateEngine);
        }
        finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Performs exception handling by attempting to render the Errors view.
     *
     * @param exception The exception that occured

     * @param engine The GSP engine
     */
    protected void handleException(Exception exception,
            GroovyPagesTemplateEngine engine)  {

        GrailsUtil.deepSanitize(exception);
        if(LOG.isDebugEnabled()) {
            LOG.debug("Error processing GroovyPageView: " + exception.getMessage(), exception);
        }
        if (exception instanceof GroovyPagesException) {
            throw (GroovyPagesException) exception;
        }

        if (engine == null) {
            throw new GroovyPagesException("Error processing GroovyPageView: " + exception.getMessage(),
                 exception, -1, getUrl());
        }

        throw createGroovyPageException(exception, engine, getUrl());
    }

    public static GroovyPagesException createGroovyPageException(Exception exception, GroovyPagesTemplateEngine engine, String pageUrl) {
        GroovyPageTemplate t = (GroovyPageTemplate) engine.createTemplate(pageUrl);
        StackTraceElement[] stackTrace = exception.getStackTrace();
        String className = stackTrace[0].getClassName();
        int lineNumber = stackTrace[0].getLineNumber();
        if (className.contains("_gsp")) {
            int[] lineNumbers = t.getMetaInfo().getLineNumbers();
            if (lineNumber < lineNumbers.length) {
                lineNumber = lineNumbers[lineNumber - 1];
            }
        }

        Resource resource = pageUrl != null ? engine.getResourceForUri(pageUrl) : null;
        String file;
        try {
            file = resource != null && resource.exists() ? resource.getFile().getAbsolutePath() : pageUrl;
        } catch (IOException e) {
            file = pageUrl;
        }

        return new GroovyPagesException("Error processing GroovyPageView: " + exception.getMessage(),
                exception, lineNumber, file);
    }

    /**
     * Creates the Response Writer for the specified HttpServletResponse instance.
     *
     * @param response The HttpServletResponse instance
     * @return A response Writer
     */
    protected GSPResponseWriter createResponseWriter(GrailsWebRequest webRequest, HttpServletResponse response) {
        GSPResponseWriter out = GSPResponseWriter.getInstance(response);
        webRequest.setOut(out);
        return out;
    }

    public void setTemplateEngine(GroovyPagesTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createTimestamp > LASTMODIFIED_CHECK_INTERVAL;
    }

    public void setScriptSource(ScriptSource scriptSource) {
        this.scriptSource = scriptSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        try {
            initTemplate();
        } catch(Exception e) {
            handleException(e, templateEngine);
        }
    }

    protected void initTemplate() throws IOException {
        if (template == null) {
            if (scriptSource == null) {
                template = (GroovyPageTemplate) templateEngine.createTemplate(getUrl());
            } else {
                template = (GroovyPageTemplate) templateEngine.createTemplate(scriptSource);
            }
        }
        if (template != null) {
            template.setAllowSettingContentType(true);
        }
    }
    
    public void rethrowRenderException(Throwable ex, String message) {
        throw new GroovyPagesException(message, ex);
    }

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = (GroovyPageTemplate) template;
    }

    @Override
    protected boolean isUrlRequired() {
        return template == null;
    }
}

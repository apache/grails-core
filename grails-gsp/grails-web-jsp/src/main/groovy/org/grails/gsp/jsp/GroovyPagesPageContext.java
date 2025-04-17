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
package org.grails.gsp.jsp;

import groovy.lang.Binding;
import jakarta.el.ELContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.*;
import jakarta.servlet.jsp.tagext.BodyContent;
import org.grails.gsp.GroovyPage;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * A JSP PageContext implementation for use with GSP.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
public class GroovyPagesPageContext extends PageContext {

    private static final Enumeration<String> EMPTY_ENUMERATION = new Enumeration<>() {
        @Override
        public boolean hasMoreElements() {
            return false;
        }

        @Override
        public String nextElement() {
            throw new NoSuchElementException();
        }
    };
    private final ServletContext servletContext;
    private final Servlet servlet;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final ServletConfig servletConfig;
    private final Binding pageScope;
    private final GrailsWebRequest webRequest;
    private JspWriter jspOut;
    private final Deque<JspWriter> outStack = new ArrayDeque<>();
    private final List<Object> tags = new ArrayList<>();

    public GroovyPagesPageContext(Servlet pagesServlet, Binding pageScope) {
        Assert.notNull(pagesServlet, "GroovyPagesPageContext class requires a reference to the GSP servlet");
        webRequest = (GrailsWebRequest) RequestContextHolder.currentRequestAttributes();

        servletContext = webRequest.getServletContext();
        request = webRequest.getCurrentRequest();
        response = webRequest.getCurrentResponse();
        servlet = pagesServlet;
        servletConfig = pagesServlet.getServletConfig();
        this.pageScope = pageScope;
        var session = request.getSession(false);
        // setup initial writer
        pushWriter(new JspWriterDelegate(webRequest.getOut()));
        // Register page attributes as per JSP spec
        setAttribute(REQUEST, request);
        setAttribute(RESPONSE, response);
        if (session != null) {
            setAttribute(SESSION, session);
        }
        setAttribute(PAGE, servlet);
        setAttribute(CONFIG, servlet.getServletConfig());
        setAttribute(PAGECONTEXT, this);
        setAttribute(APPLICATION, servletContext);
    }

    void popWriter() {
        outStack.pop();
        jspOut = (JspWriter) outStack.peek();
        setCurrentOut();
    }

    void pushWriter(JspWriter out) {
        outStack.push(out);
        jspOut = out;
        setCurrentOut();
    }

    private void setCurrentOut() {
        setAttribute(OUT, jspOut);
        setAttribute(GroovyPage.OUT, jspOut);
        webRequest.setOut(jspOut);
    }

    Object peekTopTag(Class<?> tagClass) {
        for (ListIterator<?> iter = tags.listIterator(tags.size()); iter.hasPrevious();) {
            Object tag = iter.previous();
            if (tagClass.isInstance(tag)) {
                return tag;
            }
        }
        return null;
    }

    void popTopTag() {
        tags.remove(tags.size() - 1);
    }

    void pushTopTag(Object tag) {
        tags.add(tag);
    }

    @Override
    public BodyContent pushBody() {
        BodyContent bc = new BodyContentImpl(getOut(), true);
        pushWriter(bc);
        return bc;
    }

    @Override
    public JspWriter popBody() {
        popWriter();
        return (JspWriter) getAttribute(OUT);
    }

    public GroovyPagesPageContext(Binding pageScope) {
        this(new GenericServlet() {
            @Override
            public ServletConfig getServletConfig() {
                return this;
            }
            @Override
            public void service(ServletRequest servletRequest, ServletResponse servletResponse) {
                // do nothing;
            }
        }, pageScope != null ? pageScope : new Binding());
    }

    public GroovyPagesPageContext() {
        this(new Binding());
    }

    @Override
    public void initialize(Servlet s, ServletRequest servletRequest, ServletResponse servletResponse,
            String errorPageURL, boolean needSession, int bufferSize, boolean autoFlush) {
        // do nothing, not constructed for container
    }

    @Override
    public void release() {
        // do nothing, not released by container
    }

    @Override
    public HttpSession getSession() {
        return request.getSession(false);
    }

    @Override
    public Object getPage() {
        return servlet;
    }

    @Override
    public ServletRequest getRequest() {
        return request;
    }

    @Override
    public ServletResponse getResponse() {
        return response;
    }

    @Override
    public Exception getException() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletConfig getServletConfig() {
        return servletConfig;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public void forward(String url) throws ServletException, IOException {
        request.getRequestDispatcher(url).forward(request, response);
    }

    @Override
    public void include(String url) throws ServletException, IOException {
        include(url, false);
    }

    @Override
    public void include(String url, boolean flush) throws ServletException, IOException {
        request.getRequestDispatcher(url).include(request, response);
    }

    @Override
    public void handlePageException(Exception e) throws ServletException, IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void handlePageException(Throwable throwable) throws ServletException, IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(String name, Object value) {
        Assert.notNull(name, "Attribute name cannot be null");
        pageScope.setVariable(name, value);
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {
        Assert.notNull(name, "Attribute name cannot be null");

        switch (scope) {
            case PAGE_SCOPE:
                setAttribute(name, value);
                break;
            case REQUEST_SCOPE:
                request.setAttribute(name,value);
                break;
            case SESSION_SCOPE:
                request.getSession(true).setAttribute(name, value);
                break;
            case APPLICATION_SCOPE:
                servletContext.setAttribute(name, value);
                break;
            default:
                setAttribute(name, value);
        }
    }

    @Override
    public Object getAttribute(String name) {
        Assert.notNull(name, "Attribute name cannot be null");

        if (pageScope.getVariables().containsKey(name)) {
            return pageScope.getVariable(name);
        }

        return null;
    }

    @Override
    public Object getAttribute(String name, int scope) {
        Assert.notNull(name, "Attribute name cannot be null");

        switch (scope) {
            case PAGE_SCOPE:        return getAttribute(name);
            case REQUEST_SCOPE:     return request.getAttribute(name);
            case SESSION_SCOPE:     return request.getSession(true).getAttribute(name);
            case APPLICATION_SCOPE: return servletContext.getAttribute(name);
            default:                return getAttribute(name);
        }
    }

    @Override
    public Object findAttribute(String name) {
        Assert.notNull(name, "Attribute name cannot be null");

        int scope = getAttributesScope(name);
        if (scope > 0) {
            return getAttribute(name, scope);
        }

        return null;
    }

    @Override
    public void removeAttribute(String name) {
        Assert.notNull(name, "Attribute name cannot be null");
        pageScope.getVariables().remove(name);
    }

    @Override
    public void removeAttribute(String name, int scope) {
        Assert.notNull(name, "Attribute name cannot be null");

        switch (scope) {
            case PAGE_SCOPE:
                removeAttribute(name);
                break;
            case REQUEST_SCOPE:
                request.removeAttribute(name);
                break;
            case SESSION_SCOPE:
                HttpSession httpSession = request.getSession(false);
                if (httpSession != null) {
                    httpSession.removeAttribute(name);
                }
                break;
            case APPLICATION_SCOPE:
                servletContext.removeAttribute(name);
                break;
            default:
                removeAttribute(name);
        }
    }

    @Override
    public int getAttributesScope(String name) {
        Assert.notNull(name, "Attribute name cannot be null");

        if (pageScope.getVariables().containsKey(name)) {
            return PAGE_SCOPE;
        }

        if (request.getAttribute(name) != null) {
            return REQUEST_SCOPE;
        }

        HttpSession httpSession = request.getSession(false);
        if (httpSession != null && httpSession.getAttribute(name) != null) {
            return SESSION_SCOPE;
        }

        if (servletContext.getAttribute(name) !=null) {
            return APPLICATION_SCOPE;
        }

        return 0;
    }

    @Override
    public Enumeration<String> getAttributeNamesInScope(int scope) {
        switch (scope) {
            case PAGE_SCOPE:
                final var i = ((Map<String,Object>) pageScope.getVariables()).keySet().iterator();
                return new Enumeration<>() {
                    @Override
                    public boolean hasMoreElements() {
                        return i.hasNext();
                    }

                    @Override
                    public String nextElement() {
                        return i.next();
                    }
                };
            case REQUEST_SCOPE:
                return request.getAttributeNames();
            case SESSION_SCOPE:
                var httpSession = request.getSession(false);
                if (httpSession != null) {
                    return httpSession.getAttributeNames();
                }
                else {
                    return EMPTY_ENUMERATION;
                }
            case APPLICATION_SCOPE:
                return servletContext.getAttributeNames();
        }
        return EMPTY_ENUMERATION;
    }

    @Override
    public JspWriter getOut() {
        Writer out = webRequest.getOut();
        if (out instanceof JspWriter) {
            return (JspWriter)out;
        }

        out = new JspWriterDelegate(out);
        webRequest.setOut(out);
        return (JspWriter)out;
    }

//    public ExpressionFactory getExpressionEvaluator() {
//        try {
//            Class<?> type = AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> Thread.currentThread().getContextClassLoader()).loadClass("jakarta.el.ExpressionFactory");
//            return (ExpressionFactory) type.getDeclaredConstructor().newInstance();
//        }
//        catch (Exception e) {
//            throw new UnsupportedOperationException("In order for the getExpressionEvaluator() " +
//                    "method to work, you must have downloaded the apache commons-el jar and " +
//                    "made it available in the classpath.");
//        }
//    }

//    @Override
//    public VariableResolver getVariableResolver() {
//        final PageContext ctx = this;
//        return new VariableResolver() {
//            public Object resolveVariable(String name) throws ELException {
//                return ctx.findAttribute(name);
//            }
//        };
//    }

    static {
        if (JspFactory.getDefaultFactory() == null) {
            JspFactory.setDefaultFactory(new GroovyPagesJspFactory());
        }
    }

    private ELContext elContext;

    @Override
    public ELContext getELContext() {
        if (elContext == null) {
            JspApplicationContext jspContext = JspFactory.getDefaultFactory().getJspApplicationContext(getServletContext());
            if  (jspContext instanceof GroovyPagesJspApplicationContext) {
                elContext = ((GroovyPagesJspApplicationContext)jspContext).createELContext(this);
                elContext.putContext( JspContext.class, this );
            }
            else {
                throw new IllegalStateException("Unable to create ELContext for a JspApplicationContext. It must be an instance of [GroovyPagesJspApplicationContext] do not override JspFactory.setDefaultFactory()!");
            }
        }
        return elContext;
    }
}

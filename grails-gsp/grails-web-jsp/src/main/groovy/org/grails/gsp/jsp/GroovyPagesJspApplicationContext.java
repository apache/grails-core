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

import jakarta.el.*;
import jakarta.servlet.jsp.JspApplicationContext;
import jakarta.servlet.jsp.el.ImplicitObjectELResolver;
import jakarta.servlet.jsp.el.ScopedAttributeELResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.ClassUtils;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyPagesJspApplicationContext implements JspApplicationContext {

    private static final Log LOG = LogFactory.getLog(GroovyPagesJspApplicationContext.class);

    private static final ExpressionFactory expressionFactoryImpl = findExpressionFactoryImplementation();

    private final LinkedList<ELContextListener> listeners = new LinkedList<ELContextListener>();
    private final CompositeELResolver elResolver = new CompositeELResolver();
    private final CompositeELResolver additionalResolvers = new CompositeELResolver();

    public GroovyPagesJspApplicationContext() {
        elResolver.add(new ImplicitObjectELResolver());
        elResolver.add(additionalResolvers);
        elResolver.add(new MapELResolver());
        elResolver.add(new ResourceBundleELResolver());
        elResolver.add(new ListELResolver());
        elResolver.add(new ArrayELResolver());
        elResolver.add(new BeanELResolver());
        elResolver.add(new ScopedAttributeELResolver());
    }

    private static ExpressionFactory findExpressionFactoryImplementation() {
        ExpressionFactory ef = tryExpressionFactoryImplementation("com.sun");
        if (ef == null) {
            ef = tryExpressionFactoryImplementation("org.apache");
            if (ef == null) {
                LOG.warn("Could not find any implementation for " +
                        ExpressionFactory.class.getName());
            }
        }
        return ef;
    }

    private static ExpressionFactory tryExpressionFactoryImplementation(String packagePrefix) {
        String className = packagePrefix + ".el.ExpressionFactoryImpl";
        try {
            Class<?> cl = ClassUtils.forName(className, null);
            if (ExpressionFactory.class.isAssignableFrom(cl)) {
                LOG.info("Using " + className + " as implementation of " +
                        ExpressionFactory.class.getName());
                return (ExpressionFactory)cl.newInstance();
            }
            LOG.warn("Class " + className + " does not implement " +
                    ExpressionFactory.class.getName());
        }
        catch(ClassNotFoundException e) {
            // ignored
        }
        catch(Exception e) {
            LOG.error("Failed to instantiate " + className, e);
        }
        return null;
    }

    public void addELResolver(ELResolver resolver) {
        additionalResolvers.add(resolver);
    }

    public ExpressionFactory getExpressionFactory() {
        return expressionFactoryImpl;
    }

    public void addELContextListener(ELContextListener elContextListener) {
        synchronized(listeners) {
            listeners.addLast(elContextListener);
        }
    }

    ELContext createELContext(GroovyPagesPageContext pageCtx) {
        ELContext ctx = new GroovyPagesELContext(pageCtx);
        ELContextEvent event = new ELContextEvent(ctx);
        synchronized(listeners) {
            for (Iterator<ELContextListener> iter = listeners.iterator(); iter.hasNext();) {
                iter.next().contextCreated(event);
            }
        }
        return ctx;
    }

    private class GroovyPagesELContext extends ELContext {
        private GroovyPagesPageContext pageCtx;

        public GroovyPagesELContext(GroovyPagesPageContext pageCtx) {
            this.pageCtx = pageCtx;
        }

        @Override
        public ELResolver getELResolver() {
            return elResolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return new VariableMapper() {

                @Override
                public ValueExpression resolveVariable(String name) {
                    Object o = pageCtx.findAttribute(name);
                    if (o == null) return null;
                    return expressionFactoryImpl.createValueExpression(o, o.getClass());
                }

                @Override
                public ValueExpression setVariable(String name, ValueExpression valueExpression) {
                    ValueExpression previous = resolveVariable(name);
                    if (valueExpression != null) {
                        GroovyPagesELContext.this.pageCtx.setAttribute(name, valueExpression.getValue(GroovyPagesELContext.this));
                    }
                    return previous;
                }
            };
        }
    }
}

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
package org.grails.gsp.jsp

import groovy.transform.CompileStatic

import jakarta.el.ArrayELResolver
import jakarta.el.BeanELResolver
import jakarta.el.CompositeELResolver
import jakarta.el.ELContext
import jakarta.el.ELContextEvent
import jakarta.el.ELContextListener
import jakarta.el.ELResolver
import jakarta.el.ExpressionFactory
import jakarta.el.FunctionMapper
import jakarta.el.ListELResolver
import jakarta.el.MapELResolver
import jakarta.el.ResourceBundleELResolver
import jakarta.el.ValueExpression
import jakarta.el.VariableMapper
import jakarta.servlet.jsp.JspApplicationContext
import jakarta.servlet.jsp.el.ImplicitObjectELResolver
import jakarta.servlet.jsp.el.ScopedAttributeELResolver

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import org.springframework.util.ClassUtils

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class GroovyPagesJspApplicationContext implements JspApplicationContext {

    private static final Log LOG = LogFactory.getLog(GroovyPagesJspApplicationContext)

    private static final ExpressionFactory expressionFactoryImpl = findExpressionFactoryImplementation()

    private final LinkedList<ELContextListener> listeners = new LinkedList<>()
    private final CompositeELResolver elResolver = new CompositeELResolver()
    private final CompositeELResolver additionalResolvers = new CompositeELResolver()

    GroovyPagesJspApplicationContext() {
        elResolver.add(new ImplicitObjectELResolver())
        elResolver.add(additionalResolvers)
        elResolver.add(new MapELResolver())
        elResolver.add(new ResourceBundleELResolver())
        elResolver.add(new ListELResolver())
        elResolver.add(new ArrayELResolver())
        elResolver.add(new BeanELResolver())
        elResolver.add(new ScopedAttributeELResolver())
    }

    private static ExpressionFactory findExpressionFactoryImplementation() {
        ExpressionFactory ef = tryExpressionFactoryImplementation('com.sun')
        if (ef == null) {
            ef = tryExpressionFactoryImplementation('org.apache')
            if (ef == null) {
                LOG.warn('Could not find any implementation for ' +
                        ExpressionFactory.name)
            }
        }
        return ef
    }

    private static ExpressionFactory tryExpressionFactoryImplementation(String packagePrefix) {
        String className = packagePrefix + '.el.ExpressionFactoryImpl'
        try {
            Class<?> cl = ClassUtils.forName(className, null)
            if (ExpressionFactory.isAssignableFrom(cl)) {
                LOG.info('Using ' + className + ' as implementation of ' +
                        ExpressionFactory.name)
                return (ExpressionFactory) cl.getDeclaredConstructor().newInstance()
            }
            LOG.warn('Class ' + className + ' does not implement ' +
                    ExpressionFactory.name)
        }
        catch (ClassNotFoundException e) {
            // ignored
        }
        catch (Exception e) {
            LOG.error('Failed to instantiate ' + className, e)
        }
        return null
    }

    void addELResolver(ELResolver resolver) {
        additionalResolvers.add(resolver)
    }

    ExpressionFactory getExpressionFactory() {
        return expressionFactoryImpl
    }

    void addELContextListener(ELContextListener elContextListener) {
        synchronized (listeners) {
            listeners.addLast(elContextListener)
        }
    }

    ELContext createELContext(GroovyPagesPageContext pageCtx) {
        ELContext ctx = new GroovyPagesELContext(pageCtx)
        ELContextEvent event = new ELContextEvent(ctx)
        synchronized (listeners) {
            for (Iterator<ELContextListener> iter = listeners.iterator(); iter.hasNext();) {
                iter.next().contextCreated(event)
            }
        }
        return ctx
    }

    private class GroovyPagesELContext extends ELContext {
        private GroovyPagesPageContext pageCtx

        GroovyPagesELContext(GroovyPagesPageContext pageCtx) {
            this.pageCtx = pageCtx
        }

        @Override
        ELResolver getELResolver() {
            return GroovyPagesJspApplicationContext.this.elResolver
        }

        @Override
        FunctionMapper getFunctionMapper() {
            return null
        }

        @Override
        VariableMapper getVariableMapper() {
            return new VariableMapper() {

                @Override
                ValueExpression resolveVariable(String name) {
                    Object o = GroovyPagesELContext.this.pageCtx.findAttribute(name)
                    if (o == null) return null
                    return GroovyPagesJspApplicationContext.expressionFactoryImpl.createValueExpression(o, o.getClass())
                }

                @Override
                ValueExpression setVariable(String name, ValueExpression valueExpression) {
                    ValueExpression previous = resolveVariable(name)
                    if (valueExpression != null) {
                        GroovyPagesELContext.this.pageCtx.setAttribute(name, valueExpression.getValue(GroovyPagesELContext.this))
                    }
                    return previous
                }
            }
        }
    }
}

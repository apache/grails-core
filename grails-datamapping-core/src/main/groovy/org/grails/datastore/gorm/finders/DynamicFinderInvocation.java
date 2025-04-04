/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.finders;

import grails.gorm.DetachedCriteria;
import groovy.lang.Closure;

import java.util.List;

/**
 * Value object used to construct all the information necessary to invoke a dynamic finder.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class DynamicFinderInvocation {

    private Class javaClass;
    private String methodName;
    private Object[] arguments;
    private List<MethodExpression> expressions;
    private Closure criteria;
    private String operator;
    private DetachedCriteria detachedCriteria;

    public DynamicFinderInvocation(Class javaClass, String methodName, Object[] arguments,
                                   List<MethodExpression> expressions, Closure criteria, String operator) {
        this.javaClass = javaClass;
        this.methodName = methodName;
        this.arguments = arguments;
        this.expressions = expressions;
        this.criteria = criteria;
        this.operator = operator;
    }

    public Class getJavaClass() {
        return javaClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public List<MethodExpression> getExpressions() {
        return expressions;
    }

    public Closure getCriteria() {
        return criteria;
    }

    public String getOperator() {
        return operator;
    }

    public DetachedCriteria getDetachedCriteria() {
        return detachedCriteria;
    }

    public void setDetachedCriteria(DetachedCriteria detachedCriteria) {
        this.detachedCriteria = detachedCriteria;
    }
}

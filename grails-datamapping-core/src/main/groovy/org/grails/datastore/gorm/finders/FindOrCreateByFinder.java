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

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.MappingContext;
import org.springframework.core.convert.ConversionException;

/**
 * Finder used to return a single result
 */
public class FindOrCreateByFinder extends AbstractFindByFinder {

    public static final String METHOD_PATTERN = "(findOrCreateBy)([A-Z]\\w*)";

    public FindOrCreateByFinder(final String methodPattern, final Datastore datastore) {
        super(Pattern.compile(methodPattern), datastore);
    }

    public FindOrCreateByFinder(final Datastore datastore) {
        this(METHOD_PATTERN, datastore);
    }


    public FindOrCreateByFinder(MappingContext mappingContext) {
        super(Pattern.compile(METHOD_PATTERN), mappingContext);
    }

    public FindOrCreateByFinder(final String methodPattern, MappingContext mappingContext) {
        super(Pattern.compile(methodPattern), mappingContext);
    }


    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Object doInvokeInternal(final DynamicFinderInvocation invocation) {

        if (OPERATOR_OR.equals(invocation.getOperator())) {
            throw new MissingMethodException(invocation.getMethodName(), invocation.getJavaClass(), invocation.getArguments());
        }

        Object result;
        try {
            result = super.doInvokeInternal(invocation);
        } catch (ConversionException e) { // TODO this is not the right place to deal with this...
            throw new MissingMethodException(invocation.getMethodName(), invocation.getJavaClass(), invocation.getArguments());
        }
        if (result == null) {
            Map m = new HashMap();
            List<MethodExpression> expressions = invocation.getExpressions();
            for (MethodExpression me : expressions) {
                if (!(me instanceof MethodExpression.Equal)) {
                    throw new MissingMethodException(invocation.getMethodName(), invocation.getJavaClass(), invocation.getArguments());
                }
                String propertyName = me.propertyName;
                Object[] arguments = me.getArguments();
                m.put(propertyName, arguments[0]);
            }
            MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(invocation.getJavaClass());
            result = metaClass.invokeConstructor(new Object[]{m});
            if (shouldSaveOnCreate()) {
                metaClass.invokeMethod(result, "save", null);
            }
        }
        return result;
    }

    protected boolean shouldSaveOnCreate() {
        return false;
    }
}

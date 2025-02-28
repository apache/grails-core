/*
 * Copyright 2004-2008 the original author or authors.
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
package org.grails.web.converters.marshaller;

import groovy.lang.Closure;

import org.grails.web.converters.Converter;
import org.grails.web.converters.exceptions.ConverterException;

/**
 * ObjectMarshaller that delegates the conversion logic to the supplied closure
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
@SuppressWarnings("rawtypes")
public class ClosureObjectMarshaller<T extends Converter> implements ObjectMarshaller<T> {

    private Class<?> clazz;

    private Closure closure;

    public ClosureObjectMarshaller(Class<?> clazz, Closure closure) {
        this.clazz = clazz;
        this.closure = closure;
    }

    public boolean supports(Object object) {
        return clazz.isAssignableFrom(object.getClass());
    }

    public void marshalObject(Object object, T converter) throws ConverterException {
        try {
            int argCount = closure.getParameterTypes().length;
            Object result = null;
            if (argCount <= 1) {
                result = closure.call(object);
            }
            else if (argCount == 2) {
                result = closure.call(new Object[]{object, converter});
            }
            else {
                throw new ConverterException(
                        "Invalid Parameter count for registered Object Marshaller for class " + clazz.getName());
            }

            if (result != null && result != object && result != converter) {
                converter.convertAnother(result);
            }
        }
        catch (Exception e) {
            throw e instanceof ConverterException ? (ConverterException) e : new ConverterException(e);
        }
    }
}

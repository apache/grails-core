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
package org.grails.web.converters.configuration;

import org.grails.web.converters.Converter;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * A Spring Bean that can be used to register an ObjectMarshaller.
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
@SuppressWarnings("rawtypes")
public class ObjectMarshallerRegisterer {

    private ObjectMarshaller marshaller;
    private Class<? extends Converter> converterClass;
    private int priority = DefaultConverterConfiguration.DEFAULT_PRIORITY;

    public ObjectMarshaller getMarshaller() {
        return marshaller;
    }

    public void setMarshaller(ObjectMarshaller marshaller) {
        this.marshaller = marshaller;
    }

    public Class<? extends Converter> getConverterClass() {
        return converterClass;
    }

    public void setConverterClass(Class<? extends Converter> converterClass) {
        this.converterClass = converterClass;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.converters.jackson;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

/** Leaves out a bean property that the {@link JsonProjection} of the current write excludes. */
final class ProjectedPropertyWriter extends BeanPropertyWriter {

    private final BeanPropertyWriter delegate;
    private final String beanName;

    ProjectedPropertyWriter(BeanPropertyWriter delegate, String beanName) {
        super(delegate);
        this.delegate = delegate;
        this.beanName = beanName;
    }

    @Override
    public ProjectedPropertyWriter rename(NameTransformer transformer) {
        return new ProjectedPropertyWriter(delegate.rename(transformer), beanName);
    }

    @Override
    public void assignSerializer(ValueSerializer<Object> serializer) {
        delegate.assignSerializer(serializer);
    }

    @Override
    public void assignNullSerializer(ValueSerializer<Object> serializer) {
        delegate.assignNullSerializer(serializer);
    }

    @Override
    public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        if (included(bean, context)) {
            delegate.serializeAsProperty(bean, generator, context);
        }
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        if (included(bean, context)) {
            delegate.serializeAsElement(bean, generator, context);
        }
        else {
            delegate.serializeAsOmittedElement(bean, generator, context);
        }
    }

    @Override
    public void depositSchemaProperty(JsonObjectFormatVisitor visitor, SerializationContext context) {
        delegate.depositSchemaProperty(visitor, context);
    }

    private boolean included(Object bean, SerializationContext context) {
        Object projection = context.getAttribute(JsonProjection.ATTRIBUTE);
        return !(projection instanceof JsonProjection projected) || projected.appliesTo(bean.getClass()) == null ||
                projected.includes(beanName, getName());
    }
}

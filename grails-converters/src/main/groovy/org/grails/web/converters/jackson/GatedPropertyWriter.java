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
package org.grails.web.converters.jackson;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.AnyGetterWriter;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.BeanSerializerBuilder;
import tools.jackson.databind.ser.PropertyFilter;
import tools.jackson.databind.ser.bean.UnwrappingBeanPropertyWriter;
import tools.jackson.databind.ser.impl.FilteredBeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

/**
 * Writes a property of a bean as Jackson writes it, when every gate of the write includes it: the JSON converter's
 * includes and excludes, the projection of a response, and the rendering of a domain class instance. Each module that
 * gates a property adds its gate to the same writer.
 *
 * <p>The gates are writers, rather than a {@code PropertyFilter}, since a mapper has one {@code FilterProvider}, which
 * the application's filters use, which Spring's Jackson converter replaces with the one a write is given, and which an
 * application's {@code SimpleFilterProvider} fails for a filter id it does not know: a filter id of every class would
 * break the application's own filters, and a write with a filter of its own would lose the gates. The writers of the
 * entries of a {@code @JsonAnyGetter} and of an unwrapped property are wrapped by subclasses of Jackson's own, which
 * Jackson takes for them; {@code GatedPropertyWriterContractSpec} fails where a release of Jackson adds a method to
 * those that the subclasses do not forward.</p>
 *
 * @since 9.0
 */
final class GatedPropertyWriter extends BeanPropertyWriter {

    /** Whether a property of a bean is written. */
    @FunctionalInterface
    interface Gate {

        /**
         * @param bean the bean being written
         * @param context the context of the write
         * @param writer the writer of the property, which names it as it is written
         * @return whether the property is written
         */
        boolean includes(Object bean, SerializationContext context, BeanPropertyWriter writer) throws Exception;

        /**
         * @param other another gate
         * @return a gate that includes a property when this gate and the other one do
         */
        default Gate and(Gate other) {
            return (bean, context, writer) -> includes(bean, context, writer) && other.includes(bean, context, writer);
        }
    }

    private final BeanPropertyWriter delegate;

    private final Gate gate;

    private GatedPropertyWriter(BeanPropertyWriter delegate, Gate gate) {
        super(delegate);
        this.delegate = delegate;
        this.gate = gate;
    }

    /**
     * @param writer a property writer, which may be gated already
     * @param gate a gate to add
     * @param checked whether Jackson checks the names an unwrapped property writes against the bean's other properties,
     *        as it does for its own writers, which requires that the bean's writers are those that may be written. A
     *        writer that is gated already is checked or not as this call says, whatever the call that gated it said
     * @return a writer that writes the property when its gates, and this one, include it
     */
    static BeanPropertyWriter gated(BeanPropertyWriter writer, Gate gate, boolean checked) {
        if (writer instanceof AnyGetter gated) {
            return new AnyGetter(gated.delegate, gated.gate.and(gate));
        }
        if (writer instanceof AnyGetterWriter anyGetter) {
            return new AnyGetter(anyGetter, gate);
        }
        if (writer instanceof GatedPropertyWriter gated) {
            return gated(gated.delegate, gated.gate.and(gate), checked);
        }
        if (writer instanceof Unwrapping gated) {
            return gated(gated.delegate, gated.gate.and(gate), checked);
        }
        if (checked && writer instanceof UnwrappingBeanPropertyWriter unwrapping) {
            return new Unwrapping(unwrapping, gate);
        }
        return new GatedPropertyWriter(writer, gate);
    }

    /**
     * Gates the writer of the entries of a class's {@code @JsonAnyGetter}, which Jackson adds once the properties are
     * gated: in the place of the writer of the property its field or getter is, or after the other writers.
     *
     * @param builder the builder of the bean serializer
     * @param description the bean's description
     * @param gates the gate of a property, by the name the class gives it: that of the property the {@code @JsonAnyGetter}
     *        is the field or the getter of, or else the name of the {@code @JsonAnyGetter} itself
     * @param checked whether Jackson checks the names an unwrapped property writes, as for {@link #gated}
     * @return the builder
     */
    static BeanSerializerBuilder gateAnyGetter(BeanSerializerBuilder builder, BeanDescription description,
            Function<String, Gate> gates, boolean checked) {
        List<BeanPropertyWriter> properties = builder.getProperties();
        for (int i = 0; i < properties.size(); i++) {
            if (properties.get(i) instanceof AnyGetterWriter anyGetter) {
                BeanPropertyDefinition property = anyGetterProperty(description);
                BeanPropertyWriter gated = gated(anyGetter,
                        gates.apply(property != null ? property.getInternalName() : anyGetter.getName()), checked);
                List<BeanPropertyWriter> kept = new ArrayList<>(properties);
                kept.set(i, gated);
                builder.setProperties(kept);
                BeanPropertyWriter[] filtered = builder.getFilteredProperties();
                if (filtered != null && filtered[i] != null) {
                    // the writer of the views Jackson writes the entries in
                    BeanPropertyWriter[] keptFiltered = filtered.clone();
                    keptFiltered[i] = filtered[i] == anyGetter ? gated :
                            FilteredBeanPropertyWriter.constructViewBased(gated, anyGetter.getViews());
                    builder.setFilteredProperties(keptFiltered);
                }
                break;
            }
        }
        return builder;
    }

    /**
     * @param description a bean's description
     * @return the property whose field or getter is the class's {@code @JsonAnyGetter}, or {@code null}
     */
    static BeanPropertyDefinition anyGetterProperty(BeanDescription description) {
        AnnotatedMember anyGetter = description.findAnyGetter();
        if (anyGetter == null) {
            return null;
        }
        for (BeanPropertyDefinition definition : description.findProperties()) {
            if (accessedBy(definition.getField(), anyGetter) || accessedBy(definition.getGetter(), anyGetter)) {
                return definition;
            }
        }
        return null;
    }

    private static boolean accessedBy(AnnotatedMember accessor, AnnotatedMember anyGetter) {
        return accessor != null && Objects.equals(accessor.getMember(), anyGetter.getMember());
    }

    @Override
    public BeanPropertyWriter rename(NameTransformer transformer) {
        BeanPropertyWriter renamed = delegate.rename(transformer);
        return renamed == delegate ? this : new GatedPropertyWriter(renamed, gate);
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
    public void assignTypeSerializer(TypeSerializer typeSerializer) {
        delegate.assignTypeSerializer(typeSerializer);
    }

    @Override
    public ValueSerializer<Object> getSerializer() {
        return delegate.getSerializer();
    }

    @Override
    public boolean hasSerializer() {
        return delegate.hasSerializer();
    }

    @Override
    public boolean hasNullSerializer() {
        return delegate.hasNullSerializer();
    }

    @Override
    public boolean isUnwrapping() {
        return delegate.isUnwrapping();
    }

    @Override
    public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        if (gate.includes(bean, context, this)) {
            delegate.serializeAsProperty(bean, generator, context);
            if (delegate.isUnwrapping()) {
                restoreCurrentValue(bean, generator);
            }
        }
    }

    /**
     * Jackson makes the value of an unwrapped property the current value of the object it writes it into, and leaves it
     * there; the bean is made the current value again, so that the properties written after it are written as those
     * of the bean, such as a reference with the bean's rendering.
     */
    private static void restoreCurrentValue(Object bean, JsonGenerator generator) {
        generator.assignCurrentValue(bean);
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        if (gate.includes(bean, context, this)) {
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

    /**
     * A gated writer of an unwrapped property, which Jackson takes for one, so that it checks the names the property
     * writes against the bean's other properties.
     */
    private static final class Unwrapping extends UnwrappingBeanPropertyWriter {

        private final UnwrappingBeanPropertyWriter delegate;

        private final Gate gate;

        private Unwrapping(UnwrappingBeanPropertyWriter delegate, Gate gate) {
            // the delegate unwraps the value, as it names the properties it writes
            super(delegate, NameTransformer.NOP);
            this.delegate = delegate;
            this.gate = gate;
        }

        @Override
        public UnwrappingBeanPropertyWriter rename(NameTransformer transformer) {
            UnwrappingBeanPropertyWriter renamed = delegate.rename(transformer);
            return renamed == delegate ? this : new Unwrapping(renamed, gate);
        }

        @Override
        public ValueSerializer<Object> findUnwrappingSerializer(SerializationContext context) {
            return delegate.findUnwrappingSerializer(context);
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
        public void assignTypeSerializer(TypeSerializer typeSerializer) {
            delegate.assignTypeSerializer(typeSerializer);
        }

        @Override
        public ValueSerializer<Object> getSerializer() {
            return delegate.getSerializer();
        }

        @Override
        public boolean hasSerializer() {
            return delegate.hasSerializer();
        }

        @Override
        public boolean hasNullSerializer() {
            return delegate.hasNullSerializer();
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context)
                throws Exception {
            if (gate.includes(bean, context, this)) {
                delegate.serializeAsProperty(bean, generator, context);
                restoreCurrentValue(bean, generator);
            }
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context)
                throws Exception {
            if (gate.includes(bean, context, this)) {
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
    }

    /**
     * A gated writer of the entries of a {@code @JsonAnyGetter}, which Jackson takes for its own: it resolves the
     * serializer of the entries, and a {@code @JsonFilter} filters the entries one by one, as it does those of any bean.
     */
    private static final class AnyGetter extends AnyGetterWriter {

        private final AnyGetterWriter delegate;

        private final Gate gate;

        private AnyGetter(AnyGetterWriter delegate, Gate gate) {
            // the delegate has the accessor and the serializer of the entries
            super(delegate, null, null, null);
            this.delegate = delegate;
            this.gate = gate;
        }

        @Override
        public void fixAccess(SerializationConfig config) {
            delegate.fixAccess(config);
        }

        @Override
        public void resolve(SerializationContext context) {
            delegate.resolve(context);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
            getAndSerialize(bean, generator, context);
        }

        @Override
        public void getAndSerialize(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
            if (gate.includes(bean, context, this)) {
                delegate.getAndSerialize(bean, generator, context);
            }
        }

        @Override
        public void getAndFilter(Object bean, JsonGenerator generator, SerializationContext context, PropertyFilter filter)
                throws Exception {
            if (gate.includes(bean, context, this)) {
                delegate.getAndFilter(bean, generator, context, filter);
            }
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
            if (gate.includes(bean, context, this)) {
                delegate.serializeAsElement(bean, generator, context);
            }
            else {
                delegate.serializeAsOmittedElement(bean, generator, context);
            }
        }
    }
}

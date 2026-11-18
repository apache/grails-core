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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.VirtualAnnotatedMember;
import tools.jackson.databind.ser.AnyGetterWriter;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.BeanSerializerBuilder;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.impl.FilteredBeanPropertyWriter;
import tools.jackson.databind.util.SimpleBeanPropertyDefinition;

import org.springframework.util.ClassUtils;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ManyToOne;
import org.grails.datastore.mapping.model.types.OneToOne;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.grails.datastore.mapping.reflect.ReflectionUtils;
import org.grails.web.converters.Converter.CircularReferenceBehaviour;

/**
 * Makes the bean serializer of a domain class write what {@code grails.converters.JSON} writes for an instance: the
 * class name, the id and the version when they apply, then the persistent properties in the order the domain class
 * declares them, and an association as a reference of its id, in full when rendering deep, or with the serializer of
 * the associated class, for a class with one of its own.
 *
 * <p>The serializer is otherwise Jackson's, so what Jackson applies to a bean applies to the instance: the naming
 * strategy, {@code @JsonIgnore}, {@code @JsonInclude}, {@code @JsonView}, {@code @JsonFormat}, mixins, filters, type
 * ids and unwrapping. A property that is not persistent is not written; the entries of a persistent one annotated
 * {@code @JsonAnyGetter} are written in its place.</p>
 *
 * @since 9.0
 */
final class DomainClassModifier extends ValueSerializerModifier {

    private final DomainRendering rendering;

    private final DomainReferences references = new DomainReferences();

    private final CycleTracking.OnCycle onCycle = this::onCycle;

    private final Predicate<SerializationContext> paths = context -> writesPaths();

    DomainClassModifier(DomainRendering rendering) {
        this.rendering = rendering;
    }

    /**
     * @return the entity of a domain class that is written as a bean, or {@code null} for any other type
     * @throws org.grails.core.exceptions.GrailsConfigurationException if GORM has not yet made its metadata readable,
     * so that the serializer is built when the first instance is written, once it has
     */
    private PersistentEntity entity(Class<?> type) {
        DomainClassRendering current = rendering.get();
        return current.isDomainClass(type) && !DomainClassRendering.isProxyClass(type) ? current.findEntityOfType(type) : null;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier beanDescription,
            List<BeanPropertyWriter> properties) {
        PersistentEntity entity = entity(beanDescription.get().getBeanClass());
        if (entity == null) {
            return properties;
        }
        Map<String, String> internalNames = new HashMap<>();
        for (BeanPropertyDefinition definition : beanDescription.get().findProperties()) {
            internalNames.put(definition.getName(), definition.getInternalName());
        }
        // Jackson leaves out the properties a class names in @JsonIgnoreProperties by the names it writes them with;
        // they are left out here by the names the class gives them, so that a naming strategy writes none of them, such
        // as the tenant id GORM names for every domain class
        Set<String> ignored = ignoredByClass(beanDescription.get());
        List<PersistentProperty> persistent = new ArrayList<>();
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (!property.equals(entity.getVersion()) && !property.equals(entity.getIdentity()) &&
                    !ignored.contains(property.getName())) {
                persistent.add(property);
            }
        }
        List<BeanPropertyWriter> written = new ArrayList<>();
        // Jackson checks the names an unwrapped property writes against the other properties where these are the ones that
        // may be written, which a rendering for each write may change
        boolean checked = !rendering.renderingsPerWrite();
        PersistentProperty identity = entity.getIdentity();
        if (identity != null && !ignored.contains(identity.getName())) {
            // The writer Jackson, or another module, gives the id writes it, so that what they apply to a property applies
            // to it; it is moved ahead of the other properties once Jackson has left out the properties the class ignores
            for (BeanPropertyWriter writer : properties) {
                if (identity.getName().equals(internalNames.getOrDefault(writer.getName(), writer.getName()))) {
                    written.add(GatedPropertyWriter.gated(new DomainIdWriter(writer, identity, rendering),
                            included(identity.getName()), checked));
                }
            }
        }
        for (PersistentProperty property : persistent) {
            for (BeanPropertyWriter writer : properties) {
                if (property.getName().equals(internalNames.getOrDefault(writer.getName(), writer.getName()))) {
                    written.add(GatedPropertyWriter.gated(withReferences(writer, property), included(property.getName()),
                            checked));
                }
            }
        }
        if (beanDescription.get().getClassAnnotations().get(JsonPropertyOrder.class) != null) {
            // Jackson's order, as the class asks for it, for the properties it keeps
            List<BeanPropertyWriter> ordered = new ArrayList<>();
            for (BeanPropertyWriter writer : properties) {
                for (BeanPropertyWriter kept : written) {
                    if (kept.getName().equals(writer.getName())) {
                        ordered.add(kept);
                    }
                }
            }
            return ordered;
        }
        return written;
    }

    /** The rendering of the write, and its projection, for a property of the instance being written. */
    private GatedPropertyWriter.Gate included(String property) {
        return (bean, context, writer) -> rendering.of(context, bean).includes(bean, property, writer.getName()) &&
                JsonProjection.projects(context, bean, property, writer.getName());
    }

    private BeanPropertyWriter withReferences(BeanPropertyWriter writer, PersistentProperty property) {
        if (property instanceof Association<?> association && !association.isEmbedded() &&
                association.getAssociatedEntity() != null && !association.getType().isEnum() &&
                writer.getSerializer() == null && !writer.isUnwrapping() &&
                writer.getAnnotation(JsonSerialize.class) == null) {
            // Jackson contextualizes only the serializers it assigns itself, so this one is given the property here
            writer.assignSerializer(references.serializer(association.getAssociatedEntity(),
                    !(association instanceof OneToOne || association instanceof ManyToOne), rendering, writer));
        }
        return writer;
    }

    @Override
    public List<BeanPropertyWriter> orderProperties(SerializationConfig config, BeanDescription.Supplier beanDescription,
            List<BeanPropertyWriter> properties) {
        BeanDescription description = beanDescription.get();
        PersistentEntity entity = entity(description.getBeanClass());
        if (entity == null) {
            return properties;
        }
        // The class name, the id and the version are written ahead of the other properties. The id is written by Jackson's
        // writer, unless Jackson has none for it; GORM leaves out the version, so a writer of its own writes it. The class
        // name and the version are properties only where a rendering may write them.
        DomainClassRendering defaults = rendering.get();
        boolean perWrite = rendering.renderingsPerWrite();
        List<BeanPropertyWriter> ordered = new ArrayList<>();
        if (perWrite || defaults.isIncludeClass()) {
            ordered.add(leading(config, description, "class", DomainLeadingWriter.Kind.CLASS, entity));
        }
        List<BeanPropertyWriter> others = new ArrayList<>(properties);
        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            BeanPropertyWriter id = removeWriter(others, description, identity.getName());
            if (id != null) {
                ordered.add(id);
            }
            else if (!known(description, identity.getName()) && !ignored(description.getBeanClass(), identity.getName())) {
                ordered.add(leading(config, description, identity.getName(), DomainLeadingWriter.Kind.ID, entity));
            }
        }
        if (entity.getVersion() != null && !versionIgnored(config, description) && (perWrite || defaults.isIncludeVersion())) {
            ordered.add(leading(config, description, GormProperties.VERSION, DomainLeadingWriter.Kind.VERSION, entity));
        }
        ordered.addAll(others);
        return ordered;
    }

    private static BeanPropertyWriter removeWriter(List<BeanPropertyWriter> writers, BeanDescription description,
            String property) {
        Map<String, String> internalNames = new HashMap<>();
        for (BeanPropertyDefinition definition : description.findProperties()) {
            internalNames.put(definition.getName(), definition.getInternalName());
        }
        for (Iterator<BeanPropertyWriter> iterator = writers.iterator(); iterator.hasNext();) {
            BeanPropertyWriter writer = iterator.next();
            if (property.equals(internalNames.getOrDefault(writer.getName(), writer.getName()))) {
                iterator.remove();
                return writer;
            }
        }
        return null;
    }

    /**
     * Whether Jackson decided about the property: it has a property for it, which it may have left out since, or it
     * ignores it, by an annotation of the class, of the property or of a mix-in.
     */
    private static boolean known(BeanDescription description, String property) {
        for (BeanPropertyDefinition definition : description.findProperties()) {
            if (property.equals(definition.getInternalName())) {
                return true;
            }
        }
        return description.getIgnoredPropertyNames().contains(property);
    }

    /**
     * Whether the version is left out. GORM names it in the {@code @JsonIgnoreProperties} of every domain class, so that
     * Jackson leaves it out, and the rendering decides; what is ignored for the version itself, also by a mix-in, is.
     * Jackson merges the annotations of a mix-in into those of the class, so a mix-in is read for itself, for each type
     * Jackson applies mix-ins of: the class, its superclasses, including {@code Object}, and its interfaces.
     */
    private static boolean versionIgnored(SerializationConfig config, BeanDescription description) {
        if (ignored(description.getBeanClass(), GormProperties.VERSION)) {
            return true;
        }
        Set<Class<?>> types = new LinkedHashSet<>();
        for (Class<?> type = description.getBeanClass(); type != null; type = type.getSuperclass()) {
            types.add(type);
        }
        types.addAll(ClassUtils.getAllInterfacesForClassAsSet(description.getBeanClass()));
        for (Class<?> type : types) {
            Class<?> mixIn = config.findMixInClassFor(type);
            if (mixIn != null && (ignored(mixIn, GormProperties.VERSION) || namedIgnored(mixIn, GormProperties.VERSION))) {
                return true;
            }
        }
        JsonIgnoreProperties.Value byClass = description.getPropertyIgnorals();
        return description.getIgnoredPropertyNames().contains(GormProperties.VERSION) &&
                (byClass == null || !byClass.findIgnoredForSerialization().contains(GormProperties.VERSION));
    }

    private BeanPropertyWriter leading(SerializationConfig config, BeanDescription description, String property,
            DomainLeadingWriter.Kind kind, PersistentEntity entity) {
        BeanPropertyDefinition definition = null;
        for (BeanPropertyDefinition candidate : description.findProperties()) {
            if (property.equals(candidate.getInternalName())) {
                definition = candidate;
            }
        }
        JavaType object = config.getTypeFactory().constructType(Object.class);
        if (definition == null || kind == DomainLeadingWriter.Kind.CLASS) {
            definition = SimpleBeanPropertyDefinition.construct(config,
                    new VirtualAnnotatedMember(description.getClassInfo(), description.getBeanClass(), property, object),
                    PropertyName.construct(property));
        }
        // As Jackson's own writers do, a property that names no view is written in those its class names
        Class<?>[] views = definition.findViews();
        return new DomainLeadingWriter(definition, description.getClassAnnotations(), object, kind, entity, rendering,
                views != null ? views : description.findDefaultViews());
    }

    /**
     * Writes the entries of a persistent property annotated {@code @JsonAnyGetter} in the place of the property, with
     * Jackson's own writer of them, gated as the property's writer is, so that a {@code @JsonFilter} filters them one by
     * one, as for any bean. Jackson puts its writer in the place of the property's writer when the names match, and
     * otherwise adds it after the others; it is left out for a {@code @JsonAnyGetter} that is not a persistent property,
     * since only persistent properties are written.
     */
    @Override
    public BeanSerializerBuilder updateBuilder(SerializationConfig config, BeanDescription.Supplier beanDescription,
            BeanSerializerBuilder builder) {
        PersistentEntity entity = entity(beanDescription.getBeanClass());
        if (entity == null) {
            return builder;
        }
        List<BeanPropertyWriter> properties = builder.getProperties();
        int anyGetterIndex = -1;
        for (int i = 0; i < properties.size() && anyGetterIndex < 0; i++) {
            if (properties.get(i) instanceof AnyGetterWriter) {
                anyGetterIndex = i;
            }
        }
        if (anyGetterIndex < 0) {
            return builder;
        }
        AnyGetterWriter anyGetter = (AnyGetterWriter) properties.get(anyGetterIndex);
        BeanPropertyWriter[] filtered = builder.getFilteredProperties();
        BeanPropertyWriter anyGetterFilter = filtered != null ? filtered[anyGetterIndex] : null;
        List<BeanPropertyWriter> kept = new ArrayList<>(properties);
        List<BeanPropertyWriter> keptFiltered = filtered != null ? new ArrayList<>(Arrays.asList(filtered)) : null;
        String property = persistentAnyGetter(beanDescription.get(), entity);
        int propertyIndex = property != null ? writerIndex(properties, beanDescription.get(), property) : -1;
        kept.remove(anyGetterIndex);
        if (keptFiltered != null) {
            keptFiltered.remove(anyGetterIndex);
        }
        if (property != null) {
            BeanPropertyWriter gated = GatedPropertyWriter.gated(anyGetter, included(property), !rendering.renderingsPerWrite());
            // the writer of the views Jackson writes the entries in
            BeanPropertyWriter gatedFilter = anyGetterFilter == null ? null : anyGetterFilter == anyGetter ? gated :
                    FilteredBeanPropertyWriter.constructViewBased(gated, anyGetter.getViews());
            if (propertyIndex < 0) {
                kept.add(anyGetterIndex, gated);
                if (keptFiltered != null) {
                    keptFiltered.add(anyGetterIndex, gatedFilter);
                }
            }
            else {
                int place = propertyIndex > anyGetterIndex ? propertyIndex - 1 : propertyIndex;
                kept.set(place, gated);
                if (keptFiltered != null) {
                    keptFiltered.set(place, gatedFilter);
                }
            }
        }
        builder.setProperties(kept);
        if (keptFiltered != null) {
            builder.setFilteredProperties(keptFiltered.toArray(new BeanPropertyWriter[0]));
        }
        return builder;
    }

    /**
     * @return the persistent property whose field or getter is the class's {@code @JsonAnyGetter}, unless the class
     *         ignores it, or {@code null}
     */
    private static String persistentAnyGetter(BeanDescription description, PersistentEntity entity) {
        BeanPropertyDefinition definition = GatedPropertyWriter.anyGetterProperty(description);
        if (definition == null) {
            return null;
        }
        Set<String> ignored = ignoredByClass(description);
        PersistentProperty property = entity.getPropertyByName(definition.getInternalName());
        return property != null && !ignored.contains(property.getName()) && !ignored.contains(definition.getName()) ?
                property.getName() : null;
    }

    /** The place of the writer of a property, other than the writer of the entries of a {@code @JsonAnyGetter}. */
    private static int writerIndex(List<BeanPropertyWriter> writers, BeanDescription description, String property) {
        Map<String, String> internalNames = new HashMap<>();
        for (BeanPropertyDefinition definition : description.findProperties()) {
            internalNames.put(definition.getName(), definition.getInternalName());
        }
        for (int i = 0; i < writers.size(); i++) {
            BeanPropertyWriter writer = writers.get(i);
            if (!(writer instanceof AnyGetterWriter) &&
                    property.equals(internalNames.getOrDefault(writer.getName(), writer.getName()))) {
                return i;
            }
        }
        return -1;
    }

    /** The properties a class names in {@code @JsonIgnoreProperties}, which are left out by the names it gives them. */
    private static Set<String> ignoredByClass(BeanDescription description) {
        JsonIgnoreProperties.Value ignorals = description.getPropertyIgnorals();
        return ignorals != null ? ignorals.findIgnoredForSerialization() : Set.of();
    }

    @Override
    public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDescription,
            ValueSerializer<?> serializer) {
        DomainClassRendering current = rendering.get();
        Class<?> type = beanDescription.get().getBeanClass();
        if (!current.isDomainClass(type) || DomainClassRendering.isProxyClass(type)) {
            return serializer;
        }
        ValueSerializer<?> tracked = CycleTracking.around(serializer, onCycle, paths);
        return tracked != null ? tracked : serializer;
    }

    /** Whether an instance reached again may be written as its path: by the behaviour of a write, or of the module. */
    private boolean writesPaths() {
        return rendering.renderingsPerWrite() ||
                rendering.get().getCircularReferenceBehaviour() == CircularReferenceBehaviour.PATH;
    }

    /** An instance reached again while it is being written is a reference to it, or what the behaviour selects. */
    private void onCycle(Object value, Cycles.Frame enclosing, tools.jackson.core.JsonGenerator generator,
            tools.jackson.databind.SerializationContext context) {
        DomainClassRendering current = rendering.of(context, value);
        if (current.getCircularReferenceBehaviour() != null) {
            Cycles.write(value, enclosing, generator, current.getCircularReferenceBehaviour());
        }
        else {
            references.write(value, current.findEntity(value), generator, context, current);
        }
    }

    /**
     * Whether the class leaves a property out with {@code @JsonIgnore}, or makes it write-only, on its field or getter.
     */
    private static boolean ignored(Class<?> type, String property) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(property) && ignoredForSerialization(field)) {
                    return true;
                }
            }
            for (Method method : current.getDeclaredMethods()) {
                if (ReflectionUtils.isGetter(method.getName(), method.getParameterTypes()) &&
                        property.equals(NameUtils.getPropertyNameForGetterOrSetter(method.getName())) &&
                        ignoredForSerialization(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the {@code @JsonIgnoreProperties} of a class leaves a property out when it is written. */
    private static boolean namedIgnored(Class<?> type, String property) {
        JsonIgnoreProperties ignorals = type.getAnnotation(JsonIgnoreProperties.class);
        return ignorals != null && !ignorals.allowGetters() && Arrays.asList(ignorals.value()).contains(property);
    }

    private static boolean ignoredForSerialization(AnnotatedElement element) {
        JsonIgnore ignore = element.getAnnotation(JsonIgnore.class);
        if (ignore != null && ignore.value()) {
            return true;
        }
        JsonProperty property = element.getAnnotation(JsonProperty.class);
        return property != null && property.access() == JsonProperty.Access.WRITE_ONLY;
    }
}

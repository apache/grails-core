/* Copyright (C) 2011-2025 the original author or authors.
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
package org.grails.datastore.mapping.model.types;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.engine.internal.MappingUtils;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;

import java.beans.PropertyDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Models a basic collection type such as a list of Strings
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public abstract class Basic<T extends Property> extends ToMany<T> {

    private CustomTypeMarshaller customTypeMarshaller;
    private Class componentType;

    public Basic(PersistentEntity owner, MappingContext context,
            PropertyDescriptor descriptor) {
        super(owner, context, descriptor);
        initializeComponentType();
    }

    public Basic(PersistentEntity owner, MappingContext context, String name, Class type) {
        super(owner, context, name, type);
        initializeComponentType();
    }

    private void initializeComponentType() {
        final Class type = getType();
        final Class ownerClass = getOwner().getJavaClass();
        if(Map.class.isAssignableFrom(type)) {
            this.componentType = MappingUtils.getGenericTypeForMapProperty(ownerClass, getName(), false );
        }
        else if(Collection.class.isAssignableFrom(type)) {
            this.componentType = MappingUtils.getGenericTypeForProperty(ownerClass, getName() );
        }
        else if(type.isArray()) {
            this.componentType = type.getComponentType();
        }

        if(componentType == null) {
            List<Map> maps = ClassPropertyFetcher.getStaticPropertyValuesFromInheritanceHierarchy(ownerClass, GormProperties.HAS_MANY, Map.class);

            for (Map map : maps) {
                if(map.containsKey(getName())) {
                    final Object o = map.get(getName());
                    if(o instanceof Class) {
                        this.componentType = (Class) o;
                        break;
                    }
                }
            }
            if(componentType == null) {
                this.componentType = Object.class;
            }
        }

    }

    public Class getComponentType() {
        return componentType;
    }

    public boolean isEnum() {
       return Optional.ofNullable(componentType).map(Class::isEnum).orElse(false);
    }

    @Override
    public Association getInverseSide() {
        return null; // basic collection types have no inverse side
    }

    @Override
    public boolean isOwningSide() {
        return true;
    }

    @Override
    public void setOwningSide(boolean owningSide) {
        // noop
    }

    @Override
    public PersistentEntity getAssociatedEntity() {
        return null; // basic collection types have no associated entity
    }

    /**
     * @return The type converter for this custom type
     */
    @SuppressWarnings("rawtypes")
    public CustomTypeMarshaller getCustomTypeMarshaller() {
        return customTypeMarshaller;
    }

    public void setCustomTypeMarshaller(CustomTypeMarshaller customTypeMarshaller) {
        this.customTypeMarshaller = customTypeMarshaller;
    }
}

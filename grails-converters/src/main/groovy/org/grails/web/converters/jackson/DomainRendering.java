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

import java.util.function.Function;
import java.util.function.Supplier;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

/**
 * Finds the {@link DomainClassRendering} of a write: the one the writer supplied for the instance, such as the JSON
 * converter does for each domain class instance it renders, or else the one of the mapper's
 * {@link DomainClassJacksonModule}.
 *
 * <p>The module's rendering is kept once it knows the application's domain classes, which it may not when the mapper
 * is built ahead of the application.</p>
 *
 * @since 9.0
 */
final class DomainRendering implements Supplier<DomainClassRendering> {

    private final Supplier<DomainClassRendering> source;

    private final boolean renderingsPerWrite;

    private volatile DomainClassRendering kept;

    DomainRendering(Supplier<DomainClassRendering> source, boolean renderingsPerWrite) {
        this.source = source;
        this.renderingsPerWrite = renderingsPerWrite;
    }

    /**
     * @return whether a write may supply the rendering of each instance it writes, which may then write what this one
     *         does not
     */
    boolean renderingsPerWrite() {
        return renderingsPerWrite;
    }

    @Override
    public DomainClassRendering get() {
        DomainClassRendering current = kept;
        if (current == null) {
            current = source.get();
            if (current.hasApplication()) {
                kept = current;
            }
        }
        return current;
    }

    /**
     * @param context the context of a write
     * @param instance a domain class instance being written
     * @return the rendering to write the instance with: the one the writer supplied for it, if it did
     */
    @SuppressWarnings("unchecked")
    DomainClassRendering of(SerializationContext context, Object instance) {
        if (context.getAttribute(DomainClassJacksonModule.RENDERINGS) instanceof Function<?, ?> renderings) {
            DomainClassRendering supplied = ((Function<Object, DomainClassRendering>) renderings).apply(instance);
            if (supplied != null) {
                return supplied;
            }
        }
        return get();
    }

    /**
     * @param context the context of a write
     * @param generator the generator, which is inside the object of the instance whose property is being written
     * @return the rendering to write the property with
     */
    DomainClassRendering ofOwner(SerializationContext context, JsonGenerator generator) {
        Object owner = generator.streamWriteContext().currentValue();
        return owner != null ? of(context, owner) : get();
    }
}

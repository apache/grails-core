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
package org.grails.forge.api;

import io.micronaut.context.MessageSource;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Described;
import io.micronaut.core.naming.Named;
import io.swagger.v3.oas.annotations.media.Schema;
import org.grails.forge.options.GormImpl;
import org.grails.forge.util.NameUtils;

/**
 * DTO objects for {@link GormImpl}.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Schema(name = "GormImplInfo")
@Introspected
public class GormImplDTO extends Linkable implements Named, Described, Selectable<GormImpl> {
    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".gormImpl.";
    private final String name;
    private final String description;
    private final GormImpl value;

    /**
     * @param gormImpl The GormImpl
     */
    public GormImplDTO(GormImpl gormImpl) {
        this.value = gormImpl;
        this.name = gormImpl.getName();
        this.description = gormImpl.getName();
    }

    /**
     * @param gormImpl The type
     * @param name The name
     * @param description The description
     */
    @Creator
    @Internal
    GormImplDTO(GormImpl gormImpl,
                String name,
                String description) {
        this.value = gormImpl;
        this.name = name;
        this.description = description;
    }

    /**
     * i18n constructor.
     * @param gormImpl The type
     * @param messageSource The message source
     * @param messageContext The message context
     */
    @Internal
    GormImplDTO(GormImpl gormImpl,
                MessageSource messageSource,
                MessageSource.MessageContext messageContext) {
        this.value = gormImpl;
        String name = gormImpl.getName();
        this.name = name;
        this.description = messageSource.getMessage(MESSAGE_PREFIX + name + ".description", messageContext, name);

    }

    @Override
    @Schema(description = "A description of the GORM Implementation")
    public String getDescription() {
        return description;
    }

    @Override
    @Schema(description = "The name of the Gorm Implementation")
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @Schema(description = "The value of the GORM Implementation for select options")
    public GormImpl getValue() {
        return value;
    }

    @Override
    @Schema(description = "The label of the GORM Implementation for select options")
    public String getLabel() {
        return NameUtils.getNaturalNameOfEnum(name);
    }
}

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

import org.grails.forge.options.DevelopmentReloading;
import org.grails.forge.util.NameUtils;

/**
 * DTO objects for {@link DevelopmentReloading}.
 */
@Schema(name = "DevelopmentReloadingInfo")
@Introspected
public class DevelopmentReloadingDTO extends Linkable implements Named, Described, Selectable<DevelopmentReloading> {
    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".developmentReloading.";
    private final String name;
    private final String description;
    private final DevelopmentReloading value;

    /**
     * @param developmentReloading The developmentReloading
     */
    public DevelopmentReloadingDTO(DevelopmentReloading developmentReloading) {
        this.value = developmentReloading;
        this.name = developmentReloading.toString();
        this.description = developmentReloading.name();
    }

    /**
     * @param name the name
     * @param description The description
     */
    @Creator
    @Internal
    DevelopmentReloadingDTO(DevelopmentReloading value, String name, String description) {
        this.value = value;
        this.name = name;
        this.description = description;
    }

    /**
     * i18n constructor.
     * @param developmentReloading The type
     * @param messageSource The message source
     * @param messageContext The message context
     */
    @Internal
    DevelopmentReloadingDTO(DevelopmentReloading developmentReloading, MessageSource messageSource, MessageSource.MessageContext messageContext) {
        this.value = developmentReloading;
        this.name = developmentReloading.toString();
        this.description = messageSource.getMessage(MESSAGE_PREFIX + name + ".description", messageContext, NameUtils.getNaturalNameOfEnum(name));
    }

    @NonNull
    @Override
    @Schema(description = "A description of the developmentReloading")
    public String getDescription() {
        return description;
    }

    @Override
    @Schema(description = "The name of the developmentReloading")
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @Schema(description = "The value of the developmentReloading for select options")
    @NonNull
    public DevelopmentReloading getValue() {
        return value;
    }

    @Override
    @Schema(description = "The label of the developmentReloading for select options")
    public String getLabel() {
        return value.getLabel();
    }
}

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
import org.grails.forge.options.GspLayoutImpl;

/**
 * DTO objects for {@link GspLayoutImpl}.
 *
 * @since 8.0.0
 */
@Schema(name = "GspLayoutImplInfo")
@Introspected
public class GspLayoutImplDTO extends Linkable implements Named, Described, Selectable<GspLayoutImpl> {

    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".gspLayoutImpl.";

    private final String name;
    private final String description;
    private final GspLayoutImpl value;

    /**
     * @param gspLayoutImpl The {@link GspLayoutImpl}
     */
    public GspLayoutImplDTO(GspLayoutImpl gspLayoutImpl) {
        this.value = gspLayoutImpl;
        this.name = gspLayoutImpl.getName();
        this.description = gspLayoutImpl.getName();
    }

    @Creator
    @Internal
    GspLayoutImplDTO(GspLayoutImpl gspLayoutImpl,
                     String name,
                     String description) {
        this.value = gspLayoutImpl;
        this.name = name;
        this.description = description;
    }

    @Internal
    GspLayoutImplDTO(GspLayoutImpl gspLayoutImpl,
                     MessageSource messageSource,
                     MessageSource.MessageContext messageContext) {
        this.value = gspLayoutImpl;
        String name = gspLayoutImpl.getName();
        this.name = name;
        this.description = messageSource.getMessage(MESSAGE_PREFIX + name + ".description", messageContext, name);
    }

    @NonNull
    @Override
    @Schema(description = "A description of the GSP Layout Implementation")
    public String getDescription() {
        return description;
    }

    @Override
    @Schema(description = "The name of the GSP Layout Implementation")
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @Schema(description = "The value of the GSP Layout Implementation for select options")
    public GspLayoutImpl getValue() {
        return value;
    }

    @Override
    @Schema(description = "The label of the GSP Layout Implementation for select options")
    public String getLabel() {
        return value.getLabel();
    }
}

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
import org.grails.forge.options.JdkVersion;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO objects for {@link JdkVersion}.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Schema(name = "JdkVersionInfo")
@Introspected
public class JdkVersionDTO extends Linkable implements Named, Described, Selectable<JdkVersion> {
    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".jdkVersion.";
    private final JdkVersion value;
    private final String name;
    private final String description;
    private final Integer majorVersion;

    /**
     * @param jdkVersion The jdkVersion
     */
    public JdkVersionDTO(JdkVersion jdkVersion) {
        this.value = jdkVersion;
        this.name = jdkVersion.toString();
        this.description = String.valueOf(jdkVersion.majorVersion());
        this.majorVersion = jdkVersion.majorVersion();
    }

    /**
     * @param name the name
     * @param description The description
     */
    @Creator
    @Internal
    JdkVersionDTO(String name, String description, Integer majorVersion, JdkVersion value) {
        this.value = value;
        this.name = name;
        this.description = description;
        this.majorVersion = majorVersion;
    }

    /**
     * i18n constructor.
     * @param jdkVersion The type
     * @param messageSource The message source
     * @param messageContext The message context
     */
    @Internal
    JdkVersionDTO(JdkVersion jdkVersion, MessageSource messageSource, MessageSource.MessageContext messageContext) {
        String name = jdkVersion.name();

        this.value = jdkVersion;
        this.name = name;
        this.description = messageSource.getMessage(MESSAGE_PREFIX + name + ".description", messageContext, name);
        this.majorVersion = jdkVersion.majorVersion();
    }

    @Override
    @Schema(description = "A description of the jdkVersion")
    public String getDescription() {
        return description;
    }

    @Override
    @Schema(description = "The name of the jdkVersion")
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @Schema(description = "The value of the jdkVersion for select options")
    @NonNull
    public JdkVersion getValue() {
        return value;
    }

    @Override
    @Schema(description = "The label of the jdkVersion for select options")
    public String getLabel() {
        return description.replaceFirst("JDK_", "");
    }
}

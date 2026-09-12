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

import org.springframework.context.MessageSource;
import java.util.Locale;
import jakarta.annotation.Nonnull;
import org.grails.forge.options.JdkVersion;

/**
 * DTO objects for {@link JdkVersion}.
 *
 * @author graemerocher
 * @since 6.0.0
 */
public class JdkVersionDTO extends Linkable implements Selectable<JdkVersion> {
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
    JdkVersionDTO(JdkVersion jdkVersion, MessageSource messageSource, Locale locale) {
        String name = jdkVersion.name();

        this.value = jdkVersion;
        this.name = name;
        this.description = ForgeMessages.message(messageSource, locale, MESSAGE_PREFIX + name + ".description", name);
        this.majorVersion = jdkVersion.majorVersion();
    }

    @Override
        public String getDescription() {
        return description;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Override
        @Nonnull
    public JdkVersion getValue() {
        return value;
    }

    @Override
        public String getLabel() {
        return description.replaceFirst("JDK_", "");
    }
}

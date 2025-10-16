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
package org.grails.datastore.mapping.model;

import java.lang.reflect.Field;

import org.springframework.util.ReflectionUtils;

import grails.util.Environment;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.config.Property.AutoTimestampType;

/**
 * Utility class for detecting and caching auto-timestamp annotations on domain properties.
 * This avoids repeated reflection calls by storing the annotation type in the Property metadata.
 *
 * <p>Caching is automatically disabled in development mode ({@link Environment#isDevelopmentMode()})
 * to ensure annotation changes are picked up during class reloading.</p>
 *
 * @author Scott Murphy Heiberg
 * @since 7.0
 */
public class AutoTimestampUtils {

    private static final String CREATED_DATE_ANNOTATION = "grails.gorm.annotation.CreatedDate";
    private static final String LAST_MODIFIED_DATE_ANNOTATION = "grails.gorm.annotation.LastModifiedDate";
    private static final String AUTO_TIMESTAMP_ANNOTATION = "grails.gorm.annotation.AutoTimestamp";

    /**
     * Gets the auto-timestamp type for a persistent property, using cached metadata when not in development mode.
     *
     * <p>In development mode, this method will always perform reflection to detect the current
     * annotation state, ensuring that annotation changes during class reloading are immediately
     * recognized. In production, the result is cached to avoid repeated reflection calls.</p>
     *
     * @param persistentProperty The persistent property to check
     * @return The auto-timestamp type (CREATED, UPDATED, or NONE)
     */
    public static AutoTimestampType getAutoTimestampType(PersistentProperty<?> persistentProperty) {
        Property mappedForm = persistentProperty.getMapping().getMappedForm();

        // In development mode, always detect fresh to support class reloading
        if (Environment.isDevelopmentMode()) {
            return detectAutoTimestampType(persistentProperty);
        }

        // Return cached value if available
        if (mappedForm.getAutoTimestampType() != null) {
            return mappedForm.getAutoTimestampType();
        }

        // Detect and cache the annotation type
        AutoTimestampType type = detectAutoTimestampType(persistentProperty);
        mappedForm.setAutoTimestampType(type);
        return type;
    }

    /**
     * Detects the auto-timestamp annotation type on a property using reflection.
     *
     * <p>When caching is enabled (production mode), this method is called once per property
     * and the result is cached. When caching is disabled (development mode), this method
     * is called on every access to ensure annotation changes are detected.</p>
     *
     * @param persistentProperty The persistent property to check
     * @return The auto-timestamp type (CREATED, UPDATED, or NONE)
     */
    private static AutoTimestampType detectAutoTimestampType(PersistentProperty<?> persistentProperty) {
        try {
            Field field = ReflectionUtils.findField(
                persistentProperty.getOwner().getJavaClass(),
                persistentProperty.getName()
            );

            if (field != null) {
                for (java.lang.annotation.Annotation annotation : field.getDeclaredAnnotations()) {
                    String annotationName = annotation.annotationType().getName();

                    if (CREATED_DATE_ANNOTATION.equals(annotationName)) {
                        return AutoTimestampType.CREATED;
                    } else if (LAST_MODIFIED_DATE_ANNOTATION.equals(annotationName)) {
                        return AutoTimestampType.UPDATED;
                    } else if (AUTO_TIMESTAMP_ANNOTATION.equals(annotationName)) {
                        // For @AutoTimestamp, check the EventType value
                        try {
                            Object eventTypeValue = annotation.annotationType()
                                .getMethod("value")
                                .invoke(annotation);

                            if (eventTypeValue != null) {
                                String eventTypeName = eventTypeValue.toString();
                                if (eventTypeName.equals("UPDATED")) {
                                    return AutoTimestampType.UPDATED;
                                } else {
                                    return AutoTimestampType.CREATED;
                                }
                            }
                        } catch (Exception e) {
                            // If we can't read the value, default to CREATED
                            return AutoTimestampType.CREATED;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // If reflection fails, return NONE
        }

        return AutoTimestampType.NONE;
    }

    /**
     * Checks if a property has any auto-timestamp annotation.
     *
     * @param persistentProperty The persistent property to check
     * @return true if the property has a @CreatedDate, @LastModifiedDate, or @AutoTimestamp annotation
     */
    public static boolean hasAutoTimestampAnnotation(PersistentProperty<?> persistentProperty) {
        return persistentProperty != null && getAutoTimestampType(persistentProperty) != AutoTimestampType.NONE;
    }

    /**
     * Checks if a property has a @CreatedDate annotation or @AutoTimestamp(CREATED).
     *
     * @param persistentProperty The persistent property to check
     * @return true if the property represents a creation timestamp
     */
    public static boolean isCreatedTimestamp(PersistentProperty<?> persistentProperty) {
        return getAutoTimestampType(persistentProperty) == AutoTimestampType.CREATED;
    }

    /**
     * Checks if a property has a @LastModifiedDate annotation or @AutoTimestamp(UPDATED).
     *
     * @param persistentProperty The persistent property to check
     * @return true if the property represents an update timestamp
     */
    public static boolean isUpdatedTimestamp(PersistentProperty<?> persistentProperty) {
        return getAutoTimestampType(persistentProperty) == AutoTimestampType.UPDATED;
    }
}

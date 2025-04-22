/*
 * Copyright 2014 the original author or authors.
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
package grails.validation;

import groovy.lang.Range;

import java.util.List;

/**
 * A interface for something that is constrained by various criteria
 *
 * @author Graeme Rocher
 * @since 2.4
 */
public interface Constrained {

    /**
     * @param constraintName The name of the constraint to check
     * @return Returns true if the specified constraint name is being applied to this property
     */
    boolean hasAppliedConstraint(String constraintName);

    /**
     * @return Returns the propertyType.
     */
    Class<?> getPropertyType();

    /**
     * @return Returns the maximum possible value.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Comparable getMax();

    /**
     * @return Returns the minimum possible value.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Comparable getMin();

    /**
     * @return Constrains the be within the list of given values
     */
    @SuppressWarnings("rawtypes")
    List getInList();

    /**
     * @return Constrains the be within the range of given values
     */
    @SuppressWarnings("rawtypes")
    Range getRange();

    Integer getScale();

    @SuppressWarnings("rawtypes")
    Range getSize();

    boolean isBlank();

    boolean isEmail();

    boolean isCreditCard();

    String getMatches();

    Object getNotEqual();

    Integer getMaxSize();

    Integer getMinSize();

    boolean isNullable();

    boolean isUrl();

    boolean isDisplay();

    boolean isEditable();

    int getOrder();

    String getFormat();

    boolean isPassword();

    boolean supportsContraint(String constraintName);

    void applyConstraint(String constraintName, Object constrainingValue);

    Class getOwner();
}

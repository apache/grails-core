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

package grails.gorm.validation

/**
 * Represents a constrained object
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface Constrained {

    /**
     * @return Returns the maximum possible value.
     */
    Comparable getMax()
    /**
     * @return Returns the minimum possible value.
     */
    Comparable getMin()
    /**
     * @return Constrains the be within the list of given values
     */
    List getInList()
    /**
     * @return Constrains the be within the range of given values
     */
    Range getRange()
    /**
     * @return The scale for decimal values
     */
    Integer getScale()
    /**
     * @return A range which represents the size constraints from minimum to maximum value
     */
    Range getSize()
    /**
     * @return Whether blank values are allowed
     */
    boolean isBlank()
    /**
     * @return Whether this is an email
     */
    boolean isEmail()
    /**
     * @return Whether this is a credit card string
     */
    boolean isCreditCard()
    /**
     * @return The string this constrained matches
     */
    String getMatches()
    /**
     * @return The value this constrained should not be equal to
     */
    Object getNotEqual()
    /**
     * @return The maximum size
     */
    Integer getMaxSize()
    /**
     * @return The minimum size
     */
    Integer getMinSize()
    /**
     * @return Whether the value is nullable
     */
    boolean isNullable()
    /**
     * @return Whether the value is a URL
     */
    boolean isUrl()
    /**
     * @return Whether the value should be displayed
     */
    boolean isDisplay()
    /**
     * @return Whether the value is editable
     */
    boolean isEditable()
    /**
     * @return The order of the value
     */
    int getOrder()
    /**
     * @return The format of the value
     */
    String getFormat()

    /**
     * @return The widget of the property
     */
    String getWidget()
    /**
     * @return Whether the value is a password or not
     */
    boolean isPassword()

    /**
     * Whether the given constraint has been applied
     *
     * @param constraint The name of the constraint
     * @return True it has
     */
    boolean hasAppliedConstraint(String constraint)

    /**
     * Applies the given constraint
     *
     * @param constraintName The name of the constraint
     * @param constrainingValue The constraining value
     */
    void applyConstraint(String constraintName, Object constrainingValue)

    /**
     * @return The owning class
     */
    Class getOwner()
}

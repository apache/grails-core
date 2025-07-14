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

package grails.validation

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.Constraint
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.validation.Errors

/**
 * Bridge from the old API to the new
 *
 * @author Graeme Rocher
 * @since 6.1
 *
 */
@CompileStatic
@Slf4j
class ConstrainedDelegate implements Constrained, ConstrainedProperty {

    final ConstrainedProperty property

    ConstrainedDelegate(ConstrainedProperty property) {
        this.property = property
    }

    /**
     * @return Returns the appliedConstraints.
     */
    Collection<Constraint> getAppliedConstraints() {
        return property.appliedConstraints
    }

    @Override
    String getPropertyName() {
        return property.getPropertyName()
    }

    @Override
    grails.gorm.validation.Constraint getAppliedConstraint(String name) {
        return property.getAppliedConstraint(name)
    }

    @Override
    void validate(Object target, Object propertyValue, Errors errors) {
        property.validate(target, propertyValue, errors)
    }

    @Override
    String getWidget() {
        return property.getWidget()
    }

    @Override
    boolean hasAppliedConstraint(String constraintName) {
        return property.hasAppliedConstraint(constraintName)
    }

    @Override
    Class<?> getPropertyType() {
        return property.getPropertyType()
    }

    @Override
    Comparable getMax() {
        return property.getMax()
    }

    @Override
    Comparable getMin() {
        return property.getMin()
    }

    @Override
    List getInList() {
        return property.getInList()
    }

    @Override
    Range getRange() {
        return property.getRange()
    }

    @Override
    Integer getScale() {
        return property.getScale()
    }

    @Override
    Range getSize() {
        return property.getSize()
    }

    @Override
    boolean isBlank() {
        return property.isBlank()
    }

    @Override
    boolean isEmail() {
        return property.isEmail()
    }

    @Override
    boolean isCreditCard() {
        return property.isCreditCard()
    }

    @Override
    String getMatches() {
        return property.getMatches()
    }

    @Override
    Object getNotEqual() {
        return property.getNotEqual()
    }

    @Override
    Integer getMaxSize() {
        return property.getMaxSize()
    }

    @Override
    Integer getMinSize() {
        return property.getMinSize()
    }

    @Override
    boolean isNullable() {
        return property.isNullable()
    }

    @Override
    boolean isUrl() {
        return property.isUrl()
    }

    @Override
    boolean isDisplay() {
        return property.isDisplay()
    }

    @Override
    boolean isEditable() {
        return property.isEditable()
    }

    @Override
    int getOrder() {
        return property.getOrder()
    }

    @Override
    String getFormat() {
        return property.getFormat()
    }

    @Override
    boolean isPassword() {
        return property.isPassword()
    }

    @Override
    boolean supportsContraint(String constraintName) {
        return property.supportsContraint(constraintName)
    }

    @Override
    void applyConstraint(String constraintName, Object constrainingValue) {
        property.applyConstraint(constraintName, constrainingValue)
    }

    @Override
    Class getOwner() {
        return property.getOwner()
    }

}

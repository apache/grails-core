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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import org.hibernate.MappingException

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

/**
 * A bidirectional many-to-many is written by its owning side, which {@code belongsTo} designates. When neither side
 * declares it, both collections are bound inverse and the relationship is never stored, which loses data silently. The
 * application refuses to start with such a mapping instead.
 *
 * <p>The rule covers a self-referencing many-to-many too: its sides are owning only when the class declares itself in
 * {@code belongsTo}, and without that both are inverse as well. A unidirectional many-to-many, and one where either side
 * owns the relationship, are accepted.</p>
 *
 * @since 9.0
 */
@CompileStatic
class ManyToManyOwnerValidator {

    /**
     * @param property a property of an entity about to be bound
     * @throws MappingException when the property is one side of a bidirectional many-to-many that neither side owns
     */
    void validate(HibernatePersistentProperty property) {
        if (!(property instanceof HibernateManyToManyProperty)) {
            return
        }
        HibernateManyToManyProperty manyToMany = (HibernateManyToManyProperty) property
        if (!manyToMany.isBidirectional() || manyToMany.isOwningSide()) {
            return
        }
        HibernateAssociation otherSide = manyToMany.hibernateInverseSide
        if (!(otherSide instanceof HibernateManyToManyProperty) || otherSide.isOwningSide()) {
            return
        }
        // the same message whichever side is bound first: the sides are named in alphabetical order
        HibernateAssociation first = manyToMany
        HibernateAssociation second = otherSide
        if (describe(first) > describe(second)) {
            first = otherSide
            second = manyToMany
        }
        throw new MappingException("Neither side of the many-to-many between [${describe(first)}] and [${describe(second)}] " +
                'declares belongsTo, so the relationship would never be stored. Declare belongsTo on the owned side, ' +
                "for example in ${second.owner.javaClass.simpleName}: static belongsTo = ${first.owner.javaClass.simpleName}")
    }

    private static String describe(HibernateAssociation side) {
        return "${side.owner.name}.${side.name}"
    }
}

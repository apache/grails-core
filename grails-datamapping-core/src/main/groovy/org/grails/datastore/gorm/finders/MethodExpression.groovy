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
package org.grails.datastore.gorm.finders

import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.util.Assert

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Query.Criterion
import org.grails.datastore.mapping.query.Restrictions

/**
 *  Method expression used to evaluate a dynamic finder.
 */
abstract class MethodExpression {

    protected String propertyName
    protected Object[] arguments
    protected int argumentsRequired = 1
    /**
     * @deprecated  Do not use
     */
    @Deprecated
    protected Class<?> targetClass

    abstract Query.Criterion createCriterion()

    protected MethodExpression(Class<?> targetClass, String propertyName) {
        this.propertyName = propertyName
    }

    protected MethodExpression(String propertyName) {
        this.propertyName = propertyName
    }

    int getArgumentsRequired() {
        return argumentsRequired
    }

    void convertArguments(PersistentEntity persistentEntity) {
        ConversionService conversionService = persistentEntity
                .getMappingContext().getConversionService()
        PersistentProperty<?> prop = persistentEntity
                .getPropertyByName(propertyName)
        if (prop == null) {
            if (propertyName.equals(persistentEntity.getIdentity().getName())) {
                prop = persistentEntity.getIdentity()
            }
        }
        if (prop != null && arguments != null && argumentsRequired > 0) {
            Class<?> type = prop.getType()
            for (int i = 0; i < argumentsRequired; i++) {
                Object arg = arguments[i]
                if (arg != null && !type.isAssignableFrom(arg.getClass())) {
                    // Add special handling for GStringImpl
                    if (arg instanceof CharSequence && arg.getClass() != String) {
                        arg = arg.toString()
                        arguments[i] = arg
                        if (type.isAssignableFrom(arg.getClass())) {
                            break
                        }
                    }
                    TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(type)
                    if ((typeDescriptor.isArray() || typeDescriptor.isCollection()) && (typeDescriptor.getElementTypeDescriptor() == null || typeDescriptor.getElementTypeDescriptor().getType().isAssignableFrom(arg.getClass()))) {
                        // skip converting argument to collection/array type if argument is correct instance of element type
                        break
                    }
                    if (conversionService.canConvert(arg.getClass(), type)) {
                        arguments[i] = conversionService.convert(arg, type)
                    }
                }
            }
        }
    }

    void setArguments(Object[] arguments) {
        this.arguments = arguments
    }

    Object[] getArguments() {
        return Arrays.copyOf(arguments, arguments.length)
    }

    String getPropertyName() {
        return propertyName
    }

    static class GreaterThan extends MethodExpression {
        GreaterThan(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        GreaterThan(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.gt(propertyName, arguments[0])
        }
    }

    static class GreaterThanEquals extends MethodExpression {
        GreaterThanEquals(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        GreaterThanEquals(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.gte(propertyName, arguments[0])
        }
    }

    static class LessThan extends MethodExpression {
        LessThan(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        LessThan(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.lt(propertyName, arguments[0])
        }
    }

    static class LessThanEquals extends MethodExpression {
        LessThanEquals(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        LessThanEquals(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.lte(propertyName, arguments[0])
        }
    }

    static class Like extends MethodExpression {
        Like(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        Like(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.like(propertyName, arguments[0].toString())
        }
    }

    static class Ilike extends MethodExpression {
        Ilike(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        Ilike(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.ilike(propertyName, arguments[0].toString())
        }
    }

    static class Rlike extends MethodExpression {
        Rlike(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        Rlike(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.rlike(propertyName, arguments[0].toString())
        }
    }

    static class NotInList extends MethodExpression {
        NotInList(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        NotInList(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            Query.Negation negation = new Query.Negation()
            negation.add(Restrictions.in(propertyName, (Collection<?>) arguments[0]))
            return negation
        }

        @Override
        void setArguments(Object[] arguments) {
            Assert.isTrue(arguments.length > 0,
                    "Only a collection of elements is supported in an 'in' query")

            Object arg = arguments[0]
            Assert.isTrue((arg instanceof Collection) || arg == null, "Only a collection of elements is supported in an 'in' query")

            super.setArguments(arguments)
        }

        @Override
        void convertArguments(PersistentEntity persistentEntity) {
            ConversionService conversionService = persistentEntity
                    .getMappingContext().getConversionService()
            String propertyName = this.propertyName
            PersistentProperty<?> prop = persistentEntity
                    .getPropertyByName(propertyName)
            Object[] arguments = this.arguments
            convertArgumentsForProp(persistentEntity, prop, propertyName, arguments, conversionService)
        }
    }

    static class InList extends MethodExpression {

        InList(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        InList(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.in(propertyName, (Collection<?>) arguments[0])
        }

        @Override
        void setArguments(Object[] arguments) {
            Assert.isTrue(arguments.length > 0,
                "Only a collection of elements is supported in an 'in' query")

            Object arg = arguments[0]
            Assert.isTrue((arg instanceof Collection) || arg == null, "Only a collection of elements is supported in an 'in' query")

            super.setArguments(arguments)
        }

        @Override
        void convertArguments(PersistentEntity persistentEntity) {
            ConversionService conversionService = persistentEntity
                    .getMappingContext().getConversionService()
            PersistentProperty<?> prop = persistentEntity
                    .getPropertyByName(propertyName)
            convertArgumentsForProp(persistentEntity, prop, propertyName, arguments, conversionService)
        }

    }

    static class Between extends MethodExpression {

        Between(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
            argumentsRequired = 2
        }

        Between(String propertyName) {
            super(propertyName)
            argumentsRequired = 2
        }

        @Override
        Query.Criterion createCriterion() {
            return Restrictions.between(propertyName, arguments[0], arguments[1])
        }

        @Override
        void setArguments(Object[] arguments) {
            Assert.isTrue(arguments.length > 1, "A 'between' query requires at least two arguments")
            Assert.isTrue(arguments[0] instanceof Comparable && arguments[1] instanceof Comparable,
                "A 'between' query requires that both arguments are comparable")

            super.setArguments(arguments)
        }

    }

    static class InRange extends MethodExpression {

        InRange(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
            argumentsRequired = 1
        }

        InRange(String propertyName) {
            super(propertyName)
            argumentsRequired = 1
        }

        @Override
        Query.Criterion createCriterion() {
            Range<?> range = (Range<?>) arguments[0]
            return Restrictions.between(propertyName, range.getFrom(), range.getTo())
        }

        @Override
        void convertArguments(PersistentEntity persistentEntity) {
            // setArguments already made sure arguments[0] is a Range...
        }

        @Override
        void setArguments(Object[] arguments) {
            Assert.isTrue(arguments.length == 1, "An 'inRange' query requires exactly 1 argument")
            Assert.isTrue(arguments[0] instanceof Range,
                    "An 'inRange' query requires a Range argument")

            super.setArguments(arguments)
        }

    }

    static class IsNull extends MethodExpression {

        IsNull(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
            argumentsRequired = 0
        }

        IsNull(String propertyName) {
            super(propertyName)
            argumentsRequired = 0
        }

        @Override
        Criterion createCriterion() {
            return Restrictions.isNull(propertyName)
        }

    }

    static class IsNotNull extends MethodExpression {

        IsNotNull(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
            argumentsRequired = 0
        }

        IsNotNull(String propertyName) {
            super(propertyName)
            argumentsRequired = 0
        }

        @Override
        Criterion createCriterion() {
            return Restrictions.isNotNull(propertyName)
        }

    }

    static class IsEmpty extends MethodExpression {

        IsEmpty(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
            argumentsRequired = 0
        }

        IsEmpty(String propertyName) {
            super(propertyName)
            argumentsRequired = 0
        }

        @Override
        Criterion createCriterion() {
            return Restrictions.isEmpty(propertyName)
        }

    }

    static class IsNotEmpty extends MethodExpression {

        IsNotEmpty(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
            argumentsRequired = 0
        }

        IsNotEmpty(String propertyName) {
            super(propertyName)
            argumentsRequired = 0
        }

        @Override
        Criterion createCriterion() {
            return Restrictions.isNotEmpty(propertyName)
        }

    }

    static class Equal extends MethodExpression {

        Equal(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        Equal(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            Object argument = arguments[0]
            if (argument != null) {
                return Restrictions.eq(propertyName, argument)
            } else {
                return Restrictions.isNull(propertyName)
            }
        }

    }

    static class NotEqual extends MethodExpression {

        NotEqual(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        NotEqual(String propertyName) {
            super(propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            Object argument = arguments[0]
            if (argument != null) {
                return Restrictions.ne(propertyName, arguments[0])
            } else {
                return Restrictions.isNotNull(propertyName)
            }
        }

    }

    private static void convertArgumentsForProp(PersistentEntity persistentEntity, PersistentProperty<?> prop, String propertyName, Object[] arguments, ConversionService conversionService) {
        if (prop == null) {
            if (propertyName.equals(persistentEntity.getIdentity().getName())) {
                prop = persistentEntity.getIdentity()
            }
        }
        if (prop != null) {
            Class<?> type = prop.getType()
            Collection<?> collection = (Collection<?>) arguments[0]
            List<Object> converted
            if (collection == null) {
                converted = Collections.emptyList()
            }
            else {
                converted = new ArrayList<>(collection.size())
                for (Object o : collection) {
                    if (o != null && !type.isAssignableFrom(o.getClass())) {
                        o = conversionService.convert(o, type)
                    }
                    converted.add(o)
                }
            }
            arguments[0] = converted
        }
    }
}

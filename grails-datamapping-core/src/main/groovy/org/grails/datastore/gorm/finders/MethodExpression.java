/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.finders;

import groovy.lang.Range;

import java.util.*;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.Query.Criterion;
import org.grails.datastore.mapping.query.Restrictions;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;

/**
 *  Method expression used to evaluate a dynamic finder.
 */
public abstract class MethodExpression {

    protected String propertyName;
    protected Object[] arguments;
    protected int argumentsRequired = 1;
    /**
     * @deprecated  Do not use
     */
    @Deprecated
    protected Class<?> targetClass;

    public abstract Query.Criterion createCriterion();

    protected MethodExpression(Class<?> targetClass, String propertyName) {
        this.propertyName = propertyName;
    }

    protected MethodExpression(String propertyName) {
        this.propertyName = propertyName;
    }

    public int getArgumentsRequired() {
        return argumentsRequired;
    }

    public void convertArguments(PersistentEntity persistentEntity) {
        ConversionService conversionService = persistentEntity
                .getMappingContext().getConversionService();
        PersistentProperty<?> prop = persistentEntity
                .getPropertyByName(propertyName);
        if (prop == null) {
            if (propertyName.equals(persistentEntity.getIdentity().getName())) {
                prop = persistentEntity.getIdentity();
            }
        }
        if (prop != null && arguments != null && argumentsRequired > 0) {
            Class<?> type = prop.getType();
            for (int i = 0; i < argumentsRequired; i++) {
                Object arg = arguments[i];
                if (arg != null && !type.isAssignableFrom(arg.getClass())) {
                    // Add special handling for GStringImpl
                    if(arg instanceof CharSequence && arg.getClass() != String.class) {
                        arg = arg.toString();
                        arguments[i] = arg;
                        if(type.isAssignableFrom(arg.getClass())) {
                            break;
                        }
                    }
                    TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(type);
                    if ((typeDescriptor.isArray() || typeDescriptor.isCollection()) && (typeDescriptor.getElementTypeDescriptor() == null || typeDescriptor.getElementTypeDescriptor().getType().isAssignableFrom(arg.getClass()))) {
                        // skip converting argument to collection/array type if argument is correct instance of element type
                        break;
                    }
                    if(conversionService.canConvert(arg.getClass(), type)) {
                        arguments[i] = conversionService.convert(arg, type);
                    }
                }
            }
        }
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    public Object[] getArguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    public String getPropertyName() {
        return propertyName;
    }

    public static class GreaterThan extends MethodExpression {
        public GreaterThan(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }

        public GreaterThan(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.gt(propertyName, arguments[0]);
        }
    }

    public static class GreaterThanEquals extends MethodExpression {
        public GreaterThanEquals(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }

        public GreaterThanEquals(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.gte(propertyName, arguments[0]);
        }
    }

    public static class LessThan extends MethodExpression {
        public LessThan(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }

        public LessThan(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.lt(propertyName, arguments[0]);
        }
    }

    public static class LessThanEquals extends MethodExpression {
        public LessThanEquals(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }

        public LessThanEquals(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.lte(propertyName, arguments[0]);
        }
    }

    public static class Like extends MethodExpression {
        public Like(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }

        public Like(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.like(propertyName, arguments[0].toString());
        }
    }

    public static class Ilike extends MethodExpression {
        public Ilike(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }

        public Ilike(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.ilike(propertyName, arguments[0].toString());
        }
    }

    public static class Rlike extends MethodExpression {
        public Rlike(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }

        public Rlike(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.rlike(propertyName, arguments[0].toString());
        }
    }

    public static class NotInList extends MethodExpression {
        public NotInList(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }

        public NotInList(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            Query.Negation negation = new Query.Negation();
            negation.add(Restrictions.in(propertyName, (Collection<?>) arguments[0]));
            return negation;
        }

        @Override
        public void setArguments(Object[] arguments) {
            Assert.isTrue(arguments.length > 0,
                    "Only a collection of elements is supported in an 'in' query");

            Object arg = arguments[0];
            Assert.isTrue( (arg instanceof Collection) || arg == null, "Only a collection of elements is supported in an 'in' query");

            super.setArguments(arguments);
        }

        @Override
        public void convertArguments(PersistentEntity persistentEntity) {
            ConversionService conversionService = persistentEntity
                    .getMappingContext().getConversionService();
            String propertyName = this.propertyName;
            PersistentProperty<?> prop = persistentEntity
                    .getPropertyByName(propertyName);
            Object[] arguments = this.arguments;
            convertArgumentsForProp(persistentEntity, prop, propertyName, arguments, conversionService);
        }
    }

    public static class InList extends MethodExpression {

        public InList(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }
        public InList(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.in(propertyName, (Collection<?>) arguments[0]);
        }

        @Override
        public void setArguments(Object[] arguments) {
            Assert.isTrue(arguments.length > 0,
                "Only a collection of elements is supported in an 'in' query");

            Object arg = arguments[0];
            Assert.isTrue( (arg instanceof Collection) || arg == null, "Only a collection of elements is supported in an 'in' query");

            super.setArguments(arguments);
        }

        @Override
        public void convertArguments(PersistentEntity persistentEntity) {
            ConversionService conversionService = persistentEntity
                    .getMappingContext().getConversionService();
            PersistentProperty<?> prop = persistentEntity
                    .getPropertyByName(propertyName);
            convertArgumentsForProp(persistentEntity, prop, propertyName, arguments, conversionService);
        }

    }
    public static class Between extends MethodExpression {

        public Between(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
            argumentsRequired = 2;
        }
        public Between(String propertyName) {
            super(propertyName);
            argumentsRequired = 2;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.between(propertyName, arguments[0], arguments[1]);
        }

        @Override
        public void setArguments(Object[] arguments) {
            Assert.isTrue(arguments.length > 1, "A 'between' query requires at least two arguments");
            Assert.isTrue(arguments[0] instanceof Comparable && arguments[1] instanceof Comparable,
                "A 'between' query requires that both arguments are comparable");

            super.setArguments(arguments);
        }

    }
    public static class InRange extends MethodExpression {

        public InRange(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
            argumentsRequired = 1;
        }
        public InRange(String propertyName) {
            super(propertyName);
            argumentsRequired = 1;
        }

        @Override
        public Query.Criterion createCriterion() {
            Range<?> range = (Range<?>) arguments[0];
            return Restrictions.between(propertyName, range.getFrom(), range.getTo());
        }

        @Override
        public void convertArguments(PersistentEntity persistentEntity) {
            // setArguments already made sure arguments[0] is a Range...
        }

        @Override
        public void setArguments(Object[] arguments) {
            Assert.isTrue(arguments.length == 1, "An 'inRange' query requires exactly 1 argument");
            Assert.isTrue(arguments[0] instanceof Range,
                    "An 'inRange' query requires a Range argument");

            super.setArguments(arguments);
        }

    }
    public static class IsNull extends MethodExpression {

        public IsNull(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
            argumentsRequired = 0;
        }
        public IsNull(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Criterion createCriterion() {
            return Restrictions.isNull(propertyName);
        }

    }
    public static class IsNotNull extends MethodExpression {

        public IsNotNull(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
            argumentsRequired = 0;
        }

        public IsNotNull(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Criterion createCriterion() {
            return Restrictions.isNotNull(propertyName);
        }

    }
    public static class IsEmpty extends MethodExpression {

        public IsEmpty(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
            argumentsRequired = 0;
        }
        public IsEmpty(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Criterion createCriterion() {
            return Restrictions.isEmpty(propertyName);
        }

    }
    public static class IsNotEmpty extends MethodExpression {

        public IsNotEmpty(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
            argumentsRequired = 0;
        }

        public IsNotEmpty(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Criterion createCriterion() {
            return Restrictions.isNotEmpty(propertyName);
        }


    }
    public static class Equal extends MethodExpression {

        public Equal(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }
        public Equal(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            Object argument = arguments[0];
            if (argument != null) {
                return Restrictions.eq(propertyName, argument);
            } else {
                return Restrictions.isNull(propertyName);
            }
        }

    }
    public static class NotEqual extends MethodExpression {

        public NotEqual(Class<?> targetClass, String propertyName) {
            super(targetClass, propertyName);
        }
        public NotEqual(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            Object argument = arguments[0];
            if (argument != null) {
                return Restrictions.ne(propertyName, arguments[0]);
            } else {
                return Restrictions.isNotNull(propertyName);
            }
        }

    }
    private static void convertArgumentsForProp(PersistentEntity persistentEntity, PersistentProperty<?> prop, String propertyName, Object[] arguments, ConversionService conversionService) {
        if (prop == null) {
            if (propertyName.equals(persistentEntity.getIdentity().getName())) {
                prop = persistentEntity.getIdentity();
            }
        }
        if (prop != null) {
            Class<?> type = prop.getType();
            Collection<?> collection = (Collection<?>) arguments[0];
            List<Object> converted;
            if(collection == null) {
                converted = Collections.emptyList();
            }
            else {
                converted = new ArrayList<>(collection.size());
                for (Object o : collection) {
                    if (o != null && !type.isAssignableFrom(o.getClass())) {
                        o = conversionService.convert(o, type);
                    }
                    converted.add(o);
                }
            }
            arguments[0] = converted;
        }
    }
}

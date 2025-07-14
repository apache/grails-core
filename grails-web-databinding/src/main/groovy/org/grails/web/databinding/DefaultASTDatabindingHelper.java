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
package org.grails.web.databinding;

import grails.util.CollectionUtils;
import grails.util.GrailsNameUtils;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;
import org.grails.compiler.injection.GrailsASTUtils;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class DefaultASTDatabindingHelper implements ASTDatabindingHelper {

    public static final String CONSTRAINTS_FIELD_NAME = "constraints";
    public static final String BINDABLE_CONSTRAINT_NAME = "bindable";

    public static final String DEFAULT_DATABINDING_WHITELIST = "$defaultDatabindingWhiteList";
    public static final String NO_BINDABLE_PROPERTIES = "$_NO_BINDABLE_PROPERTIES_$";

    private static final Map<ClassNode, Set<String>> CLASS_NODE_TO_WHITE_LIST_PROPERTY_NAMES = new HashMap<ClassNode, Set<String>>();

    @SuppressWarnings("serial")
    private static final List<ClassNode> SIMPLE_TYPES = new ArrayList<ClassNode>() {{
        add(new ClassNode(Boolean.class));
        add(new ClassNode(Boolean.TYPE));
        add(new ClassNode(Byte.class));
        add(new ClassNode(Byte.TYPE));
        add(new ClassNode(Character.class));
        add(new ClassNode(Character.TYPE));
        add(new ClassNode(Short.class));
        add(new ClassNode(Short.TYPE));
        add(new ClassNode(Integer.class));
        add(new ClassNode(Integer.TYPE));
        add(new ClassNode(Long.class));
        add(new ClassNode(Long.TYPE));
        add(new ClassNode(Float.class));
        add(new ClassNode(Float.TYPE));
        add(new ClassNode(Double.class));
        add(new ClassNode(Double.TYPE));
        add(new ClassNode(BigInteger.class));
        add(new ClassNode(BigDecimal.class));
        add(new ClassNode(String.class));
        add(new ClassNode(URL.class));
    }};

    private static final Set<String> DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT = CollectionUtils.newSet("id", "version", "dateCreated", "lastUpdated");

    public void injectDatabindingCode(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) {
        addDefaultDatabindingWhitelistField(source, classNode);
    }

    private void addDefaultDatabindingWhitelistField(final SourceUnit sourceUnit, final ClassNode classNode) {
        final FieldNode defaultWhitelistField = classNode.getDeclaredField(DEFAULT_DATABINDING_WHITELIST);
        if (defaultWhitelistField != null) {
            return;
        }

        final Set<String> propertyNamesToIncludeInWhiteList = getPropertyNamesToIncludeInWhiteList(sourceUnit, classNode);

        final ListExpression listExpression = new ListExpression();
        if (propertyNamesToIncludeInWhiteList.size() > 0) {
            for (String propertyName : propertyNamesToIncludeInWhiteList) {
                listExpression.addExpression(new ConstantExpression(propertyName));

                final FieldNode declaredField = getDeclaredFieldInInheritanceHierarchy(classNode, propertyName);
                boolean isSimpleType = false;
                if (declaredField != null) {
                    final ClassNode type = declaredField.getType();
                    if (type != null) {
                        isSimpleType = SIMPLE_TYPES.contains(type);
                    }
                }
                if (!isSimpleType) {
                    listExpression.addExpression(new ConstantExpression(propertyName + "_*"));
                    listExpression.addExpression(new ConstantExpression(propertyName + ".*"));
                }
            }
        } else {
            listExpression.addExpression(new ConstantExpression(NO_BINDABLE_PROPERTIES));
        }

        classNode.addField(DEFAULT_DATABINDING_WHITELIST,
                Modifier.STATIC | Modifier.PUBLIC | Modifier.FINAL, new ClassNode(List.class),
                listExpression);
    }

    private FieldNode getDeclaredFieldInInheritanceHierarchy(final ClassNode classNode, String propertyName) {
        FieldNode fieldNode = classNode.getDeclaredField(propertyName);
        if (fieldNode == null) {
            if (!classNode.getSuperClass().equals(new ClassNode(Object.class))) {
                return getDeclaredFieldInInheritanceHierarchy(classNode.getSuperClass(), propertyName);
            }
        }
        return fieldNode;
    }

    private Set<String> getPropertyNamesToIncludeInWhiteListForParentClass(final SourceUnit sourceUnit, final ClassNode parentClassNode) {
        final Set<String> propertyNames;
        if (CLASS_NODE_TO_WHITE_LIST_PROPERTY_NAMES.containsKey(parentClassNode)) {
            propertyNames = CLASS_NODE_TO_WHITE_LIST_PROPERTY_NAMES.get(parentClassNode);
        } else {
            propertyNames = getPropertyNamesToIncludeInWhiteList(sourceUnit, parentClassNode);
        }
        return propertyNames;
    }

    private Set<String> getPropertyNamesToIncludeInWhiteList(final SourceUnit sourceUnit, final ClassNode classNode) {
        final Set<String> propertyNamesToIncludeInWhiteList = new HashSet<String>();
        final Set<String> unbindablePropertyNames = new HashSet<String>();
        final Set<String> bindablePropertyNames = new HashSet<String>();
        if (!classNode.getSuperClass().equals(new ClassNode(Object.class))) {
            final Set<String> parentClassPropertyNames = getPropertyNamesToIncludeInWhiteListForParentClass(sourceUnit, classNode.getSuperClass());
            bindablePropertyNames.addAll(parentClassPropertyNames);
        }

        final FieldNode constraintsFieldNode = classNode.getDeclaredField(CONSTRAINTS_FIELD_NAME);
        if (constraintsFieldNode != null && constraintsFieldNode.hasInitialExpression()) {
            final Expression constraintsInitialExpression = constraintsFieldNode.getInitialExpression();
            if (constraintsInitialExpression instanceof ClosureExpression) {

                final Map<String, Map<String, Expression>> constraintsInfo = GrailsASTUtils.getConstraintMetadata((ClosureExpression) constraintsInitialExpression);

                for (Entry<String, Map<String, Expression>> constraintConfig : constraintsInfo.entrySet()) {
                    final String propertyName = constraintConfig.getKey();
                    final Map<String, Expression> mapEntryExpressions = constraintConfig.getValue();
                    for (Entry<String, Expression> entry : mapEntryExpressions.entrySet()) {
                        final String constraintName = entry.getKey();
                        if (BINDABLE_CONSTRAINT_NAME.equals(constraintName)) {
                            final Expression valueExpression = entry.getValue();
                            Boolean bindableValue = null;
                            if (valueExpression instanceof ConstantExpression) {
                                final Object constantValue = ((ConstantExpression) valueExpression).getValue();
                                if (constantValue instanceof Boolean) {
                                    bindableValue = (Boolean) constantValue;
                                }
                            }
                            if (bindableValue != null) {
                                if (Boolean.TRUE.equals(bindableValue)) {
                                    unbindablePropertyNames.remove(propertyName);
                                    bindablePropertyNames.add(propertyName);
                                } else {
                                    bindablePropertyNames.remove(propertyName);
                                    unbindablePropertyNames.add(propertyName);
                                }
                            } else {
                                GrailsASTUtils.warning(sourceUnit, valueExpression, "The bindable constraint for property [" +
                                        propertyName + "] in class [" + classNode.getName() +
                                        "] has a value which is not a boolean literal and will be ignored.");
                            }
                        }
                    }
                }
            }
        }

        final Set<String> fieldsInTransientsList = getPropertyNamesExpressedInTransientsList(classNode);
        final boolean isDomainClass = GrailsASTUtils.isDomainClass(classNode, sourceUnit);

        propertyNamesToIncludeInWhiteList.addAll(bindablePropertyNames);
        final List<FieldNode> fields = classNode.getFields();
        for (FieldNode fieldNode : fields) {
            final String fieldName = fieldNode.getName();
            if ((!unbindablePropertyNames.contains(fieldName)) &&
                    (bindablePropertyNames.contains(fieldName) || shouldFieldBeInWhiteList(fieldNode, fieldsInTransientsList, isDomainClass))) {
                propertyNamesToIncludeInWhiteList.add(fieldName);
            }
        }

        final Map<String, MethodNode> declaredMethodsMap = classNode.getDeclaredMethodsMap();
        for (Entry<String, MethodNode> methodEntry : declaredMethodsMap.entrySet()) {
            final MethodNode value = methodEntry.getValue();
            if (classNode.equals(value.getDeclaringClass())) {
                Parameter[] parameters = value.getParameters();
                if (parameters != null && parameters.length == 1) {
                    final String methodName = value.getName();
                    if (methodName.startsWith("set")) {
                        final Parameter parameter = parameters[0];
                        final ClassNode paramType = parameter.getType();
                        if (!paramType.equals(new ClassNode(Object.class))) {
                            final String restOfMethodName = methodName.substring(3);
                            final String propertyName = GrailsNameUtils.getPropertyName(restOfMethodName);
                            if (!unbindablePropertyNames.contains(propertyName) &&
                                    (!isDomainClass || !DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT.contains(propertyName))) {
                                propertyNamesToIncludeInWhiteList.add(propertyName);
                            }
                        }
                    }
                }
            }
        }
        CLASS_NODE_TO_WHITE_LIST_PROPERTY_NAMES.put(classNode, propertyNamesToIncludeInWhiteList);
        Map<String, ClassNode> allAssociationMap = GrailsASTUtils.getAllAssociationMap(classNode);
        for (String associationName : allAssociationMap.keySet()) {
            if (!propertyNamesToIncludeInWhiteList.contains(associationName) && !unbindablePropertyNames.contains(associationName)) {
                propertyNamesToIncludeInWhiteList.add(associationName);
            }
        }
        return propertyNamesToIncludeInWhiteList;
    }

    private boolean shouldFieldBeInWhiteList(final FieldNode fieldNode, final Set<String> fieldsInTransientsList, final boolean isDomainClass) {
        boolean shouldInclude = true;
        final int modifiers = fieldNode.getModifiers();
        final String fieldName = fieldNode.getName();
        if ((modifiers & Modifier.STATIC) != 0 ||
                (modifiers & Modifier.TRANSIENT) != 0 ||
                fieldsInTransientsList.contains(fieldName) ||
                (fieldNode.getType().equals(new ClassNode(Object.class)) && !fieldNode.getType().isUsingGenerics())) {
            shouldInclude = false;
        } else if (isDomainClass) {
            if (DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT.contains(fieldName)) {
                shouldInclude = false;
            }
        }
        return shouldInclude;
    }

    private Set<String> getPropertyNamesExpressedInTransientsList(final ClassNode classNode) {
        final Set<String> transientFields = new HashSet<String>();
        final FieldNode transientsField = classNode.getField("transients");
        if (transientsField != null && transientsField.isStatic()) {
            final Expression initialValueExpression = transientsField.getInitialValueExpression();
            if (initialValueExpression instanceof ListExpression) {
                final ListExpression le = (ListExpression) initialValueExpression;
                final List<Expression> expressions = le.getExpressions();
                for (Expression expr : expressions) {
                    if (expr instanceof ConstantExpression) {
                        final ConstantExpression ce = (ConstantExpression) expr;
                        final Object contantValue = ce.getValue();
                        if (contantValue instanceof String) {
                            transientFields.add((String) contantValue);
                        }
                    }
                }
            }
        }
        return transientFields;
    }
}

/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.compiler.injection;

import grails.artefact.Artefact;

import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import grails.compiler.ast.AstTransformer;
import grails.compiler.ast.GrailsArtefactClassInjector;
import grails.compiler.ast.GrailsDomainClassInjector;
import org.apache.groovy.ast.tools.AnnotatedNodeUtils;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.io.support.GrailsResourceUtils;

/**
 * Default implementation of domain class injector interface that adds the 'id'
 * and 'version' properties and other previously boilerplate code.
 *
 * @author Graeme Rocher
 * @since 0.2
 */
@AstTransformer
public class DefaultGrailsDomainClassInjector implements GrailsDomainClassInjector, GrailsArtefactClassInjector {

    private List<ClassNode> classesWithInjectedToString = new ArrayList<ClassNode>();

    public void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if (GrailsASTUtils.isDomainClass(classNode, source) && shouldInjectClass(classNode)) {
            if(!classNode.getAnnotations(new ClassNode(Artefact.class)).isEmpty()) return;
            performInjectionOnAnnotatedEntity(classNode);
        }
    }

    public void performInjectionOnAnnotatedEntity(ClassNode classNode) {
        injectIdProperty(classNode);
        injectVersionProperty(classNode);
        injectToStringMethod(classNode);
        injectAssociations(classNode);
    }

    public boolean shouldInject(URL url) {
        return GrailsResourceUtils.isDomainClass(url);
    }

    protected boolean shouldInjectClass(ClassNode classNode) {
        String fullName = GrailsASTUtils.getFullName(classNode);
        String mappingFile = getMappingFileName(fullName);

        if (getClass().getResource(mappingFile) != null) {
            return false;
        }

        return !isEnum(classNode);
    }

    /**
     * Returns the ORM framework's mapping file name for the specified class name.
     *
     * @param className The class name of the mapped file
     * @return The mapping file name
     */
    private String getMappingFileName(String className) {
        return className.replaceAll("\\.", "/") + ".hbm.xml";
    }

    private void injectAssociations(ClassNode classNode) {

        List<PropertyNode> propertiesToAdd = new ArrayList<PropertyNode>();
        for (PropertyNode propertyNode : classNode.getProperties()) {
            final String name = propertyNode.getName();
            final boolean isHasManyProperty = name.equals(GormProperties.HAS_MANY);
            if (isHasManyProperty) {
                Expression e = propertyNode.getInitialExpression();
                propertiesToAdd.addAll(createPropertiesForHasManyExpression(e, classNode));
            }
            final boolean isBelongsToOrHasOne = name.equals(GormProperties.BELONGS_TO) || name.equals(GormProperties.HAS_ONE);
            if (isBelongsToOrHasOne) {
                Expression initialExpression = propertyNode.getInitialExpression();
                if ((!(initialExpression instanceof MapExpression)) &&
                        (!(initialExpression instanceof ClassExpression))) {
                    if (name.equals(GormProperties.HAS_ONE)) {
                        final String message = "WARNING: The hasOne property in class [" + classNode.getName() + "] should have an initial expression of type Map or Class.";
                        System.err.println(message);
                    } else if (!(initialExpression instanceof ListExpression)) {
                        final String message = "WARNING: The belongsTo property in class [" + classNode.getName() + "] should have an initial expression of type List, Map or Class.";
                        System.err.println(message);
                    }
                }
                propertiesToAdd.addAll(createPropertiesForBelongsToOrHasOneExpression(initialExpression, classNode));
            }
        }
        injectAssociationProperties(classNode, propertiesToAdd);
    }

    private Collection<PropertyNode> createPropertiesForBelongsToOrHasOneExpression(Expression e, ClassNode classNode) {
        List<PropertyNode> properties = new ArrayList<>();
        if (e instanceof MapExpression) {
            MapExpression me = (MapExpression) e;
            for (MapEntryExpression mme : me.getMapEntryExpressions()) {
                String key = mme.getKeyExpression().getText();
                final Expression expression = mme.getValueExpression();
                ClassNode type;
                if (expression instanceof ClassExpression) {
                    type = expression.getType();
                }
                else {
                    type = ClassHelper.make(expression.getText());
                }

                properties.add(new PropertyNode(key, Modifier.PUBLIC, type, classNode, null, null, null));
            }
        }

        return properties;
    }

    private void injectAssociationProperties(ClassNode classNode, List<PropertyNode> propertiesToAdd) {
        for (PropertyNode pn : propertiesToAdd) {
            if (!GrailsASTUtils.hasProperty(classNode, pn.getName())) {
                classNode.addProperty(pn);
            }
        }
    }

    private List<PropertyNode> createPropertiesForHasManyExpression(Expression e, ClassNode classNode) {
        List<PropertyNode> properties = new ArrayList<>();
        if (e instanceof MapExpression) {
            MapExpression me = (MapExpression) e;
            for (MapEntryExpression mee : me.getMapEntryExpressions()) {
                String key = mee.getKeyExpression().getText();
                addAssociationForKey(key, properties, classNode, findPropertyType(mee.getValueExpression()));
            }
        }
        return properties;
    }

    /**
     * Finds the type of the generated property.  The type will be a {@link Set} that is parameterized
     * by the type of the expression passed in.
     * @param expression the expression used to parameterize the {@link Set}.  Only used if a {@link ClassExpression}.  Otherwise ignored.
     * @return A {@link ClassNode} of type {@link Set} that is possibly parameterized by the expression that is passed in.
     */
    private ClassNode findPropertyType(Expression expression) {
        ClassNode setNode = ClassHelper.make(Set.class).getPlainNodeReference();
        if (expression instanceof ClassExpression) {
            GenericsType[] genericsTypes = new GenericsType[1];
            genericsTypes[0] = new GenericsType(GrailsASTUtils.nonGeneric(expression.getType()));
            setNode.setGenericsTypes(genericsTypes);
        }
        return setNode;
    }

    private void addAssociationForKey(String key, List<PropertyNode> properties, ClassNode declaringType, ClassNode propertyType) {
        properties.add(new PropertyNode(key, Modifier.PUBLIC, propertyType, declaringType, null, null, null));
    }

    private void injectToStringMethod(ClassNode classNode) {
        final boolean hasToString = GrailsASTUtils.implementsOrInheritsZeroArgMethod(
                classNode, "toString", classesWithInjectedToString);
        final boolean hasToStringAnnotation = GrailsASTUtils.hasAnnotation(classNode, groovy.transform.ToString.class);

        if (!hasToString && !isEnum(classNode) && !hasToStringAnnotation) {
            GStringExpression ge = new GStringExpression(classNode.getName() + " : ${id ? id : '(unsaved)'}");
            ge.addString(new ConstantExpression(classNode.getName() + " : "));
            VariableExpression idVariable = new VariableExpression("id");
            ge.addValue(new TernaryExpression(new BooleanExpression(idVariable), idVariable, new ConstantExpression("(unsaved)")));
            Statement s = new ReturnStatement(ge);
            MethodNode mn = new MethodNode("toString", Modifier.PUBLIC, new ClassNode(String.class), new Parameter[0], new ClassNode[0], s);
            classNode.addMethod(mn);
            classesWithInjectedToString.add(classNode);
            AnnotatedNodeUtils.markAsGenerated(classNode, mn);
        }
    }

    private boolean isEnum(ClassNode classNode) {
        ClassNode parent = classNode.getSuperClass();
        while (parent != null) {
            if (parent.getName().equals("java.lang.Enum")) return true;
            parent = parent.getSuperClass();
        }
        return false;
    }

    private void injectVersionProperty(ClassNode classNode) {
        final boolean hasVersion = GrailsASTUtils.hasOrInheritsProperty(classNode, GormProperties.VERSION);

        if (!hasVersion) {
            ClassNode parent = GrailsASTUtils.getFurthestUnresolvedParent(classNode);
            parent.addProperty(GormProperties.VERSION, Modifier.PUBLIC, new ClassNode(Long.class), null, null, null);
        }
    }

    private void injectIdProperty(ClassNode classNode) {
        final boolean hasId = GrailsASTUtils.hasOrInheritsProperty(classNode, GormProperties.IDENTITY);

        if (!hasId) {
            // inject into furthest relative
            ClassNode parent = GrailsASTUtils.getFurthestUnresolvedParent(classNode);

            parent.addProperty(GormProperties.IDENTITY, Modifier.PUBLIC, new ClassNode(Long.class), null, null, null);
        }
    }

    public void performInjection(SourceUnit source, ClassNode classNode) {
        performInjection(source, null, classNode);
    }


    public void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedEntity(classNode);
    }

    public String[] getArtefactTypes() {
        return new String[] {DomainClassArtefactHandler.TYPE};
    }
}

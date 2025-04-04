package org.grails.datastore.gorm.transform;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.reflect.AstUtils;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.datastore.mapping.reflect.NameUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilities for resolving property names from domain classes etc.
 *
 * @author Graeme Rocher
 * @since 6.1
 */
public class AstPropertyResolveUtils {
    protected static Map<String, Map<String, ClassNode>> cachedClassProperties = new HashMap<>();

    /**
     * Resolves the type of of the given property
     *
     * @param classNode The class node
     * @param propertyName The property
     * @return The type
     */
    public static ClassNode getPropertyType(ClassNode classNode, String propertyName) {
        if(propertyName == null || propertyName.length() == 0) {
            return null;
        }
        Map<String, ClassNode> cachedProperties = getPropertiesFromCache(classNode);
        if (cachedProperties.containsKey(propertyName)) {
            return cachedProperties.get(propertyName);
        }
        ClassNode type = null;
        PropertyNode property = classNode.getProperty(propertyName);
        if (property != null) {
            type = property.getType();
        } else {
            MethodNode methodNode = classNode.getMethod(NameUtils.getGetterName(propertyName), new Parameter[0]);
            if (methodNode != null) {
                type = methodNode.getReturnType();
            } else {
                FieldNode fieldNode = classNode.getDeclaredField(propertyName);
                if (fieldNode != null) {
                    type = fieldNode.getType();
                }
            }
        }
        return type;
    }

    /**
     * Resolves the property names for the given class node
     *
     * @param classNode The class node
     * @return The property names
     */
    public static List<String> getPropertyNames(ClassNode classNode) {
        Map<String, ClassNode> cachedProperties = getPropertiesFromCache(classNode);
        return new ArrayList<>(cachedProperties.keySet());
    }

    private static Map<String, ClassNode> getPropertiesFromCache(ClassNode classNode) {
        String className = classNode.getName();
        Map<String, ClassNode> cachedProperties = cachedClassProperties.get(className);
        if (cachedProperties == null) {
            cachedProperties = new HashMap<>();
            boolean isDomainClass = AstUtils.isDomainClass(classNode);
            if(isDomainClass) {
                cachedProperties.put(GormProperties.IDENTITY, new ClassNode(Long.class));
                cachedProperties.put(GormProperties.VERSION, new ClassNode(Long.class));
            }
            cachedClassProperties.put(className, cachedProperties);
            ClassNode currentNode = classNode;
            while (currentNode != null && !currentNode.equals(ClassHelper.OBJECT_TYPE)) {
                populatePropertiesForClassNode(currentNode, cachedProperties, isDomainClass, !isDomainClass);
                currentNode = currentNode.getSuperClass();
            }
        } return cachedProperties;
    }

    private static void populatePropertiesForClassNode(ClassNode classNode, Map<String, ClassNode> cachedProperties, boolean isDomainClass, boolean allowAbstract) {
        List<MethodNode> methods = classNode.getMethods();
        for (MethodNode method : methods) {
            String methodName = method.getName();
            if (AstUtils.isGetter(method)) {
                if(!allowAbstract && method.isAbstract()) continue;
                String propertyName = NameUtils.getPropertyNameForGetterOrSetter(methodName);
                if(GormProperties.META_CLASS.equals(propertyName)) continue;
                if (isDomainClass && (GormProperties.HAS_MANY.equals(propertyName) || GormProperties.BELONGS_TO.equals(propertyName) || GormProperties.HAS_ONE.equals(propertyName))) {
                    FieldNode field = classNode.getField(propertyName);
                    if (field != null) {
                        populatePropertiesForInitialExpression(cachedProperties, field.getInitialExpression());
                    }
                } else if (!method.isStatic()) {
                    cachedProperties.put(propertyName, method.getReturnType());
                }
            }
        }
        List<PropertyNode> properties = classNode.getProperties();
        for (PropertyNode property : properties) {

            String propertyName = property.getName();
            if(propertyName.equals(GormProperties.META_CLASS)) continue;
            if (isDomainClass && (GormProperties.HAS_MANY.equals(propertyName) || GormProperties.BELONGS_TO.equals(propertyName) || GormProperties.HAS_ONE.equals(propertyName))) {
                Expression initialExpression = property.getInitialExpression();
                populatePropertiesForInitialExpression(cachedProperties, initialExpression);
            } else {
                cachedProperties.put(propertyName, property.getType());
            }
        }

        if (isDomainClass && classNode.isResolved()) {
            ClassPropertyFetcher propertyFetcher = ClassPropertyFetcher.forClass(classNode.getTypeClass());
            cachePropertiesForAssociationMetadata(cachedProperties, propertyFetcher, GormProperties.HAS_MANY);
            cachePropertiesForAssociationMetadata(cachedProperties, propertyFetcher, GormProperties.BELONGS_TO);
            cachePropertiesForAssociationMetadata(cachedProperties, propertyFetcher, GormProperties.HAS_ONE);
        }

    }

    private static void cachePropertiesForAssociationMetadata(Map<String, ClassNode> cachedProperties, ClassPropertyFetcher propertyFetcher, String associationMetadataName) {
        if (propertyFetcher.isReadableProperty(associationMetadataName)) {
            Object propertyValue = propertyFetcher.getPropertyValue(associationMetadataName);
            if (propertyValue instanceof Map) {
                Map hasManyMap = (Map) propertyValue;
                for (Object propertyName : hasManyMap.keySet()) {
                    Object val = hasManyMap.get(propertyName);
                    if (val instanceof Class) {
                        cachedProperties.put(propertyName.toString(), ClassHelper.make((Class) val).getPlainNodeReference());
                    }
                }
            }
        }
    }

    private static void populatePropertiesForInitialExpression(Map<String, ClassNode> cachedProperties, Expression initialExpression) {
        if (initialExpression instanceof MapExpression) {
            MapExpression me = (MapExpression) initialExpression;
            List<MapEntryExpression> mapEntryExpressions = me.getMapEntryExpressions();
            for (MapEntryExpression mapEntryExpression : mapEntryExpressions) {
                Expression keyExpression = mapEntryExpression.getKeyExpression();
                Expression valueExpression = mapEntryExpression.getValueExpression();
                if (valueExpression instanceof ClassExpression) {
                    cachedProperties.put(keyExpression.getText(), valueExpression.getType());
                }
            }
        }
    }

}

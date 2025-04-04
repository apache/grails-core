package org.grails.gorm.rx.transform

import grails.gorm.rx.DetachedCriteria
import grails.gorm.rx.RxEntity
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.grails.datastore.mapping.reflect.AstUtils

/**
 * Utility methods for AST handling in RxGORM
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class RxAstUtils extends AstUtils {
    /**
     * The rx entity class node
     */
    static final ClassNode RX_ENTITY = ClassHelper.make(RxEntity).plainNodeReference

    static final ClassNode RX_DETACHED_CRITERIA = ClassHelper.make(DetachedCriteria).plainNodeReference
    /**
     * Whether the class node is an rx entity
     *
     * @param classNode The class node
     * @return True if is an {@link RxEntity}
     */
    static boolean isRxEntity(ClassNode classNode) {
        isSubclassOf(classNode, RX_ENTITY.name)
    }
    /**
     * Return if the given class is an Observable of domain class
     *
     * @param cls The class node
     * @return True if it is
     */
    static boolean isObservableOfDomainClass(ClassNode cls) {
        if(isObservable(cls)) {
            GenericsType[] genericsTypes = cls.genericsTypes
            if(genericsTypes != null && genericsTypes.length == 1) {
                ClassNode type = genericsTypes[0].type
                return type != null && isDomainClass(type)
            }
        }
        return false
    }

    /**
     * Return if the given class is an Observable of domain class
     *
     * @param cls The class node
     * @return True if it is
     */
    static boolean isSingleOfDomainClass(ClassNode cls) {
        if(isSingle(cls)) {
            GenericsType[] genericsTypes = cls.genericsTypes
            if(genericsTypes != null && genericsTypes.length == 1) {
                ClassNode type = genericsTypes[0].type
                return type != null && isDomainClass(type)
            }
        }
        return false
    }

    /**
     * Return if the given class is an Observable of domain class
     *
     * @param cls The class node
     * @return True if it is
     */
    static boolean isObservableOf(ClassNode cls, ClassNode parent) {
        if(isObservable(cls) || isSingle(cls)) {
            GenericsType[] genericsTypes = cls.genericsTypes
            if(genericsTypes != null && genericsTypes.length == 1) {
                ClassNode type = genericsTypes[0].type
                return type != null && isSubclassOfOrImplementsInterface(type, parent)
            }
        }
        return false
    }
    /**
     * Return if the given class is an Observable of domain class
     *
     * @param cls The class node
     * @return True if it is
     */
    static boolean isSingleOf(ClassNode cls, Class parent) {
        if(isSingle(cls)) {
            GenericsType[] genericsTypes = cls.genericsTypes
            if(genericsTypes != null && genericsTypes.length == 1) {
                ClassNode type = genericsTypes[0].type
                return type != null && isSubclassOfOrImplementsInterface(type, parent.name)
            }
        }
        return false
    }
    /**
     * Return if the given class is an Observable of domain class
     *
     * @param cls The class node
     * @return True if it is
     */
    static boolean isObservableOf(ClassNode cls, Class parent) {
        if(isObservable(cls)) {
            GenericsType[] genericsTypes = cls.genericsTypes
            if(genericsTypes != null && genericsTypes.length == 1) {
                ClassNode type = genericsTypes[0].type
                return type != null && isSubclassOfOrImplementsInterface(type, parent.name)
            }
        }
        return false
    }
    /**
     * Is the given class a {@link rx.Single}
     *
     * @param cls The class
     * @return True if it is
     */
    static boolean isSingle(ClassNode cls) {
        isSubclassOf(cls, "rx.Single")
    }

    /**
     * Is the given class an {@link rx.Observable}
     *
     * @param cls The class
     * @return True if it is
     */
    static boolean isObservable(ClassNode cls) {
        return isSubclassOf(cls, "rx.Observable")
    }
}

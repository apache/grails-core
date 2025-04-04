package org.grails.gorm.rx.services.implementers

import grails.gorm.rx.services.RxSchedule
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.services.implementers.AdaptedImplementer
import org.grails.datastore.gorm.services.implementers.AnnotatedServiceImplementer
import org.grails.datastore.gorm.services.implementers.IterableInterfaceProjectionBuilder
import org.grails.datastore.gorm.services.implementers.IterableProjectionServiceImplementer
import org.grails.datastore.gorm.services.implementers.IterableServiceImplementer
import org.grails.datastore.gorm.services.implementers.PrefixedServiceImplementer
import org.grails.datastore.mapping.core.Ordered
import org.grails.gorm.rx.transform.RxAstUtils
import org.grails.gorm.rx.transform.RxScheduleIOTransformation
import org.springframework.core.GenericTypeResolver

import static org.grails.datastore.mapping.reflect.AstGenericsUtils.resolveSingleGenericType
import static org.grails.datastore.mapping.reflect.AstUtils.addAnnotationOrGetExisting
import static org.grails.gorm.rx.transform.RxAstUtils.isRxEntity

/**
 * Adapts results for queries that return an {@link rx.Observable}
 *
 * @author Graeme Rocher
 * @since 6.1.1
 */
@CompileStatic
class ObservableResultAdapter  implements ServiceImplementer, Ordered, AdaptedImplementer {
    final PrefixedServiceImplementer adapted
    final Class returnType
    final boolean isDomainReturnType

    ObservableResultAdapter(IterableServiceImplementer adapted) {
        this.adapted = adapted
        this.returnType = GenericTypeResolver.resolveTypeArgument(adapted.getClass(), IterableServiceImplementer)
        this.isDomainReturnType = returnType == GormEntity
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        def alreadyImplemented = methodNode.getNodeMetaData(IMPLEMENTED)
        if(!alreadyImplemented) {
            boolean isObservableOfReturnType

            ClassNode methodReturnType = methodNode.returnType
            if(isDomainReturnType) {
                isObservableOfReturnType = RxAstUtils.isObservableOfDomainClass(methodReturnType)
            }
            else if(returnType != null) {
                isObservableOfReturnType = RxAstUtils.isObservableOf(methodReturnType, returnType)
            }
            else {
                isObservableOfReturnType = RxAstUtils.isObservableOf(methodReturnType, Object)
            }

            if(!isObservableOfReturnType && (adapted instanceof IterableInterfaceProjectionBuilder) && RxAstUtils.isObservable(methodReturnType)) {
                ClassNode genericType = resolveSingleGenericType(methodReturnType)
                isObservableOfReturnType =  ((IterableInterfaceProjectionBuilder)adapted).isInterfaceProjection(domainClass, methodNode, genericType )
            }

            if(adapted instanceof AnnotatedServiceImplementer) {
                return ((AnnotatedServiceImplementer)adapted).isAnnotated(domainClass, methodNode) && isObservableOfReturnType
            }
            else {
                String prefix = adapted.resolvePrefix(methodNode)
                if(adapted instanceof IterableProjectionServiceImplementer) {
                    ClassNode genericType = resolveSingleGenericType(methodReturnType)
                    ClassNode iterableType = GenericsUtils.makeClassSafeWithGenerics(Iterable, genericType)

                    if(adapted instanceof IterableInterfaceProjectionBuilder) {
                        return prefix && ((IterableInterfaceProjectionBuilder)adapted).isInterfaceProjection(domainClass, methodNode, iterableType )
                    }
                    else {
                        return prefix && ((IterableProjectionServiceImplementer)adapted).isCompatibleReturnType(domainClass, methodNode, iterableType, prefix)
                    }
                }
                else {
                    return prefix && isObservableOfReturnType
                }
            }
        }

        return false
    }

    @Override
    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        ClassNode returnType = resolveSingleGenericType(abstractMethodNode.returnType)
        if(isDomainReturnType && !(adapted instanceof IterableInterfaceProjectionBuilder)) {
            domainClassNode = returnType
        }

        ClassNode iterableType = GenericsUtils.makeClassSafeWithGenerics(Iterable, returnType)
        newMethodNode.setNodeMetaData(RETURN_TYPE, iterableType )
        adapted.implement(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode)

        if(!isRxEntity(domainClassNode)) {
            def ann = addAnnotationOrGetExisting(newMethodNode, RxSchedule)
            ann.setMember(RxScheduleIOTransformation.ANN_SINGLE_RESULT, ConstantExpression.TRUE)
            newMethodNode.addAnnotation(ann)
        }
    }

    @Override
    int getOrder() {
        if(adapted instanceof Ordered) {
            return ((Ordered)adapted).getOrder()
        }
        return 0
    }
}

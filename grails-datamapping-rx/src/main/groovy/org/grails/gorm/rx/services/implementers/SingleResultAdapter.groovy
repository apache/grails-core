package org.grails.gorm.rx.services.implementers

import grails.gorm.rx.services.RxSchedule
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.services.implementers.AdaptedImplementer
import org.grails.datastore.gorm.services.implementers.AnnotatedServiceImplementer
import org.grails.datastore.gorm.services.implementers.PrefixedServiceImplementer
import org.grails.datastore.gorm.services.implementers.SingleResultInterfaceProjectionBuilder
import org.grails.datastore.gorm.services.implementers.SingleResultProjectionServiceImplementer
import org.grails.datastore.gorm.services.implementers.SingleResultServiceImplementer
import org.grails.datastore.mapping.core.Ordered
import org.grails.gorm.rx.transform.RxAstUtils
import org.grails.gorm.rx.transform.RxScheduleIOTransformation
import org.springframework.core.GenericTypeResolver

import static org.grails.datastore.mapping.reflect.AstGenericsUtils.resolveSingleGenericType
import static org.grails.datastore.mapping.reflect.AstUtils.addAnnotationOrGetExisting
import static org.grails.gorm.rx.transform.RxAstUtils.isRxEntity

/**
 * Adapts existing implementers for RxJava
 *
 * @author Graeme Rocher
 * @since 6.1.1
 */
@CompileStatic
class SingleResultAdapter implements ServiceImplementer, Ordered, AdaptedImplementer {

    final PrefixedServiceImplementer adapted
    final Class returnType
    final boolean isDomainReturnType

    SingleResultAdapter(SingleResultServiceImplementer adapted) {
        this.adapted = adapted
        this.returnType = GenericTypeResolver.resolveTypeArgument(adapted.getClass(), SingleResultServiceImplementer)
        this.isDomainReturnType = returnType == GormEntity
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        def alreadyImplemented = methodNode.getNodeMetaData(IMPLEMENTED)
        if(!alreadyImplemented) {
            boolean isObservableOfReturnType

            ClassNode methodReturnType = methodNode.returnType
            if(isDomainReturnType) {
                isObservableOfReturnType = RxAstUtils.isSingleOfDomainClass(methodReturnType) && !methodReturnType.isArray()
            }
            else if(returnType != null) {
                isObservableOfReturnType = RxAstUtils.isSingleOf(methodReturnType, returnType) && !methodReturnType.isArray()
            }
            else {
                isObservableOfReturnType = RxAstUtils.isSingleOf(methodReturnType, Object) && !methodReturnType.isArray()
            }

            if(!isObservableOfReturnType && (adapted instanceof SingleResultInterfaceProjectionBuilder) && RxAstUtils.isSingle(methodReturnType)) {
                ClassNode genericType = resolveSingleGenericType(methodReturnType)
                isObservableOfReturnType =  ((SingleResultInterfaceProjectionBuilder)adapted).isInterfaceProjection(domainClass, methodNode, genericType )
            }

            if(adapted instanceof AnnotatedServiceImplementer) {
                return ((AnnotatedServiceImplementer)adapted).isAnnotated(domainClass, methodNode) && isObservableOfReturnType
            }
            else {
                String prefix = adapted.resolvePrefix(methodNode)
                if(adapted instanceof SingleResultProjectionServiceImplementer) {
                    ClassNode genericType = resolveSingleGenericType(methodReturnType)
                    return ((SingleResultProjectionServiceImplementer)adapted).isCompatibleReturnType(domainClass, methodNode, genericType, prefix)
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
        newMethodNode.setNodeMetaData(RETURN_TYPE, returnType )
        adapted.implement(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode)

        if(!isRxEntity(returnType)) {
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

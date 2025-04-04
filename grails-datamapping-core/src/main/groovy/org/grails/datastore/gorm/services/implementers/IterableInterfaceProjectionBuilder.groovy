package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.transform.AstPropertyResolveUtils
import org.grails.datastore.mapping.reflect.AstGenericsUtils
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Projection builder for iterable results like lists and arrays
 *
 * @author Graeme Rocher
 * @since 6.1.1
 */
@CompileStatic
trait IterableInterfaceProjectionBuilder extends InterfaceProjectionBuilder{

    /**
     * Is the method an interface projection
     * @param domainClass
     * @param methodNode
     * @param returnType
     * @return True if it is
     */
    @Override
    boolean isInterfaceProjection(ClassNode domainClass, MethodNode methodNode, ClassNode returnType) {
        if(AstUtils.isSubclassOfOrImplementsInterface(returnType, Iterable.name) || returnType.isArray()) {
            ClassNode genericType = AstGenericsUtils.resolveSingleGenericType(returnType)
            if(genericType != null && genericType.isInterface() && !genericType.packageName?.startsWith("java.")) {

                List<String> interfacePropertyNames = AstPropertyResolveUtils.getPropertyNames(genericType)

                for(prop in interfacePropertyNames) {
                    ClassNode existingType = AstPropertyResolveUtils.getPropertyType(domainClass, prop)
                    ClassNode propertyType = AstPropertyResolveUtils.getPropertyType(genericType, prop)
                    if(existingType == null) {
                        return false
                    }
                    else if(!AstUtils.isSubclassOfOrImplementsInterface(existingType, propertyType)) {
                        return false
                    }
                }
                return true
            }
        }
        return false
    }

    Statement buildInterfaceProjection(ClassNode targetDomainClass, MethodNode abstractMethodNode, Expression queryMethodCall, Expression args, MethodNode newMethodNode) {
        ClassNode declaringClass = newMethodNode.declaringClass
        ClassNode returnType = (ClassNode) newMethodNode.getNodeMetaData(ServiceImplementer.RETURN_TYPE) ?: abstractMethodNode.returnType
        ClassNode interfaceNode = AstGenericsUtils.resolveSingleGenericType(returnType)
        if(!interfaceNode.isInterface()) {
            AstUtils.error(targetDomainClass.module.context, abstractMethodNode, "Cannot implement interface projection, [$interfaceNode.name] is not an interface!")
        }
        MethodNode methodTarget = buildInterfaceImpl(interfaceNode, declaringClass, targetDomainClass, abstractMethodNode)
        ClassNode innerClassNode = methodTarget.getDeclaringClass()

        VariableExpression delegateVar = varX('$delegate', innerClassNode)
        Parameter p = param(ClassHelper.OBJECT_TYPE, '$target')
        Expression setTargetCall = assignX(propX(delegateVar, '$target'), castX(targetDomainClass, varX(p)) )
        Statement closureBody = block(
                declS(delegateVar, ctorX(innerClassNode)),
                stmt(setTargetCall),
                returnS(delegateVar)
        )
        ClosureExpression closureExpression = closureX(params(p), closureBody)
        def variableScope = newMethodNode.getVariableScope()
        variableScope.putDeclaredVariable(delegateVar)
        closureExpression.setVariableScope(variableScope)
        Expression collectCall = callX(queryMethodCall, "collect", closureExpression)

        if(returnType.isArray()) {
            // handle array cast
            collectCall = castX( returnType.plainNodeReference, collectCall)
        }
        stmt(collectCall)

    }
}
package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement

import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX

/**
 * Used for performing interface projections on
 */
@CompileStatic
class FindAllInterfaceProjectionImplementer extends FindAllImplementer implements IterableInterfaceProjectionBuilder, IterableProjectionServiceImplementer {
    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return currentDomainClassNode
    }

    @Override
    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return isInterfaceProjection(domainClass, methodNode, returnType)
    }

    @Override
    void implementWithQuery(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode, BlockStatement body, VariableExpression detachedCriteriaVar, Expression queryArgs) {
        ClassNode returnType = (ClassNode)newMethodNode.getNodeMetaData(RETURN_TYPE) ?: newMethodNode.returnType
        Expression methodCall = callX(detachedCriteriaVar, "list", queryArgs)
        if(returnType.isArray()) {
            methodCall = castX(returnType.plainNodeReference, methodCall)
        }

        body.addStatement(
            buildInterfaceProjection(domainClassNode, abstractMethodNode, methodCall, queryArgs, newMethodNode)
        )
    }

}

package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.reflect.AstUtils
import static org.codehaus.groovy.ast.tools.GeneralUtils.*
/**
 * An implementer that handles delete methods
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindAndDeleteImplementer extends FindOneImplementer implements SingleResultServiceImplementer<GormEntity> {

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if(methodNode.parameters.length == 0) {
            return false
        }
        else {
            return super.doesImplement(domainClass, methodNode)
        }
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isDomainClass(returnType)
    }

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return methodNode.returnType
    }

    @Override
    protected Statement buildReturnStatement(ClassNode targetDomainClass, MethodNode abstractMethodNode, Expression queryMethodCall, Expression args, MethodNode newMethodNode) {
        VariableExpression var = varX('$obj', targetDomainClass)
        MethodCallExpression deleteCall = args != null ? callX(var, "delete", args) : callX(var, "delete")

        deleteCall.setSafe(true) // null safe
        block(
            declS(var, queryMethodCall),
            stmt(deleteCall),
            returnS(var)
        )
    }

    @Override
    protected void applyDefaultTransactionHandling(MethodNode newMethodNode) {
        newMethodNode.addAnnotation(new AnnotationNode(TransactionalTransform.MY_TYPE))
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return DeleteImplementer.HANDLED_PREFIXES
    }
}

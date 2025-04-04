package org.grails.datastore.gorm.services.implementers

import grails.gorm.services.Query
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt

/**
 * Support for update String-queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class UpdateStringQueryImplementer extends AbstractStringQueryImplementer implements SingleResultServiceImplementer<Number>, AnnotatedServiceImplementer<Query>, NoResultServiceImplementer {

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        return isAnnotated(domainClass, methodNode) && isCompatibleReturnType(domainClass, methodNode, methodNode.returnType, methodNode.name)
    }

    @Override
    boolean isAnnotated(ClassNode domainClass, MethodNode methodNode) {
        AnnotationNode annotation = AstUtils.findAnnotation(methodNode, Query)
        if (annotation != null) {
            Expression expr = annotation.getMember("value")
            if (expr instanceof GStringExpression) {
                GStringExpression gstring = (GStringExpression) expr
                String queryStem = gstring.strings[0].text.toLowerCase(Locale.ENGLISH)
                if (queryStem.contains("update") || queryStem.contains('delete')) {
                    return true
                }
            } else if (expr instanceof ConstantExpression) {
                String queryStem = ((ConstantExpression) expr).text.toLowerCase(Locale.ENGLISH)
                if (queryStem.contains("update") || queryStem.contains('delete')) {
                    return true
                }
            }
        }
        return false
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isSubclassOfOrImplementsInterface(returnType, Number.name) || returnType == ClassHelper.VOID_TYPE
    }

    @Override
    protected Statement buildQueryReturnStatement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression args) {
        ClassNode returnType = newMethodNode.returnType
        boolean isVoid = returnType == ClassHelper.VOID_TYPE
        Expression methodCall = callX(findStaticApiForConnectionId(domainClassNode, newMethodNode), "executeUpdate", args)
        methodCall = isVoid ? methodCall : castX(returnType.plainNodeReference, methodCall)
        return isVoid ? stmt(methodCall) : returnS(methodCall)
    }

    @Override
    protected void applyDefaultTransactionHandling(MethodNode newMethodNode) {
        newMethodNode.addAnnotation(new AnnotationNode(TransactionalTransform.MY_TYPE))
    }
}

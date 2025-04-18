package org.grails.datastore.gorm.neo4j.services.implementers

import grails.gorm.services.Query
import grails.neo4j.services.Cypher
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.services.implementers.AnnotatedServiceImplementer
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt

/**
 * Implementer for Cypher update queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class UpdateCypherQueryImplementer extends FindAllCypherQueryImplementer implements AnnotatedServiceImplementer<Cypher> {

    @Override
    int getOrder() {
        return super.getOrder() - 100
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        AnnotationNode ann = AstUtils.findAnnotation(methodNode, Cypher)
        if( ann != null) {
            Expression expr = ann.getMember("value")
            if(expr instanceof GStringExpression) {
                GStringExpression gstring = (GStringExpression)expr
                for(ConstantExpression ce in gstring.strings) {
                    String queryStem = ce.text
                    if(isWriteOperation(queryStem)) {
                        return isCompatibleReturnType(domainClass, methodNode, methodNode.returnType, methodNode.name)
                    }

                }
            }
            else if(expr instanceof ConstantExpression) {
                String queryStem = ((ConstantExpression)expr).text
                if(isWriteOperation(queryStem)) {
                    return isCompatibleReturnType(domainClass, methodNode, methodNode.returnType, methodNode.name)
                }
            }
        }
        return false
    }

    protected boolean isWriteOperation(String queryStem) {
        ['SET ', 'CREATE ', 'DELETE ', 'REMOVE '].any() { queryStem.contains(it) }
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isSubclassOfOrImplementsInterface(returnType, Number.name) || returnType == ClassHelper.VOID_TYPE
    }

    @Override
    protected Statement buildQueryReturnStatement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression args) {
        ClassNode returnType = newMethodNode.returnType
        boolean isVoid = returnType == ClassHelper.VOID_TYPE
        Expression methodCall = callX(domainClassNode, "executeUpdate", args)
        methodCall = isVoid ? methodCall : castX(returnType.plainNodeReference, methodCall)
        return isVoid ? stmt(methodCall) : returnS(methodCall)
    }

    @Override
    protected void applyDefaultTransactionHandling(MethodNode newMethodNode) {
        newMethodNode.addAnnotation(new AnnotationNode(TransactionalTransform.MY_TYPE))
    }
}

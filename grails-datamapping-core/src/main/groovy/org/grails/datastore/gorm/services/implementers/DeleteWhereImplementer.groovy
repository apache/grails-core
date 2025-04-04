package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.reflect.AstUtils

/**
 * Implement delete method that are annotated with {@link grails.gorm.services.Where}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class DeleteWhereImplementer extends AbstractWhereImplementer {

    public static final int POSITION = AbstractWhereImplementer.POSITION - 100

    @Override
    int getOrder() {
        return POSITION
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        String prefix = handledPrefixes.find() { String it -> methodNode.name.startsWith(it) }
        if(prefix != null) {
            return super.doesImplement(domainClass, methodNode)
        }
        return false
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return ClassHelper.VOID_TYPE.equals(returnType) || AstUtils.isSubclassOfOrImplementsInterface(returnType, Number.name)
    }

    @Override
    protected void applyDefaultTransactionHandling(MethodNode newMethodNode) {
        newMethodNode.addAnnotation(new AnnotationNode(TransactionalTransform.MY_TYPE))
    }

    @Override
    protected Statement buildReturnStatement(ClassNode domainClass, MethodNode abstractMethodNode, MethodNode methodNode, Expression queryExpression) {
        boolean isVoid = abstractMethodNode.returnType == ClassHelper.VOID_TYPE
        if(isVoid) {
            return GeneralUtils.stmt(queryExpression)
        }
        else {
            return GeneralUtils.returnS( GeneralUtils.castX( abstractMethodNode.returnType, queryExpression) )
        }
    }

    @Override
    protected Expression findArgsExpression(MethodNode newMethodNode) {
        return null
    }

    @Override
    protected String getQueryMethodToExecute(ClassNode domainClass, MethodNode newMethodNode) {
        return "deleteAll"
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return DeleteImplementer.HANDLED_PREFIXES
    }
}

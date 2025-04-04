package org.grails.datastore.gorm.transactions.transform

import grails.gorm.transactions.Rollback
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.transform.GroovyASTTransformation

@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class RollbackTransform extends TransactionalTransform {

    public static final ClassNode MY_TYPE = new ClassNode(Rollback)

    @Override
    protected String getTransactionTemplateMethodName() {
        return "executeAndRollback"
    }
}

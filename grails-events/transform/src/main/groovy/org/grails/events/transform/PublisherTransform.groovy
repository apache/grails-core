package org.grails.events.transform

import grails.events.Event
import grails.events.EventPublisher
import grails.events.annotation.Events
import grails.events.annotation.Publisher
import grails.gorm.transactions.Transactional
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.EmptyExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.trait.TraitComposer
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.gorm.transform.AbstractMethodDecoratingTransformation
import org.grails.datastore.mapping.core.Ordered
import org.grails.datastore.mapping.reflect.AstUtils
import org.springframework.transaction.event.TransactionPhase

import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.codehaus.groovy.ast.tools.GeneralUtils.args

/**
 * A transform that transforms a method publishing the result to the given event
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class PublisherTransform extends AbstractMethodDecoratingTransformation implements Ordered {
    /**
     * The position of the transform. Before the transactional transform
     */
    public static final int POSITION = TransactionalTransform.POSITION + 50
    public static final Object APPLIED_MARKER = new Object()

    @Override
    int getOrder() {
        return POSITION
    }

    @Override
    protected String getRenamedMethodPrefix() {
        return '$pub__'
    }

    @Override
    protected void enhanceClassNode(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        if(!AstUtils.implementsInterface(classNode, EventPublisher.name)) {
            classNode.addInterface(ClassHelper.make(EventPublisher))
            if (compilationUnit != null) {
                TraitComposer.doExtendTraits(classNode, sourceUnit, compilationUnit)
            }
        }
    }

    @Override
    protected Expression buildDelegatingMethodCall(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, MethodCallExpression originalMethodCallExpr, BlockStatement newMethodBody) {

        Expression result = varX('$result')
        // if the return type is void
        // def $result = null
        // callMethod()

        BlockStatement tryBody = new BlockStatement()
        TryCatchStatement tryCatch = new TryCatchStatement(tryBody, new EmptyStatement())
        newMethodBody.addStatement(declS(result, new EmptyExpression()))
        if(methodNode.returnType != ClassHelper.VOID_TYPE) {
            tryBody.addStatement(assignS(result, originalMethodCallExpr))
        }
        // otherwise..
        // def $result = callMethod()
        else {
            tryBody.addStatement(stmt(originalMethodCallExpr))
            newMethodBody.addStatement(assignS(result, ConstantExpression.NULL))
        }
        newMethodBody.addStatement(
            tryCatch
        )

        AnnotationNode eventsAnn = AstUtils.findAnnotation(classNode, Events)

        Expression eventId = annotationNode.getMember("value")
        if(!eventId?.text) {
            eventId = constX(methodNode.name)
        }

        Expression namespace = eventsAnn?.getMember("namespace")
        boolean hasNamespace = namespace instanceof ConstantExpression
        if(hasNamespace) {
            eventId = new ConstantExpression(namespace.text + ':' + eventId.text )
        }

        Expression errorEventId = annotationNode.getMember("error")
        if(errorEventId == null) {
            errorEventId = eventsAnn?.getMember("error")
        }
        if(!errorEventId?.text) {
            errorEventId = eventId
        }
        else if(hasNamespace) {
            errorEventId = new ConstantExpression(namespace.text + ':' + errorEventId.text )
        }

        Expression phase = annotationNode.getMember("phase")
        if(phase == null) {
            phase = eventsAnn?.getMember("phase")
        }
        MapExpression params = new MapExpression()
        for(param in methodNode.parameters) {
            params.addMapEntryExpression(
                constX(param.name),
                varX(param)
            )
        }
        Expression newEvent = ctorX(ClassHelper.make(Event), args(eventId, params, result))
        def eventArgs = args(newEvent)
        if(phase != null) {
            eventArgs.addExpression(phase)
        }
        else {
            if( AstUtils.hasAnnotation(methodNode, Transactional) ) {
                eventArgs.addExpression(propX(classX(TransactionPhase), "AFTER_COMMIT"))
            }
        }

        Parameter exceptionParam = param(ClassHelper.make(Throwable), '$t')
        Expression errorEvent = ctorX(ClassHelper.make(Event), args(errorEventId, params, varX(exceptionParam)))
        def errorArgs = args(errorEvent)
        if(phase != null) {
            errorArgs.addExpression(phase)
        }
        else {
            if( AstUtils.hasAnnotation(methodNode, Transactional) ) {
                errorArgs.addExpression(propX(classX(TransactionPhase), "AFTER_ROLLBACK"))
            }
        }

        Statement catchBody = block(
            stmt(callThisX("publish", errorArgs)),
            throwS(varX(exceptionParam))
        )
        CatchStatement catchStatement = new CatchStatement(exceptionParam, catchBody)
        tryCatch.addCatch(catchStatement)
        tryBody.addStatement(
            stmt( callThisX("publish", eventArgs) )
        )
        return result
    }

    @Override
    protected ClassNode getAnnotationType() {
        return ClassHelper.make(Publisher)
    }


    @Override
    protected Object getAppliedMarker() {
        return APPLIED_MARKER
    }
}

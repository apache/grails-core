package org.grails.gorm.rx.transform

import grails.gorm.rx.services.RxSchedule
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.datastore.gorm.multitenancy.transform.TenantTransform
import org.grails.datastore.gorm.transform.AbstractDatastoreMethodDecoratingTransformation
import org.grails.datastore.gorm.transform.AbstractMethodDecoratingTransformation
import org.grails.datastore.mapping.core.Ordered
import org.grails.datastore.mapping.reflect.AstGenericsUtils
import org.grails.gorm.rx.services.support.RxServiceSupport
import rx.Scheduler

import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_PARAMETERS
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX

/**
 * A transformation that will convert a blocking GORM operation into an Observable that runs on the RxJava {@link rx.schedulers.Schedulers#io()} scheduler
 *
 * @see {@link rx.schedulers.Schedulers#io()}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class RxScheduleIOTransformation extends AbstractMethodDecoratingTransformation implements Ordered {
    private static final Object APPLIED_MARKER = new Object()
    public static final String RENAMED_METHOD_PREFIX = '$rx__'

    public static final ClassNode ANNOTATION_TYPE = ClassHelper.make(RxSchedule)
    public static final AnnotationNode ANNOTATION = new AnnotationNode(ANNOTATION_TYPE)
    public static final String ANN_SINGLE_RESULT = "singleResult"
    public static final String ANN_SCHEDULER = "scheduler"

    @Override
    protected ClassNode getAnnotationType() {
        return ANNOTATION_TYPE
    }

    @Override
    protected Object getAppliedMarker() {
        return APPLIED_MARKER
    }

    @Override
    protected String getRenamedMethodPrefix() {
        return RENAMED_METHOD_PREFIX
    }

    @Override
    protected MethodNode weaveNewMethod(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, Map<String, ClassNode> genericsSpec) {
        Object appliedMarker = getAppliedMarker()
        if ( methodNode.getNodeMetaData(appliedMarker) == appliedMarker ) {
            return methodNode
        }
        if(methodNode.isAbstract()) {
            return methodNode
        }

        List<MethodNode> decorated = (List<MethodNode>)methodNode.getNodeMetaData(DECORATED_METHODS)
        ClassNode newReturnType = resolveReturnTypeForNewMethod(methodNode)
        if(RxAstUtils.isObservable(methodNode.returnType)) {
            newReturnType = GenericsUtils.makeClassSafeWithGenerics(Iterable, newReturnType)
        }
        if(decorated != null) {

            for(MethodNode mn in decorated) {
                mn.setReturnType(newReturnType)
                alignReturnType(mn, newReturnType)
            }
        }

        // align the return types
        MethodNode newMethod = super.weaveNewMethod(sourceUnit, annotationNode, classNode, methodNode, genericsSpec)
        alignReturnType(newMethod, newReturnType)
        return newMethod
    }

    protected void alignReturnType(MethodNode newMethod, ClassNode newReturnType) {
        newMethod.setReturnType(newReturnType)
        BlockStatement newMethodBody = (BlockStatement) newMethod.code
        if(newMethodBody != null) {

            ReturnStatement rs = null
            Statement last = newMethodBody.statements[-1]
            if (last instanceof ReturnStatement) {
                rs = (ReturnStatement) last
            }
            Expression returnExpr = rs?.expression
            if (returnExpr instanceof CastExpression) {
                rs.expression = CastExpression.asExpression(newReturnType, returnExpr)
            }
        }
    }

    @Override
    protected MethodCallExpression buildDelegatingMethodCall(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, MethodCallExpression originalMethodCallExpr, BlockStatement newMethodBody) {
        Expression schedulerExpression = annotationNode.getMember(ANN_SCHEDULER)
        ArgumentListExpression args = new ArgumentListExpression()
        if(schedulerExpression instanceof ClosureExpression) {
            args.addExpression(
                castX(ClassHelper.make(Scheduler), callX(schedulerExpression, "call"))
            )
        }

        VariableScope variableScope = methodNode.getVariableScope()
        if(RxAstUtils.isSingle(methodNode.returnType)) {
            return makeDelegatingClosureCall( classX(RxServiceSupport), "createSingle", args, ZERO_PARAMETERS, originalMethodCallExpr, variableScope)
        }
        else {
            return makeDelegatingClosureCall( classX(RxServiceSupport), "create", args, ZERO_PARAMETERS, originalMethodCallExpr, variableScope)
        }
    }

    @Override
    protected ClassNode resolveReturnTypeForNewMethod(MethodNode methodNode) {
        AstGenericsUtils.resolveSingleGenericType(methodNode.returnType)
    }

    @Override
    int getOrder() {
        return TenantTransform.POSITION - 100
    }
}

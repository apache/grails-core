package grails.views.compiler

import grails.views.Views
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport

import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE

/**
 * An abstract type checking extension for view DSLs to implement
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class BuilderTypeCheckingExtension extends GroovyTypeCheckingExtensionSupport.TypeCheckingDSL {

    boolean insideScope = true

    @Override
    @CompileDynamic
    Object run() {
        BuilderTypeCheckingExtension self = this

        def modelTypesClassNodes = null



        beforeVisitClass { classNode ->
            modelTypesClassNodes = classNode.getNodeMetaData(Views.MODEL_TYPES)
            if (modelTypesClassNodes==null) {
                // push a new error collector, we want type checking errors to be silent
                context.pushErrorCollector()
            }
        }
        beforeMethodCall { mec ->
            if(mec instanceof MethodCallExpression) {
                beforeMethodCallExpression(mec)
            }
        }
        beforeVisitMethod {
            newScope {
                dynamicMethods = [] as Set
                builderCalls = [] as Set
            }
        }

        unresolvedProperty { PropertyExpression pe ->
            if(isPropertyDynamic(pe)) {
                return makeDynamic(pe)
            }
        }
        methodNotFound { receiver, name, argList, argTypes, call ->
            if (call.implicitThis && insideScope) {
                currentScope.builderCalls << call
                return makeDynamic(call, OBJECT_TYPE)
            }
            else if(receiver.name == self.getBuilderClassNode().name) {
                currentScope.builderCalls << call
                return makeDynamic(call, OBJECT_TYPE)
            }
            else if(isMethodDynamic(receiver, name, argList, argTypes, call)) {
                currentScope.dynamicMethods << call
                return makeDynamic(call, OBJECT_TYPE)
            }
        }

        afterVisitMethod { mn ->
            scopeExit {
                if(mn.name == 'run') {

                    new BuilderMethodReplacer(
                            self.getBuilderInvokeMethod(),
                            self.getDelegateInvokeMethod(),
                            self.getBuilderVariableName(),
                            context.source,
                            builderCalls)
                            .visitMethod(mn)

                    self.transformDynamicMethods(context.source, mn, dynamicMethods)
                }

            }
        }
    }

    void beforeMethodCallExpression(MethodCallExpression methodCallExpression) {
        // no-op
    }
    void transformDynamicMethods(SourceUnit source, MethodNode mn, Set dynamicCalls) {
        // no-op
    }

    boolean isMethodDynamic(receiver, name, argList, argTypes, call) {
        return false
    }

    boolean isPropertyDynamic(PropertyExpression propertyExpression) {
        return false
    }


    /**
     * @return The method node to invoke for an unresolved dynamic method on the main builder variable
     */
    abstract MethodNode getBuilderInvokeMethod()
    /**
     * @return The method node to invoke for unresolved methods within closures of the builder (implicit this)
     */
    abstract MethodNode getDelegateInvokeMethod()
    /**
     * @return The name within the view script of the main builder variable
     */
    abstract String getBuilderVariableName()

    /**
     * @return The class node of the builder
     */
    abstract ClassNode getBuilderClassNode()

    protected static class BuilderMethodReplacer extends ClassCodeExpressionTransformer {

        private final MethodNode builderInvokeMethod
        private final MethodNode delegateInvokeMethod
        private final String builderVariableName
        private final SourceUnit unit
        private final Set<MethodCallExpression> callsToBeReplaced

        private Expression builderExpression

        BuilderMethodReplacer(MethodNode builderInvokeMethod, MethodNode delegateInvokeMethod, String builderVariableName, SourceUnit unit, Set<MethodCallExpression> callsToBeReplaced) {
            this.builderInvokeMethod = builderInvokeMethod
            this.delegateInvokeMethod = delegateInvokeMethod
            this.builderVariableName = builderVariableName
            this.unit = unit
            this.callsToBeReplaced = callsToBeReplaced
        }

        @Override
        protected SourceUnit getSourceUnit() {
            unit
        }

        @Override
        void visitClosureExpression(final ClosureExpression expression) {
            super.visitClosureExpression(expression)
        }

        @Override
        @CompileDynamic
        Expression transform(final Expression exp) {
            if (callsToBeReplaced.contains(exp)) {
                def args = exp.arguments instanceof TupleExpression ? exp.arguments.expressions : [exp.arguments]
                if(exp.objectExpression.name == builderVariableName) {
                    this.builderExpression = exp.objectExpression
                }
                args*.visit(this)
                // replace with direct call to methodMissing
                def isImplicitThis = exp.implicitThis
                def call = new MethodCallExpression(
                        isImplicitThis ? new VariableExpression("delegate") : exp.objectExpression,
                        "invokeMethod",
                        new ArgumentListExpression(
                                new ConstantExpression(exp.getMethodAsString()),
                                new ArrayExpression(
                                        OBJECT_TYPE,
                                        [* args]
                                )
                        )
                )
                call.implicitThis = false
                call.safe = exp.safe
                call.spreadSafe = exp.spreadSafe

                if(isImplicitThis) {
                    call.methodTarget = delegateInvokeMethod
                }
                else {

                    call.methodTarget = builderInvokeMethod
                }
                call
            } else if (exp instanceof ClosureExpression) {
                exp.code.visit(this)
                super.transform(exp)
            } else {
                super.transform(exp)
            }
        }
    }
}


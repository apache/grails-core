package grails.views.compiler

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilationUnit

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

@CompileStatic
class HalCodeVisitorSupport extends CodeVisitorSupport {

    Map<BlockStatement, Statement> newStatements = [:]
    BlockStatement currentBlock

    CompilationUnit unit

    HalCodeVisitorSupport(CompilationUnit unit) {
        this.unit = unit
    }

    @Override
    void visitBlockStatement(BlockStatement block) {
        currentBlock = block
        List<Statement> statements = block.statements
        for(int i = 0; i < statements.size(); i++) {
            statements[i].visit(this)
            if (newStatements.containsKey(block)) {
                statements.add(i, newStatements.get(block))
                i++
                newStatements.remove(block)
            }
        }
    }

    @Override
    void visitVariableExpression(VariableExpression expression) {
        if (expression.accessedVariable && expression.accessedVariable.name == "hal") {
            Statement statement = stmt(
                assignX(propX(varX(expression.accessedVariable), 'delegate'), varX('delegate'))
            )
            newStatements.put(currentBlock, statement)
        }
    }
}

package org.grails.orm.hibernate.query;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.dialect.Dialect;
import org.hibernate.type.StandardBasicTypes;

public class GrailsRLikeFunctionContributor implements FunctionContributor {

    public static final String RLIKE = "rlike";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        Dialect dialect = functionContributions.getDialect();

        // Use the Enum to resolve the pattern
        String pattern = RegexDialectPattern.findPatternForDialect(dialect);

        functionContributions.getFunctionRegistry().registerPattern(
                RLIKE,
                pattern,
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.BOOLEAN)
        );
    }
}

package liquibase.ext.hibernate.snapshot;

import liquibase.Scope;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl;
import org.hibernate.generator.Generator;
import org.hibernate.id.NativeGenerator;
import org.hibernate.mapping.GeneratorSettings;
import org.hibernate.mapping.SimpleValue;

/**
 * Shared helpers for the snapshot generators that inspect identifier generators.
 */
final class IdentifierGeneratorSupport {

    private IdentifierGeneratorSupport() {
    }

    /**
     * For annotation-based entities an identifier without {@code @GeneratedValue} is application-assigned, so no
     * generator applies. XML-mapped entities have no member details and declare their generator in the hbm.xml
     * mapping, so they always count as having generation intent.
     */
    static boolean hasGenerationIntent(SimpleValue simpleValue) {
        var memberDetails = simpleValue.getMemberDetails();
        return memberDetails == null ||
                memberDetails.hasDirectAnnotationUsage(jakarta.persistence.GeneratedValue.class);
    }

    /**
     * Hibernate does not expose the generator a {@link NativeGenerator} delegates to, so it is read reflectively.
     *
     * @return the delegate, or {@code null} if it cannot be resolved
     */
    static Generator nativeDelegate(NativeGenerator nativeGen) {
        try {
            var field = NativeGenerator.class.getDeclaredField("dialectNativeGenerator");
            field.setAccessible(true);
            return (Generator) field.get(nativeGen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Scope.getCurrentScope().getLog(IdentifierGeneratorSupport.class)
                    .fine("Could not access NativeGenerator delegate", e);
            return null;
        }
    }

    static GeneratorSettings createGeneratorSettings(SimpleValue simpleValue) {
        var buildingContext = simpleValue.getBuildingContext();
        return new GeneratorSettings() {
            @Override
            public String getDefaultCatalog() {
                return null;
            }

            @Override
            public String getDefaultSchema() {
                return null;
            }

            @Override
            public SqlStringGenerationContext getSqlStringGenerationContext() {
                var db = buildingContext.getMetadataCollector().getDatabase();
                return SqlStringGenerationContextImpl.fromExplicit(
                        db.getJdbcEnvironment(), db, getDefaultCatalog(), getDefaultSchema());
            }
        };
    }
}

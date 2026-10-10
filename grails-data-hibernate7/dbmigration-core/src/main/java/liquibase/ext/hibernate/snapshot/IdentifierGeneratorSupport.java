package liquibase.ext.hibernate.snapshot;

import liquibase.Scope;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.QualifiedName;
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
     * Uses the configured creator when available, otherwise checks explicit annotation or mapping intent.
     */
    static boolean hasGenerationIntent(SimpleValue simpleValue) {
        var creator = simpleValue.getCustomIdGeneratorCreator();
        if (creator != null) {
            return !creator.isAssigned();
        }
        var memberDetails = simpleValue.getMemberDetails();
        return memberDetails == null ||
                memberDetails.hasDirectAnnotationUsage(jakarta.persistence.GeneratedValue.class) ||
                memberDetails.hasDirectAnnotationUsage(org.hibernate.annotations.NativeGenerator.class);
    }

    static SequenceKey sequenceKey(QualifiedName name) {
        return new SequenceKey(canonicalName(name.getCatalogName()), canonicalName(name.getSchemaName()),
                canonicalName(name.getObjectName()));
    }

    private static String canonicalName(Identifier identifier) {
        return identifier == null ? null : identifier.getCanonicalName();
    }

    record SequenceKey(String catalog, String schema, String name) {
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

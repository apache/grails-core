package liquibase.ext.hibernate.snapshot;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import liquibase.Scope;
import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Sequence;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.id.NativeGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;

/**
 * Snapshots sequences from Hibernate metadata, including sequences managed by {@code @NativeGenerator} or
 * {@code SequenceStyleGenerator} identifiers that are not registered in the relational namespace. This class also
 * prevents the default SequenceSnapshotGenerator from running.
 */
public class HibernateSequenceSnapshotGenerator extends HibernateSnapshotGenerator {

    public HibernateSequenceSnapshotGenerator() {
        super(Sequence.class, Schema.class);
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        return example;
    }

    @Override
    @SuppressWarnings("PMD.CloseResource")
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Sequence.class)) {
            return;
        }

        if (foundObject instanceof Schema schema) {
            HibernateDatabase database = (HibernateDatabase) snapshot.getDatabase();
            Set<String> addedSequences = new HashSet<>();

            for (org.hibernate.boot.model.relational.Namespace namespace :
                    database.getMetadata().getDatabase().getNamespaces()) {
                for (org.hibernate.boot.model.relational.Sequence sequence : namespace.getSequences()) {
                    String name = sequence.getName().getSequenceName().getText();
                    schema.addDatabaseObject(new Sequence()
                            .setName(name)
                            .setSchema(schema)
                            .setStartValue(BigInteger.valueOf(sequence.getInitialValue()))
                            .setIncrementBy(BigInteger.valueOf(sequence.getIncrementSize())));
                    addedSequences.add(name.toLowerCase(Locale.ROOT));
                }
            }

            addGeneratorSequences(database, schema, addedSequences);
        }
    }

    private void addGeneratorSequences(HibernateDatabase database, Schema schema, Set<String> addedSequences) {
        MetadataImplementor metadata = (MetadataImplementor) database.getMetadata();
        var dialect = database.getDialect();

        for (PersistentClass entityBinding : metadata.getEntityBindings()) {
            if (!(entityBinding instanceof RootClass rootClass) ||
                    !(rootClass.getIdentifier() instanceof SimpleValue simpleValue) ||
                    !IdentifierGeneratorSupport.hasGenerationIntent(simpleValue)) {
                continue;
            }

            try {
                var generator = simpleValue.createGenerator(
                        dialect,
                        rootClass,
                        rootClass.getIdentifierProperty(),
                        IdentifierGeneratorSupport.createGeneratorSettings(simpleValue));

                SequenceStyleGenerator seqGen = null;
                // NativeGenerator may wrap a SequenceStyleGenerator delegate depending on the dialect.
                if (generator instanceof NativeGenerator nativeGen) {
                    if (IdentifierGeneratorSupport.nativeDelegate(nativeGen) instanceof SequenceStyleGenerator s) {
                        seqGen = s;
                    }
                } else if (generator instanceof SequenceStyleGenerator s) {
                    seqGen = s;
                }

                if (seqGen != null) {
                    var structure = seqGen.getDatabaseStructure();
                    if (structure != null && structure.getPhysicalName() != null) {
                        String name = structure.getPhysicalName().render();
                        if (addedSequences.add(name.toLowerCase(Locale.ROOT))) {
                            schema.addDatabaseObject(new Sequence()
                                    .setName(name)
                                    .setSchema(schema)
                                    .setStartValue(BigInteger.valueOf(structure.getInitialValue()))
                                    .setIncrementBy(BigInteger.valueOf(structure.getIncrementSize())));
                        }
                    }
                }
            } catch (RuntimeException e) {
                Scope.getCurrentScope().getLog(getClass())
                        .fine("Could not resolve generator for " + rootClass.getEntityName(), e);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {liquibase.snapshot.jvm.SequenceSnapshotGenerator.class};
    }
}

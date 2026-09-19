package org.cassandraunit.test.assertion;

import com.datastax.oss.driver.api.core.CqlSession;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.assertion.DataSetMismatchError;
import org.cassandraunit.assertion.Difference;
import org.cassandraunit.assertion.ExpectedDataSetFactory;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The same assertions without the annotation - useful when the expectation is chosen at runtime,
 * or when you are not on a framework cassandra-unit integrates with.
 *
 * <p>{@link ExpectedDataSetFactory} builds an immutable {@code ExpectedDataSet}; every option is a
 * method rather than an annotation attribute, and {@code verify(session)} runs the comparison.
 */
class ExpectedDataSetFluentTest {

    private static final String KEYSPACE = "fluentassertionkeyspace";

    @RegisterExtension
    static CassandraUnitExtension cassandra = new CassandraUnitExtension(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/assertionSchema.cql", "rows/assertion-data.yaml"));

    @Test
    void verifies_a_dataset_against_the_database(CqlSession session) {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                + "where id = 11111111-1111-1111-1111-111111111111");

        ExpectedDataSetFactory.fromClassPath("rows/expected-widget.yaml", KEYSPACE)
                .ignoringColumns("created")
                .verify(session);
    }

    /** Every annotation attribute has a method here, and they compose. */
    @Test
    void the_options_compose(CqlSession session) {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                + "where id = 11111111-1111-1111-1111-111111111111");
        session.execute("insert into " + KEYSPACE + ".widget (id, label) "
                + "values (44444444-4444-4444-4444-444444444444, 'new arrival')");

        ExpectedDataSetFactory.fromClassPath("rows/expected-widget-contains.yaml", KEYSPACE)
                .containing()
                .verify(session);
    }

    /**
     * A mismatch is a {@link DataSetMismatchError}, which is an {@code AssertionError} - the code
     * under test is wrong, and a test engine reports it as a failure.
     *
     * <p>The message renders values as CQL literals and echoes the {@code SELECT} that was run, so
     * it pastes into {@code cqlsh}. {@link DataSetMismatchError#getDifferences()} gives the same
     * information as data, split by cause: a <b>missing</b> row means a write did not happen, an
     * <b>unexpected</b> one means a write happened that should not have, and a <b>different</b>
     * value means a write landed with the wrong content.
     */
    @Test
    void a_mismatch_is_an_assertion_error_carrying_the_differences(CqlSession session) {
        // The expectation says this widget should read 'shipped'. Nothing shipped it.
        DataSetMismatchError error = catchThrowableOfType(
                DataSetMismatchError.class,
                () -> ExpectedDataSetFactory.fromClassPath("rows/expected-widget.yaml", KEYSPACE)
                        .ignoringColumns("created")
                        .verify(session));

        assertThat(error).isNotNull();
        assertThat(error).isInstanceOf(AssertionError.class);
        assertThat(error).hasMessageContaining(KEYSPACE + ".widget");

        List<Difference> differences = error.getDifferences();
        assertThat(differences).hasSize(1);
        assertThat(differences.get(0).kind()).isEqualTo(Difference.Kind.VALUE);
        assertThat(differences.get(0).column()).isEqualTo("label");
        assertThat(differences.get(0).expected()).contains("shipped");
        assertThat(differences.get(0).actual()).contains("ordered");
    }

    /**
     * An unusable expectation is a different thing: a {@code ParseException}, reported as an
     * error rather than a failure. Getting that backwards sends someone hunting through
     * production code for a typo in a fixture.
     *
     * <p>A {@code .cql} script is not an expectation - there is nothing in it to compare.
     */
    @Test
    void a_broken_expectation_is_an_error_not_a_failure(CqlSession session) {
        assertThatThrownBy(() -> ExpectedDataSetFactory.fromClassPath("cql/assertionSchema.cql", KEYSPACE)
                .verify(session))
                .isInstanceOf(org.cassandraunit.dataset.ParseException.class)
                .isNotInstanceOf(AssertionError.class);
    }
}

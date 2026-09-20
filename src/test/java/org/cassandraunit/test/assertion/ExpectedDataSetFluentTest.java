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
 * Expected-dataset assertions without the annotation: {@link ExpectedDataSetFactory} builds one,
 * every annotation attribute is a method, and {@code verify(session)} runs the comparison.
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

    @Test
    void a_mismatch_is_an_assertion_error_carrying_the_differences(CqlSession session) {
        // No update this time, so the expected 'shipped' will not match.
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

    /** A {@code .cql} script is not an expectation - there is nothing in it to compare. */
    @Test
    void a_broken_expectation_is_an_error_not_a_failure(CqlSession session) {
        assertThatThrownBy(() -> ExpectedDataSetFactory.fromClassPath("cql/assertionSchema.cql", KEYSPACE)
                .verify(session))
                .isInstanceOf(org.cassandraunit.dataset.ParseException.class)
                .isNotInstanceOf(AssertionError.class);
    }
}

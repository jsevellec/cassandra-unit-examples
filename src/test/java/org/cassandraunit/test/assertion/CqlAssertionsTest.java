package org.cassandraunit.test.assertion;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.assertion.DataSetMismatchError;
import org.cassandraunit.assertion.ExpectedDataSetFactory;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cassandraunit.assertion.CqlAssertions.assertThat;

/**
 * Fluent assertions on what the database actually holds, written in code rather than in a file.
 *
 * <p>Needs {@code assertj-core}, which is {@code optional} in cassandra-unit: declare it yourself.
 */
class CqlAssertionsTest {

    private static final String KEYSPACE = "cqlassertionskeyspace";
    private static final String ONE = "11111111-1111-1111-1111-111111111111";

    @RegisterExtension
    static CassandraUnitExtension cassandra = new CassandraUnitExtension(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/assertionSchema.cql", "rows/assertion-data.yaml"));

    @Test
    void asserts_one_value_without_writing_a_fixture(CqlSession session) {
        assertThat(session).keyspace(KEYSPACE)
                .table("widget")
                    .hasRowCount(3)
                    .row("id", ONE)                 // a uuid given as its string form
                        .hasValue("label", "ordered")
                        .hasValue("quantity", 1)    // an int against a bigint column
                        .hasNonNull("created");
    }

    @Test
    void asserts_what_exists(CqlSession session) {
        assertThat(session)
                .hasKeyspace(KEYSPACE)
                .doesNotHaveKeyspace("nosuchkeyspace")
                .keyspace(KEYSPACE)
                    .hasTable("widget")
                    .doesNotHaveTable("nosuchtable");
    }

    @Test
    void addresses_a_row_by_its_whole_key(CqlSession session) {
        assertThat(session).keyspace(KEYSPACE)
                .table("event")
                    .row(Map.of("day", "2026-09-19", "at", Instant.parse("2026-09-19T12:00:00Z")))
                        .hasValue("kind", "start");
    }

    /** {@code hasNoRow} needs the whole primary key, like {@code row} - a partial key throws. */
    @Test
    void asserts_over_the_rows_of_a_table(CqlSession session) {
        assertThat(session).keyspace(KEYSPACE)
                .table("event")
                    .isNotEmpty()
                    .hasNoRow(Map.of("day", "2026-09-21", "at", Instant.parse("2026-09-21T09:00:00Z")))
                    .rows()
                        .hasSize(3)
                        .extracting("kind")
                        .containsExactlyInAnyOrder("start", "retry", "start");
    }

    @Test
    void asserts_on_a_row_you_already_have(CqlSession session) {
        Row row = session.execute("select * from " + KEYSPACE + ".widget where id = " + ONE).one();

        assertThat(row)
                .hasValues(Map.of("label", "ordered", "quantity", 1))
                .hasNonNull("created");
    }

    /** A whole expected dataset: mismatch is a {@link DataSetMismatchError}. */
    @Test
    void hands_a_whole_expected_dataset_to_the_chain(CqlSession session) {
        assertThat(session).keyspace(KEYSPACE)
                .matches(ExpectedDataSetFactory.fromClassPath("rows/assertion-data.yaml", KEYSPACE));
    }

    @Test
    void reports_the_column_that_is_wrong(CqlSession session) {
        assertThatThrownBy(() -> assertThat(session).keyspace(KEYSPACE)
                .table("widget")
                    .row("id", ONE)
                        .hasValue("label", "shipped"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("label");
    }
}

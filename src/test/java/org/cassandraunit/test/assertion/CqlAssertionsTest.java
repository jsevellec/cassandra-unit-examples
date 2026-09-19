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
 * <p>The companion to {@code @ExpectedCassandraDataSet}, not a replacement for it. A dataset file
 * is the right tool for "these are <em>all</em> the rows this table should hold"; this is the right
 * tool for one value or one row count, where a file would be out of proportion.
 *
 * <p>Both agree on what equal means - they share the same value comparison - so a {@code set}
 * column reading back empty rather than null, or {@code 1.50} against {@code 1.5}, behaves
 * identically whichever you use. Expected values accept the same forms a row dataset accepts, so
 * {@code hasValue("quantity", 1)} works against a {@code bigint} and a {@code uuid} can be given as
 * its string form.
 *
 * <p><b>Needs {@code assertj-core}</b>, which is {@code optional} in cassandra-unit so it reaches
 * nobody who does not ask for it. Every assert type extends AssertJ's {@code AbstractAssert}, so
 * {@code as()}, {@code satisfies()} and {@code SoftAssertions} work as usual. Import
 * {@code CqlAssertions.assertThat} - the overloads take driver types, so it sits beside AssertJ's
 * own static import without ambiguity.
 *
 * @see ExpectedCassandraDataSetAnnotationTest for the file-shaped half
 */
class CqlAssertionsTest {

    private static final String KEYSPACE = "cqlassertionskeyspace";
    private static final String ONE = "11111111-1111-1111-1111-111111111111";

    @RegisterExtension
    static CassandraUnitExtension cassandra = new CassandraUnitExtension(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/assertionSchema.cql", "rows/assertion-data.yaml"));

    /** The whole chain: session, keyspace, table, row, column. */
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

    /** Schema-shaped checks, before any row is looked at. */
    @Test
    void asserts_what_exists(CqlSession session) {
        assertThat(session)
                .hasKeyspace(KEYSPACE)
                .doesNotHaveKeyspace("nosuchkeyspace")
                .keyspace(KEYSPACE)
                    .hasTable("widget")
                    .doesNotHaveTable("nosuchtable");
    }

    /**
     * A row is addressed by its primary key, so a compound key is a map - here the partition key
     * and the clustering column of the {@code event} table.
     */
    @Test
    void addresses_a_row_by_its_whole_key(CqlSession session) {
        assertThat(session).keyspace(KEYSPACE)
                .table("event")
                    .row(Map.of("day", "2026-09-19", "at", Instant.parse("2026-09-19T12:00:00Z")))
                        .hasValue("kind", "start");
    }

    /**
     * Whole-table shape, and AssertJ's own list assertions once you extract a column.
     *
     * <p>{@code hasNoRow} addresses a row the same way {@code row} does, so on a table with a
     * clustering column it needs the whole primary key - naming only the partition would be a
     * {@code ParseException}, not a passing assertion.
     */
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

    /** A row you already fetched yourself, for when the query is the interesting part. */
    @Test
    void asserts_on_a_row_you_already_have(CqlSession session) {
        Row row = session.execute("select * from " + KEYSPACE + ".widget where id = " + ONE).one();

        assertThat(row)
                .hasValues(Map.of("label", "ordered", "quantity", 1))
                .hasNonNull("created");
    }

    /**
     * The bridge between the two halves: a keyspace can be asserted against a whole expected
     * dataset from inside the fluent chain, which fails as a
     * {@link DataSetMismatchError} exactly as the annotation would.
     */
    @Test
    void hands_a_whole_expected_dataset_to_the_chain(CqlSession session) {
        assertThat(session).keyspace(KEYSPACE)
                .matches(ExpectedDataSetFactory.fromClassPath("rows/assertion-data.yaml", KEYSPACE));
    }

    /** And when it does not hold, the failure names the column. */
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

package org.cassandraunit.test.assertion;

import com.datastax.oss.driver.api.core.CqlSession;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.assertion.ExpectedCassandraDataSet;
import org.cassandraunit.assertion.MatchMode;
import org.cassandraunit.assertion.Scope;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Asserting what the database holds <em>after</em> a test, with {@link ExpectedCassandraDataSet}.
 *
 * <p>A dataset file can state the expectation as well as the setup - the load rules and the assert
 * rules are deliberately identical, so one file can do both jobs. A column absent from a row is
 * not asserted; a column present with {@code null} asserts that it reads back as null.
 *
 * <p>Nothing has to be wired up here: {@link CassandraUnitExtension} picks the annotation up. The
 * expectation is verified after the test method, and <b>only if the test passed</b> - an
 * expectation that fired after a failure would bury the real error under a second one.
 *
 * <p><b>Strict by default:</b> a table the file names must hold exactly the rows it lists, and a
 * table it does not name is not asserted at all. That is stricter than DBUnit's usual default, on
 * purpose: Cassandra is upsert-only and has no unique constraints, so the bug an integration test
 * most needs to catch is a write landing in the wrong partition or under the wrong clustering key -
 * which produces an <em>extra</em> row, invisible to a contains-style assertion.
 *
 * <p>Rows are matched on the primary key, and only the columns the file mentions are selected -
 * never {@code SELECT *}, never {@code ALLOW FILTERING}. A mismatch is a
 * {@link org.cassandraunit.assertion.DataSetMismatchError}, an {@code AssertionError}: the code
 * under test is wrong. An unusable expectation - unknown column, a row missing part of its key - is
 * a {@code ParseException}: the test is wrong. Test engines report the first as a failure and the
 * second as an error, which is the right way round.
 *
 * @see ExpectedDataSetFluentTest for the same thing without the annotation
 */
class ExpectedCassandraDataSetAnnotationTest {

    private static final String KEYSPACE = "assertionkeyspace";

    @RegisterExtension
    static CassandraUnitExtension cassandra = new CassandraUnitExtension(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/assertionSchema.cql", "rows/assertion-data.yaml"));

    /**
     * The normal case. The expectation lists all three widgets; this test changes one of them.
     *
     * <p>{@code created} is in the file but ignored: it is the timestamp the code under test
     * wrote, and there is no value it could be expected to equal. An ignored column is not
     * compared and not even selected - so an unrelated column of a type the driver cannot decode
     * can never break an assertion. A primary-key column cannot be ignored; it is how rows match.
     */
    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-widget.yaml", keyspace = KEYSPACE,
            ignoreColumns = "created")
    void shipping_a_widget_changes_its_label(CqlSession session) {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped', created = toTimestamp(now()) "
                + "where id = 11111111-1111-1111-1111-111111111111");
    }

    /**
     * {@link MatchMode#CONTAINS} allows rows the file does not list. The file names one widget;
     * this test also inserts a fourth, which strict matching would report as unexpected.
     *
     * <p>The usual reason to relax it is a suite that pre-seeds reference data the fixture does
     * not describe.
     */
    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-widget-contains.yaml", keyspace = KEYSPACE,
            mode = MatchMode.CONTAINS)
    void contains_ignores_rows_the_expectation_does_not_list(CqlSession session) {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                + "where id = 11111111-1111-1111-1111-111111111111");
        session.execute("insert into " + KEYSPACE + ".widget (id, label) "
                + "values (44444444-4444-4444-4444-444444444444, 'new arrival')");
    }

    /**
     * {@link Scope#MENTIONED_PARTITIONS} narrows the assertion to the partitions the file names,
     * while staying strict inside each of them. Here the {@code 2026-09-19} partition must match
     * exactly; the {@code 2026-09-20} partition this test writes to is not looked at.
     *
     * <p>{@code checkClusteringOrder} additionally asserts the order of rows within a partition -
     * off by default. Across partitions, order is never compared and that is not configurable: an
     * unrestricted {@code SELECT} returns partition-token order, which is stable but meaningless
     * to whoever wrote the fixture.
     */
    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-event-partition.yaml", keyspace = KEYSPACE,
            scope = Scope.MENTIONED_PARTITIONS, checkClusteringOrder = true)
    void a_write_to_another_partition_is_out_of_scope(CqlSession session) {
        session.execute("insert into " + KEYSPACE + ".event (day, at, kind) "
                + "values ('2026-09-20', '2026-09-20T11:00:00Z', 'retry')");
    }

    /** Several expectations are verified in order, and a test may of course assert nothing else. */
    @Test
    @ExpectedCassandraDataSet(value = {"rows/expected-widget.yaml", "rows/expected-event-partition.yaml"},
            keyspace = KEYSPACE, ignoreColumns = "created", scope = Scope.MENTIONED_PARTITIONS)
    void verifies_every_listed_file(CqlSession session) {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                + "where id = 11111111-1111-1111-1111-111111111111");
    }
}

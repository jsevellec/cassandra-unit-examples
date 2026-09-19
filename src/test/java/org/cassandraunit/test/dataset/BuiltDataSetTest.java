package org.cassandraunit.test.dataset;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.assertion.ExpectedDataSetFactory;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.cassandraunit.dataset.CompositeCQLDataSet;
import org.cassandraunit.dataset.ParseException;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.dataset.rows.RowsCQLDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A dataset written in Java - the sixth format, and the only one with no file.
 *
 * <p>For three rows, a file is a file's worth of ceremony, and it puts the fixture somewhere other
 * than the test that depends on it. {@link CQLDataSetFactory#builder(String)} keeps it next to the
 * test.
 *
 * <p>It is <em>not</em> a second way of loading rows. {@code build()} returns an ordinary
 * {@link RowsCQLDataSet}, so from there on everything is the code a file goes through: the column
 * types still come from the live schema, the values still bind through prepared statements, and the
 * rule, the extensions and {@code CQLDataLoader} all take it as they take any dataset. The fixture
 * below is {@code rows/assertion-data.yaml} rewritten in code, and
 * {@link #rows_built_in_code_satisfy_the_expectation_written_as_a_file} checks exactly that.
 *
 * <p>What the builder can do that a file cannot is hand over the object you already have - a
 * {@link UUID}, an {@link Instant}, a {@link java.util.Set} - instead of its string form.
 */
class BuiltDataSetTest {

    private static final String KEYSPACE = "builtdatasetkeyspace";

    private static final UUID ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant ORDERED_AT = Instant.parse("2026-09-19T10:00:00Z");

    /**
     * {@code named} is what the dataset calls itself in error and failure messages, where a file
     * would show its path. Without it the messages say "a dataset built in code", which is honest
     * and unhelpful once a suite has two.
     *
     * <p>Keyspace creation and deletion default to <b>off</b> here, unlike the file factories: a
     * builder describes rows and never schema, so dropping the keyspace would destroy the tables
     * its own inserts need.
     */
    private static final RowsCQLDataSet FIXTURE = CQLDataSetFactory.builder(KEYSPACE)
            .named("the widget fixture, built in code")
            .table("widget").columns("id", "label", "quantity", "created")
                .row(ONE, "ordered", 1, ORDERED_AT)
                .row(TWO, "ordered", 2, ORDERED_AT)
                .row(THREE, "ordered", 3, ORDERED_AT)
            .table("event").columns("day", "at", "kind")
                .row("2026-09-19", Instant.parse("2026-09-19T12:00:00Z"), "start")
                .row("2026-09-19", Instant.parse("2026-09-19T10:00:00Z"), "retry")
                .row("2026-09-20", Instant.parse("2026-09-20T09:00:00Z"), "start")
            .build();

    /**
     * A built dataset drives the extension like any other. It needs its schema first, and
     * {@code fromClassPathAll} chains <em>locations</em>, so the pairing is a
     * {@link CompositeCQLDataSet}: the schema script with both keyspace flags off, the built rows
     * after it, and the keyspace dropped and created once for the pair.
     */
    @RegisterExtension
    static final CassandraUnitExtension cassandra = new CassandraUnitExtension(
            new CompositeCQLDataSet(
                    List.of(new ClassPathCQLDataSet("cql/assertionSchema.cql", false, false, KEYSPACE), FIXTURE),
                    true, true, KEYSPACE));

    @Test
    void loads_java_values_against_the_real_column_types(CqlSession session) {
        Row row = session.execute("select * from " + KEYSPACE + ".widget where id = " + ONE).one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("ordered");
        assertThat(row.getLong("quantity")).isEqualTo(1L);   // an int in the builder, widened
        assertThat(row.getInstant("created")).isEqualTo(ORDERED_AT);
    }

    /**
     * The builder and the YAML parser produce the same thing, so the file can state the
     * expectation for rows that were built in code. If the two ever drifted, this would fail.
     */
    @Test
    void rows_built_in_code_satisfy_the_expectation_written_as_a_file(CqlSession session) {
        assertThatCode(() -> ExpectedDataSetFactory.fromClassPath("rows/assertion-data.yaml", KEYSPACE)
                .verify(session))
                .doesNotThrowAnyException();
    }

    /**
     * And the other direction: a row dataset read backwards is an expectation, so the same object
     * that set the table up can state what should still be true.
     *
     * <p>{@code ExpectedDataSetFactory.of} is typed on the concrete {@link RowsCQLDataSet}, which
     * is why {@code FIXTURE} is declared as that rather than as {@code CQLDataSet}.
     */
    @Test
    void the_same_object_can_state_the_expectation(CqlSession session) {
        ExpectedDataSetFactory.of(FIXTURE, KEYSPACE).verify(session);
    }

    /**
     * Rows in one table need not share a shape. The positional {@code row(...)} fills the columns
     * declared once for the table; {@code row(Map)} is for a row whose columns differ.
     *
     * <p>Absent and null are the two different things they are everywhere else in this library:
     * {@code quantity} is left out and keeps the value already in the table, while {@code label} is
     * explicitly null and is tombstoned over it. {@link Map#of} rejects null values, so an explicit
     * null needs a {@link LinkedHashMap} - or the positional form, which accepts one.
     */
    @Test
    void a_row_may_set_a_different_set_of_columns(CqlSession session) {
        Map<String, Object> nulledLabel = new LinkedHashMap<>();
        nulledLabel.put("id", ONE);
        nulledLabel.put("label", null);

        new CQLDataLoader(session).load(CQLDataSetFactory.builder(KEYSPACE)
                .named("the update, built in code")
                .table("widget")
                    .row(nulledLabel)
                .build());

        Row row = session.execute("select * from " + KEYSPACE + ".widget where id = " + ONE).one();

        assertThat(row.getString("label")).as("explicit null - tombstoned").isNull();
        assertThat(row.getLong("quantity")).as("absent - left alone").isEqualTo(1L);
    }

    /**
     * A malformed dataset raises {@link ParseException} <b>at the call that malformed it</b>,
     * rather than at {@code build()}, so the stack trace points at the row that is wrong.
     *
     * <p>That is the same split the rest of the library keeps: a dataset that cannot be used at all
     * means the test is wrong and is reported as an error, while data that does not match means the
     * code under test is wrong and is reported as a failure.
     */
    @Test
    void a_row_that_does_not_fit_its_columns_fails_where_it_is_written() {
        assertThatThrownBy(() -> CQLDataSetFactory.builder(KEYSPACE)
                .named("a fixture with a bad row")
                .table("widget").columns("id", "label", "quantity", "created")
                    .row(ONE, "ordered"))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("2 values but 4 columns were declared");
    }
}

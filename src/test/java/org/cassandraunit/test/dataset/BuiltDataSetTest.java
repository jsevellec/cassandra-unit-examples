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
 * A dataset written in Java: {@link CQLDataSetFactory#builder(String)} returns an ordinary
 * {@link RowsCQLDataSet}, loaded like any file-based one, but it takes the object you already have
 * - a {@link UUID}, an {@link Instant} - instead of its string form.
 */
class BuiltDataSetTest {

    private static final String KEYSPACE = "builtdatasetkeyspace";

    private static final UUID ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant ORDERED_AT = Instant.parse("2026-09-19T10:00:00Z");

    /**
     * Keyspace creation and deletion default to off, unlike the file factories: a builder
     * describes rows, never schema.
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
     * The built rows need their schema first, and {@code fromClassPathAll} chains locations
     * only - hence the composite.
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

    @Test
    void rows_built_in_code_satisfy_the_expectation_written_as_a_file(CqlSession session) {
        assertThatCode(() -> ExpectedDataSetFactory.fromClassPath("rows/assertion-data.yaml", KEYSPACE)
                .verify(session))
                .doesNotThrowAnyException();
    }

    /**
     * {@code ExpectedDataSetFactory.of} is typed on {@link RowsCQLDataSet}, which is why
     * {@code FIXTURE} is declared as one.
     */
    @Test
    void the_same_object_can_state_the_expectation(CqlSession session) {
        ExpectedDataSetFactory.of(FIXTURE, KEYSPACE).verify(session);
    }

    /** A {@link LinkedHashMap} rather than {@link Map#of}, which rejects null values. */
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

    /** {@link ParseException} is raised at the offending call, not at {@code build()}. */
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

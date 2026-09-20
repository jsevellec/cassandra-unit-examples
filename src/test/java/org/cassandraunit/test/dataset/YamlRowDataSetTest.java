package org.cassandraunit.test.dataset;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.ByteUtils;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A row dataset: fixture data as a list of rows instead of a CQL script.
 *
 * <p>A row dataset is data only and needs the schema to already exist, so
 * {@link CQLDataSetFactory#fromClassPathAll(String, String...)} chains the script and the rows
 * into the one dataset the extension takes.
 */
class YamlRowDataSetTest {

    private static final String KEYSPACE = "rowsyamlkeyspace";

    @RegisterExtension
    static CassandraUnitExtension cassandra = new CassandraUnitExtension(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/widgetSchema.cql",
                    "cql/preexistingWidget.cql",   // a row already there before the fixture loads
                    "rows/widget.yaml"));

    @Test
    void converts_each_value_against_the_real_column_type(CqlSession session) {
        Row row = widget(session, "11111111-1111-1111-1111-111111111111");

        assertThat(row.getString("label")).isEqualTo("1");   // the text "1", not the number 1
        assertThat(row.getSet("tags", String.class)).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(row.getInstant("created")).isEqualTo(Instant.parse("2026-09-19T10:00:00Z"));
        assertThat(row.getLong("quantity")).isEqualTo(42L);  // an Integer in the file, widened
        assertThat(row.getMap("props", String.class, Integer.class))
                .containsEntry("a", 1)
                .containsEntry("b", 2);
        assertThat(ByteUtils.toHexString(row.getByteBuffer("payload"))).isEqualTo("0x0a0b0c");
    }

    /** A timestamp may be an ISO-8601 string, epoch millis, or an unquoted YAML date. */
    @Test
    void accepts_a_timestamp_as_epoch_millis(CqlSession session) {
        Row row = widget(session, "22222222-2222-2222-2222-222222222222");

        assertThat(row.getString("label")).isEqualTo("it's fine");
        assertThat(row.getInstant("created")).isEqualTo(Instant.ofEpochMilli(1758276000000L));
    }

    @Test
    void an_explicit_null_writes_a_tombstone(CqlSession session) {
        Row row = widget(session, "33333333-3333-3333-3333-333333333333");

        assertThat(row.getString("label")).isNull();
    }

    /** The row is preloaded by {@code cql/preexistingWidget.cql} with a label and no tags. */
    @Test
    void an_absent_column_leaves_the_existing_value_alone(CqlSession session) {
        Row row = widget(session, "00000000-0000-0000-0000-000000000009");

        assertThat(row.getString("label")).isEqualTo("preexisting");
        assertThat(row.getSet("tags", String.class)).containsExactly("kept");
    }

    private static Row widget(CqlSession session, String id) {
        Row row = session
                .execute("select * from " + KEYSPACE + ".widget where id = " + id)
                .one();

        assertThat(row).as("widget %s", id).isNotNull();
        return row;
    }
}

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
 * <p>The two kinds of dataset split the work. A CQL script creates the schema and can do anything
 * CQL can do; a row dataset describes <em>data only</em> and needs the schema to already be there.
 * That split is what makes the values right: because the table exists when the rows load, the
 * loader reads each column's real type from the database and the driver's own codecs convert, so
 * nothing in the file has to be written as a CQL literal - and a {@code text} column holding
 * {@code "1"} stays the string {@code "1"}.
 *
 * <p>The format is chosen by <b>file extension</b> - {@code .yaml}, {@code .yml}, {@code .json},
 * {@code .xml}, {@code .csv}. There is no {@code type} attribute to keep in sync with the filename.
 *
 * <p>{@link CQLDataSetFactory#fromClassPathAll(String, String...)} chains the files into one
 * dataset, which is the only way to give a row dataset its schema through the extension: the
 * extension takes exactly one dataset. The keyspace is dropped and created <b>once</b>, for the
 * chain, and the members then load into it in order - doing it by hand is where people drop the
 * keyspace they just populated.
 *
 * @see RowDataSetFormatsTest for the same rows in JSON, XML and CSV
 */
class YamlRowDataSetTest {

    private static final String KEYSPACE = "rowsyamlkeyspace";

    @RegisterExtension
    static CassandraUnitExtension cassandra = new CassandraUnitExtension(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/widgetSchema.cql",        // the schema
                    "cql/preexistingWidget.cql",   // a row that is there before the fixture loads
                    "rows/widget.yaml"));          // the fixture

    /** Every type in the file arrives as the Java type the driver decodes that column to. */
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

    /**
     * {@code label: null} in the file writes a tombstone - it erases whatever was there.
     *
     * @see #an_absent_column_leaves_the_existing_value_alone
     */
    @Test
    void an_explicit_null_writes_a_tombstone(CqlSession session) {
        Row row = widget(session, "33333333-3333-3333-3333-333333333333");

        assertThat(row.getString("label")).isNull();
    }

    /**
     * The other half of the same rule, and the one that is easy to get wrong: a column <em>absent
     * from a row</em> is not written at all, so an existing value survives the load.
     *
     * <p>This row was inserted by {@code cql/preexistingWidget.cql} with a label and no tags; the
     * fixture then gives it tags and says nothing about the label. Both are true afterwards.
     *
     * <p>The difference is only visible when the column already had a value - on an empty table an
     * unset column and a tombstoned one read back identically.
     */
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

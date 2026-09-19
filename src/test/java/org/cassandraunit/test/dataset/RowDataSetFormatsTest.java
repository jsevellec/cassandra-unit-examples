package org.cassandraunit.test.dataset;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.ByteUtils;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.cassandraunit.utils.CqlOperations;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same rows in JSON, XML and CSV - the format comes from the file extension and nothing else.
 *
 * <p>All formats share one model (tables, rows, values), so the rules that matter are the same in
 * every one of them: an absent column is unset, values convert against the real column type, and a
 * single bare value counts as a collection of one. Only the syntax differs.
 *
 * <p><b>CSV is the exception worth knowing.</b> It is flat, so one file is one table, named after
 * the file - {@code rows/widget.csv} loads into {@code widget}. Collections are split on {@code |}.
 * And an empty field means <em>unset</em>: CSV has no way to say "explicitly null", deliberately,
 * because a {@code text} column can legitimately hold the string {@code NULL} and a sentinel would
 * corrupt it silently. {@link #csv_cannot_express_a_null_so_an_empty_field_is_unset()} is that
 * difference, demonstrated.
 *
 * <p>CSV also needs {@code com.fasterxml.jackson.dataformat:jackson-dataformat-csv} on the test
 * classpath. It is {@code optional} in cassandra-unit, so YAML, JSON and XML cost nothing; loading
 * a {@code .csv} without it fails immediately with a message saying exactly that.
 *
 * <p>This example drives {@link CQLDataLoader} directly rather than through an extension, because
 * each test loads a different file into a keyspace whose schema is built once.
 */
class RowDataSetFormatsTest {

    private static final String KEYSPACE = "rowsformatskeyspace";
    private static final String FULL_ROW = "11111111-1111-1111-1111-111111111111";
    private static final String PREEXISTING_ROW = "00000000-0000-0000-0000-000000000009";

    private static CqlSession session;
    private static CQLDataLoader loader;

    @BeforeAll
    static void startCassandraAndCreateSchema() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        session = EmbeddedCassandraServerHelper.getSession();
        loader = new CQLDataLoader(session);

        // Loads only if the keyspace is not already there, and says whether it did.
        loader.loadIfKeyspaceAbsent(CQLDataSetFactory.fromClassPath("cql/widgetSchema.cql", KEYSPACE));
    }

    /**
     * Back to a known state without rebuilding the schema: empty every table, then put the one
     * pre-existing row back. {@code truncateKeyspace} is public API as of 5.1.0.
     */
    @BeforeEach
    void resetTheKeyspace() {
        CqlOperations.truncateKeyspace(session, KEYSPACE);

        // keyspaceCreation and keyspaceDeletion both false: this dataset must not drop the
        // keyspace whose schema was built once in @BeforeAll.
        loader.load(CQLDataSetFactory.fromClassPath("cql/preexistingWidget.cql", false, false, KEYSPACE));
    }

    @Test
    void loads_a_json_row_dataset() {
        loader.load(CQLDataSetFactory.fromClassPath("rows/widget.json", false, false, KEYSPACE));

        assertTheFullRowConverted();
        assertThat(widget(PREEXISTING_ROW).getString("label"))
                .as("label: null wrote a tombstone over 'preexisting'")
                .isNull();
    }

    @Test
    void loads_an_xml_row_dataset() {
        loader.load(CQLDataSetFactory.fromClassPath("rows/widget.xml", false, false, KEYSPACE));

        assertTheFullRowConverted();
        assertThat(widget(PREEXISTING_ROW).getString("label"))
                .as("<label null=\"true\"/> wrote a tombstone over 'preexisting'")
                .isNull();
    }

    @Test
    void loads_a_csv_row_dataset() {
        loader.load(CQLDataSetFactory.fromClassPath("rows/widget.csv", false, false, KEYSPACE));

        assertTheFullRowConverted();
    }

    /**
     * The same file position that holds {@code null} in JSON and XML is empty in the CSV, and an
     * empty field is unset - so the value already in the table survives, where the other two
     * formats erased it.
     *
     * <p>When you need a tombstone, use YAML or JSON.
     */
    @Test
    void csv_cannot_express_a_null_so_an_empty_field_is_unset() {
        loader.load(CQLDataSetFactory.fromClassPath("rows/widget.csv", false, false, KEYSPACE));

        assertThat(widget(PREEXISTING_ROW).getString("label")).isEqualTo("preexisting");
    }

    /** Identical in all three formats: the column type decides what each value becomes. */
    private void assertTheFullRowConverted() {
        Row row = widget(FULL_ROW);

        assertThat(row.getString("label")).isEqualTo("1");
        assertThat(row.getSet("tags", String.class)).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(row.getInstant("created")).isEqualTo(Instant.parse("2026-09-19T10:00:00Z"));
        assertThat(row.getLong("quantity")).isEqualTo(42L);
        assertThat(ByteUtils.toHexString(row.getByteBuffer("payload"))).isEqualTo("0x0a0b0c");
    }

    private static Row widget(String id) {
        Row row = session.execute("select * from " + KEYSPACE + ".widget where id = " + id).one();

        assertThat(row).as("widget %s", id).isNotNull();
        return row;
    }
}

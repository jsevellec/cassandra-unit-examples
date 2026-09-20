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
 * <p>CSV additionally needs {@code com.fasterxml.jackson.dataformat:jackson-dataformat-csv}, which
 * is {@code optional} in cassandra-unit; YAML, JSON and XML cost nothing extra.
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

        loader.loadIfKeyspaceAbsent(CQLDataSetFactory.fromClassPath("cql/widgetSchema.cql", KEYSPACE));
    }

    @BeforeEach
    void resetTheKeyspace() {
        CqlOperations.truncateKeyspace(session, KEYSPACE);

        // false, false: keyspace creation and deletion off, so the schema survives.
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

    /** Where JSON and XML hold null, the CSV field is empty - use YAML or JSON for a tombstone. */
    @Test
    void csv_cannot_express_a_null_so_an_empty_field_is_unset() {
        loader.load(CQLDataSetFactory.fromClassPath("rows/widget.csv", false, false, KEYSPACE));

        assertThat(widget(PREEXISTING_ROW).getString("label")).isEqualTo("preexisting");
    }

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

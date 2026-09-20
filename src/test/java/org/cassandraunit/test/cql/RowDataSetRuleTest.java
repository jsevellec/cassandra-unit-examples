package org.cassandraunit.test.cql;

import java.time.Instant;

import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.ByteUtils;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Row datasets are not a Jupiter feature: the same YAML the extension loads goes through the
 * JUnit 4 {@code @Rule}, because both take the same {@code CQLDataSet}. See
 * {@code org.cassandraunit.test.dataset.YamlRowDataSetTest} for the Jupiter twin.
 */
public class RowDataSetRuleTest {

    private static final String KEYSPACE = "rowsjunit4keyspace";

    @Rule
    public CassandraCQLUnit cassandra = new CassandraCQLUnit(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/widgetSchema.cql",
                    "cql/preexistingWidget.cql",
                    "rows/widget.yaml"));

    @Test
    public void converts_each_value_against_the_real_column_type() {
        Row row = widget("11111111-1111-1111-1111-111111111111");

        assertThat(row.getString("label")).isEqualTo("1");
        assertThat(row.getSet("tags", String.class)).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(row.getInstant("created")).isEqualTo(Instant.parse("2026-09-19T10:00:00Z"));
        assertThat(row.getLong("quantity")).isEqualTo(42L);
        assertThat(row.getMap("props", String.class, Integer.class)).containsEntry("a", 1);
        assertThat(ByteUtils.toHexString(row.getByteBuffer("payload"))).isEqualTo("0x0a0b0c");
    }

    @Test
    public void an_explicit_null_writes_a_tombstone() {
        assertThat(widget("33333333-3333-3333-3333-333333333333").getString("label")).isNull();
    }

    @Test
    public void an_absent_column_leaves_the_existing_value_alone() {
        Row row = widget("00000000-0000-0000-0000-000000000009");

        assertThat(row.getString("label")).isEqualTo("preexisting");
        assertThat(row.getSet("tags", String.class)).containsExactly("kept");
    }

    private Row widget(String id) {
        Row row = cassandra.session
                .execute("select * from " + KEYSPACE + ".widget where id = " + id)
                .one();

        assertThat(row).as("widget %s", id).isNotNull();
        return row;
    }
}

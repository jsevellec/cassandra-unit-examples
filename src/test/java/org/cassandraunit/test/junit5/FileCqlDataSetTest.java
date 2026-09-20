package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.dataset.cql.FileCQLDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FileCQLDataSet} reads a script from the filesystem rather than the classpath - here from
 * Maven's copy of {@code src/test/resources}, so the example needs no extra file.
 */
class FileCqlDataSetTest {

    @RegisterExtension
    static CassandraUnitExtension cassandra = new CassandraUnitExtension(
            new FileCQLDataSet("target/test-classes/simple.cql", true, true, "filedatasetkeyspace"));

    @Test
    void reads_a_dataset_from_a_path_on_disk(CqlSession session) {
        Row row = session.execute("select value from filedatasetkeyspace.mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }
}

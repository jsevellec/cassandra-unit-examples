package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.dataset.cql.FileCQLDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FileCQLDataSet} reads a script from the filesystem rather than the classpath - for
 * scripts that are generated at build time, or shipped with the application rather than the
 * tests.
 *
 * <p>Here the path points into {@code target/test-classes}, where Maven copies
 * {@code src/test/resources}, purely so the example needs no extra file.
 *
 * <p>Note the constructor sets are <em>not</em> identical to
 * {@link org.cassandraunit.dataset.cql.ClassPathCQLDataSet}: {@code FileCQLDataSet} has no
 * {@code (String, boolean keyspaceCreation, String keyspaceName)} overload. Use the four-arg
 * form when you need to name the keyspace.
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

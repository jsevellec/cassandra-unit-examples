package org.cassandraunit.test.junit6;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No extension: drive {@link EmbeddedCassandraServerHelper} and {@link CQLDataLoader} yourself.
 * {@code startEmbeddedCassandra()} is a no-op once one is running, so several test classes can
 * share a JVM.
 */
class EmbeddedCassandraManualStartTest {

    private static CqlSession session;

    @BeforeAll
    static void startCassandraAndLoadData() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();

        session = EmbeddedCassandraServerHelper.getSession();
        new CQLDataLoader(session).load(new ClassPathCQLDataSet("simple.cql", "keyspaceNameToCreate"));
    }

    @Test
    void the_server_reports_where_it_is_listening() {
        assertThat(EmbeddedCassandraServerHelper.getHost()).isNotBlank();
        assertThat(EmbeddedCassandraServerHelper.getNativeTransportPort()).isPositive();
        assertThat(EmbeddedCassandraServerHelper.getClusterName()).isEqualTo("Test Cluster");
    }

    @Test
    void the_dataset_was_loaded() {
        Row row = session.execute("select value from mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }
}

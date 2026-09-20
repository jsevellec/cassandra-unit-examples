package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recommended way to use cassandra-unit from JUnit 5 when you want the embedded server.
 * The dataset comes in through the constructor, so register it with a {@code static}
 * {@code @RegisterExtension} field rather than {@code @ExtendWith}.
 */
class CassandraUnitExtensionTest {

    @RegisterExtension
    static CassandraUnitExtension cassandra =
            new CassandraUnitExtension(new ClassPathCQLDataSet("simple.cql", "keyspaceNameToCreate"));

    /** The extension is a {@code ParameterResolver}, so a {@link CqlSession} parameter is injected. */
    @Test
    void reads_the_dataset_through_an_injected_session(CqlSession session) {
        Row row = session.execute("select value from mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }

    @Test
    void reads_the_dataset_through_the_extension() {
        Row row = cassandra.getSession().execute("select value from mytable where id = 'myKey02'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue02");
    }
}

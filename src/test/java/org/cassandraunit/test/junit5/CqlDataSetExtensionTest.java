package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recommended way to use cassandra-unit from JUnit 5.
 *
 * <p>{@link CassandraUnitExtension} has no no-arg constructor - the dataset has to come in
 * through it - so it is registered with {@code @RegisterExtension} rather than
 * {@code @ExtendWith}. Make the field {@code static}: the extension implements
 * {@code BeforeAllCallback}, and starting Cassandra once per class is the whole point.
 */
class CqlDataSetExtensionTest {

    @RegisterExtension
    static CassandraUnitExtension cassandra =
            new CassandraUnitExtension(new ClassPathCQLDataSet("simple.cql", "keyspaceNameToCreate"));

    /**
     * The extension is a {@code ParameterResolver}, so a {@link CqlSession} parameter on a
     * test method is injected for you. This is the tidiest of the two options.
     */
    @Test
    void reads_the_dataset_through_an_injected_session(CqlSession session) {
        Row row = session.execute("select value from mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }

    /** The same session is also reachable from the extension itself. */
    @Test
    void reads_the_dataset_through_the_extension() {
        Row row = cassandra.getSession().execute("select value from mytable where id = 'myKey02'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue02");
    }
}

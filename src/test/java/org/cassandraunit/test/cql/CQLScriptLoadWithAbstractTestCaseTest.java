package org.cassandraunit.test.cql;

import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.AbstractCassandraUnit4CQLTestCase;
import org.cassandraunit.dataset.CQLDataSet;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JUnit 4 inheritance variant: extend the base class and supply the dataset.
 *
 * <p>Unlike the {@code @Rule}, this one calls
 * {@code EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()} after every test method, so
 * each test starts from an empty server.
 */
public class CQLScriptLoadWithAbstractTestCaseTest extends AbstractCassandraUnit4CQLTestCase {

    @Override
    public CQLDataSet getDataSet() {
        return new ClassPathCQLDataSet("simple.cql", "keyspaceNameToCreate");
    }

    @Test
    public void should_have_started_and_execute_cql_script() {
        Row row = getSession().execute("select value from mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }
}

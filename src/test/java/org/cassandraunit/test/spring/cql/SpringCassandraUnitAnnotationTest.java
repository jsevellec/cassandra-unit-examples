package org.cassandraunit.test.spring.cql;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.spring.CassandraUnit;
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The short form: {@code @CassandraUnit} is a composed annotation that means
 * {@code @EmbeddedCassandra @CassandraDataSet}, both at their defaults.
 *
 * <p>Defaulting {@code @CassandraDataSet} means two things:
 * <ul>
 *   <li>the keyspace is {@code cassandra_unit_keyspace};</li>
 *   <li>the dataset is found by convention at
 *       {@code <package>/<SimpleName>-dataset.cql} on the classpath - here
 *       {@code org/cassandraunit/test/spring/cql/SpringCassandraUnitAnnotationTest-dataset.cql}.</li>
 * </ul>
 *
 * <p>{@link CassandraUnitDependencyInjectionTestExecutionListener} loads the dataset once per
 * test <em>instance</em> rather than once per method, and only cleans up after the class. Use
 * it when loading is expensive and the tests do not mutate the data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener.class)
@CassandraUnit
class SpringCassandraUnitAnnotationTest {

    @Configuration
    static class Config {
    }

    @Test
    void should_load_the_dataset_found_by_convention() {
        CqlSession session = EmbeddedCassandraServerHelper.getSession();

        Row row = session
                .execute("select label from cassandra_unit_keyspace.widget where id = 1").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("hello");
    }
}

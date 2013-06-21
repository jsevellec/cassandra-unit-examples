package org.cassandraunit.test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import me.prettyprint.cassandra.service.CassandraHostConfigurator;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.ddl.KeyspaceDefinition;
import me.prettyprint.hector.api.factory.HFactory;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.Before;
import org.junit.Test;

public class ManuallyStartWithMyOwnCassandraYaml {

	@Before
	public void before() throws TTransportException, IOException, InterruptedException, ConfigurationException {
		EmbeddedCassandraServerHelper.startEmbeddedCassandra("another-cassandra.yaml");
	}

	@Test
	public void shouldHaveAnEmbeddedCassandraStartOn9175Port() throws Exception {
		Cluster cluster = HFactory.getOrCreateCluster("TestCluster", new CassandraHostConfigurator("localhost:9175"));
		assertThat(cluster.getConnectionManager().getActivePools().size(), is(1));
		KeyspaceDefinition keyspaceDefinition = cluster.describeKeyspace("system");
		assertThat(keyspaceDefinition, notNullValue());
		assertThat(keyspaceDefinition.getReplicationFactor(), is(1));

	}

}

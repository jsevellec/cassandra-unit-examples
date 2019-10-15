package org.cassandraunit.test;

import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ManuallyStartWithMyOwnCassandraYaml {

//	@Before
//	public void before() throws TTransportException, IOException, InterruptedException, ConfigurationException {
//		EmbeddedCassandraServerHelper.startEmbeddedCassandra("another-cassandra.yaml");
//	}
//
//	@Test
//	public void shouldHaveAnEmbeddedCassandraStartOn9143Port() throws Exception {
//		com.datastax.oss.driver.api.core.CqlSession session = EmbeddedCassandraServerHelper.getSession();
//		Metadata metadata = session.getMetadata();
//		String systemKeyspaceName = metadata.getKeyspace("system").get().getName().toString();
//		assertThat(systemKeyspaceName,is("system"));
//		Node node = (Node) metadata.getNodes().values().toArray()[0];
//		assertThat(node.getEndPoint().toString(),is("localhost/127.0.0.1:9143"));
//	}

}

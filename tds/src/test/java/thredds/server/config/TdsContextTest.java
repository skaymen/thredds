package thredds.server.config;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import thredds.mock.web.MockTdsContextLoader;
import thredds.mock.web.TdsContentRootPath;

import java.util.Map;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/WEB-INF/applicationContext.xml"},loader=MockTdsContextLoader.class)
public class TdsContextTest {

	@Autowired
	private TdsContext tdsContext;

	@Test
	public void testInit() {
		System.out.printf("%s%n", tdsContext);
		//All the initialization was done
		//serverInfo, htmlConfig, wmsConfig are initialized by TdsConfigMapper after ThreddConfig reads the threddsServer.xml file
		assertNotNull( tdsContext.getServerInfo() );
		assertNotNull( tdsContext.getHtmlConfig() );
		assertNotNull( tdsContext.getWmsConfig() );
		
	}

	@Test
	public void testVersionRetrieval() {
		String stableKey = "stable";
		String devKey = "development";
		Map<String, String> latestVersionInfo = tdsContext.getLatestVersionInfo();
		// is not empty
		assert(!latestVersionInfo.isEmpty());
		// contains the stable key and the stable version is not empty
		assert(latestVersionInfo.containsKey(stableKey));
		assert(!latestVersionInfo.get(stableKey).isEmpty());
		// contains the dev key and the dev version is not empty
		assert(latestVersionInfo.containsKey(devKey));
		assert(!latestVersionInfo.get(devKey).isEmpty());
	}

}

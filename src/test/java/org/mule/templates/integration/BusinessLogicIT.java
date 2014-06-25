package org.mule.templates.integration;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.config.MuleProperties;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.modules.salesforce.bulk.EnrichedSaveResult;
import org.mule.modules.siebel.api.model.response.CreateResult;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.streaming.ConsumerIterator;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Prober;
import org.mule.templates.test.utils.PipelineSynchronizeListener;
import org.mule.transport.NullPayload;

import com.mulesoft.module.batch.BatchTestHelper;

/**
 * The objective of this class is to validate the correct behavior of the flows
 * for this Anypoint Tempalte that make calls to external systems.
 * 
 */
public class BusinessLogicIT extends AbstractTemplateTestCase {

	private static final String KEY_ID = "Id";
	private static final String KEY_FIRST_NAME = "First Name";	
	private static final String KEY_LAST_NAME = "Last Name";
	private static final String KEY_LAST_NAME_SF = "LastName";
	private static final String KEY_EMAIL = "Email Address";
	private static final String KEY_FIRST_NAME_SF = "FirstName";	
	private static final String KEY_EMAIL_SF = "Email";
	private static final String MAPPINGS_FOLDER_PATH = "./mappings";
	private static final String TEST_FLOWS_FOLDER_PATH = "./src/test/resources/flows/";
	private static final String MULE_DEPLOY_PROPERTIES_PATH = "./src/main/app/mule-deploy.properties";
	private static final String KEY_ACCOUNT = "Account";
	protected static final int TIMEOUT_SEC = 60;
	protected static final String TEMPLATE_NAME = "contact-migration";
	private String accountName = TEMPLATE_NAME + "-account-"+System.currentTimeMillis();
	
	protected SubflowInterceptingChainLifecycleWrapper retrieveContactFromSFFlow;
	private List<Map<String, Object>> createdContactsInA = new ArrayList<Map<String, Object>>(),
			createdContactsInB = new ArrayList<Map<String, Object>>();
	private BatchTestHelper helper;
	private Map<String, Object> account = new HashMap<String, Object>(); 
	
	@BeforeClass
	public static void init() {
		System.setProperty("page.size", "1000");
		System.setProperty("poll.frequencyMillis", "10000");
		System.setProperty("poll.startDelayMillis", "20000");
//		System.setProperty("watermark.default.expression", "#[groovy: new Date(System.currentTimeMillis() - 10000).format(\"M/d/y H:m:s\", TimeZone.getTimeZone('UTC'))]");
		System.setProperty("watermark.default.expression", "#[org.joda.time.format.DateTimeFormat.forPattern(\"M/d/y H:m:s\").parseDateTime(\"01/01/2010 00:00:00\")]");
		

	}
	
	@Before
	public void setUp() throws Exception {
		stopFlowSchedulers("triggerFlow");
		helper = new BatchTestHelper(muleContext);
		// Flow to retrieve accounts from target system after sync in g
		retrieveContactFromSFFlow = getSubFlow("selectContactSF1");
		retrieveContactFromSFFlow.initialise();
		createTestDataInSandBox();
	}

	@After
	public void tearDown() throws Exception {
		System.err.println("delete ");
		//deleteTestContactsFromSandBoxA();		
		//deleteTestContactsFromSandBoxB();
	}

	@Test
	public void testMainFlow() throws Exception {
		System.err.println("test ");
		runSchedulersOnce("triggerFlow");
		runFlow("mainFlow");
		
		// Wait for the batch job executed by the poll flow to finish
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, 500);
		helper.assertJobWasSuccessful();
	
		Map<String, Object> payload0 = invokeRetrieveFlow(retrieveContactFromSFFlow, createdContactsInA.get(0));
		Assert.assertNotNull("The contact 0 should have been sync but is null", payload0);
		Assert.assertEquals("The contact 0 should have been sync (First Name)", createdContactsInA.get(0).get(KEY_FIRST_NAME), payload0.get(KEY_FIRST_NAME_SF));
		Assert.assertEquals("The contact 0 should have been sync (Email)", createdContactsInA.get(0).get(KEY_EMAIL).toString().toLowerCase(), payload0.get(KEY_EMAIL_SF));
	
		// test if an account was created along with the contact
		MuleEvent event = getSubFlow("selectAccountSF1").process(getTestEvent(createdContactsInA.get(0), MessageExchangePattern.REQUEST_RESPONSE));
		Assert.assertNotNull("The account for contact 0 should have been sync but is null", event.getMessage().getPayload());
		
		Map<String, Object>  payload1 = invokeRetrieveFlow(retrieveContactFromSFFlow, createdContactsInA.get(1));
		Assert.assertNotNull("The contact 1 should have been sync but is null", payload1);
		Assert.assertEquals("The contact 1 should have been sync (First Name)", createdContactsInA.get(1).get(KEY_FIRST_NAME), payload1.get(KEY_FIRST_NAME_SF));		
		Assert.assertEquals("The contact 1 should have been sync (Email)", createdContactsInA.get(1).get(KEY_EMAIL).toString().toLowerCase(), payload1.get(KEY_EMAIL_SF));				
	}

	@Override
	protected String getConfigResources() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream(MULE_DEPLOY_PROPERTIES_PATH));
		} catch (IOException e) {
			throw new IllegalStateException(
					"Could not find mule-deploy.properties file on classpath. " +
					"Please add any of those files or override the getConfigResources() method to provide the resources by your own.");
		}

		return props.getProperty("config.resources") + getTestFlows();
	}


	private void createTestDataInSandBox() throws MuleException, Exception {
		// Create object in target system to be updated
		
		account.put("Name", accountName);
		String uniqueSuffix = "_" + TEMPLATE_NAME + "_" + System.currentTimeMillis();
		
		Map<String, Object> contact_3_B = new HashMap<String, Object>();
		contact_3_B.put(KEY_FIRST_NAME_SF, "Name_3_B" + uniqueSuffix);
		contact_3_B.put(KEY_LAST_NAME_SF, "Name_3_B" + uniqueSuffix);		
		contact_3_B.put(KEY_EMAIL_SF, "Name_3_B" + uniqueSuffix + "@gmail.com");		
		createdContactsInB.add(contact_3_B);
	
		SubflowInterceptingChainLifecycleWrapper createAccountInBFlow = getSubFlow("insertContactSF1");
		createAccountInBFlow.initialise();
		MuleEvent event = createAccountInBFlow.process(getTestEvent(createdContactsInB, MessageExchangePattern.REQUEST_RESPONSE));
		@SuppressWarnings(value = { "unchecked" })
		List<EnrichedSaveResult> list = (List<EnrichedSaveResult>) event.getMessage().getPayload();
		createdContactsInB.get(0).put(KEY_ID, list.get(0).getId());
		
		Thread.sleep(1001); // this is here to prevent equal LastModifiedDate
		
		// Create accounts in source system to be or not to be synced
	
		Map<String, Object> contact_0_A = new HashMap<String, Object>();
		contact_0_A.put(KEY_FIRST_NAME, "Name_0_A"+ uniqueSuffix);
		contact_0_A.put(KEY_LAST_NAME, "Name_0_A"+ uniqueSuffix);
		contact_0_A.put(KEY_EMAIL, contact_0_A.get(KEY_FIRST_NAME) + "@gmail.com");
		contact_0_A.put(KEY_ACCOUNT, account.get("Name"));
		createdContactsInA.add(contact_0_A);
				

		Map<String, Object> contact_1_A = new HashMap<String, Object>();
		contact_1_A.put(KEY_FIRST_NAME,  "Name_updated_"+uniqueSuffix);
		contact_1_A.put(KEY_LAST_NAME, "Name_1_A_"+uniqueSuffix);
		contact_1_A.put(KEY_EMAIL, contact_3_B.get(KEY_EMAIL_SF));
		createdContactsInA.add(contact_1_A);
		
		SubflowInterceptingChainLifecycleWrapper createContactInAFlow = getSubFlow("insertContactSiebel1");
		createContactInAFlow.initialise();
		for (int i = 0; i < createdContactsInA.size(); i++){			
			event = createContactInAFlow.process(getTestEvent(createdContactsInA.get(i), MessageExchangePattern.REQUEST_RESPONSE));			
			CreateResult cr = (CreateResult) event.getMessage().getPayload();
			// assign Siebel-generated IDs						
			createdContactsInA.get(i).put(KEY_ID, cr.getCreatedObjects().get(0));
		}
		System.out.println("Results after adding: " + createdContactsInA.toString());
	}

	@Override
	protected Properties getStartUpProperties() {
		Properties properties = new Properties(super.getStartUpProperties());
		properties.put(
				MuleProperties.APP_HOME_DIRECTORY_PROPERTY,
				new File(MAPPINGS_FOLDER_PATH).getAbsolutePath());
		return properties;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> invokeRetrieveFlow(SubflowInterceptingChainLifecycleWrapper flow, Map<String, Object> payload) throws Exception {
		MuleEvent event = flow.process(getTestEvent(payload, MessageExchangePattern.REQUEST_RESPONSE));
		Object resultPayload = event.getMessage().getPayload();
		return resultPayload instanceof NullPayload ? null : ((ConsumerIterator<Map<String, Object>>) resultPayload).next();
	}
	
	private void deleteTestContactsFromSandBoxA() throws InitialisationException, MuleException, Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("deleteContactSiebel1");
		flow.initialise();
		deleteTestEntityFromSandBox(flow, createdContactsInA);
		
		flow = getSubFlow("selectAccountSiebel1");
		flow.initialise();
		List<Map<String, Object>> resp = (List<Map<String, Object>>)flow.process(getTestEvent(account, MessageExchangePattern.REQUEST_RESPONSE)).getMessage().getPayload();
		
		List<String> idList = new ArrayList<String>();
		flow = getSubFlow("deleteAccountSiebel1");
		flow.initialise();
		idList.add(resp.get(0).get("Id").toString());
		flow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));	
	}

	private void deleteTestContactsFromSandBoxB() throws InitialisationException, MuleException, Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("selectAccountSF1");
		flow.initialise();
		MuleEvent event = flow.process(getTestEvent(accountName, MessageExchangePattern.REQUEST_RESPONSE));	
		List<String> idList = new ArrayList<String>();
		idList.add(((HashMap<String, String>)event.getMessage().getPayload()).get("Id"));
		
		flow = getSubFlow("deleteAccountSF1");
		flow.initialise();
		flow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));	
	}
	
	private void deleteTestEntityFromSandBox(SubflowInterceptingChainLifecycleWrapper deleteFlow, List<Map<String, Object>> entitities) throws MuleException, Exception {
		List<String> idList = new ArrayList<String>();
		for (Map<String, Object> c : entitities) {
			idList.add(c.get(KEY_ID).toString());
		}
		deleteFlow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
	}

}

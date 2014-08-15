/**
 * Mule Anypoint Template
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.modules.siebel.api.model.response.CreateResult;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.streaming.ConsumerIterator;

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
	private static final String KEY_EMAIL = "Email Address";
	private static final String KEY_ACCOUNT = "Account";
	
	private static final String INBOUND_FLOW_NAME = "mainFlow";
	private static final int TIMEOUT_MILLIS = 120;

	List<Map<String, Object>> contactsInSiebel = new ArrayList<Map<String,Object>>();
	List<Map<?, ?>> contactsInSalesforce = new ArrayList<Map<?, ?>>();
	
	
	private SubflowInterceptingChainLifecycleWrapper deleteContactSiebel;
	private SubflowInterceptingChainLifecycleWrapper deleteAccountSiebel;
	private SubflowInterceptingChainLifecycleWrapper queryAccountSiebel;
	private SubflowInterceptingChainLifecycleWrapper deleteObjectFromSalesforce;
	private SubflowInterceptingChainLifecycleWrapper createAccountInSiebel;
	private SubflowInterceptingChainLifecycleWrapper createContactInSiebel;
	private SubflowInterceptingChainLifecycleWrapper queryContactFromSalesforce;
	
	private BatchTestHelper batchTestHelper;

	@BeforeClass
	public static void beforeTestClass() {
		System.setProperty("page.size", "1000");

		// Set polling frequency to 10 seconds
		System.setProperty("poll.frequency", "10000");

		// Set polling delay
		System.setProperty("poll.startDelayMillis", "0");

		// Set default water-mark expression to current time
		System.setProperty("watermark.default.expression", "#[System.currentTimeMillis() - 1000 * 60 * 60 * 10]");
	}

	@Before
	public void setUp() throws Exception {
		stopAutomaticPollTriggering();
		getAndInitializeFlows();
		
		batchTestHelper = new BatchTestHelper(muleContext);
		createTestDataInSandBox();
	}

	@After
	public void tearDown() throws MuleException, Exception {
		deleteTestContactsFromSiebel();
		deleteTestContactsFromSalesforce();
	}

	private void stopAutomaticPollTriggering() throws MuleException {
		stopFlowSchedulers(INBOUND_FLOW_NAME);
	}

	private void getAndInitializeFlows() throws InitialisationException {
		deleteContactSiebel = getSubFlow("deleteContactSiebel");
		deleteContactSiebel.initialise();

		deleteAccountSiebel = getSubFlow("deleteAccountSiebel");
		deleteAccountSiebel.initialise();

		queryAccountSiebel = getSubFlow("queryAccountSiebel");
		queryAccountSiebel.initialise();
		
		deleteObjectFromSalesforce = getSubFlow("deleteObjectFromSalesforce");
		deleteObjectFromSalesforce.initialise();
		
		createAccountInSiebel = getSubFlow("createAccountInSiebel");
		createAccountInSiebel.initialise();
		
		createContactInSiebel = getSubFlow("createContactInSiebel");
		createContactInSiebel.initialise();
		
		queryContactFromSalesforce = getSubFlow("queryContactFromSalesforce");
		queryContactFromSalesforce.initialise();
	}

	@Test
	public void testMainFlow() throws Exception {
		runSchedulersOnce(INBOUND_FLOW_NAME);
		
		// Wait for the batch job executed by the poll flow to finish
		batchTestHelper.awaitJobTermination(TIMEOUT_MILLIS * 1000, 500);
		batchTestHelper.assertJobWasSuccessful();

		for (Map<String, Object> contact : contactsInSiebel) {
			MuleEvent event = queryContactFromSalesforce.process(getTestEvent(contact, MessageExchangePattern.REQUEST_RESPONSE));
			ConsumerIterator<?> it = (ConsumerIterator<?>) event.getMessage().getPayload();
			while(it.hasNext()){
				Map<?, ?> contactSalesforce = (Map<?, ?>) it.next();
				Assert.assertEquals("Contacts should be the same", contact.get(KEY_FIRST_NAME), contactSalesforce.get("FirstName"));
				Assert.assertNotNull("There should be account for created contact", contactSalesforce.get("AccountId"));
				contactsInSalesforce.add(contactSalesforce);
			}
		}
	}

	private void createTestDataInSandBox() throws Exception {
		String uniqueSuffix = "" + System.currentTimeMillis();
		
		Map<String, Object> contactInSiebel = new HashMap<String, Object>();
		String name = "ContactBroadcast"+ uniqueSuffix;
		String email = name + "@gmail.com";
		
		contactInSiebel.put(KEY_FIRST_NAME, name);
		contactInSiebel.put(KEY_LAST_NAME, name);
		contactInSiebel.put(KEY_EMAIL, email);
		contactInSiebel.put(KEY_ACCOUNT, name);
		contactsInSiebel.add(contactInSiebel);
		
		MuleEvent event = createContactInSiebel.process(getTestEvent(contactInSiebel, MessageExchangePattern.REQUEST_RESPONSE));
		CreateResult cr = (CreateResult) event.getMessage().getPayload();
		contactInSiebel.put(KEY_ID, cr.getCreatedObjects().get(0));
	}

	private void deleteTestContactsFromSiebel() throws InitialisationException, MuleException, Exception {
		// delete contacts
		List<String> idList = new ArrayList<String>();
		for (Map<String, Object> contact : contactsInSiebel) {
			idList.add((String)contact.get("Id"));
		}
		MuleEvent event = deleteContactSiebel.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
		
		// delete previously created account
		List<?> accountsToDelete = null;
		for (Map<String, Object> contact : contactsInSiebel) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("Name", contact.get(KEY_ACCOUNT));
			event = queryAccountSiebel.process(getTestEvent(map, MessageExchangePattern.REQUEST_RESPONSE));
			accountsToDelete = (List<?>) event.getMessage().getPayload();
		}
		idList = new ArrayList<String>();
		for (Object item : (List<?>) accountsToDelete) {
			idList.add((String) ((Map<?, ?>) item).get("Id"));
		}
		deleteAccountSiebel.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
	}

	private void deleteTestContactsFromSalesforce() throws InitialisationException, MuleException, Exception {
		List<String> idList = new ArrayList<String>();
		for (Map<?, ?> contact : contactsInSalesforce) {
			idList.add((String) contact.get("Id"));
			idList.add((String) contact.get("AccountId"));
		}
		deleteObjectFromSalesforce.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
	}

}

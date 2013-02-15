/**
 * *******************************************************
 * Copyright (C) 2013 catify <info@catify.com>
 * *******************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.catify.processengine.core.nodes.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBException;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.helpers.collection.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.catify.processengine.core.data.model.NodeInstaceStates;
import com.catify.processengine.core.data.model.entities.FlowNodeInstance;
import com.catify.processengine.core.data.model.entities.ProcessNode;
import com.catify.processengine.core.data.services.FlowNodeInstanceRepositoryService;
import com.catify.processengine.core.data.services.IdService;
import com.catify.processengine.core.data.services.ProcessNodeRepositoryService;
import com.catify.processengine.core.messages.TriggerMessage;
import com.catify.processengine.core.processdefinition.jaxb.TProcess;
import com.catify.processengine.management.ProcessManagementService;
import com.catify.processengine.management.ProcessManagementServiceImpl;
import com.catify.processengine.management.XmlJaxbTransformer;

/**
 * @author chris
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:META-INF/spring/spring-context.xml" })
//@Transactional
public class IntegrationTests extends IntegrationTestBase {
	
	@Test
	public void testprocessTimerCatchDuration() throws IOException, JAXBException, InterruptedException {
		TProcess process = simpleProcessTest("testprocess_timer_catch_duration.bpmn", 3000, 20000, 8, 4);
	    Assert.assertTrue(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	}
	
	@Test
	public void testprocessTimerStartCycle() throws IOException, JAXBException, InterruptedException {
		TProcess process = startProcess("testprocess_timer_start_cycle.bpmn", 3000);
		Thread.sleep(20000);
		// check results
		Assert.assertTrue(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
		Assert.assertEquals(6, getFlowNodeCount(process));
		Assert.assertEquals(9, getFlowNodeInstanceCount(process));
	}
	
	@Test
	public void testprocessBoundaryNoninterruptingDeactivated() throws IOException, JAXBException, InterruptedException {
		TProcess process = simpleProcessTest("testprocess_boundary_noninterrupting_deactivated_messageIntegration_camel.bpmn", 3000, 5000, 12, 6);	
	    Assert.assertFalse(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	    checkNodeInstance(process, "_8", NodeInstaceStates.DEACTIVATED_STATE);
	    checkNodeInstance(process, "_9", NodeInstaceStates.INACTIVE_STATE);
	    checkNodeInstance(process, "_10", NodeInstaceStates.INACTIVE_STATE);
	}
	
	@Test
	public void testprocessBoundaryNoninterruptingActivated() throws IOException, JAXBException, InterruptedException {

		TProcess process = startProcess("testprocess_boundary_noninterrupting_activated_messageIntegration_camel.bpmn", 4000);
		pm.createProcessInstance(client, process, startEvent, new TriggerMessage(defaultInstanceId, null));

		// wait for the process instance to start up
		Thread.sleep(3000);
		
	    // trigger the waiting boundary event
	    pm.sendTriggerMessage(client, process, "_8", new TriggerMessage(defaultInstanceId, null));
		Thread.sleep(3000);
		
		// trigger the waiting receiving activity
		pm.sendTriggerMessage(client, process, "_5", new TriggerMessage(defaultInstanceId, null));
		Thread.sleep(3000);
		
		// check results
		Assert.assertEquals(12, getFlowNodeCount(process));
		Assert.assertEquals(6, getFlowNodeInstanceCount(process));
	    Assert.assertTrue(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	}
	
	@Test
	public void testprocessBoundaryNoninterruptingActivatedMultiple() throws IOException, JAXBException, InterruptedException {

		TProcess process = startProcess("testprocess_boundary_noninterrupting_activated_multiple_messageIntegration_camel.bpmn", 4000);
		pm.createProcessInstance(client, process, startEvent, new TriggerMessage(defaultInstanceId, null));

		// wait for the process instance to start up
		Thread.sleep(3000);
		
	    // trigger the first waiting boundary event
	    pm.sendTriggerMessage(client, process, "_8", new TriggerMessage(defaultInstanceId, null));
		
	    // trigger the second waiting boundary event
	    pm.sendTriggerMessage(client, process, "_2", new TriggerMessage(defaultInstanceId, null));
		Thread.sleep(3000);
		
		// trigger the waiting receiving activity
		pm.sendTriggerMessage(client, process, "_5", new TriggerMessage(defaultInstanceId, null));
		Thread.sleep(3000);
		
		// check results
		Assert.assertEquals(14, getFlowNodeCount(process));
		Assert.assertEquals(7, getFlowNodeInstanceCount(process));
	    Assert.assertTrue(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	}
	
	@Test
	public void testprocessBoundaryInterruptingActivatedMultiple() throws IOException, JAXBException, InterruptedException {

		TProcess process = startProcess("testprocess_boundary_interrupting_activated_multiple_messageIntegration_camel.bpmn", 4000);
		pm.createProcessInstance(client, process, startEvent, new TriggerMessage(defaultInstanceId, null));

		// wait for the process instance to start up
		Thread.sleep(3000);
		
	    // trigger the first waiting boundary event
	    pm.sendTriggerMessage(client, process, "_8", new TriggerMessage(defaultInstanceId, null));
	    Thread.sleep(3000);
		
	    // trigger the second waiting boundary event (should not have an effect, because it will have been deactivated)
	    pm.sendTriggerMessage(client, process, "_2", new TriggerMessage(defaultInstanceId, null));
		
		// trigger the waiting receiving activity (should not have an effect, because it will have been deactivated)
		pm.sendTriggerMessage(client, process, "_5", new TriggerMessage(defaultInstanceId, null));
		Thread.sleep(3000);
		
		// check results
		Assert.assertEquals(14, getFlowNodeCount(process));
		Assert.assertEquals(7, getFlowNodeInstanceCount(process));
	    Assert.assertFalse(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	    
	    checkNodeInstance(process, "_2", NodeInstaceStates.DEACTIVATED_STATE);
	    checkNodeInstance(process, "_5", NodeInstaceStates.DEACTIVATED_STATE);
	    checkNodeInstance(process, "_8", NodeInstaceStates.PASSED_STATE);
	    checkNodeInstance(process, "_9", NodeInstaceStates.PASSED_STATE);
	    checkNodeInstance(process, "_10", NodeInstaceStates.PASSED_STATE);
	}
	
	@Test
	public void testprocessBoundaryInterruptingTimerActivatedSubprocess() throws IOException, JAXBException, InterruptedException {

		TProcess process = startProcess("testprocess_boundary_interrupting_timer_activated_subprocess.bpmn", 4000);
		pm.createProcessInstance(client, process, startEvent, new TriggerMessage(defaultInstanceId, null));

		// wait for the process instance to start up
		Thread.sleep(3000);
		
	    // do not trigger the waiting catch event in the sub process so that the boundary timer event fires after 5 secs
		Thread.sleep(9000);
		
		// check results
		Assert.assertEquals(10, getFlowNodeCount(process));
		Assert.assertEquals(8, getFlowNodeInstanceCount(process));
	    Assert.assertFalse(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	    
	    // check sub process and end events
	    checkNodeInstance(process, "_8", NodeInstaceStates.PASSED_STATE);
	    checkNodeInstance(process, "_4", NodeInstaceStates.DEACTIVATED_STATE);
	    checkNodeInstance(process, "_3", NodeInstaceStates.INACTIVE_STATE);
	    
	    // check sub process embedded events
	    checkNodeInstance(process, "_10", NodeInstaceStates.PASSED_STATE);
	    checkNodeInstance(process, "_11", NodeInstaceStates.DEACTIVATED_STATE);
	    checkNodeInstance(process, "_12", NodeInstaceStates.INACTIVE_STATE);
	    
	    Thread.sleep(1000);
	}
	
	@Test
	public void testprocessThrowMessageIntegration() throws IOException, JAXBException, InterruptedException {
		TProcess process = simpleProcessTest("testprocess_throw_camel_messageIntegration.bpmn", 3000, 5000, 6, 3);
	    Assert.assertTrue(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	}
}

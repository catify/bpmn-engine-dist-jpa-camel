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
public class IntegrationTests {
	
	static final Logger LOG = LoggerFactory
			.getLogger(IntegrationTests.class);
	
	@Autowired
    private ApplicationContext ctx;
	
	@Autowired 
	private Neo4jTemplate neo4jTemplate;
	
	@Autowired
	private FlowNodeInstanceRepositoryService flowNodeInstanceRepo;
	
	@Autowired
	private ProcessNodeRepositoryService processNodeRepo;

    private final String client = "Client";
    private final String startEvent = "startEvent1";
//    private final String catchEvent = "catchEvent1";
    private final String defaultInstanceId = "42";
    
    private ProcessManagementService pm = new ProcessManagementServiceImpl();
    private XmlJaxbTransformer xmlJaxbTransformer = new XmlJaxbTransformer();
	
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
		
	    // trigger the waiting catch event
	    pm.sendTriggerMessage(client, process, "_8", new TriggerMessage(defaultInstanceId, null));
		
		Thread.sleep(3000);
		
		pm.sendTriggerMessage(client, process, "_5", new TriggerMessage(defaultInstanceId, null));
		
		Thread.sleep(3000);
		
		// check results
		Assert.assertEquals(12, getFlowNodeCount(process));
		Assert.assertEquals(6, getFlowNodeInstanceCount(process));
		
	    Assert.assertTrue(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	    checkNodeInstance(process, "_5", NodeInstaceStates.PASSED_STATE);
	    checkNodeInstance(process, "_8", NodeInstaceStates.PASSED_STATE);
	    checkNodeInstance(process, "_9", NodeInstaceStates.PASSED_STATE);
	    checkNodeInstance(process, "_10", NodeInstaceStates.PASSED_STATE);
	}
	
	
	@Test
	public void testprocessThrowMessageIntegration() throws IOException, JAXBException, InterruptedException {
		TProcess process = simpleProcessTest("testprocess_throw_camel_messageIntegration.bpmn", 3000, 5000, 6, 3);
	    Assert.assertTrue(checkFlowNodeInstanceState(NodeInstaceStates.PASSED_STATE, process, defaultInstanceId));
	}
	
	/**
	 * Helper method to test standard scenarios.
	 *
	 * @param fileName name of the file without path and slash (must be in /src/test/resources/data/)
	 * @param firstSleep milliseconds of the first sleep
	 * @param secondSleep milliseconds of the first sleep
	 * @param awaitedFlowNodeCount awaited number of flow nodes
	 * @param awaitedInstanceNodeCount awaited number of flow node instances
	 * @return the jaxb process
	 * @throws FileNotFoundException the file not found exception
	 * @throws JAXBException the jAXB exception
	 * @throws InterruptedException the interrupted exception
	 */
	private TProcess simpleProcessTest(String fileName, int firstSleep, int secondSleep, int awaitedFlowNodeCount, int awaitedInstanceNodeCount) 
			throws FileNotFoundException, JAXBException, InterruptedException {
		TProcess process = startProcess(fileName, firstSleep);
		pm.createProcessInstance(client, process, startEvent, new TriggerMessage(defaultInstanceId, null));

		// wait for the process instance to start up
		Thread.sleep(secondSleep);

		// check results
		Assert.assertEquals(awaitedFlowNodeCount, getFlowNodeCount(process));
		Assert.assertEquals(awaitedInstanceNodeCount, getFlowNodeInstanceCount(process));
		
		return process;
	}
	
//	/**
//	 * Helper method to test standard scenarios with a node to trigger.
//	 *
//	 * @param fileName name of the file without path and slash (must be in /src/test/resources/data/)
//	 * @param flowNodeIdToTrigger the flow node id to trigger
//	 * @param firstSleep milliseconds of the first sleep
//	 * @param secondSleep milliseconds of the first sleep
//	 * @param thirdSleep milliseconds of the third sleep
//	 * @param awaitedFlowNodeCount awaited number of flow nodes
//	 * @param awaitedInstanceNodeCount awaited number of flow node instances
//	 * @return the jaxb process
//	 * @throws FileNotFoundException the file not found exception
//	 * @throws JAXBException the jAXB exception
//	 * @throws InterruptedException the interrupted exception
//	 */
//	private TProcess simpleProcessTestWithTrigger(String fileName, String flowNodeIdToTrigger, int firstSleep, int secondSleep, int thirdSleep, int awaitedFlowNodeCount, int awaitedInstanceNodeCount) 
//			throws FileNotFoundException, JAXBException, InterruptedException {
//		TProcess process = startProcess(fileName, firstSleep);
//		pm.createProcessInstance(client, process, startEvent, new TriggerMessage(defaultInstanceId, null));
//
//		// wait for the process instance to start up
//		Thread.sleep(secondSleep);
//		
//	    // trigger the waiting catch event
//	    pm.sendTriggerMessage(client, process, flowNodeIdToTrigger, new TriggerMessage(defaultInstanceId, null));
//	    
//	    // wait for the process instance to end
//	    Thread.sleep(thirdSleep);
//
//		// check results
//		Assert.assertEquals(awaitedFlowNodeCount, getFlowNodeCount(process));
//		Assert.assertEquals(awaitedInstanceNodeCount, getFlowNodeInstanceCount(process));
//		
//		return process;
//	}
	
	/**
	 * Start a process.
	 *
	 * @param fileName name of the file without path and slash (must be in /src/test/resources/data/)
	 * @param firstSleep milliseconds of the first sleep
	 * @return the jaxb process
	 * @throws FileNotFoundException the file not found exception
	 * @throws JAXBException the jAXB exception
	 * @throws InterruptedException the interrupted exception
	 */
	private TProcess startProcess(String fileName, int firstSleep)
			throws FileNotFoundException, JAXBException, InterruptedException {
		File processDefinition = new File(getClass().getResource("/data/" + fileName).getFile());
		Assert.assertTrue(processDefinition.exists());

		pm.startProcessFromDefinitionFile(client, processDefinition);

		// wait for the process to start up
		Thread.sleep(firstSleep);

		List<TProcess> processes = xmlJaxbTransformer.getTProcessesFromBpmnXml(processDefinition);
		assertNotNull(processes);
		return processes.get(0);
	}
	
	/**
	 * Gets the flow node count. Note that a process always has running and archived flow nodes.
	 *
	 * @return the flow node count
	 */
	long getFlowNodeCount(TProcess process) {
		String processId = IdService.getUniqueProcessId(client, process);
		ProcessNode processNode = processNodeRepo.findByUniqueProcessId(processId);
		ProcessNode archivedProcessNode = processNodeRepo.findArchivedByRunningUniqueProcessId(processId);
		
		return processNode.getFlowNodes().size() + archivedProcessNode.getFlowNodes().size();
	}
	
	/**
	 * Gets the flow node instance count.
	 *
	 * @return the flow node instance count
	 */
	long getFlowNodeInstanceCount(TProcess process) {		
		return this.getFlowNodeInstanceCount(process, defaultInstanceId);
	}
	
	/**
	 * Gets the flow node instance count.
	 * 
	 * @param process {@link TProcess} process object
	 * @param instanceId instance id as {@link String}
	 * @return
	 */
	long getFlowNodeInstanceCount(TProcess process, String instanceId) {
		String processId = IdService.getUniqueProcessId(client, process);
		Set<FlowNodeInstance> fni = flowNodeInstanceRepo.findAllFlowNodeInstances(processId, instanceId);
		
		return Iterables.count(fni);
	}

	/**
	 * Check flow node instance state.
	 *
	 * @param state the state
	 * @return true, if all FlowNodeInstances have the given state
	 */
	boolean checkFlowNodeInstanceState(String state, TProcess process, String processInstanceId) {
		
		String processId = IdService.getUniqueProcessId(client, process);
		Set<FlowNodeInstance> fni = flowNodeInstanceRepo.findAllFlowNodeInstances(processId, processInstanceId);
		
		if (fni.size()==0) {
			return false;
		}
		
		for (FlowNodeInstance flowNodeInstance : fni) {
			if (!flowNodeInstance.getNodeInstanceState().equals(state)) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Count the flow node instances in a given state.
	 *
	 * @param state the state
	 * @return the int
	 */
	int countFlowNodeInstanceWithState(String state, TProcess process, String processInstanceId) {
		String processId = IdService.getUniqueProcessId(client, process);
		Set<FlowNodeInstance> fni = flowNodeInstanceRepo.findAllFlowNodeInstances(processId, processInstanceId);
		
		int counter = 0;
		
		for (FlowNodeInstance flowNodeInstance : fni) {
			if (flowNodeInstance.getNodeInstanceState().equals(state)) {
				counter++;
			}
		}
		
		return counter;
	}
	
	/**
	 * Count the flow node instances of a process.
	 *
	 * @param state the state
	 * @return the int
	 */
	int countAllProcessInstances(TProcess process) {
		String processId = IdService.getUniqueProcessId(client, process);
		Set<String> fni = flowNodeInstanceRepo.findAllFlowNodeInstances(processId);

		return fni.size();
	}
	

	/**
	 * Check node instance for a given state.
	 *
	 * @param process the jaxb process
	 * @param id the id of the flow node to check
	 * @param state the desired state
	 */
	private void checkNodeInstance(TProcess process, String id, String state) {
		String flowNodeId = IdService.getUniqueFlowNodeId(client, process, null, id); // default throw
		String processId = IdService.getUniqueProcessId(client, process);
		FlowNodeInstance nodeInstance = flowNodeInstanceRepo.findFlowNodeInstance(processId, flowNodeId, defaultInstanceId);
		assertNotNull(nodeInstance);
		assertEquals(state, nodeInstance.getNodeInstanceState());
	}
}

package com.catify.processengine.dist;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.bind.JAXBException;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.catify.processengine.management.ProcessImportService;
import com.catify.processengine.management.ProcessManagementService;
import com.catify.processengine.management.ProcessManagementServiceImpl;

public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		AbstractApplicationContext context = new ClassPathXmlApplicationContext("META-INF/spring/spring-context.xml");
	    context.registerShutdownHook();
		
	    ProcessImportService pi = new ProcessImportService();
	    ProcessManagementService pm = new ProcessManagementServiceImpl();
	    
	    try {
			pm.startAllDeployedProcesses("Client");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    File processDefinition = new File("testprocess_throw.xml");

	    if (processDefinition.exists()) {
	    
	    	try {
				pi.importProcessDefinition(processDefinition);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
	    	try {
				pm.startDeployedProcess("Client", processDefinition.getName());
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JAXBException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}   	
	    }
	}

}

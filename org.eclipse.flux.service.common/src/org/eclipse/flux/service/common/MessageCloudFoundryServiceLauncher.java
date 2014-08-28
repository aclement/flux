/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made 
 * available under the terms of the Eclipse Public License v1.0 
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution 
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html). 
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
*******************************************************************************/
package org.eclipse.flux.service.common;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.UploadStatusCallback;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.Staging;
import org.eclipse.flux.client.MessageConnector;

/**
 * Starts/Stops services on the Cloud Foundry
 * 
 * @author aboyko
 *
 */
public class MessageCloudFoundryServiceLauncher extends MessageServiceLauncher {
	
	private CloudFoundryClient cfClient;
	
	private AtomicInteger numberOfInstances;
	
	public MessageCloudFoundryServiceLauncher(MessageConnector messageConnector, URL cfControllerUrl, String orgName, String spaceName, String cfLogin, String cfPassword, String fluxUrl, String username, String password, String serviceID, int maxPoolSize, 
			long timeout, File appLocation) throws IOException {
		super(messageConnector, serviceID, maxPoolSize, timeout);
		this.numberOfInstances = new AtomicInteger(maxPoolSize);
		cfClient = new CloudFoundryClient(new CloudCredentials(cfLogin, cfPassword), cfControllerUrl, orgName, spaceName);
		cfClient.login();
		try {
			CloudApplication cfApp = cfClient.getApplication(serviceID);
			if (cfApp != null) {
				cfClient.deleteApplication(serviceID);
			}
		} catch (CloudFoundryException e) {
			e.printStackTrace();
		}
		cfClient.createApplication(serviceID, new Staging(), 1024, null, null);
		cfClient.uploadApplication(serviceID , appLocation, new UploadStatusCallback() {
			
			@Override
			public boolean onProgress(String arg0) {
				System.out.println("Progress: " + arg0);
				return false;
			}
			
			@Override
			public void onProcessMatchedResources(int arg0) {
				System.out.println("Matching Resources: " + arg0);
			}
			
			@Override
			public void onMatchedFileNames(Set<String> arg0) {
				System.out.println("Matching file names: " + arg0);
			}
			
			@Override
			public void onCheckResources() {
				System.out.println("Check resources!");
			}
		});		
		cfClient.updateApplicationEnv(serviceID, createEnv(fluxUrl, username, password));
		cfClient.updateApplicationInstances(serviceID, maxPoolSize);
	}

	@Override
	protected void addService() {
		cfClient.login();
		cfClient.updateApplicationInstances(serviceID, numberOfInstances.incrementAndGet());
	}

	@Override
	protected void initServices() {
		cfClient.login();
		cfClient.startApplication(serviceID);
	}

	@Override
	protected boolean removeService(String socketId) {
		boolean stopped = super.removeService(socketId);
		if (stopped) {
//			cfClient.login();
//			cfClient.updateApplicationInstances(serviceID, numberOfInstances.decrementAndGet());
		}
		return stopped;
	}
	
	private List<String> createEnv(String fluxUrl, String username, String password) {
		List<String> env = new ArrayList<String>(3);
		env.add("FLUX_HOST=" + fluxUrl);
		env.add("FLUX_USER_ID=" + username.replace("$", "\\$"));
		env.add("FLUX_USER_TOKEN=" + password);
		env.add("FLUX_LAZY_START=true");
		env.add("PATH=/bin:/usr/bin:/home/vcap/app/.java-buildpack/open_jdk_jre/bin");
		return env;
	}

	@Override
	public void dispose() {
		super.dispose();
		cfClient.login();
		cfClient.stopApplication(serviceID);
	}
	
}

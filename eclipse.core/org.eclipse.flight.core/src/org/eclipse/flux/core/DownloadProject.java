/*******************************************************************************
 * Copyright (c) 2013, 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made 
 * available under the terms of the Eclipse Public License v1.0 
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution 
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html). 
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
*******************************************************************************/
package org.eclipse.flux.core;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Martin Lippert
 */
public class DownloadProject {

	public interface CompletionCallback {
		public void downloadComplete(IProject project);

		public void downloadFailed();
	}

	private IMessagingConnector messagingConnector;

	private String projectName;
	private int callbackID;
	private CompletionCallback completionCallback;

	private String username;
	private IProject project;

	private AtomicInteger requestedFileCount = new AtomicInteger(0);
	private AtomicInteger downloadedFileCount = new AtomicInteger(0);

	private CallbackIDAwareMessageHandler projectResponseHandler;
	private CallbackIDAwareMessageHandler resourceResponseHandler;

	public DownloadProject(IMessagingConnector messagingConnector, String projectName, String username) {
		this.messagingConnector = messagingConnector;
		this.projectName = projectName;
		this.username = username;

		this.callbackID = this.hashCode();

		projectResponseHandler = new CallbackIDAwareMessageHandler("getProjectResponse", this.callbackID) {
			@Override
			public void handleMessage(String messageType, JSONObject message) {
				getProjectResponse(message);
			}
		};
		resourceResponseHandler = new CallbackIDAwareMessageHandler("getResourceResponse", this.callbackID) {
			@Override
			public void handleMessage(String messageType, JSONObject message) {
				getResourceResponse(message);
			}
		};
	}

	public void run(CompletionCallback completionCallback) {
		this.messagingConnector.addMessageHandler(projectResponseHandler);
		this.messagingConnector.addMessageHandler(resourceResponseHandler);

		this.completionCallback = completionCallback;

		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		project = root.getProject(projectName);

		try {
			project.create(null);
			project.open(null);

			JSONObject message = new JSONObject();
			message.put("callback_id", this.callbackID);
			message.put("username", this.username);
			message.put("project", this.projectName);

			messagingConnector.send("getProjectRequest", message);
		} catch (CoreException e1) {
			e1.printStackTrace();
			this.messagingConnector.removeMessageHandler(projectResponseHandler);
			this.messagingConnector.removeMessageHandler(resourceResponseHandler);
			this.completionCallback.downloadFailed();
		} catch (JSONException e) {
			e.printStackTrace();
			this.messagingConnector.removeMessageHandler(projectResponseHandler);
			this.messagingConnector.removeMessageHandler(resourceResponseHandler);
			this.completionCallback.downloadFailed();
		}
	}

	public void getProjectResponse(JSONObject response) {
		try {
			final String responseProject = response.getString("project");
			final String responseUser = response.getString("username");
			final JSONArray files = response.getJSONArray("files");

			if (this.username.equals(responseUser)) {
				for (int i = 0; i < files.length(); i++) {
					JSONObject resource = files.getJSONObject(i);

					String resourcePath = resource.getString("path");
					long timestamp = resource.getLong("timestamp");

					String type = resource.optString("type");

					if (type.equals("folder")) {
						if (resourcePath.isEmpty()) {
							project.setLocalTimeStamp(timestamp);
						} else {
							IFolder folder = project.getFolder(new Path(resourcePath));
							if (!folder.exists()) {
								folder.create(true, true, null);
							}
							folder.setLocalTimeStamp(timestamp);
						}
					} else if (type.equals("file")) {
						requestedFileCount.incrementAndGet();
					}
				}

				for (int i = 0; i < files.length(); i++) {
					JSONObject resource = files.getJSONObject(i);

					String resourcePath = resource.getString("path");
					String type = resource.optString("type");

					if (type.equals("file")) {
						JSONObject message = new JSONObject();
						message.put("callback_id", callbackID);
						message.put("username", this.username);
						message.put("project", responseProject);
						message.put("resource", resourcePath);

						messagingConnector.send("getResourceRequest", message);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			this.messagingConnector.removeMessageHandler(projectResponseHandler);
			this.messagingConnector.removeMessageHandler(resourceResponseHandler);
			this.completionCallback.downloadFailed();
		}
	}

	public void getResourceResponse(JSONObject response) {
		try {
			final String responseUser = response.getString("username");
			final String resourcePath = response.getString("resource");
			final long timestamp = response.getLong("timestamp");
			final String content = response.getString("content");

			if (this.username.equals(responseUser)) {
				IFile file = project.getFile(resourcePath);
				if (!file.exists()) {
					file.create(new ByteArrayInputStream(content.getBytes()), true, null);
				} else {
					file.setContents(new ByteArrayInputStream(content.getBytes()), true, false, null);
				}
				file.setLocalTimeStamp(timestamp);

				int downloaded = this.downloadedFileCount.incrementAndGet();
				if (downloaded == this.requestedFileCount.get()) {
					this.messagingConnector.removeMessageHandler(projectResponseHandler);
					this.messagingConnector.removeMessageHandler(resourceResponseHandler);
					this.completionCallback.downloadComplete(project);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			this.messagingConnector.removeMessageHandler(projectResponseHandler);
			this.messagingConnector.removeMessageHandler(resourceResponseHandler);
			this.completionCallback.downloadFailed();
		}
	}

}

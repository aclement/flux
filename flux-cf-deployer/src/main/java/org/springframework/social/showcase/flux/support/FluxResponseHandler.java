package org.springframework.social.showcase.flux.support;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.eclipse.flux.client.IMessageHandler;
import org.eclipse.flux.client.MessageConnector;
import org.json.JSONObject;

/**
 * This class provides a easier way to handle responses if you are content with
 * just getting the first response to your request.
 * <p>
 * It handles thread synchronization and will timeout if no resonpse arrives within
 * some time limit (currently the timeout is fixed to 1000 milliseconds.
 * <p>
 * Typical use:
 * <p>
 * FluxResponseHandler<ProjectList> resp = new FluxResponseHandler(conn, "getProjectsResponse", "kdvolder") {
 *      protected ProjectList parse(JSONObject message) throws Exception {
 *          ... extract ProjectList from message...
 *          ... throw some exception if message doesn't parse...
 *      }
 * }
 * conn.send(...send the request...);
 * return resp.awaitResult();
 * 
 */
public abstract class FluxResponseHandler<T> implements IMessageHandler {
	
	public static final String USERNAME = "username";

	/**
	 * Positive timeout in milliseconds. Negative number or 0 means 'infinite'.
	 */
	private static final long TIME_OUT = 3000; 
	
	private static Timer timer;
	
	/**
	 * Timer thread shared between all 'FluxResponseHandler' to handle timeouts.
	 */
	private static synchronized Timer timer() {
		if (timer==null) {
			timer = new Timer(FluxResponseHandler.class.getName()+"_TIMER", true);
		}
		return timer;
	}
	
	private MessageConnector conn;
	private String messageType;
	private String username;
	private BasicFuture<T> future; // the result goes in here once we got it.

	private final FutureCallback<T> done = new FutureCallback<T>() {
		@Override
		public void completed(T result) {
			cleanup();
		}

		@Override
		public void failed(Exception ex) {
			cleanup();
		}

		@Override
		public void cancelled() {
			cleanup();
		}
	};
	
	private void cleanup() {
		MessageConnector c = this.conn; // local var: thread safe
		if (c!=null) {
			this.conn = null;
			c.removeMessageHandler(this);
		}
	}
	
	public FluxResponseHandler(MessageConnector conn, String messageType, String username) {
		this.conn = conn;
		this.messageType = messageType;
		this.username = username;
		this.future = new BasicFuture<T>(done);
		conn.addMessageHandler(this);
	}

	@Override
	public boolean canHandle(String type, JSONObject message) {
		try {
			return type.equals(this.messageType)
					&& username.equals(message.get(USERNAME));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public void handle(String type, JSONObject message) {
		try {
			future.completed(parse(message));
		} catch (Exception e) {
			future.failed(e);
		} catch (Throwable e) {
			//future doesn't like 'Throwable's' so wrap em.
			future.failed(new RuntimeException(e));
		}
	}

	protected abstract T parse(JSONObject message) throws Exception;

	@Override
	public String getMessageType() {
		return messageType;
	}

	/**
	 * Block while waiting for the response. Returns the result once its been received.
	 */
	public T awaitResult() throws Exception {
		if (!future.isDone() && TIME_OUT>0) {
			timer().schedule(new TimerTask() {
				@Override
				public void run() {
					try {
						future.failed(new TimeoutException());
					} catch (Throwable e) {
						//don't let Exception fly.. the timer thread will die!
						e.printStackTrace();
					}
				}
			}, TIME_OUT);
		}
		return future.get();
	}

}

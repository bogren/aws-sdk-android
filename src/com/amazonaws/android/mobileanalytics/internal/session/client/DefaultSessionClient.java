/*
 * Copyright 2010-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.android.mobileanalytics.internal.session.client;

import static com.amazonaws.android.mobileanalytics.internal.core.util.Preconditions.*;

import com.amazonaws.android.mobileanalytics.internal.core.AnalyticsContext;
import com.amazonaws.android.mobileanalytics.internal.core.log.Logger;
import com.amazonaws.android.mobileanalytics.internal.event.InternalEventClient;
import com.amazonaws.android.mobileanalytics.internal.session.FileSessionStore;
import com.amazonaws.android.mobileanalytics.internal.session.InternalSessionClient;
import com.amazonaws.android.mobileanalytics.internal.session.Session;
import com.amazonaws.android.mobileanalytics.internal.session.SessionStore;

/**
 * The Default Session Client
 * 
 * Works with Amazon Mobile Analytics and the Event Client to manage mobile application
 *   sessions. Will relay events to the Delivery Client, which will eventually
 *   send them to ERS, that will be interpreted by TailWind as data necessary
 *   to generate reports on Daily Active Users and Average Session Length.
 *   
 * Additionally, when a session is "active", a global attribute will be added
 *   to custom events traveling through the Event Client that will associate
 *   these custom events with the active session so that relationships can be
 *   interpreted.
 *   
 * Session Events fired:
 *   - Session Start  - Beginning of the application session
 *   - Session Stop   - End of the application session
 *   - Session Pause  - A brief pause in the application session
 *   - Session Resume - Reactivates the application session after a pause
 *   
 * Follows a State Design pattern, wherein the actual implementation of the 
 * 	 methods start(), stop(), pause(), and resume() will be carried out by
 *   nested classes, to accommodate for behavior changes of the client in 
 * 	 response to changes of the application session's state.
 * 
 */
public class DefaultSessionClient implements InternalSessionClient {

	//~ Event Type Constants ---------------------------=
	public static final String SESSION_START_EVENT_TYPE            = "_session.start";
	public static final String SESSION_STOP_EVENT_TYPE             = "_session.stop";
	public static final String SESSION_PAUSE_EVENT_TYPE            = "_session.pause";
	public static final String SESSION_RESUME_EVENT_TYPE           = "_session.resume";
	//- Event Attribute/Metric Key Constants -----------=
	public static final String SESSION_ID_ATTRIBUTE_KEY            = "_session.id";
	public static final String SESSION_DURATION_METRIC_KEY         = "_session.duration";
	public static final String SESSION_START_TIME_ATTRIBUTE_KEY    = "_session.startTime";
	public static final String SESSION_STOP_TIME_ATTRIBUTE_KEY     = "_session.stopTime";
	//- Session Timer Constants ------------------------=
	protected static final long   DEFAULT_RESUME_DELAY     = (long) 5e3;
	protected static final long   DEFAULT_RESTART_DELAY    = (long) 30e3;
	protected static final String RESUME_DELAY_CONFIG_KEY  = "sessionResumeDelay";
	protected static final String RESTART_DELAY_CONFIG_KEY = "sessionRestartDelay";
	
	protected static final Logger logger = Logger.getLogger(DefaultSessionClient.class);

	//- Field Declaration -------------------------------=
	protected final InternalEventClient eventClient;
	protected final AnalyticsContext     context;
	protected       Session             session;
	protected       SessionClientState  state;
	protected final SessionStore        sessionStore;
	
	private final SessionClientState INACTIVE_STATE = new InactiveSessionState(this);
	private final SessionClientState ACTIVE_STATE   = new ActiveSessionState(this);
	private final SessionClientState PAUSED_STATE   = new PausedSessionState(this);
	
	private final long resumeDelay;
	private final long restartDelay;

	/**
	 * STATIC FACTORY
	 * 
	 * @param context
	 * @param eventClient
	 * @return new DefaultSessionClient object
	 */
	public static DefaultSessionClient newInstance(final AnalyticsContext context, final InternalEventClient eventClient) {
		SessionStore sessionStore = new FileSessionStore(context);
		return new DefaultSessionClient(context, eventClient, sessionStore);
	}
	
	/**
	 * Starts an application session
	 * 
	 * Used solely by Amazon Insights
	 */
	public synchronized void startSession() {
		state.start();
	}
	/**
	 * Stops an application session
	 * 
	 * Used solely by Amazon Insights
	 */
	public synchronized void stopSession() {
		state.stop();
	}	
	/**
	 * Briefly pauses an application session. 
	 * 
	 * Should be called in an activity's onPause() method.
	 */
	public synchronized void pauseSession() {
		state.pause();
	}	
	/**
	 * Resumes an application session if the session has been paused within a 
	 *   defined time interval. Otherwise, stops the old session and starts a
	 *   new one.
	 *   
	 * Should be called in an activity's onResume() method.
	 */
	public synchronized void resumeSession() {
		state.resume();
	}
	
	/**
	 * Overridden toString method for testing
	 * 
	 * @return diagnostic string
	 */
	@Override
	public String toString() {
		return "[DefaultSessionClient]\n"
		     + "- session: " 
		     + ((this.session == null) ? "<null>" : this.session.getSessionID())
		     + ((this.session != null && this.session.isPaused()) ? ": paused" : "");
	}
	
	/**
	 * CONSTRUCTOR
	 * 
	 * @param context
	 * @param eventClient
	 */
	protected DefaultSessionClient(final AnalyticsContext context, final InternalEventClient eventClient, final SessionStore sessionStore) {
		checkNotNull(context, "A valid InsightsContext must be provided!");
		checkNotNull(eventClient, "A valid EventClient must be provided!");
		checkNotNull(sessionStore, "A valid SessionStore must be provided!");

		this.sessionStore = sessionStore;
		this.eventClient  = eventClient;
		this.context      = context;
		this.session      = this.sessionStore.getSession();
		if(session != null){
	        eventClient.setSessionId(session.getSessionID());
	        eventClient.setSessionStartTime(session.getStartTime());
		}
		
		this.state        = (this.session == null) ? INACTIVE_STATE : PAUSED_STATE;
		
		this.restartDelay = context.getConfiguration().optLong(RESTART_DELAY_CONFIG_KEY, DEFAULT_RESTART_DELAY);
		this.resumeDelay  = context.getConfiguration().optLong(RESUME_DELAY_CONFIG_KEY, DEFAULT_RESUME_DELAY);
	}
	
	/**
	 * Getters
	 */
	protected Session getSession() {
		return this.session;
	}
	public long getRestartDelay() {
		return this.restartDelay;
	}
	public long getResumeDelay() {
		return this.resumeDelay;
	}
	
	/**
	 * Internal Representation of Application Session's state
	 */
	protected static enum SessionState {
		INACTIVE,
		ACTIVE,
		PAUSED
	}
	/**
	 * Returns the Application Session's state
	 */
	protected SessionState getSessionState() {
		if (this.session != null) {
			return (this.session.isPaused() ? SessionState.PAUSED : SessionState.ACTIVE);
		}
		return SessionState.INACTIVE;
	}
	
	protected synchronized void changeState(final SessionState s) {
		switch (s) {
			case INACTIVE:
				this.state = this.INACTIVE_STATE;
				break;
			case ACTIVE:
				this.state = this.ACTIVE_STATE;
				break;
			case PAUSED:
				this.state = this.PAUSED_STATE;
				break;
			default:
				break;
		}
	}
}

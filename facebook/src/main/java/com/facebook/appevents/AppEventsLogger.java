/**
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.
 *
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.
 *
 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.facebook.appevents;


import android.app.Application;

import android.content.Context;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;


import com.facebook.AccessToken;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;

import com.facebook.internal.Utility;

import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;



/**
 * <p>
 * The AppEventsLogger class allows the developer to log various types of events back to Facebook.  In order to log
 * events, the app must create an instance of this class via a {@link #newLogger newLogger} method, and then call
 * the various "log" methods off of that.
 * </p>
 * <p>
 * This client-side event logging is then available through Facebook App Insights
 * and for use with Facebook Ads conversion tracking and optimization.
 * </p>
 * <p>
 * The AppEventsLogger class has a few related roles:
 * </p>
 * <ul>
 * <li>
 * Logging predefined and application-defined events to Facebook App Insights with a
 * numeric value to sum across a large number of events, and an optional set of key/value
 * parameters that define "segments" for this event (e.g., 'purchaserStatus' : 'frequent', or
 * 'gamerLevel' : 'intermediate').  These events may also be used for ads conversion tracking,
 * optimization, and other ads related targeting in the future.
 * </li>
 * <li>
 * Methods that control the way in which events are flushed out to the Facebook servers.
 * </li>
 * </ul>
 * <p>
 * Here are some important characteristics of the logging mechanism provided by AppEventsLogger:
 * <ul>
 * <li>
 * Events are not sent immediately when logged.  They're cached and flushed out to the
 * Facebook servers in a number of situations:
 * <ul>
 * <li>when an event count threshold is passed (currently 100 logged events).</li>
 * <li>when a time threshold is passed (currently 15 seconds).</li>
 * <li>when an app has gone to background and is then brought back to the foreground.</li>
 * </ul>
 * <li>
 * Events will be accumulated when the app is in a disconnected state, and sent when the connection
 * is restored and one of the above 'flush' conditions are met.
 * </li>
 * <li>
 * The AppEventsLogger class is intended to be used from the thread it was created on.  Multiple
 * AppEventsLoggers may be created on other threads if desired.
 * </li>
 * <li>
 * The developer can call the setFlushBehavior method to force the flushing of events to only
 * occur on an explicit call to the `flush` method.
 * </li>
 * <li>
 * The developer can turn on console debug output for event logging and flushing to the server by
 * calling FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS);
 * </li>
 * </ul>
 * </p>
 * <p>
 * Some things to note when logging events:
 * <ul>
 * <li>
 * There is a limit on the number of unique event names an app can use, on the order of 1000.
 * </li>
 * <li>
 * There is a limit to the number of unique parameter names in the provided parameters that can
 * be used per event, on the order of 25.  This is not just for an individual call, but for all
 * invocations for that eventName.
 * </li>
 * <li>
 * Event names and parameter names must be between 2 and 40
 * characters, and must consist of alphanumeric characters, _, -, or spaces.
 * </li>
 * <li>
 * The length of each parameter value can be no more than on the order of 100 characters.
 * </li>
 * </ul>
 * </p>
 */
public class AppEventsLogger {
    // Enums

    /**
     * Controls when an AppEventsLogger sends log events to the server
     */
    public enum FlushBehavior {
        /**
         * Flush automatically: periodically (every 15 seconds or after every 100 events), and
         * always at app reactivation. This is the default value.
         */
        AUTO,

        /**
         * Only flush when AppEventsLogger.flush() is explicitly invoked.
         */
        EXPLICIT_ONLY,
    }

    // Constants
    private static final String TAG = AppEventsLogger.class.getCanonicalName();

    private static final int APP_SUPPORTS_ATTRIBUTION_ID_RECHECK_PERIOD_IN_SECONDS = 60 * 60 * 24;
    private static final int FLUSH_APP_SESSION_INFO_IN_SECONDS = 30;

    public static final String APP_EVENT_PREFERENCES = "com.facebook.sdk.appEventPreferences";

    private static final String SOURCE_APPLICATION_HAS_BEEN_SET_BY_THIS_INTENT =
            "_fbSourceApplicationHasBeenSet";

    private static final String PUSH_PAYLOAD_KEY = "fb_push_payload";
    private static final String PUSH_PAYLOAD_CAMPAIGN_KEY = "campaign";

    private static final String APP_EVENT_NAME_PUSH_OPENED = "fb_mobile_push_opened";
    private static final String APP_EVENT_PUSH_PARAMETER_CAMPAIGN = "fb_push_campaign";
    private static final String APP_EVENT_PUSH_PARAMETER_ACTION = "fb_push_action";

    private static ScheduledThreadPoolExecutor backgroundExecutor;
    private static FlushBehavior flushBehavior = FlushBehavior.AUTO;
    private static Object staticLock = new Object();
    private static String anonymousAppDeviceGUID;
    private static String sourceApplication;
    private static boolean isOpenedByApplink;
    private static boolean isActivateAppEventRequested;
    private static String pushNotificationsRegistrationId;

    /**
     * Notifies the events system that the app has launched and activate and deactivate events
     * should start being logged automatically. This should be called from the OnCreate method
     * of you application.
     *
     * @param application The running application
     */
    public static void activateApp(Application application) {
        activateApp(application, null);
    }

    /**
     * Notifies the events system that the app has launched and activate and deactivate events
     * should start being logged automatically. This should be called from the OnCreate method
     * of you application.
     *
     * Call this if you wish to use a different Application ID then the one specified in the
     * Facebook SDK.
     *
     * @param application The running application
     * @param applicationId The application id used to log activate/deactivate events.
     */
    public static void activateApp(Application application, String applicationId) {
        if (!FacebookSdk.isInitialized()) {
            throw new FacebookException("The Facebook sdk must be initialized before calling " +
                    "activateApp");
        }

    }


    /**
     * Build an AppEventsLogger instance to log events through.  The Facebook app that these events
     * are targeted at comes from this application's metadata. The application ID used to log events
     * will be determined from the app ID specified in the package metadata.
     *
     * @param context Used to access the applicationId and the attributionId for non-authenticated
     *                users.
     * @return AppEventsLogger instance to invoke log* methods on.
     */
    public static AppEventsLogger newLogger(Context context) {
        return new AppEventsLogger(context, null, null);
    }

    /**
     * Build an AppEventsLogger instance to log events through.
     *
     * @param context Used to access the attributionId for non-authenticated users.
     * @param accessToken Access token to use for logging events. If null, the active access token
     *                    will be used, if any; if not the logging will happen against the default
     *                    app ID specified in the package metadata.
     */
    public static AppEventsLogger newLogger(Context context, AccessToken accessToken) {
        return new AppEventsLogger(context, null, accessToken);
    }

    /**
     * Build an AppEventsLogger instance to log events through.
     *
     * @param context       Used to access the attributionId for non-authenticated users.
     * @param applicationId Explicitly specified Facebook applicationId to log events against.  If
     *                      null, the default app ID specified in the package metadata will be
     *                      used.
     * @param accessToken   Access token to use for logging events. If null, the active access token
     *                      will be used, if any; if not the logging will happen against the default
     *                      app ID specified in the package metadata.
     * @return AppEventsLogger instance to invoke log* methods on.
     */
    public static AppEventsLogger newLogger(
            Context context,
            String applicationId,
            AccessToken accessToken) {
        return new AppEventsLogger(context, applicationId, accessToken);
    }

    /**
     * Build an AppEventsLogger instance to log events that are attributed to the application but
     * not to any particular Session.
     *
     * @param context       Used to access the attributionId for non-authenticated users.
     * @param applicationId Explicitly specified Facebook applicationId to log events against.  If
     *                      null, the default app ID specified in the package metadata will be
     *                      used.
     * @return AppEventsLogger instance to invoke log* methods on.
     */
    public static AppEventsLogger newLogger(Context context, String applicationId) {
        return new AppEventsLogger(context, applicationId, null);
    }

    /**
     * The action used to indicate that a flush of app events has occurred. This should
     * be used as an action in an IntentFilter and BroadcastReceiver registered with
     * the {@link android.support.v4.content.LocalBroadcastManager}.
     */
    public static final String ACTION_APP_EVENTS_FLUSHED = "com.facebook.sdk.APP_EVENTS_FLUSHED";

    /**
     * Access the behavior that AppEventsLogger uses to determine when to flush logged events to the
     * server. This setting applies to all instances of AppEventsLogger.
     *
     * @return Specified flush behavior.
     */
    public static FlushBehavior getFlushBehavior() {
        synchronized (staticLock) {
            return flushBehavior;
        }
    }

    /**
     * Set the behavior that this AppEventsLogger uses to determine when to flush logged events to
     * the server. This setting applies to all instances of AppEventsLogger.
     *
     * @param flushBehavior the desired behavior.
     */
    public static void setFlushBehavior(FlushBehavior flushBehavior) {
        synchronized (staticLock) {
            AppEventsLogger.flushBehavior = flushBehavior;
        }
    }

    /**
     * Log an app event with the specified name.
     *
     * @param eventName eventName used to denote the event.  Choose amongst the EVENT_NAME_*
     *                  constants in when possible.  Or create your own
     *                  if none of the EVENT_NAME_* constants are applicable. Event names should be
     *                  40 characters or less, alphanumeric, and can include spaces, underscores or
     *                  hyphens, but must not have a space or hyphen as the first character.  Any
     *                  given app should have no more than 1000 distinct event names.
     */
    public void logEvent(String eventName) {
        logEvent(eventName, null);
    }


    /**
     * Log an app event with the specified name and set of parameters.
     *
     * @param eventName  eventName used to denote the event.  Choose amongst the EVENT_NAME_*
     *                   constants in when possible.  Or create your own
     *                   if none of the EVENT_NAME_* constants are applicable. Event names should be
     *                   40 characters or less, alphanumeric, and can include spaces, underscores or
     *                   hyphens, but must not have a space or hyphen as the first character.  Any
     *                   given app should have no more than 1000 distinct event names.
     * @param parameters A Bundle of parameters to log with the event.  Insights will allow looking
     *                   at the logs of these events via different parameter values.  You can log on
     *                   the order of 25 parameters with each distinct eventName.  It's advisable to
     *                   limit the number of unique values provided for each parameter in the
     *                   thousands.  As an example, don't attempt to provide a unique
     *                   parameter value for each unique user in your app.  You won't get meaningful
     *                   aggregate reporting on so many parameter values.  The values in the bundles
     *                   should be Strings or numeric values.
     */
    public void logEvent(String eventName, Bundle parameters) {

    }

    /**
     * Explicitly flush any stored events to the server.  Implicit flushes may happen depending on
     * the value of getFlushBehavior.  This method allows for explicit, app invoked flushing.
     */
    public void flush() {

    }


    /**
     * This method is intended only for internal use by the Facebook SDK and other use is
     * unsupported.
     */
    public void logSdkEvent(String eventName, Double valueToSum, Bundle parameters) {

    }



    //
    // Private implementation
    //

    /**
     * Constructor is private, newLogger() methods should be used to build an instance.
     */
    private AppEventsLogger(Context context, String applicationId, AccessToken accessToken) {
        this(
                Utility.getActivityName(context),
                applicationId,
                accessToken);
    }

    protected AppEventsLogger(
            String activityName,
            String applicationId,
            AccessToken accessToken) {

    }


    private void logEvent(
            String eventName,
            Double valueToSum,
            Bundle parameters,
            boolean isImplicitlyLogged,
            @Nullable final UUID currentSessionId) {

    }

    /**
     * Each app/device pair gets an GUID that is sent back with App Events and persisted with this
     * app/device pair.
     * @param context The application context.
     * @return The GUID for this app/device pair.
     */
    public static String getAnonymousAppDeviceGUID(Context context) {

        if (anonymousAppDeviceGUID == null) {
            synchronized (staticLock) {
                if (anonymousAppDeviceGUID == null) {

                    SharedPreferences preferences = context.getSharedPreferences(
                            APP_EVENT_PREFERENCES,
                            Context.MODE_PRIVATE);
                    anonymousAppDeviceGUID = preferences.getString("anonymousAppDeviceGUID", null);
                    if (anonymousAppDeviceGUID == null) {
                        // Arbitrarily prepend XZ to distinguish from device supplied identifiers.
                        anonymousAppDeviceGUID = "XZ" + UUID.randomUUID().toString();

                        context.getSharedPreferences(APP_EVENT_PREFERENCES, Context.MODE_PRIVATE)
                                .edit()
                                .putString("anonymousAppDeviceGUID", anonymousAppDeviceGUID)
                                .apply();
                    }
                }
            }
        }

        return anonymousAppDeviceGUID;
    }

}

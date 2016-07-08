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

package com.facebook;

import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.test.suitebuilder.annotation.LargeTest;

import com.facebook.internal.GraphUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RequestTests extends FacebookTestCase {
    private static final String TEST_OG_OBJECT_TYPE = "facebooksdktests:test";
    private static final String TEST_OG_ACTION_TYPE = "facebooksdktests:run";
    private static final long REQUEST_TIMEOUT_MILLIS = 10000;

    protected String[] getDefaultPermissions()
    {
        return new String[] {
                "email",
                "publish_actions",
                "user_posts",
                "user_photos",
                "user_videos" };
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();
        AccessToken.setCurrentAccessToken(getAccessTokenForSharedUser());
    }

    @Override
    public void tearDown() throws Exception {
        AccessToken.setCurrentAccessToken(null);
        super.tearDown();
    }

    @LargeTest
    public void testExecuteSingleGet() {
        Bundle parameters = new Bundle();
        parameters.putString("fields", "location");

        GraphRequest request = new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                "TourEiffel",
                parameters,
                null);
        GraphResponse response = request.executeAndWait();

        assertTrue(response != null);
        assertTrue(response.getError() == null);
        assertNotNull(response.getJSONObject());
        assertNotNull(response.getRawResponse());

        JSONObject graphPlace = response.getJSONObject();
        assertEquals("Paris", graphPlace.optJSONObject("location").optString("city"));
    }



    @LargeTest
    public void testExecuteSingleGetUsingHttpURLConnection() throws IOException {
        Bundle parameters = new Bundle();
        parameters.putString("fields", "location");

        GraphRequest request = new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                "TourEiffel",
                parameters,
                null);
        HttpURLConnection connection = GraphRequest.toHttpConnection(request);

        assertEquals("gzip", connection.getRequestProperty("Content-Encoding"));
        assertEquals(
                "application/x-www-form-urlencoded",
                connection.getRequestProperty("Content-Type"));

        List<GraphResponse> responses = GraphRequest.executeConnectionAndWait(
                connection,
                Arrays.asList(new GraphRequest[]{request}));
        assertNotNull(responses);
        assertEquals(1, responses.size());

        GraphResponse response = responses.get(0);

        assertTrue(response != null);
        assertTrue(response.getError() == null);
        assertNotNull(response.getJSONObject());
        assertNotNull(response.getRawResponse());

        JSONObject graphPlace = response.getJSONObject();
        assertEquals("Paris", graphPlace.optJSONObject("location").optString("city"));

        // Make sure calling code can still access HTTP headers and call disconnect themselves.
        int code = connection.getResponseCode();
        assertEquals(200, code);
        assertTrue(connection.getHeaderFields().keySet().contains("Content-Type"));
        connection.disconnect();
    }

    @LargeTest
    public void testFacebookErrorResponseCreatesError() {
        GraphRequest request = new GraphRequest(null, "somestringthatshouldneverbeavalidfobjectid");
        GraphResponse response = request.executeAndWait();

        assertTrue(response != null);

        FacebookRequestError error = response.getError();
        assertNotNull(error);
        FacebookException exception = error.getException();
        assertNotNull(exception);

        assertTrue(exception instanceof FacebookServiceException);
        assertNotNull(error.getErrorType());
        assertTrue(error.getErrorCode() != FacebookRequestError.INVALID_ERROR_CODE);
        assertNotNull(error.getRequestResultBody());
    }

    @LargeTest
    public void testRequestWithNoTokenFails() {
        GraphRequest request = new GraphRequest(null, "me");
        GraphResponse response = request.executeAndWait();

        assertNotNull(response.getError());
    }

    @LargeTest
    public void testExecuteRequestMe() {
        GraphRequest request = GraphRequest.newMeRequest(AccessToken.getCurrentAccessToken(), null);
        GraphResponse response = request.executeAndWait();

        validateMeResponse(AccessToken.getCurrentAccessToken(), response);
    }

    static void validateMeResponse(AccessToken accessToken, GraphResponse response) {
        assertNull(response.getError());

        JSONObject me = response.getJSONObject();
        assertNotNull(me);
        assertEquals(accessToken.getUserId(), me.optString("id"));
        assertNotNull(response.getRawResponse());
    }

    @LargeTest
    public void testExecuteMyFriendsRequest() {
        GraphRequest request =
                GraphRequest.newMyFriendsRequest(AccessToken.getCurrentAccessToken(), null);
        GraphResponse response = request.executeAndWait();

        validateMyFriendsResponse(response);
    }

    static void validateMyFriendsResponse(GraphResponse response) {
        assertNotNull(response);

        assertNull(response.getError());

        JSONObject graphResult = response.getJSONObject();
        assertNotNull(graphResult);

        JSONArray results = graphResult.optJSONArray("data");
        assertNotNull(results);

        assertNotNull(response.getRawResponse());
    }

    @LargeTest
    public void testExecutePlaceRequestWithLocation() {
        Location location = new Location("");
        location.setLatitude(47.6204);
        location.setLongitude(-122.3491);

        GraphRequest request = GraphRequest.newPlacesSearchRequest(
                AccessToken.getCurrentAccessToken(),
                location,
                5,
                5,
                null,
                null);
        GraphResponse response = request.executeAndWait();
        assertNotNull(response);

        assertNull(response.getError());

        JSONObject graphResult = response.getJSONObject();
        assertNotNull(graphResult);

        JSONArray results = graphResult.optJSONArray("data");
        assertNotNull(results);

        assertNotNull(response.getRawResponse());
    }

    @LargeTest
    public void testExecutePlaceRequestWithSearchText() {
        // Pass a distance without a location to ensure it is correctly ignored.
        GraphRequest request = GraphRequest.newPlacesSearchRequest(
                AccessToken.getCurrentAccessToken(),
                null,
                1000,
                5,
                "Starbucks",
                null);
        GraphResponse response = request.executeAndWait();
        assertNotNull(response);

        assertNull(response.getError());

        JSONObject graphResult = response.getJSONObject();
        assertNotNull(graphResult);

        JSONArray results = graphResult.optJSONArray("data");
        assertNotNull(results);

        assertNotNull(response.getRawResponse());
    }

    @LargeTest
    public void testExecutePlaceRequestWithLocationAndSearchText() {
        Location location = new Location("");
        location.setLatitude(47.6204);
        location.setLongitude(-122.3491);

        GraphRequest request = GraphRequest.newPlacesSearchRequest(
                AccessToken.getCurrentAccessToken(),
                location,
                1000,
                5,
                "Starbucks",
                null);
        GraphResponse response = request.executeAndWait();
        assertNotNull(response);

        assertNull(response.getError());

        JSONObject graphResult = response.getJSONObject();
        assertNotNull(graphResult);

        JSONArray results = graphResult.optJSONArray("data");
        assertNotNull(results);

        assertNotNull(response.getRawResponse());
    }



    private String executePostOpenGraphRequest() {
        JSONObject data = new JSONObject();
        try {
            data.put("a_property", "hello");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        JSONObject ogObject = GraphUtil.createOpenGraphObjectForPost(
                TEST_OG_OBJECT_TYPE,
                "a title",
                "http://www.facebook.com",
                "http://www.facebook.com/zzzzzzzzzzzzzzzzzzz",
                "a description",
                data,
                null);

        Bundle bundle = new Bundle();
        bundle.putString("object", ogObject.toString());
        GraphRequest request = new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                "/me/objects/" + TEST_OG_OBJECT_TYPE,
                bundle,
                HttpMethod.POST,
                null);

        GraphResponse response = request.executeAndWait();
        assertNotNull(response);

        assertNull(response.getError());

        JSONObject graphResult = response.getJSONObject();
        assertNotNull(graphResult);
        assertNotNull(graphResult.optString("id"));

        assertNotNull(response.getRawResponse());

        return graphResult.optString("id");
    }

    @LargeTest
    public void testExecutePostOpenGraphRequest() {
        executePostOpenGraphRequest();
    }



    @LargeTest
    public void testDeleteObjectRequest() {
        String id = executePostOpenGraphRequest();

        GraphRequest request = GraphRequest.newDeleteObjectRequest(
                AccessToken.getCurrentAccessToken(),
                id,
                null);
        GraphResponse response = request.executeAndWait();
        assertNotNull(response);

        assertNull(response.getError());

        JSONObject result = response.getJSONObject();
        assertNotNull(result);

        assertTrue(result.optBoolean(GraphResponse.SUCCESS_KEY));
        assertNotNull(response.getRawResponse());
    }

    @LargeTest
    public void testUpdateOpenGraphObjectRequest() throws JSONException {
        String id = executePostOpenGraphRequest();

        JSONObject data = new JSONObject();
        data.put("a_property", "goodbye");

        JSONObject ogObject = GraphUtil.createOpenGraphObjectForPost(
                TEST_OG_OBJECT_TYPE,
                "another title",
                null,
                "http://www.facebook.com/aaaaaaaaaaaaaaaaa",
                "another description",
                data,
                null);
        Bundle bundle = new Bundle();
        bundle.putString("object", ogObject.toString());
        GraphRequest request = new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                id,
                bundle,
                HttpMethod.POST,
                null);
        GraphResponse response = request.executeAndWait();
        assertNotNull(response);

        assertNull(response.getError());

        JSONObject result = response.getJSONObject();
        assertNotNull(result);
        assertEquals("another title", result.optString("title"));
        assertNotNull(response.getRawResponse());
    }



    @LargeTest
    public void testPostStatusUpdate() {
        JSONObject statusUpdate = createStatusUpdate("");

        JSONObject retrievedStatusUpdate = postGetAndAssert(
                AccessToken.getCurrentAccessToken(),
                "me/feed",
                statusUpdate);

        assertEquals(statusUpdate.optString("message"), retrievedStatusUpdate.optString("message"));
    }

    @LargeTest
    public void testCallbackIsCalled() {
        GraphRequest request = new GraphRequest(null, "4");

        final ArrayList<Boolean> calledBack = new ArrayList<Boolean>();
        request.setCallback(new GraphRequest.Callback() {
            @Override
            public void onCompleted(GraphResponse response) {
                calledBack.add(true);
            }
        });

        GraphResponse response = request.executeAndWait();
        assertNotNull(response);
        assertTrue(calledBack.size() == 1);
    }



    @LargeTest
    public void testBatchTimeoutIsApplied() {
        GraphRequest request = new GraphRequest(null, "me");
        GraphRequestBatch batch = new GraphRequestBatch(request);

        // We assume 5 ms is short enough to fail
        batch.setTimeout(1);

        List<GraphResponse> responses = GraphRequest.executeBatchAndWait(batch);
        assertNotNull(responses);
        assertTrue(responses.size() == 1);
        GraphResponse response = responses.get(0);
        assertNotNull(response);
        assertNotNull(response.getError());
    }

    @LargeTest
    public void testBatchTimeoutCantBeNegative() {
        try {
            GraphRequestBatch batch = new GraphRequestBatch();
            batch.setTimeout(-1);
            fail();
        } catch (IllegalArgumentException ex) {
        }
    }

    @LargeTest
    public void testCantUseComplexParameterInGetRequest() {
        Bundle parameters = new Bundle();
        parameters.putShortArray("foo", new short[1]);

        GraphRequest request = new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                "me",
                parameters,
                HttpMethod.GET,
                new ExpectFailureCallback());
        GraphResponse response = request.executeAndWait();

        FacebookRequestError error = response.getError();
        assertNotNull(error);
        FacebookException exception = error.getException();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("short[]"));
    }

    private final Location SEATTLE_LOCATION = new Location("") {
        {
            setLatitude(47.6097);
            setLongitude(-122.3331);
        }
    };

    @LargeTest
    public void testPaging() {
        final List<JSONObject> returnedPlaces = new ArrayList<JSONObject>();
        GraphRequest request = GraphRequest
                .newPlacesSearchRequest(
                        AccessToken.getCurrentAccessToken(),
                        SEATTLE_LOCATION,
                        1000,
                        3,
                        null,
                        new GraphRequest.GraphJSONArrayCallback() {
                            @Override
                            public void onCompleted(JSONArray places, GraphResponse response) {
                                if (places == null) {
                                    assertNotNull(places);
                                }
                                for (int i = 0; i < places.length(); ++i) {
                                    returnedPlaces.add(places.optJSONObject(i));
                                }
                            }
                        });
        GraphResponse response = request.executeAndWait();

        assertNull(response.getError());
        assertNotNull(response.getJSONObject());
        assertNotSame(0, returnedPlaces.size());

        returnedPlaces.clear();

        GraphRequest nextRequest = response.getRequestForPagedResults(GraphResponse.PagingDirection.NEXT);
        assertNotNull(nextRequest);

        nextRequest.setCallback(request.getCallback());
        response = nextRequest.executeAndWait();

        assertNull(response.getError());
        assertNotNull(response.getJSONObject());
        assertNotSame(0, returnedPlaces.size());

        returnedPlaces.clear();

        GraphRequest previousRequest = response.getRequestForPagedResults(GraphResponse.PagingDirection.PREVIOUS);
        assertNotNull(previousRequest);

        previousRequest.setCallback(request.getCallback());
        response = previousRequest.executeAndWait();

        assertNull(response.getError());
        assertNotNull(response.getJSONObject());
        assertNotSame(0, returnedPlaces.size());
    }
}

package com.cloudelements.nexus.consumer;

import com.cloudelements.nexus.model.DispatcherResponse;
import com.cloudelements.nexus.util.Constants;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.RequestBodyEntity;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StopWatch;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AbstractNexusConsumerApiTest {

    private AbstractNexusConsumerApi<Map> abstractNexusConsumerApi;

    private AbstractNexusConsumerApi<Map> consumerApi;

    @Mock
    private HttpRequestWithBody requestWithBody;

    @Mock
    private RequestBodyEntity requestBodyEntity;

    MockedStatic uniRest;

    HttpHeaders headers;


    @BeforeEach
    public void init() {
        abstractNexusConsumerApi = new AbstractNexusConsumerApi<Map>() {
            @Override
            protected void handle(String queueName, Map message) {
                //do nothing
            }
        };
        ReflectionTestUtils.setField(abstractNexusConsumerApi, "enableDelayNotification", true);
        ReflectionTestUtils.setField(abstractNexusConsumerApi, "nexusApiKey", "nexusApiKey");
        headers = new HttpHeaders();
        headers.add(Constants.X_PRIVATE_API_KEY, "nexusApiKey");
        consumerApi = spy(abstractNexusConsumerApi);
    }

    @Test
    public void testOnMessage() {
        ResponseEntity<DispatcherResponse> expectedResponse = ResponseEntity.ok(new DispatcherResponse());
        doReturn(expectedResponse).when(consumerApi).buildAndReturn(anyString(), any(StopWatch.class),
                any(HttpHeaders.class), any(HttpStatus.class));
        ResponseEntity<DispatcherResponse> actualResponse = consumerApi.onMessage("test", headers,
                new HashMap<>());
        assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
        assertEquals(expectedResponse.getBody(), actualResponse.getBody());
    }

    @Test
    public void testOnMessageForUnauthorized() throws Exception {
        ResponseEntity<DispatcherResponse> expectedResponse =
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new DispatcherResponse());
        headers = new HttpHeaders();
        headers.add(Constants.X_PRIVATE_API_KEY, "wrongKey");
        doReturn(expectedResponse).when(consumerApi).buildAndReturn(anyString(), any(StopWatch.class),
                any(HttpHeaders.class), any(HttpStatus.class));
        ResponseEntity<DispatcherResponse> actualResponse = consumerApi.onMessage("test", headers,
                new HashMap<>());
        assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());

        actualResponse = consumerApi.onMessage("test", new HttpHeaders(), new HashMap<>());
        assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    }
    @Test
    public void testOnMessageWithException() throws Exception {
        ResponseEntity<DispatcherResponse> expectedResponse =
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new DispatcherResponse());

        doThrow(new RuntimeException("This Root cause")).when(consumerApi).handle(anyString(), any(Map.class));
        doReturn(expectedResponse).when(consumerApi).buildAndReturn(anyString(), any(StopWatch.class),
                any(HttpHeaders.class), any(HttpStatus.class));
        ResponseEntity<DispatcherResponse> actualResponse = consumerApi.onMessage("test", headers,
                new HashMap<>());
        assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
        assertEquals(expectedResponse.getBody(), actualResponse.getBody());
    }

    @Test
    public void testBuildAndReturn() throws InterruptedException {
        StopWatch watch = new StopWatch();
        watch.start();
        Thread.sleep(10);
        watch.stop();
        ResponseEntity<DispatcherResponse> responseEntity = consumerApi.buildAndReturn("test", watch,
                headers, HttpStatus.OK);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    }


    @Test
    public void testBuildAndReturnWithTimeOut() throws InterruptedException {
        String message = "{\"message\": \"test\"}";
        doReturn(new JsonNode(message)).when(consumerApi).notifyOnDelay(anyString(), any(HttpHeaders.class));
        StopWatch watch = new StopWatch();
        watch.start();
        Thread.sleep(70000);
        watch.stop();
        ResponseEntity<DispatcherResponse> responseEntity = consumerApi.buildAndReturn("test", watch,
                headers, HttpStatus.BAD_REQUEST);
        DispatcherResponse dispatcherResponse = responseEntity.getBody();
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
    }

    @Test
    public void testBuildAndReturnWithTimeOutWithDisabledMode() throws InterruptedException {
        ReflectionTestUtils.setField(abstractNexusConsumerApi, "enableDelayNotification", false);
        String message = "{\"message\": \"test\"}";
        doReturn(new JsonNode(message)).when(consumerApi).notifyOnDelay(anyString(), any(HttpHeaders.class));
        StopWatch watch = new StopWatch();
        watch.start();
        Thread.sleep(70000);
        watch.stop();
        ResponseEntity<DispatcherResponse> responseEntity = consumerApi.buildAndReturn("test", watch,
                headers, HttpStatus.BAD_REQUEST);
        DispatcherResponse dispatcherResponse = responseEntity.getBody();
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
    }

    @Test
    public void testNotifyOnDelay() throws UnirestException, UnsupportedEncodingException {
        uniRest = mockStatic(Unirest.class);
        HttpResponseFactory factory = new DefaultHttpResponseFactory();
        org.apache.http.HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
                200, null), null);
        JSONObject json = new JSONObject();
        json.put("message", "test");
        response.setEntity(new StringEntity(json.toString()));
        HttpResponse<JsonNode> httpResponse = new HttpResponse<JsonNode>(response, JsonNode.class);
        uniRest.when(() -> Unirest.post(anyString())).thenReturn(requestWithBody);
        when(requestWithBody.header(anyString(), anyString())).thenReturn(requestWithBody);
        when(requestWithBody.body(anyMap())).thenReturn(requestBodyEntity);
        when(requestBodyEntity.asJson()).thenReturn(httpResponse);
        JsonNode jsonNode = consumerApi.notifyOnDelay("test", new HttpHeaders());
        assertNotNull(jsonNode);
    }

    @Test
    public void testNotifyOnDelayWithException() throws UnirestException {
        uniRest = mockStatic(Unirest.class);
        uniRest.when(() -> Unirest.post(anyString())).thenReturn(requestWithBody);
        when(requestWithBody.header(anyString(), anyString())).thenReturn(requestWithBody);
        when(requestWithBody.body(anyMap())).thenReturn(requestBodyEntity);
        when(requestBodyEntity.asJson()).thenThrow(new UnirestException("Unable get body"));
        JsonNode jsonNode = consumerApi.notifyOnDelay("test", new HttpHeaders());
        assertNotNull(jsonNode);
    }

    @AfterEach
    public void close() {
        if (uniRest != null) {
            uniRest.close();
        }
    }
}

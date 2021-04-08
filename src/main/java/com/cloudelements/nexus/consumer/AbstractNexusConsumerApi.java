package com.cloudelements.nexus.consumer;

import com.cloudelements.nexus.exception.ServiceException;
import com.cloudelements.nexus.model.DispatcherResponse;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;

import java.util.logging.Logger;

import static com.cloudelements.nexus.util.Constants.CAUSE;
import static com.cloudelements.nexus.util.Constants.FAILURE;
import static com.cloudelements.nexus.util.Constants.STATUS;
import static com.cloudelements.nexus.util.Constants.SUCCESS;
import static com.cloudelements.nexus.util.Constants.X_PRIVATE_API_KEY;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author venkat
 * This abstract consumer services for nexus services with push notification approach.
 * @param <T> type of message is received from nexus platform.
 * Usage of Class.
 *           Example: Here i would like consumer event from Nexus.
 *  @RestController
 *  @RequestMapping(path = "/event")
 *  public class NexusIncomingEventConsumerApi extends AbstractNexusConsumerApi<Map<String, Object>> {
 *
 *      @Api(requiresUserOrgAuth = false)
 *     @RequestMapping(value = { "/dispatch/{queueName}"  }, method = RequestMethod.POST,
 *             consumes =  {APPLICATION_JSON_VALUE, APPLICATION_X_PROTOBUF})
 *     public ResponseEntity<DispatcherResponse> onMessage(@PathVariable String queueName, @RequestHeader HttpHeaders
 *     headers, @RequestBody Message message) {
 *         return super.onMessage(queueName, headers, message);
 *     }
 *
 *     @Override
 *     protected void handle(String queueName, Map<String,Object> message) throw Exception {
 *         //Do work with message and handle it properly
 *         //throw Exception if something wrong during process.
 *     }
 *   }
 */
public abstract class AbstractNexusConsumerApi<T> {

    private final Logger logger = Logger.getLogger(AbstractNexusConsumerApi.class.getName());

    @Value("${nexus.enable.delay.notification:false}")
    private boolean enableDelayNotification;

    @Value("${nexus.service.wait.timeout:60}")
    private final Integer nexusServiceWaitTimeout = 60;

    @Value("${nexus.api.key}")
    private String nexusApiKey;

    @Value("${nexus.url:http://localhost:8080}")
    private String nexusHost;

    private final String requestDelayNotificationUri = "/nexus/dispatch/notifyOnDelay/";

    private static final String ERROR_PREFIX = "Dispatcher-Error";

    private static final  String MSG_PREFIX = "Dispatcher-Response";

    private static final String MSG_FORMAT = "%s : status: %d";

    private static final String ERROR_MSG_FORMAT = "%s : Error while notifying Nexus service on API Delays. Reason : " +
            "%s";

    protected abstract void handle(String queueName, T message) throws Exception;

    /**
     * This class entry point for message consumer.
     * @param queueName queue name. which is determine where message coming from.
     * @param headers additional headers which will be used trace purpose.
     * @param message message object.
     * @return dispatcher response.
     */
    public ResponseEntity<DispatcherResponse> onMessage(String queueName, HttpHeaders headers, T message) {
        StopWatch watch = new StopWatch();
        watch.start();
        try {
            //Here allow only authentication message.
            allowOnlyAuthenticatedMessage(headers);
            //Here calling sync handle of message.
            handle(queueName, message);
            watch.stop();
            //Return response message based total wait or execution time.
            return buildAndReturn(queueName, watch, headers, HttpStatus.OK);
        } catch (Exception e) {
            watch.stop();
            headers.add(CAUSE, e.getMessage());
            //anyways it's exception. its might be failed with error.
            HttpStatus httpStatus = e instanceof ServiceException ? ((ServiceException) e).getStatusCode() :
                    HttpStatus.BAD_REQUEST;
           return buildAndReturn(queueName, watch, headers, httpStatus);
        }
    }

    /**
     * This method will only allow authentication message.
     * @param headers http headers which contains request details.
     */
    protected void allowOnlyAuthenticatedMessage(HttpHeaders headers) {
        if(!headers.containsKey(X_PRIVATE_API_KEY)) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "Missing private api key in request");
        }
        String privateApiKey = headers.getFirst(X_PRIVATE_API_KEY);
        if(privateApiKey == null || privateApiKey.isBlank() || !privateApiKey.equalsIgnoreCase(nexusApiKey)) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "Invalid private api key in request");
        }
    }

    /**
     * This method will build response and return response based total executions information.
     * @param queueName queue name.
     * @param watch stop watch.
     * @param headers http headers.
     * @param httpStatus http status
     * @return response entity will return as consumer api response.
     */
    protected ResponseEntity<DispatcherResponse> buildAndReturn(String queueName, StopWatch watch, HttpHeaders headers,
                                                              HttpStatus httpStatus) {
        DispatcherResponse dispatcherResponse = new DispatcherResponse();
        dispatcherResponse.setStatus(httpStatus);
        String status = httpStatus.isError() ? FAILURE : SUCCESS;
        if (enableDelayNotification && isWaitTimeOutMessage(watch)) {
            //notify to nexus for delay process.
            headers.add(STATUS, status);
            notifyOnDelay(queueName, headers);
        }
        dispatcherResponse.setInfo(headers.toSingleValueMap());
        return ResponseEntity.status(httpStatus).body(dispatcherResponse);
    }

    /**
     * This method will determine message exceeds the max wait time.
     * @param watch stop watch reference to calculate total time for message consume process.
     * @return either true or false.
     */
    private boolean isWaitTimeOutMessage(StopWatch watch) {
        return watch.getTotalTimeMillis() > nexusServiceWaitTimeout * 1000;
    }

    /**
     * If message of consume took long time which is more than max wait time. Nexus dispatcher will already
     * disconnected and store message on S3 for process. If this consumer is done even after max wait time then using
     * this method its will be notify the nexus what happened for this process with status. So Nexus platform will
     * either delete or update status based this API.
     * @param queueName queue name.
     * @param headers headers which is received as part of consumer api.
     * @return response of notify On delay api.
     */
    protected JsonNode notifyOnDelay(String queueName, HttpHeaders headers) {
        try {
            String fullyQualifiedUrl = nexusHost + requestDelayNotificationUri + queueName;
            HttpResponse<JsonNode> jsonResponse = Unirest.post(fullyQualifiedUrl)
                    .header(HttpHeaders.ACCEPT, APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .body(headers.toSingleValueMap())
                    .asJson();
            logger.info(String.format(MSG_FORMAT, MSG_PREFIX, jsonResponse.getStatus()));
            return jsonResponse.getBody();
        } catch (UnirestException e) {
            String errorMessage = String.format(ERROR_MSG_FORMAT, ERROR_PREFIX, e.getMessage());
            logger.info(errorMessage);
            String error = "{\"error\": \""+ errorMessage + "\"}";
            return new JsonNode(error);
        }
    }
}

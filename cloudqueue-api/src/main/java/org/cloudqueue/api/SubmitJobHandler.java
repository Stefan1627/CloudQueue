package org.cloudqueue.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SubmitJobHandler implements RequestHandler<Map<String, Object>, Map<String, Object>>{
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TABLE_NAME = "CloudQueueJobs";
    private static final String QUEUE_URL = "https://sqs.eu-north-1.amazonaws.com/922231562082/cloudqueue-jobs";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final SqsClient sqs = SqsClient.create();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try{
            String body = (String) event.get("body");
            Map<String, Object> payload = mapper.readValue(body, MAP_TYPE);

            String jobType = (String) payload.get("type");
            Object jobPayloadObj = payload.get("payload");
            String jobPayloadJson = mapper.writeValueAsString(jobPayloadObj);

            if (jobType == null || jobPayloadJson == null) {
                return response(400, "Missing required fields: type, payload");
            }

            String jobId = UUID.randomUUID().toString();
            String now = Instant.now().toString();

            // 1. Insert job into DynamoDB
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("jobId", AttributeValue.fromS(jobId));
            item.put("status", AttributeValue.fromS("QUEUED"));
            item.put("type", AttributeValue.fromS(jobType));
            item.put("payload", AttributeValue.fromS(jobPayloadJson));
            item.put("createdAt", AttributeValue.fromS(now));
            item.put("updatedAt", AttributeValue.fromS(now));

            dynamoDb.putItem(
                    PutItemRequest.builder()
                            .tableName(TABLE_NAME)
                            .item(item)
                            .build()
            );

            Map<String, Object> sqsMessage = new HashMap<>();
            sqsMessage.put("jobId", jobId);
            sqsMessage.put("type", jobType);
            sqsMessage.put("payload", jobPayloadObj);

            sqs.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(QUEUE_URL)
                            .messageBody(mapper.writeValueAsString(sqsMessage))
                            .build()
            );

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("jobId", jobId);
            responseBody.put("status", "QUEUED");

            return response(202, mapper.writeValueAsString(responseBody));
        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return response(500, "Internal server error");
        }
    }

    private Map<String, Object> response(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", body);
        return response;
    }
}

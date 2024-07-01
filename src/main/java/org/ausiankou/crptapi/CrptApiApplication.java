package org.ausiankou.crptapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


@SpringBootApplication
public class CrptApiApplication {

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 10);
        String document = "{\"description\": { \"participantInn\": \"1234567890\" }, \"doc_id\": \"doc123\", \"doc_status\": \"pending\", \"doc_type\": \"LP_INTRODUCE_GOODS\", \"importRequest\": true, \"owner_inn\": \"9876543210\", \"participant_inn\": \"1234567890\", \"producer_inn\": \"9876543210\", \"production_date\": \"2020-01-23\", \"production_type\": \"sample\", \"products\": [ { \"certificate_document\": \"cert123\", \"certificate_document_date\": \"2020-01-23\", \"certificate_document_number\": \"cert456\", \"owner_inn\": \"9876543210\", \"producer_inn\": \"9876543210\", \"production_date\": \"2020-01-23\", \"tnved_code\": \"tnved123\", \"uit_code\": \"uit123\", \"uitu_code\": \"uitu123\" } ], \"reg_date\": \"2020-01-23\", \"reg_number\": \"reg123\"}";
        String signature = "sample_signature";
        api.createDocument(document, signature);
    }
}
class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final long timeIntervalInMillis;
    private final Lock lock;
    private final AtomicInteger requestCount;
    private volatile long resetTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.timeIntervalInMillis = timeUnit.toMillis(1);
        this.lock = new ReentrantLock();
        this.requestCount = new AtomicInteger(0);
        this.resetTime = System.currentTimeMillis() + timeIntervalInMillis;
    }

    public void createDocument(String document, String signature) {
        boolean lockAcquired = false;
        try {
            lockAcquired = lock.tryLock();
            if (lockAcquired) {
                long currentTime = System.currentTimeMillis();
                if (currentTime > resetTime) {
                    requestCount.set(0);
                    resetTime = currentTime + timeIntervalInMillis;
                }

                int currentCount = requestCount.getAndIncrement();
                if (currentCount >= requestLimit) {
                    long sleepTime = resetTime - currentTime;
                    TimeUnit.MILLISECONDS.sleep(sleepTime);
                }
                
                performApiCall(document, signature);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread was interrupted: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO error occurred: " + e.getMessage());
            throw new RuntimeException("Failed to perform API call", e);
        } finally {
            if (lockAcquired) {
                lock.unlock();
            }
        }
    }

    private void performApiCall(String document, String signature) throws IOException {
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost request = new HttpPost("https://ismp.crpt.ru/api/v3/lk/documents/create");

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonDocument = objectMapper.writeValueAsString(document);

        StringEntity requestEntity = new StringEntity(jsonDocument, ContentType.APPLICATION_JSON);
        request.setEntity(requestEntity);

        request.setHeader("Authorization", "");

        HttpResponse response = httpClient.execute(request);

        int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity responseEntity = response.getEntity();
        if (responseEntity != null) {
            String responseBody = EntityUtils.toString(responseEntity);
            System.out.println("API response received: " + responseBody);
        }
    }
}



package com.api;

import com.github.javafaker.Faker;
import com.interfaces.PayLoadValidator;
import com.pojo.Transaction;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.testng.Assert;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class TransactionAPI extends BaseTest implements PayLoadValidator {
    private static final ThreadLocal<Faker> faker = ThreadLocal.withInitial(Faker::new);
    private static final ThreadLocal<Transaction> transaction = ThreadLocal.withInitial(Transaction::new);
    private static final ThreadLocal<Boolean> deletionSuccessful = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Response> latestResponse = new ThreadLocal<>();
    private static final ThreadLocal<Object> clientIdOverride = new ThreadLocal<>();
    private static final ThreadLocal<String> accountIdOverride = new ThreadLocal<>();

    @Override
    public void post() {
        Response response = given()
                .headers(returnAuthHeaders())
                .body(buildCreateBody())
                .when()
                .post(baseURI + allTransactionsEndPoint);
        latestResponse.set(response);
    }

    @Override
    public void patch() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", transaction.get().getId())
                .body(buildPatchBody())
                .when()
                .patch(baseURI + transactionsEndPoint);
        latestResponse.set(response);
    }

    @Override
    public void put() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", transaction.get().getId())
                .body(buildPutBody())
                .when()
                .put(baseURI + transactionsEndPoint);
        latestResponse.set(response);
    }

    @Override
    public void delete() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", transaction.get().getId())
                .when()
                .delete(baseURI + transactionsEndPoint);
        latestResponse.set(response);
        deletionSuccessful.set(response.statusCode() == 200);
    }

    @Override
    public JSONObject get() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", transaction.get().getId())
                .when()
                .get(baseURI + transactionsEndPoint);
        latestResponse.set(response);
        return new JSONObject(response.getBody().asString());
    }

    @Override
    public void validatePayload(JSONObject payload) {
        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("id", payload.getString("id"));
        responseMap.put("clientId", String.valueOf(payload.get("clientId")));
        responseMap.put("date", payload.getString("date"));
        responseMap.put("type", payload.getString("type"));
        responseMap.put("amount", payload.getInt("amount"));
        responseMap.put("currency", payload.getString("currency"));
        responseMap.put("accountId", payload.getString("accountId"));

        Map<String, Object> expectedMap = transaction.get().generatePojoMap();
        for (String attribute : getAttributeList()) {
            Object actualValue = responseMap.get(attribute);
            Object expectedValue = expectedMap.get(attribute);
            if ("clientId".equals(attribute)) {
                Assert.assertEquals(String.valueOf(actualValue), String.valueOf(expectedValue),
                        "Value mismatch for attribute: " + attribute);
                continue;
            }
            Assert.assertEquals(actualValue, expectedValue,
                    "Value mismatch for attribute: " + attribute);
        }
    }

    @Override
    public void validateDeletion() {
        Assert.assertTrue(deletionSuccessful.get(), "Transaction deletion should return HTTP 200");
    }

    @Override
    public String getCurrentEntityId() {
        return transaction.get().getId();
    }

    @Override
    public String getEntityType() {
        return "TRANSACTION";
    }

    @Override
    public Response fetchLatestResponse() {
        return latestResponse.get();
    }

    private static Transaction buildCreateBody() {
        Faker f = faker.get();
        Transaction currentTransaction = transaction.get();
        currentTransaction.setId("T" + f.numerify("#########"));
        currentTransaction.setClientId(resolveClientIdOverride(1));
        currentTransaction.setDate(LocalDate.now().toString());
        currentTransaction.setType(f.options().option("Deposit", "Withdrawal", "Transfer"));
        currentTransaction.setAmount(Integer.parseInt(f.numerify("#####")));
        currentTransaction.setCurrency("USD");
        currentTransaction.setAccountId(resolveAccountIdOverride("A123456"));
        return currentTransaction;
    }

    private static Transaction buildPutBody() {
        Faker f = faker.get();
        Transaction currentTransaction = transaction.get();
        currentTransaction.setClientId(resolveClientIdOverride(currentTransaction.getClientId()));
        currentTransaction.setDate(LocalDate.now().minusDays(2).toString());
        currentTransaction.setType(f.options().option("Transfer", "Deposit", "Withdrawal"));
        currentTransaction.setAmount(Integer.parseInt(f.numerify("#####")));
        currentTransaction.setCurrency("USD");
        currentTransaction.setAccountId(resolveAccountIdOverride(currentTransaction.getAccountId()));
        return currentTransaction;
    }

    private static Map<String, Object> buildPatchBody() {
        Faker f = faker.get();
        Map<String, Object> patchBody = new LinkedHashMap<>();
        if (clientIdOverride.get() != null) {
            patchBody.put("clientId", clientIdOverride.get());
            transaction.get().setClientId(clientIdOverride.get());
        }
        if (accountIdOverride.get() != null) {
            patchBody.put("accountId", accountIdOverride.get());
            transaction.get().setAccountId(accountIdOverride.get());
        }
        if (patchBody.isEmpty()) {
            int newAmount = Integer.parseInt(f.numerify("#####"));
            patchBody.put("amount", newAmount);
            transaction.get().setAmount(newAmount);
        }
        return patchBody;
    }

    private static Object resolveClientIdOverride(Object defaultClientId) {
        return clientIdOverride.get() != null ? clientIdOverride.get() : defaultClientId;
    }

    private static String resolveAccountIdOverride(String defaultAccountId) {
        return accountIdOverride.get() != null ? accountIdOverride.get() : defaultAccountId;
    }

    private List<String> getAttributeList() {
        return Arrays.asList("id", "clientId", "date", "type", "amount", "currency", "accountId");
    }

    public static void clearThreadContext() {
        faker.remove();
        transaction.remove();
        deletionSuccessful.remove();
        latestResponse.remove();
        clientIdOverride.remove();
        accountIdOverride.remove();
    }

    public static void useClientId(Object clientId) {
        clientIdOverride.set(clientId);
    }

    public static void useAccountId(String accountId) {
        accountIdOverride.set(accountId);
    }

    public static String getCurrentTransactionId() {
        return transaction.get().getId();
    }

    public static Object getCurrentClientReference() {
        return transaction.get().getClientId();
    }

    public static String getCurrentAccountReference() {
        return transaction.get().getAccountId();
    }
}

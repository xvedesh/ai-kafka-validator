package com.api;

import com.github.javafaker.Faker;
import com.interfaces.PayLoadValidator;
import com.pojo.Account;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.testng.Assert;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class AccountAPI extends BaseTest implements PayLoadValidator {
    private static final ThreadLocal<Faker> faker = ThreadLocal.withInitial(Faker::new);
    private static final ThreadLocal<Account> account = ThreadLocal.withInitial(Account::new);
    private static final ThreadLocal<Boolean> deletionSuccessful = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Response> latestResponse = new ThreadLocal<>();
    private static final ThreadLocal<Object> clientIdOverride = new ThreadLocal<>();

    @Override
    public void post() {
        Response response = given()
                .headers(returnAuthHeaders())
                .body(buildCreateBody())
                .when()
                .post(baseURI + allAccountsEndPoint)
                .prettyPeek();
        latestResponse.set(response);
    }

    @Override
    public void patch() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", account.get().getId())
                .body(buildPatchBody())
                .when()
                .patch(baseURI + accountsEndPoint)
                .prettyPeek();
        latestResponse.set(response);
    }

    @Override
    public void put() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", account.get().getId())
                .body(buildPutBody())
                .when()
                .put(baseURI + accountsEndPoint)
                .prettyPeek();
        latestResponse.set(response);
    }

    @Override
    public void delete() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", account.get().getId())
                .when()
                .delete(baseURI + accountsEndPoint)
                .prettyPeek();
        latestResponse.set(response);
        deletionSuccessful.set(response.statusCode() == 200);
    }

    @Override
    public JSONObject get() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", account.get().getId())
                .when()
                .get(baseURI + accountsEndPoint)
                .prettyPeek();
        latestResponse.set(response);
        return new JSONObject(response.getBody().asString());
    }

    @Override
    public void validatePayload(JSONObject payload) {
        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("id", payload.getString("id"));
        responseMap.put("clientId", String.valueOf(payload.get("clientId")));
        responseMap.put("type", payload.getString("type"));
        responseMap.put("balance", payload.getInt("balance"));
        responseMap.put("currency", payload.getString("currency"));
        responseMap.put("creationDate", payload.getString("creationDate"));

        Map<String, Object> expectedMap = account.get().generatePojoMap();
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
        Assert.assertTrue(deletionSuccessful.get(), "Account deletion should return HTTP 200");
    }

    @Override
    public String getCurrentEntityId() {
        return account.get().getId();
    }

    @Override
    public String getEntityType() {
        return "ACCOUNT";
    }

    @Override
    public Response fetchLatestResponse() {
        return latestResponse.get();
    }

    private static Account buildCreateBody() {
        Faker f = faker.get();
        Account currentAccount = account.get();
        currentAccount.setId("A" + f.numerify("######"));
        currentAccount.setClientId(resolveClientIdOverride(1));
        currentAccount.setType(f.options().option("Investment", "Savings", "Brokerage"));
        currentAccount.setBalance(Integer.parseInt(f.numerify("######")));
        currentAccount.setCurrency("USD");
        currentAccount.setCreationDate(LocalDate.now().toString());
        return currentAccount;
    }

    private static Account buildPutBody() {
        Faker f = faker.get();
        Account currentAccount = account.get();
        currentAccount.setClientId(resolveClientIdOverride(currentAccount.getClientId()));
        currentAccount.setType(f.options().option("Retirement", "Savings", "Investment"));
        currentAccount.setBalance(Integer.parseInt(f.numerify("######")));
        currentAccount.setCurrency("USD");
        currentAccount.setCreationDate(LocalDate.now().minusDays(1).toString());
        return currentAccount;
    }

    private static Map<String, Object> buildPatchBody() {
        Faker f = faker.get();
        Map<String, Object> patchBody = new LinkedHashMap<>();
        if (clientIdOverride.get() != null) {
            patchBody.put("clientId", clientIdOverride.get());
            account.get().setClientId(clientIdOverride.get());
        } else {
            int newBalance = Integer.parseInt(f.numerify("######"));
            patchBody.put("balance", newBalance);
            account.get().setBalance(newBalance);
        }
        return patchBody;
    }

    private static Object resolveClientIdOverride(Object defaultClientId) {
        return clientIdOverride.get() != null ? clientIdOverride.get() : defaultClientId;
    }

    private List<String> getAttributeList() {
        return Arrays.asList("id", "clientId", "type", "balance", "currency", "creationDate");
    }

    public static void clearThreadContext() {
        faker.remove();
        account.remove();
        deletionSuccessful.remove();
        latestResponse.remove();
        clientIdOverride.remove();
    }

    public static void useClientId(Object clientId) {
        clientIdOverride.set(clientId);
    }

    public static String getCurrentAccountId() {
        return account.get().getId();
    }

    public static Object getCurrentClientReference() {
        return account.get().getClientId();
    }
}

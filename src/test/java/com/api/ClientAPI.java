package com.api;

import com.pojo.Client;
import io.restassured.response.Response;
import org.json.*;
import com.github.javafaker.Faker;
import com.interfaces.PayLoadValidator;
import org.testng.Assert;

import java.util.*;

import static io.restassured.RestAssured.given;

public class ClientAPI extends BaseTest implements PayLoadValidator {
    private static final ThreadLocal<Faker> faker = ThreadLocal.withInitial(Faker::new);
    private static final ThreadLocal<Client> client = ThreadLocal.withInitial(Client::new);
    private static final ThreadLocal<Client.Address> address = ThreadLocal.withInitial(Client.Address::new);
    private static final ThreadLocal<Boolean> deletionSuccessful = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Response> latestResponse = new ThreadLocal<>();

    @Override
    public void post() {
        Response response = given()
                .headers(returnAuthHeaders())
                .body(returnBody())
                .when()
                .post(baseURI + allClientsEndPoint);
        latestResponse.set(response);
    }

    @Override
    public void patch() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", client.get().getId())
                .body(patchBody())
                .when()
                .patch(baseURI + clientEndPoint);
        latestResponse.set(response);
    }

    @Override
    public void put() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", client.get().getId())
                .body(putBody())
                .when()
                .put(baseURI + clientEndPoint);
        latestResponse.set(response);
    }

    @Override
    public void delete() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", client.get().getId())
                .when()
                .delete(baseURI + clientEndPoint);
        latestResponse.set(response);
        deletionSuccessful.set(response.getStatusCode() == 200);
    }

    @Override
    public JSONObject get() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", client.get().getId())
                .when()
                .get(baseURI + clientEndPoint);
        latestResponse.set(response);

        String responseBody = response.getBody().asString();
        return new JSONObject(responseBody);
    }

    @Override
    public void validatePayload(JSONObject payload) {

        // Define a list of attributes
        List<String> attributes = getAttributeList();

        // Generate maps from the POJO and the response payload
        Map<String, Object> pojoMap = client.get().generatePojoMap();
        Map<String, Object> responseMap = generateResponseMap(payload);

        // Iterate through the attributes and validate values
        for (String attribute : attributes) {
            Object expectedValue = pojoMap.get(attribute);
            Object actualValue = responseMap.get(attribute);
            if (attribute.equals("id") && expectedValue instanceof UUID) {
                Assert.assertEquals(actualValue, expectedValue.toString(),
                        "Value mismatch for attribute: " + attribute);
            } else {
                Assert.assertEquals(actualValue, expectedValue, "Value mismatch for attribute: " + attribute);
            }
        }
    }

    @Override
    public void validateDeletion() {
        Assert.assertTrue(deletionSuccessful.get());
    }

    private static Client returnBody() {
        Faker f = faker.get();
        Client currentClient = client.get();
        Client.Address currentAddress = address.get();

        currentAddress.setStreet(f.address().streetAddress());
        currentAddress.setCity(f.address().cityName());
        currentAddress.setState(f.address().stateAbbr());
        currentAddress.setZipCode(f.numerify("#####"));

        currentClient.setId(UUID.randomUUID());
        currentClient.setFirstName(f.name().firstName());
        currentClient.setLastName(f.name().lastName());
        currentClient.setEmail(f.internet().emailAddress());
        currentClient.setPhone(f.numerify("###-###-####"));
        currentClient.setAddress(currentAddress);

        return currentClient;
    }

    private static Client putBody() {
        Faker f = faker.get();
        Client currentClient = client.get();
        Client.Address currentAddress = address.get();

        currentAddress.setStreet(f.address().streetAddress());
        currentAddress.setCity(f.address().cityName());
        currentAddress.setState(f.address().stateAbbr());
        currentAddress.setZipCode(f.numerify("#####"));

        currentClient.setFirstName(f.name().firstName());
        currentClient.setLastName(f.name().lastName());
        currentClient.setEmail(f.internet().emailAddress());
        currentClient.setPhone(f.numerify("###-###-####"));
        currentClient.setAddress(currentAddress);

        return currentClient;
    }

    private static Map<String, Object> patchBody() {
        Faker f = faker.get();
        Client.Address currentAddress = address.get();

        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> addressUpdate = new LinkedHashMap<>();

        addressUpdate.put("street", f.address().streetAddress());
        addressUpdate.put("city", f.address().cityName());
        addressUpdate.put("state", f.address().stateAbbr());
        addressUpdate.put("zipCode", f.numerify("#####"));

        map.put("address", addressUpdate);

        currentAddress.setStreet((String) addressUpdate.get("street"));
        currentAddress.setCity((String) addressUpdate.get("city"));
        currentAddress.setState((String) addressUpdate.get("state"));
        currentAddress.setZipCode((String) addressUpdate.get("zipCode"));

        return map;
    }

    private Map<String, Object> generateResponseMap(JSONObject payload) {
        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("id", payload.getString("id"));
        responseMap.put("firstName", payload.getString("firstName"));
        responseMap.put("lastName", payload.getString("lastName"));
        responseMap.put("email", payload.getString("email"));
        responseMap.put("phone", payload.getString("phone"));

        // Add address attributes to the map
        JSONObject addressObject = payload.getJSONObject("address");
        responseMap.put("street", addressObject.getString("street"));
        responseMap.put("city", addressObject.getString("city"));
        responseMap.put("state", addressObject.getString("state"));
        responseMap.put("zipCode", addressObject.getString("zipCode"));

        return responseMap;
    }

    public List<String> getAttributeList() {
        return Arrays.asList(
                "id",
                "firstName",
                "lastName",
                "email",
                "phone",
                "street",
                "city",
                "state",
                "zipCode");
    }

    public static Response getLatestResponse() {
        return latestResponse.get();
    }

    @Override
    public String getCurrentEntityId() {
        return getCurrentClientId();
    }

    @Override
    public String getEntityType() {
        return "CLIENT";
    }

    @Override
    public Response fetchLatestResponse() {
        return latestResponse.get();
    }

    public static JSONObject getLatestResponseBodyAsJsonObject() {
        return new JSONObject(latestResponse.get().getBody().asString());
    }

    public static String getCurrentClientId() {
        return String.valueOf(client.get().getId());
    }

    public static Client getCurrentClient() {
        return client.get();
    }

    public static void clearThreadContext() {
        faker.remove();
        client.remove();
        address.remove();
        deletionSuccessful.remove();
        latestResponse.remove();
    }
}

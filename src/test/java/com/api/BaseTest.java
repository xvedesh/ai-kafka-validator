package com.api;

import com.utils.ConfigurationReader;

import java.util.Optional;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class BaseTest {

    private static final ThreadLocal<String> token = ThreadLocal.withInitial(() -> "");
    public static final String baseURI =
            System.getProperty(
                    "baseURI",
                    Optional.ofNullable(System.getenv("BASE_URI"))
                            .orElse(ConfigurationReader.getProperty("baseURI"))
            );
    private static final String authEndPoint = ConfigurationReader.getProperty("authEndPoint");
    public static final String clientEndPoint = ConfigurationReader.getProperty("clientEndPoint");
    public static final String allClientsEndPoint = ConfigurationReader.getProperty("allClientsEndPoint");
    public static final String allAccountsEndPoint = ConfigurationReader.getProperty("allAccountsEndPoint");
    public static final String accountsEndPoint = ConfigurationReader.getProperty("accountEndPoint");
    public static final String allTransactionsEndPoint = ConfigurationReader.getProperty("allTransactionsEndPoint");
    public static final String transactionsEndPoint = ConfigurationReader.getProperty("transactionEndPoint");
    public static final String allPortfoliosEndPoint = ConfigurationReader.getProperty("allPortfoliosEndPoint");
    public static final String portfolioEndPoint = ConfigurationReader.getProperty("portfolioEndPoint");

    public static void generateToken() {
        token.set("Bearer " + given()
                .headers(returnAuthHeaders())
                .body(returnCredentials())
                .when()
                .post(baseURI + authEndPoint)
                .prettyPeek()
                .path("accessToken"));
    }


    public static Map<String, String> returnCredentials() {
        Map<String,String> map = new LinkedHashMap<>();
        map.put("username", ConfigurationReader.getProperty("username"));
        map.put("password", ConfigurationReader.getProperty("password"));

        return map;
    }

    public static Map<String, String> returnAuthHeaders() {
        Map<String,String> map = new HashMap<>();
        map.put("Authorization", token.get());
        map.put("Content-Type", "application/json");
        return map;
    }

    public static void clearThreadContext() {
        token.remove();
    }
}

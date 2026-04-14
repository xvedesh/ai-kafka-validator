package com.api;

import com.github.javafaker.Faker;
import com.interfaces.PayLoadValidator;
import com.pojo.Portfolio;
import com.pojo.PortfolioAsset;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class PortfolioAPI extends BaseTest implements PayLoadValidator {
    private static final ThreadLocal<Faker> faker = ThreadLocal.withInitial(Faker::new);
    private static final ThreadLocal<Portfolio> portfolio = ThreadLocal.withInitial(Portfolio::new);
    private static final ThreadLocal<Boolean> deletionSuccessful = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Response> latestResponse = new ThreadLocal<>();
    private static final ThreadLocal<Object> clientIdOverride = new ThreadLocal<>();

    @Override
    public void post() {
        Response response = given()
                .headers(returnAuthHeaders())
                .body(buildCreateBody())
                .when()
                .post(baseURI + allPortfoliosEndPoint)
                .prettyPeek();
        latestResponse.set(response);
    }

    @Override
    public void patch() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", portfolio.get().getId())
                .body(buildPatchBody())
                .when()
                .patch(baseURI + portfolioEndPoint)
                .prettyPeek();
        latestResponse.set(response);
    }

    @Override
    public void put() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", portfolio.get().getId())
                .body(buildPutBody())
                .when()
                .put(baseURI + portfolioEndPoint)
                .prettyPeek();
        latestResponse.set(response);
    }

    @Override
    public void delete() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", portfolio.get().getId())
                .when()
                .delete(baseURI + portfolioEndPoint)
                .prettyPeek();
        latestResponse.set(response);
        deletionSuccessful.set(response.statusCode() == 200);
    }

    @Override
    public JSONObject get() {
        Response response = given()
                .headers(returnAuthHeaders())
                .pathParam("clientId", portfolio.get().getId())
                .when()
                .get(baseURI + portfolioEndPoint)
                .prettyPeek();
        latestResponse.set(response);
        return new JSONObject(response.getBody().asString());
    }

    @Override
    public void validatePayload(JSONObject payload) {
        Assert.assertEquals(payload.getString("id"), portfolio.get().getId(), "Portfolio id mismatch");
        Assert.assertEquals(String.valueOf(payload.get("clientId")), String.valueOf(portfolio.get().getClientId()),
                "Portfolio clientId mismatch");
        Assert.assertEquals(payload.getString("name"), portfolio.get().getName(), "Portfolio name mismatch");

        JSONArray assetsArray = payload.getJSONArray("assets");
        Assert.assertEquals(assetsArray.length(), portfolio.get().getAssets().size(), "Portfolio assets size mismatch");

        for (int i = 0; i < portfolio.get().getAssets().size(); i++) {
            PortfolioAsset expectedAsset = portfolio.get().getAssets().get(i);
            JSONObject actualAsset = assetsArray.getJSONObject(i);
            Assert.assertEquals(actualAsset.getString("assetId"), expectedAsset.getAssetId(), "Portfolio assetId mismatch");
            Assert.assertEquals(actualAsset.getString("type"), expectedAsset.getType(), "Portfolio asset type mismatch");
            Assert.assertEquals(actualAsset.getString("name"), expectedAsset.getName(), "Portfolio asset name mismatch");
            Assert.assertEquals(actualAsset.getInt("quantity"), expectedAsset.getQuantity(), "Portfolio asset quantity mismatch");
            Assert.assertEquals(actualAsset.getInt("currentValue"), expectedAsset.getCurrentValue(), "Portfolio asset currentValue mismatch");
        }
    }

    @Override
    public void validateDeletion() {
        Assert.assertTrue(deletionSuccessful.get(), "Portfolio deletion should return HTTP 200");
    }

    @Override
    public String getCurrentEntityId() {
        return portfolio.get().getId();
    }

    @Override
    public String getEntityType() {
        return "PORTFOLIO";
    }

    @Override
    public Response fetchLatestResponse() {
        return latestResponse.get();
    }

    private static Portfolio buildCreateBody() {
        Faker f = faker.get();
        Portfolio currentPortfolio = portfolio.get();
        currentPortfolio.setId("P" + f.numerify("######"));
        currentPortfolio.setClientId(resolveClientIdOverride(1));
        currentPortfolio.setName(f.options().option("Retirement Fund", "Growth Portfolio", "Income Strategy"));
        currentPortfolio.setAssets(buildAssets(2));
        return currentPortfolio;
    }

    private static Portfolio buildPutBody() {
        Faker f = faker.get();
        Portfolio currentPortfolio = portfolio.get();
        currentPortfolio.setClientId(resolveClientIdOverride(currentPortfolio.getClientId()));
        currentPortfolio.setName(f.options().option("Balanced Portfolio", "Long Term Growth", "Income Focus"));
        currentPortfolio.setAssets(buildAssets(2));
        return currentPortfolio;
    }

    private static Map<String, Object> buildPatchBody() {
        Faker f = faker.get();
        Map<String, Object> patchBody = new LinkedHashMap<>();
        if (clientIdOverride.get() != null) {
            patchBody.put("clientId", clientIdOverride.get());
            portfolio.get().setClientId(clientIdOverride.get());
        } else {
            String newName = f.options().option("Rebalanced Portfolio", "Updated Retirement Fund", "Defensive Portfolio");
            patchBody.put("name", newName);
            portfolio.get().setName(newName);
        }
        return patchBody;
    }

    private static Object resolveClientIdOverride(Object defaultClientId) {
        return clientIdOverride.get() != null ? clientIdOverride.get() : defaultClientId;
    }

    private static List<PortfolioAsset> buildAssets(int assetCount) {
        Faker f = faker.get();
        List<PortfolioAsset> assets = new ArrayList<>();

        for (int i = 0; i < assetCount; i++) {
            PortfolioAsset asset = new PortfolioAsset();
            asset.setAssetId("ASSET" + f.numerify("###"));
            asset.setType(f.options().option("Stock", "Bond", "ETF"));
            asset.setName(f.company().name());
            asset.setQuantity(Integer.parseInt(f.numerify("###")));
            asset.setCurrentValue(Integer.parseInt(f.numerify("#####")));
            assets.add(asset);
        }

        return assets;
    }

    public static void clearThreadContext() {
        faker.remove();
        portfolio.remove();
        deletionSuccessful.remove();
        latestResponse.remove();
        clientIdOverride.remove();
    }

    public static void useClientId(Object clientId) {
        clientIdOverride.set(clientId);
    }

    public static String getCurrentPortfolioId() {
        return portfolio.get().getId();
    }

    public static Object getCurrentClientReference() {
        return portfolio.get().getClientId();
    }
}

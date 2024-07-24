package com.bybit.api.domain.trade;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.trade.Side;
import com.bybit.api.client.domain.trade.TimeInForce;
import com.bybit.api.client.domain.trade.request.TradeOrderRequest;
import com.bybit.api.client.restApi.BybitApiTradeRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenOrderTest {
    BybitApiTradeRestClient client = BybitApiClientFactory.newInstance("YOUR_API_KEY", "YOUR_API_SECRET", BybitApiConfig.TESTNET_DOMAIN).newTradeRestClient();

    @Test
    public void Test_PlaceLinearOrder() throws InterruptedException {

       List<String> orderLinkIdList = new ArrayList<>();
        var newSpotOrderRequest = TradeOrderRequest.builder().category(CategoryType.SPOT).symbol("XRPUSDT").side(Side.BUY).price("0.1").orderType(TradeOrderType.LIMIT).qty("10").timeInForce(TimeInForce.IOC);

        // place 510 spot orders in canceled status
        int lastIdx = 510;
        for (int i = 0; i < lastIdx; i++) {
            String orderCustomId = "bybit_test_spot_order_link_id_" + String.valueOf(i + 1);
            var newOrder = client.createOrder(newSpotOrderRequest.orderLinkId(orderCustomId).build());
            System.out.println("order " + (i + 1));
            System.out.println(newOrder);
            orderLinkIdList.add(orderCustomId);
        }

        // place 510 linear orders in filled status
        var newLinearOrderRequest = TradeOrderRequest.builder().category(CategoryType.LINEAR).symbol("XRPUSDT").side(Side.BUY).orderType(TradeOrderType.MARKET).qty("10").timeInForce(TimeInForce.IOC);
        for (int i = 0; i < lastIdx; i++) {
            String orderCustomId = "bybit_test_linear_order_link_Id_" + String.valueOf(i + 1);
            var newOrder = client.createOrder(newLinearOrderRequest.orderLinkId(orderCustomId).build());
            System.out.println("order " + (i + 1));
            System.out.println(newOrder);
            orderLinkIdList.add(orderCustomId);
        }

        // place 510 linear orders in filled status
        var newInverseOrderRequest = TradeOrderRequest.builder().category(CategoryType.INVERSE).symbol("BTCUSD").side(Side.BUY).orderType(TradeOrderType.MARKET).qty("1").timeInForce(TimeInForce.IOC);
        for (int i = 0; i < lastIdx; i++) {
            String orderCustomId = "bybit_test_inverse_order_link_Id_" + String.valueOf(i + 1);
            var newOrder = client.createOrder(newInverseOrderRequest.orderLinkId(orderCustomId).build());
            System.out.println("order " + (i + 1));
            System.out.println(newOrder);
            orderLinkIdList.add(orderCustomId);
        }

        // after 10 seconds
        TimeUnit.SECONDS.sleep(10);

        var openOrderRequest = TradeOrderRequest.builder();
        //get spot open orders
        var openSpotOrdersResult = client.getOpenOrders(openOrderRequest.category(CategoryType.SPOT).openOnly(1).build());
        System.out.println(openSpotOrdersResult);
        //test spot order
        String lastSpotOrderLinkId = orderLinkIdList.get(lastIdx - 1), firstSpotOrderLinkId = orderLinkIdList.get(0);
        //get 510th spot order
        var lastSpotOrderResult = client.getOpenOrders(openOrderRequest.category(CategoryType.SPOT).orderLinkId(lastSpotOrderLinkId).build());
        System.out.println(lastSpotOrderResult);
        //get 1st spot order
        var firstSpotOrderResult = client.getOpenOrders(openOrderRequest.category(CategoryType.SPOT).orderLinkId(firstSpotOrderLinkId).build());
        System.out.println(firstSpotOrderResult);

        //get linear open orders
        var openLinearOrdersResult = client.getOpenOrders(openOrderRequest.category(CategoryType.LINEAR).openOnly(1).build());
        System.out.println(openLinearOrdersResult);
        //test linear order
        String lastLinearOrderLinkId = orderLinkIdList.get(lastIdx * 2 - 1), firstLinearOrderLinkId = orderLinkIdList.get(lastIdx);
        //get 510th linear order
        var lastLinearOrderResult = client.getOpenOrders(openOrderRequest.category(CategoryType.LINEAR).orderLinkId(lastLinearOrderLinkId).build());
        System.out.println(lastLinearOrderResult);
        //get 1st linear order
        var firstLinearOrderResult = client.getOpenOrders(openOrderRequest.category(CategoryType.LINEAR).orderLinkId(firstLinearOrderLinkId).build());
        System.out.println(firstLinearOrderResult);

        //get linear open orders
        var openInverseOrdersResult = client.getOpenOrders(openOrderRequest.category(CategoryType.INVERSE).openOnly(2).build());
        System.out.println(openInverseOrdersResult);
        //test inverse order
        String lastInverseOrderLinkId = orderLinkIdList.get(lastIdx * 3 - 1), firstInverseOrderLinkId = orderLinkIdList.get(lastIdx * 2);
        //get 510th inverse order
        var lastInverseOrderResult = client.getOpenOrders(openOrderRequest.category(CategoryType.INVERSE).orderLinkId(lastInverseOrderLinkId).build());
        System.out.println(lastInverseOrderResult);
        //get 1st inverse order
        var firInverseOrderResult = client.getOpenOrders(openOrderRequest.category(CategoryType.INVERSE).orderLinkId(firstInverseOrderLinkId).build());
        System.out.println(firInverseOrderResult);

    }
}

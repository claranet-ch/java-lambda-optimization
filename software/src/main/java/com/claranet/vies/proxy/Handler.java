package com.claranet.vies.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.xray.AWSXRay;
import eu.europa.ec.vies.CheckVatPortType;
import eu.europa.ec.vies.CheckVatService;
import jakarta.xml.ws.Holder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.Tracing;

import javax.xml.namespace.QName;
import java.util.Map;

public class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Handler.class);
    private static final CheckVatPortType PORT;

    static {
        var wsdlLocation = Thread.currentThread().getContextClassLoader()
            .getResource("wsdl/checkVatService.wsdl");
        var qName = new QName("urn:ec.europa.eu:taxud:vies:services:checkVat", "checkVatService");
        PORT = new CheckVatService(wsdlLocation, qName).getCheckVatPort();
    }


    @Metrics(captureColdStart = true)
    @Tracing
    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        var params = input.getPathParameters();
        var valid = new Holder<Boolean>();
        var name = new Holder<String>();
        var address = new Holder<String>();

        try (var subsegment = AWSXRay.beginSubsegment("Call remote webservice")) {
            PORT.checkVat(
                new Holder<>(params.get("country")),
                new Holder<>(params.get("vatNumber")),
                new Holder<>(),
                valid,
                name,
                address
            );
            subsegment.putMetadata("valid", valid.value);
        } catch (Exception ex) {
            LOGGER.error("Error while calling VIES service", ex);
            return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(500)
                .build();
        }
        return APIGatewayV2HTTPResponse.builder()
            .withHeaders(Map.of("Content-Type", "application/json"))
            .withBody("{}")
            .withStatusCode(200)
            .build();
    }
}

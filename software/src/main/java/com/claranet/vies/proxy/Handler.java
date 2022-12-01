package com.claranet.vies.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.claranet.vies.proxy.model.ValidationRequest;
import com.claranet.vies.proxy.model.ValidationResponse;
import eu.europa.ec.vies.CheckVatPortType;
import eu.europa.ec.vies.CheckVatService;
import jakarta.xml.ws.Holder;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.Tracing;

import javax.xml.namespace.QName;

public class Handler implements RequestHandler<ValidationRequest, ValidationResponse>, Resource {

    private static final Logger LOGGER = LoggerFactory.getLogger(Handler.class);
    private static final CheckVatPortType PORT;

    static {
        var wsdlLocation = Thread.currentThread().getContextClassLoader()
            .getResource("wsdl/checkVatService.wsdl");
        var qName = new QName("urn:ec.europa.eu:taxud:vies:services:checkVat", "checkVatService");
        PORT = new CheckVatService(wsdlLocation, qName).getCheckVatPort();
    }

    public Handler() {
        Core.getGlobalContext().register(this);
    }

    @Metrics(captureColdStart = true)
    @Tracing
    @Override
    public ValidationResponse handleRequest(ValidationRequest input, Context context) {
        LOGGER.info("Entering handleRequest");

        var country = input.getCountry();
        var vatNumber = input.getVatNumber();

        if (country == null || country.strip().length() != 2 || vatNumber == null || vatNumber.isEmpty()) {
            throw new IllegalArgumentException("Invalid input");
        }

        try {
            return performValidation(country, vatNumber);
        } catch (Exception ex) {
            LOGGER.error("Error while calling VIES service. Returning 500", ex);
            throw new RuntimeException(ex);
        }
    }

    @Tracing(segmentName = "Call remote webservice")
    private static ValidationResponse performValidation(String country, String vatNumber) {
        var valid = new Holder<Boolean>();
        var name = new Holder<String>();
        var address = new Holder<String>();
        PORT.checkVat(
            new Holder<>(country),
            new Holder<>(vatNumber),
            new Holder<>(),
            valid,
            name,
            address
        );
        return new ValidationResponse(valid.value, name.value, address.value);
    }

    @Override
    public void beforeCheckpoint(org.crac.Context<? extends Resource> context) {
        // as per https://docs.aws.amazon.com/lambda/latest/dg/snapstart-runtime-hooks.html SnapStart supports CRaC hooks.
        // We use that to load as many objects as we can in memory before performing the checkpoint,
        // in order to prevent expensive initializations from happening at runtime
        performValidation("IE", "123456");
    }

    @Override
    public void afterRestore(org.crac.Context<? extends Resource> context) {
        LOGGER.info("Restore complete");
    }
}

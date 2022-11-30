# Cold start optimization for Java serverless functions

This repository contains a simple example of SOAP client on Lambda, optimized for faster startup.
The client calls [VIES](https://ec.europa.eu/taxation_customs/vies/#/vat-validation) webservice to validate EU VAT numbers

## Prerequisites

- JDK 11+
- Docker
- AWS [CDK](https://docs.aws.amazon.com/cdk/v2/guide/getting_started.html) installed and configured

## How to build and install

You should run the following sequence of commands:

1. `./mvnw install` to initialize dependencies
2. `cdk bootstrap` to initialize IaC environment
3. `cdk deploy` to deploy the infrastructure

Deploy will create the following resources:

- `vies-proxy-11`: Lambda running on `Amazon Corretto 11` runtime, ready for SnapStart activation
- `vies-proxy-19`: Lambda running on a slim `Amazon Corretto 19` runtime, using a custom Docker image
- an ECR repository to push `vies-proxy-19` images
- `vies-proxy-19-custom`: Lambda running on a custom runtime based on `Amazon Corretto 19`


## How to enable Lambda SnapStart

Lambda SnapStart can be enabled on the `vies-proxy-11` function.
To enable it, follow the [official instructions](https://docs.aws.amazon.com/lambda/latest/dg/snapstart-activate.html#snapshot-console)

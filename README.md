# Water Meter Service

Spring Boot REST API packaged for AWS Lambda behind API Gateway.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/testlamda` | Returns `Hello world` |
| PUT | `/test/{testId}` | Stores `testId` in DynamoDB `TestTable` (partition key `test_id`) |

Example after deploy:

```bash
curl -X PUT "https://<api-id>.execute-api.ap-south-1.amazonaws.com/Prod/test/my-value-123"
# {"test_id":"my-value-123","status":"stored"}
```

## Prerequisites

- Java 17+
- Maven 3.9+
- AWS SAM CLI (for deployment)

## Build

```bash
mvn clean package
```

Deployment artifact:

`target/water-meter-service-0.0.1-SNAPSHOT-aws.jar`

## Test locally

```bash
mvn test
```

Run as a normal Spring Boot app (without Lambda):

```bash
mvn spring-boot:run -Dspring-boot.run.main-class=com.vswitch.watermeter.WaterMeterServiceApplication
curl http://localhost:8080/testlamda
```

## Deploy with SAM

```bash
mvn clean package
sam build
sam deploy --guided
```

After deploy, call:

```bash
curl https://<api-id>.execute-api.<region>.amazonaws.com/Prod/testlamda
```

## Lambda handler

`com.vswitch.watermeter.StreamLambdaHandler::handleRequest`

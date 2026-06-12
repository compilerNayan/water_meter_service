# Water Meter Service

Spring Boot REST API packaged for AWS Lambda behind API Gateway.

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/testlamda` | No | Returns `Hello world` |
| GET | `/test` | No | Returns all stored `test_id` values as a JSON array |
| PUT | `/test/{testId}` | No | Stores `testId` in DynamoDB `TestTable` |
| POST | `/users` | Cognito JWT | Register user + create tenant (idempotent) |
| GET | `/users/me` | Cognito JWT | Returns authenticated user profile from DynamoDB |
| GET | `/tenants/{tenantId}` | Cognito JWT | Returns tenant/building config |
| POST | `/tenants/{tenantId}/building` | Cognito JWT (owner) | Save building name + structure; completes onboarding |
| PUT | `/tenants/{tenantId}/structure` | Cognito JWT (owner) | Update building layout |
| POST | `/tenants/{tenantId}/devices/pre-enroll` | Cognito JWT | Reserve device serial for tenant (pending enrollment) |
| GET | `/devices/{serialNumber}/tenant` | No | Resolve tenant for serial (fleet provisioning) |
| POST | `/tenants/{tenantId}/units` | Cognito JWT | Create unit with metadata after enrollment starts |
| GET | `/tenants/{tenantId}/units` | Cognito JWT | List units for tenant |
| GET | `/tenants/{tenantId}/devices/{deviceId}/enrollment-status` | Cognito JWT | Enrollment completion (placeholder) |

**DynamoDB tables:** `WaterMeterUsers` (PK `userId`), `WaterMeterTenants` (PK `tenantId`), `WaterMeterDevicePreEnrollments` (PK `serialNumber`), `WaterMeterUnits` (PK `unitId`, GSI `tenantId-index`)

**Cognito issuer:** `https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_vm19Xv95r`

Example after deploy:

```bash
curl -X PUT "https://<api-id>.execute-api.ap-south-1.amazonaws.com/Prod/test/my-value-123"
# {"test_id":"my-value-123","status":"stored"}

curl "https://<api-id>.execute-api.ap-south-1.amazonaws.com/Prod/test"
# ["my-value-123","other-value"]
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

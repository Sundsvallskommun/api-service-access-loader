# AccessLoader

_Keeps access-mapper and SupportManagement in sync with the organization. The access loader job gives every manager
in the configured parts of the organization access to their own units in access-mapper. The subscriber loader job
then makes the managers holding a configured role the members of the SupportManagement subscription profile the role
is mapped to._

## Getting Started

### Prerequisites

- **Java 25 or higher**
- **Maven**
- **MariaDB** (used for the scheduler locks)
- **Git**
- **[Dependent Microservices](#dependencies)**

### Installation

1. **Clone the repository:**

   ```bash
   git clone https://github.com/Sundsvallskommun/api-service-access-loader.git
   cd api-service-access-loader
   ```
2. **Configure the application:**

   Before running the application, you need to set up configuration settings.
   See [Configuration](#Configuration)

   **Note:** Ensure all required configurations are set; otherwise, the application may fail to start.

3. **Ensure dependent services are running:**

   If this microservice depends on other services, make sure they are up and accessible.
   See [Dependencies](#dependencies) for more details.

4. **Build and run the application:**

   ```bash
   mvn spring-boot:run
   ```

## Dependencies

This microservice depends on the following services:

- **MDViewer**
  - **Purpose:** Provides the organization tree and the employees of each unit, which the access loader job uses to
    find the units and their managers.
  - **API specification:** [md-viewer-api.yaml](src/main/resources/integrations/md-viewer-api.yaml)
  - **Setup Instructions:** Refer to its documentation for installation and configuration steps.
- **Employee**
  - **Purpose:** Provides the employments of a person, which the access loader job uses to find the manager of each
    unit and that manager's own manager.
  - **API specification:** [employee-api.yaml](src/main/resources/integrations/employee-api.yaml)
  - **Setup Instructions:** Refer to its documentation for installation and configuration steps.
- **AccessMapper**
  - **Purpose:** The target of the access loader job, which creates, updates and removes the managers as access users
    (origin `AUTOMATIC`). The subscriber loader job reads the users and their roles from it.
  - **Repository:** [Link to the repository](https://github.com/Sundsvallskommun/api-service-access-mapper)
  - **Setup Instructions:** Refer to its documentation for installation and configuration steps.
- **SupportManagement**
  - **Purpose:** The target of the subscriber loader job, which replaces the members of the subscription profiles.
  - **Repository:** [Link to the repository](https://github.com/Sundsvallskommun/api-service-support-management)
  - **Setup Instructions:** Refer to its documentation for installation and configuration steps.
  - **Configuration:** SupportManagement authorizes calls by the `X-Sent-By` header, see
    [Key Configuration Parameters](#key-configuration-parameters).

Ensure that these services are running and properly configured before starting this microservice.

## API Documentation

Access the API documentation via Swagger UI:

- **Swagger UI:** [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

Alternatively, refer to the [openapi.yaml](src/integration-test/resources/api/openapi.yaml) file for the OpenAPI
specification.

## Usage

Both jobs run on a schedule (see [Scheduling](#scheduling)), and can also be triggered manually through the API.

### Access loader job

For each configured municipality and namespace, the job reads the organization tree below each configured `orgId`
and finds the manager of every unit. Each manager gets a `label` access with the pattern `LOCATION/<org path>/**` for
their units, and their own manager inherits the same accesses. The job then:

- creates the managers that are not yet access users in access-mapper,
- updates the access users whose accesses have changed,
- removes the accesses within the configured `orgIds` from users that are no longer managers there, and deletes
  the user if no other accesses remain.

Only users with origin `AUTOMATIC` are handled. Accesses outside the configured `orgIds` are left untouched.

### Subscriber loader job

For each configured municipality and namespace, the job makes the managers (access users with origin `AUTOMATIC`)
holding a configured role the members of the subscription profile the role is mapped to. A role is the access
pattern of an access of type `role` in access-mapper.

- Managers who no longer hold the role, or are no longer managers, lose their membership.
- Members that are manually created users in access-mapper (origin `MANUAL`) are kept as long as they hold a role
  mapped to the profile. Manually created users are never added by the job.
- The configured roles act as an allowlist. A role that is not configured, or a profile that no configured role is
  mapped to, is never touched.
- If the roles or the members cannot be read, the profile is left as it is, so that no members are removed by
  mistake.

### API Endpoints

Refer to the [API Documentation](#api-documentation) for detailed information on available endpoints.

| Method |        Path         |                 Description                  |
|--------|---------------------|----------------------------------------------|
| POST   | `/sync`             | Triggers the access loader job manually.     |
| POST   | `/sync-subscribers` | Triggers the subscriber loader job manually. |

Both endpoints respond with `204 No Content` once the job has run.

### Example Request

```bash
curl -X POST http://localhost:8080/sync-subscribers
```

## Configuration

Configuration is crucial for the application to run successfully. Ensure all necessary settings are configured in
`application.yml`.

### Key Configuration Parameters

- **Server Port:**

  ```yaml
  server:
    port: 8080
  ```
- **Database Settings:**

  The database only holds the ShedLock table, which makes sure that a job runs on one instance at a time.

  ```yaml
  spring:
    datasource:
      url: jdbc:mariadb://localhost:3306/your_database
      username: your_db_username
      password: your_db_password
  ```
- **External Service URLs:**

  ```yaml
  integration:
    mdviewer:
      url: http://mdviewer.url
      oauth2-token-grant-url: http://token.url
      oauth2-client-id: the-client-id
      oauth2-client-secret: the-client-secret
    employee:
      url: http://employee.url
      oauth2-token-grant-url: http://token.url
      oauth2-client-id: the-client-id
      oauth2-client-secret: the-client-secret
    access-mapper:
      url: http://access-mapper.url
      oauth2-token-grant-url: http://token.url
      oauth2-client-id: the-client-id
      oauth2-client-secret: the-client-secret
    support-management:
      url: http://support-management.url
      oauth2-token-grant-url: http://token.url
      oauth2-client-id: the-client-id
      oauth2-client-secret: the-client-secret
      sent-by: the-ad-account
  ```

  Each integration also has `connect-timeout` and `read-timeout` (in seconds), which default to 5 and 30.

  `integration.support-management.sent-by` is the AD account the service acts as towards SupportManagement, sent in
  the `X-Sent-By` header. Set it as a secret per environment. It is optional so that the service still starts without
  it, but SupportManagement then rejects the calls and the subscriber loader job fails.

- **Access level:**

  The access level (`RW`, `R` or `LR`) the access loader job gives the managers in access-mapper. Defaults to `RW`.

  ```yaml
  accessloader:
    access-level: RW
  ```

### Scheduling

Both jobs are scheduled with a cron expression and run per municipality and namespace. A failing namespace does not
stop the others, but the run is reported as failed in the scheduler health once all namespaces have been synced. Set
`cron` to `'-'` to disable a schedule.

- **Access loader job:** the `org-ids` are the units in MDViewer whose managers get access. A namespace without
  `org-ids` is skipped.

  ```yaml
  scheduler:
    accessloader:
      cron: '0 0 2 * * *'
      name: access-loader
      lock-at-most-for: PT15M
      maximum-execution-time: PT15M
      municipalities:
        '2281':
          namespaces:
            MY_NAMESPACE:
              org-ids:
                - 13
                - 14
  ```
- **Subscriber loader job:** `role-profiles` maps a role in access-mapper to the name of the SupportManagement
  subscription profile its holders are members of. The namespace is the same in access-mapper and SupportManagement.
  Several roles can be mapped to the same profile.

  ```yaml
  scheduler:
    subscriberloader:
      cron: '0 30 2 * * *'
      name: subscriber-loader
      lock-at-most-for: PT15M
      maximum-execution-time: PT15M
      municipalities:
        '2281':
          namespaces:
            MY_NAMESPACE:
              role-profiles:
                UNIT_MANAGER: Chefer
                HEAD_OF_OPERATIONS: Chefer
  ```

### Database Initialization

The project is set up with [Flyway](https://github.com/flyway/flyway) for database migrations. Flyway is disabled by
default so you will have to enable it to automatically populate the database schema upon application startup.

```yaml
spring:
  flyway:
    enabled: true
```

- **No additional setup is required** for database initialization, as long as the database connection settings are
  correctly configured.

### Additional Notes

- **Application Profiles:**

  Use Spring profiles (`dev`, `prod`, etc.) to manage different configurations for different environments.

- **Logging Configuration:**

  Adjust logging levels if necessary.

## Contributing

Contributions are welcome! Please
see [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the [MIT License](LICENSE).

## Code status

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-access-loader&metric=alert_status)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-access-loader)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-access-loader&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-access-loader)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-access-loader&metric=security_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-access-loader)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-access-loader&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-access-loader)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-access-loader&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-access-loader)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-access-loader&metric=bugs)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-access-loader)

---

© 2024 Sundsvalls kommun

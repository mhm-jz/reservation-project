package com.azki.reservation.config;

import com.azki.reservation.exception.ErrorResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Reservation Platform API",
                version = "1.0",
                description = "Reservation platform REST API"
        ),
        security = {
                @SecurityRequirement(
                        name = OpenApiConfig.BEARER_AUTH
                )
        }
)
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    private static final String ERROR_RESPONSE_SCHEMA =
            "#/components/schemas/ErrorResponse";

    private static final Set<String> ERROR_STATUS_CODES =
            Set.of("400", "401", "404", "409", "500");

    @Bean
    public OpenAPI reservationOpenApi() {
        Components components = new Components()
                .addResponses(
                        "RegistrationBadRequest",
                        errorResponse(
                                "Registration request validation failed",
                                orderedExamples(
                                        "invalidUsername",
                                        errorExample(
                                                "VALIDATION_ERROR",
                                                "username: size must be between 3 and 100"
                                        ),
                                        "invalidEmail",
                                        errorExample(
                                                "VALIDATION_ERROR",
                                                "email: must be a well-formed email address"
                                        )
                                )
                        )
                )
                .addResponses(
                        "LoginBadRequest",
                        errorResponse(
                                "Login request validation failed",
                                Map.of(
                                        "validationError",
                                        errorExample(
                                                "VALIDATION_ERROR",
                                                "password: must not be blank"
                                        )
                                )
                        )
                )
                .addResponses(
                        "ReservationBadRequest",
                        errorResponse(
                                "Reservation request validation failed",
                                Map.of(
                                        "validationError",
                                        errorExample(
                                                "VALIDATION_ERROR",
                                                "slotId: must be greater than 0"
                                        )
                                )
                        )
                )
                .addResponses(
                        "SlotQueryBadRequest",
                        errorResponse(
                                "Slot query validation failed",
                                orderedExamples(
                                        "invalidRange",
                                        errorExample(
                                                "VALIDATION_ERROR",
                                                "'to' must be after 'from'"
                                        ),
                                        "invalidCursor",
                                        errorExample(
                                                "VALIDATION_ERROR",
                                                "Cursor is invalid"
                                        ),
                                        "invalidLimit",
                                        errorExample(
                                                "VALIDATION_ERROR",
                                                "'limit' must be between 1 and 100"
                                        )
                                )
                        )
                )
                .addResponses(
                        "Unauthorized",
                        errorResponse(
                                "Authentication is required",
                                Map.of(
                                        "unauthorized",
                                        errorExample(
                                                "UNAUTHORIZED",
                                                "Authentication is required"
                                        )
                                )
                        )
                )
                .addResponses(
                        "InvalidCredentials",
                        errorResponse(
                                "Authentication failed",
                                Map.of(
                                        "invalidCredentials",
                                        errorExample(
                                                "INVALID_CREDENTIALS",
                                                "Username or password is incorrect"
                                        )
                                )
                        )
                )
                .addResponses(
                        "SlotNotFound",
                        errorResponse(
                                "The requested slot was not found",
                                Map.of(
                                        "slotNotFound",
                                        errorExample(
                                                "SLOT_NOT_FOUND",
                                                "Slot not found: 42"
                                        )
                                )
                        )
                )
                .addResponses(
                        "ReservationCancellationNotFound",
                        errorResponse(
                                "The reservation or its slot was not found",
                                orderedExamples(
                                        "reservationNotFound",
                                        errorExample(
                                                "RESERVATION_NOT_FOUND",
                                                "Reservation not found: 42"
                                        ),
                                        "slotNotFound",
                                        errorExample(
                                                "SLOT_NOT_FOUND",
                                                "Slot not found: 42"
                                        )
                                )
                        )
                )
                .addResponses(
                        "RegistrationConflict",
                        errorResponse(
                                "The username or email is already registered",
                                orderedExamples(
                                        "usernameAlreadyExists",
                                        errorExample(
                                                "USERNAME_ALREADY_EXISTS",
                                                "Username already exists: alice"
                                        ),
                                        "emailAlreadyExists",
                                        errorExample(
                                                "EMAIL_ALREADY_EXISTS",
                                                "Email already exists: alice@example.com"
                                        )
                                )
                        )
                )
                .addResponses(
                        "SlotReservationConflict",
                        errorResponse(
                                "The slot cannot be reserved",
                                orderedExamples(
                                        "slotAlreadyReserved",
                                        errorExample(
                                                "SLOT_ALREADY_RESERVED",
                                                "Slot is already reserved: 42"
                                        ),
                                        "slotUnavailable",
                                        errorExample(
                                                "SLOT_UNAVAILABLE",
                                                "Slot is no longer available: 42"
                                        )
                                )
                        )
                )
                .addResponses(
                        "InternalServerError",
                        errorResponse(
                                "The reservation state is inconsistent",
                                Map.of(
                                        "reservationStateError",
                                        errorExample(
                                                "RESERVATION_STATE_ERROR",
                                                "Reservation could not be cancelled"
                                        )
                                )
                        )
                );

        return new OpenAPI().components(components);
    }

    @Bean
    public OperationCustomizer protectedEndpointResponses() {
        return (operation, handlerMethod) -> {
            ERROR_STATUS_CODES.forEach(responseCode -> {
                ApiResponse response =
                        operation.getResponses().get(responseCode);

                if (response != null && response.get$ref() == null) {
                    operation.getResponses().remove(responseCode);
                }
            });

            boolean inheritsGlobalSecurity =
                    operation.getSecurity() == null;
            boolean declaresSecurity =
                    operation.getSecurity() != null
                            && !operation.getSecurity().isEmpty();

            if (inheritsGlobalSecurity || declaresSecurity) {
                operation.getResponses().addApiResponse(
                        "401",
                        new ApiResponse().$ref(
                                "#/components/responses/Unauthorized"
                        )
                );
            }

            operation.getResponses().values().forEach(response -> {
                Content content = response.getContent();
                if (content != null && content.containsKey("*/*")) {
                    MediaType mediaType = content.remove("*/*");
                    content.addMediaType(
                            org.springframework.http.MediaType
                                    .APPLICATION_JSON_VALUE,
                            mediaType
                    );
                }
            });

            return operation;
        };
    }

    private ApiResponse errorResponse(
            String description,
            Map<String, Example> examples
    ) {
        MediaType mediaType = new MediaType()
                .schema(new Schema<ErrorResponse>()
                        .$ref(ERROR_RESPONSE_SCHEMA));
        examples.forEach(mediaType::addExamples);

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType
                                .APPLICATION_JSON_VALUE,
                        mediaType
                ));
    }

    private Example errorExample(
            String code,
            String message
    ) {
        Map<String, String> example = new LinkedHashMap<>();
        example.put("code", code);
        example.put("message", message);
        example.put("timestamp", "2026-07-24T10:00:00Z");
        return new Example().value(example);
    }

    private Map<String, Example> orderedExamples(
            String firstName,
            Example firstExample,
            String secondName,
            Example secondExample
    ) {
        Map<String, Example> examples = new LinkedHashMap<>();
        examples.put(firstName, firstExample);
        examples.put(secondName, secondExample);
        return examples;
    }

    private Map<String, Example> orderedExamples(
            String firstName,
            Example firstExample,
            String secondName,
            Example secondExample,
            String thirdName,
            Example thirdExample
    ) {
        Map<String, Example> examples = orderedExamples(
                firstName,
                firstExample,
                secondName,
                secondExample
        );
        examples.put(thirdName, thirdExample);
        return examples;
    }
}

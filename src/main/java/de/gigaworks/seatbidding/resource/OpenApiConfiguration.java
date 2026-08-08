package de.gigaworks.seatbidding.resource;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(info = @Info(title = "Office Seat Bidding API", version = "1.0.0"))
@SecurityScheme(securitySchemeName = "formCookie", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE, apiKeyName = "seat_session",
        description = "Encrypted Quarkus form-authentication cookie")
public class OpenApiConfiguration {

}

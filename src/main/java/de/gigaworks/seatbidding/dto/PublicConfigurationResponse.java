package de.gigaworks.seatbidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PublicConfiguration")
public record PublicConfigurationResponse(
        String androidDownloadUrl,
        String apiBasePath) {
    
}

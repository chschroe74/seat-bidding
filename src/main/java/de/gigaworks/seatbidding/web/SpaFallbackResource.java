package de.gigaworks.seatbidding.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

@Path("/")
public class SpaFallbackResource {

    @GET
    @Path("{route:.+}")
    @Produces(MediaType.TEXT_HTML)
    public Response index(@PathParam("route") String route) throws IOException {
        if (isNonApplicationRoute(route)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/resources/index.html");
        if (stream == null) {
            return Response.status(404).build();
        }
        try (stream) {
            return Response.ok(stream.readAllBytes(), MediaType.TEXT_HTML_TYPE)
                    .header("Cache-Control", "no-cache")
                    .build();
        }
    }

    private static boolean isNonApplicationRoute(String route) {
        return route.equals("api") || route.startsWith("api/")
                || route.equals("q") || route.startsWith("q/")
                || route.equals("j_security_check");
    }
}
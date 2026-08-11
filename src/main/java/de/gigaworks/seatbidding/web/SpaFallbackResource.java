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
    @Path("{route:assignments|bids|help|login}")
    @Produces(MediaType.TEXT_HTML)
    public Response index(@PathParam("route") String ignored) throws IOException {
        var stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/resources/index.html");
        if (stream == null) {
            return Response.status(404).build();
        }
        try (stream) {
            return Response.ok(stream.readAllBytes(), MediaType.TEXT_HTML_TYPE).build();
        }
    }
    
    @GET
    @Path("activate/{step:code|password}")
    @Produces(MediaType.TEXT_HTML)
    public Response activation(@PathParam("step") String ignored) throws IOException {
        return index(ignored);
    }
    
    @GET
    @Path("admin/reservations")
    @Produces(MediaType.TEXT_HTML)
    public Response reservations() throws IOException {
        return index("admin/reservations");
    }
    
}

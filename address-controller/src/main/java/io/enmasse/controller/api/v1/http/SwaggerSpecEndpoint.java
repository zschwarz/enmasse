package io.enmasse.controller.api.v1.http;

import java.io.InputStream;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("swagger.json")
public class SwaggerSpecEndpoint {

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public InputStream getOpenApiSpec() {
        return getClass().getResourceAsStream("/swagger.json");
    }
}

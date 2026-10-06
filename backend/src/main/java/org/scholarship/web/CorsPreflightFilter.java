package org.scholarship.web;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/** 对 CORS 预检 OPTIONS 请求直接放行。 */
@Provider
public class CorsPreflightFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext ctx) {
        if ("OPTIONS".equalsIgnoreCase(ctx.getMethod())) {
            ctx.abortWith(Response.ok().build());
        }
    }
}

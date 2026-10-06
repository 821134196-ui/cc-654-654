package edu.scholar.web;

import edu.scholar.service.BusinessException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {
    @Override
    public Response toResponse(BusinessException e) {
        return Response.status(e.status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", e.getMessage() == null ? "业务处理失败" : e.getMessage()))
                .build();
    }
}

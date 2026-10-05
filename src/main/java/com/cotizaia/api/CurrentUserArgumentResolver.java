package com.cotizaia.api;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Projects verified JWT claims into controller arguments (project.txt section 2).
 * Centralizing identity extraction keeps security framework details out of controllers.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadCredentialsException("Authentication required");
        }
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            Long agencyId = Long.valueOf(jwt.getClaimAsString("agencyId"));
            return new CurrentUser(userId, agencyId, jwt.getClaimAsString("role"), jwt.getClaimAsString("email"));
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("Authentication required", exception);
        }
    }
}

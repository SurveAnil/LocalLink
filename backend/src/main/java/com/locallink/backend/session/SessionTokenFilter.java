
package com.locallink.backend.session;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.locallink.backend.device.Device;
import com.locallink.backend.room.RoomService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SessionTokenFilter extends OncePerRequestFilter {


    private final SessionTokenService sessionTokenService;

    public SessionTokenFilter(SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authHeader.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "SESSION_TOKEN_INVALID",
                    "The session token is invalid.");
            return;
        }
        String rawToken = authHeader.substring(7).trim();

        try {
            Device device = sessionTokenService.validateToken(rawToken);

            var authentication = new UsernamePasswordAuthenticationToken(
                    device.getId().toString(),
                    null,
                    AuthorityUtils.createAuthorityList(
                            "ROLE_" + device.getRole().name()
                    )
            );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            request.setAttribute("authenticatedDevice", device);

        } catch (SessionTokenService.InvalidSessionException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "SESSION_TOKEN_INVALID",
                    "The session token is invalid.");
            return;
        } catch (SessionTokenService.ExpiredSessionException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "SESSION_TOKEN_EXPIRED",
                    "The session token has expired.");
            return;
        } catch (RoomService.RoomExpiredException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_GONE);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"ROOM_EXPIRED\","
                            + "\"message\":\"This room has expired.\","
                            + "\"expiresAt\":\"" + ex.getExpiresAt() + "\"}");
            return;
        } catch (RoomService.RoomClosedException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, HttpServletResponse.SC_GONE,
                    "ROOM_CLOSED", "This room is closed.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String error,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"message\":\""
                        + message + "\"}");
    }
}

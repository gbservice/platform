package lsfusion.http.controller;

import com.google.common.base.Throwables;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import lsfusion.base.col.heavy.OrderedMap;
import lsfusion.http.authentication.LSFAuthenticationToken;
import lsfusion.http.provider.navigator.NavigatorProviderImpl;
import lsfusion.http.provider.session.SessionSessionObject;
import lsfusion.interop.base.exception.AuthenticationException;
import lsfusion.interop.base.exception.RemoteInternalException;
import lsfusion.interop.session.ExecInterface;
import lsfusion.interop.session.ExternalUtils;
import lsfusion.interop.logics.LogicsRunnable;
import lsfusion.interop.logics.LogicsSessionObject;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.util.Enumeration;

import static java.util.Collections.list;
import static lsfusion.base.ServerMessages.getString;

public abstract class ExternalRequestHandler extends LogicsRequestHandler implements HttpRequestHandler {

    protected abstract void handleRequest(LogicsSessionObject sessionObject, HttpServletRequest request, HttpServletResponse response) throws Exception;

    private void handleRequestException(LogicsSessionObject sessionObject, HttpServletRequest request, HttpServletResponse response) throws RemoteException {
        try {
            handleRequest(sessionObject, request, response);
        } catch (Exception e) {
            if(e instanceof AuthenticationException) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/html; charset=utf-8");
                try { // in theory here can be changed exception (despite the fact that remote call is wrapped into RemoteExceptionAspect)
                    Pair<String, Pair<String, String>> actualStacks = RemoteInternalException.toString(e);
                    response.getWriter().print(actualStacks.first+'\n'+ ExceptionUtils.getExStackTrace(actualStacks.second.first, actualStacks.second.second));
                } catch (IOException e1) {
                    throw Throwables.propagate(e1);
                }

                if (e instanceof RemoteException)  // rethrow RemoteException to invalidate LogicsSessionObject in LogicsProvider
                    throw (RemoteException) e;
            }
        }
    }

    @Override
    public void handleRequest(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        try {
            runRequest(request, new LogicsRunnable<Object>() {
                @Override
                public Object run(LogicsSessionObject sessionObject) throws RemoteException {
                    handleRequestException(sessionObject, request, response);
                    return null;
                }
            });
        } catch (RemoteException e) { // will suppress that error, because we rethrowed it when handling request (see above)
        }
    }

    protected void sendOKResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        sendResponse(request, response, getString(request, "executed.successfully"), Charset.forName("UTF-8"), false, false);
    }

    protected void sendResponse(HttpServletRequest request, HttpServletResponse response, String message, Charset charset, boolean error, boolean accessControl) throws IOException {
        sendResponse(request, response, new ExternalUtils.ExternalResponse(new StringEntity(message, charset), null, null, null, null, null), error, accessControl);
    }

    // copy of ExternalHTTPServer.sendResponse
    protected void sendResponse(HttpServletRequest request, HttpServletResponse response, ExternalUtils.ExternalResponse responseHttpEntity, boolean error, boolean accessControl) throws IOException {
        HttpEntity responseEntity = responseHttpEntity.response;
        Header contentType = responseEntity.getContentType();
        String contentDisposition = responseHttpEntity.contentDisposition;
        String[] headerNames = responseHttpEntity.headerNames;
        String[] headerValues = responseHttpEntity.headerValues;
        String[] cookieNames = responseHttpEntity.cookieNames;
        String[] cookieValues = responseHttpEntity.cookieValues;

        boolean hasContentType = false; 
        boolean hasContentDisposition = false;
        if(headerNames != null) {
            for (int i = 0; i < headerNames.length; i++) {
                String headerName = headerNames[i];
                if (headerName.equals("Content-Type")) {
                    hasContentType = true;
                    response.setContentType(headerValues[i]);
                } else {
                    response.addHeader(headerName, headerValues[i]);
                }
                hasContentDisposition = hasContentDisposition || headerName.equals("Content-Disposition");
            }
        }

        if(cookieNames != null) {
            for (int i = 0; i < cookieNames.length; i++) {
                response.addCookie(new Cookie(cookieNames[i], cookieValues[i]));
            }
        }

        if(contentType != null && !hasContentType)
            response.setContentType(contentType.getValue());
        if(contentDisposition != null && !hasContentDisposition)
            response.addHeader("Content-Disposition", contentDisposition);        
        response.setStatus(error ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : HttpServletResponse.SC_OK);
        if(accessControl) {
            //marks response as successful for js request
            response.addHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
            //allows to use cookies for js request
            response.addHeader("Access-Control-Allow-Credentials", "true");
        }
        responseEntity.writeTo(response.getOutputStream());
    }
}

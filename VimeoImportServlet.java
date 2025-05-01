package com.aem.amit.core.servlets;
 
import java.io.IOException;
 
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
 
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
import com.aem.amit.core.service.VimeoAemSyncProcessService;
 
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
 
/**
* 
* @author Amit Patel
*
*/
 
@Component(service = Servlet.class, property = { "sling.servlet.paths=/bin/amit/fetchVimeoData",
        "sling.servlet.methods=GET" })
public class VimeoImportServlet extends SlingSafeMethodsServlet {
 
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(VimeoImportServlet.class);
 
    @Reference
    transient VimeoAemSyncProcessService vimeoImportService;
 
    @Reference
    transient SlingSettingsService slingSettingsService;
 
    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // Check if the current instance is an author instance
        if (!slingSettingsService.getRunModes().contains("author")) {
            LOGGER.warn("This servlet is only available on author instances.");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Error: This servlet is only available on author instances.");
            return;
        }
 
        // Check if this is a request for real-time logs
        if ("true".equals(request.getParameter("streamLogs"))) {
            streamLogs(request, response);
            return;
        }
 
        // Render the main HTML page
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
 
        // Escape the token to prevent XSS
        String token = request.getParameter("token");
        String escapedToken = token != null ? escapeHtml(token) : ""; // Default to an empty string if token is null
 
        StringBuilder htmlResponse = new StringBuilder();
        htmlResponse.append("<html><head><title>Vimeo-AEM Connector</title></head><body>");
        htmlResponse.append("<h1>Vimeo-AEM Connector</h1>");
        htmlResponse.append("<form method='GET' action='/bin/amit/fetchVimeoData'>");
        htmlResponse.append("<label for='token'>Enter Token:</label>");
        htmlResponse.append("<input type='text' id='token' name='token' placeholder='Enter your token' value='")
                .append(escapedToken).append("' required />");
        htmlResponse.append("<button type='button' id='runSync'>Run Sync</button>");
        htmlResponse.append("</form>");
        htmlResponse.append("<hr>");
        htmlResponse.append("<div id='logs' style='white-space: pre-wrap; background: #f9f9f9; border: 1px solid #ccc; padding: 10px; height: 500px; overflow-y: auto;'>");
        htmlResponse.append("Logs will appear here...</div>");
        htmlResponse.append("<script>");
        htmlResponse.append("document.getElementById('runSync').addEventListener('click', function() {");
        htmlResponse.append("  const token = document.getElementById('token').value;");
        htmlResponse.append("  if (!token) { alert('Please enter a valid token.'); return; }");
        htmlResponse.append("  const logsDiv = document.getElementById('logs');");
        htmlResponse.append("  logsDiv.textContent = 'Starting import...\\n';");
        htmlResponse.append("  const eventSource = new EventSource('/bin/amit/fetchVimeoData?streamLogs=true&token=' + encodeURIComponent(token));");
        htmlResponse.append("  eventSource.onmessage = function(event) {");
        htmlResponse.append("    if (event.data === '[END]') {");
        htmlResponse.append("      logsDiv.textContent += '\\nLog streaming completed.';");
        htmlResponse.append("      eventSource.close();");
        htmlResponse.append("    } else {");
        htmlResponse.append("      logsDiv.textContent += event.data + '\\n';");
        htmlResponse.append("      logsDiv.scrollTop = logsDiv.scrollHeight;");
        htmlResponse.append("    }");
        htmlResponse.append("  };");
        htmlResponse.append("  eventSource.onerror = function() {");
        htmlResponse.append("    logsDiv.textContent += '\\nAn error occurred while streaming logs.';");
        htmlResponse.append("    eventSource.close();");
        htmlResponse.append("  };");
        htmlResponse.append("});");
        htmlResponse.append("</script>");
        htmlResponse.append("</body></html>");
 
        response.getWriter().write(htmlResponse.toString());
    }
 
    private void streamLogs(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
 
        String token = request.getParameter("token");
        if (!isValidToken(token)) {
            response.getWriter().println("data: Error: Invalid token provided.\n");
            response.getWriter().flush();
            return;
        }
 
        RealTimeLogAppender logAppender = new RealTimeLogAppender(response);
 
        ch.qos.logback.classic.Logger vimeoLogger = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger("com.aem.amit.core.service.impl.VimeoAemSyncProcess");
        vimeoLogger.addAppender(logAppender);
        logAppender.start();
 
        try {
            LOGGER.info("Starting Vimeo video import...");
            response.getWriter().println("data: Starting Vimeo video import...\n");
            response.getWriter().flush();
 
            vimeoImportService.fetchVimeoVideos();
 
            LOGGER.info("Vimeo video import completed successfully.");
            response.getWriter().println("data: Vimeo video import completed successfully.\n");
            response.getWriter().flush();
        } catch (Exception e) {
            LOGGER.error("Error occurred during Vimeo video import: {}", e.getMessage(), e);
            response.getWriter().println("data: Error occurred during Vimeo video import: " + e.getMessage() + "\n");
            response.getWriter().flush();
        } finally {
            vimeoLogger.detachAppender(logAppender);
            logAppender.stop();
            // Send a special "end" message to indicate the process is complete
            response.getWriter().println("data: [END]\n");
            response.getWriter().flush();
        }
    }
 
    private static class RealTimeLogAppender extends AppenderBase<ILoggingEvent> {
        private final SlingHttpServletResponse response;
 
        public RealTimeLogAppender(SlingHttpServletResponse response) {
            this.response = response;
        }
 
        @Override
        protected void append(ILoggingEvent eventObject) {
            try {
                String logMessage = eventObject.getFormattedMessage();
                response.getWriter().println("data: " + logMessage + "\n");
                response.getWriter().flush();
            } catch (IOException e) {
                stop();
            }
        }
    }
 
    private boolean isValidToken(String token) {
        String validToken = "f234234pa34g23423454223457689";
        return validToken.equals(token);
    }
 
    // Manual HTML escaping logic
    private String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '&':
                    escaped.append("&amp;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }
}

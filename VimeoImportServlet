package com.aem.amit.core.servlets;
 
import java.io.IOException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
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
 
@Component(service = Servlet.class, property = { "sling.servlet.paths=/bin/amit/fetchVimeoVideos",

		"sling.servlet.methods=GET" })

public class VimeoImportServlet extends SlingAllMethodsServlet {
 
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LoggerFactory.getLogger(VimeoImportServlet.class);
 
	@Reference

	transient VimeoAemSyncProcessService vimeoImportService;
 
	@Override

	protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)

			throws ServletException, IOException {

		response.setContentType("text/plain");

		response.setCharacterEncoding("UTF-8");
 
		// Retrieve the token from the query parameter

		String token = request.getParameter("token");
 
		// Validate the token

		if (!isValidToken(token)) {

			LOGGER.error("Invalid token provided: {}", token);

			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

			response.getWriter().println("Error: You have not provided a valid token.");

			return;

		}
 
		// Custom log collector

		RealTimeLogAppender logAppender = new RealTimeLogAppender(response);
 
		// Attach the custom appender to the Impl logger

		ch.qos.logback.classic.Logger vimeoLogger = (ch.qos.logback.classic.Logger) LoggerFactory

				.getLogger("com.aem.amit.core.service.impl.VimeoAemSyncProcess");

		vimeoLogger.addAppender(logAppender);

		logAppender.start();
 
		// Record the start time

		long startTime = System.currentTimeMillis();
 
		try {

			LOGGER.info("Starting Vimeo video import...");

			response.getWriter().println("Starting Vimeo video import...");

			response.getWriter().flush();
 
			// Trigger the fetchVimeoVideos method

			vimeoImportService.fetchVimeoVideos();
 
			// Record the end time

			long endTime = System.currentTimeMillis();

			long totalTimeInSeconds = (endTime - startTime) / 1000;
 
			LOGGER.info("Vimeo video import completed successfully in {} seconds.", totalTimeInSeconds);

			response.getWriter()

					.println("Vimeo video import completed successfully in " + totalTimeInSeconds + " seconds.");

			response.getWriter().flush();

		} catch (Exception e) {

			LOGGER.error("Error occurred during Vimeo video import: {}", e.getMessage(), e);

			response.getWriter().println("Error occurred during Vimeo video import: " + e.getMessage());

			response.getWriter().flush();

		} finally {

			// Detach the custom appender

			vimeoLogger.detachAppender(logAppender);

			logAppender.stop();

		}

	}
 
	// Custom log appender to send logs in real-time to the servlet response

	private static class RealTimeLogAppender extends AppenderBase<ILoggingEvent> {

		private final SlingHttpServletResponse response;
 
		public RealTimeLogAppender(SlingHttpServletResponse response) {

			this.response = response;

		}
 
		@Override

		protected void append(ILoggingEvent eventObject) {

			try {

				String logMessage = eventObject.getFormattedMessage();

				response.getWriter().println(logMessage);

				response.getWriter().flush(); // Flush the response buffer to send logs in real-time

			} catch (IOException e) {

				// If an error occurs while writing to the response, stop the appender

				stop();

			}

		}

	}
 
	// Method to validate the token

	private boolean isValidToken(String token) {

		String validToken = "f234234pa34g23423454223457689";

		return validToken.equals(token);

	}

}
 

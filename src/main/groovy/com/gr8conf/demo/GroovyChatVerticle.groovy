package com.gr8conf.demo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.vertx.groovy.core.http.RouteMatcher
import org.vertx.groovy.platform.Verticle

import java.util.regex.Pattern

class GroovyChatVerticle extends Verticle {

	private static final String RECEIVED_DATE_FORMAT = "MMM-dd h:mm a"

	def start() {

		def chatUrlPattern = Pattern.compile("/chat/(\\w+)")
		def eventBus = vertx.eventBus
		def logger = container.logger

		logger.info "Starting HTTP BroChat Server on 8080"

		def rm = new RouteMatcher()

		// Catch all - serve the chat page
		rm.get("/") { req ->
			req.response.sendFile "web/brochat.html"
		}.get(".*\\.(css|js)") { req ->
			container.logger.info "Rendering resources " + new File(req.path)
			req.response.sendFile "web" + new File(req.path)
		}
		vertx.createHttpServer().requestHandler(rm.asClosure()).listen(8080, "localhost")

		logger.info "Starting Websocket BroChat Server on 8090"

		vertx.createHttpServer().websocketHandler { ws ->
			def m = chatUrlPattern.matcher(ws.path)
			if (!m.matches()) {
				ws.reject()
				return
			}

			String chatRoom = m.group(1)
			String id = ws.textHandlerID
			logger.info "Registering new connection with id: $id for BroChat room: $chatRoom"
			vertx.sharedData.getSet("chat.room." + chatRoom).add(id)

			ws.closeHandler { event ->
				logger.info "Un-registering connection with id: $id for BroChat room: $chatRoom"
				vertx.sharedData.getSet("chat.room." + chatRoom).remove(id)
			}

			ws.dataHandler { data ->
				def mapper = new ObjectMapper()
				try {
					JsonNode rootNode = mapper.readTree(data.toString())
					((ObjectNode) rootNode).put("received", new Date().format(RECEIVED_DATE_FORMAT))
					String jsonOutput = mapper.writeValueAsString(rootNode)
					logger.info "json generated: " + jsonOutput
					vertx.sharedData.getSet("chat.room." + chatRoom).each { chatter ->
						eventBus.send((String) chatter, jsonOutput)
					}
				} catch (IOException e) {
					ws.reject()
				}
			}
		}.listen(8090)

		logger.info "GroovyChatVerticle started"
	}
}

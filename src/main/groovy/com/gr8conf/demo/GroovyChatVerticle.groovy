package com.gr8conf.demo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import groovy.json.JsonBuilder
import org.vertx.groovy.core.http.RouteMatcher
import org.vertx.groovy.platform.Verticle

import java.util.regex.Pattern

class GroovyChatVerticle extends Verticle {

	private static final String RECEIVED_DATE_FORMAT = "MMM-dd h:mm a"
	private static final List BRO_REPLIES = [
		"Cool story bro.",
		"Bro, do you even lift?",
		"U mad bro?",
		"Don't tase me bro!",
		"Come at me bro!"
	]

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
			req.response.sendFile "web" + new File(req.path)
		}
		vertx.createHttpServer().requestHandler(rm.asClosure()).listen(8080)

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
					def dateReceived = new Date().format(RECEIVED_DATE_FORMAT)
					((ObjectNode) rootNode).put("received", dateReceived)
					String jsonOutput = mapper.writeValueAsString(rootNode)
					logger.info "json generated: " + jsonOutput
					def broReply = getBroReply(jsonOutput)
					vertx.sharedData.getSet("chat.room." + chatRoom).each { chatter ->
						eventBus.send((String) chatter, jsonOutput)
						if (broReply) {
							logger.info "Sending bro reply: " + broReply
							String broReplyJSON = new JsonBuilder([message: broReply, sender:"bro",received:dateReceived]).toString()
							eventBus.send((String) chatter, broReplyJSON)
						}
					}
				} catch (IOException e) {
					ws.reject()
				}
			}
		}.listen(8090)

		logger.info "GroovyChatVerticle started"
	}

	String getBroReply(message) {
		message.contains("@bro") ? BRO_REPLIES[new Random().nextInt(BRO_REPLIES.size())] : null
	}
}

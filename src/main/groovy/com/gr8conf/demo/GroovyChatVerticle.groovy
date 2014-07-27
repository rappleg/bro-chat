package com.gr8conf.demo

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
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

		def chatUrlPattern = Pattern.compile("/chat/(\\w+)/(\\w+)")
		def eventBus = vertx.eventBus
		def logger = container.logger

		logger.info "Starting HTTP BroChat Server on 8080"

		def rm = new RouteMatcher()

		// Catch all - serve the bro chat page
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
			String username = m.group(2)

			logger.info "Registering new connection with username: $username for BroChat room: $chatRoom"
			vertx.sharedData.getSet("chat.room." + chatRoom).add(username)

			ws.closeHandler { event ->
				logger.info "Un-registering connection with username: $username for BroChat room: $chatRoom"
				vertx.sharedData.getSet("chat.room." + chatRoom).remove(username)
				// Un-register message to new client so they have a complete list of all available users in the room
				String unregisterJSON = new JsonBuilder([unregister: true, sender: username]).toString()
				eventBus.publish("chat.$chatRoom", unregisterJSON)
			}

			ws.dataHandler { data ->
				try {
					def origJsonData = new JsonSlurper().parseText(data.toString())
					def dateReceived = new Date().format(RECEIVED_DATE_FORMAT)
					origJsonData.received = dateReceived
					def room = origJsonData.remove("room")
					String jsonOutput = new JsonBuilder(origJsonData)

					// Match on all users that start with @ to determine if anyone should receive direct messages
					def userMatcher = origJsonData.message =~ /@(\w+)/
					def matchingUsers = userMatcher?.collect { it[1] }
					logger.info "Matching users " + matchingUsers
					if (matchingUsers && !matchingUsers.contains("bro")) {
						// Send this message to the user that sent it and to each user they're direct messaging
						eventBus.send("chat.$room.${origJsonData.sender}", jsonOutput)
						logger.debug "Sending message:[$jsonOutput] directly to users: $matchingUsers"
						matchingUsers.each { String matchingUsername ->
							eventBus.send("chat.$room.$matchingUsername", jsonOutput)
						}
					} else {
						logger.debug "Broadcasting message:[$jsonOutput] to everyone in room: $room"
						eventBus.publish("chat.$room", jsonOutput)

						def broReply = getBroReply(jsonOutput)
						if (broReply) {
							logger.info "Sending bro reply: " + broReply
							String broReplyJSON = new JsonBuilder([message: broReply, sender:"bro", received: dateReceived]).toString()
							eventBus.publish("chat.$room", broReplyJSON)
						}
					}
				} catch (IOException e) {
					ws.reject()
				}
			}

			// Handle broadcasted messages to a room
			eventBus.registerHandler("chat.$chatRoom") { message ->
				logger.info "Received broadcasted message ${message.body}"
				ws.writeTextFrame(message.body)
			}

			// Handle direct messages to a specific user in a room
			eventBus.registerHandler("chat.$chatRoom.$username") { message ->
				logger.info "Received a direct message ${message.body}"
				ws.writeTextFrame(message.body)
			}

			(vertx.sharedData.getSet("chat.room." + chatRoom) - username).each { existingUsername ->
				// Register message to new client so they have a complete list of all available users in the room
				String registerJSON = new JsonBuilder([register: true, sender: existingUsername]).toString()
				eventBus.send("chat.$chatRoom.$username", registerJSON)
			}
			// Register message to all clients to add this user to the list of available users in the room
			String registerJSON = new JsonBuilder([register: true, sender: username]).toString()
			eventBus.publish("chat.$chatRoom", registerJSON)

		}.listen(8090)

		logger.info "GroovyChatVerticle started"
	}

	String getBroReply(message) {
		message.contains("@bro ") ? BRO_REPLIES[new Random().nextInt(BRO_REPLIES.size())] : null
	}
}

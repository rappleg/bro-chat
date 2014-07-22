package com.gr8conf.demo;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ChatVerticle extends Verticle {

	public void start() {

		final Pattern chatUrlPattern = Pattern.compile("/chat/(\\w+)");
		final EventBus eventBus = vertx.eventBus();
		final Logger logger = container.logger();

		container.logger().info("Starting HTTP Server on 8080");

		RouteMatcher httpRouteMatcher = new RouteMatcher().get("/", new
			Handler<HttpServerRequest>() {
				@Override
				public void handle(final HttpServerRequest request) {
					request.response().sendFile("com/gr8conf/demo/web/brochat.html");
				}
			}).get(".*\\.(css|js)$", new Handler<HttpServerRequest>() {
			@Override
			public void handle(final HttpServerRequest request) {
				request.response().sendFile("com/gr8conf/demo/web/" + new File(request.path()));
			}
		});

		vertx.createHttpServer().requestHandler(httpRouteMatcher).listen(8080, "localhost");

		container.logger().info("Starting Websocket Chat Server on 8090");

		vertx.createHttpServer().websocketHandler(new Handler<ServerWebSocket>() {
			@Override
			public void handle(final ServerWebSocket ws) {
				final Matcher m = chatUrlPattern.matcher(ws.path());
				if (!m.matches()) {
					ws.reject();
					return;
				}

				final String chatRoom = m.group(1);
				final String id = ws.textHandlerID();
				logger.info("registering new connection with id: " + id + " for chat-room: " + chatRoom);
				vertx.sharedData().getSet("chat.room." + chatRoom).add(id);

				ws.closeHandler(new Handler<Void>() {
					@Override
					public void handle(final Void event) {
						logger.info("un-registering connection with id: " + id + " from chat-room: " + chatRoom);
						vertx.sharedData().getSet("chat.room." + chatRoom).remove(id);
					}
				});

				ws.dataHandler(new Handler<Buffer>() {
					@Override
					public void handle(final Buffer data) {

						ObjectMapper m = new ObjectMapper();
						try {
							JsonNode rootNode = m.readTree(data.toString());
							((ObjectNode) rootNode).put("received", new Date().toString());
							String jsonOutput = m.writeValueAsString(rootNode);
							logger.info("json generated: " + jsonOutput);
							for (Object chatter : vertx.sharedData().getSet("chat.room." + chatRoom)) {
								eventBus.send((String) chatter, jsonOutput);
							}
						} catch (IOException e) {
							ws.reject();
						}
					}
				});

			}
		}).listen(8090);

		container.logger().info("ChatVerticle started");
	}
}

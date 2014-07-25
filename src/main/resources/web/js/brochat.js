var wsocket;
var serviceLocation = "ws://0.0.0.0:8090/chat/";
var $username;
var $message;
var $chatWindow;
var room = '';

function onMessageReceived(evt) {
	var msg = JSON.parse(evt.data); // native API
	var $messageLine = $('<tr>'
		+ '<td class="received">' + msg.received + '</td>'
		+ '<td class="user label label-info">' + msg.sender+ '</td>'
		+ '<td class="message">' + msg.message + '</td>'
		+ '</tr>');
	$chatWindow.append($messageLine);
}

function sendMessage() {
	var msg = '{"message":"' + $message.val() + '", "sender":"'
		+ $username.val() + '", "room":"' + room + '"}';
	wsocket.send(msg);
	$message.val('').focus();
}

function connectToChatserver() {
	room = $('#chatroom option:selected').val();
	wsocket = new WebSocket(serviceLocation + room + '/' + $username.val());
	wsocket.onmessage = onMessageReceived;
}

function leaveRoom() {
	wsocket.close();
	$chatWindow.empty();
	$('.chat-wrapper').hide();
	$('.chat-signin').show();
	$username.focus();
}

$(document).ready(function() {
	$username = $('#username');
	$message = $('#message');
	$chatWindow = $('#response');
	$('.chat-wrapper').hide();
	$username.focus();

	$('#enterRoom').click(function(evt) {
		evt.preventDefault();
		connectToChatserver();
		$('.chat-wrapper h2').text('BroChat - '+$username.val() + "@" + room);
		$('.chat-signin').hide();
		$('.chat-wrapper').show();
		$message.focus();
	});
	$('#do-chat').submit(function(evt) {
		evt.preventDefault();
		sendMessage()
	});

	$('#leave-room').click(function(){
		leaveRoom();
	});
});
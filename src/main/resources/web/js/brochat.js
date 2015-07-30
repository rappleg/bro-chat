var wsocket;
// If doing a demo from change this to the ip of the computer so others may access it on the network
var serviceLocation = "ws://192.168.2.39:8090/chat/";
var $username;
var $message;
var $chatWindow;
var $loggedInUsers;
var room = '';

function onMessageReceived(evt) {
	var msg = JSON.parse(evt.data); // native API
	if (msg.register) {
		$loggedInUsers.append('<li><a class="directMessageLink user label label-info" href="javascript://">' + msg.sender + '</a></li>');
	} else if (msg.unregister) {
		$loggedInUsers.find("li").filter(function () {
			return $(this).text() === msg.sender
		}).remove();
	} else if (msg.message) {
		var $messageLine = $('<tr>'
			+ '<td class="received">' + msg.received + '</td>'
			+ '<td class="user label label-info">' + msg.sender + '</td>'
			+ '<td class="message">' + msg.message + '</td>'
			+ '</tr>');
		$chatWindow.append($messageLine);
	}
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
	$loggedInUsers.empty();
	$('.chat-wrapper').hide();
	$('.chat-signin').show();
	$username.focus();
}

$(document).ready(function() {
	$username = $('#username');
	$message = $('#message');
	$chatWindow = $('#response');
	$loggedInUsers = $('#loggedInUsers');
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
		if ($message.val().indexOf("<script>") > -1) {
			alert('Nice try bro!');
		} else {
			sendMessage();
		}
	});

	$('#leave-room').click(function() {
		leaveRoom();
	});

	$(document).on('click', '.directMessageLink', function(evt) {
		$message.val($message.val() + '@' + $(this).html() + ' ');
		$message.focus();
	});
});
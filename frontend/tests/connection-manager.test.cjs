const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..");
const source = fs.readFileSync(
    path.join(root, "src/scripts/connection-manager.js"),
    "utf8"
);

test("connection manager derives REST and WebSocket endpoints from LAN page host", () => {
    const context = createContext("http://192.168.1.22:5500/dashboard.html");
    vm.runInNewContext(source, context);

    assert.equal(
        context.window.LocalLinkConnection.apiBaseUrl(),
        "http://192.168.1.22:8080/api/v1/rooms"
    );
    assert.equal(
        context.window.LocalLinkConnection.websocketUrl(),
        "ws://192.168.1.22:8080/ws"
    );
});

test("connection manager authenticates STOMP and routes real-time room events", () => {
    const context = createContext("http://localhost:5500/dashboard.html");
    vm.runInNewContext(source, context);
    const receivedMessages = [];
    const receivedMembers = [];
    const receivedFiles = [];
    const states = [];
    const manager = new context.window.LocalLinkConnection.RoomConnectionManager({
        roomId: "room-1",
        token: "session-token",
        onState: (state) => states.push(state),
        onMessage: (message) => receivedMessages.push(message),
        onMembers: (update) => receivedMembers.push(update),
        onFiles: (file) => receivedFiles.push(file)
    });

    assert.equal(manager.sendMessage({ content: "too early" }), false);
    manager.start();
    const socket = context.FakeWebSocket.instances[0];
    socket.readyState = context.FakeWebSocket.OPEN;
    socket.onopen();
    assert.match(socket.sent[0], /Authorization:Bearer session-token/);
    socket.onmessage({ data: "CONNECTED\nversion:1.2\n\n\0" });
    assert.match(socket.sent[1], /destination:\/topic\/rooms\/room-1\/messages/);
    assert.match(socket.sent[2], /destination:\/topic\/rooms\/room-1\/members/);
    assert.match(socket.sent[3], /destination:\/topic\/rooms\/room-1\/files/);

    socket.onmessage({
        data: "MESSAGE\ndestination:/topic/rooms/room-1/messages\n\n{\"id\":\"m1\"}\0"
    });
    socket.onmessage({
        data: "MESSAGE\ndestination:/topic/rooms/room-1/members\n\n{\"roomId\":\"room-1\",\"memberCount\":2}\0"
    });
    socket.onmessage({
        data: "MESSAGE\ndestination:/topic/rooms/room-1/files\n\n{\"id\":\"f1\"}\0"
    });
    assert.equal(receivedMessages[0].id, "m1");
    assert.equal(receivedMembers[0].memberCount, 2);
    assert.equal(receivedFiles[0].id, "f1");
    assert.ok(states.includes("connected"));
    assert.equal(manager.sendMessage({ content: "live" }), true);
    assert.match(socket.sent[4], /destination:\/app\/rooms\/room-1\/messages/);
    manager.stop();
});

test("connection retries use capped exponential backoff", () => {
    const context = createContext("http://localhost:5500/dashboard.html");
    vm.runInNewContext(source, context);
    const manager = new context.window.LocalLinkConnection.RoomConnectionManager({
        roomId: "room-1",
        token: "session-token",
        onState() {},
        onMessage() {},
        onMembers() {}
    });

    manager.stopped = false;
    manager.retryDelay = 30_000;
    manager.scheduleRetry();

    assert.equal(context.timeouts.length, 1);
    assert.ok(context.timeouts[0].delay >= 30_000);
    assert.ok(context.timeouts[0].delay <= 36_000);
    assert.equal(manager.retryDelay, 30_000);
    manager.stop();
});

function createContext(href) {
    class FakeWebSocket {
        static OPEN = 1;
        static CLOSING = 2;
        static instances = [];

        constructor(url) {
            this.url = url;
            this.readyState = 0;
            this.sent = [];
            FakeWebSocket.instances.push(this);
        }

        send(frame) {
            this.sent.push(frame);
        }

        close() {
            this.readyState = FakeWebSocket.CLOSING;
            this.onclose?.();
        }
    }

    const timeouts = [];
    const window = {
        location: { href },
        addEventListener() {},
        removeEventListener() {},
        setTimeout(callback) {
            const timer = { callback, delay: arguments[1] };
            timeouts.push(timer);
            return timer;
        },
        clearTimeout() {},
        setInterval() {
            return 1;
        },
        clearInterval() {}
    };
    return {
        window,
        navigator: { onLine: true },
        WebSocket: FakeWebSocket,
        FakeWebSocket,
        timeouts,
        URL,
        TextEncoder,
        Date,
        Math
    };
}

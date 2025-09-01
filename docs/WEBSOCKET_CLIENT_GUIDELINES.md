
# WebSocket Client Guidelines

This document provides guidelines for client-side implementation of WebSocket communication with the SimpleChatServer, focusing on heartbeat and automatic reconnection mechanisms.

## 1. Heartbeat Mechanism

To maintain a stable connection and prevent timeouts, the client should send a heartbeat message to the server periodically.

**Implementation:**

*   **Interval:** Send a heartbeat message every 30 seconds.
*   **Message Format:** The heartbeat message should be a JSON object with the following structure:

    ```json
    {
      "type": "HEARTBEAT",
      "timestamp": "2025-09-01T12:00:00Z"
    }
    ```

    *   `type`: Must be `HEARTBEAT`.
    *   `timestamp`: The current UTC timestamp in ISO 8601 format.

**Example (JavaScript):**

```javascript
let heartbeatInterval;

function startHeartbeat(socket) {
  heartbeatInterval = setInterval(() => {
    if (socket.readyState === WebSocket.OPEN) {
      const heartbeatMessage = {
        type: 'HEARTBEAT',
        timestamp: new Date().toISOString(),
      };
      socket.send(JSON.stringify(heartbeatMessage));
    }
  }, 30000); // 30 seconds
}

function stopHeartbeat() {
  clearInterval(heartbeatInterval);
}
```

## 2. Automatic Reconnection

The client should implement a mechanism to automatically reconnect to the server if the connection is lost.

**Implementation:**

*   **Detection:** Listen for the `onclose` event on the WebSocket object.
*   **Reconnection Strategy:** Use an exponential backoff strategy to avoid overwhelming the server with reconnection attempts.

**Example (JavaScript):**

```javascript
let reconnectAttempts = 0;
const maxReconnectAttempts = 10;

function connect() {
  const socket = new WebSocket('ws://localhost:8080/ws/chat/123'); // Replace with your user ID

  socket.onopen = () => {
    console.log('WebSocket connection established.');
    reconnectAttempts = 0; // Reset attempts on successful connection
    startHeartbeat(socket);
  };

  socket.onclose = (event) => {
    console.log(`WebSocket connection closed: ${event.code} ${event.reason}`);
    stopHeartbeat();
    handleReconnect();
  };

  socket.onerror = (error) => {
    console.error('WebSocket error:', error);
    socket.close(); // Ensure the socket is closed before attempting to reconnect
  };

  // ... other event listeners (onmessage, etc.)
}

function handleReconnect() {
  if (reconnectAttempts < maxReconnectAttempts) {
    const delay = Math.pow(2, reconnectAttempts) * 1000; // Exponential backoff
    console.log(`Attempting to reconnect in ${delay / 1000} seconds...`);
    setTimeout(() => {
      reconnectAttempts++;
      connect();
    }, delay);
  } else {
    console.error('Max reconnection attempts reached. Please check your connection and refresh the page.');
  }
}

// Initial connection
connect();
```

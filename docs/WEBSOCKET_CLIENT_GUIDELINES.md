# WebSocket 클라이언트 가이드라인

이 문서는 SimpleChatServer의 WebSocket API를 사용하여 클라이언트 애플리케이션을 개발하기 위한 가이드라인을 제공합니다. 서버는 **순수 WebSocket**을 사용하며, 모든 메시지는 **JSON 형식**으로 주고받습니다.

## 1. 연결 (Connection)

클라이언트는 특정 사용자 ID와 채팅방 ID를 포함하는 URL을 통해 WebSocket 연결을 설정합니다. 연결 시 JWT(JSON Web Token)를 사용하여 인증해야 합니다.

*   **엔드포인트 형식:** `ws(s)://{호스트}/ws/chat/{userId}?token={JWT_토큰}&roomId={채팅방_ID}`
    *   `{호스트}`: 서버의 도메인 또는 IP 주소 (예: `localhost:8080`)
    *   `{userId}`: 연결하려는 사용자의 고유 ID (경로 변수)
    *   `{JWT_토큰}`: 사용자 인증을 위한 JWT 토큰 (쿼리 파라미터)
    *   `{채팅방_ID}`: 연결하려는 채팅방의 고유 ID (쿼리 파라미터)
*   **프로토콜:** 순수 WebSocket
*   **인증:** 연결 URL의 `token` 쿼리 파라미터로 JWT 토큰을 전달합니다.

**JavaScript 예시 (Native WebSocket 사용):**

```javascript
const userId = 123; // 실제 사용자 ID
const roomId = 'chat-room-1'; // 실제 채팅방 ID
const jwtToken = 'YOUR_JWT_TOKEN_HERE'; // 로그인 후 서버로부터 받은 JWT 토큰

const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const host = window.location.host;
const wsUrl = `${protocol}//${host}/ws/chat/${userId}?token=${encodeURIComponent(jwtToken)}&roomId=${encodeURIComponent(roomId)}`;

const websocket = new WebSocket(wsUrl);

websocket.onopen = (event) => {
    console.log('WebSocket 연결 성공:', event);
    // 연결 성공 후 메시지 전송 및 수신 로직을 여기에 작성합니다.
};

websocket.onmessage = (event) => {
    const message = JSON.parse(event.data);
    console.log('메시지 수신:', message);
    // 수신된 메시지 처리
};

websocket.onclose = (event) => {
    console.log('WebSocket 연결 종료:', event);
    // 연결 종료 처리 (재연결 로직 등)
};

websocket.onerror = (error) => {
    console.error('WebSocket 에러:', error);
    // 에러 처리
};

// 메시지 전송 예시
function sendMessage(messageObject) {
    if (websocket.readyState === WebSocket.OPEN) {
        websocket.send(JSON.stringify(messageObject));
    } else {
        console.warn('WebSocket이 연결되지 않았습니다.');
    }
}
```

## 2. 메시지 형식 (Message Format)

모든 WebSocket 메시지는 `type` 필드를 포함하는 JSON 객체입니다. `type` 필드는 메시지의 종류를 나타내며, 각 `type`에 따라 추가 필드가 달라집니다.

### 공통 메시지 필드 (서버 -> 클라이언트)

서버에서 클라이언트로 전송되는 모든 메시지는 다음 공통 필드를 포함할 수 있습니다.

```json
{
  "type": "string",       // 메시지 타입 (예: "CHAT", "JOIN", "LEAVE", "SYSTEM", "ERROR", "HEARTBEAT", "ACK")
  "messageId": "string",  // 메시지 고유 ID (선택 사항)
  "timestamp": long,      // 메시지 생성 시간 (Unix timestamp in milliseconds)
  "sessionId": "string"   // 메시지를 보낸/받는 세션 ID (선택 사항)
}
```

### 주요 메시지 타입별 상세 필드

#### 2.1. `JOIN` 메시지 (클라이언트 -> 서버 / 서버 -> 클라이언트)

사용자가 채팅방에 입장했음을 알립니다.

*   **클라이언트 -> 서버 전송 시:**
    ```json
    {
      "type": "JOIN",
      "roomId": "string",       // 입장할 채팅방 ID
      "userId": long,           // 사용자 ID
      "userNickname": "string"  // 사용자 닉네임
    }
    ```
*   **서버 -> 클라이언트 수신 시:** (위 공통 필드 +)
    ```json
    {
      "type": "JOIN",
      "roomId": "string",
      "userId": long,
      "userNickname": "string"
    }
    ```

#### 2.2. `CHAT` 메시지 (클라이언트 -> 서버 / 서버 -> 클라이언트)

일반적인 채팅 메시지입니다.

*   **클라이언트 -> 서버 전송 시:**
    ```json
    {
      "type": "CHAT",
      "roomId": "string",       // 메시지를 보낼 채팅방 ID
      "userId": long,           // 발신자 ID
      "userNickname": "string", // 발신자 닉네임
      "content": "string"       // 메시지 내용
    }
    ```
*   **서버 -> 클라이언트 수신 시:** (위 공통 필드 +)
    ```json
    {
      "type": "CHAT",
      "roomId": "string",
      "userId": long,
      "userNickname": "string",
      "content": "string",
      "highlightedContent": "string" // 검색어 하이라이트된 내용 (선택 사항)
    }
    ```

#### 2.3. `LEAVE` 메시지 (클라이언트 -> 서버 / 서버 -> 클라이언트)

사용자가 채팅방을 나갔음을 알립니다.

*   **클라이언트 -> 서버 전송 시:**
    ```json
    {
      "type": "LEAVE",
      "roomId": "string",       // 나갈 채팅방 ID
      "userId": long,           // 사용자 ID
      "userNickname": "string"  // 사용자 닉네임
    }
    ```
*   **서버 -> 클라이언트 수신 시:** (위 공통 필드 +)
    ```json
    {
      "type": "LEAVE",
      "roomId": "string",
      "userId": long,
      "userNickname": "string"
    }
    ```

#### 2.4. `TYPING` 메시지 (클라이언트 -> 서버 / 서버 -> 클라이언트)

사용자의 타이핑 상태를 알립니다.

*   **클라이언트 -> 서버 전송 시:**
    ```json
    {
      "type": "TYPING",
      "roomId": "string",       // 타이핑 중인 채팅방 ID
      "userId": long,           // 사용자 ID
      "userNickname": "string", // 사용자 닉네임
      "isTyping": boolean       // true: 타이핑 시작, false: 타이핑 중단
    }
    ```
*   **서버 -> 클라이언트 수신 시:** (위 공통 필드 +)
    ```json
    {
      "type": "TYPING",
      "roomId": "string",
      "userId": long,
      "userNickname": "string",
      "isTyping": boolean
    }
    ```

#### 2.5. `HEARTBEAT` 메시지 (클라이언트 -> 서버 / 서버 -> 클라이언트)

연결 활성 상태를 유지하기 위한 하트비트 메시지입니다.

*   **클라이언트 -> 서버 전송 시:**
    ```json
    {
      "type": "HEARTBEAT"
    }
    ```
*   **서버 -> 클라이언트 수신 시:**
    ```json
    {
      "type": "HEARTBEAT" // 또는 "pong"으로 응답 가능
    }
    ```

#### 2.6. `SYSTEM` 메시지 (서버 -> 클라이언트)

서버에서 클라이언트로 보내는 시스템 메시지 (예: 공지, 경고).

```json
{
  "type": "SYSTEM",
  "content": "string",    // 시스템 메시지 내용
  "level": "string",      // 메시지 레벨 (예: "INFO", "WARN", "ERROR")
  "userId": long,         // 관련 사용자 ID (선택 사항)
  "userNickname": "string"// 관련 사용자 닉네임 (선택 사항)
}
```

#### 2.7. `ERROR` 메시지 (서버 -> 클라이언트)

오류 발생 시 서버에서 클라이언트로 보내는 메시지.

```json
{
  "type": "ERROR",
  "errorCode": "string",  // 오류 코드
  "errorMessage": "string"// 오류 메시지
}
```

#### 2.8. `ACK` 메시지 (서버 -> 클라이언트)

클라이언트의 메시지 수신 확인 응답.

```json
{
  "type": "ACK",
  "originalMessageId": "string", // 원본 메시지 ID
  "status": "string"             // 처리 상태 (예: "SUCCESS", "FAILED")
}
```

## 3. 메시지 전송 (Sending Messages)

클라이언트는 `WebSocket.send()` 메서드를 사용하여 JSON 문자열로 변환된 메시지 객체를 전송합니다.

```javascript
// 예시: 채팅 메시지 전송
const chatMessage = {
    type: "CHAT",
    roomId: "chat-room-1",
    userId: 123,
    userNickname: "사용자1",
    content: "안녕하세요!"
};
websocket.send(JSON.stringify(chatMessage));

// 예시: 타이핑 시작 알림
const typingMessage = {
    type: "TYPING",
    roomId: "chat-room-1",
    userId: 123,
    userNickname: "사용자1",
    isTyping: true
};
websocket.send(JSON.stringify(typingMessage));
```

## 4. 메시지 수신 (Receiving Messages)

클라이언트는 `websocket.onmessage` 이벤트 리스너를 통해 서버로부터 메시지를 수신합니다. 수신된 데이터는 JSON 문자열이므로 `JSON.parse()`를 사용하여 객체로 변환해야 합니다.

```javascript
websocket.onmessage = (event) => {
    const receivedMessage = JSON.parse(event.data);
    console.log('수신된 메시지:', receivedMessage);

    switch (receivedMessage.type) {
        case 'CHAT':
            // 채팅 메시지 처리 로직
            console.log(`[${receivedMessage.userNickname}]: ${receivedMessage.content}`);
            break;
        case 'JOIN':
            // 입장 메시지 처리 로직
            console.log(`${receivedMessage.userNickname}님이 입장했습니다.`);
            break;
        case 'LEAVE':
            // 퇴장 메시지 처리 로직
            console.log(`${receivedMessage.userNickname}님이 퇴장했습니다.`);
            break;
        case 'TYPING':
            // 타이핑 상태 메시지 처리 로직
            console.log(`${receivedMessage.userNickname}님이 타이핑 중: ${receivedMessage.isTyping}`);
            break;
        case 'SYSTEM':
            // 시스템 메시지 처리 로직
            console.log(`[시스템 알림]: ${receivedMessage.content}`);
            break;
        case 'ERROR':
            // 에러 메시지 처리 로직
            console.error(`[에러 ${receivedMessage.errorCode}]: ${receivedMessage.errorMessage}`);
            break;
        case 'HEARTBEAT':
            // 하트비트 응답 처리 (클라이언트에서 보낸 HEARTBEAT에 대한 서버의 응답)
            console.log('하트비트 수신');
            break;
        case 'ACK':
            // ACK 메시지 처리
            console.log(`메시지 ${receivedMessage.originalMessageId} 처리 상태: ${receivedMessage.status}`);
            break;
        default:
            console.warn('알 수 없는 메시지 타입:', receivedMessage.type);
    }
};
```

## 5. 하트비트 (Heartbeat)

클라이언트는 연결 활성 상태를 유지하기 위해 주기적으로 `HEARTBEAT` 메시지를 서버로 전송해야 합니다. 서버는 이에 대해 `HEARTBEAT` (또는 `pong`) 메시지로 응답할 수 있습니다. `websocket-client.js`는 이 로직을 내장하고 있습니다.

## 6. 재연결 로직 (Reconnection Logic)

`websocket-client.js`는 연결이 끊어졌을 때 자동으로 재연결을 시도하는 로직을 포함하고 있습니다. 이는 지수 백오프(Exponential Backoff) 전략을 사용하여 재연결 시도 간격을 늘리고, 최대 재연결 시도 횟수를 제한합니다.

---
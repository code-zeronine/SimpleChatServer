/**
 * SimpleChatServer - WebSocket Client
 * 
 * WebSocket 연결 관리 및 실시간 메시징을 위한 클라이언트 라이브러리
 */

/**
 * WebSocket 클라이언트 클래스
 */
class ChatClient {
    constructor(options = {}) {
        this.options = {
            url: options.url || this.getWebSocketUrl(),
            reconnectInterval: options.reconnectInterval || 5000,
            maxReconnectAttempts: options.maxReconnectAttempts || 10,
            heartbeatInterval: options.heartbeatInterval || 30000,
            debug: options.debug || false,
            ...options
        };
        
        // 상태 관리
        this.websocket = null;
        this.isConnected = false;
        this.isReconnecting = false;
        this.reconnectAttempts = 0;
        this.lastActivity = Date.now();
        
        // 타이머
        this.reconnectTimer = null;
        this.heartbeatTimer = null;
        
        // 이벤트 핸들러
        this.eventHandlers = {
            open: [],
            close: [],
            error: [],
            message: [],
            reconnect: [],
            stateChange: []
        };
        
        // 메시지 타입별 핸들러
        this.messageHandlers = new Map();
        
        // 전송 대기 중인 메시지
        this.pendingMessages = [];
        
        // 현재 사용자 및 방 정보
        this.currentUser = null;
        this.currentRoomId = null;
        
        this.log('ChatClient initialized', this.options);
    }
    
    /**
     * WebSocket URL 생성
     */
    getWebSocketUrl() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/ws/chat`;
    }
    
    /**
     * 이벤트 리스너 등록
     */
    on(event, handler) {
        if (this.eventHandlers[event]) {
            this.eventHandlers[event].push(handler);
        }
        return this;
    }
    
    /**
     * 이벤트 리스너 제거
     */
    off(event, handler) {
        if (this.eventHandlers[event]) {
            const index = this.eventHandlers[event].indexOf(handler);
            if (index > -1) {
                this.eventHandlers[event].splice(index, 1);
            }
        }
        return this;
    }
    
    /**
     * 이벤트 발생
     */
    emit(event, data) {
        if (this.eventHandlers[event]) {
            this.eventHandlers[event].forEach(handler => {
                try {
                    handler(data);
                } catch (error) {
                    this.log('Event handler error', { event, error });
                }
            });
        }
    }
    
    /**
     * 메시지 타입별 핸들러 등록
     */
    onMessage(type, handler) {
        if (!this.messageHandlers.has(type)) {
            this.messageHandlers.set(type, []);
        }
        this.messageHandlers.get(type).push(handler);
        return this;
    }
    
    /**
     * 연결 시작
     */
    connect(user = null, roomId = null) {
        if (this.isConnected || this.isReconnecting) {
            this.log('Already connected or connecting');
            return Promise.resolve();
        }
        
        this.currentUser = user;
        this.currentRoomId = roomId;
        
        return new Promise((resolve, reject) => {
            try {
                this.log('Connecting to WebSocket...', this.options.url);
                
                this.websocket = new WebSocket(this.options.url);
                this.setupWebSocketEvents();
                
                // 연결 성공 시 resolve
                const onOpen = () => {
                    this.off('open', onOpen);
                    this.off('error', onError);
                    resolve();
                };
                
                // 연결 실패 시 reject
                const onError = (error) => {
                    this.off('open', onOpen);
                    this.off('error', onError);
                    reject(error);
                };
                
                this.on('open', onOpen);
                this.on('error', onError);
                
            } catch (error) {
                this.log('Connection error', error);
                reject(error);
            }
        });
    }
    
    /**
     * WebSocket 이벤트 설정
     */
    setupWebSocketEvents() {
        this.websocket.onopen = (event) => {
            this.log('WebSocket connected');
            this.isConnected = true;
            this.isReconnecting = false;
            this.reconnectAttempts = 0;
            this.lastActivity = Date.now();
            
            // 재연결 타이머 정리
            if (this.reconnectTimer) {
                clearTimeout(this.reconnectTimer);
                this.reconnectTimer = null;
            }
            
            // 하트비트 시작
            this.startHeartbeat();
            
            // 대기 중인 메시지 전송
            this.sendPendingMessages();
            
            // 사용자 정보가 있으면 자동 입장
            if (this.currentUser && this.currentRoomId) {
                this.joinRoom(this.currentRoomId, this.currentUser);
            }
            
            this.emit('open', event);
            this.emit('stateChange', { state: 'connected', isConnected: true });
        };
        
        this.websocket.onmessage = (event) => {
            this.lastActivity = Date.now();
            
            try {
                const data = JSON.parse(event.data);
                this.log('Received message', data);
                
                // 하트비트 응답 처리
                if (data.type === 'pong') {
                    return;
                }
                
                // 메시지 타입별 핸들러 실행
                if (this.messageHandlers.has(data.type)) {
                    this.messageHandlers.get(data.type).forEach(handler => {
                        try {
                            handler(data);
                        } catch (error) {
                            this.log('Message handler error', { type: data.type, error });
                        }
                    });
                }
                
                this.emit('message', data);
                
            } catch (error) {
                this.log('Message parsing error', error);
            }
        };
        
        this.websocket.onclose = (event) => {
            this.log('WebSocket closed', { code: event.code, reason: event.reason });
            this.isConnected = false;
            
            // 하트비트 정지
            this.stopHeartbeat();
            
            this.emit('close', event);
            this.emit('stateChange', { state: 'disconnected', isConnected: false });
            
            // 자동 재연결 (정상 종료가 아닌 경우)
            if (!event.wasClean && this.reconnectAttempts < this.options.maxReconnectAttempts) {
                this.scheduleReconnect();
            }
        };
        
        this.websocket.onerror = (error) => {
            this.log('WebSocket error', error);
            this.emit('error', error);
        };
    }
    
    /**
     * 재연결 스케줄링
     */
    scheduleReconnect() {
        if (this.isReconnecting || this.reconnectAttempts >= this.options.maxReconnectAttempts) {
            return;
        }
        
        this.isReconnecting = true;
        this.reconnectAttempts++;
        
        const delay = this.options.reconnectInterval * Math.pow(1.5, this.reconnectAttempts - 1);
        const maxDelay = 30000; // 최대 30초
        const actualDelay = Math.min(delay, maxDelay);
        
        this.log(`Scheduling reconnect attempt ${this.reconnectAttempts}/${this.options.maxReconnectAttempts} in ${actualDelay}ms`);
        
        this.emit('stateChange', { 
            state: 'reconnecting', 
            isConnected: false, 
            attempt: this.reconnectAttempts,
            maxAttempts: this.options.maxReconnectAttempts,
            delay: actualDelay
        });
        
        this.reconnectTimer = setTimeout(() => {
            this.log('Attempting to reconnect...');
            this.emit('reconnect', { attempt: this.reconnectAttempts });
            
            this.connect(this.currentUser, this.currentRoomId)
                .catch(error => {
                    this.log('Reconnect failed', error);
                    if (this.reconnectAttempts < this.options.maxReconnectAttempts) {
                        this.scheduleReconnect();
                    } else {
                        this.log('Max reconnect attempts reached');
                        this.isReconnecting = false;
                        this.emit('stateChange', { 
                            state: 'failed', 
                            isConnected: false,
                            error: 'Max reconnect attempts reached'
                        });
                    }
                });
        }, actualDelay);
    }
    
    /**
     * 하트비트 시작
     */
    startHeartbeat() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
        }
        
        this.heartbeatTimer = setInterval(() => {
            if (this.isConnected && this.websocket?.readyState === WebSocket.OPEN) {
                // 마지막 활동으로부터 너무 오래 지났는지 확인
                const timeSinceLastActivity = Date.now() - this.lastActivity;
                if (timeSinceLastActivity > this.options.heartbeatInterval * 2) {
                    this.log('Connection seems stale, closing...');
                    this.disconnect();
                    return;
                }
                
                // ping 전송
                this.send({ type: 'ping', timestamp: Date.now() });
            }
        }, this.options.heartbeatInterval);
    }
    
    /**
     * 하트비트 정지
     */
    stopHeartbeat() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }
    
    /**
     * 메시지 전송
     */
    send(message) {
        if (!this.isConnected || this.websocket?.readyState !== WebSocket.OPEN) {
            this.log('Not connected, queuing message', message);
            this.pendingMessages.push(message);
            return false;
        }
        
        try {
            const messageStr = typeof message === 'string' ? message : JSON.stringify(message);
            this.websocket.send(messageStr);
            this.log('Message sent', message);
            return true;
        } catch (error) {
            this.log('Send error', error);
            this.pendingMessages.push(message);
            return false;
        }
    }
    
    /**
     * 대기 중인 메시지 전송
     */
    sendPendingMessages() {
        if (this.pendingMessages.length === 0) {
            return;
        }
        
        this.log(`Sending ${this.pendingMessages.length} pending messages`);
        
        const messages = [...this.pendingMessages];
        this.pendingMessages = [];
        
        messages.forEach(message => {
            if (!this.send(message)) {
                // 전송 실패 시 다시 대기열에 추가
                this.pendingMessages.push(message);
            }
        });
    }
    
    /**
     * 채팅방 입장
     */
    joinRoom(roomId, user = null) {
        const joinMessage = {
            type: 'join',
            roomId: roomId,
            userId: user?.id || this.currentUser?.id,
            userName: user?.name || this.currentUser?.name,
            timestamp: Date.now()
        };
        
        this.currentRoomId = roomId;
        if (user) this.currentUser = user;
        
        this.log('Joining room', joinMessage);
        return this.send(joinMessage);
    }
    
    /**
     * 채팅방 퇴장
     */
    leaveRoom(roomId = null) {
        const targetRoomId = roomId || this.currentRoomId;
        if (!targetRoomId) {
            this.log('No room to leave');
            return false;
        }
        
        const leaveMessage = {
            type: 'leave',
            roomId: targetRoomId,
            userId: this.currentUser?.id,
            timestamp: Date.now()
        };
        
        this.log('Leaving room', leaveMessage);
        const result = this.send(leaveMessage);
        
        if (roomId === this.currentRoomId || !roomId) {
            this.currentRoomId = null;
        }
        
        return result;
    }
    
    /**
     * 채팅 메시지 전송
     */
    sendChatMessage(content, options = {}) {
        if (!this.currentRoomId) {
            this.log('No current room for sending message');
            return false;
        }
        
        const message = {
            type: 'message',
            roomId: this.currentRoomId,
            content: content,
            userId: this.currentUser?.id,
            userName: this.currentUser?.name,
            timestamp: Date.now(),
            tempId: options.tempId || `temp_${Date.now()}`,
            ...options
        };
        
        this.log('Sending chat message', message);
        return this.send(message);
    }
    
    /**
     * 타이핑 시작 알림
     */
    startTyping() {
        if (!this.currentRoomId) return false;
        
        return this.send({
            type: 'typing_start',
            roomId: this.currentRoomId,
            userId: this.currentUser?.id,
            userName: this.currentUser?.name,
            timestamp: Date.now()
        });
    }
    
    /**
     * 타이핑 중단 알림
     */
    stopTyping() {
        if (!this.currentRoomId) return false;
        
        return this.send({
            type: 'typing_stop',
            roomId: this.currentRoomId,
            userId: this.currentUser?.id,
            timestamp: Date.now()
        });
    }
    
    /**
     * 연결 상태 확인
     */
    isConnectionOpen() {
        return this.isConnected && this.websocket?.readyState === WebSocket.OPEN;
    }
    
    /**
     * 연결 상태 정보
     */
    getConnectionState() {
        return {
            isConnected: this.isConnected,
            isReconnecting: this.isReconnecting,
            reconnectAttempts: this.reconnectAttempts,
            maxReconnectAttempts: this.options.maxReconnectAttempts,
            readyState: this.websocket?.readyState,
            currentUser: this.currentUser,
            currentRoomId: this.currentRoomId,
            pendingMessages: this.pendingMessages.length
        };
    }
    
    /**
     * 연결 해제
     */
    disconnect(code = 1000, reason = 'Client disconnect') {
        this.log('Disconnecting...', { code, reason });
        
        // 재연결 방지
        this.reconnectAttempts = this.options.maxReconnectAttempts;
        
        // 타이머 정리
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
        
        this.stopHeartbeat();
        
        // 현재 방에서 퇴장
        if (this.currentRoomId && this.isConnected) {
            this.leaveRoom();
        }
        
        // WebSocket 연결 해제
        if (this.websocket) {
            this.websocket.close(code, reason);
            this.websocket = null;
        }
        
        this.isConnected = false;
        this.isReconnecting = false;
        this.currentRoomId = null;
        
        this.emit('stateChange', { state: 'disconnected', isConnected: false });
    }
    
    /**
     * 로깅
     */
    log(message, data = null) {
        if (!this.options.debug) return;
        
        const timestamp = new Date().toISOString();
        if (data) {
            console.log(`[ChatClient ${timestamp}] ${message}`, data);
        } else {
            console.log(`[ChatClient ${timestamp}] ${message}`);
        }
    }
    
    /**
     * 정리 작업
     */
    destroy() {
        this.log('Destroying ChatClient...');
        this.disconnect();
        
        // 모든 이벤트 핸들러 정리
        Object.keys(this.eventHandlers).forEach(event => {
            this.eventHandlers[event] = [];
        });
        
        // 메시지 핸들러 정리
        this.messageHandlers.clear();
        
        // 대기 메시지 정리
        this.pendingMessages = [];
    }
}

/**
 * WebSocket 클라이언트 팩토리
 */
const WebSocketClient = {
    /**
     * 새 클라이언트 인스턴스 생성
     */
    create(options = {}) {
        return new ChatClient(options);
    },
    
    /**
     * 글로벌 클라이언트 인스턴스
     */
    _globalClient: null,
    
    /**
     * 글로벌 클라이언트 가져오기 (싱글톤)
     */
    getGlobalClient(options = {}) {
        if (!this._globalClient) {
            this._globalClient = new ChatClient({
                debug: window.location.hostname === 'localhost',
                ...options
            });
        }
        return this._globalClient;
    },
    
    /**
     * 글로벌 클라이언트 정리
     */
    destroyGlobalClient() {
        if (this._globalClient) {
            this._globalClient.destroy();
            this._globalClient = null;
        }
    }
};

// 페이지 언로드 시 글로벌 클라이언트 정리
window.addEventListener('beforeunload', () => {
    WebSocketClient.destroyGlobalClient();
});

// 전역 노출
if (typeof window !== 'undefined') {
    window.ChatClient = ChatClient;
    window.WebSocketClient = WebSocketClient;
}

// CommonJS/AMD 지원
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { ChatClient, WebSocketClient };
}
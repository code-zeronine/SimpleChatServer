/**
 * SimpleChatServer - Chat Page JavaScript
 * 
 * 실시간 채팅 화면의 기능을 담당합니다.
 */

document.addEventListener('DOMContentLoaded', function() {
    const { DOM, Http, Storage, Auth, Toast, Utils } = window.SimpleChatServer.utils;
    
    // 인증 확인
    if (!Auth.isAuthenticated()) {
        window.location.href = '/login?auth=required';
        return;
    }
    
    // 채팅 관리 객체
    const ChatManager = {
        // 상태
        currentRoomId: null,
        currentUser: null,
        websocket: null,
        messages: [],
        isConnected: false,
        typingTimer: null,
        unreadCount: 0,
        
        // 초기화
        init() {
            this.currentUser = Storage.getUser();
            console.log("currentUser: {}", this.currentUser)
            this.currentRoomId = this.getRoomIdFromURL();
            
            if (!this.currentRoomId) {
                Toast.error('잘못된 채팅방 접근입니다.');
                window.location.href = '/rooms';
                return;
            }
            
            this.setupEventListeners();
            this.loadChatRoom();
            this.connectWebSocket();
        },
        
        // URL에서 채팅방 ID 추출
        getRoomIdFromURL() {
            const params = new URLSearchParams(window.location.search);
            return params.get('roomId');
        },

        // 타임스탬프를 Date 객체로 변환 (다양한 형식 지원)
        parseTimestamp(timestamp) {
            if (!timestamp) return null;
            if (typeof timestamp === 'number') return new Date(timestamp);
            if (typeof timestamp === 'string') {
                if (timestamp.trim() === '') return null;
                const date = new Date(timestamp);
                if (isNaN(date.getTime())) return null;
                return date;
            }
            if (typeof timestamp === 'object' && timestamp.epochSecond !== undefined && timestamp.nano !== undefined) {
                return new Date(timestamp.epochSecond * 1000 + Math.floor(timestamp.nano / 1000000));
            }
            if (timestamp instanceof Date) return timestamp;
            
            console.warn('Unknown timestamp format:', timestamp);
            return null;
        },
        
        // 이벤트 리스너 설정
        setupEventListeners() {
            // 뒤로가기 버튼
            const backBtn = DOM.select('#backBtn');
            DOM.on(backBtn, 'click', () => {
                window.location.href = '/rooms';
            });
            
            // 메시지 입력창
            const messageInput = DOM.select('#messageInput');
            DOM.on(messageInput, 'input', this.handleMessageInput.bind(this));
            DOM.on(messageInput, 'keydown', this.handleKeyDown.bind(this));
            
            // 전송 버튼
            const sendBtn = DOM.select('#sendBtn');
            DOM.on(sendBtn, 'click', this.sendMessage.bind(this));
            
            // 파일 첨부
            const attachmentBtn = DOM.select('#attachmentBtn');
            DOM.on(attachmentBtn, 'click', this.showAttachmentModal.bind(this));
            
            // 참여자 목록 버튼
            const memberListBtn = DOM.select('#memberListBtn');
            DOM.on(memberListBtn, 'click', this.toggleMembersSidebar.bind(this));
            
            // 스크롤 다운 버튼
            const scrollDownBtn = DOM.select('#scrollDownBtn');
            DOM.on(scrollDownBtn, 'click', this.scrollToBottom.bind(this));
            
            // 메시지 리스트 스크롤
            const messagesList = DOM.select('#messagesList');
            DOM.on(messagesList, 'scroll', this.handleScroll.bind(this));
            
            // 모달 이벤트들
            this.setupModalEvents();
            
            // 파일 드래그 앤 드롭
            this.setupFileDropEvents();
        },
        
        // 모달 이벤트 설정
        setupModalEvents() {
            // 파일 첨부 모달
            const closeAttachmentModal = DOM.select('#closeAttachmentModal');
            const cancelAttachment = DOM.select('#cancelAttachment');
            
            DOM.on(closeAttachmentModal, 'click', this.hideAttachmentModal.bind(this));
            DOM.on(cancelAttachment, 'click', this.hideAttachmentModal.bind(this));
            
            // 참여자 사이드바
            const closeMembersSidebar = DOM.select('#closeMembersSidebar');
            const overlay = DOM.select('#overlay');
            
            DOM.on(closeMembersSidebar, 'click', this.hideMembersSidebar.bind(this));
            DOM.on(overlay, 'click', this.hideMembersSidebar.bind(this));
            
            // ESC 키로 모달/사이드바 닫기
            DOM.on(document, 'keydown', (e) => {
                if (e.key === 'Escape') {
                    this.hideAttachmentModal();
                    this.hideMembersSidebar();
                }
            });
        },
        
        // 파일 드래그 앤 드롭 설정
        setupFileDropEvents() {
            const fileDropArea = DOM.select('#fileDropArea');
            const fileInput = DOM.select('#fileInput');
            const selectFileBtn = DOM.select('#selectFileBtn');
            
            // 파일 선택 버튼
            DOM.on(selectFileBtn, 'click', () => {
                fileInput.click();
            });
            
            // 파일 드래그 이벤트
            DOM.on(fileDropArea, 'dragover', (e) => {
                e.preventDefault();
                fileDropArea.classList.add('drag-over');
            });
            
            DOM.on(fileDropArea, 'dragleave', () => {
                fileDropArea.classList.remove('drag-over');
            });
            
            DOM.on(fileDropArea, 'drop', (e) => {
                e.preventDefault();
                fileDropArea.classList.remove('drag-over');
                const files = e.dataTransfer.files;
                this.handleFileSelection(files);
            });
            
            // 파일 입력 변경
            DOM.on(fileInput, 'change', (e) => {
                this.handleFileSelection(e.target.files);
            });
        },
        
        // 채팅방 정보 로드
        async loadChatRoom() {
            try {
                const response = await Http.get(`/api/rooms/${this.currentRoomId}`);
                if (response.success) {
                    this.updateRoomInfo(response.data);
                    await this.loadMessages();
                } else {
                    Toast.error('채팅방 정보를 불러올 수 없습니다.');
                    window.location.href = '/rooms';
                }
            } catch (error) {
                console.error('채팅방 로드 오류:', error);
                Toast.error('채팅방에 접근할 수 없습니다.');
                window.location.href = '/rooms';
            }
        },
        
        // 채팅방 정보 업데이트
        updateRoomInfo(roomData) {
            const roomName = DOM.select('#roomName');
            const memberCount = DOM.select('#memberCount');
            
            // ChatRoomDetailsDto 구조: { room: ChatRoomDto, participants: [...], ... }
            const room = roomData.room || roomData; // ChatRoomDetailsDto 또는 ChatRoomDto 대응
            const participantCount = room.currentParticipants || roomData.participants?.length || 0;
            
            roomName.textContent = room.name || '채팅방';
            memberCount.textContent = `참여자 ${participantCount}명`;
            
            // 페이지 제목 업데이트
            document.title = `${room.name} - SimpleChatServer`;
        },
        
        // 메시지 로드
        async loadMessages() {
            try {
                const loadingEl = DOM.select('#loadingMessages');
                loadingEl.style.display = 'block';
                
                const response = await Http.get(`/api/messages/room/${this.currentRoomId}?limit=50`);
                
                if (response.success && response.data) {
                    this.messages = response.data.content || [];
                    this.renderMessages();
                    setTimeout(() => this.scrollToBottom(false), 100);
                } else {
                    console.warn('메시지 로드 실패:', response);
                }
            } catch (error) {
                console.error('메시지 로드 오류:', error);
                Toast.error('메시지를 불러올 수 없습니다.');
            } finally {
                const loadingEl = DOM.select('#loadingMessages');
                loadingEl.style.display = 'none';
            }
        },
        
        // 메시지 렌더링
        renderMessages() {
            const messagesList = DOM.select('#messagesList');
            
            // 모든 메시지 관련 요소 제거 (더 확실한 정리)
            const messagesToRemove = messagesList.querySelectorAll('.message-group, .message, .message-sender');
            messagesToRemove.forEach(element => element.remove());
            
            console.log('Cleared', messagesToRemove.length, 'message elements');
            
            if (this.messages.length === 0) {
                return;
            }
            
            // 메시지 그룹화 및 렌더링
            const groupedMessages = this.groupMessages(this.messages);
            
            groupedMessages.forEach((group, index) => {
                const groupEl = this.createMessageGroup(group);
                messagesList.appendChild(groupEl);
            });
        },
        
        // 메시지 그룹화 (같은 사용자의 연속 메시지)
        groupMessages(messages) {
            const groups = [];
            let currentGroup = null;
            
            messages.forEach(message => {
                // 시스템 메시지는 별도 그룹으로 처리
                if (message.type === 'SYSTEM') {
                    const systemGroup = {
                        type: 'SYSTEM',
                        messages: [message]
                    };
                    groups.push(systemGroup);
                    currentGroup = null; // 시스템 메시지 후에는 새 그룹 시작
                    return;
                }
                
                if (!currentGroup || currentGroup.userId !== message.userId || 
                    this.isTimeDifferenceSignificant(currentGroup.messages[currentGroup.messages.length - 1], message)) {
                    const isOwn = String(message.userId) === String(this.currentUser?.id);
                    currentGroup = {
                        userId: message.userId,
                        userName: message.userNickname || message.userName,
                        isOwn: isOwn,
                        messages: [message]
                    };
                    groups.push(currentGroup);
                } else {
                    currentGroup.messages.push(message);
                }
            });
            
            console.log('Grouped messages result:', groups);
            return groups;
        },
        
        // 시간 차이가 의미있는지 확인 (5분 이상)
        isTimeDifferenceSignificant(prevMessage, currentMessage) {
            const prevTime = this.parseTimestamp(prevMessage.timestamp);
            const currentTime = this.parseTimestamp(currentMessage.timestamp);
            if (!prevTime || !currentTime) {
                return false;
            }
            return (currentTime - prevTime) > 5 * 60 * 1000; // 5분
        },
        
        // 메시지 그룹 생성
        createMessageGroup(group) {
            console.log('Creating message group:', {
                type: group.type,
                userId: group.userId,
                userName: group.userName,
                isOwn: group.isOwn,
                messageCount: group.messages?.length
            });
            const groupEl = document.createElement('div');
            
            // 시스템 메시지 처리
            if (group.type === 'SYSTEM') {
                console.log('Creating SYSTEM message group');
                groupEl.className = 'message-group system';
                const message = group.messages[0];
                const systemMessageEl = this.createSystemMessage(message);
                groupEl.appendChild(systemMessageEl);
                return groupEl;
            }
            
            groupEl.className = 'message-group';
            
            // 다른 사용자의 메시지인 경우 사용자 이름 표시 (한 번만)
            if (!group.isOwn) {
                console.log('Adding sender name:', group.userName);
                const nameEl = document.createElement('div');
                nameEl.className = 'message-sender';
                nameEl.textContent = group.userName || 'Unknown User';
                groupEl.appendChild(nameEl);
                console.log('Added sender element with text:', nameEl.textContent);
            }
            
            group.messages.forEach((message, index) => {
                const messageEl = this.createMessageElement(message, group.isOwn, index === group.messages.length - 1);
                groupEl.appendChild(messageEl);
            });
            
            return groupEl;
        },
        
        // 메시지 요소 생성
        createMessageElement(message, isOwn, showMeta = true) {
            const messageEl = document.createElement('div');
            messageEl.className = `message ${isOwn ? 'own' : 'other'}`;
            messageEl.dataset.messageId = message.id;
            
            const bubbleEl = document.createElement('div');
            bubbleEl.className = 'message-bubble';
            
            const contentEl = document.createElement('p');
            contentEl.className = 'message-content';
            contentEl.textContent = message.content;
            bubbleEl.appendChild(contentEl);
            
            if (showMeta) {
                const metaEl = document.createElement('div');
                metaEl.className = 'message-meta';
                
                const timeEl = document.createElement('span');
                timeEl.className = 'message-time';
                timeEl.textContent = this.formatMessageTime(message.timestamp);
                metaEl.appendChild(timeEl);
                
                if (isOwn) {
                    const statusEl = document.createElement('div');
                    statusEl.className = `message-status status-${message.status || 'sent'}`;
                    
                    const statusIcon = document.createElement('div');
                    statusIcon.className = 'status-icon';
                    statusEl.appendChild(statusIcon);
                    
                    metaEl.appendChild(statusEl);
                }
                
                bubbleEl.appendChild(metaEl);
            }
            
            messageEl.appendChild(bubbleEl);
            return messageEl;
        },
        
        // 메시지 시간 포맷
        formatMessageTime(timestamp) {
            const date = this.parseTimestamp(timestamp);
            if (!date) {
                return ''; // Or some other placeholder for invalid time
            }
            const now = new Date();
            
            if (date.toDateString() === now.toDateString()) {
                // 오늘: 시:분
                return date.toLocaleTimeString('ko-KR', { 
                    hour: '2-digit', 
                    minute: '2-digit',
                    hour12: false
                });
            } else {
                // 다른 날: 월/일 시:분
                return date.toLocaleString('ko-KR', { 
                    month: 'numeric',
                    day: 'numeric',
                    hour: '2-digit', 
                    minute: '2-digit',
                    hour12: false
                });
            }
        },
        
        // 메시지 입력 처리
        handleMessageInput(event) {
            const input = event.target;
            const content = input.value.trim();
            const sendBtn = DOM.select('#sendBtn');
            const messageCounter = DOM.select('#messageCounter');
            const charCount = DOM.select('.char-count');
            
            // 버튼 활성화/비활성화
            sendBtn.disabled = !content;
            
            // 문자 수 카운터 업데이트
            charCount.textContent = input.value.length;
            
            // 경고 색상
            messageCounter.classList.remove('warning', 'danger');
            if (input.value.length > 800) {
                messageCounter.classList.add('warning');
            }
            if (input.value.length > 950) {
                messageCounter.classList.add('danger');
            }
            
            // 입력창 높이 자동 조절
            input.style.height = 'auto';
            input.style.height = Math.min(input.scrollHeight, 120) + 'px';
            
            // 타이핑 인디케이터
            this.handleTypingIndicator(content.length > 0);
        },
        
        // 키보드 입력 처리
        handleKeyDown(event) {
            // IME(입력기) 조합 중인 경우 Enter 키 이벤트를 처리하지 않습니다.
            // 이렇게 하면 한글 등 조합 언어 입력 시 마지막 글자가 중복 전송되는 문제를 방지할 수 있습니다.
            if (event.isComposing || event.keyCode === 229) {
                return;
            }
            
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                this.sendMessage();
            }
        },
        
        // 타이핑 인디케이터 처리
        handleTypingIndicator(isTyping) {
            if (!this.websocket || !this.isConnected) return;
            
            if (isTyping) {
                if (!this.typingTimer) {
                    // 타이핑 시작 신호 전송
                    this.websocket.startTyping();
                }
                
                // 타이핑 중단 타이머 재설정
                clearTimeout(this.typingTimer);
                this.typingTimer = setTimeout(() => {
                    this.websocket.stopTyping();
                    this.typingTimer = null;
                }, 3000);
            }
        },
        
        // 메시지 전송
        async sendMessage() {
            const messageInput = DOM.select('#messageInput');
            const content = messageInput.value.trim();
            
            if (!content || !this.websocket || !this.isConnected) {
                return;
            }
            
            const tempId = 'temp_' + Date.now();
            const message = {
                id: tempId,
                content: content,
                userId: this.currentUser.id,
                userName: this.currentUser.nickname,
                timestamp: new Date().toISOString(),
                status: 'sending'
            };
            
            // 임시 메시지 추가
            this.addMessage(message, true);
            
            // 입력창 초기화
            messageInput.value = '';
            messageInput.style.height = 'auto';
            DOM.select('#sendBtn').disabled = true;
            DOM.select('.char-count').textContent = '0';
            DOM.select('#messageCounter').classList.remove('warning', 'danger');
            
            // WebSocket으로 메시지 전송
            try {
                if (!this.websocket.sendChatMessage(content, { tempId })) {
                    throw new Error('메시지 전송 실패');
                }
            } catch (error) {
                console.error('메시지 전송 오류:', error);
                this.updateMessageStatus(tempId, 'failed');
                Toast.error('메시지 전송에 실패했습니다.');
            }
        },
        
        // 메시지 추가
        addMessage(message, isOwn = false) {
            this.messages.push(message);
            
            // 스크롤 위치 확인
            const wasScrollAtBottom = this.isScrollAtBottom();
            
            // 전체 메시지 다시 렌더링하여 그룹화 적용
            this.renderMessages();
            
            // 자신이 보낸 메시지이거나 스크롤이 맨 아래에 있으면 자동 스크롤
            if (isOwn || wasScrollAtBottom) {
                setTimeout(() => this.scrollToBottom(), 10); // 약간의 딜레이로 DOM 업데이트 후 스크롤
            } else {
                this.updateUnreadCount(1);
            }
        },
        
        // 메시지 상태 업데이트
        updateMessageStatus(messageId, status) {
            const messageEl = DOM.select(`[data-message-id="${messageId}"]`);
            if (messageEl) {
                const statusEl = messageEl.querySelector('.message-status');
                if (statusEl) {
                    statusEl.className = `message-status status-${status}`;
                }
            }
        },
        
        // 스크롤 처리
        handleScroll() {
            const scrollDownBtn = DOM.select('#scrollDownBtn');
            
            if (this.isScrollAtBottom()) {
                scrollDownBtn.style.display = 'none';
                this.unreadCount = 0;
                this.updateUnreadCount(0);
            } else {
                scrollDownBtn.style.display = 'flex';
            }
        },
        
        // 스크롤이 맨 아래인지 확인
        isScrollAtBottom() {
            const container = DOM.select('#messagesList');
            return container.scrollTop + container.clientHeight >= container.scrollHeight - 50;
        },
        
        // 맨 아래로 스크롤
        scrollToBottom(smooth = true) {
            const container = DOM.select('#messagesList');
            container.scrollTo({
                top: container.scrollHeight,
                behavior: smooth ? 'smooth' : 'auto'
            });
        },
        
        // 읽지 않은 메시지 수 업데이트
        updateUnreadCount(increment) {
            if (increment) {
                this.unreadCount += increment;
            } else {
                this.unreadCount = 0;
            }
            
            const unreadCountEl = DOM.select('#unreadCount');
            if (this.unreadCount > 0) {
                unreadCountEl.textContent = this.unreadCount > 99 ? '99+' : this.unreadCount;
                unreadCountEl.classList.add('show');
            } else {
                unreadCountEl.classList.remove('show');
            }
        },
        
        // WebSocket 연결
        connectWebSocket() {
            const { WebSocketUtil } = window.SimpleChatServer.utils;
            
            try {
                this.websocket = WebSocketUtil.getClient();
                
                // 현재 채팅방 ID를 WebSocket 클라이언트에 설정
                if (this.currentRoomId) {
                    this.websocket.setCurrentRoomId(this.currentRoomId);
                } else {
                    throw new Error('채팅방 ID가 설정되지 않았습니다.');
                }
                
                // WebSocket 이벤트 리스너 설정
                this.websocket.on('open', () => {
                    console.log('WebSocket 연결됨');
                    this.isConnected = true;
                    this.updateConnectionStatus('connected');
                    
                    // 연결 시 현재 참여자 수 다시 조회
                    this.refreshParticipantCount();
                });
                
                this.websocket.on('close', () => {
                    console.log('WebSocket 연결 해제됨');
                    this.isConnected = false;
                    this.updateConnectionStatus('disconnected');
                });
                
                this.websocket.on('error', (error) => {
                    console.error('WebSocket 오류:', error);
                    this.updateConnectionStatus('error');
                });
                
                this.websocket.on('stateChange', (state) => {
                    if (state.state === 'reconnecting') {
                        this.updateConnectionStatus('connecting');
                    } else if (state.state === 'connected') {
                        this.updateConnectionStatus('connected');
                        this.isConnected = true;
                    } else if (state.state === 'disconnected') {
                        this.updateConnectionStatus('disconnected');
                        this.isConnected = false;
                    }
                });
                
                // 메시지 타입별 핸들러 등록
                this.websocket.onMessage('CHAT', (data) => {
                    if (String(data.userId) !== String(this.currentUser.id)) {
                        // 다른 사용자로부터 받은 메시지
                        const parsedTimestamp = this.parseTimestamp(data.timestamp);
                        const message = {
                            id: data.messageId || Date.now(),
                            content: data.content,
                            userId: data.userId,
                            userName: data.userNickname || 'Unknown User',
                            timestamp: parsedTimestamp ? parsedTimestamp.toISOString() : new Date().toISOString(),
                            status: 'received'
                        };
                        this.addMessage(message, false);
                    } else {
                        // 내가 보낸 메시지의 확인
                        this.updateMessageStatus(data.tempId, 'sent');
                    }
                });
                
                // 레거시 지원을 위한 일반 메시지 핸들러
                this.websocket.onMessage('message', (data) => {
                    console.log('Received WebSocket message:', data);
                    if (data.type === 'CHAT') {
                        // CHAT 타입은 위에서 처리됨
                        return;
                    }
                    if (data.type === 'SYSTEM') {
                        console.log('Processing SYSTEM message:', data);
                        this.addMessage({
                            type: 'SYSTEM',
                            content: data.content,
                            timestamp: data.timestamp || new Date().toISOString(),
                            messageId: data.messageId || Date.now().toString()
                        }, false);
                        return;
                    }
                    if (String(data.userId) !== String(this.currentUser.id)) {
                        this.addMessage(data, false);
                    } else {
                        this.updateMessageStatus(data.tempId, 'sent');
                    }
                });
                
                this.websocket.onMessage('TYPING', (data) => {
                    if (String(data.userId) !== String(this.currentUser.id)) {
                        if (data.isTyping) {
                            this.showTypingIndicator(data.userNickname || data.userName);
                        } else {
                            this.hideTypingIndicator();
                        }
                    }
                });
                
                this.websocket.onMessage('user_joined', (data) => {
                    Toast.info(`${data.userName}님이 입장했습니다.`);
                    this.updateMemberCount(data.memberCount);
                });
                
                this.websocket.onMessage('user_left', (data) => {
                    Toast.info(`${data.userName}님이 퇴장했습니다.`);
                    this.updateMemberCount(data.memberCount);
                });
                
                // 연결 및 채팅방 입장
                this.websocket.connect(this.currentUser, this.currentRoomId);
                
            } catch (error) {
                console.error('WebSocket 연결 오류:', error);
                this.updateConnectionStatus('error');
            }
        },
        
        // WebSocket 메시지 처리 (더 이상 사용되지 않음 - 새 클라이언트에서 처리)
        handleWebSocketMessage(data) {
            // 레거시 메소드 - 새로운 WebSocket 클라이언트에서 이벤트 핸들러로 처리됨
            console.log('Legacy message handler called:', data.type);
        },
        
        // 연결 상태 업데이트
        updateConnectionStatus(status) {
            const statusEl = DOM.select('#connectionStatus');
            statusEl.classList.remove('connected', 'connecting', 'disconnected');
            statusEl.classList.add(status);
            
            switch (status) {
                case 'connected':
                    statusEl.textContent = '온라인';
                    break;
                case 'connecting':
                    statusEl.textContent = '연결 중...';
                    break;
                case 'disconnected':
                    statusEl.textContent = '연결 끊김';
                    break;
                case 'error':
                    statusEl.textContent = '연결 오류';
                    break;
            }
        },
        
        // 타이핑 인디케이터 표시
        showTypingIndicator(userName) {
            const typingIndicator = DOM.select('#typingIndicator');
            const typingText = DOM.select('#typingText');
            
            typingText.textContent = `${userName}님이 입력 중...`;
            typingIndicator.style.display = 'flex';
        },
        
        // 타이핑 인디케이터 숨김
        hideTypingIndicator() {
            const typingIndicator = DOM.select('#typingIndicator');
            typingIndicator.style.display = 'none';
        },
        
        // 멤버 수 업데이트
        updateMemberCount(count) {
            const memberCount = DOM.select('#memberCount');
            memberCount.textContent = `참여자 ${count}명`;
        },
        
        // 참여자 수 새로고침
        async refreshParticipantCount() {
            try {
                const response = await Http.get(`/api/rooms/${this.currentRoomId}/participants`);
                if (response.success) {
                    const participantCount = response.data.length;
                    this.updateMemberCount(participantCount);
                }
            } catch (error) {
                console.error('참여자 수 조회 오류:', error);
            }
        },
        
        // 파일 첨부 모달 표시
        showAttachmentModal() {
            const modal = DOM.select('#attachmentModal');
            modal.classList.add('show');
            document.body.style.overflow = 'hidden';
        },
        
        // 파일 첨부 모달 숨김
        hideAttachmentModal() {
            const modal = DOM.select('#attachmentModal');
            modal.classList.remove('show');
            document.body.style.overflow = '';
            
            // 파일 목록 초기화
            DOM.select('#fileList').innerHTML = '';
            DOM.select('#fileInput').value = '';
            DOM.select('#uploadFiles').disabled = true;
        },
        
        // 파일 선택 처리
        handleFileSelection(files) {
            if (!files || files.length === 0) return;
            
            const fileList = DOM.select('#fileList');
            const uploadBtn = DOM.select('#uploadFiles');
            
            fileList.innerHTML = '';
            
            Array.from(files).forEach((file, index) => {
                const fileItem = this.createFileItem(file, index);
                fileList.appendChild(fileItem);
            });
            
            uploadBtn.disabled = false;
        },
        
        // 파일 아이템 생성
        createFileItem(file, index) {
            const fileItem = document.createElement('div');
            fileItem.className = 'file-item';
            fileItem.dataset.index = index;
            
            const fileInfo = document.createElement('div');
            fileInfo.className = 'file-info';
            
            const fileIcon = document.createElement('span');
            fileIcon.className = 'file-icon';
            fileIcon.textContent = this.getFileIcon(file.type);
            
            const fileDetails = document.createElement('div');
            fileDetails.className = 'file-details';
            
            const fileName = document.createElement('div');
            fileName.className = 'file-name';
            fileName.textContent = file.name;
            
            const fileSize = document.createElement('div');
            fileSize.className = 'file-size';
            fileSize.textContent = this.formatFileSize(file.size);
            
            fileDetails.appendChild(fileName);
            fileDetails.appendChild(fileSize);
            
            const removeBtn = document.createElement('button');
            removeBtn.className = 'remove-file-btn';
            removeBtn.textContent = '×';
            removeBtn.title = '파일 제거';
            
            DOM.on(removeBtn, 'click', () => {
                fileItem.remove();
                
                // 파일 목록이 비어있으면 업로드 버튼 비활성화
                const remainingFiles = DOM.selectAll('#fileList .file-item');
                if (remainingFiles.length === 0) {
                    DOM.select('#uploadFiles').disabled = true;
                }
            });
            
            fileInfo.appendChild(fileIcon);
            fileInfo.appendChild(fileDetails);
            fileItem.appendChild(fileInfo);
            fileItem.appendChild(removeBtn);
            
            return fileItem;
        },
        
        // 파일 타입별 아이콘
        getFileIcon(mimeType) {
            if (mimeType.startsWith('image/')) return '🖼️';
            if (mimeType.startsWith('video/')) return '🎥';
            if (mimeType.startsWith('audio/')) return '🎵';
            if (mimeType.includes('pdf')) return '📄';
            if (mimeType.includes('document') || mimeType.includes('word')) return '📝';
            if (mimeType.includes('spreadsheet') || mimeType.includes('excel')) return '📊';
            return '📁';
        },
        
        // 파일 크기 포맷
        formatFileSize(bytes) {
            if (bytes === 0) return '0 Bytes';
            
            const k = 1024;
            const sizes = ['Bytes', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        },
        
        // 참여자 사이드바 토글
        toggleMembersSidebar() {
            const sidebar = DOM.select('#membersSidebar');
            const overlay = DOM.select('#overlay');
            
            if (sidebar.classList.contains('open')) {
                this.hideMembersSidebar();
            } else {
                sidebar.classList.add('open');
                overlay.classList.add('show');
                document.body.style.overflow = 'hidden';
                
                // 참여자 목록 로드
                this.loadMembers();
            }
        },
        
        // 참여자 사이드바 숨김
        hideMembersSidebar() {
            const sidebar = DOM.select('#membersSidebar');
            const overlay = DOM.select('#overlay');
            
            sidebar.classList.remove('open');
            overlay.classList.remove('show');
            document.body.style.overflow = '';
        },
        
        // 참여자 목록 로드
        async loadMembers() {
            try {
                const response = await Http.get(`/api/rooms/${this.currentRoomId}/participants`);
                if (response.success) {
                    this.renderMembers(response.data);
                }
            } catch (error) {
                console.error('참여자 목록 로드 오류:', error);
            }
        },
        
        // 참여자 목록 렌더링
        renderMembers(members) {
            const membersList = DOM.select('#membersList');
            membersList.innerHTML = '';
            
            members.forEach(member => {
                const memberItem = this.createMemberItem(member);
                membersList.appendChild(memberItem);
            });
        },
        
        // 참여자 아이템 생성
        createMemberItem(member) {
            const memberItem = document.createElement('div');
            memberItem.className = 'member-item';
            
            const memberAvatar = document.createElement('div');
            memberAvatar.className = 'member-avatar';
            memberAvatar.textContent = (member.nickname || 'U').charAt(0).toUpperCase();
            
            const memberInfo = document.createElement('div');
            memberInfo.className = 'member-info';
            
            const memberName = document.createElement('div');
            memberName.className = 'member-name';
            memberName.textContent = member.nickname || 'Unknown User';
            
            const memberStatus = document.createElement('div');
            // Use the new isOnline field from the DTO
            memberStatus.className = `member-status ${member.isOnline ? 'online' : 'offline'}`;
            memberStatus.textContent = member.isOnline ? '온라인' : '오프라인';
            
            memberInfo.appendChild(memberName);
            memberInfo.appendChild(memberStatus);
            
            memberItem.appendChild(memberAvatar);
            memberItem.appendChild(memberInfo);
            
            return memberItem;
        },
        
        // 시스템 메시지 요소 생성
        createSystemMessage(message) {
            console.log('Creating system message element:', message);
            const systemMessageEl = document.createElement('div');
            systemMessageEl.className = 'system-message';
            
            const contentEl = document.createElement('div');
            contentEl.className = 'system-message-content';
            contentEl.textContent = message.content;
            
            systemMessageEl.appendChild(contentEl);
            
            return systemMessageEl;
        },
        
        // 정리
        cleanup() {
            if (this.websocket) {
                this.websocket.disconnect();
            }
            
            if (this.typingTimer) {
                clearTimeout(this.typingTimer);
            }
        }
    };
    
    // 페이지 언로드 시 정리
    window.addEventListener('beforeunload', () => {
        ChatManager.cleanup();
    });
    
    // 채팅 매니저 초기화
    ChatManager.init();
});

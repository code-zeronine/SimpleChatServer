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
        currentPage: 0, // Add this
        isLoadingMessages: false, // Add this
        userMappings: new Map(), // userId -> userNickname 매핑
        
        // 초기화
        init() {
            this.currentUser = Storage.getUser();
            console.log("ChatManager.init: currentUser =", this.currentUser);
            this.currentRoomId = this.getRoomIdFromURL();
            console.log("ChatManager.init: currentRoomId =", this.currentRoomId);
            
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

        // 타임스탬프를 Date 객체로 변환 (Long 타임스탬프 최적화)
        parseTimestamp(timestamp) {
            if (timestamp === null || timestamp === undefined) {
                console.debug('parseTimestamp: No timestamp provided');
                return null;
            }
            
            try {
                // Date object (이미 파싱됨)
                if (timestamp instanceof Date) {
                    return isNaN(timestamp.getTime()) ? null : timestamp;
                }
                
                // Number type (통일된 Long 타임스탬프 형태 - 밀리초)
                if (typeof timestamp === 'number') {
                    if (timestamp <= 0) {
                        console.warn('parseTimestamp: Invalid numeric timestamp:', timestamp);
                        return null;
                    }
                    
                    // Unix timestamp in seconds (10자리 미만)
                    if (timestamp < 10000000000) {
                        return new Date(timestamp * 1000);
                    }
                    // Unix timestamp in milliseconds (기본)
                    return new Date(timestamp);
                }
                
                // String type (레거시 지원용)
                if (typeof timestamp === 'string') {
                    if (timestamp.trim() === '') {
                        console.debug('parseTimestamp: Empty string timestamp');
                        return null;
                    }
                    
                    // ISO 문자열 또는 기타 날짜 문자열 파싱
                    const date = new Date(timestamp);
                    if (isNaN(date.getTime())) {
                        console.warn('parseTimestamp: Invalid string date:', timestamp);
                        return null;
                    }
                    return date;
                }
                
                console.warn('parseTimestamp: Unexpected timestamp format (should be Long number):', timestamp, 'Type:', typeof timestamp);
                return null;
                
            } catch (error) {
                console.error('parseTimestamp: Error parsing timestamp:', error, 'timestamp:', timestamp);
                return null;
            }
        },
        
        // 이벤트 리스너 설정 (접근성 및 키보드 내비게이션 개선)
        setupEventListeners() {
            // 뒤로가기 버튼
            const backBtn = DOM.select('#backBtn');
            DOM.on(backBtn, 'click', () => {
                window.location.href = '/rooms';
            });
            
            // 메시지 입력창 및 폼
            const messageInput = DOM.select('#messageInput');
            const messageForm = DOM.select('#messageForm');
            
            DOM.on(messageInput, 'input', this.handleMessageInput.bind(this));
            DOM.on(messageInput, 'keydown', this.handleKeyDown.bind(this));
            DOM.on(messageInput, 'paste', this.handlePaste.bind(this));
            
            // 폼 제출 이벤트 (Enter 키 처리)
            DOM.on(messageForm, 'submit', (e) => {
                e.preventDefault();
                this.sendMessage();
            });
            
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
            
            // 키보드 단축키 설정
            this.setupKeyboardShortcuts();
            
            // 모달 이벤트들
            this.setupModalEvents();
            
            // 파일 드래그 앤 드롭
            this.setupFileDropEvents();
        },
        
        // 키보드 단축키 설정
        setupKeyboardShortcuts() {
            DOM.on(document, 'keydown', (e) => {
                // Ctrl/Cmd + Enter: 메시지 전송
                if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                    e.preventDefault();
                    this.sendMessage();
                }
                
                // Alt + M: 참여자 목록 토글
                if (e.altKey && e.key === 'm') {
                    e.preventDefault();
                    this.toggleMembersSidebar();
                }
                
                // Alt + F: 파일 첨부 모달
                if (e.altKey && e.key === 'f') {
                    e.preventDefault();
                    this.showAttachmentModal();
                }
                
                // Page Down: 스크롤 다운
                if (e.key === 'PageDown' && e.target === document.body) {
                    e.preventDefault();
                    this.scrollToBottom();
                }
            });
        },
        
        // 붙여넣기 처리 (파일 포함)
        handlePaste(event) {
            const items = event.clipboardData?.items;
            if (!items) return;
            
            const files = [];
            for (const item of items) {
                if (item.kind === 'file') {
                    const file = item.getAsFile();
                    if (file) {
                        files.push(file);
                    }
                }
            }
            
            if (files.length > 0) {
                event.preventDefault();
                this.showAttachmentModal();
                setTimeout(() => {
                    this.handleFileSelection(files);
                }, 300);
            }
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
            console.log("loadChatRoom: Loading room info for roomId =", this.currentRoomId);
            try {
                const response = await Http.get(`/api/rooms/${this.currentRoomId}`);
                console.log("loadChatRoom: Room info response =", response);
                if (response.success) {
                    this.updateRoomInfo(response.data);
                    await this.loadInitialData();
                }
                else {
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
            
            // 온라인 참여자 수만 표시
            let onlineParticipantCount = 0;
            if (roomData.participants && Array.isArray(roomData.participants)) {
                onlineParticipantCount = roomData.participants.filter(participant => participant.isOnline).length;
            }
            
            roomName.textContent = room.name || '채팅방';
            memberCount.textContent = `온라인 ${onlineParticipantCount}명`;
            
            // 페이지 제목 업데이트
            document.title = `${room.name} - SimpleChatServer`;
        },
        
        // 초기 데이터 로드 (참여자 정보와 메시지)
        async loadInitialData() {
            try {
                // 참여자 정보를 먼저 로드하여 사용자 매핑 생성
                const participantsResponse = await Http.get(`/api/rooms/${this.currentRoomId}/participants`);
                if (participantsResponse.success) {
                    this.updateUserMappings(participantsResponse.data);
                }
                
                // 이후 메시지 로드
                await this.loadMessages();
            } catch (error) {
                console.error('초기 데이터 로드 오류:', error);
                // 참여자 정보 로드 실패 시에도 메시지는 로드
                await this.loadMessages();
            }
        },
        
        // 메시지 로드
        async loadMessages(loadMore = false) {
            console.log(`loadMessages: Called with loadMore=${loadMore}, currentPage=${this.currentPage}, isLoadingMessages=${this.isLoadingMessages}`);
            if (this.isLoadingMessages) {
                console.log("loadMessages: Already loading, returning.");
                return;
            }
            this.isLoadingMessages = true;

            try {
                const loadingEl = DOM.select('#loadingMessages');
                loadingEl.style.display = 'block';

                const pageToLoad = loadMore ? this.currentPage + 1 : 0;
                const limit = 50;
                console.log(`loadMessages: Fetching messages for page=${pageToLoad}, size=${limit}`);

                const response = await Http.get(`/api/messages/room/${this.currentRoomId}?page=${pageToLoad}&size=${limit}`);
                console.log("loadMessages: RAW API response =", response); // This log is fine here

                if (response.success && response.data) { // This 'if' needs to wrap the successful processing
                    console.log("loadMessages: API response =", response); // This log is fine here
                    const newMessages = Array.isArray(response.data) ? response.data : (response.data.content || []);
                    console.log(`loadMessages: Fetched ${newMessages.length} new messages.`);
                    
                    // 각 메시지의 타임스탬프 정보 로깅 (상세)
                    newMessages.forEach((msg, idx) => {
                        console.log(`loadMessages: Message ${idx} raw timestamp data:`, {
                            id: msg.id,
                            timestamp: msg.timestamp,
                            timestampType: typeof msg.timestamp,
                            timestampValue: msg.timestamp,
                            parsed: this.parseTimestamp(msg.timestamp),
                            formatted: this.formatMessageTime(msg.timestamp)
                        });
                    });
                    
                    if (newMessages.length > 0) { // Only process if there are new messages
                        // 메시지 타임스탬프 표준화 (Long 타임스탬프 처리)
                        const normalizedMessages = newMessages.map(msg => {
                            let timestampValue = msg.timestamp;
                            
                            // 모든 timestamp를 Long (Unix timestamp in milliseconds)으로 정규화
                            if (timestampValue !== null && timestampValue !== undefined) {
                                if (typeof timestampValue === 'number') {
                                    // 이미 숫자인 경우 검증만 (밀리초 단위 확인)
                                    if (timestampValue > 0) {
                                        // Unix timestamp가 초 단위인지 밀리초 단위인지 확인
                                        if (timestampValue < 10000000000) {
                                            // 초 단위라면 밀리초로 변환
                                            timestampValue = timestampValue * 1000;
                                        }
                                        // 유효한 숫자 타임스탬프 그대로 사용
                                    } else {
                                        console.warn('Invalid numeric timestamp:', timestampValue, 'using current time');
                                        timestampValue = Date.now();
                                    }
                                } else if (typeof timestampValue === 'string') {
                                    // 문자열인 경우 Date로 파싱 후 밀리초로 변환
                                    try {
                                        const parsedDate = new Date(timestampValue);
                                        if (isNaN(parsedDate.getTime())) {
                                            throw new Error('Invalid date string');
                                        }
                                        timestampValue = parsedDate.getTime();
                                    } catch (e) {
                                        console.warn('Invalid timestamp string:', timestampValue, 'using current time');
                                        timestampValue = Date.now();
                                    }
                                } else {
                                    // 기타 형식인 경우 현재 시간 사용
                                    console.warn('Unexpected timestamp format:', timestampValue);
                                    timestampValue = Date.now();
                                }
                            } else {
                                // timestamp가 없는 경우 현재 시간 사용
                                timestampValue = Date.now();
                            }
                            
                            return {
                                ...msg,
                                timestamp: timestampValue, // 항상 Long (밀리초) 형태로 저장
                                // 사용자 이름 처리
                                userName: msg.userName || msg.userNickname || msg.senderName || 'Unknown User'
                            };
                        });
                        
                        console.log('loadMessages: Normalized messages with timestamps:', normalizedMessages.map(msg => ({
                            id: msg.id,
                            timestamp: msg.timestamp,
                            userName: msg.userName
                        })));
                        
                        // 메시지에서 사용자 ID들을 추출하고 매핑되지 않은 사용자 정보 로드
                        await this.loadMissingUserInfo(normalizedMessages);
                        
                        if (loadMore) {
                            this.messages = [...normalizedMessages.reverse(), ...this.messages];
                            this.prependMessages(normalizedMessages);
                            this.currentPage = pageToLoad;
                            console.log(`loadMessages: Prepended messages. Total messages in array: ${this.messages.length}`);
                        } else {
                            this.messages = normalizedMessages.reverse();
                            this.renderMessages();
                            setTimeout(() => this.scrollToBottom(false), 100);
                            console.log(`loadMessages: Initial load. Total messages in array: ${this.messages.length}`);
                        }
                    } else {
                        console.log("loadMessages: No new messages received from API.");
                    }

                    if (newMessages.length < limit) {
                        console.log("loadMessages: Reached end of older messages.");
                    }

                } else { // This 'else' is correctly paired with the 'if (response.success && response.data)'
                    console.warn('loadMessages: 메시지 로드 실패:', response);
                }
            } catch (error) {
                console.error('loadMessages: 메시지 로드 오류:', error);
                Toast.error('메시지를 불러올 수 없습니다.');
            } finally {
                const loadingEl = DOM.select('#loadingMessages');
                loadingEl.style.display = 'none';
                this.isLoadingMessages = false;
                console.log("loadMessages: Loading finished.");
            }
        },

        // 메시지 렌더링 (초기 로드 및 전체 재렌더링용)
        renderMessages() {
            console.log(`renderMessages: Called. Messages to render: ${this.messages.length}`);
            const messagesList = DOM.select('#messagesList');
            
            // 모든 메시지 관련 요소 제거 (더 확실한 정리)
            const messagesToRemove = messagesList.querySelectorAll('.message-group, .message, .message-sender');
            messagesToRemove.forEach(element => element.remove());
            
            console.log('renderMessages: Cleared', messagesToRemove.length, 'message elements from DOM');
            
            if (this.messages.length === 0) {
                console.log("renderMessages: No messages to render.");
                return;
            }
            
            // 메시지 그룹화 및 렌더링
            const groupedMessages = this.groupMessages(this.messages);
            console.log(`renderMessages: Grouped messages into ${groupedMessages.length} groups.`);
            
            groupedMessages.forEach((group, index) => {
                const groupEl = this.createMessageGroup(group);
                messagesList.appendChild(groupEl);
            });
            console.log("renderMessages: Messages appended to DOM.");
        },

        // 메시지 앞에 추가 (무한 스크롤용)
        prependMessages(newMessages) {
            const messagesList = DOM.select('#messagesList');
            const oldScrollHeight = messagesList.scrollHeight;
            const oldScrollTop = messagesList.scrollTop;

            // Group and create elements for new messages
            const groupedNewMessages = this.groupMessages(newMessages);
            const fragment = document.createDocumentFragment();
            groupedNewMessages.forEach(group => {
                const groupEl = this.createMessageGroup(group);
                fragment.appendChild(groupEl);
            });

            messagesList.prepend(fragment);

            // Adjust scroll position to maintain view
            const newScrollHeight = messagesList.scrollHeight;
            messagesList.scrollTop = oldScrollTop + (newScrollHeight - oldScrollTop);
        },
        
        // 메시지 그룹화 (같은 사용자의 연속 메시지)
        groupMessages(messages) {
            console.log(`groupMessages: Grouping ${messages.length} messages.`);
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
                    const resolvedUserName = this.getUserNickname(message.userId) || message.userNickname || message.userName || 'Unknown User';
                    currentGroup = {
                        userId: message.userId,
                        userName: resolvedUserName,
                        isOwn: isOwn,
                        messages: [message]
                    };
                    groups.push(currentGroup);
                } else {
                    currentGroup.messages.push(message);
                }
            });
            
            console.log('groupMessages: Grouped messages result:', groups);
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
            console.log('createMessageGroup: Creating message group:', {
                type: group.type,
                userId: group.userId,
                userName: group.userName,
                isOwn: group.isOwn,
                messageCount: group.messages?.length
            });
            const groupEl = document.createElement('div');
            
            // 시스템 메시지 처리
            if (group.type === 'SYSTEM') {
                console.log('createMessageGroup: Creating SYSTEM message group');
                groupEl.className = 'message-group system';
                const message = group.messages[0];
                const systemMessageEl = this.createSystemMessage(message);
                groupEl.appendChild(systemMessageEl);
                return groupEl;
            }
            
            groupEl.className = 'message-group';
            
            // 다른 사용자의 메시지인 경우 사용자 이름 표시 (한 번만)
            if (!group.isOwn) {
                console.log('createMessageGroup: Adding sender name:', group.userName);
                const nameEl = document.createElement('div');
                nameEl.className = 'message-sender';
                nameEl.textContent = group.userName || 'Unknown User';
                groupEl.appendChild(nameEl);
                console.log('createMessageGroup: Added sender element with text:', nameEl.textContent);
            }
            
            group.messages.forEach((message, index) => {
                const messageEl = this.createMessageElement(message, group.isOwn, index === group.messages.length - 1, group.userName);
                groupEl.appendChild(messageEl);
            });
            
            return groupEl;
        },
        
        // 메시지 요소 생성 (index 페이지 스타일과 동일)
        createMessageElement(message, isOwn, showMeta = true, userName = null) {
            console.log('createMessageElement: Creating message element:', { 
                messageId: message.id,
                timestamp: message.timestamp,
                content: message.content,
                userName: userName,
                isOwn: isOwn,
                showMeta: showMeta
            });
            const messageEl = document.createElement('div');
            messageEl.className = `message ${isOwn ? 'mine' : ''}`;
            messageEl.dataset.messageId = message.id;
            
            // 다른 사용자의 메시지인 경우만 작성자 표시
            if (!isOwn && userName) {
                const authorEl = document.createElement('span');
                authorEl.className = 'message-author';
                authorEl.textContent = userName;
                messageEl.appendChild(authorEl);
            }
            
            const contentEl = document.createElement('span');
            contentEl.className = 'message-content';
            contentEl.textContent = message.content;
            messageEl.appendChild(contentEl);
            
            // 메시지 시간 표시
            if (message.timestamp && showMeta) {
                const timeEl = document.createElement('span');
                timeEl.className = 'message-time';
                const formattedTime = this.formatMessageTime(message.timestamp);
                if (formattedTime) {
                    timeEl.textContent = formattedTime;
                    messageEl.appendChild(timeEl);
                } else {
                    console.warn('createMessageElement: Empty formatted time for timestamp:', message.timestamp);
                }
            }
            
            return messageEl;
        },
        
        // 메시지 시간 포맷
        formatMessageTime(timestamp) {
            const date = this.parseTimestamp(timestamp);
            
            if (!date || isNaN(date.getTime())) {
                console.warn('formatMessageTime: Invalid timestamp:', timestamp, 'parsed as:', date);
                // 대안으로 현재 시간을 사용
                return new Date().toLocaleTimeString('ko-KR', { 
                    hour: '2-digit', 
                    minute: '2-digit',
                    hour12: false
                });
            }
            
            const now = new Date();
            
            try {
                if (date.toDateString() === now.toDateString()) {
                    // 오늘: 시:분
                    const formatted = date.toLocaleTimeString('ko-KR', { 
                        hour: '2-digit', 
                        minute: '2-digit',
                        hour12: false
                    });
                    return formatted || '시간 불명';
                } else {
                    // 다른 날: 월/일 시:분
                    const formatted = date.toLocaleString('ko-KR', { 
                        month: 'numeric',
                        day: 'numeric',
                        hour: '2-digit', 
                        minute: '2-digit',
                        hour12: false
                    });
                    return formatted || `${date.getMonth()+1}/${date.getDate()} ${String(date.getHours()).padStart(2,'0')}:${String(date.getMinutes()).padStart(2,'0')}`;
                }
            } catch (error) {
                console.error('formatMessageTime: Formatting error:', error, 'timestamp:', timestamp, 'date:', date);
                // 백업 포맷팅
                return `${String(date.getHours()).padStart(2,'0')}:${String(date.getMinutes()).padStart(2,'0')}`;
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
                timestamp: Date.now(), // Unix timestamp in milliseconds
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
            // 내용이 없는 메시지는 추가하지 않음
            if (!message || !message.content || message.content.trim() === '') {
                return;
            }

            // Add to the internal messages array
            this.messages.push(message);

            // Check scroll position before adding to DOM
            const wasScrollAtBottom = this.isScrollAtBottom();

            // Create and append the new message element to the DOM
            const messagesList = DOM.select('#messagesList');
            
            // Create a new group for this single message
            const groupedNewMessage = this.groupMessages([message]);
            if (groupedNewMessage.length > 0) {
                const groupEl = this.createMessageGroup(groupedNewMessage[0]);
                messagesList.appendChild(groupEl);
            }

            // Auto-scroll if it was at the bottom or if it's an own message
            if (isOwn || wasScrollAtBottom) {
                setTimeout(() => this.scrollToBottom(), 10);
            }
            else {
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
            const messagesList = DOM.select('#messagesList'); // Get messagesList here

            // Logic for scrollDownBtn (existing)
            if (this.isScrollAtBottom()) {
                scrollDownBtn.style.display = 'none';
                this.unreadCount = 0;
                this.updateUnreadCount(0);
            } else {
                scrollDownBtn.style.display = 'flex';
            }

            // New logic for loading older messages
            // Check if scrolled to top and not currently loading
            if (messagesList.scrollTop < 50 && !this.isLoadingMessages && this.messages.length > 0) { // Threshold 50px from top
                this.loadMessages(true); // Load more older messages
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
                        // 다른 사용자로부터 메시지 수신 시 타이핑 인디케이터 즉시 숨김
                        this.hideTypingIndicator();

                        // 다른 사용자로부터 받은 메시지
                        const parsedTimestamp = this.parseTimestamp(data.timestamp);
                        // 사용자 매핑 업데이트
                        if (data.userNickname) {
                            this.userMappings.set(String(data.userId), data.userNickname);
                        }
                        
                        const message = {
                            id: data.messageId || Date.now(),
                            content: data.content,
                            userId: data.userId,
                            userName: data.userNickname || this.getUserNickname(data.userId) || 'Unknown User',
                            timestamp: data.timestamp || Date.now(), // Long timestamp
                            status: 'received'
                        };
                        this.addMessage(message, false);
                    } else {
                        // 내가 보낸 메시지의 확인
                        this.updateMessageStatus(data.tempId, 'sent');
                    }
                });
                
                // 레거시 지원을 위한 일반 메시지 핸들러
                this.websocket.on('message', (data) => {
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
                            timestamp: data.timestamp || Date.now(), // Long timestamp
                            messageId: data.messageId || Date.now().toString()
                        }, false);

                        // 자신의 입장/퇴장 메시지인 경우 토스트를 띄우지 않음
                        if (data.userId && String(data.userId) !== String(this.currentUser.id)) {
                            Toast.info(data.content);
                        }

                        // 참여자 수 새로고침
                        this.refreshParticipantCount();
                        return;
                    }
                    if (String(data.userId) !== String(this.currentUser.id)) {
                        this.addMessage(data, false);
                    }
                    else {
                        this.updateMessageStatus(data.tempId, 'sent');
                    }
                });
                
                this.websocket.onMessage('TYPING', (data) => {
                    if (String(data.userId) !== String(this.currentUser.id)) {
                        if (data.isTyping) {
                            this.showTypingIndicator(data.userNickname || data.userName);
                        }
                        else {
                            this.hideTypingIndicator();
                        }
                    }
                });

                this.websocket.onMessage('JOIN', (data) => {
                    Toast.info(`${data.userName}님이 입장했습니다.`);
                    // 참여자 정보를 새로고침하여 온라인 사용자 수 업데이트
                    this.refreshParticipantCount();
                });

                this.websocket.onMessage('LEAVE', (data) => {
                    Toast.info(`${data.userName}님이 퇴장했습니다.`);
                    // 참여자 정보를 새로고침하여 온라인 사용자 수 업데이트
                    this.refreshParticipantCount();
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
        
        // 연결 상태 업데이트 (시각적 피드백 강화)
        updateConnectionStatus(status) {
            const statusEl = DOM.select('#connectionStatus');
            const inputWrapper = DOM.select('.input-wrapper');
            const sendBtn = DOM.select('#sendBtn');
            
            // 기존 상태 클래스 제거
            statusEl.classList.remove('connected', 'connecting', 'disconnected', 'error');
            inputWrapper.classList.remove('connection-error', 'connection-warning');
            
            statusEl.classList.add(status);
            
            switch (status) {
                case 'connected':
                    statusEl.textContent = '온라인';
                    statusEl.setAttribute('aria-label', '온라인 상태');
                    break;
                case 'connecting':
                    statusEl.textContent = '연결 중...';
                    statusEl.setAttribute('aria-label', '연결 시도 중');
                    inputWrapper.classList.add('connection-warning');
                    break;
                case 'disconnected':
                    statusEl.textContent = '연결 끊김';
                    statusEl.setAttribute('aria-label', '연결 끊김 상태');
                    inputWrapper.classList.add('connection-error');
                    sendBtn.disabled = true;
                    break;
                case 'error':
                    statusEl.textContent = '연결 오류';
                    statusEl.setAttribute('aria-label', '연결 오류 상태');
                    inputWrapper.classList.add('connection-error');
                    sendBtn.disabled = true;
                    Toast.error('서버와의 연결에 문제가 발생했습니다.');
                    break;
            }
        },
        
        // 타이핑 인디케이터 처리
        showTypingIndicator(userName) {
            const messagesList = DOM.select('#messagesList');
            let typingIndicator = DOM.select('#typingIndicator');

            if (!typingIndicator) {
                typingIndicator = DOM.create('div', {
                    id: 'typingIndicator',
                    className: 'message typing-indicator',
                }, `
                    <span class="message-author">${userName}</span>
                    <span class="message-content">
                        <span class="typing-dots">
                            <span></span><span></span><span></span>
                        </span>
                    </span>
                `);
                messagesList.appendChild(typingIndicator);
            } else {
                typingIndicator.querySelector('.message-author').textContent = userName;
            }

            typingIndicator.classList.add('show');
            this.scrollToBottom();
        },

        // 타이핑 인디케이터 숨김
        hideTypingIndicator() {
            const typingIndicator = DOM.select('#typingIndicator');
            if (typingIndicator) {
                typingIndicator.classList.remove('show');
                // Optionally remove the element after transition
                setTimeout(() => {
                    if (typingIndicator && !typingIndicator.classList.contains('show')) {
                         typingIndicator.remove();
                    }
                }, 500); // Should match CSS transition time
            }
        },
        
        // 멤버 수 업데이트 (온라인 사용자 수로 변경)
        updateMemberCount(count) {
            const memberCount = DOM.select('#memberCount');
            memberCount.textContent = `온라인 ${count}명`;
        },
        
        // 참여자 수 새로고침 (온라인 사용자만 카운트)
        async refreshParticipantCount() {
            try {
                const response = await Http.get(`/api/rooms/${this.currentRoomId}/participants`);
                if (response.success) {
                    // 온라인인 참여자만 카운트
                    const onlineParticipantCount = response.data.filter(participant => participant.isOnline).length;
                    this.updateMemberCount(onlineParticipantCount);
                    
                    console.log(`Total participants: ${response.data.length}, Online: ${onlineParticipantCount}`);
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
            removeBtn.innerHTML = '<i class="fas fa-times"></i>';
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
            if (mimeType.startsWith('image/')) return '<i class="fas fa-file-image"></i>';
            if (mimeType.startsWith('video/')) return '<i class="fas fa-file-video"></i>';
            if (mimeType.startsWith('audio/')) return '<i class="fas fa-file-audio"></i>';
            if (mimeType.includes('pdf')) return '<i class="fas fa-file-pdf"></i>';
            if (mimeType.includes('document') || mimeType.includes('word')) return '<i class="fas fa-file-word"></i>';
            if (mimeType.includes('spreadsheet') || mimeType.includes('excel')) return '<i class="fas fa-file-excel"></i>';
            return '<i class="fas fa-file"></i>';
        },
        
        // 파일 크기 포맷
        formatFileSize(bytes) {
            if (bytes === 0) return '0 Bytes';
            
            const k = 1024;
            const sizes = ['Bytes', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        },
        
        // 참여자 사이드바 토글 (접근성 및 UX 개선)
        toggleMembersSidebar() {
            const sidebar = DOM.select('#membersSidebar');
            const overlay = DOM.select('#overlay');
            const memberListBtn = DOM.select('#memberListBtn');
            
            if (sidebar.classList.contains('show')) {
                this.hideMembersSidebar();
            }
            else {
                sidebar.classList.add('show');
                sidebar.setAttribute('aria-hidden', 'false');
                overlay.classList.add('show');
                document.body.style.overflow = 'hidden';
                
                // 버튼 상태 업데이트
                memberListBtn.setAttribute('aria-expanded', 'true');
                
                // 참여자 목록 로드
                this.loadMembers();
                
                // 포커스 설정
                setTimeout(() => {
                    const firstFocusable = sidebar.querySelector('button, [tabindex]:not([tabindex="-1"])');
                    if (firstFocusable) {
                        firstFocusable.focus();
                    }
                }, 300);
            }
        },
        
        // 참여자 사이드바 숨김 (접근성 개선)
        hideMembersSidebar() {
            const sidebar = DOM.select('#membersSidebar');
            const overlay = DOM.select('#overlay');
            const memberListBtn = DOM.select('#memberListBtn');
            
            sidebar.classList.remove('show');
            sidebar.setAttribute('aria-hidden', 'true');
            overlay.classList.remove('show');
            document.body.style.overflow = '';
            
            // 버튼 상태 업데이트
            memberListBtn.setAttribute('aria-expanded', 'false');
            
            // 포커스 복원
            setTimeout(() => {
                if (memberListBtn) {
                    memberListBtn.focus();
                }
            }, 100);
        },
        
        // 참여자 목록 로드
        async loadMembers() {
            try {
                const response = await Http.get(`/api/rooms/${this.currentRoomId}/participants`);
                if (response.success) {
                    this.updateUserMappings(response.data);
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
        
        // 사용자 닉네임 조회 헬퍼
        getUserNickname(userId) {
            const nickname = this.userMappings.get(String(userId));
            return nickname || null;
        },
        
        // 매핑되지 않은 사용자 정보 로드
        async loadMissingUserInfo(messages) {
            const missingUserIds = new Set();
            
            messages.forEach(message => {
                const userId = String(message.userId);
                if (!this.userMappings.has(userId)) {
                    missingUserIds.add(userId);
                }
            });
            
            if (missingUserIds.size > 0) {
                console.log('loadMissingUserInfo: Loading info for users:', Array.from(missingUserIds));
                try {
                    // 참여자 목록에서 사용자 정보 가져오기
                    const participantsResponse = await Http.get(`/api/rooms/${this.currentRoomId}/participants`);
                    if (participantsResponse.success && participantsResponse.data) {
                        participantsResponse.data.forEach(participant => {
                            const userId = String(participant.userId || participant.id || participant.user_id);
                            if (missingUserIds.has(userId)) {
                                console.log(`loadMissingUserInfo: Found user ${userId}: ${participant.nickname}`);
                                this.userMappings.set(userId, participant.nickname);
                                missingUserIds.delete(userId);
                            }
                        });
                    }
                    
                    // 여전히 찾지 못한 사용자들을 Unknown User로 설정
                    missingUserIds.forEach(userId => {
                        console.warn(`loadMissingUserInfo: Could not find user ${userId}, setting as Unknown User`);
                        this.userMappings.set(userId, 'Unknown User');
                    });
                } catch (error) {
                    console.error('loadMissingUserInfo: Error loading user info:', error);
                    // 오류 발생 시 모든 누락된 사용자를 Unknown User로 설정
                    missingUserIds.forEach(userId => {
                        this.userMappings.set(userId, 'Unknown User');
                    });
                }
            }
        },
        
        // 사용자 매핑 업데이트
        updateUserMappings(members) {
            if (Array.isArray(members)) {
                console.log('updateUserMappings: Mapping', members.length, 'members');
                members.forEach(member => {
                    const userId = member.userId || member.id || member.user_id;
                    const nickname = member.nickname || member.userName || member.user_name;
                    if (userId && nickname) {
                        console.log(`updateUserMappings: ${userId} -> ${nickname}`);
                        this.userMappings.set(String(userId), nickname);
                    }
                });
                console.log('updateUserMappings: Total mappings:', this.userMappings.size);
            }
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
    
    // 채팅 매니저 초기화
    ChatManager.init();
});
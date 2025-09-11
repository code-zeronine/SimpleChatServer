/**
 * SimpleChatServer - Rooms Page JavaScript
 * 
 * 채팅방 목록 페이지의 기능을 담당합니다.
 */

document.addEventListener('DOMContentLoaded', function() {
    const { DOM, Http, Storage, Auth, Toast, Utils } = window.SimpleChatServer.utils;
    
    // 인증 확인
    if (!Auth.isAuthenticated()) {
        window.location.href = '/login?auth=required';
        return;
    }
    
    // 페이지 초기화
    initializePage();
    setupEventListeners();
    loadChatRooms('all');
});

let currentRooms = [];
let filteredRooms = [];
let currentFilter = 'all';
let currentSort = 'recent';

/**
 * 페이지 초기화
 */
function initializePage() {
    const { Storage, DOM } = window.SimpleChatServer.utils;
    
    // 사용자 정보 표시
    const user = Storage.getUser();
    if (user) {
        const userNameEl = DOM.select('#userName');
        if (userNameEl) {
            userNameEl.textContent = user.name || '사용자';
        }
    }
}

/**
 * 이벤트 리스너 설정
 */
function setupEventListeners() {
    const { DOM } = window.SimpleChatServer.utils;
    
    // 로그아웃 버튼
    const logoutBtn = DOM.select('#logoutBtn');
    DOM.on(logoutBtn, 'click', handleLogout);
    
    // 채팅방 만들기 버튼들
    const createRoomBtn = DOM.select('#createRoomBtn');
    const createFirstRoomBtn = DOM.select('#createFirstRoomBtn');
    DOM.on(createRoomBtn, 'click', showCreateRoomModal);
    DOM.on(createFirstRoomBtn, 'click', showCreateRoomModal);
    
    // 검색
    const searchInput = DOM.select('#searchInput');
    const searchBtn = DOM.select('#searchBtn');
    
    const debouncedSearch = Utils.debounce(handleSearch, 500);
    DOM.on(searchInput, 'input', debouncedSearch);
    DOM.on(searchBtn, 'click', handleSearch);
    
    // 필터 탭
    const filterTabs = DOM.selectAll('.filter-tab');
    filterTabs.forEach(tab => {
        DOM.on(tab, 'click', function() {
            handleFilterChange(this.dataset.filter);
        });
    });
    
    // 정렬 선택
    const sortSelect = DOM.select('#sortSelect');
    DOM.on(sortSelect, 'change', function() {
        handleSortChange(this.value);
    });
    
    // 모달 이벤트
    setupModalEvents();
    
    // 채팅방 생성 폼
    setupCreateRoomForm();
}

/**
 * 모달 이벤트 설정
 */
function setupModalEvents() {
    const { DOM } = window.SimpleChatServer.utils;
    
    // 채팅방 생성 모달
    const createRoomModal = DOM.select('#createRoomModal');
    const closeCreateRoomModal = DOM.select('#closeCreateRoomModal');
    const cancelCreateRoom = DOM.select('#cancelCreateRoom');
    
    DOM.on(closeCreateRoomModal, 'click', hideCreateRoomModal);
    DOM.on(cancelCreateRoom, 'click', hideCreateRoomModal);
    
    // 모달 백드롭 클릭으로 닫기
    DOM.on(createRoomModal, 'click', function(e) {
        if (e.target === this || e.target.classList.contains('modal-backdrop')) {
            hideCreateRoomModal();
        }
    });
    
    // 채팅방 상세 모달
    const roomDetailModal = DOM.select('#roomDetailModal');
    const closeRoomDetailModal = DOM.select('#closeRoomDetailModal');
    const closeRoomDetail = DOM.select('#closeRoomDetail');
    
    DOM.on(closeRoomDetailModal, 'click', hideRoomDetailModal);
    DOM.on(closeRoomDetail, 'click', hideRoomDetailModal);
    
    DOM.on(roomDetailModal, 'click', function(e) {
        if (e.target === this || e.target.classList.contains('modal-backdrop')) {
            hideRoomDetailModal();
        }
    });
    
    // ESC 키로 모달 닫기
    DOM.on(document, 'keydown', function(e) {
        if (e.key === 'Escape') {
            hideCreateRoomModal();
            hideRoomDetailModal();
        }
    });
}

/**
 * 채팅방 생성 폼 설정
 */
function setupCreateRoomForm() {
    const { DOM, Form, Http, Toast, Loading } = window.SimpleChatServer.utils;
    const form = DOM.select('#createRoomForm');
    
    DOM.on(form, 'submit', async function(e) {
        e.preventDefault();
        
        const submitBtn = DOM.select('#submitCreateRoom');
        const formData = Form.getData(this);
        
        // 유효성 검사
        const validation = validateCreateRoomForm(formData);
        if (!validation.isValid) {
            Form.showErrors(this, validation.errors);
            return;
        }
        
        // 로딩 상태
        Loading.showButton(submitBtn);
        Form.clearErrors(this);
        
        try {
            // 채팅방 생성 API 호출
            const response = await Http.post('/api/rooms', {
                name: formData.roomName.trim(),
                description: formData.roomDescription?.trim() || '',
                isPrivate: !(formData.isPublic || false),
                maxParticipants: parseInt(formData.maxMembers) || 100
            });
            
            Toast.success('채팅방이 생성되었습니다!');
            hideCreateRoomModal();
            Form.reset(this);
            
            // 채팅방 목록 새로고침
            await loadChatRooms();
            
            // 생성된 채팅방으로 이동
            if (response.roomId) {
                setTimeout(() => {
                    window.location.href = `/chat?roomId=${response.roomId}`;
                }, 1000);
            }
            
        } catch (error) {
            let errorMessage = '채팅방 생성에 실패했습니다.';
            
            if (error.status === 400) {
                errorMessage = '입력 정보를 확인해주세요.';
            } else if (error.status === 409) {
                errorMessage = '이미 존재하는 채팅방 이름입니다.';
            } else if (error.status === 403) {
                errorMessage = '채팅방 생성 권한이 없습니다.';
            }
            
            Form.showError(this, errorMessage);
            Toast.error(errorMessage);
            
        } finally {
            Loading.hideButton(submitBtn);
        }
    });
}

/**
 * 채팅방 생성 폼 유효성 검사
 */
function validateCreateRoomForm(data) {
    const errors = {};
    
    // 채팅방 이름 검사
    if (!data.roomName || data.roomName.trim().length < 2) {
        errors.roomName = '채팅방 이름은 최소 2자 이상이어야 합니다.';
    } else if (data.roomName.trim().length > 50) {
        errors.roomName = '채팅방 이름은 최대 50자까지 입력할 수 있습니다.';
    }
    
    // 설명 검사 (선택사항)
    if (data.roomDescription && data.roomDescription.length > 200) {
        errors.roomDescription = '설명은 최대 200자까지 입력할 수 있습니다.';
    }
    
    // 최대 참여자 수 검사
    const maxMembers = parseInt(data.maxMembers);
    if (isNaN(maxMembers) || maxMembers < 2 || maxMembers > 1000) {
        errors.maxMembers = '참여자 수는 2명~1000명 사이로 설정해주세요.';
    }
    
    return {
        isValid: Object.keys(errors).length === 0,
        errors
    };
}

/**
 * 채팅방 목록 로드
 */
async function loadChatRooms(filter = 'all') {
    const { Http, DOM, Toast, Loading } = window.SimpleChatServer.utils;
    
    const loadingSpinner = DOM.select('#loadingSpinner');
    const emptyState = DOM.select('#emptyState');
    const roomsGrid = DOM.select('#roomsGrid');

    try {
        // 로딩 상태 표시
        Loading.show('채팅방 목록을 불러오는 중...');
        if (loadingSpinner) DOM.show(loadingSpinner);
        if (emptyState) DOM.hide(emptyState);
        
        // 기존 카드들 제거
        const existingCards = roomsGrid.querySelectorAll('.room-card');
        existingCards.forEach(card => card.remove());

        // API 호출 - JWT 토큰은 자동으로 포함됨
        const response = await Http.get(`/api/rooms?filter=${filter}`);
        
        // 응답 데이터 검증 - ChatRoomListResponse 형식
        if (!response.success || !response.data) {
            throw new Error('Invalid response format');
        }
        
        // ChatRoomListResponse에서 rooms 배열 추출
        const chatRoomListResponse = response.data;
        if (!Array.isArray(chatRoomListResponse.rooms)) {
            throw new Error('Invalid rooms data format');
        }
        
        currentRooms = chatRoomListResponse.rooms || [];
        
        // 필터링 및 정렬 적용
        applyFiltersAndSort();
        
        // UI 업데이트
        updateRoomsDisplay();
        updateRoomsCount();
        
    } catch (error) {
        console.error('Failed to load chat rooms:', error);
        
        let errorMessage = '채팅방 목록을 불러오는데 실패했습니다.';
        if (error.status === 401) {
            errorMessage = '로그인이 필요합니다.';
            setTimeout(() => window.location.href = '/login', 1500);
        } else if (error.status === 403) {
            errorMessage = '채팅방 목록을 볼 권한이 없습니다.';
        } else if (error.status >= 500) {
            errorMessage = '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
        }
        
        Toast.error(errorMessage);
        currentRooms = [];
        filteredRooms = [];
        updateRoomsDisplay();
        
    } finally {
        Loading.hide();
        if (loadingSpinner) DOM.hide(loadingSpinner);
    }
}

/**
 * 채팅방 목록 표시 업데이트
 */
function updateRoomsDisplay() {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    
    const roomsGrid = DOM.select('#roomsGrid');
    const emptyState = DOM.select('#emptyState');
    
    // 기존 카드들만 제거 (스피너는 유지)
    const existingCards = roomsGrid.querySelectorAll('.room-card');
    existingCards.forEach(card => card.remove());
    
    if (filteredRooms.length === 0) {
        const loadingSpinner = DOM.select('#loadingSpinner');
        if (loadingSpinner && !DOM.isHidden(loadingSpinner)) {
            DOM.hide(emptyState);
        } else {
            DOM.show(emptyState);
        }
        return;
    }
    
    DOM.hide(emptyState);
    
    // 애니메이션을 위한 지연 로딩
    filteredRooms.forEach((room, index) => {
        setTimeout(() => {
            const card = createRoomCard(room);
            card.style.opacity = '0';
            card.style.transform = 'translateY(20px)';
            roomsGrid.appendChild(card);
            
            // 애니메이션 시작
            requestAnimationFrame(() => {
                Utils.animate(card, {
                    opacity: 1,
                    transform: 'translateY(0)'
                }, 300);
            });
        }, index * 50); // 카드별 50ms 지연
    });
}

/**
 * 채팅방 카드 생성
 */
function createRoomCard(room) {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    
    const isJoined = room.isJoined || false;
    const memberCount = room.currentParticipants || 0;
    const maxMembers = room.maxParticipants || 100;
    
    // 마지막 메시지 및 활동 시간 처리
    let lastMessageContent = '아직 메시지가 없습니다';
    let lastActivity = room.createdAt ? Utils.formatRelativeTime(room.createdAt) : '방금 전';

    if (room.latestMessage) {
        const sender = room.latestMessage.userNickname || '사용자';
        const content = Utils.escapeHtml(room.latestMessage.content);
        // 메시지 내용이 길 경우 잘라내기
        const truncatedContent = content.length > 30 ? content.substring(0, 30) + '...' : content;
        lastMessageContent = `${sender}: ${truncatedContent}`;
        lastActivity = Utils.formatRelativeTime(room.latestMessage.timestamp);
    }

    const card = DOM.create('div', {
        className: `room-card ${isJoined ? 'joined' : ''}`,
        'data-room-id': room.id
    });
    
    card.innerHTML = `
        <div class="room-card-header">
            <div class="room-info">
                <h3 class="room-name" title="${Utils.escapeHtml(room.name)}">${Utils.escapeHtml(room.name)}</h3>
                ${room.description ? `<p class="room-description" title="${Utils.escapeHtml(room.description)}">${Utils.escapeHtml(room.description)}</p>` : ''}
            </div>
            <div class="room-status">
                <div class="room-type-badge ${!room.isPrivate ? 'public' : 'private'}" 
                     title="${!room.isPrivate ? '누구나 참여할 수 있는 공개 채팅방' : '초대받은 사람만 참여할 수 있는 비공개 채팅방'}">
                    <i class="fas ${!room.isPrivate ? 'fa-globe' : 'fa-lock'}"></i>
                    ${!room.isPrivate ? '공개' : '비공개'}
                </div>
                <div class="room-online-indicator ${isJoined ? 'active' : ''}" 
                     title="${isJoined ? '참여 중인 채팅방' : '참여하지 않은 채팅방'}"></div>
            </div>
        </div>
        
        <div class="room-card-body">
            <div class="room-stats">
                <div class="room-stat" title="현재 참여자 수">
                    <span class="room-stat-icon"><i class="fas fa-users"></i></span>
                    <span class="room-stat-value">${memberCount}</span>
                    <span class="room-stat-max">/${maxMembers}</span>
                </div>
                <div class="room-stat" title="마지막 활동 시간">
                    <span class="room-stat-icon"><i class="fas fa-clock"></i></span>
                    <span class="room-stat-value">${lastActivity}</span>
                </div>
                ${room.unreadCount > 0 ? `
                <div class="room-stat unread" title="읽지 않은 메시지">
                    <span class="room-stat-icon"><i class="fas fa-envelope"></i></span>
                    <span class="room-stat-value">${room.unreadCount}</span>
                </div>
                ` : ''}
            </div>
        </div>
        
        <div class="room-card-footer">
            <div class="room-last-message" title="${lastMessageContent}">
                <i class="fas fa-comment-alt room-message-icon"></i>
                <span class="message-content">${lastMessageContent}</span>
            </div>
            <div class="room-actions">
                <button class="room-action-btn info" data-action="info" 
                        title="채팅방 상세 정보 보기" aria-label="채팅방 정보">
                    <i class="fas fa-info-circle"></i>
                </button>
                ${isJoined ? 
                    `<button class="room-action-btn enter" data-action="enter" 
                             title="채팅방으로 이동" aria-label="채팅방 입장">
                        <i class="fas fa-sign-in-alt"></i>
                        입장
                    </button>` :
                    `<button class="room-action-btn join" data-action="join" 
                             title="채팅방에 참여하기" aria-label="채팅방 참여">
                        <i class="fas fa-plus-circle"></i>
                        참여
                    </button>`
                }
            </div>
        </div>
    `;
    
    // 이벤트 리스너 추가
    setupRoomCardEvents(card, room);
    
    return card;
}

/**
 * 채팅방 카드 이벤트 설정
 */
function setupRoomCardEvents(card, room) {
    const { DOM } = window.SimpleChatServer.utils;
    
    // 카드 클릭으로 채팅방 입장
    DOM.on(card, 'click', function(e) {
        // 버튼 클릭인 경우 무시
        if (e.target.classList.contains('room-action-btn')) {
            return;
        }
        
        if (room.isJoined) {
            window.location.href = `/chat?roomId=${room.id}`;
        } else {
            showRoomDetail(room);
        }
    });
    
    // 액션 버튼들
    const actionButtons = card.querySelectorAll('.room-action-btn');
    actionButtons.forEach(button => {
        DOM.on(button, 'click', function(e) {
            e.stopPropagation();
            const action = this.dataset.action;
            
            switch (action) {
                case 'join':
                    handleJoinRoom(room.id);
                    break;
                case 'enter':
                    window.location.href = `/chat?roomId=${room.id}`;
                    break;
                case 'leave':
                    handleLeaveRoom(room.id);
                    break;
                case 'info':
                    showRoomDetail(room);
                    break;
            }
        });
    });
}

/**
 * 필터 및 정렬 적용
 */
function applyFiltersAndSort() {
    // 필터 적용
    filteredRooms = currentRooms.filter(room => {
        switch (currentFilter) {
            case 'joined':
                return room.isJoined;
            case 'public':
                return !room.isPrivate;
            case 'private':
                return room.isPrivate;
            case 'all':
            default:
                return true;
        }
    });
    
    // 검색 적용
    const searchTerm = document.getElementById('searchInput').value.toLowerCase().trim();
    if (searchTerm) {
        filteredRooms = filteredRooms.filter(room => 
            room.name.toLowerCase().includes(searchTerm) ||
            (room.description && room.description.toLowerCase().includes(searchTerm))
        );
    }
    
    // 정렬 적용
    filteredRooms.sort((a, b) => {
        switch (currentSort) {
            case 'name':
                return a.name.localeCompare(b.name);
            case 'members':
                return (b.memberCount || 0) - (a.memberCount || 0);
            case 'created':
                return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
            case 'recent':
            default:
                return new Date(b.lastActivity || b.createdAt || 0) - new Date(a.lastActivity || a.createdAt || 0);
        }
    });
}

/**
 * 이벤트 핸들러들
 */
async function handleLogout() {
    const { Auth } = window.SimpleChatServer.utils;
    
    if (confirm('로그아웃 하시겠습니까?')) {
        await Auth.logout();
    }
}

function handleSearch() {
    applyFiltersAndSort();
    updateRoomsDisplay();
    updateRoomsCount();
}

function handleFilterChange(filter) {
    const { DOM } = window.SimpleChatServer.utils;
    
    currentFilter = filter;
    
    // 활성 탭 업데이트
    const filterTabs = DOM.selectAll('.filter-tab');
    filterTabs.forEach(tab => {
        DOM.removeClass(tab, 'active');
        if (tab.dataset.filter === filter) {
            DOM.addClass(tab, 'active');
        }
    });
    
    // Re-fetch data from server based on the new filter
    loadChatRooms(currentFilter);
}

function handleSortChange(sort) {
    currentSort = sort;
    applyFiltersAndSort();
    updateRoomsDisplay();
}

async function handleJoinRoom(roomId) {
    const { Http, Toast, Loading, DOM } = window.SimpleChatServer.utils;
    
    // 버튼을 찾아 로딩 상태 적용
    const roomCard = DOM.select(`[data-room-id="${roomId}"]`);
    const joinBtn = roomCard ? roomCard.querySelector('[data-action="join"]') : null;
    
    try {
        if (joinBtn) Loading.showButton(joinBtn);
        
        const response = await Http.post(`/api/rooms/${roomId}/join`);
        
        if (response.success) {
            Toast.success('채팅방에 참여했습니다!');
            
            // 채팅방 목록 새로고침
            await loadChatRooms(currentFilter);
            
            // 채팅 페이지로 이동
            setTimeout(() => {
                window.location.href = `/chat?roomId=${roomId}`;
            }, 800);
        } else {
            throw new Error('Join request failed');
        }
        
    } catch (error) {
        console.error('Join room error:', error);
        let errorMessage = '채팅방 참여에 실패했습니다.';
        
        if (error.status === 404) {
            errorMessage = '존재하지 않는 채팅방입니다.';
        } else if (error.status === 409) {
            errorMessage = '이미 참여 중인 채팅방입니다.';
        } else if (error.status === 403) {
            errorMessage = '채팅방 참여 권한이 없습니다.';
        } else if (error.status === 401) {
            errorMessage = '로그인이 필요합니다.';
        } else if (error.status >= 500) {
            errorMessage = '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
        }
        
        Toast.error(errorMessage);
    } finally {
        if (joinBtn) Loading.hideButton(joinBtn);
    }
}

async function handleLeaveRoom(roomId) {
    const { Http, Toast } = window.SimpleChatServer.utils;
    
    if (!confirm('정말로 채팅방을 나가시겠습니까?')) {
        return;
    }
    
    try {
        await Http.delete(`/api/rooms/${roomId}/leave`);
        Toast.success('채팅방에서 나갔습니다.');
        // 채팅방 목록 새로고침
        await loadChatRooms();
    } catch (error) {
        let errorMessage = '채팅방 나가기에 실패했습니다.';
        console.error('Error leaving room:', error); // Add more detailed logging

        if (error.status) { // Check if status exists
            if (error.status === 404) {
                errorMessage = '존재하지 않는 채팅방입니다.';
            } else if (error.status === 409) {
                errorMessage = '참여하지 않은 채팅방입니다.';
            } else {
                errorMessage = `채팅방 나가기에 실패했습니다. (오류 코드: ${error.status})`;
            }
        } else {
            // This block will catch network errors, SyntaxErrors from response.json(), etc.
            errorMessage = '채팅방 나가기 중 알 수 없는 오류가 발생했습니다.';
        }

        Toast.error(errorMessage);
    }
}

function updateRoomsCount() {
    const { DOM } = window.SimpleChatServer.utils;
    const roomsCountText = DOM.select('#roomsCountText');
    
    if (roomsCountText) {
        const totalCount = currentRooms.length;
        const filteredCount = filteredRooms.length;
        
        if (totalCount === filteredCount) {
            roomsCountText.textContent = `${totalCount}개의 채팅방`;
        } else {
            roomsCountText.textContent = `${filteredCount}개의 채팅방 (전체 ${totalCount}개)`;
        }
    }
}

/**
 * 모달 관련 함수들
 */
function showCreateRoomModal() {
    const { DOM } = window.SimpleChatServer.utils;
    const modal = DOM.select('#createRoomModal');
    DOM.addClass(modal, 'show');
    
    // 첫 번째 입력 필드에 포커스
    setTimeout(() => {
        const firstInput = modal.querySelector('input[type="text"]');
        if (firstInput) firstInput.focus();
    }, 100);
}

function hideCreateRoomModal() {
    const { DOM, Form } = window.SimpleChatServer.utils;
    const modal = DOM.select('#createRoomModal');
    const form = DOM.select('#createRoomForm');
    
    DOM.removeClass(modal, 'show');
    Form.reset(form);
    Form.clearErrors(form);
}

function showRoomDetail(room) {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    const modal = DOM.select('#roomDetailModal');
    const titleEl = DOM.select('#roomDetailTitle');
    const contentEl = DOM.select('#roomDetailContent');
    const joinBtn = DOM.select('#joinRoomBtn');
    
    titleEl.textContent = room.name;
    
    const memberCount = room.currentParticipants || 0;
    const maxMembers = room.maxParticipants || 100;
    const createdAt = room.createdAt ? Utils.formatDate(room.createdAt) : '알 수 없음';
    const lastActivity = room.lastActivity ? Utils.formatRelativeTime(room.lastActivity) : '방금 전';
    
    contentEl.innerHTML = `
        <div class="room-detail-header">
            <h3 class="room-detail-name">${Utils.escapeHtml(room.name)}</h3>
            ${room.description ? `<p class="room-detail-description">${Utils.escapeHtml(room.description)}</p>` : ''}
        </div>
        
        <div class="room-detail-stats">
            <div class="stat-item">
                <span class="stat-value">${memberCount}</span>
                <div class="stat-label">참여자</div>
            </div>
            <div class="stat-item">
                <span class="stat-value">${maxMembers}</span>
                <div class="stat-label">최대 인원</div>
            </div>
            <div class="stat-item">
                <span class="stat-value">${!room.isPrivate ? '공개' : '비공개'}</span>
                <div class="stat-label">타입</div>
            </div>
            <div class="stat-item">
                <span class="stat-value">${createdAt}</span>
                <div class="stat-label">생성일</div>
            </div>
        </div>
        
        <div class="room-detail-info">
            <p><strong>마지막 활동:</strong> ${lastActivity}</p>
            ${room.lastMessage ? `<p><strong>최근 메시지:</strong> "${Utils.escapeHtml(room.lastMessage)}"</p>` : ''}
        </div>
    `;
    
    // 참여 버튼 설정
    if (room.isJoined) {
        joinBtn.textContent = '채팅방 입장';
        joinBtn.onclick = () => {
            window.location.href = `/chat?roomId=${room.id}`;
        };
    } else {
        joinBtn.textContent = '채팅방 참여';
        joinBtn.onclick = () => {
            hideRoomDetailModal();
            handleJoinRoom(room.id);
        };
    }
    
    DOM.addClass(modal, 'show');
}

function hideRoomDetailModal() {
    const { DOM } = window.SimpleChatServer.utils;
    const modal = DOM.select('#roomDetailModal');
    DOM.removeClass(modal, 'show');
}

console.log('SimpleChatServer rooms.js loaded successfully');
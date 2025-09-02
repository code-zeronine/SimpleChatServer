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
    loadChatRooms();
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
            const response = await Http.post('/api/chat-rooms', {
                name: formData.roomName.trim(),
                description: formData.roomDescription?.trim() || '',
                isPublic: formData.isPublic || false,
                allowSearch: formData.allowSearch || false,
                maxMembers: parseInt(formData.maxMembers) || 100
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
async function loadChatRooms() {
    const { Http, DOM, Toast } = window.SimpleChatServer.utils;
    
    const loadingSpinner = DOM.select('#loadingSpinner');
    const roomsGrid = DOM.select('#roomsGrid');
    const emptyState = DOM.select('#emptyState');
    
    try {
        // 로딩 상태 표시
        loadingSpinner.style.display = 'flex';
        emptyState.style.display = 'none';
        
        // API 호출
        const response = await Http.get('/api/chat-rooms');
        currentRooms = response.rooms || [];
        
        // 초기 필터링 및 정렬
        applyFiltersAndSort();
        
        // UI 업데이트
        updateRoomsDisplay();
        updateRoomsCount();
        
    } catch (error) {
        console.error('Failed to load chat rooms:', error);
        Toast.error('채팅방 목록을 불러오는데 실패했습니다.');
        
        // 빈 상태 표시
        currentRooms = [];
        filteredRooms = [];
        updateRoomsDisplay();
        
    } finally {
        loadingSpinner.style.display = 'none';
    }
}

/**
 * 채팅방 목록 표시 업데이트
 */
function updateRoomsDisplay() {
    const { DOM } = window.SimpleChatServer.utils;
    
    const roomsGrid = DOM.select('#roomsGrid');
    const emptyState = DOM.select('#emptyState');
    const loadingSpinner = DOM.select('#loadingSpinner');
    
    // 로딩 스피너 제거
    const existingSpinner = roomsGrid.querySelector('.loading-spinner');
    if (existingSpinner) {
        existingSpinner.remove();
    }
    
    // 기존 카드들 제거
    const existingCards = roomsGrid.querySelectorAll('.room-card');
    existingCards.forEach(card => card.remove());
    
    if (filteredRooms.length === 0) {
        emptyState.style.display = 'flex';
        return;
    }
    
    emptyState.style.display = 'none';
    
    // 채팅방 카드 생성
    filteredRooms.forEach(room => {
        const card = createRoomCard(room);
        roomsGrid.appendChild(card);
    });
}

/**
 * 채팅방 카드 생성
 */
function createRoomCard(room) {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    
    const isJoined = room.isJoined || false;
    const memberCount = room.memberCount || 0;
    const maxMembers = room.maxMembers || 100;
    const lastMessage = room.lastMessage || '';
    const lastActivity = room.lastActivity ? Utils.formatRelativeTime(room.lastActivity) : '방금 전';
    
    const card = DOM.create('div', {
        className: `room-card ${isJoined ? 'joined' : ''}`,
        'data-room-id': room.id
    });
    
    card.innerHTML = `
        <div class="room-card-header">
            <div class="room-info">
                <h3 class="room-name">${Utils.escapeHtml(room.name)}</h3>
                ${room.description ? `<p class="room-description">${Utils.escapeHtml(room.description)}</p>` : ''}
            </div>
            <div class="room-status">
                <div class="room-type-badge ${room.isPublic ? 'public' : 'private'}">
                    ${room.isPublic ? 'PUBLIC' : 'PRIVATE'}
                </div>
                <div class="room-online-indicator"></div>
            </div>
        </div>
        
        <div class="room-card-body">
            <div class="room-stats">
                <div class="room-stat">
                    <span class="room-stat-icon">👥</span>
                    <span>${memberCount}/${maxMembers}</span>
                </div>
                <div class="room-stat">
                    <span class="room-stat-icon">🕒</span>
                    <span>${lastActivity}</span>
                </div>
            </div>
        </div>
        
        <div class="room-card-footer">
            <div class="room-last-message">
                ${lastMessage ? `"${Utils.escapeHtml(lastMessage)}"` : '아직 메시지가 없습니다'}
            </div>
            <div class="room-actions">
                <button class="room-action-btn info" data-action="info">
                    정보
                </button>
                ${isJoined ? 
                    `<button class="room-action-btn leave" data-action="leave">나가기</button>` :
                    `<button class="room-action-btn join" data-action="join">참여</button>`
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
                return room.isPublic;
            case 'private':
                return !room.isPublic;
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
function handleLogout() {
    const { Auth } = window.SimpleChatServer.utils;
    
    if (confirm('로그아웃 하시겠습니까?')) {
        Auth.logout();
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
    
    applyFiltersAndSort();
    updateRoomsDisplay();
    updateRoomsCount();
}

function handleSortChange(sort) {
    currentSort = sort;
    applyFiltersAndSort();
    updateRoomsDisplay();
}

async function handleJoinRoom(roomId) {
    const { Http, Toast } = window.SimpleChatServer.utils;
    
    try {
        await Http.post(`/api/chat-rooms/${roomId}/join`);
        Toast.success('채팅방에 참여했습니다!');
        
        // 채팅방 목록 새로고침
        await loadChatRooms();
        
        // 채팅 페이지로 이동
        setTimeout(() => {
            window.location.href = `/chat?roomId=${roomId}`;
        }, 1000);
        
    } catch (error) {
        let errorMessage = '채팅방 참여에 실패했습니다.';
        
        if (error.status === 404) {
            errorMessage = '존재하지 않는 채팅방입니다.';
        } else if (error.status === 409) {
            errorMessage = '이미 참여 중인 채팅방입니다.';
        } else if (error.status === 403) {
            errorMessage = '채팅방 참여 권한이 없습니다.';
        }
        
        Toast.error(errorMessage);
    }
}

async function handleLeaveRoom(roomId) {
    const { Http, Toast } = window.SimpleChatServer.utils;
    
    if (!confirm('정말로 채팅방을 나가시겠습니까?')) {
        return;
    }
    
    try {
        await Http.delete(`/api/chat-rooms/${roomId}/leave`);
        Toast.success('채팅방에서 나갔습니다.');
        
        // 채팅방 목록 새로고침
        await loadChatRooms();
        
    } catch (error) {
        let errorMessage = '채팅방 나가기에 실패했습니다.';
        
        if (error.status === 404) {
            errorMessage = '존재하지 않는 채팅방입니다.';
        } else if (error.status === 409) {
            errorMessage = '참여하지 않은 채팅방입니다.';
        }
        
        Toast.error(errorMessage);
    }
}

function updateRoomsCount() {
    const { DOM } = window.SimpleChatServer.utils;
    const roomsCountEl = DOM.select('#roomsCount');
    
    if (roomsCountEl) {
        roomsCountEl.textContent = `${filteredRooms.length}개의 채팅방`;
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
    
    const memberCount = room.memberCount || 0;
    const maxMembers = room.maxMembers || 100;
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
                <span class="stat-value">${room.isPublic ? '공개' : '비공개'}</span>
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
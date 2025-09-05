/**
 * SimpleChatServer - Index Page JavaScript
 * 
 * 메인 페이지의 인터랙션과 기능을 담당합니다.
 */

document.addEventListener('DOMContentLoaded', function() {
    const { DOM, Router, Auth } = window.SimpleChatServer.utils;
    
    // 인증 상태 확인
    checkAuthStatus();
    
    // 네비게이션 설정
    setupNavigation();
    
    // 기능 카드 애니메이션
    setupFeatureCards();
    
    // 채팅 프리뷰 애니메이션
    setupChatPreview();
    
    // 스크롤 이벤트
    setupScrollEffects();
});

/**
 * 인증 상태 확인 및 네비게이션 업데이트
 */
function checkAuthStatus() {
    const { Auth, DOM, Router } = window.SimpleChatServer.utils;
    const nav = DOM.select('.nav');
    
    if (Auth.isAuthenticated()) {
        // 로그인된 상태 - 네비게이션 업데이트
        const user = window.SimpleChatServer.utils.Storage.getUser();
        nav.innerHTML = `
            <div class="user-menu">
                <span class="user-name">안녕하세요, ${user?.name || '사용자'}님</span>
                <a href="/chat" class="nav-link">채팅하기</a>
                <a href="/rooms" class="nav-link">채팅방</a>
                <button class="nav-link logout-btn" id="logoutBtn">로그아웃</button>
            </div>
        `;
        
        // 로그아웃 버튼 이벤트
        const logoutBtn = DOM.select('#logoutBtn');
        DOM.on(logoutBtn, 'click', handleLogout);
        
        // 히어로 섹션 버튼 업데이트
        const heroActions = DOM.select('.hero-actions');
        if (heroActions) {
            heroActions.innerHTML = `
                <a href="/chat" class="btn btn-primary">채팅하기</a>
                <a href="/rooms" class="btn btn-secondary">채팅방 목록</a>
            `;
        }
    } else {
        // 비로그인 상태 - 기본 네비게이션
        nav.innerHTML = `
            <a href="/login" class="nav-link">로그인</a>
        `;
    }
}

/**
 * 네비게이션 설정
 */
function setupNavigation() {
    const { DOM } = window.SimpleChatServer.utils;
    
    // 스무스 스크롤
    const scrollLinks = DOM.selectAll('a[href^="#"]');
    scrollLinks.forEach(link => {
        DOM.on(link, 'click', function(e) {
            e.preventDefault();
            const targetId = this.getAttribute('href').substring(1);
            const targetElement = DOM.select(`#${targetId}`);
            
            if (targetElement) {
                targetElement.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        });
    });
}

/**
 * 기능 카드 애니메이션 설정
 */
function setupFeatureCards() {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    const featureCards = DOM.selectAll('.feature-card');
    
    // Intersection Observer로 스크롤 애니메이션
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
    };
    
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                DOM.addClass(entry.target, 'animate-in');
            }
        });
    }, observerOptions);
    
    featureCards.forEach((card, index) => {
        // 초기 애니메이션 상태 설정
        card.style.opacity = '0';
        card.style.transform = 'translateY(30px)';
        card.style.transition = `opacity 0.6s ease ${index * 0.1}s, transform 0.6s ease ${index * 0.1}s`;
        
        observer.observe(card);
        
        // 호버 효과 개선
        DOM.on(card, 'mouseenter', function() {
            this.style.transform = 'translateY(-8px) scale(1.02)';
        });
        
        DOM.on(card, 'mouseleave', function() {
            this.style.transform = 'translateY(-2px) scale(1)';
        });
    });
    
    // CSS 애니메이션 클래스 추가
    const animationStyles = `
        .feature-card.animate-in {
            opacity: 1 !important;
            transform: translateY(0) !important;
        }
    `;
    
    const styleEl = document.createElement('style');
    styleEl.textContent = animationStyles;
    document.head.appendChild(styleEl);
}

/**
 * 채팅 프리뷰 애니메이션 설정
 */
function setupChatPreview() {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    const chatMessages = DOM.select('.chat-messages');
    const typingIndicator = createTypingIndicator();
    
    if (!chatMessages) return;
    
    // 타이핑 인디케이터 생성
    function createTypingIndicator() {
        return DOM.create('div', {
            className: 'message typing-indicator',
            style: 'display: none; opacity: 0; transition: all 0.3s ease;'
        }, `
            <span class="message-author">Bob</span>
            <span class="message-content">
                <span class="typing-dots">
                    <span></span><span></span><span></span>
                </span>
            </span>
        `);
    }
    
    // 새 메시지 추가 함수
    function addMessage(author, content, isOwn = false) {
        const message = DOM.create('div', {
            className: `message ${isOwn ? 'mine' : ''}`
        });
        
        message.innerHTML = isOwn ? 
            `<span class="message-content">${content}</span>` :
            `<span class="message-author">${author}</span><span class="message-content">${content}</span>`;
        
        // 애니메이션으로 추가
        message.style.opacity = '0';
        message.style.transform = 'translateY(10px)';
        chatMessages.appendChild(message);
        
        // 애니메이션 실행
        setTimeout(() => {
            message.style.opacity = '1';
            message.style.transform = 'translateY(0)';
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }, 50);
        
        return message;
    }
    
    // 타이핑 인디케이터 표시/숨기기
    function showTyping() {
        chatMessages.appendChild(typingIndicator);
        typingIndicator.style.display = 'block';
        setTimeout(() => {
            typingIndicator.style.opacity = '1';
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }, 50);
    }
    
    function hideTyping() {
        typingIndicator.style.opacity = '0';
        setTimeout(() => {
            if (typingIndicator.parentNode) {
                typingIndicator.parentNode.removeChild(typingIndicator);
            }
        }, 300);
    }
    
    // 데모 메시지 시퀀스
    const demoMessages = [
        { author: 'Charlie', content: '프로젝트 진행 어떻게 되고 있나요?', delay: 2000 },
        { author: 'Alice', content: '거의 완료되어 가고 있습니다! 🚀', delay: 1500 },
        { author: 'You', content: '좋은 소식이네요!', isOwn: true, delay: 2000 },
        { author: 'Bob', content: '다음 주까지는 모든 기능이 준비될 것 같습니다.', delay: 1800 }
    ];
    
    // 데모 애니메이션 시작
    let messageIndex = 0;
    
    function playDemoSequence() {
        if (messageIndex >= demoMessages.length) {
            // 시퀀스 완료 후 잠시 대기 후 다시 시작
            setTimeout(() => {
                // 기존 데모 메시지 제거
                const demoMsgs = chatMessages.querySelectorAll('.message:nth-child(n+4)');
                demoMsgs.forEach(msg => msg.remove());
                messageIndex = 0;
                setTimeout(playDemoSequence, 1000);
            }, 5000);
            return;
        }
        
        const msg = demoMessages[messageIndex];
        
        // 타이핑 인디케이터 표시
        if (!msg.isOwn) {
            showTyping();
            
            setTimeout(() => {
                hideTyping();
                setTimeout(() => {
                    addMessage(msg.author, msg.content, msg.isOwn);
                    messageIndex++;
                    setTimeout(playDemoSequence, msg.delay);
                }, 300);
            }, 1000 + Math.random() * 1000); // 1-2초 타이핑 시간
        } else {
            addMessage(msg.author, msg.content, msg.isOwn);
            messageIndex++;
            setTimeout(playDemoSequence, msg.delay);
        }
    }
    
    // 채팅 프리뷰가 뷰포트에 들어올 때 데모 시작
    const chatPreview = DOM.select('.chat-preview');
    const previewObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                setTimeout(playDemoSequence, 1000);
                previewObserver.unobserve(entry.target);
            }
        });
    }, { threshold: 0.5 });
    
    if (chatPreview) {
        previewObserver.observe(chatPreview);
    }
    
    // 타이핑 도트 애니메이션 CSS
    const typingStyles = `
        .typing-indicator .message-content {
            background: #e9ecef;
            color: #6c757d;
            border-radius: 18px;
            padding: 8px 12px;
            display: inline-block;
        }
        .typing-dots {
            display: flex;
            align-items: center;
            gap: 2px;
        }
        .typing-dots span {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background-color: #6c757d;
            animation: typingDot 1.4s infinite ease-in-out;
        }
        .typing-dots span:nth-child(2) {
            animation-delay: 0.2s;
        }
        .typing-dots span:nth-child(3) {
            animation-delay: 0.4s;
        }
        @keyframes typingDot {
            0%, 60%, 100% { transform: scale(1); opacity: 0.5; }
            30% { transform: scale(1.2); opacity: 1; }
        }
    `;
    
    const typingStyleEl = document.createElement('style');
    typingStyleEl.textContent = typingStyles;
    document.head.appendChild(typingStyleEl);
}

/**
 * 스크롤 효과 설정
 */
function setupScrollEffects() {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    const header = DOM.select('.header');
    
    if (!header) return;
    
    let lastScrollTop = 0;
    
    const handleScroll = Utils.throttle(() => {
        const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
        
        // 헤더 배경 투명도 조절
        if (scrollTop > 50) {
            header.style.backgroundColor = 'rgba(255, 255, 255, 0.95)';
            header.style.backdropFilter = 'blur(10px)';
        } else {
            header.style.backgroundColor = 'var(--card-bg)';
            header.style.backdropFilter = 'none';
        }
        
        // 스크롤 방향에 따른 헤더 표시/숨김
        if (scrollTop > lastScrollTop && scrollTop > 100) {
            // 아래로 스크롤 - 헤더 숨김
            header.style.transform = 'translateY(-100%)';
        } else {
            // 위로 스크롤 - 헤더 표시
            header.style.transform = 'translateY(0)';
        }
        
        lastScrollTop = scrollTop;
    }, 100);
    
    DOM.on(window, 'scroll', handleScroll);
    
    // 페이지 상단으로 이동 버튼
    createScrollTopButton();
}

/**
 * 페이지 상단으로 이동 버튼 생성
 */
function createScrollTopButton() {
    const { DOM, Utils } = window.SimpleChatServer.utils;
    
    const scrollTopBtn = DOM.create('button', {
        className: 'scroll-top-btn',
        style: `
            position: fixed;
            bottom: 20px;
            right: 20px;
            width: 50px;
            height: 50px;
            border-radius: 50%;
            background: var(--primary-color);
            color: white;
            border: none;
            box-shadow: var(--shadow-lg);
            cursor: pointer;
            font-size: 18px;
            z-index: 1000;
            opacity: 0;
            visibility: hidden;
            transition: all 0.3s ease;
            transform: translateY(20px);
        `
    }, '<i class="fas fa-arrow-up"></i>');
    
    document.body.appendChild(scrollTopBtn);
    
    // 스크롤 위치에 따라 버튼 표시/숨김
    const toggleScrollTopBtn = Utils.throttle(() => {
        const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
        
        if (scrollTop > 300) {
            scrollTopBtn.style.opacity = '1';
            scrollTopBtn.style.visibility = 'visible';
            scrollTopBtn.style.transform = 'translateY(0)';
        } else {
            scrollTopBtn.style.opacity = '0';
            scrollTopBtn.style.visibility = 'hidden';
            scrollTopBtn.style.transform = 'translateY(20px)';
        }
    }, 100);
    
    // 클릭 이벤트
    DOM.on(scrollTopBtn, 'click', () => {
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    });
    
    DOM.on(window, 'scroll', toggleScrollTopBtn);
}

/**
 * 로그아웃 처리
 */
function handleLogout() {
    const { Auth, Toast } = window.SimpleChatServer.utils;
    
    if (confirm('로그아웃 하시겠습니까?')) {
        Auth.logout();
        Toast.success('로그아웃되었습니다.');
    }
}

console.log('SimpleChatServer index.js loaded successfully');
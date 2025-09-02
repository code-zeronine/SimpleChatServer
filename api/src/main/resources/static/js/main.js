/**
 * SimpleChatServer - Main JavaScript
 * 
 * 공통 유틸리티 함수와 전역 기능을 제공합니다.
 */

// 전역 설정
window.SimpleChatServer = {
    apiUrl: '/api',
    wsUrl: `ws://${window.location.host}/ws/chat`,
    version: '1.0.0'
};

/**
 * DOM 유틸리티
 */
const DOM = {
    /**
     * 요소 선택
     */
    select: (selector, parent = document) => parent.querySelector(selector),
    selectAll: (selector, parent = document) => parent.querySelectorAll(selector),
    
    /**
     * 요소 생성
     */
    create: (tag, attributes = {}, content = '') => {
        const element = document.createElement(tag);
        
        Object.entries(attributes).forEach(([key, value]) => {
            if (key === 'className') {
                element.className = value;
            } else if (key === 'dataset') {
                Object.entries(value).forEach(([dataKey, dataValue]) => {
                    element.dataset[dataKey] = dataValue;
                });
            } else {
                element.setAttribute(key, value);
            }
        });
        
        if (content) {
            element.innerHTML = content;
        }
        
        return element;
    },
    
    /**
     * 클래스 관리
     */
    addClass: (element, className) => element?.classList.add(className),
    removeClass: (element, className) => element?.classList.remove(className),
    toggleClass: (element, className) => element?.classList.toggle(className),
    hasClass: (element, className) => element?.classList.contains(className),
    
    /**
     * 이벤트 리스너
     */
    on: (element, event, handler, options = {}) => {
        element?.addEventListener(event, handler, options);
    },
    
    off: (element, event, handler) => {
        element?.removeEventListener(event, handler);
    },
    
    /**
     * 스타일 설정
     */
    setStyle: (element, styles) => {
        if (element && typeof styles === 'object') {
            Object.entries(styles).forEach(([property, value]) => {
                element.style[property] = value;
            });
        }
    }
};

/**
 * HTTP 요청 유틸리티
 */
const Http = {
    /**
     * 기본 요청 함수
     */
    request: async (url, options = {}) => {
        const token = Storage.getToken();
        const defaultHeaders = {
            'Content-Type': 'application/json'
        };
        
        if (token) {
            defaultHeaders['Authorization'] = `Bearer ${token}`;
        }
        
        const config = {
            headers: { ...defaultHeaders, ...options.headers },
            ...options
        };
        
        try {
            const response = await fetch(url, config);
            const data = await response.json();
            
            if (!response.ok) {
                throw new HttpError(response.status, data.message || '요청 처리 중 오류가 발생했습니다.');
            }
            
            return data;
        } catch (error) {
            if (error instanceof HttpError) {
                throw error;
            }
            throw new HttpError(500, '네트워크 오류가 발생했습니다.');
        }
    },
    
    /**
     * GET 요청
     */
    get: (url, params = {}) => {
        const urlObj = new URL(url, window.location.origin);
        Object.entries(params).forEach(([key, value]) => {
            if (value !== null && value !== undefined) {
                urlObj.searchParams.append(key, value);
            }
        });
        return Http.request(urlObj.toString(), { method: 'GET' });
    },
    
    /**
     * POST 요청
     */
    post: (url, data = {}) => {
        return Http.request(url, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },
    
    /**
     * PUT 요청
     */
    put: (url, data = {}) => {
        return Http.request(url, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },
    
    /**
     * DELETE 요청
     */
    delete: (url) => {
        return Http.request(url, { method: 'DELETE' });
    }
};

/**
 * HTTP 오류 클래스
 */
class HttpError extends Error {
    constructor(status, message) {
        super(message);
        this.name = 'HttpError';
        this.status = status;
    }
}

/**
 * 스토리지 유틸리티
 */
const Storage = {
    /**
     * JWT 토큰 관리
     */
    setToken: (token) => {
        localStorage.setItem('auth_token', token);
    },
    
    getToken: () => {
        return localStorage.getItem('auth_token');
    },
    
    removeToken: () => {
        localStorage.removeItem('auth_token');
    },
    
    /**
     * 사용자 정보 관리
     */
    setUser: (user) => {
        localStorage.setItem('user_info', JSON.stringify(user));
    },
    
    getUser: () => {
        const userInfo = localStorage.getItem('user_info');
        return userInfo ? JSON.parse(userInfo) : null;
    },
    
    removeUser: () => {
        localStorage.removeItem('user_info');
    },
    
    /**
     * 일반 스토리지
     */
    set: (key, value) => {
        localStorage.setItem(key, JSON.stringify(value));
    },
    
    get: (key, defaultValue = null) => {
        const item = localStorage.getItem(key);
        return item ? JSON.parse(item) : defaultValue;
    },
    
    remove: (key) => {
        localStorage.removeItem(key);
    },
    
    /**
     * 전체 정리
     */
    clear: () => {
        localStorage.clear();
    }
};

/**
 * 폼 유틸리티
 */
const Form = {
    /**
     * 폼 데이터 수집
     */
    getData: (formElement) => {
        const formData = new FormData(formElement);
        const data = {};
        
        for (const [key, value] of formData.entries()) {
            data[key] = value;
        }
        
        return data;
    },
    
    /**
     * 폼 유효성 검사
     */
    validate: (formElement) => {
        const errors = {};
        const inputs = formElement.querySelectorAll('input, textarea, select');
        
        inputs.forEach(input => {
            const value = input.value.trim();
            const name = input.name;
            
            // 필수 필드 검사
            if (input.hasAttribute('required') && !value) {
                errors[name] = '이 필드는 필수입니다.';
                return;
            }
            
            // 이메일 검사
            if (input.type === 'email' && value) {
                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                if (!emailRegex.test(value)) {
                    errors[name] = '올바른 이메일 형식이 아닙니다.';
                }
            }
            
            // 비밀번호 검사 (최소 6자)
            if (input.type === 'password' && value && value.length < 6) {
                errors[name] = '비밀번호는 최소 6자 이상이어야 합니다.';
            }
        });
        
        return {
            isValid: Object.keys(errors).length === 0,
            errors
        };
    },
    
    /**
     * 에러 표시
     */
    showErrors: (formElement, errors) => {
        // 기존 에러 메시지 제거
        Form.clearErrors(formElement);
        
        Object.entries(errors).forEach(([field, message]) => {
            const input = formElement.querySelector(`[name="${field}"]`);
            const errorElement = formElement.querySelector(`#${field}Error`);
            
            if (input) {
                DOM.addClass(input, 'error');
            }
            
            if (errorElement) {
                errorElement.textContent = message;
                DOM.addClass(errorElement, 'show');
            }
        });
    },
    
    /**
     * 에러 메시지 제거
     */
    clearErrors: (formElement) => {
        const inputs = formElement.querySelectorAll('input, textarea, select');
        const errorElements = formElement.querySelectorAll('.form-error');
        
        inputs.forEach(input => DOM.removeClass(input, 'error'));
        errorElements.forEach(element => {
            element.textContent = '';
            DOM.removeClass(element, 'show');
        });
    },
    
    /**
     * 성공 메시지 표시
     */
    showSuccess: (formElement, message) => {
        const successElement = formElement.querySelector('.form-success');
        if (successElement) {
            successElement.textContent = message;
            DOM.addClass(successElement, 'show');
            
            setTimeout(() => {
                DOM.removeClass(successElement, 'show');
            }, 5000);
        }
    }
};

/**
 * 로딩 상태 관리
 */
const Loading = {
    /**
     * 버튼 로딩 상태
     */
    showButton: (button) => {
        DOM.addClass(button, 'loading');
        button.disabled = true;
    },
    
    hideButton: (button) => {
        DOM.removeClass(button, 'loading');
        button.disabled = false;
    },
    
    /**
     * 전역 로딩 상태
     */
    show: (message = '로딩 중...') => {
        let loader = DOM.select('#global-loader');
        
        if (!loader) {
            loader = DOM.create('div', {
                id: 'global-loader',
                className: 'global-loader'
            }, `
                <div class="loader-backdrop">
                    <div class="loader-content">
                        <div class="loading-spinner"></div>
                        <div class="loader-message">${message}</div>
                    </div>
                </div>
            `);
            document.body.appendChild(loader);
        }
        
        loader.style.display = 'flex';
    },
    
    hide: () => {
        const loader = DOM.select('#global-loader');
        if (loader) {
            loader.style.display = 'none';
        }
    }
};

/**
 * 알림 메시지
 */
const Toast = {
    /**
     * 토스트 메시지 표시
     */
    show: (message, type = 'info', duration = 3000) => {
        const toast = DOM.create('div', {
            className: `toast toast-${type}`
        }, `
            <div class="toast-content">
                <span class="toast-message">${message}</span>
                <button class="toast-close">&times;</button>
            </div>
        `);
        
        // 토스트 컨테이너 생성 (없으면)
        let container = DOM.select('#toast-container');
        if (!container) {
            container = DOM.create('div', { id: 'toast-container', className: 'toast-container' });
            document.body.appendChild(container);
        }
        
        container.appendChild(toast);
        
        // 닫기 버튼 이벤트
        const closeBtn = toast.querySelector('.toast-close');
        DOM.on(closeBtn, 'click', () => toast.remove());
        
        // 자동 제거
        if (duration > 0) {
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.remove();
                }
            }, duration);
        }
        
        return toast;
    },
    
    /**
     * 타입별 편의 메소드
     */
    success: (message, duration) => Toast.show(message, 'success', duration),
    error: (message, duration) => Toast.show(message, 'error', duration),
    warning: (message, duration) => Toast.show(message, 'warning', duration),
    info: (message, duration) => Toast.show(message, 'info', duration)
};

/**
 * 유틸리티 함수들
 */
const Utils = {
    /**
     * 디바운스 함수
     */
    debounce: (func, wait) => {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },
    
    /**
     * 스로틀 함수
     */
    throttle: (func, limit) => {
        let inThrottle;
        return function(...args) {
            if (!inThrottle) {
                func.apply(this, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        };
    },
    
    /**
     * 랜덤 ID 생성
     */
    generateId: () => {
        return Date.now().toString(36) + Math.random().toString(36).substr(2);
    },
    
    /**
     * 날짜 포맷팅
     */
    formatDate: (date, format = 'YYYY-MM-DD HH:mm') => {
        const d = new Date(date);
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        
        return format
            .replace('YYYY', year)
            .replace('MM', month)
            .replace('DD', day)
            .replace('HH', hours)
            .replace('mm', minutes);
    },
    
    /**
     * 상대 시간 표시
     */
    formatRelativeTime: (date) => {
        const now = new Date();
        const diff = now - new Date(date);
        const seconds = Math.floor(diff / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);
        
        if (days > 0) return `${days}일 전`;
        if (hours > 0) return `${hours}시간 전`;
        if (minutes > 0) return `${minutes}분 전`;
        return '방금 전';
    },
    
    /**
     * URL 파라미터 파싱
     */
    parseUrlParams: () => {
        const params = {};
        const urlParams = new URLSearchParams(window.location.search);
        for (const [key, value] of urlParams) {
            params[key] = value;
        }
        return params;
    }
};

/**
 * 페이지 라우팅
 */
const Router = {
    /**
     * 페이지 이동
     */
    navigate: (path) => {
        window.location.href = path;
    },
    
    /**
     * 새 탭에서 열기
     */
    openNewTab: (url) => {
        window.open(url, '_blank');
    },
    
    /**
     * 뒤로가기
     */
    back: () => {
        window.history.back();
    },
    
    /**
     * 현재 경로 확인
     */
    getCurrentPath: () => {
        return window.location.pathname;
    }
};

/**
 * 인증 관련 유틸리티
 */
const Auth = {
    /**
     * 로그인 상태 확인
     */
    isAuthenticated: () => {
        return !!Storage.getToken();
    },
    
    /**
     * 로그아웃
     */
    logout: () => {
        Storage.removeToken();
        Storage.removeUser();
        Router.navigate('/login');
    },
    
    /**
     * 인증이 필요한 페이지 접근 제어
     */
    requireAuth: () => {
        if (!Auth.isAuthenticated()) {
            Router.navigate('/login');
            return false;
        }
        return true;
    }
};

// 전역 객체로 노출
window.SimpleChatServer.utils = {
    DOM,
    Http,
    Storage,
    Form,
    Loading,
    Toast,
    Utils,
    Router,
    Auth
};

// DOM 로드 완료 후 실행할 함수들
document.addEventListener('DOMContentLoaded', function() {
    // 전역 스타일 추가
    const globalStyles = `
        .global-loader {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            z-index: 9999;
            display: none;
        }
        .loader-backdrop {
            background: rgba(0,0,0,0.7);
            display: flex;
            align-items: center;
            justify-content: center;
            width: 100%; height: 100%;
        }
        .loader-content {
            background: white;
            padding: 2rem;
            border-radius: 8px;
            text-align: center;
        }
        .loader-message {
            margin-top: 1rem;
        }
        .toast-container {
            position: fixed;
            top: 20px;
            right: 20px;
            z-index: 10000;
        }
        .toast {
            background: white;
            border-radius: 4px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.15);
            margin-bottom: 10px;
            overflow: hidden;
            animation: slideIn 0.3s ease;
        }
        .toast-success { border-left: 4px solid #28a745; }
        .toast-error { border-left: 4px solid #dc3545; }
        .toast-warning { border-left: 4px solid #ffc107; }
        .toast-info { border-left: 4px solid #17a2b8; }
        .toast-content {
            padding: 12px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .toast-close {
            background: none;
            border: none;
            font-size: 18px;
            cursor: pointer;
            margin-left: 10px;
        }
        @keyframes slideIn {
            from { transform: translateX(100%); }
            to { transform: translateX(0); }
        }
    `;
    
    const styleEl = document.createElement('style');
    styleEl.textContent = globalStyles;
    document.head.appendChild(styleEl);
});

console.log('SimpleChatServer main.js loaded successfully');